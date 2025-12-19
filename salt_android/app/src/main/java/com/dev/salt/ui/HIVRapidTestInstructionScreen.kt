package com.dev.salt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dev.salt.auth.BiometricAuthManager
import com.dev.salt.auth.BiometricAuthManagerFactory
import android.util.Log
import com.dev.salt.auth.BiometricResult
import com.dev.salt.data.SurveyDatabase
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import android.media.MediaPlayer
import androidx.compose.runtime.DisposableEffect
import com.dev.salt.PasswordUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.dev.salt.fingerprint.IFingerprintCapture
import com.dev.salt.fingerprint.SecuGenFingerprintImpl
import com.dev.salt.fingerprint.MockFingerprintImpl
import com.dev.salt.util.EmulatorDetector
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler

@Composable
fun HIVRapidTestInstructionScreen(
    surveyId: String,
    onStaffValidated: () -> Unit,
    onCancel: () -> Unit
) {
    // Disable hardware back button during survey flow
    BackHandler(enabled = true) {
        // Intentionally empty - back button is disabled during survey flow
    }

    val context = LocalContext.current
    val database = remember { SurveyDatabase.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    var password by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showPasswordField by remember { mutableStateOf(false) }

    // Media player for instructions
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var surveyLanguage by remember { mutableStateOf("en") }
    var message by remember { mutableStateOf<Pair<String, String?>?>(null) }

    // Load survey language and message
    LaunchedEffect(surveyId) {
        withContext(Dispatchers.IO) {
            val survey = database.surveyDao().getSurveyById(surveyId)
            survey?.let {
                surveyLanguage = it.language
                Log.d("HIVTestInstruction", "Survey language: $surveyLanguage")

                // Load HIV test instruction message
                val systemMessage = database.systemMessageDao().getSystemMessage("hiv_rapid_test_instruction", surveyLanguage)
                    ?: database.systemMessageDao().getSystemMessage("hiv_rapid_test_instruction", "en")
                    ?: database.systemMessageDao().getSystemMessage("hiv_rapid_test_instruction", "English")
                    ?: database.systemMessageDao().getSystemMessageAnyLanguage("hiv_rapid_test_instruction")

                systemMessage?.let { msg ->
                    message = Pair(msg.messageText, msg.audioFileName)
                    Log.d("HIVTestInstruction", "Loaded message: ${msg.messageText}")

                    // Play audio if available
                    msg.audioFileName?.let { audioFile ->
                        try {
                            val audioPath = context.filesDir.resolve("audio/${msg.messageKey}_${msg.language}_${audioFile}")
                            if (audioPath.exists()) {
                                val mp = MediaPlayer().apply {
                                    setDataSource(audioPath.absolutePath)
                                    prepare()
                                    start()
                                }
                                mediaPlayer = mp
                                Log.d("HIVTestInstruction", "Playing audio: ${audioPath.absolutePath}")
                            } else {
                                Log.w("HIVTestInstruction", "Audio file not found: ${audioPath.absolutePath}")
                            }
                        } catch (e: Exception) {
                            Log.e("HIVTestInstruction", "Error playing audio", e)
                        }
                    }
                } ?: run {
                    // Default message if not found in database
                    message = Pair("Please perform an HIV rapid test for this participant. Once the test is complete, authenticate as staff to continue.", null)
                }
            }
        }
    }

    // Clean up media player
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        }
    }

    // Fingerprint authentication
    val biometricAuthManager = remember { BiometricAuthManagerFactory.create(context, database.userDao()) }

    val handleFingerprintAuth = {
        coroutineScope.launch {
            isProcessing = true
            errorMessage = null

            // Try to authenticate any enrolled staff user
            val users = withContext(Dispatchers.IO) {
                database.userDao().getAllUsers()
            }

            var authenticated = false
            for (user in users) {
                if ((user.role == "SURVEY_STAFF" || user.role == "ADMINISTRATOR") && user.biometricEnabled) {
                    biometricAuthManager.authenticateUserBiometric(user.userName) { result ->
                        if (result is BiometricResult.Success) {
                            authenticated = true
                            Log.d("HIVTestInstruction", "Staff validated via fingerprint: ${user.userName}")
                            onStaffValidated()
                        }
                    }
                    if (authenticated) break
                }
            }

            if (!authenticated) {
                errorMessage = "Fingerprint not recognized as staff"
                showPasswordField = true
            }
            isProcessing = false
        }
    }

    val handlePasswordValidation = {
        coroutineScope.launch {
            isProcessing = true
            errorMessage = null

            // Validate password
            val isValid = withContext(Dispatchers.IO) {
                val users = database.userDao().getAllUsers()
                users.any { user ->
                    (user.role == "SURVEY_STAFF" || user.role == "ADMINISTRATOR") &&
                    PasswordUtils.verifyPassword(password, user.hashedPassword)
                }
            }

            if (isValid) {
                Log.d("HIVTestInstruction", "Staff validated via password")
                onStaffValidated()
            } else {
                errorMessage = "Invalid password"
            }
            isProcessing = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Title
                    Text(
                        text = "HIV Rapid Test Required",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Instructions
                    message?.let { (text, audio) ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            // Replay button if audio exists
                            if (audio != null) {
                                TextButton(
                                    onClick = {
                                        mediaPlayer?.apply {
                                            if (isPlaying) stop()
                                            release()
                                        }

                                        // Replay audio
                                        coroutineScope.launch(Dispatchers.IO) {
                                            try {
                                                val audioPath = context.filesDir.resolve("audio/hiv_rapid_test_instruction_${surveyLanguage}_${audio}")
                                                if (audioPath.exists()) {
                                                    val mp = MediaPlayer().apply {
                                                        setDataSource(audioPath.absolutePath)
                                                        prepare()
                                                        start()
                                                    }
                                                    mediaPlayer = mp
                                                }
                                            } catch (e: Exception) {
                                                Log.e("HIVTestInstruction", "Error replaying audio", e)
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "Replay")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Replay Instructions")
                                }
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 16.dp))

                    // Staff authentication section
                    Text(
                        text = "Staff Authentication Required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = "After performing the test, authenticate to continue",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    if (!showPasswordField) {
                        // Fingerprint authentication button
                        Button(
                            onClick = { handleFingerprintAuth() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            enabled = !isProcessing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Fingerprint,
                                    contentDescription = "Fingerprint",
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Authenticate with Fingerprint")
                            }
                        }

                        TextButton(
                            onClick = { showPasswordField = true },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Use Password Instead")
                        }
                    } else {
                        // Password authentication
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Staff Password") },
                            leadingIcon = {
                                Icon(Icons.Filled.Lock, contentDescription = "Password")
                            },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        )

                        Button(
                            onClick = { handlePasswordValidation() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            enabled = !isProcessing && password.isNotEmpty()
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("Authenticate")
                            }
                        }

                        TextButton(
                            onClick = { showPasswordField = false },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Use Fingerprint Instead")
                        }
                    }

                    // Error message
                    errorMessage?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    // Cancel button
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}