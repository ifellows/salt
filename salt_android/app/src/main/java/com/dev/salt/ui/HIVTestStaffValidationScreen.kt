package com.dev.salt.ui

import android.media.MediaPlayer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dev.salt.AppDestinations
import com.dev.salt.R
import com.dev.salt.data.SurveyDatabase
import kotlinx.coroutines.launch
import android.util.Log
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HIVTestStaffValidationScreen(
    navController: NavController,
    surveyId: String
) {
    val context = LocalContext.current
    val database = remember { SurveyDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    // Media player for audio message
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    // Message states
    var messageTitle by remember { mutableStateOf("") }
    var messageBody by remember { mutableStateOf("") }
    var audioFilePath by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Load the staff validation message for HIV testing
    LaunchedEffect(surveyId) {
        scope.launch {
            try {
                // Get the survey to determine language
                val survey = database.surveyDao().getSurveyById(surveyId)
                val surveyLanguage = survey?.language ?: "English"

                Log.d("HIVTestStaffValidation", "Loading message for survey: $surveyId, language: $surveyLanguage")

                // Try to get the staff validation message
                val message = database.systemMessageDao().getSystemMessage("staff_validation", surveyLanguage)
                    ?: database.systemMessageDao().getSystemMessage("staff_validation", "en")
                    ?: database.systemMessageDao().getSystemMessage("staff_validation", "English")
                    ?: database.systemMessageDao().getSystemMessageAnyLanguage("staff_validation")

                if (message != null) {
                    messageTitle = "Staff Validation Required"
                    messageBody = message.messageText
                    audioFilePath = message.audioFileName
                    Log.d("HIVTestStaffValidation", "Loaded message: $messageTitle")
                } else {
                    // Fallback to default message
                    messageTitle = "Staff Validation Required"
                    messageBody = "Please verify that you are authorized staff before proceeding."
                    Log.d("HIVTestStaffValidation", "Using fallback message")
                }

                isLoading = false
            } catch (e: Exception) {
                Log.e("HIVTestStaffValidation", "Error loading message", e)
                messageTitle = "Staff Validation Required"
                messageBody = "Please verify that you are authorized staff before proceeding."
                isLoading = false
            }
        }
    }

    // Auto-play audio when available
    LaunchedEffect(audioFilePath) {
        audioFilePath?.let { path ->
            try {
                val file = File(context.filesDir, path)
                if (file.exists()) {
                    mediaPlayer?.release()
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(file.absolutePath)
                        prepare()
                        start()
                    }
                    Log.d("HIVTestStaffValidation", "Playing audio: ${file.absolutePath}")
                } else {
                    Log.w("HIVTestStaffValidation", "Audio file not found: ${file.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e("HIVTestStaffValidation", "Error playing audio", e)
            }
        }
    }

    // Clean up media player
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.staff_validation_title)) },
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
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Verified User icon
            Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = stringResource(R.string.cd_verified_user),
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isLoading) {
                CircularProgressIndicator()
            } else {
                // Message content
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = messageTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = messageBody,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Replay audio button if available
                    if (audioFilePath != null) {
                        Spacer(modifier = Modifier.height(24.dp))

                        OutlinedButton(
                            onClick = {
                                audioFilePath?.let { path ->
                                    try {
                                        val file = File(context.filesDir, path)
                                        if (file.exists()) {
                                            mediaPlayer?.release()
                                            mediaPlayer = MediaPlayer().apply {
                                                setDataSource(file.absolutePath)
                                                prepare()
                                                start()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("HIVTestStaffValidation", "Error replaying audio", e)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(0.6f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay,
                                contentDescription = stringResource(R.string.cd_replay),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.replay_audio))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Continue button
            Button(
                onClick = {
                    // Navigate to HIV Test Instruction screen
                    navController.navigate("${AppDestinations.HIV_TEST_INSTRUCTION}/$surveyId") {
                        popUpTo("${AppDestinations.HIV_TEST_STAFF_VALIDATION}/$surveyId") { inclusive = true }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = stringResource(R.string.common_next),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}