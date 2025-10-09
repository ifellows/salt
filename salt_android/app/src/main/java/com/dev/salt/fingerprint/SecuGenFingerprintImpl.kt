package com.dev.salt.fingerprint

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import SecuGen.FDxSDKPro.JSGFPLib
import SecuGen.FDxSDKPro.SGFDxDeviceName
import SecuGen.FDxSDKPro.SGFDxErrorCode
import SecuGen.FDxSDKPro.SGDeviceInfoParam
import SecuGen.FDxSDKPro.SGFingerInfo
import SecuGen.FDxSDKPro.SGFingerPosition
import SecuGen.FDxSDKPro.SGImpressionType

/**
 * SecuGen HU20-A fingerprint implementation using FDx SDK Pro
 */
class SecuGenFingerprintImpl(private val context: Context) : IFingerprintCapture {
    companion object {
        private const val TAG = "SecuGenFingerprintImpl"
        private const val SECUGEN_VENDOR_ID = 0x1162 // SecuGen Vendor ID
        private const val IMAGE_CAPTURE_TIMEOUT_MS = 10000L // 10 seconds
        private const val IMAGE_CAPTURE_QUALITY = 50 // Quality threshold (0-100)
    }

    private var sgfplib: JSGFPLib? = null
    private var deviceInfo: SGDeviceInfoParam? = null
    private var isInitialized = false
    private var imageBuffer: ByteArray? = null

    override suspend fun initializeDevice(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Initializing SecuGen fingerprint device...")

                // Get USB manager
                val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

                // Check if SecuGen device is connected
                val deviceList = usbManager.deviceList
                var hasSecuGenDevice = false

                for ((name, device) in deviceList) {
                    Log.d(TAG, "USB Device $name: Vendor=0x${device.vendorId.toString(16)}, Product=0x${device.productId.toString(16)}")
                    if (device.vendorId == SECUGEN_VENDOR_ID) {
                        hasSecuGenDevice = true
                        Log.i(TAG, "Found SecuGen device: $name")
                        break
                    }
                }

                if (!hasSecuGenDevice) {
                    Log.w(TAG, "No SecuGen device found")
                    return@withContext false
                }

                // Check if we have USB permission first
                var hasPermission = false
                for ((_, device) in deviceList) {
                    if (device.vendorId == SECUGEN_VENDOR_ID) {
                        hasPermission = usbManager.hasPermission(device)
                        if (!hasPermission) {
                            Log.e(TAG, "No USB permission for SecuGen device")
                            return@withContext false
                        }
                        break
                    }
                }

                // Create JSGFPLib instance
                try {
                    Log.d(TAG, "Creating JSGFPLib instance with USB permission: $hasPermission")
                    sgfplib = JSGFPLib(context, usbManager)
                    Log.d(TAG, "JSGFPLib instance created successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create JSGFPLib instance", e)
                    return@withContext false
                } catch (e: UnsatisfiedLinkError) {
                    Log.e(TAG, "Native library not found when creating JSGFPLib", e)
                    return@withContext false
                } catch (e: NoClassDefFoundError) {
                    Log.e(TAG, "JSGFPLib class not found", e)
                    return@withContext false
                } catch (e: Error) {
                    Log.e(TAG, "Fatal error creating JSGFPLib", e)
                    return@withContext false
                }

                // Check if native library loaded successfully
                val jniLoadStatus = try {
                    sgfplib?.GetJniLoadStatus() ?: -1
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get JNI load status", e)
                    -1
                }
                if (jniLoadStatus != SGFDxErrorCode.SGFDX_ERROR_NONE.toLong()) {
                    Log.e(TAG, "Failed to load native library. Error: $jniLoadStatus")
                    return@withContext false
                }

                Log.d(TAG, "Native library loaded successfully")

                // Initialize the SDK with auto-detect
                val initError = sgfplib?.Init(SGFDxDeviceName.SG_DEV_AUTO) ?: -1
                if (initError != SGFDxErrorCode.SGFDX_ERROR_NONE.toLong()) {
                    Log.e(TAG, "Failed to initialize SDK. Error: $initError")
                    return@withContext false
                }

                Log.i(TAG, "SDK initialized successfully")

                // Open the device
                val openError = sgfplib?.OpenDevice(SGFDxDeviceName.SG_DEV_AUTO) ?: -1
                if (openError != SGFDxErrorCode.SGFDX_ERROR_NONE.toLong()) {
                    Log.e(TAG, "Failed to open device. Error: $openError")
                    return@withContext false
                }

                Log.i(TAG, "Device opened successfully")

                // Get device information
                deviceInfo = SGDeviceInfoParam()
                val infoError = sgfplib?.GetDeviceInfo(deviceInfo) ?: -1
                if (infoError != SGFDxErrorCode.SGFDX_ERROR_NONE.toLong()) {
                    Log.e(TAG, "Failed to get device info. Error: $infoError")
                    return@withContext false
                }

                deviceInfo?.let { info ->
                    Log.i(TAG, "Device Info:")
                    Log.i(TAG, "  Serial Number: ${info.deviceSN()}")
                    Log.i(TAG, "  Device ID: ${info.deviceID}")
                    Log.i(TAG, "  Image Width: ${info.imageWidth}")
                    Log.i(TAG, "  Image Height: ${info.imageHeight}")
                    Log.i(TAG, "  Image DPI: ${info.imageDPI}")

                    // Allocate image buffer
                    val bufferSize = info.imageWidth * info.imageHeight
                    imageBuffer = ByteArray(bufferSize)
                    Log.i(TAG, "Allocated image buffer of size: $bufferSize")
                }

                isInitialized = true
                Log.i(TAG, "SecuGen device initialization complete!")
                true

            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize SecuGen device", e)
                false
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "SecuGen SDK native library not found", e)
                false
            } catch (e: NoClassDefFoundError) {
                Log.e(TAG, "SecuGen SDK classes not found", e)
                false
            }
        }
    }

    override suspend fun captureFingerprint(): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                if (!isInitialized || sgfplib == null) {
                    Log.e(TAG, "Device not initialized")
                    return@withContext null
                }

                val buffer = imageBuffer ?: run {
                    Log.e(TAG, "Image buffer not allocated")
                    return@withContext null
                }

                Log.i(TAG, "Starting fingerprint capture...")
                Log.i(TAG, "Please place finger on sensor...")

                // Check if finger is present
                val fingerPresent = BooleanArray(1)
                val fpError = sgfplib?.FingerPresent(fingerPresent) ?: -1
                if (fpError == SGFDxErrorCode.SGFDX_ERROR_NONE.toLong() && fingerPresent[0]) {
                    Log.i(TAG, "Finger detected on sensor")
                } else {
                    Log.i(TAG, "No finger detected, waiting for placement...")
                }

                // Capture image with quality threshold
                val captureError = sgfplib?.GetImageEx(
                    buffer,
                    IMAGE_CAPTURE_TIMEOUT_MS,
                    IMAGE_CAPTURE_QUALITY.toLong()
                ) ?: -1

                if (captureError != SGFDxErrorCode.SGFDX_ERROR_NONE.toLong()) {
                    Log.e(TAG, "Failed to capture image. Error: $captureError")
                    return@withContext null
                }

                Log.i(TAG, "Image captured successfully")

                // Get image quality
                val quality = IntArray(1)
                val qualityError = sgfplib?.GetImageQuality(
                    deviceInfo?.imageWidth?.toLong() ?: 0,
                    deviceInfo?.imageHeight?.toLong() ?: 0,
                    buffer,
                    quality
                ) ?: -1

                if (qualityError == SGFDxErrorCode.SGFDX_ERROR_NONE.toLong()) {
                    Log.i(TAG, "Image quality: ${quality[0]}")
                }

                // Get max template size
                val maxTemplateSize = IntArray(1)
                sgfplib?.GetMaxTemplateSize(maxTemplateSize)
                val templateBuffer = ByteArray(maxTemplateSize[0])

                // Create template from image
                val fingerInfo = SGFingerInfo()
                fingerInfo.FingerNumber = SGFingerPosition.SG_FINGPOS_RI // Right index finger
                fingerInfo.ImageQuality = quality[0]
                fingerInfo.ImpressionType = SGImpressionType.SG_IMPTYPE_LP // Live print
                fingerInfo.ViewNumber = 1

                val templateError = sgfplib?.CreateTemplate(fingerInfo, buffer, templateBuffer) ?: -1
                if (templateError != SGFDxErrorCode.SGFDX_ERROR_NONE.toLong()) {
                    Log.e(TAG, "Failed to create template. Error: $templateError")
                    return@withContext null
                }

                Log.i(TAG, "Template created successfully")

                // Get actual template size
                val templateSize = IntArray(1)
                sgfplib?.GetTemplateSize(templateBuffer, templateSize)
                Log.i(TAG, "Template size: ${templateSize[0]} bytes")

                // Return the actual template for storage
                val actualTemplate = templateBuffer.take(templateSize[0]).toByteArray()

                Log.i(TAG, "Fingerprint captured and processed successfully")
                Log.i(TAG, "Template size: ${actualTemplate.size} bytes")

                actualTemplate

            } catch (e: Exception) {
                Log.e(TAG, "Failed to capture fingerprint", e)
                null
            }
        }
    }

    /**
     * Interface implementation for template matching
     * @param template1 First template to match
     * @param template2 Second template to match
     * @return true if templates match, false otherwise
     */
    override suspend fun matchTemplates(template1: ByteArray, template2: ByteArray): Boolean {
        val score = matchTemplatesWithScore(template1, template2)
        // SecuGen recommends a threshold of 50-100 for a match
        // Using 50 as the threshold for now
        return (score ?: 0) >= 50
    }

    /**
     * Matches a captured template against a stored template
     * @param capturedTemplate The newly captured template
     * @param storedTemplate The stored template to compare against
     * @return Match score (0-200+), or null if matching failed
     */
    suspend fun matchTemplatesWithScore(capturedTemplate: ByteArray, storedTemplate: ByteArray): Int? {
        return withContext(Dispatchers.IO) {
            try {
                if (!isInitialized || sgfplib == null) {
                    Log.e(TAG, "Device not initialized for matching")
                    return@withContext null
                }

                val matchScore = IntArray(1)
                val matchError = sgfplib?.GetMatchingScore(
                    capturedTemplate,
                    storedTemplate,
                    matchScore
                ) ?: -1

                if (matchError != SGFDxErrorCode.SGFDX_ERROR_NONE.toLong()) {
                    Log.e(TAG, "Failed to get matching score. Error: $matchError")
                    return@withContext null
                }

                Log.i(TAG, "Template matching score: ${matchScore[0]}")
                matchScore[0]
            } catch (e: Exception) {
                Log.e(TAG, "Failed to match templates", e)
                null
            }
        }
    }

    override suspend fun closeDevice() {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Closing SecuGen device, isInitialized: $isInitialized")

                // Only close if we actually initialized
                if (!isInitialized) {
                    Log.d(TAG, "Device was not initialized, skipping close")
                    return@withContext
                }

                sgfplib?.let { lib ->
                    try {
                        // Close the device to release hardware
                        val closeError = lib.CloseDevice()
                        if (closeError != SGFDxErrorCode.SGFDX_ERROR_NONE.toLong()) {
                            Log.w(TAG, "Error closing device: $closeError")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception while closing device", e)
                    } catch (e: Error) {
                        Log.e(TAG, "Error while closing device", e)
                    }

                    // NOTE: Skipping lib.Close() due to SecuGen SDK bug in fake detection component
                    // The CFakeDetect destructor causes SIGABRT crashes when lib.Close() is called
                    // CloseDevice() is sufficient to release the hardware resources
                    // The library cleanup will happen automatically when the app terminates
                    Log.d(TAG, "Skipped lib.Close() to avoid SecuGen SDK crash in fake detection destructor")
                }

                sgfplib = null
                deviceInfo = null
                imageBuffer = null
                isInitialized = false

                Log.i(TAG, "SecuGen device closed")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to close device", e)
            }
        }
    }

    override fun getImplementationType(): String {
        return if (isInitialized && deviceInfo != null) {
            "SECUGEN (${deviceInfo?.deviceID ?: "Unknown"})"
        } else {
            "SECUGEN (Not initialized)"
        }
    }

    /**
     * Hashes fingerprint template using SHA-256
     */
    private fun hashFingerprint(template: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(template)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}