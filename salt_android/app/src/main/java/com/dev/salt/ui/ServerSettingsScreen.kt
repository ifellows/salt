package com.dev.salt.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
                title = { Text("Server Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Refresh, contentDescription = "Back")
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
                        text = "Upload Server Configuration",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = {
                            serverUrl = it
                            testResult = null // Clear test result when URL changes
                        },
                        label = { Text("Server URL") },
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
                                text = "API Key Configured",
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
                                text = "No API Key - Use Setup Code",
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
                                    testResult = testConnection(serverUrl, apiKey)
                                    isLoading = false
                                }
                            },
                            enabled = !isLoading && serverUrl.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            } else {
                                Text("Test Connection")
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
                                    saveMessage = "Server URL saved successfully"
                                    // Clear message after delay
                                    kotlinx.coroutines.delay(3000)
                                    saveMessage = null
                                }
                            },
                            enabled = serverUrl.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save URL")
                        }
                    }

                    // Add setup code button
                    Button(
                        onClick = onNavigateToSetup,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (hasApiKey) "Update Setup Code" else "Enter Setup Code")
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
                        text = "Configuration Help",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "• Server URL can be edited to point to your server",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• API Key is obtained from the facility setup code",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Use setup code to configure facility and get API key",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Test Connection verifies the server and API key",
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

private suspend fun testConnection(serverUrl: String, apiKey: String): TestResult = withContext(Dispatchers.IO) {
    try {
        // Validate URL format
        val url = try {
            URL(serverUrl)
        } catch (e: Exception) {
            return@withContext TestResult.Error("Invalid URL format")
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
                    TestResult.Success("Connection successful")
                }
                401, 403 -> TestResult.Error(if (apiKey.isBlank()) "No API key - enter setup code" else "Invalid API key - enter new setup code")
                404 -> TestResult.Error("Server endpoint not found")
                in 400..499 -> TestResult.Error("Client error (HTTP $responseCode)")
                in 500..599 -> TestResult.Error("Server error (HTTP $responseCode)")
                else -> TestResult.Error("Unexpected response (HTTP $responseCode)")
            }
        } finally {
            connection.disconnect()
        }
    } catch (e: java.net.UnknownHostException) {
        TestResult.Error("Server not found - check URL")
    } catch (e: java.net.ConnectException) {
        TestResult.Error("Cannot connect to server - check URL and port")
    } catch (e: java.net.SocketTimeoutException) {
        TestResult.Error("Connection timeout - server may be unreachable")
    } catch (e: java.io.IOException) {
        TestResult.Error("Network error: ${e.javaClass.simpleName}")
    } catch (e: Exception) {
        TestResult.Error("Connection failed: ${e.message ?: e.javaClass.simpleName}")
    }
}