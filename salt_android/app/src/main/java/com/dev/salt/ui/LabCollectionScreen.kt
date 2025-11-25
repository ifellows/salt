package com.dev.salt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.dev.salt.R
import com.dev.salt.data.LabTestConfiguration
import com.dev.salt.data.SurveyDatabase
import com.dev.salt.util.JexlContextDebugInfo
import com.dev.salt.util.LabTestEvaluationResult
import com.dev.salt.util.LabTestJexlEvaluator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabCollectionScreen(
    surveyId: String,
    subjectId: String,
    onSamplesCollected: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { SurveyDatabase.getInstance(context) }
    val scrollState = rememberScrollState()

    // State for lab tests and debug info
    var qualifyingTests by remember { mutableStateOf<List<LabTestConfiguration>>(emptyList()) }
    var debugInfo by remember { mutableStateOf<Pair<JexlContextDebugInfo, List<LabTestEvaluationResult>>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showDebugPanel by remember { mutableStateOf(false) }
    var shouldSkip by remember { mutableStateOf(false) }

    // Load qualifying lab tests
    LaunchedEffect(surveyId) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val (contextInfo, results) = LabTestJexlEvaluator.getDebugInfo(surveyId, database)
            debugInfo = Pair(contextInfo, results)
            val qualifying = results.filter { it.shouldShow }.map { it.labTest }
            qualifyingTests = qualifying

            // If no qualifying tests, auto-skip this screen
            if (qualifying.isEmpty()) {
                android.util.Log.d("LabCollectionScreen", "No qualifying lab tests - auto-skipping screen")
                shouldSkip = true
            }
            isLoading = false
        }
    }

    // Auto-skip if no qualifying tests
    LaunchedEffect(shouldSkip, isLoading) {
        if (shouldSkip && !isLoading) {
            android.util.Log.d("LabCollectionScreen", "Auto-navigating past lab collection (no qualifying tests)")
            onSamplesCollected() // Proceed to next screen as if samples were collected
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = stringResource(R.string.cd_lab),
                            tint = Color.White
                        )
                        Text(stringResource(R.string.lab_title))
                    }
                },
                actions = {
                    // Debug toggle button
                    IconButton(onClick = { showDebugPanel = !showDebugPanel }) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Toggle Debug",
                            tint = if (showDebugPanel) Color.Yellow else Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1976D2),
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Subject ID Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.lab_subject_id),
                            fontSize = 14.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Large ID Display
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Color.White,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = 2.dp,
                                    color = Color(0xFF1976D2),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = subjectId,
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF1976D2),
                                letterSpacing = 4.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // Required Lab Tests Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Required Lab Tests (${qualifyingTests.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF1565C0)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (qualifyingTests.isEmpty()) {
                            Text(
                                text = "No lab tests required for this participant.",
                                fontSize = 14.sp,
                                color = Color.Gray,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        } else {
                            qualifyingTests.forEachIndexed { index, labTest ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = labTest.testName,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 16.sp
                                        )
                                        if (labTest.testCode != null) {
                                            Text(
                                                text = "Code: ${labTest.testCode}",
                                                fontSize = 12.sp,
                                                color = Color.Gray
                                            )
                                        }
                                        Text(
                                            text = when (labTest.testType) {
                                                "numeric" -> "Type: Numeric${labTest.unit?.let { " ($it)" } ?: ""}"
                                                "dropdown" -> "Type: Dropdown"
                                                else -> "Type: ${labTest.testType}"
                                            },
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                if (index < qualifyingTests.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = Color(0xFFBBDEFB)
                                    )
                                }
                            }
                        }
                    }
                }

                // Debug Panel (collapsible)
                if (showDebugPanel && debugInfo != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "JEXL Debug Information",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFFE65100)
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Context Variables
                            Text(
                                text = "Survey Answers:",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            val contextInfo = debugInfo!!.first
                            if (contextInfo.surveyAnswers.isEmpty()) {
                                Text(
                                    text = "  (none)",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.Gray
                                )
                            } else {
                                contextInfo.surveyAnswers.forEach { (key, value) ->
                                    Text(
                                        text = "  $key = $value",
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Rapid Test Results:",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            if (contextInfo.rapidTestResults.isEmpty()) {
                                Text(
                                    text = "  (none)",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.Gray
                                )
                            } else {
                                contextInfo.rapidTestResults.forEach { (key, value) ->
                                    Text(
                                        text = "  $key = \"$value\"",
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = Color(0xFFFFCC80))
                            Spacer(modifier = Modifier.height(12.dp))

                            // Lab Test Evaluations
                            Text(
                                text = "Lab Test Evaluations:",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            debugInfo!!.second.forEach { result ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (result.shouldShow) Color(0xFFC8E6C9) else Color(0xFFFFCDD2)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = result.labTest.testName,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "Condition: ${result.jexlCondition ?: "(none - always show)"}",
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = "Result: ${result.evaluationResult ?: "N/A"} => ${if (result.shouldShow) "SHOW" else "HIDE"}",
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = if (result.shouldShow) Color(0xFF2E7D32) else Color(0xFFC62828)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Instructions Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFECB3))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.lab_instructions),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFFE65100)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.lab_instructions_text),
                            fontSize = 14.sp,
                            color = Color(0xFF5D4037)
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFD32F2F)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFD32F2F))
                    ) {
                        Text(stringResource(R.string.lab_refused))
                    }

                    Button(
                        onClick = onSamplesCollected,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1976D2)
                        )
                    ) {
                        Text(stringResource(R.string.lab_collected))
                    }
                }

            }
        }
    }
}
