package com.dev.salt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.dev.salt.data.SurveyDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class CouponLogEntry(
    val referralCoupon: String?, // null for seed participants
    val subjectId: String?,
    val recruitStatus: String, // "Completed", "Ineligible", "Incomplete"
    val surveyStartTime: Long?,
    val paymentDate: Long?,
    val issuedCoupons: List<IssuedCouponInfo>
)

data class IssuedCouponInfo(
    val couponCode: String,
    val status: String, // "Not Used", "Ineligible", "Completed", "Incomplete"
    val timestamp: Long?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CouponLoggingScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val database = remember { SurveyDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    var logEntries by remember { mutableStateOf<List<CouponLogEntry>>(emptyList()) }
    var filteredEntries by remember { mutableStateOf<List<CouponLogEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }

    // Load coupon log data
    LaunchedEffect(Unit) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val entries = mutableListOf<CouponLogEntry>()

            // Get all surveys (completed and incomplete)
            val allSurveys = database.surveyDao().getAllSurveys()

            // Process each survey
            for (survey in allSurveys) {
                val referralCoupon = survey.referralCouponCode
                val subjectId = survey.subjectId

                // Determine recruit status (don't use "Seed" status)
                val recruitStatus = when {
                    !survey.isCompleted -> "Incomplete"
                    survey.paymentConfirmed == true -> "Completed"
                    survey.paymentConfirmed == false -> "Ineligible"
                    else -> "Ineligible" // isCompleted but paymentConfirmed is null means ineligible
                }

                // Get coupons issued to this survey
                val issuedCoupons = database.couponDao().getCouponsIssuedToSurvey(survey.id)
                // Filter to only show ISSUED or USED coupons (hide UNUSED coupons from incomplete surveys)
                val issuedCouponInfoList = issuedCoupons
                    .filter { it.status == "ISSUED" || it.status == "USED" }
                    .map { coupon ->
                        // Determine status of issued coupon
                        val couponStatus = when {
                            coupon.status == "ISSUED" && coupon.usedBySurveyId == null -> "Not Used"
                            else -> {
                                // Coupon was used, check the recruit's survey status
                                val recruitSurvey = database.surveyDao().getSurveyById(coupon.usedBySurveyId!!)
                                when {
                                    recruitSurvey == null -> "Not Used"
                                    !recruitSurvey.isCompleted -> "Incomplete"
                                    recruitSurvey.paymentConfirmed == true -> "Completed"
                                    recruitSurvey.paymentConfirmed == false -> "Ineligible"
                                    else -> "Ineligible" // isCompleted but paymentConfirmed is null means ineligible
                                }
                            }
                        }

                        IssuedCouponInfo(
                            couponCode = coupon.couponCode,
                            status = couponStatus,
                            timestamp = coupon.usedDate
                        )
                    }

                entries.add(
                    CouponLogEntry(
                        referralCoupon = referralCoupon,
                        subjectId = subjectId,
                        recruitStatus = recruitStatus,
                        surveyStartTime = survey.startDatetime,
                        paymentDate = survey.paymentDate,
                        issuedCoupons = issuedCouponInfoList
                    )
                )
            }

            // Sort by most recent first (by survey start time)
            logEntries = entries.sortedByDescending { it.surveyStartTime ?: 0L }
            filteredEntries = logEntries
        }
        isLoading = false
    }

    // Filter entries when search query changes
    LaunchedEffect(searchQuery, logEntries) {
        filteredEntries = if (searchQuery.isEmpty()) {
            logEntries
        } else {
            logEntries.filter { entry ->
                (entry.referralCoupon?.contains(searchQuery, ignoreCase = true) == true) ||
                (entry.subjectId?.contains(searchQuery, ignoreCase = true) == true) ||
                entry.issuedCoupons.any { it.couponCode.contains(searchQuery, ignoreCase = true) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Coupon Logging") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search by coupon code or subject ID") },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredEntries.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No coupon data found")
                }
            } else {
                // Make the entire table scrollable both directions
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState())
                ) {
                    // Table header
                    Row(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            "Recruit ID",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(120.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Status",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(120.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Start",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(140.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Issued Coupons",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(400.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    HorizontalDivider()

                    // Table content (scrollable vertically)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredEntries) { entry ->
                            CouponLogRow(entry, dateFormatter)
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CouponLogRow(
    entry: CouponLogEntry,
    dateFormatter: SimpleDateFormat
) {
    Row(
        modifier = Modifier
            .padding(8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        // Combined Recruit ID column (shows referral coupon if not seed, otherwise subject ID)
        Text(
            text = if (entry.referralCoupon.isNullOrEmpty()) {
                "Seed: ${entry.subjectId ?: "N/A"}"
            } else {
                entry.referralCoupon
            },
            modifier = Modifier.width(120.dp)
        )

        // Status column
        Row(
            modifier = Modifier.width(120.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val statusIcon = when (entry.recruitStatus) {
                "Completed" -> "✓"
                "Ineligible" -> "✗"
                "Incomplete" -> "⚠"
                else -> "?"
            }
            val statusColor = when (entry.recruitStatus) {
                "Completed" -> Color(0xFF4CAF50)
                "Ineligible" -> Color(0xFFF44336)
                "Incomplete" -> Color(0xFFFF9800)
                else -> Color.Gray
            }
            Text(
                text = "$statusIcon ",
                color = statusColor,
                fontWeight = FontWeight.Bold
            )
            Text(text = entry.recruitStatus)
        }

        // Start time column
        Text(
            text = entry.surveyStartTime?.let { dateFormatter.format(Date(it)) } ?: "N/A",
            modifier = Modifier.width(140.dp)
        )

        // Issued coupons (horizontal layout)
        Row(
            modifier = Modifier.width(400.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (entry.issuedCoupons.isEmpty()) {
                Text(
                    text = "None",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                entry.issuedCoupons.forEach { issuedCoupon ->
                    val statusIcon = when (issuedCoupon.status) {
                        "Completed" -> "✓"
                        "Ineligible" -> "✗"
                        "Incomplete" -> "⚠"
                        else -> "○"
                    }
                    val statusColor = when (issuedCoupon.status) {
                        "Completed" -> Color(0xFF4CAF50)
                        "Ineligible" -> Color(0xFFF44336)
                        "Incomplete" -> Color(0xFFFF9800)
                        else -> Color.Gray
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.padding(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$statusIcon ",
                                color = statusColor,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = issuedCoupon.couponCode,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
