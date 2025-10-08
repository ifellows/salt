package com.dev.salt.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Initial admin user setup screen shown during first-time setup wizard.
 *
 * Collects admin user credentials (username, full name, password) before
 * proceeding to fingerprint enrollment. This is the second step in the
 * setup wizard after server configuration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InitialAdminSetupScreen(
    onAdminInfoCollected: (username: String, fullName: String, password: String) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    // Validation errors
    var usernameError by remember { mutableStateOf<String?>(null) }
    var fullNameError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }

    fun validate(): Boolean {
        var isValid = true

        // Username validation
        if (username.isBlank()) {
            usernameError = "Username is required"
            isValid = false
        } else if (username.length < 3) {
            usernameError = "Username must be at least 3 characters"
            isValid = false
        } else {
            usernameError = null
        }

        // Full name validation
        if (fullName.isBlank()) {
            fullNameError = "Full name is required"
            isValid = false
        } else {
            fullNameError = null
        }

        // Password validation
        if (password.isBlank()) {
            passwordError = "Password is required"
            isValid = false
        } else if (password.length < 3) {
            passwordError = "Password must be at least 3 characters"
            isValid = false
        } else {
            passwordError = null
        }

        // Confirm password validation
        if (confirmPassword.isBlank()) {
            confirmPasswordError = "Please confirm password"
            isValid = false
        } else if (password != confirmPassword) {
            confirmPasswordError = "Passwords do not match"
            isValid = false
        } else {
            confirmPasswordError = null
        }

        return isValid
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SALT Setup - Create Admin Account") },
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
                    text = "Create Administrator Account",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "This account will have full administrative access to the SALT system",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Form Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Username field
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            usernameError = null
                        },
                        label = { Text("Username") },
                        placeholder = { Text("admin") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = usernameError != null,
                        supportingText = {
                            if (usernameError != null) {
                                Text(usernameError!!)
                            }
                        }
                    )

                    // Full name field
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = {
                            fullName = it
                            fullNameError = null
                        },
                        label = { Text("Full Name") },
                        placeholder = { Text("Administrator") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = fullNameError != null,
                        supportingText = {
                            if (fullNameError != null) {
                                Text(fullNameError!!)
                            }
                        }
                    )

                    // Password field
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            passwordError = null
                        },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = passwordError != null,
                        supportingText = {
                            if (passwordError != null) {
                                Text(passwordError!!)
                            }
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        }
                    )

                    // Confirm password field
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            confirmPasswordError = null
                        },
                        label = { Text("Confirm Password") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = confirmPasswordError != null,
                        supportingText = {
                            if (confirmPasswordError != null) {
                                Text(confirmPasswordError!!)
                            }
                        },
                        trailingIcon = {
                            IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                Icon(
                                    imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password"
                                )
                            }
                        }
                    )

                    // Info text
                    Text(
                        text = "• Username must be at least 3 characters\n" +
                                "• Password must be at least 3 characters\n" +
                                "• Fingerprint enrollment will be required in the next step",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Next button
            Button(
                onClick = {
                    if (validate()) {
                        onAdminInfoCollected(username, fullName, password)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = username.isNotBlank() && fullName.isNotBlank() &&
                          password.isNotBlank() && confirmPassword.isNotBlank()
            ) {
                Text("Next: Fingerprint Enrollment")
            }
        }
    }
}
