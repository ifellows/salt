package com.dev.salt

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.dev.salt.sync.SurveySyncManager
import com.dev.salt.sync.SurveyCheckResult
import com.dev.salt.viewmodel.LoginViewModel
import com.dev.salt.viewmodel.UserRole
import kotlinx.coroutines.launch
import com.dev.salt.data.SurveyDatabase
import android.util.Log

/**
 * Sync message to be displayed on the menu screen after login.
 * @param message The message text
 * @param isError True if this is an error (alarming), false if informational
 */
data class SyncMessage(val message: String, val isError: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    loginViewModel: LoginViewModel,
    onLoginSuccess: (UserRole, SyncMessage?) -> Unit // Callback with role and optional sync message
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Observe error state from ViewModel for Dialog
    val loginError = loginViewModel.loginError
    var showErrorDialog by remember(loginError) { mutableStateOf(loginError != null) }

    // Function to sync facility config and check for survey updates after successful login
    // Navigates immediately, sync runs in background and reports via callback
    fun syncAfterLogin(role: UserRole) {
        // Navigate immediately - don't block on sync
        coroutineScope.launch {
            var syncMessage: SyncMessage? = null

            try {
                val database = com.dev.salt.data.SurveyDatabase.getInstance(context)

                // Sync facility config (quick, best-effort)
                try {
                    val facilityConfigManager = com.dev.salt.sync.FacilityConfigSyncManager(database)
                    facilityConfigManager.syncFacilityConfig()
                } catch (e: Exception) {
                    Log.w("LoginScreen", "Failed to sync facility config", e)
                }

                // Check for survey updates with detailed status
                val surveySyncManager = SurveySyncManager(context)
                val checkResult = surveySyncManager.checkForSurveyUpdateWithStatus()

                syncMessage = when (checkResult) {
                    is SurveyCheckResult.NeedsUpdate -> {
                        Log.i("LoginScreen", "New survey version available, downloading...")
                        val result = surveySyncManager.downloadAndReplaceSurvey()
                        if (result.isSuccess) {
                            Log.i("LoginScreen", "Survey updated successfully")
                            SyncMessage("Survey updated", isError = false)
                        } else {
                            Log.e("LoginScreen", "Failed to update survey", result.exceptionOrNull())
                            SyncMessage("Error: ${result.exceptionOrNull()?.message ?: "Download failed"}", isError = true)
                        }
                    }
                    is SurveyCheckResult.UpToDate -> {
                        Log.i("LoginScreen", "Survey is up to date")
                        SyncMessage("Survey is current", isError = false)
                    }
                    is SurveyCheckResult.Unreachable -> {
                        Log.i("LoginScreen", "Server unreachable - offline mode")
                        SyncMessage("Note: No server connection", isError = false)
                    }
                    is SurveyCheckResult.Error -> {
                        Log.w("LoginScreen", "Sync error: ${checkResult.reason}")
                        SyncMessage("Error: ${checkResult.reason}", isError = true)
                    }
                }
            } catch (e: Exception) {
                Log.e("LoginScreen", "Failed to sync data", e)
                syncMessage = SyncMessage("Error: ${e.message ?: "Unknown error"}", isError = true)
            }

            onLoginSuccess(role, syncMessage)
        }
    }


    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = {
                showErrorDialog = false
                loginViewModel.clearError() // Clear error in ViewModel
            },
            title = { Text(stringResource(R.string.login_failed_title)) },
            text = { Text(loginError ?: stringResource(R.string.login_error_unexpected)) },
            confirmButton = {
                TextButton(onClick = {
                    showErrorDialog = false
                    loginViewModel.clearError()
                }) {
                    Text(stringResource(R.string.common_ok))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.login_title)) })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.login_subtitle), style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = loginViewModel.username,
                onValueChange = {
                    loginViewModel.username = it
                    loginViewModel.checkBiometricAvailability() // Check if biometric is available for this user
                },
                label = { Text(stringResource(R.string.login_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = loginViewModel.password,
                onValueChange = { loginViewModel.password = it },
                label = { Text(stringResource(R.string.login_password)) },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    val image = if (passwordVisible)
                        Icons.Filled.Visibility
                    else Icons.Filled.VisibilityOff
                    val description = if (passwordVisible) "Hide password" else "Show password"
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, description)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    loginViewModel.login { result ->
                        if (result.success) {
                            syncAfterLogin(result.role)
                        }
                        // Error is handled by the showErrorDialog via observing loginViewModel.loginError
                    }
                },
                enabled = !loginViewModel.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (loginViewModel.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.login_button_login))
                }
            }

            // Biometric authentication button
            if (loginViewModel.showBiometricOption) {
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = {
                        loginViewModel.authenticateWithBiometric { result ->
                            if (result.success) {
                                syncAfterLogin(result.role)
                            }
                            // Error is handled by the showErrorDialog via observing loginViewModel.loginError
                        }
                    },
                    enabled = !loginViewModel.isLoading && !loginViewModel.isBiometricLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (loginViewModel.isBiometricLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            Icons.Default.Fingerprint,
                            contentDescription = "Biometric Login",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.login_button_biometric))
                    }
                }
            }
        }
    }
}