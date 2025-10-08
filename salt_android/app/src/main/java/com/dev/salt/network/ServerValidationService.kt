package com.dev.salt.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Service for validating SALT server connections during initial setup.
 *
 * Verifies that a given URL points to a valid SALT management server
 * by calling the /api/health endpoint and checking for the "SALT" system identifier.
 */
object ServerValidationService {

    private const val TAG = "ServerValidationService"
    private const val HEALTH_ENDPOINT = "/api/health"
    private const val TIMEOUT_MS = 10000 // 10 seconds

    /**
     * Validation result containing status and optional error message
     */
    sealed class ValidationResult {
        data class Success(val version: String) : ValidationResult()
        data class Error(val message: String) : ValidationResult()
    }

    /**
     * Validates that the given URL points to a SALT server.
     *
     * @param baseUrl The base URL to validate (e.g., "http://10.0.2.2:3000")
     * @return ValidationResult indicating success or failure with details
     */
    suspend fun validateSaltServer(baseUrl: String): ValidationResult = withContext(Dispatchers.IO) {
        try {
            // Clean up URL - remove trailing slash
            val cleanUrl = baseUrl.trimEnd('/')

            // Validate URL format
            if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                return@withContext ValidationResult.Error("URL must start with http:// or https://")
            }

            // Check if HTTPS is required (not localhost, emulator, or local network)
            val isLocalhost = cleanUrl.contains("localhost") || cleanUrl.contains("127.0.0.1")
            val isEmulator = cleanUrl.contains("10.0.2.2")
            val isLocalNetwork = cleanUrl.contains("192.168.")
            val isDebugUrl = isLocalhost || isEmulator || isLocalNetwork

            if (!isDebugUrl && !cleanUrl.startsWith("https://")) {
                return@withContext ValidationResult.Error("HTTPS required for non-local servers")
            }

            // Build health check URL
            val healthUrl = "$cleanUrl$HEALTH_ENDPOINT"
            Log.d(TAG, "Validating SALT server at: $healthUrl")

            // Make HTTP request
            val url = URL(healthUrl)
            val connection = url.openConnection() as HttpURLConnection

            try {
                connection.apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                }

                val responseCode = connection.responseCode
                Log.d(TAG, "Response code: $responseCode")

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext ValidationResult.Error("Server returned HTTP $responseCode")
                }

                // Read and parse response
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "Response: $response")

                val jsonResponse = JSONObject(response)

                // Verify it's a SALT server
                val system = jsonResponse.optString("system", "")
                if (system != "SALT") {
                    return@withContext ValidationResult.Error("Not a SALT server (system=$system)")
                }

                // Verify status
                val status = jsonResponse.optString("status", "")
                if (status != "ok") {
                    return@withContext ValidationResult.Error("Server status: $status")
                }

                // Extract version
                val version = jsonResponse.optString("version", "unknown")
                Log.d(TAG, "SALT server validated successfully (version: $version)")

                ValidationResult.Success(version)

            } finally {
                connection.disconnect()
            }

        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "Unknown host", e)
            ValidationResult.Error("Server not found - check URL")
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "Connection failed", e)
            ValidationResult.Error("Cannot connect - check URL and network")
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "Timeout", e)
            ValidationResult.Error("Connection timeout - server may be unreachable")
        } catch (e: org.json.JSONException) {
            Log.e(TAG, "Invalid JSON response", e)
            ValidationResult.Error("Invalid server response")
        } catch (e: Exception) {
            Log.e(TAG, "Validation failed", e)
            ValidationResult.Error("Validation failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}
