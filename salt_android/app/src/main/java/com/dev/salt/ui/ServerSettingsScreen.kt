package com.dev.salt.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.dev.salt.R
import com.dev.salt.session.SessionManagerInstance
import com.dev.salt.data.SurveyDatabase
import com.dev.salt.upload.SurveyUploadManager
import com.dev.salt.upload.UploadResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsScreen(
    onBack: () -> Unit,
    onNavigateToSetup: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionManager = SessionManagerInstance.instance
    val database = SurveyDatabase.getInstance(context)
    val appServerConfigDao = database.appServerConfigDao()
    
    var serverUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var hasApiKey by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<TestResult?>(null) }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    // Load current configuration
    LaunchedEffect(Unit) {
        val config = appServerConfigDao.getServerConfig()
        serverUrl = config?.serverUrl ?: "http://10.0.2.2:3000"
        apiKey = config?.apiKey ?: ""
        hasApiKey = !config?.apiKey.isNullOrBlank()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.server_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.server_settings_config_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = {
                            serverUrl = it
                            testResult = null // Clear test result when URL changes
                        },
                        label = { Text(stringResource(R.string.server_settings_server_url_label)) },
                        placeholder = { Text("http://10.0.2.2:3000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = testResult is TestResult.Error
                    )

                    // Show API key status
                    if (hasApiKey) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.server_settings_api_key_configured),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = stringResource(R.string.server_settings_no_api_key),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    
                    // Test connection result
                    testResult?.let { result ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                when (result) {
                                    is TestResult.Success -> Icons.Default.CheckCircle
                                    is TestResult.Error -> Icons.Default.Error
                                },
                                contentDescription = null,
                                tint = when (result) {
                                    is TestResult.Success -> MaterialTheme.colorScheme.primary
                                    is TestResult.Error -> MaterialTheme.colorScheme.error
                                }
                            )
                            Text(
                                text = when (result) {
                                    is TestResult.Success -> result.message
                                    is TestResult.Error -> result.message
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = when (result) {
                                    is TestResult.Success -> MaterialTheme.colorScheme.primary
                                    is TestResult.Error -> MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                    
                    // Save message
                    saveMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    testResult = testConnection(context, serverUrl, apiKey)
                                    isLoading = false
                                }
                            },
                            enabled = !isLoading && serverUrl.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            } else {
                                Text(stringResource(R.string.server_settings_test_connection))
                            }
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    // Save server URL (keeping existing API key)
                                    val currentConfig = appServerConfigDao.getServerConfig()
                                    val serverConfig = com.dev.salt.data.AppServerConfig(
                                        serverUrl = serverUrl,
                                        apiKey = currentConfig?.apiKey ?: ""  // Keep existing API key
                                    )
                                    appServerConfigDao.insertOrUpdate(serverConfig)
                                    saveMessage = context.getString(R.string.server_settings_url_saved)
                                    // Clear message after delay
                                    kotlinx.coroutines.delay(3000)
                                    saveMessage = null
                                }
                            },
                            enabled = serverUrl.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.server_settings_save_url))
                        }
                    }

                    // Add setup code button
                    Button(
                        onClick = onNavigateToSetup,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (hasApiKey)
                                stringResource(R.string.server_settings_update_setup_code)
                            else
                                stringResource(R.string.server_settings_enter_setup_code)
                        )
                    }
                }
            }
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.server_settings_help_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.server_settings_help_url),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.server_settings_help_api_key),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.server_settings_help_setup),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.server_settings_help_test),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

sealed class TestResult {
    data class Success(val message: String) : TestResult()
    data class Error(val message: String) : TestResult()
}

private suspend fun testConnection(context: android.content.Context, serverUrl: String, apiKey: String): TestResult = withContext(Dispatchers.IO) {
    try {
        // Validate URL format
        val url = try {
            URL(serverUrl)
        } catch (e: Exception) {
            return@withContext TestResult.Error(context.getString(R.string.server_settings_invalid_url))
        }

        // Use the survey version endpoint to test connection
        val testUrl = "$serverUrl/api/sync/survey/version"
        val connection = URL(testUrl).openConnection() as java.net.HttpURLConnection

        try {
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("X-API-Key", apiKey)
                setRequestProperty("Accept", "application/json")
                connectTimeout = 10000
                readTimeout = 10000
            }

            val responseCode = connection.responseCode
            when (responseCode) {
                in 200..299 -> {
                    TestResult.Success(context.getString(R.string.server_settings_connection_successful))
                }
                401, 403 -> TestResult.Error(
                    if (apiKey.isBlank())
                        context.getString(R.string.server_settings_no_api_key_setup)
                    else
                        context.getString(R.string.server_settings_invalid_api_key)
                )
                404 -> TestResult.Error(context.getString(R.string.server_settings_endpoint_not_found))
                in 400..499 -> TestResult.Error(context.getString(R.string.server_settings_client_error, responseCode))
                in 500..599 -> TestResult.Error(context.getString(R.string.server_settings_server_error, responseCode))
                else -> TestResult.Error(context.getString(R.string.server_settings_unexpected_response, responseCode))
            }
        } finally {
            connection.disconnect()
        }
    } catch (e: java.net.UnknownHostException) {
        TestResult.Error(context.getString(R.string.server_settings_not_found))
    } catch (e: java.net.ConnectException) {
        TestResult.Error(context.getString(R.string.server_settings_cannot_connect))
    } catch (e: java.net.SocketTimeoutException) {
        TestResult.Error(context.getString(R.string.server_settings_timeout))
    } catch (e: java.io.IOException) {
        TestResult.Error(context.getString(R.string.server_settings_network_error, e.javaClass.simpleName))
    } catch (e: Exception) {
        TestResult.Error(context.getString(R.string.server_settings_connection_failed, e.message ?: e.javaClass.simpleName))
    }
}