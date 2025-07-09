package com.dev.salt

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dev.salt.viewmodel.LoginViewModel
import com.dev.salt.viewmodel.UserRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    loginViewModel: LoginViewModel,
    onLoginSuccess: (UserRole) -> Unit // Callback to navigate based on role
) {
    var passwordVisible by remember { mutableStateOf(false) }

    // Observe error state from ViewModel for Dialog
    val loginError = loginViewModel.loginError
    var showErrorDialog by remember(loginError) { mutableStateOf(loginError != null) }


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
                onValueChange = { loginViewModel.username = it },
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
                            onLoginSuccess(result.role)
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
        }
    }
}