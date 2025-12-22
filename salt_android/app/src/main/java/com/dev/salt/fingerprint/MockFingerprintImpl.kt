package com.dev.salt.fingerprint

import com.dev.salt.logging.AppLogger as Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Mock implementation of fingerprint capture for testing/emulator
 */
class MockFingerprintImpl : IFingerprintCapture {
    companion object {
        private const val TAG = "MockFingerprintImpl"
        private const val MOCK_FINGERPRINT_PREFIX = "MOCK_FP_"
    }

    private var isInitialized = false

    override suspend fun initializeDevice(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Initializing MOCK fingerprint device")
                // Simulate initialization delay
                delay(500)
                isInitialized = true
                Log.i(TAG, "MOCK device initialized successfully")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize MOCK device", e)
                false
            }
        }
    }

    override suspend fun captureFingerprint(): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                if (!isInitialized) {
                    Log.e(TAG, "MOCK device not initialized")
                    return@withContext null
                }

                Log.d(TAG, "Starting MOCK fingerprint capture")
                // Simulate capture delay
                delay(1000)

                // Generate a mock fingerprint template
                val mockId = System.currentTimeMillis()
                val mockFingerprint = "$MOCK_FINGERPRINT_PREFIX$mockId"

                // For testing duplicate detection, uncomment this line:
                // val mockFingerprint = "${MOCK_FINGERPRINT_PREFIX}DUPLICATE"

                // Create a mock template (just use the hash bytes for testing)
                val mockTemplate = mockFingerprint.toByteArray()
                Log.i(TAG, "MOCK fingerprint captured successfully. Template size: ${mockTemplate.size} bytes")

                mockTemplate
            } catch (e: Exception) {
                Log.e(TAG, "Failed to capture MOCK fingerprint", e)
                null
            }
        }
    }

    override suspend fun closeDevice() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Closing MOCK device")
                isInitialized = false
                Log.i(TAG, "MOCK device closed")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to close MOCK device", e)
            }
        }
    }

    override fun getImplementationType(): String {
        return "MOCK"
    }

    override suspend fun matchTemplates(template1: ByteArray, template2: ByteArray): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Matching templates in MOCK mode")
                // In mock mode, just compare the byte arrays
                // In real implementation, this would use the SecuGen SDK matching function
                val match = template1.contentEquals(template2)
                Log.i(TAG, "Template match result: $match")
                match
            } catch (e: Exception) {
                Log.e(TAG, "Failed to match templates", e)
                false
            }
        }
    }

    /**
     * Hashes fingerprint data using SHA-256
     */
    private fun hashFingerprint(fingerprintData: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(fingerprintData.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}