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
import com.dev.salt.fingerprint.FingerprintManager
import com.dev.salt.upload.SurveyUploadManager
import com.dev.salt.upload.SurveyUploadWorkManager
import com.dev.salt.upload.UploadResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

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
    val fingerprintManager = remember { FingerprintManager(database.subjectFingerprintDao()) }

    var facilityConfig by remember { mutableStateOf<com.dev.salt.data.FacilityConfig?>(null) }
    var survey by remember { mutableStateOf<com.dev.salt.data.Survey?>(null) }
    var paymentAmount by remember { mutableStateOf(0.0) }
    var isCapturingFingerprint by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentMediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadMessage by remember { mutableStateOf<String?>(null) }

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
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Payment Confirmation") },
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
                contentDescription = "Payment",
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            // Thank you message
            Text(
                text = getPaymentMessage(survey?.language ?: "en"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary
            )

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
            Button(
                onClick = {
                    scope.launch {
                        isCapturingFingerprint = true
                        errorMessage = null

                        // Initialize fingerprint device
                        if (!fingerprintManager.initializeDevice()) {
                            errorMessage = "Failed to initialize fingerprint device"
                            isCapturingFingerprint = false
                            return@launch
                        }

                        // Capture fingerprint
                        val fingerprintHash = fingerprintManager.captureFingerprint()

                        if (fingerprintHash == null) {
                            errorMessage = "Failed to capture fingerprint. Please try again."
                            isCapturingFingerprint = false
                            fingerprintManager.closeDevice()
                            return@launch
                        }

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

                        fingerprintManager.closeDevice()
                        isCapturingFingerprint = false

                        // Upload survey after payment confirmation
                        isUploading = true
                        uploadMessage = "Uploading survey..."
                        try {
                            Log.i("SubjectPaymentScreen", "Starting upload for survey: $surveyId")
                            val uploadManager = SurveyUploadManager(context, database)
                            val uploadResult = uploadManager.uploadSurvey(surveyId)

                            when (uploadResult) {
                                is UploadResult.Success -> {
                                    Log.i("SubjectPaymentScreen", "Survey uploaded successfully: $surveyId")
                                    uploadMessage = "Survey uploaded successfully"
                                }
                                else -> {
                                    Log.e("SubjectPaymentScreen", "Upload failed: $uploadResult")
                                    uploadMessage = "Upload failed - will retry in background"
                                    // Schedule retry if failed
                                    val uploadWorkManager = SurveyUploadWorkManager(context)
                                    uploadWorkManager.scheduleImmediateRetry(surveyId)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("SubjectPaymentScreen", "Error uploading survey", e)
                            uploadMessage = "Error during upload - will retry in background"
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
                            text = "Confirm Payment Received",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            // Skip button if payment is None
            if (facilityConfig?.subjectPaymentType == "None") {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            // Upload survey without payment confirmation
                            isUploading = true
                            uploadMessage = "Uploading survey..."
                            try {
                                Log.i("SubjectPaymentScreen", "Starting upload for survey: $surveyId")
                                val uploadManager = SurveyUploadManager(context, database)
                                val uploadResult = uploadManager.uploadSurvey(surveyId)

                                when (uploadResult) {
                                    is UploadResult.Success -> {
                                        Log.i("SubjectPaymentScreen", "Survey uploaded successfully: $surveyId")
                                        uploadMessage = "Survey uploaded successfully"
                                    }
                                    else -> {
                                        Log.e("SubjectPaymentScreen", "Upload failed: $uploadResult")
                                        uploadMessage = "Upload failed - will retry in background"
                                        // Schedule retry if failed
                                        val uploadWorkManager = SurveyUploadWorkManager(context)
                                        uploadWorkManager.scheduleImmediateRetry(surveyId)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("SubjectPaymentScreen", "Error uploading survey", e)
                                uploadMessage = "Error during upload - will retry in background"
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
                        text = "Continue Without Payment",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

// Helper functions for multilingual support
private fun getPaymentMessage(language: String): String {
    return when (language) {
        "es" -> "Gracias por su participación. Ahora se le pagará el monto a continuación:"
        "fr" -> "Merci pour votre participation. Vous allez maintenant recevoir le montant ci-dessous:"
        else -> "Thank you for your participation. You will now be paid the amount below:"
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