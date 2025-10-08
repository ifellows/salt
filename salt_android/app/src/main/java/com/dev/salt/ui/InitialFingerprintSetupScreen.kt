package com.dev.salt.ui

import android.util.Log
import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.dev.salt.R
import com.dev.salt.PasswordUtils
import com.dev.salt.data.SurveyDatabase
import com.dev.salt.data.User
import com.dev.salt.fingerprint.FingerprintManager
import kotlinx.coroutines.launch

/**
 * Initial fingerprint setup screen for admin user during first-time setup wizard.
 *
 * Captures fingerprint template for the admin user and creates the user account
 * in the database. This is the third and final step in the setup wizard before
 * proceeding to facility configuration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InitialFingerprintSetupScreen(
    username: String,
    fullName: String,
    password: String,
    onSetupComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = SurveyDatabase.getInstance(context)
    val fingerprintManager = remember { FingerprintManager(database.subjectFingerprintDao(), context) }

    var captureStatus by remember { mutableStateOf("Ready to capture fingerprint") }
    var fingerprintTemplate by remember { mutableStateOf<ByteArray?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var isCreatingUser by remember { mutableStateOf(false) }
    var retryCount by remember { mutableStateOf(0) }

    fun captureFingerprint() {
        scope.launch {
            isCapturing = true
            captureStatus = "Capturing fingerprint..."

            Log.i("InitialFingerprintSetup", "Starting fingerprint enrollment for admin user: $username")

            // Check for USB device and permission
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val deviceList = usbManager.deviceList
            var secugenDevice: UsbDevice? = null

            for ((_, device) in deviceList) {
                if (device.vendorId == 0x1162) { // SecuGen vendor ID
                    secugenDevice = device
                    break
                }
            }

            if (secugenDevice == null) {
                Log.e("InitialFingerprintSetup", "No SecuGen device found")
                captureStatus = "✗ Fingerprint scanner not connected. Please connect the device and try again."
                isCapturing = false
                return@launch
            }

            if (!usbManager.hasPermission(secugenDevice)) {
                Log.i("InitialFingerprintSetup", "Requesting USB permission")

                val permissionIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent("com.dev.salt.USB_PERMISSION").apply {
                        setPackage(context.packageName)
                    },
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_IMMUTABLE
                    } else {
                        0
                    }
                )

                usbManager.requestPermission(secugenDevice, permissionIntent)
                captureStatus = "✗ Please grant USB permission and try again."
                isCapturing = false
                return@launch
            }

            // Initialize device
            if (!fingerprintManager.initializeDevice()) {
                Log.e("InitialFingerprintSetup", "Device initialization failed")
                captureStatus = "✗ Failed to initialize fingerprint scanner"
                isCapturing = false
                return@launch
            }

            // Capture fingerprint
            val template = fingerprintManager.captureFingerprint()

            if (template == null) {
                Log.e("InitialFingerprintSetup", "Capture failed")
                retryCount++
                if (retryCount >= 3) {
                    captureStatus = "✗ Failed to capture fingerprint after 3 attempts"
                    retryCount = 0
                } else {
                    captureStatus = "✗ Low quality scan. Please try again (Attempt $retryCount of 3)"
                }
                fingerprintManager.closeDevice()
                isCapturing = false
                return@launch
            }

            Log.i("InitialFingerprintSetup", "Fingerprint captured successfully")
            fingerprintTemplate = template
            captureStatus = "✓ Fingerprint captured successfully"
            fingerprintManager.closeDevice()
            isCapturing = false
        }
    }

    fun completeSetup() {
        if (fingerprintTemplate == null) {
            captureStatus = "✗ Please capture fingerprint first"
            return
        }

        isCreatingUser = true
        scope.launch {
            try {
                // Hash the password
                val hashedPassword = PasswordUtils.hashPasswordWithNewSalt(password)
                if (hashedPassword == null) {
                    captureStatus = "✗ Failed to hash password"
                    isCreatingUser = false
                    return@launch
                }

                // Create admin user
                val adminUser = User(
                    userName = username,
                    fullName = fullName,
                    hashedPassword = hashedPassword,
                    role = "ADMINISTRATOR",
                    fingerprintTemplate = fingerprintTemplate,
                    biometricEnabled = true,
                    sessionTimeoutMinutes = 30,
                    lastLoginTime = System.currentTimeMillis(),
                    lastActivityTime = System.currentTimeMillis()
                )

                // Insert user into database
                database.userDao().insertUser(adminUser)
                Log.d("FingerprintSetup", "Admin user created: $username")

                // Navigate to facility setup
                onSetupComplete()
            } catch (e: Exception) {
                captureStatus = "✗ Failed to create user: ${e.message}"
                isCreatingUser = false
                Log.e("FingerprintSetup", "Error creating user", e)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SALT Setup - Fingerprint Enrollment") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Fingerprint Enrollment",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Enroll your fingerprint for secure authentication",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // User info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Account Information",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text("Username: $username")
                    Text("Full Name: $fullName")
                    Text("Role: Administrator")
                }
            }

            // Fingerprint capture card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Fingerprint Scanner",
                        style = MaterialTheme.typography.titleMedium
                    )

                    // Fingerprint instruction image
                    Image(
                        painter = painterResource(id = R.drawable.fingerprint_instruction),
                        contentDescription = "Fingerprint scanner instruction",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    )

                    // Status message
                    Text(
                        text = captureStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            captureStatus.startsWith("✓") -> MaterialTheme.colorScheme.primary
                            captureStatus.startsWith("✗") -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    // Capture button
                    Button(
                        onClick = { captureFingerprint() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isCapturing && !isCreatingUser
                    ) {
                        if (isCapturing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (fingerprintTemplate != null) "Recapture Fingerprint" else "Capture Fingerprint")
                    }

                    // Instructions
                    Text(
                        text = "• Place your index finger on the scanner\n" +
                                "• Keep your finger steady during capture\n" +
                                "• Fingerprint is required for account creation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Complete setup button
            Button(
                onClick = { completeSetup() },
                modifier = Modifier.fillMaxWidth(),
                enabled = fingerprintTemplate != null && !isCapturing && !isCreatingUser
            ) {
                if (isCreatingUser) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Creating Account...")
                } else {
                    Text("Complete Setup")
                }
            }
        }
    }
}
