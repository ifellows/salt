package com.dev.salt

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
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
import com.dev.salt.auth.BiometricResult
import com.dev.salt.data.SurveyDatabase
import kotlinx.coroutines.launch

@Composable
fun StaffValidationScreen(
    onValidationSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { SurveyDatabase.getInstance(context) }
    val biometricAuthManager = remember {
        BiometricAuthManagerFactory.create(context, database.userDao())
    }

    var showPasswordDialog by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var attemptsRemaining by remember { mutableIntStateOf(10) }

    // For now, use a default message. In production, this would come from survey config
    val validationMessage = "Please hand the tablet back to the staff member who gave it to you"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Title
                Text(
                    text = "Staff Validation Required",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1976D2),
                    textAlign = TextAlign.Center
                )

                // Message
                Text(
                    text = validationMessage,
                    fontSize = 18.sp,
                    color = Color.DarkGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                // Error message
                errorMessage?.let { error ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                    ) {
                        Text(
                            text = error,
                            color = Color(0xFFC62828),
                            modifier = Modifier.padding(12.dp),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Attempts remaining
                if (attemptsRemaining < 10) {
                    Text(
                        text = "Attempts remaining: $attemptsRemaining",
                        fontSize = 14.sp,
                        color = if (attemptsRemaining <= 3) Color.Red else Color.Gray
                    )
                }

                // Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Fingerprint button
                    Button(
                        onClick = {
                            scope.launch {
                                errorMessage = null
                                // Try biometric auth for any staff user
                                val staffUsers = database.userDao().getAllUsers()
                                    .filter { it.role == "SURVEY_STAFF" || it.role == "ADMINISTRATOR" }

                                if (staffUsers.isEmpty()) {
                                    errorMessage = "No staff users configured. Please use username and password."
                                    showPasswordDialog = true
                                } else {
                                    // Use mock biometric prompt which auto-succeeds for testing
                                    biometricAuthManager.showBiometricPrompt(
                                        title = "Staff Authentication",
                                        subtitle = "Verify your identity"
                                    ) { result ->
                                        when (result) {
                                            is BiometricResult.Success -> {
                                                // In production, verify against specific staff fingerprint
                                                // For now, accept any successful biometric auth
                                                onValidationSuccess()
                                            }
                                            is BiometricResult.Error -> {
                                                attemptsRemaining--
                                                errorMessage = if (attemptsRemaining <= 0) {
                                                    "Maximum attempts exceeded. Please restart the application."
                                                } else {
                                                    "Authentication failed: ${result.message}"
                                                }
                                            }
                                            else -> {
                                                errorMessage = "Authentication cancelled"
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = attemptsRemaining > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1976D2)
                        )
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Fingerprint,
                            contentDescription = "Fingerprint",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Authenticate with Fingerprint", fontSize = 16.sp)
                    }

                    // Password button
                    Button(
                        onClick = { showPasswordDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = attemptsRemaining > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF757575)
                        )
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Lock,
                            contentDescription = "Password",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Use Username & Password", fontSize = 16.sp)
                    }

                    // Cancel button
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel", fontSize = 16.sp)
                    }
                }
            }
        }
    }

    // Password dialog
    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("Staff Login") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true
                    )

                    // Show hint for testing
                    Text(
                        text = "Test credentials: admin/123 or staff/123",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )

                    errorMessage?.let { error ->
                        Text(
                            text = error,
                            color = Color.Red,
                            fontSize = 14.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            errorMessage = null
                            // Validate against database users
                            val user = database.userDao().getUserByUserName(username)

                            if (user != null &&
                                (user.role == "SURVEY_STAFF" || user.role == "ADMINISTRATOR") &&
                                PasswordUtils.verifyPassword(password, user.hashedPassword)) {
                                showPasswordDialog = false
                                onValidationSuccess()
                            } else {
                                attemptsRemaining--
                                if (attemptsRemaining <= 0) {
                                    errorMessage = "Maximum attempts exceeded. Please restart the application."
                                    showPasswordDialog = false
                                } else {
                                    errorMessage = "Invalid username or password"
                                }
                                password = ""
                            }
                        }
                    },
                    enabled = username.isNotBlank() && password.isNotBlank() && attemptsRemaining > 0
                ) {
                    Text("Login")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}