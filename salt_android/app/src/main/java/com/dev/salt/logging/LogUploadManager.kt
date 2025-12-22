package com.dev.salt.logging

import android.content.Context
import android.os.Build
import com.dev.salt.data.SurveyDatabase
import com.dev.salt.upload.UploadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class LogUploadManager(
    private val context: Context,
    private val database: SurveyDatabase
) {
    companion object {
        private const val TAG = "LogUploadManager"
        private const val CONNECTION_TIMEOUT_MS = 30000
        private const val READ_TIMEOUT_MS = 30000
    }

    suspend fun uploadLogs(): UploadResult {
        return withContext(Dispatchers.IO) {
            // Collect latest log file
            val logs = AppLogger.collectLatestLogs()
            if (logs.isNullOrEmpty()) {
                return@withContext UploadResult.ConfigurationError("No logs to upload")
            }

            // Get server configuration
            val serverConfig = database.appServerConfigDao().getServerConfig()
            if (serverConfig == null || serverConfig.serverUrl.isBlank() || serverConfig.apiKey.isBlank()) {
                return@withContext UploadResult.ConfigurationError("Server not configured. Please configure server settings.")
            }

            // Build JSON payload (using recruitment payment format as temporary hack)
            // Send raw log text directly (no compression/encoding)
            val jsonData = buildLogUploadJson(logs)

            // Upload URL
            val uploadUrl = "${serverConfig.serverUrl}/api/sync/recruitment-payment/upload"

            // Perform HTTP upload
            performHttpUpload(uploadUrl, jsonData, serverConfig.apiKey)
        }
    }

    private fun buildLogUploadJson(logs: String): JSONObject {
        val logUploadId = "LOG_UPLOAD_${UUID.randomUUID()}"
        val facilityConfig = database.facilityConfigDao().getFacilityConfig()

        return JSONObject().apply {
            put("paymentId", logUploadId)
            put("surveyId", "DEV_LOG_UPLOAD")
            put("subjectId", "SYSTEM")
            put("phone", null)
            put("totalAmount", 0.0)
            put("currency", facilityConfig?.paymentCurrency ?: "USD")
            put("paymentDate", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                .format(java.util.Date()))
            put("confirmationMethod", "dev_log_upload")
            put("signatureHex", logs) // HACK: raw log text in signature field
            put("couponCodes", JSONArray().apply {
                put("DEV_LOG_UPLOAD") // Dummy coupon code to satisfy server validation
            })
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

    private suspend fun performHttpUpload(serverUrl: String, jsonData: JSONObject, apiKey: String): UploadResult {
        return withContext(Dispatchers.IO) {
            try {
                AppLogger.d(TAG, "Uploading logs to: $serverUrl")

                val url = URL(serverUrl)
                val connection = url.openConnection() as HttpURLConnection

                connection.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Authorization", "Bearer $apiKey")
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
                AppLogger.d(TAG, "Upload response code: $responseCode")

                when (responseCode) {
                    in 200..299 -> {
                        AppLogger.i(TAG, "Logs uploaded successfully")
                        UploadResult.Success
                    }
                    in 400..499 -> {
                        val errorMessage = try {
                            connection.errorStream?.bufferedReader()?.readText() ?: "Client error"
                        } catch (e: Exception) {
                            "Client error"
                        }
                        AppLogger.w(TAG, "Client error during log upload: $responseCode - $errorMessage")
                        UploadResult.ServerError(responseCode, errorMessage)
                    }
                    in 500..599 -> {
                        val errorMessage = try {
                            connection.errorStream?.bufferedReader()?.readText() ?: "Server error"
                        } catch (e: Exception) {
                            "Server error"
                        }
                        AppLogger.w(TAG, "Server error during log upload: $responseCode - $errorMessage")
                        UploadResult.ServerError(responseCode, errorMessage)
                    }
                    else -> {
                        AppLogger.w(TAG, "Unexpected response code: $responseCode")
                        UploadResult.UnknownError("Unexpected response code: $responseCode")
                    }
                }
            } catch (e: java.net.UnknownHostException) {
                AppLogger.e(TAG, "Network error: Unknown host", e)
                UploadResult.NetworkError("Cannot reach server: ${e.message}")
            } catch (e: java.net.SocketTimeoutException) {
                AppLogger.e(TAG, "Network error: Timeout", e)
                UploadResult.NetworkError("Connection timeout: ${e.message}")
            } catch (e: java.io.IOException) {
                AppLogger.e(TAG, "Network error: IO exception", e)
                UploadResult.NetworkError("Network error: ${e.message}")
            } catch (e: Exception) {
                AppLogger.e(TAG, "Upload exception", e)
                UploadResult.UnknownError("Upload failed: ${e.message}")
            }
        }
    }
}
