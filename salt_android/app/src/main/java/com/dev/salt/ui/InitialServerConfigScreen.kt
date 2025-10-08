package com.dev.salt.ui

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dev.salt.R
import com.dev.salt.data.AppServerConfig
import com.dev.salt.data.SurveyDatabase
import com.dev.salt.network.ServerValidationService
import kotlinx.coroutines.launch

/**
 * Initial server configuration screen shown on first app launch.
 *
 * Allows the user to enter and validate the SALT server URL before proceeding
 * to admin user creation. This screen is part of the first-time setup wizard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InitialServerConfigScreen(
    onServerConfigured: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = SurveyDatabase.getInstance(context)
    val appServerConfigDao = database.appServerConfigDao()

    // Detect if running on emulator
    val isEmulator = Build.FINGERPRINT.contains("generic") ||
                     Build.FINGERPRINT.contains("unknown") ||
                     Build.MODEL.contains("google_sdk") ||
                     Build.MODEL.contains("Emulator") ||
                     Build.MODEL.contains("Android SDK built for x86") ||
                     Build.MANUFACTURER.contains("Genymotion") ||
                     Build.PRODUCT.contains("sdk_gphone") ||
                     Build.PRODUCT.contains("sdk") ||
                     Build.PRODUCT.contains("vbox86p") ||
                     Build.HARDWARE.contains("goldfish") ||
                     Build.HARDWARE.contains("ranchu")

    // Set default URL based on device type
    val defaultUrl = if (isEmulator) "http://10.0.2.2:3000" else "http://192.168.1.137:3000"

    var serverUrl by remember { mutableStateOf(defaultUrl) }
    var isValidating by remember { mutableStateOf(false) }
    var validationResult by remember { mutableStateOf<ServerValidationService.ValidationResult?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setup_server_title)) },
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
                    text = stringResource(R.string.setup_welcome_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.setup_server_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Server URL Input
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.label_server_url),
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = {
                            serverUrl = it
                            validationResult = null // Clear validation when URL changes
                        },
                        label = { Text(stringResource(R.string.label_server_url)) },
                        placeholder = { Text(defaultUrl) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isValidating,
                        isError = validationResult is ServerValidationService.ValidationResult.Error
                    )

                    // Info text
                    Text(
                        text = stringResource(R.string.info_server_url_examples),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Validation result
                    validationResult?.let { result ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = when (result) {
                                    is ServerValidationService.ValidationResult.Success -> Icons.Default.CheckCircle
                                    is ServerValidationService.ValidationResult.Error -> Icons.Default.Error
                                },
                                contentDescription = null,
                                tint = when (result) {
                                    is ServerValidationService.ValidationResult.Success -> MaterialTheme.colorScheme.primary
                                    is ServerValidationService.ValidationResult.Error -> MaterialTheme.colorScheme.error
                                }
                            )
                            Text(
                                text = when (result) {
                                    is ServerValidationService.ValidationResult.Success -> stringResource(R.string.status_server_connected, result.version)
                                    is ServerValidationService.ValidationResult.Error -> "✗ ${result.message}"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = when (result) {
                                    is ServerValidationService.ValidationResult.Success -> MaterialTheme.colorScheme.primary
                                    is ServerValidationService.ValidationResult.Error -> MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Action buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            isValidating = true
                            validationResult = ServerValidationService.validateSaltServer(serverUrl, context)
                            isValidating = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isValidating && serverUrl.isNotBlank()
                ) {
                    if (isValidating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.status_validating))
                    } else {
                        Text(stringResource(R.string.button_validate_connection))
                    }
                }

                Button(
                    onClick = {
                        scope.launch {
                            // Save server URL to database
                            val config = AppServerConfig(
                                id = 1, // Single row table
                                serverUrl = serverUrl.trimEnd('/'),
                                apiKey = "", // Will be set later via facility setup code
                                updatedAt = System.currentTimeMillis()
                            )
                            appServerConfigDao.insertOrUpdate(config)

                            // Proceed to next setup step
                            onServerConfigured()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = validationResult is ServerValidationService.ValidationResult.Success && !isValidating
                ) {
                    Text(stringResource(R.string.button_next_create_admin))
                }
            }
        }
    }
}
