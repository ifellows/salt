package com.dev.salt.ui

import android.graphics.Bitmap
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dev.salt.AppDestinations
import com.dev.salt.data.SurveyDatabase
import androidx.compose.ui.res.stringResource
import com.dev.salt.R
import io.github.joelkanyi.sain.Sain
import io.github.joelkanyi.sain.SignatureAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsentSignatureScreen(
    navController: NavController,
    surveyId: String,
    coupons: String,
    returnTo: String = "survey_start"  // "survey_start" or "survey" (return to survey after staff eligibility)
) {
    val context = LocalContext.current
    val database = remember { SurveyDatabase.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    var consentText by remember { mutableStateOf<String?>(null) }
    var audioFileName by remember { mutableStateOf<String?>(null) }
    var surveyLanguage by remember { mutableStateOf("en") }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasDrawnSignature by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var signatureBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var signatureAction by remember { mutableStateOf<((SignatureAction) -> Unit)?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    // Load consent message
    LaunchedEffect(Unit) {
        try {
            withContext(Dispatchers.IO) {
                // Get survey language
                val survey = database.surveyDao().getSurveyById(surveyId)
                surveyLanguage = survey?.language ?: "en"
                Log.d("ConsentSignature", "Survey language: $surveyLanguage")

                // Try to load consent message with language fallback
                var message = database.systemMessageDao().getSystemMessage(
                    "consent_agreement",
                    surveyLanguage
                )

                // Fallback chain: survey language -> "en" -> any available
                if (message == null) {
                    Log.d("ConsentSignature", "No message for language $surveyLanguage, trying 'en'")
                    message = database.systemMessageDao().getSystemMessage(
                        "consent_agreement",
                        "en"
                    )
                }

                if (message == null) {
                    Log.d("ConsentSignature", "No message for 'en', getting any available")
                    val allMessages = database.systemMessageDao().getAllMessagesForKey("consent_agreement")
                    message = allMessages.firstOrNull()
                }

                if (message != null) {
                    consentText = message.messageText
                    audioFileName = message.audioFileName
                    Log.d("ConsentSignature", "Loaded consent: ${message.messageText.take(50)}...")

                    // Auto-play audio if available
                    if (!audioFileName.isNullOrEmpty()) {
                        try {
                            val audioFile = File(context.filesDir, "audio/$audioFileName")
                            if (audioFile.exists()) {
                                mediaPlayer = MediaPlayer().apply {
                                    setDataSource(audioFile.absolutePath)
                                    prepare()
                                    start()
                                }
                                Log.d("ConsentSignature", "Started audio playback: $audioFileName")
                            }
                        } catch (e: Exception) {
                            Log.e("ConsentSignature", "Error playing audio", e)
                        }
                    }
                } else {
                    errorMessage = "No consent agreement configured. Please contact administrator."
                    Log.e("ConsentSignature", "No consent message found in database")
                }
            }
        } catch (e: Exception) {
            Log.e("ConsentSignature", "Error loading consent", e)
            errorMessage = "Error loading consent: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    // Cleanup media player
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    // Track signature changes
    LaunchedEffect(signatureBitmap) {
        hasDrawnSignature = signatureBitmap != null
        Log.d("ConsentSignature", "Signature bitmap updated: ${signatureBitmap != null}")
    }

    // Periodically check if signature has been drawn
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500) // Check every 500ms
            signatureAction?.invoke(SignatureAction.COMPLETE)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.consent_title)) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = errorMessage ?: "Unknown error",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { navController.popBackStack() }) {
                        Text(stringResource(R.string.consent_go_back))
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Consent text card (scrollable)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.consent_read_instruction),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Audio replay button
                        if (!audioFileName.isNullOrEmpty()) {
                            IconButton(
                                onClick = {
                                    try {
                                        mediaPlayer?.release()
                                        val audioFile = File(context.filesDir, "audio/$audioFileName")
                                        if (audioFile.exists()) {
                                            mediaPlayer = MediaPlayer().apply {
                                                setDataSource(audioFile.absolutePath)
                                                prepare()
                                                start()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("ConsentSignature", "Error replaying audio", e)
                                    }
                                },
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Replay,
                                        contentDescription = stringResource(R.string.cd_replay_audio)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.consent_replay_audio), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        // Scrollable consent text
                        Text(
                            text = consentText ?: stringResource(R.string.consent_text_unavailable),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }

                // Signature instructions
                Text(
                    text = stringResource(R.string.consent_sign_instruction),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Signature pad
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .border(2.dp, MaterialTheme.colorScheme.outline)
                        .padding(8.dp)
                ) {
                    Sain(
                        modifier = Modifier.fillMaxSize(),
                        signaturePadColor = MaterialTheme.colorScheme.surface,
                        signatureColor = MaterialTheme.colorScheme.onSurface,
                        onComplete = { bitmap ->
                            signatureBitmap = bitmap
                            Log.d("ConsentSignature", "onComplete called with bitmap: ${bitmap != null}")
                        }
                    ) { action ->
                        signatureAction = action
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Clear button
                    OutlinedButton(
                        onClick = {
                            signatureAction?.invoke(SignatureAction.CLEAR)
                            signatureBitmap = null
                            hasDrawnSignature = false
                        },
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(R.string.cd_clear_signature),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.common_clear))
                    }

                    // I Agree button
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isSaving = true
                                try {
                                    // Convert signature to hexadecimal string
                                    val bitmap = signatureBitmap?.asAndroidBitmap()
                                    if (bitmap != null) {
                                        val couponCode = withContext(Dispatchers.IO) {
                                            // Convert bitmap to PNG bytes
                                            val outputStream = ByteArrayOutputStream()
                                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                                            val pngBytes = outputStream.toByteArray()

                                            // Convert bytes to hexadecimal string
                                            val hexString = pngBytes.joinToString("") { "%02x".format(it) }

                                            // Update survey with signature hex string
                                            val survey = database.surveyDao().getSurveyById(surveyId)
                                            survey?.let {
                                                it.consentSignaturePath = hexString
                                                database.surveyDao().updateSurvey(it)
                                            }

                                            Log.d("ConsentSignature", "Signature saved as hex (${hexString.length} chars)")

                                            // Get coupon code from survey for survey screen
                                            survey?.referralCouponCode ?: ""
                                        }

                                        // Navigate based on returnTo parameter
                                        when (returnTo) {
                                            "survey" -> {
                                                // Staff screening: return to survey to continue after eligibility
                                                Log.d("ConsentSignature", "Staff screening mode - returning to survey")
                                                navController.navigate("${AppDestinations.SURVEY_SCREEN}?couponCode=$couponCode") {
                                                    // Pop consent screens and return to survey
                                                    popUpTo(AppDestinations.CONSENT_INSTRUCTION) { inclusive = true }
                                                }
                                            }
                                            else -> {
                                                // Self-screening mode (default): go to survey start instruction
                                                Log.d("ConsentSignature", "Self-screening mode - going to survey start instruction")
                                                navController.navigate("${AppDestinations.SURVEY_START_INSTRUCTION}?surveyId=$surveyId&couponCode=$couponCode") {
                                                    popUpTo(AppDestinations.CONSENT_SIGNATURE) { inclusive = true }
                                                }
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("ConsentSignature", "Error saving signature", e)
                                    errorMessage = "Error saving signature: ${e.message}"
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        enabled = hasDrawnSignature && !isSaving,
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(stringResource(R.string.consent_i_agree))
                        }
                    }
                }
            }
        }
    }
}
