package com.dev.salt.fingerprint

/**
 * Interface for fingerprint capture implementations
 */
interface IFingerprintCapture {
    /**
     * Initialize the fingerprint device
     * @return true if initialization successful, false otherwise
     */
    suspend fun initializeDevice(): Boolean

    /**
     * Capture a fingerprint from the device
     * @return The captured fingerprint template as a ByteArray, or null if capture failed
     */
    suspend fun captureFingerprint(): ByteArray?

    /**
     * Close the fingerprint device connection
     */
    suspend fun closeDevice()

    /**
     * Match two fingerprint templates
     * @param template1 First template to match
     * @param template2 Second template to match
     * @return true if templates match, false otherwise
     */
    suspend fun matchTemplates(template1: ByteArray, template2: ByteArray): Boolean

    /**
     * Get the implementation type for logging/debugging
     * @return String describing the implementation (e.g., "MOCK", "SECUGEN")
     */
    fun getImplementationType(): String
}