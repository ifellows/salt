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
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionManager = SessionManagerInstance.instance
    val database = SurveyDatabase.getInstance(context)
    val userDao = database.userDao()
    
    var serverUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<TestResult?>(null) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    
    // Load current configuration
    LaunchedEffect(Unit) {
        val currentUser = sessionManager.getCurrentUser()
        if (currentUser != null) {
            val config = userDao.getAdminServerConfig(currentUser)
            serverUrl = config?.uploadServerUrl ?: ""
            apiKey = config?.uploadApiKey ?: ""
        }
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
                        placeholder = { Text("https://your-server.com/api/surveys") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = testResult is TestResult.Error
                    )
                    
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { 
                            apiKey = it
                            testResult = null // Clear test result when API key changes
                        },
                        label = { Text("API Key (Optional)") },
                        placeholder = { Text("Bearer token or API key") },
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showApiKey) "Hide API key" else "Show API key"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
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
                                    testResult = testConnection(serverUrl, apiKey)
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
                                    val currentUser = sessionManager.getCurrentUser()
                                    if (currentUser != null) {
                                        userDao.updateUserServerConfig(
                                            currentUser, 
                                            serverUrl.ifBlank { null },
                                            apiKey.ifBlank { null }
                                        )
                                        saveMessage = "Settings saved successfully"
                                        // Clear message after delay
                                        kotlinx.coroutines.delay(3000)
                                        saveMessage = null
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save Settings")
                        }
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
                        text = "• Server URL should be the complete endpoint where survey data will be uploaded",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• API Key is optional - only needed if your server requires authentication",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Test Connection will verify the server is reachable and accepting data",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Survey data will be uploaded as JSON via POST requests",
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

private suspend fun testConnection(serverUrl: String, apiKey: String): TestResult {
    return try {
        // Validate URL format
        URL(serverUrl)
        
        // Create a test JSON payload
        val testPayload = """
            {
                "test": true,
                "message": "SALT connection test",
                "timestamp": "${System.currentTimeMillis()}"
            }
        """.trimIndent()
        
        // Attempt connection (this is a simplified test)
        // In a real implementation, you might want to use a specific test endpoint
        val connection = URL(serverUrl).openConnection() as java.net.HttpURLConnection
        connection.apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            if (apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
            connectTimeout = 10000
            readTimeout = 10000
            doOutput = true
        }
        
        // Write test data
        connection.outputStream.use { output ->
            output.write(testPayload.toByteArray())
        }
        
        val responseCode = connection.responseCode
        when (responseCode) {
            in 200..299 -> TestResult.Success("Connection successful (HTTP $responseCode)")
            401 -> TestResult.Error("Authentication failed - check API key")
            404 -> TestResult.Error("Endpoint not found - check URL")
            in 400..499 -> TestResult.Error("Client error (HTTP $responseCode)")
            in 500..599 -> TestResult.Error("Server error (HTTP $responseCode)")
            else -> TestResult.Error("Unexpected response (HTTP $responseCode)")
        }
        
    } catch (e: java.net.MalformedURLException) {
        TestResult.Error("Invalid URL format")
    } catch (e: java.net.UnknownHostException) {
        TestResult.Error("Server not found - check URL")
    } catch (e: java.net.ConnectException) {
        TestResult.Error("Cannot connect to server")
    } catch (e: java.net.SocketTimeoutException) {
        TestResult.Error("Connection timeout")
    } catch (e: Exception) {
        TestResult.Error("Connection failed: ${e.message}")
    }
}