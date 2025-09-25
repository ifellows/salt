package com.dev.salt.ui

import android.media.MediaPlayer
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.dev.salt.AppDestinations
import com.dev.salt.data.SurveyDatabase
import com.dev.salt.playAudio
import com.dev.salt.fingerprint.IFingerprintCapture
import com.dev.salt.fingerprint.SecuGenFingerprintImpl
import com.dev.salt.fingerprint.MockFingerprintImpl
import com.dev.salt.util.EmulatorDetector
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.dev.salt.upload.SurveyUploadManager
import com.dev.salt.upload.SurveyUploadWorkManager
import com.dev.salt.upload.UploadResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.res.stringResource
import com.dev.salt.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectPaymentScreen(
    navController: NavController,
    surveyId: String,
    coupons: String
) {
    val context = LocalContext.current
    val database = remember { SurveyDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    var facilityConfig by remember { mutableStateOf<com.dev.salt.data.FacilityConfig?>(null) }
    var survey by remember { mutableStateOf<com.dev.salt.data.Survey?>(null) }
    var paymentAmount by remember { mutableStateOf(0.0) }
    var isCapturingFingerprint by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentMediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadMessage by remember { mutableStateOf<String?>(null) }
    var paymentMessage by remember { mutableStateOf("Thank you for your participation. You will now receive your payment.") }
    var messageMediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    // Load facility config and survey
    LaunchedEffect(Unit) {
        facilityConfig = database.facilityConfigDao().getFacilityConfig()
        survey = database.surveyDao().getSurveyById(surveyId)

        // Calculate payment amount
        facilityConfig?.let { config ->
            if (config.subjectPaymentType != "None") {
                paymentAmount = config.participationPaymentAmount
            }
        }

        Log.d("SubjectPaymentScreen", "Payment config: type=${facilityConfig?.subjectPaymentType}, amount=$paymentAmount")

        // Load payment confirmation message from database
        scope.launch {
            val lang = survey?.language ?: "en"
            val systemMessage = database.systemMessageDao().getSystemMessage("payment_confirmation", lang)
                ?: database.systemMessageDao().getSystemMessage("payment_confirmation", "English")
                ?: database.systemMessageDao().getSystemMessageAnyLanguage("payment_confirmation")

            if (systemMessage != null) {
                paymentMessage = systemMessage.messageText
                Log.d("SubjectPaymentScreen", "Loaded payment message: ${systemMessage.messageText}")

                // Play audio if available
                if (!systemMessage.audioFileName.isNullOrEmpty()) {
                    Log.d("SubjectPaymentScreen", "Playing payment audio: ${systemMessage.audioFileName}")
                    try {
                        messageMediaPlayer = playAudio(context, systemMessage.audioFileName)
                    } catch (e: Exception) {
                        Log.e("SubjectPaymentScreen", "Error playing message audio", e)
                    }
                }
            } else {
                Log.d("SubjectPaymentScreen", "No payment_confirmation message found, using default")
            }
        }
    }

    // Play audio instruction if available
    LaunchedEffect(survey?.language) {
        survey?.let { s ->
            // TODO: Get payment audio file based on language from survey config
            // For now, using placeholder
            val audioFileName = when (s.language) {
                "es" -> "payment_instruction_es.mp3"
                else -> "payment_instruction_en.mp3"
            }

            try {
                currentMediaPlayer?.stop()
                currentMediaPlayer?.release()
                currentMediaPlayer = playAudio(context, audioFileName)
            } catch (e: Exception) {
                Log.e("SubjectPaymentScreen", "Error playing audio", e)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            currentMediaPlayer?.stop()
            currentMediaPlayer?.release()
            messageMediaPlayer?.let { player ->
                try {
                    if (player.isPlaying) {
                        player.stop()
                    }
                    player.release()
                    Log.d("SubjectPaymentScreen", "Message MediaPlayer released")
                } catch (e: Exception) {
                    Log.e("SubjectPaymentScreen", "Error releasing message MediaPlayer", e)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.payment_title)) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
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
            // Payment icon
            Icon(
                imageVector = Icons.Default.AttachMoney,
                contentDescription = stringResource(R.string.cd_payment),
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            // Thank you message
            Text(
                text = paymentMessage,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary
            )

            // Audio replay button (only show if audio is available)
            if (messageMediaPlayer != null) {
                OutlinedButton(
                    onClick = {
                        try {
                            messageMediaPlayer?.let { player ->
                                if (!player.isPlaying) {
                                    player.seekTo(0)
                                    player.start()
                                    Log.d("SubjectPaymentScreen", "Replaying payment audio")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("SubjectPaymentScreen", "Error replaying audio", e)
                        }
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.cd_replay),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.payment_replay_audio), fontSize = 14.sp)
                }
            }

            // Payment amount card
            if (paymentAmount > 0) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = getPaymentAmountLabel(survey?.language ?: "en"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )

                        Text(
                            text = "${facilityConfig?.paymentCurrencySymbol ?: "$"}${String.format("%.2f", paymentAmount)}",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = facilityConfig?.paymentCurrency ?: "USD",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Instructions
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFF3CD)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = Color(0xFF856404),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = getConfirmationInstruction(survey?.language ?: "en"),
                        color = Color(0xFF856404),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Upload message
            uploadMessage?.let { message ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (message.contains("success", true))
                            Color(0xFF4CAF50).copy(alpha = 0.1f)
                        else
                            MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = message,
                        color = if (message.contains("success", true))
                            Color(0xFF2E7D32)
                        else
                            MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Error message
            errorMessage?.let { message ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Confirm payment button
            // Get error and upload messages outside onClick lambda
            val deviceInitError = stringResource(R.string.fingerprint_device_init_failed)
            val captureFailedError = stringResource(R.string.fingerprint_capture_failed)
            val uploadingMsg = stringResource(R.string.payment_uploading)
            val uploadSuccessMsg = stringResource(R.string.payment_upload_success)
            val uploadFailedMsg = stringResource(R.string.payment_upload_failed)
            val uploadErrorMsg = stringResource(R.string.payment_upload_error)

            Button(
                onClick = {
                    scope.launch {
                        isCapturingFingerprint = true
                        errorMessage = null

                        // Get the subject's previously enrolled fingerprint
                        val subjectFingerprint = withContext(Dispatchers.IO) {
                            database.subjectFingerprintDao().getFingerprintBySurveyId(surveyId)
                        }

                        if (subjectFingerprint == null) {
                            Log.e("SubjectPaymentScreen", "No fingerprint found for survey $surveyId")
                            errorMessage = "No fingerprint enrollment found for this survey"
                            isCapturingFingerprint = false
                            return@launch
                        }

                        // Check for USB device and permission
                        val usbManager = context.getSystemService(android.content.Context.USB_SERVICE) as UsbManager
                        val deviceList = usbManager.deviceList
                        var secugenDevice: UsbDevice? = null

                        for ((_, device) in deviceList) {
                            if (device.vendorId == 0x1162) { // SecuGen vendor ID
                                secugenDevice = device
                                break
                            }
                        }

                        if (secugenDevice == null) {
                            errorMessage = "Fingerprint scanner not connected"
                            isCapturingFingerprint = false
                            return@launch
                        }

                        if (!usbManager.hasPermission(secugenDevice)) {
                            Log.i("SubjectPaymentScreen", "Requesting USB permission")
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
                            errorMessage = "Please grant USB permission and try again"
                            isCapturingFingerprint = false
                            return@launch
                        }

                        // Create fingerprint implementation
                        val fingerprintImpl: IFingerprintCapture = if (EmulatorDetector.isEmulator()) {
                            Log.i("SubjectPaymentScreen", "Using mock fingerprint implementation for emulator")
                            MockFingerprintImpl()
                        } else {
                            try {
                                Log.i("SubjectPaymentScreen", "Using SecuGen fingerprint implementation")
                                SecuGenFingerprintImpl(context)
                            } catch (e: Exception) {
                                Log.e("SubjectPaymentScreen", "Failed to create SecuGen implementation, using mock", e)
                                MockFingerprintImpl()
                            }
                        }

                        // Initialize fingerprint device
                        if (!fingerprintImpl.initializeDevice()) {
                            errorMessage = deviceInitError
                            isCapturingFingerprint = false
                            return@launch
                        }

                        // Capture fingerprint
                        val capturedTemplate = fingerprintImpl.captureFingerprint()

                        if (capturedTemplate == null) {
                            errorMessage = captureFailedError
                            isCapturingFingerprint = false
                            fingerprintImpl.closeDevice()
                            return@launch
                        }

                        // Match against the subject's original fingerprint
                        val isMatch = fingerprintImpl.matchTemplates(
                            capturedTemplate,
                            subjectFingerprint.fingerprintTemplate
                        )

                        fingerprintImpl.closeDevice()

                        if (!isMatch) {
                            errorMessage = "Fingerprint does not match. Please try again."
                            isCapturingFingerprint = false
                            Log.w("SubjectPaymentScreen", "Fingerprint mismatch for survey $surveyId")
                            return@launch
                        }

                        Log.i("SubjectPaymentScreen", "Fingerprint verified successfully for survey $surveyId")

                        // Update survey with payment confirmation
                        survey?.let { s ->
                            val updatedSurvey = s.copy(
                                paymentConfirmed = true,
                                paymentAmount = paymentAmount,
                                paymentType = facilityConfig?.subjectPaymentType ?: "Cash",
                                paymentDate = System.currentTimeMillis()
                            )
                            database.surveyDao().updateSurvey(updatedSurvey)
                            Log.i("SubjectPaymentScreen", "Payment confirmed for survey $surveyId: amount=$paymentAmount")
                        }

                        isCapturingFingerprint = false

                        // Upload survey after payment confirmation
                        isUploading = true
                        uploadMessage = uploadingMsg
                        try {
                            Log.i("SubjectPaymentScreen", "Starting upload for survey: $surveyId")
                            val uploadManager = SurveyUploadManager(context, database)
                            val uploadResult = uploadManager.uploadSurvey(surveyId)

                            when (uploadResult) {
                                is UploadResult.Success -> {
                                    Log.i("SubjectPaymentScreen", "Survey uploaded successfully: $surveyId")
                                    uploadMessage = uploadSuccessMsg
                                }
                                else -> {
                                    Log.e("SubjectPaymentScreen", "Upload failed: $uploadResult")
                                    uploadMessage = uploadFailedMsg
                                    // Schedule retry if failed
                                    val uploadWorkManager = SurveyUploadWorkManager(context)
                                    uploadWorkManager.scheduleImmediateRetry(surveyId)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("SubjectPaymentScreen", "Error uploading survey", e)
                            uploadMessage = uploadErrorMsg
                        } finally {
                            isUploading = false
                            // Navigate to menu after a short delay to show the message
                            delay(1500)
                            navController.navigate(AppDestinations.MENU) {
                                popUpTo(AppDestinations.SURVEY) { inclusive = true }
                                popUpTo(AppDestinations.SUBJECT_PAYMENT) { inclusive = true }
                            }
                        }
                    }
                },
                enabled = !isCapturingFingerprint && !isUploading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (isCapturingFingerprint || isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null
                        )
                        Text(
                            text = stringResource(R.string.payment_confirm_received),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            // Skip button if payment is None
            if (facilityConfig?.subjectPaymentType == "None") {
                // Get upload messages outside onClick lambda
                val skipUploadingMsg = stringResource(R.string.payment_uploading)
                val skipUploadSuccessMsg = stringResource(R.string.payment_upload_success)
                val skipUploadFailedMsg = stringResource(R.string.payment_upload_failed)
                val skipUploadErrorMsg = stringResource(R.string.payment_upload_error)

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            // Upload survey without payment confirmation
                            isUploading = true
                            uploadMessage = skipUploadingMsg
                            try {
                                Log.i("SubjectPaymentScreen", "Starting upload for survey: $surveyId")
                                val uploadManager = SurveyUploadManager(context, database)
                                val uploadResult = uploadManager.uploadSurvey(surveyId)

                                when (uploadResult) {
                                    is UploadResult.Success -> {
                                        Log.i("SubjectPaymentScreen", "Survey uploaded successfully: $surveyId")
                                        uploadMessage = skipUploadSuccessMsg
                                    }
                                    else -> {
                                        Log.e("SubjectPaymentScreen", "Upload failed: $uploadResult")
                                        uploadMessage = skipUploadFailedMsg
                                        // Schedule retry if failed
                                        val uploadWorkManager = SurveyUploadWorkManager(context)
                                        uploadWorkManager.scheduleImmediateRetry(surveyId)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("SubjectPaymentScreen", "Error uploading survey", e)
                                uploadMessage = skipUploadErrorMsg
                            } finally {
                                isUploading = false
                                // Navigate to menu after a short delay to show the message
                                delay(1500)
                                navController.navigate(AppDestinations.MENU) {
                                    popUpTo(AppDestinations.SURVEY) { inclusive = true }
                                    popUpTo(AppDestinations.SUBJECT_PAYMENT) { inclusive = true }
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        text = stringResource(R.string.payment_continue_without),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

private fun getPaymentAmountLabel(language: String): String {
    return when (language) {
        "es" -> "Monto del Pago"
        "fr" -> "Montant du Paiement"
        else -> "Payment Amount"
    }
}

private fun getConfirmationInstruction(language: String): String {
    return when (language) {
        "es" -> "Por favor confirme que ha recibido el pago usando su huella digital"
        "fr" -> "Veuillez confirmer que vous avez reçu le paiement en utilisant votre empreinte digitale"
        else -> "Please confirm that you have received the payment using your fingerprint"
    }
}