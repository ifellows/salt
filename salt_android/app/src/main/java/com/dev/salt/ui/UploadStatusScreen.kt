package com.dev.salt.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dev.salt.data.*
import com.dev.salt.upload.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadStatusScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = SurveyDatabase.getInstance(context)
    val uploadManager = SurveyUploadManager(context, database)
    
    var uploadStates by remember { mutableStateOf<List<SurveyUploadState>>(emptyList()) }
    var statistics by remember { mutableStateOf<UploadStatistics?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isRetrying by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    
    // Load data
    val loadData = {
        scope.launch {
            isLoading = true
            try {
                uploadStates = database.uploadStateDao().getAllUploadStates()
                statistics = uploadManager.getUploadStatistics()
            } finally {
                isLoading = false
            }
        }
    }
    
    LaunchedEffect(Unit) {
        loadData()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upload Status") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { loadData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            if (statistics?.pendingUploads ?: 0 > 0 || statistics?.failedUploads ?: 0 > 0) {
                ExtendedFloatingActionButton(
                    onClick = {
                        scope.launch {
                            isRetrying = true
                            message = null
                            try {
                                val results = uploadManager.retryPendingUploads()
                                val successCount = results.count { it.second is UploadResult.Success }
                                message = if (successCount > 0) {
                                    "Successfully uploaded $successCount surveys"
                                } else {
                                    "No surveys were successfully uploaded"
                                }
                                loadData() // Refresh data
                            } catch (e: Exception) {
                                message = "Retry failed: ${e.message}"
                            } finally {
                                isRetrying = false
                                // Clear message after delay
                                kotlinx.coroutines.delay(5000)
                                message = null
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    if (isRetrying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retrying...")
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry All")
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Statistics Card
            statistics?.let { stats ->
                StatisticsCard(statistics = stats)
            }
            
            // Message display
            message?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = msg,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            // Upload Status List
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Survey Upload Details",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (uploadStates.isEmpty()) {
                        Text(
                            text = "No surveys to upload",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(uploadStates) { uploadState ->
                                UploadStateItem(
                                    uploadState = uploadState,
                                    onRetry = { surveyId ->
                                        scope.launch {
                                            try {
                                                val result = uploadManager.uploadSurvey(surveyId)
                                                message = when (result) {
                                                    is UploadResult.Success -> "Survey uploaded successfully"
                                                    else -> "Upload failed"
                                                }
                                                loadData() // Refresh data
                                            } catch (e: Exception) {
                                                message = "Retry failed: ${e.message}"
                                            }
                                            // Clear message after delay
                                            kotlinx.coroutines.delay(3000)
                                            message = null
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatisticsCard(statistics: UploadStatistics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Upload Statistics",
                style = MaterialTheme.typography.titleMedium
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatisticItem(
                    icon = Icons.Default.Assessment,
                    label = "Total",
                    value = statistics.totalSurveys.toString(),
                    color = MaterialTheme.colorScheme.primary
                )
                
                StatisticItem(
                    icon = Icons.Default.CheckCircle,
                    label = "Completed",
                    value = statistics.completedUploads.toString(),
                    color = MaterialTheme.colorScheme.primary
                )
                
                StatisticItem(
                    icon = Icons.Default.Schedule,
                    label = "Pending",
                    value = statistics.pendingUploads.toString(),
                    color = MaterialTheme.colorScheme.secondary
                )
                
                StatisticItem(
                    icon = Icons.Default.Error,
                    label = "Failed",
                    value = statistics.failedUploads.toString(),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun StatisticItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun UploadStateItem(
    uploadState: SurveyUploadState,
    onRetry: (String) -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (uploadState.uploadStatus) {
                UploadStatus.COMPLETED.name -> MaterialTheme.colorScheme.primaryContainer
                UploadStatus.FAILED.name -> MaterialTheme.colorScheme.errorContainer
                UploadStatus.UPLOADING.name -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Survey: ${uploadState.surveyId.take(8)}...",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Status: ${uploadState.uploadStatus}",
                    style = MaterialTheme.typography.bodySmall
                )
                uploadState.lastAttemptTime?.let { time ->
                    Text(
                        text = "Last attempt: ${dateFormat.format(Date(time))}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (uploadState.attemptCount > 1) {
                    Text(
                        text = "Attempts: ${uploadState.attemptCount}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                uploadState.errorMessage?.let { error ->
                    Text(
                        text = "Error: $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            if (uploadState.uploadStatus in listOf(UploadStatus.FAILED.name, UploadStatus.PENDING.name)) {
                IconButton(
                    onClick = { onRetry(uploadState.surveyId) }
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Retry Upload",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Icon(
                    when (uploadState.uploadStatus) {
                        UploadStatus.COMPLETED.name -> Icons.Default.CheckCircle
                        UploadStatus.UPLOADING.name -> Icons.Default.Upload
                        else -> Icons.Default.Schedule
                    },
                    contentDescription = null,
                    tint = when (uploadState.uploadStatus) {
                        UploadStatus.COMPLETED.name -> MaterialTheme.colorScheme.primary
                        UploadStatus.UPLOADING.name -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}