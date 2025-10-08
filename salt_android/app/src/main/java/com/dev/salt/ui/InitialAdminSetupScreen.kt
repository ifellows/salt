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
import androidx.compose.ui.res.stringResource
import com.dev.salt.R

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
    val context = androidx.compose.ui.platform.LocalContext.current
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
            usernameError = context.getString(R.string.error_username_required)
            isValid = false
        } else if (username.length < 3) {
            usernameError = context.getString(R.string.error_username_too_short)
            isValid = false
        } else {
            usernameError = null
        }

        // Full name validation
        if (fullName.isBlank()) {
            fullNameError = context.getString(R.string.error_fullname_required)
            isValid = false
        } else {
            fullNameError = null
        }

        // Password validation
        if (password.isBlank()) {
            passwordError = context.getString(R.string.error_password_required)
            isValid = false
        } else if (password.length < 3) {
            passwordError = context.getString(R.string.error_password_too_short)
            isValid = false
        } else {
            passwordError = null
        }

        // Confirm password validation
        if (confirmPassword.isBlank()) {
            confirmPasswordError = context.getString(R.string.error_confirm_password_required)
            isValid = false
        } else if (password != confirmPassword) {
            confirmPasswordError = context.getString(R.string.error_passwords_no_match)
            isValid = false
        } else {
            confirmPasswordError = null
        }

        return isValid
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setup_admin_title)) },
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
                    text = stringResource(R.string.setup_admin_header),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.setup_admin_description),
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
                        label = { Text(stringResource(R.string.label_username)) },
                        placeholder = { Text(stringResource(R.string.placeholder_admin_username)) },
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
                        label = { Text(stringResource(R.string.label_full_name)) },
                        placeholder = { Text(stringResource(R.string.placeholder_administrator)) },
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
                        label = { Text(stringResource(R.string.label_password)) },
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
                                    contentDescription = if (passwordVisible) stringResource(R.string.cd_hide_password) else stringResource(R.string.cd_show_password)
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
                        label = { Text(stringResource(R.string.label_confirm_password)) },
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
                                    contentDescription = if (confirmPasswordVisible) stringResource(R.string.cd_hide_password) else stringResource(R.string.cd_show_password)
                                )
                            }
                        }
                    )

                    // Info text
                    Text(
                        text = stringResource(R.string.info_admin_requirements),
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
                Text(stringResource(R.string.button_next_fingerprint))
            }
        }
    }
}
