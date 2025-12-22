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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.dev.salt.R
import com.dev.salt.data.*
import com.dev.salt.upload.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

// Data classes for Survey Status Screen
data class SurveyStatusInfo(
    val surveyId: String,
    val subjectId: String,
    val isCompleted: Boolean,
    val startDatetime: Long,
    val endDatetime: Long?,  // paymentDate or null
    val testResultCount: Int,
    val couponsIssuedCount: Int,
    val paymentStatus: PaymentStatusDisplay,
    val uploadStatus: UploadStatusDisplay,
    val lastAttemptTime: Long?,
    val attemptCount: Int,
    val errorMessage: String?
)

data class PaymentStatusDisplay(
    val confirmed: Boolean,
    val displayText: String  // e.g., "Cash: 5000 AMD" or "No Payment"
)

data class UploadStatusDisplay(
    val status: String,       // COMPLETED, FAILED, PENDING, INCOMPLETE_SURVEY
    val displayText: String,  // User-friendly text
    val color: Color          // Material3 color for background
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadStatusScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = SurveyDatabase.getInstance(context)
    val uploadManager = SurveyUploadManager(context, database)

    var surveyStatusData by remember { mutableStateOf<List<SurveyStatusInfo>>(emptyList()) }
    var statistics by remember { mutableStateOf<UploadStatistics?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isRetrying by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    // Load data
    val loadData = {
        scope.launch {
            isLoading = true
            try {
                surveyStatusData = loadSurveyStatusData(database, context)
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
                title = { Text(stringResource(R.string.upload_status_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = { loadData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.upload_status_refresh))
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
                                    context.getString(R.string.upload_status_success, successCount)
                                } else {
                                    context.getString(R.string.upload_status_no_success)
                                }
                                loadData() // Refresh data
                            } catch (e: Exception) {
                                message = context.getString(R.string.upload_status_retry_failed, e.message ?: "")
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
                        Text(stringResource(R.string.upload_status_retrying))
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.upload_status_retry_all))
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
                        text = stringResource(R.string.upload_status_details_title),
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
                    } else if (surveyStatusData.isEmpty()) {
                        Text(
                            text = stringResource(R.string.upload_status_no_surveys),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(surveyStatusData) { statusInfo ->
                                SurveyStatusCard(
                                    statusInfo = statusInfo,
                                    onRetry = { surveyId ->
                                        scope.launch {
                                            try {
                                                val result = uploadManager.uploadSurvey(surveyId)
                                                message = when (result) {
                                                    is UploadResult.Success -> context.getString(R.string.upload_status_upload_success)
                                                    else -> context.getString(R.string.upload_status_upload_failed)
                                                }
                                                loadData() // Refresh data
                                            } catch (e: Exception) {
                                                message = context.getString(R.string.upload_status_retry_failed, e.message ?: "")
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
                text = stringResource(R.string.upload_status_statistics_title),
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatisticItem(
                    icon = Icons.Default.Assessment,
                    label = stringResource(R.string.upload_status_total),
                    value = statistics.totalSurveys.toString(),
                    color = MaterialTheme.colorScheme.primary
                )

                StatisticItem(
                    icon = Icons.Default.CheckCircle,
                    label = stringResource(R.string.upload_status_completed),
                    value = statistics.completedUploads.toString(),
                    color = MaterialTheme.colorScheme.primary
                )

                StatisticItem(
                    icon = Icons.Default.Schedule,
                    label = stringResource(R.string.upload_status_pending),
                    value = statistics.pendingUploads.toString(),
                    color = MaterialTheme.colorScheme.secondary
                )

                StatisticItem(
                    icon = Icons.Default.Error,
                    label = stringResource(R.string.upload_status_failed),
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
    val context = LocalContext.current
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
                    text = context.getString(R.string.upload_status_survey, uploadState.surveyId.take(8)),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = context.getString(R.string.upload_status_status, uploadState.uploadStatus),
                    style = MaterialTheme.typography.bodySmall
                )
                uploadState.lastAttemptTime?.let { time ->
                    Text(
                        text = context.getString(R.string.upload_status_last_attempt, dateFormat.format(Date(time))),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (uploadState.attemptCount > 1) {
                    Text(
                        text = context.getString(R.string.upload_status_attempts, uploadState.attemptCount),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                uploadState.errorMessage?.let { error ->
                    Text(
                        text = context.getString(R.string.upload_status_error, error),
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
                        contentDescription = stringResource(R.string.upload_status_retry_upload),
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
// Helper functions for Survey Status Screen

/**
 * Format payment status display text based on survey data
 */
fun formatPaymentStatus(survey: Survey, context: android.content.Context): PaymentStatusDisplay {
    return when {
        survey.paymentConfirmed == true -> {
            val amount = survey.paymentAmount?.toString() ?: "0"
            val type = survey.paymentType ?: context.getString(R.string.survey_status_payment_none)
            PaymentStatusDisplay(
                confirmed = true,
                displayText = "$type: $amount"
            )
        }
        survey.paymentConfirmed == false -> PaymentStatusDisplay(
            confirmed = false,
            displayText = context.getString(R.string.survey_status_payment_declined)
        )
        else -> PaymentStatusDisplay(
            confirmed = false,
            displayText = context.getString(R.string.survey_status_payment_none)
        )
    }
}

/**
 * Derive upload status display from survey and upload state
 */
fun deriveUploadStatus(
    survey: Survey,
    uploadState: SurveyUploadState?,
    context: android.content.Context
): UploadStatusDisplay {
    // If survey is not completed, show as incomplete
    if (!survey.isCompleted) {
        return UploadStatusDisplay(
            status = "INCOMPLETE_SURVEY",
            displayText = context.getString(R.string.survey_status_upload_incomplete),
            color = Color.Gray
        )
    }

    // If no upload state exists, show as pending
    if (uploadState == null) {
        return UploadStatusDisplay(
            status = "PENDING",
            displayText = context.getString(R.string.survey_status_upload_pending),
            color = Color.Blue
        )
    }

    // Otherwise use upload state status
    return when (uploadState.uploadStatus) {
        UploadStatus.COMPLETED.name -> UploadStatusDisplay(
            status = "COMPLETED",
            displayText = context.getString(R.string.survey_status_upload_completed),
            color = Color.Green
        )
        UploadStatus.FAILED.name -> UploadStatusDisplay(
            status = "FAILED",
            displayText = context.getString(R.string.survey_status_upload_failed),
            color = Color.Red
        )
        UploadStatus.UPLOADING.name -> UploadStatusDisplay(
            status = "UPLOADING",
            displayText = context.getString(R.string.survey_status_upload_uploading),
            color = Color(0xFFFFA500) // Orange
        )
        else -> UploadStatusDisplay(
            status = "PENDING",
            displayText = context.getString(R.string.survey_status_upload_pending),
            color = Color.Blue
        )
    }
}

/**
 * Load survey status data with efficient batch queries
 */
suspend fun loadSurveyStatusData(
    database: SurveyDatabase,
    context: android.content.Context
): List<SurveyStatusInfo> {
    return withContext(Dispatchers.IO) {
        // 1. Load all upload states
        val uploadStates = database.uploadStateDao().getAllUploadStates()
        val uploadStateMap = uploadStates.associateBy { it.surveyId }

        // 2. Load surveys
        val surveyIds = uploadStates.map { it.surveyId }
        val surveys = surveyIds.mapNotNull {
            database.surveyDao().getSurveyById(it)
        }
        val surveyMap = surveys.associateBy { it.id }

        // 3. Batch load coupons (filter in memory)
        val allCoupons = try {
            database.couponDao().getCouponsByStatus("ISSUED")
        } catch (e: Exception) {
            emptyList<Coupon>()
        }
        val couponCountMap = allCoupons
            .filter { it.issuedToSurveyId != null }
            .groupBy { it.issuedToSurveyId!! }
            .mapValues { it.value.size }

        // 4. Load test results
        val testResultsMap = surveyIds.associateWith { surveyId ->
            try {
                database.testResultDao()
                    .getTestResultsBySurveyId(surveyId)
                    .count { it.result != "not_performed" }
            } catch (e: Exception) {
                0
            }
        }

        // 5. Combine all data
        uploadStates.mapNotNull { uploadState ->
            val survey = surveyMap[uploadState.surveyId] ?: return@mapNotNull null

            SurveyStatusInfo(
                surveyId = survey.id,
                subjectId = survey.subjectId,
                isCompleted = survey.isCompleted,
                startDatetime = survey.startDatetime,
                endDatetime = survey.paymentDate,
                testResultCount = testResultsMap[survey.id] ?: 0,
                couponsIssuedCount = couponCountMap[survey.id] ?: 0,
                paymentStatus = formatPaymentStatus(survey, context),
                uploadStatus = deriveUploadStatus(survey, uploadState, context),
                lastAttemptTime = uploadState.lastAttemptTime,
                attemptCount = uploadState.attemptCount,
                errorMessage = uploadState.errorMessage
            )
        }
    }
}

@Composable
fun SurveyStatusCard(
    statusInfo: SurveyStatusInfo,
    onRetry: (String) -> Unit
) {
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = statusInfo.uploadStatus.color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: Participant ID + Retry button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = statusInfo.subjectId,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (statusInfo.isCompleted)
                            context.getString(R.string.survey_status_completed)
                        else
                            context.getString(R.string.survey_status_incomplete),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (statusInfo.isCompleted)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }

                // Action button (retry or status icon)
                if (statusInfo.uploadStatus.status in listOf("FAILED", "PENDING")) {
                    IconButton(onClick = { onRetry(statusInfo.surveyId) }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.upload_status_retry_upload))
                    }
                } else {
                    Icon(
                        when (statusInfo.uploadStatus.status) {
                            "COMPLETED" -> Icons.Default.CheckCircle
                            "UPLOADING" -> Icons.Default.Upload
                            "INCOMPLETE_SURVEY" -> Icons.Default.Warning
                            else -> Icons.Default.Schedule
                        },
                        contentDescription = null,
                        tint = statusInfo.uploadStatus.color
                    )
                }
            }

            HorizontalDivider()

            // Upload status
            Text(
                text = context.getString(R.string.survey_status_upload, statusInfo.uploadStatus.displayText),
                style = MaterialTheme.typography.bodyMedium
            )

            // Test results and coupons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(
                        text = context.getString(R.string.survey_status_tests, statusInfo.testResultCount),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.ConfirmationNumber, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(
                        text = context.getString(R.string.survey_status_coupons, statusInfo.couponsIssuedCount),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Payment status
            Text(
                text = context.getString(R.string.survey_status_payment, statusInfo.paymentStatus.displayText),
                style = MaterialTheme.typography.bodySmall
            )

            HorizontalDivider()

            // Timestamps
            Text(
                text = context.getString(R.string.survey_status_started, dateFormat.format(Date(statusInfo.startDatetime))),
                style = MaterialTheme.typography.bodySmall
            )

            statusInfo.endDatetime?.let { endTime ->
                Text(
                    text = context.getString(R.string.survey_status_ended, dateFormat.format(Date(endTime))),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Upload/attempt timestamp
            if (statusInfo.lastAttemptTime != null) {
                val (labelRes, textColor) = when (statusInfo.uploadStatus.status) {
                    "COMPLETED" -> R.string.survey_status_upload_datetime to MaterialTheme.colorScheme.onSurface
                    else -> R.string.survey_status_last_attempt to MaterialTheme.colorScheme.error
                }
                Text(
                    text = context.getString(labelRes, dateFormat.format(Date(statusInfo.lastAttemptTime))),
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor
                )
            }

            // Error message (if exists)
            statusInfo.errorMessage?.let { error ->
                Text(
                    text = context.getString(R.string.survey_status_error, error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
