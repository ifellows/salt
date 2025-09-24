package com.dev.salt.fingerprint

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Simple USB test implementation to verify device detection without SDK
 */
class UsbTestImpl(private val context: Context) : IFingerprintCapture {
    companion object {
        private const val TAG = "UsbTestImpl"
        private const val SECUGEN_VENDOR_ID = 0x1162
    }

    private var isInitialized = false
    private var detectedDevice: UsbDevice? = null

    override suspend fun initializeDevice(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "USB Test: Checking for SecuGen device...")

                val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                val deviceList = usbManager.deviceList

                Log.d(TAG, "USB Test: Found ${deviceList.size} USB devices")

                for ((name, device) in deviceList) {
                    Log.d(TAG, "USB Device $name:")
                    Log.d(TAG, "  Vendor ID: 0x${device.vendorId.toString(16)}")
                    Log.d(TAG, "  Product ID: 0x${device.productId.toString(16)}")
                    Log.d(TAG, "  Device Name: ${device.deviceName}")
                    Log.d(TAG, "  Device Class: ${device.deviceClass}")
                    Log.d(TAG, "  Device Subclass: ${device.deviceSubclass}")
                    Log.d(TAG, "  Device Protocol: ${device.deviceProtocol}")

                    if (device.vendorId == SECUGEN_VENDOR_ID) {
                        detectedDevice = device
                        Log.i(TAG, "✅ Found SecuGen device: $name")

                        // Check permission
                        val hasPermission = usbManager.hasPermission(device)
                        Log.i(TAG, "USB Permission: $hasPermission")

                        if (!hasPermission) {
                            Log.w(TAG, "No USB permission - request it first")
                            return@withContext false
                        }

                        // Try to open connection (without SDK)
                        try {
                            val connection = usbManager.openDevice(device)
                            if (connection != null) {
                                Log.i(TAG, "✅ Successfully opened USB connection")
                                connection.close()
                                Log.i(TAG, "✅ Successfully closed USB connection")
                            } else {
                                Log.e(TAG, "❌ Failed to open USB connection (null)")
                                return@withContext false
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error opening USB connection", e)
                            return@withContext false
                        }

                        break
                    }
                }

                if (detectedDevice != null) {
                    isInitialized = true
                    Log.i(TAG, "✅ USB Test: SecuGen device ready!")
                    true
                } else {
                    Log.w(TAG, "❌ USB Test: No SecuGen device found")
                    false
                }

            } catch (e: Exception) {
                Log.e(TAG, "USB Test failed", e)
                false
            }
        }
    }

    override suspend fun captureFingerprint(): ByteArray? {
        return withContext(Dispatchers.IO) {
            if (!isInitialized) {
                Log.e(TAG, "Device not initialized")
                return@withContext null
            }

            Log.i(TAG, "USB Test: Mock capture (SDK not loaded)")
            "USB_TEST_SUCCESS_${System.currentTimeMillis()}".toByteArray()
        }
    }

    override suspend fun closeDevice() {
        withContext(Dispatchers.IO) {
            isInitialized = false
            detectedDevice = null
            Log.i(TAG, "USB Test: Device closed")
        }
    }

    override fun getImplementationType(): String {
        return "USB_TEST_ONLY"
    }
}