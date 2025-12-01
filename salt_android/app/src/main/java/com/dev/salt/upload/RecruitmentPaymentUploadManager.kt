package com.dev.salt.upload

import android.content.Context
import android.os.Build
import android.util.Log
import com.dev.salt.data.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.min
import kotlin.math.pow

/**
 * Manages uploading recruitment payment records to the server.
 *
 * Recruitment payments are made when a participant returns and receives
 * compensation for recruiting other participants. This manager handles:
 * - Serializing payment data to JSON
 * - HTTP upload with retry logic
 * - Tracking upload state for offline retry
 */
class RecruitmentPaymentUploadManager(
    private val context: Context,
    private val database: SurveyDatabase
) {
    private val uploadStateDao = database.recruitmentPaymentUploadStateDao()
    private val appServerConfigDao = database.appServerConfigDao()
    private val facilityConfigDao = database.facilityConfigDao()

    companion object {
        private const val TAG = "RecruitPaymentUpload"
        private const val MAX_RETRY_ATTEMPTS = 5
        private const val BASE_RETRY_DELAY_MS = 1000L
        private const val CONNECTION_TIMEOUT_MS = 30000
        private const val READ_TIMEOUT_MS = 30000
    }

    /**
     * Upload a recruitment payment by its payment ID.
     * Creates the upload state if it doesn't exist.
     */
    suspend fun uploadPayment(paymentId: String): UploadResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting upload for payment: $paymentId")

                // Get upload state
                val uploadState = uploadStateDao.getByPaymentId(paymentId)
                if (uploadState == null) {
                    Log.e(TAG, "Payment upload state not found: $paymentId")
                    return@withContext UploadResult.UnknownError("Payment upload state not found")
                }

                // Get server configuration
                val serverConfig = appServerConfigDao.getServerConfig()
                if (serverConfig == null || serverConfig.serverUrl.isBlank() || serverConfig.apiKey.isBlank()) {
                    Log.e(TAG, "No server configuration found for upload")
                    updateUploadState(paymentId, "FAILED", "Server not configured")
                    return@withContext UploadResult.ConfigurationError("Server not configured. Please configure server settings.")
                }

                // Build JSON payload
                val jsonData = buildPaymentJson(uploadState)

                // Build the upload URL
                val uploadUrl = "${serverConfig.serverUrl}/api/sync/recruitment-payment/upload"

                // Perform HTTP upload
                val uploadResult = performHttpUpload(uploadUrl, jsonData, serverConfig.apiKey)

                when (uploadResult) {
                    is UploadResult.Success -> {
                        Log.i(TAG, "Successfully uploaded payment: $paymentId")
                        uploadStateDao.markCompleted(paymentId)
                    }
                    else -> {
                        Log.w(TAG, "Upload failed for payment: $paymentId, result: $uploadResult")
                        val errorMessage = when (uploadResult) {
                            is UploadResult.NetworkError -> uploadResult.message
                            is UploadResult.ServerError -> "Server error ${uploadResult.code}: ${uploadResult.message}"
                            is UploadResult.ConfigurationError -> uploadResult.message
                            is UploadResult.UnknownError -> uploadResult.message
                            else -> "Unknown error"
                        }
                        updateUploadState(paymentId, "FAILED", errorMessage)
                    }
                }

                uploadResult

            } catch (e: Exception) {
                Log.e(TAG, "Exception during payment upload: $paymentId", e)
                updateUploadState(paymentId, "FAILED", "Upload exception: ${e.message}")
                UploadResult.UnknownError("Upload failed with exception: ${e.message}")
            }
        }
    }

    private fun buildPaymentJson(uploadState: RecruitmentPaymentUploadState): JSONObject {
        val facilityConfig = facilityConfigDao.getFacilityConfig()

        return JSONObject().apply {
            put("paymentId", uploadState.paymentId)
            put("surveyId", uploadState.surveyId)
            put("subjectId", uploadState.subjectId)
            put("phone", uploadState.paymentPhone)
            put("totalAmount", uploadState.paymentAmount)
            put("currency", facilityConfig?.paymentCurrency ?: "USD")
            put("paymentDate", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                .format(java.util.Date(uploadState.createdTime)))
            put("confirmationMethod", uploadState.confirmationMethod)
            put("signatureHex", uploadState.signatureHex)
            put("couponCodes", JSONArray(uploadState.couponCodes.split(",")))
            put("deviceInfo", JSONObject().apply {
                put("deviceId", android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                ))
                put("appVersion", try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (e: Exception) { "unknown" })
                put("androidVersion", Build.VERSION.RELEASE)
                put("deviceModel", "${Build.MANUFACTURER} ${Build.MODEL}")
            })
        }
    }

    private fun updateUploadState(paymentId: String, status: String, errorMessage: String?) {
        val currentState = uploadStateDao.getByPaymentId(paymentId)
        val newAttemptCount = (currentState?.attemptCount ?: 0) + 1

        uploadStateDao.updateAttempt(
            paymentId = paymentId,
            status = status,
            time = System.currentTimeMillis(),
            count = newAttemptCount,
            error = errorMessage
        )
    }

    private suspend fun performHttpUpload(serverUrl: String, jsonData: JSONObject, apiKey: String?): UploadResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Performing HTTP upload to: $serverUrl")

                val url = URL(serverUrl)
                val connection = url.openConnection() as HttpURLConnection

                connection.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    if (!apiKey.isNullOrBlank()) {
                        setRequestProperty("Authorization", "Bearer $apiKey")
                    }
                    setRequestProperty("User-Agent", "SALT-Android-Client/1.0")

                    connectTimeout = CONNECTION_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    doOutput = true
                }

                // Write JSON data
                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(jsonData.toString())
                    writer.flush()
                }

                // Check response
                val responseCode = connection.responseCode
                Log.d(TAG, "Upload response code: $responseCode")

                when (responseCode) {
                    in 200..299 -> {
                        // Check if server indicated this was a duplicate
                        val responseBody = try {
                            connection.inputStream.bufferedReader().readText()
                        } catch (e: Exception) { "" }

                        if (responseBody.contains("duplicate")) {
                            Log.i(TAG, "Payment already uploaded (duplicate)")
                        } else {
                            Log.i(TAG, "Upload successful")
                        }
                        UploadResult.Success
                    }
                    in 400..499 -> {
                        val errorMessage = try {
                            connection.errorStream?.bufferedReader()?.readText() ?: "Client error"
                        } catch (e: Exception) {
                            "Client error"
                        }
                        Log.w(TAG, "Client error during upload: $responseCode - $errorMessage")
                        UploadResult.ServerError(responseCode, errorMessage)
                    }
                    in 500..599 -> {
                        val errorMessage = try {
                            connection.errorStream?.bufferedReader()?.readText() ?: "Server error"
                        } catch (e: Exception) {
                            "Server error"
                        }
                        Log.w(TAG, "Server error during upload: $responseCode - $errorMessage")
                        UploadResult.ServerError(responseCode, errorMessage)
                    }
                    else -> {
                        Log.w(TAG, "Unexpected response code: $responseCode")
                        UploadResult.UnknownError("Unexpected response code: $responseCode")
                    }
                }

            } catch (e: java.net.SocketTimeoutException) {
                Log.w(TAG, "Upload timeout", e)
                UploadResult.NetworkError("Connection timeout")
            } catch (e: java.net.UnknownHostException) {
                Log.w(TAG, "Unknown host", e)
                UploadResult.NetworkError("Server not reachable: ${e.message}")
            } catch (e: java.net.ConnectException) {
                Log.w(TAG, "Connection failed", e)
                UploadResult.NetworkError("Connection failed: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Network error during upload", e)
                UploadResult.NetworkError("Network error: ${e.message}")
            }
        }
    }

    /**
     * Retry all pending recruitment payment uploads.
     */
    suspend fun retryPendingUploads(): List<Pair<String, UploadResult>> {
        return withContext(Dispatchers.IO) {
            val pendingUploads = uploadStateDao.getPendingUploads()
            Log.i(TAG, "Found ${pendingUploads.size} pending recruitment payment uploads")

            val results = mutableListOf<Pair<String, UploadResult>>()

            for (uploadState in pendingUploads) {
                // Apply exponential backoff
                val timeSinceLastAttempt = System.currentTimeMillis() - (uploadState.lastAttemptTime ?: 0)
                val requiredDelay = calculateBackoffDelay(uploadState.attemptCount)

                if (timeSinceLastAttempt < requiredDelay) {
                    Log.d(TAG, "Skipping retry for ${uploadState.paymentId} - backoff period not met")
                    continue
                }

                val result = uploadPayment(uploadState.paymentId)
                results.add(uploadState.paymentId to result)

                // Small delay between retries
                delay(1000)
            }

            results
        }
    }

    private fun calculateBackoffDelay(attemptCount: Int): Long {
        return (BASE_RETRY_DELAY_MS * 2.0.pow(min(attemptCount, 6).toDouble())).toLong()
    }
}
