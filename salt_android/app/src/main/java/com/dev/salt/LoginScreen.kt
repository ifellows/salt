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
import com.dev.salt.sync.SurveySyncManager
import com.dev.salt.viewmodel.LoginViewModel
import com.dev.salt.viewmodel.UserRole
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    loginViewModel: LoginViewModel,
    onLoginSuccess: (UserRole) -> Unit // Callback to navigate based on role
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Observe error state from ViewModel for Dialog
    val loginError = loginViewModel.loginError
    var showErrorDialog by remember(loginError) { mutableStateOf(loginError != null) }
    
    // Function to sync facility config and check for survey updates after successful login
    fun syncAfterLogin(role: UserRole) {
        coroutineScope.launch {
            try {
                val database = com.dev.salt.data.SurveyDatabase.getInstance(context)

                // Sync facility config
                val facilityConfigManager = com.dev.salt.sync.FacilityConfigSyncManager(database)
                facilityConfigManager.syncFacilityConfig()

                // Check for survey updates
                val surveySyncManager = SurveySyncManager(context)
                val needsUpdate = surveySyncManager.checkForSurveyUpdate()

                if (needsUpdate) {
                    android.util.Log.i("LoginScreen", "New survey version available, downloading...")
                    val result = surveySyncManager.downloadAndReplaceSurvey()
                    if (result.isSuccess) {
                        android.util.Log.i("LoginScreen", "Survey updated successfully")
                    } else {
                        android.util.Log.e("LoginScreen", "Failed to update survey", result.exceptionOrNull())
                    }
                }
            } catch (e: Exception) {
                // Log but don't block login on sync failure
                android.util.Log.e("LoginScreen", "Failed to sync data", e)
            }
            onLoginSuccess(role)
        }
    }


    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = {
                showErrorDialog = false
                loginViewModel.clearError() // Clear error in ViewModel
            },
            title = { Text("Login Failed") },
            text = { Text(loginError ?: "An unknown error occurred.") },
            confirmButton = {
                TextButton(onClick = {
                    showErrorDialog = false
                    loginViewModel.clearError()
                }) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Login") })
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
            Text("Please Log In", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = loginViewModel.username,
                onValueChange = { 
                    loginViewModel.username = it
                    loginViewModel.checkBiometricAvailability() // Check if biometric is available for this user
                },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = loginViewModel.password,
                onValueChange = { loginViewModel.password = it },
                label = { Text("Password") },
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
                    Text("Login")
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
                        Text("Login with Biometric")
                    }
                }
            }
        }
    }
}