package com.dev.salt.util

import android.os.Build
import android.util.Log

/**
 * Utility class to detect if the app is running in an emulator
 */
object EmulatorDetector {
    private const val TAG = "EmulatorDetector"

    /**
     * Check if the app is running in an emulator
     *
     * @return true if running in emulator, false if on real device
     */
    fun isEmulator(): Boolean {
        val result = checkEmulator()
        Log.i(TAG, "Emulator detection result: $result")
        Log.i(TAG, "Device details - Hardware: ${Build.HARDWARE}, Fingerprint: ${Build.FINGERPRINT}")
        return result
    }

    private fun checkEmulator(): Boolean {
        return when {
            // Check for goldfish (traditional emulator)
            Build.HARDWARE.contains("goldfish") -> {
                Log.d(TAG, "Detected goldfish hardware")
                true
            }

            // Check for ranchu (newer 64-bit emulator)
            Build.HARDWARE.contains("ranchu") -> {
                Log.d(TAG, "Detected ranchu hardware")
                true
            }

            // Check fingerprint patterns
            Build.FINGERPRINT.startsWith("generic") -> {
                Log.d(TAG, "Detected generic fingerprint")
                true
            }

            Build.FINGERPRINT.startsWith("unknown") -> {
                Log.d(TAG, "Detected unknown fingerprint")
                true
            }

            // Check model
            Build.MODEL.contains("google_sdk") -> {
                Log.d(TAG, "Detected google_sdk model")
                true
            }

            Build.MODEL.contains("Emulator") -> {
                Log.d(TAG, "Detected Emulator in model")
                true
            }

            Build.MODEL.contains("Android SDK built for") -> {
                Log.d(TAG, "Detected Android SDK built for in model")
                true
            }

            // Check manufacturer
            Build.MANUFACTURER == "Genymotion" -> {
                Log.d(TAG, "Detected Genymotion manufacturer")
                true
            }

            Build.MANUFACTURER == "Google" && Build.BRAND == "google" && Build.DEVICE == "generic" -> {
                Log.d(TAG, "Detected Google emulator combination")
                true
            }

            // Check product
            Build.PRODUCT.contains("sdk_gphone") -> {
                Log.d(TAG, "Detected sdk_gphone product")
                true
            }

            Build.PRODUCT.contains("vbox") -> {
                Log.d(TAG, "Detected vbox product")
                true
            }

            Build.PRODUCT.contains("emulator") -> {
                Log.d(TAG, "Detected emulator in product")
                true
            }

            // Check board
            Build.BOARD == "QC_Reference_Phone" -> {
                Log.d(TAG, "Detected QC_Reference_Phone board")
                false // This is actually a real device indicator
            }

            else -> {
                Log.d(TAG, "No emulator patterns detected - assuming real device")
                false
            }
        }
    }

    /**
     * Get a description of the current environment
     */
    fun getEnvironmentDescription(): String {
        return if (isEmulator()) {
            "Running in EMULATOR"
        } else {
            "Running on REAL DEVICE"
        }
    }
}