package com.dev.salt.auth

import android.content.Context
import com.dev.salt.logging.AppLogger as Log
import com.dev.salt.PasswordUtils
import com.dev.salt.data.User
import com.dev.salt.data.UserDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * Manages biometric authentication for the SALT application.
 * This is a mock implementation that always returns "testtesttest" as the biometric key
 * for testing purposes until actual biometric hardware is available.
 */
class BiometricAuthManager(
    private val context: Context,
    private val userDao: UserDao
) {
    
    companion object {
        private const val TAG = "BiometricAuthManager"
        private const val MOCK_BIOMETRIC_KEY = "testtesttest"
    }

    /**
     * Checks if biometric authentication is supported on this device.
     * Currently returns true for testing purposes.
     */
    fun isBiometricSupported(): Boolean {
        // TODO: In real implementation, check for biometric hardware availability
        // BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        Log.d(TAG, "Mock biometric support check - returning true")
        return true
    }

    /**
     * Checks if biometric authentication is enabled for the given user.
     */
    suspend fun isBiometricEnabledForUser(userName: String): Boolean {
        return withContext(Dispatchers.IO) {
            val user = userDao.getUserForBiometricAuth(userName)
            user?.biometricEnabled ?: false
        }
    }

    /**
     * Enrolls a user for biometric authentication.
     * In mock mode, this always succeeds with the test key.
     */
    suspend fun enrollUserBiometric(userName: String): BiometricResult {
        return withContext(Dispatchers.IO) {
            try {
                // In mock mode, always use the test key
                val biometricKey = getMockBiometricKey()
                val hashedKey = hashBiometricKey(biometricKey)
                
                val enrollmentTime = System.currentTimeMillis()
                userDao.updateUserBiometric(
                    userName = userName,
                    keyHash = hashedKey,
                    enabled = true,
                    enrolledDate = enrollmentTime
                )
                
                Log.d(TAG, "Mock biometric enrollment successful for user: $userName")
                BiometricResult.Success
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enroll biometric for user: $userName", e)
                BiometricResult.Error("Failed to enroll biometric: ${e.message}")
            }
        }
    }

    /**
     * Disables biometric authentication for a user.
     */
    suspend fun disableBiometricForUser(userName: String): BiometricResult {
        return withContext(Dispatchers.IO) {
            try {
                userDao.updateUserBiometric(
                    userName = userName,
                    keyHash = null,
                    enabled = false,
                    enrolledDate = null
                )
                
                Log.d(TAG, "Biometric disabled for user: $userName")
                BiometricResult.Success
            } catch (e: Exception) {
                Log.e(TAG, "Failed to disable biometric for user: $userName", e)
                BiometricResult.Error("Failed to disable biometric: ${e.message}")
            }
        }
    }

    /**
     * Authenticates a user using biometric data.
     * In mock mode, this always succeeds by comparing against the test key.
     */
    suspend fun authenticateUserBiometric(userName: String, callback: (BiometricResult) -> Unit) {
        withContext(Dispatchers.IO) {
            try {
                val user = userDao.getUserForBiometricAuth(userName)
                if (user == null) {
                    callback(BiometricResult.Error("User not found or biometric not enabled"))
                    return@withContext
                }

                // Get mock biometric key from "sensor"
                val scannedKey = getMockBiometricKey()
                val hashedScannedKey = hashBiometricKey(scannedKey)

                // Compare with stored key
                val isMatch = user.biometricKeyHash == hashedScannedKey
                
                if (isMatch) {
                    // Update last authentication time
                    userDao.updateLastBiometricAuth(userName, System.currentTimeMillis())
                    Log.d(TAG, "Mock biometric authentication successful for user: $userName")
                    callback(BiometricResult.Success)
                } else {
                    Log.w(TAG, "Mock biometric authentication failed for user: $userName")
                    callback(BiometricResult.Error("Biometric authentication failed"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during biometric authentication for user: $userName", e)
                callback(BiometricResult.Error("Authentication error: ${e.message}"))
            }
        }
    }

    /**
     * Shows biometric authentication prompt.
     * In mock mode, this immediately calls the callback with success.
     */
    fun showBiometricPrompt(
        title: String,
        subtitle: String,
        callback: (BiometricResult) -> Unit
    ) {
        // TODO: In real implementation, show BiometricPrompt
        // val biometricPrompt = BiometricPrompt(...)
        // biometricPrompt.authenticate(...)
        
        Log.d(TAG, "Mock biometric prompt shown - auto-succeeding")
        // Simulate successful biometric scan
        callback(BiometricResult.Success)
    }

    /**
     * Gets all users who have biometric authentication enabled.
     */
    suspend fun getUsersWithBiometricEnabled(): List<User> {
        return withContext(Dispatchers.IO) {
            userDao.getUsersWithBiometricEnabled()
        }
    }

    /**
     * Mock function that simulates getting biometric key from sensor.
     * Always returns "testtesttest" for testing purposes.
     */
    private fun getMockBiometricKey(): String {
        Log.d(TAG, "Mock biometric sensor reading - returning test key")
        return MOCK_BIOMETRIC_KEY
    }

    /**
     * Hashes a biometric key for secure storage.
     */
    private fun hashBiometricKey(key: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(key.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hash biometric key", e)
            throw e
        }
    }

    /**
     * Validates biometric key format and strength.
     */
    private fun validateBiometricKey(key: String): Boolean {
        // Basic validation - in real implementation, this would check biometric data quality
        return key.isNotBlank() && key.length >= 8
    }
}

/**
 * Sealed class representing biometric authentication results.
 */
sealed class BiometricResult {
    object Success : BiometricResult()
    data class Error(val message: String) : BiometricResult()
    object UserCancelled : BiometricResult()
    object NotSupported : BiometricResult()
    object NotEnrolled : BiometricResult()
}

/**
 * Factory for creating BiometricAuthManager instances.
 */
object BiometricAuthManagerFactory {
    fun create(context: Context, userDao: UserDao): BiometricAuthManager {
        return BiometricAuthManager(context, userDao)
    }
}