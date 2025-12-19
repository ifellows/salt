package com.dev.salt.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.util.Log
import androidx.compose.ui.res.stringResource
import com.dev.salt.R
import com.dev.salt.data.SurveyDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.compose.BackHandler

@Composable
fun BiologicalSampleCollectionScreen(
    surveyId: String,
    onSamplesConfirmed: () -> Unit,
    onCancel: () -> Unit
) {
    // Disable hardware back button during survey flow
    BackHandler(enabled = true) {
        // Intentionally empty - back button is disabled during survey flow
    }

    val context = LocalContext.current
    val database = remember { SurveyDatabase.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    var enabledTests by remember { mutableStateOf<List<com.dev.salt.data.TestConfiguration>>(emptyList()) }
    var checkedTests by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }

    // Load enabled tests
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            // Get the actual survey configuration ID from sections
            // All sections in the database belong to the same survey
            val sections = database.sectionDao().getAllSections()
            val configSurveyId = if (sections.isNotEmpty()) {
                sections.first().surveyId.toLong()
            } else {
                // Fallback to -1 to ensure errors surface rather than silently using wrong data
                Log.e("BiologicalSampleCollection", "CRITICAL ERROR: No sections found in database - survey not properly synced! Using fallback ID -1")
                Log.e("BiologicalSampleCollection", "This will likely result in no tests being found. Please ensure survey is properly downloaded.")
                -1L
            }

            if (configSurveyId == -1L) {
                Log.e("BiologicalSampleCollection", "WARNING: Using fallback survey ID -1, no tests will be found")
            } else {
                Log.d("BiologicalSampleCollection", "Loading tests for survey configuration ID: $configSurveyId")
            }

            val tests = database.testConfigurationDao().getEnabledTestConfigurations(configSurveyId)
            enabledTests = tests
            isLoading = false
            Log.d("BiologicalSampleCollection", "Loaded ${tests.size} enabled tests for survey ID $configSurveyId")
        }
    }

    val allChecked = enabledTests.isNotEmpty() && checkedTests.size == enabledTests.size

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Icon
                    Icon(
                        Icons.Filled.Science,
                        contentDescription = stringResource(R.string.cd_sample_collection),
                        modifier = Modifier
                            .size(64.dp)
                            .padding(bottom = 16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )

                    // Title
                    Text(
                        text = stringResource(R.string.sample_collection_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Instructions
                    Text(
                        text = stringResource(R.string.sample_collection_instruction),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    if (isLoading) {
                        CircularProgressIndicator()
                    } else if (enabledTests.isEmpty()) {
                        Text(
                            text = stringResource(R.string.sample_collection_no_tests),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        // Test checklist
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            enabledTests.forEach { test ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (checkedTests.contains(test.testId)) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = checkedTests.contains(test.testId),
                                            onCheckedChange = { checked ->
                                                checkedTests = if (checked) {
                                                    checkedTests + test.testId
                                                } else {
                                                    checkedTests - test.testId
                                                }
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = test.testName,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = if (checkedTests.contains(test.testId))
                                                FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }

                        // Continue button
                        Button(
                            onClick = {
                                Log.d("BiologicalSampleCollection", "All samples confirmed for ${enabledTests.size} tests")
                                onSamplesConfirmed()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp)
                                .height(56.dp),
                            enabled = allChecked,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = stringResource(R.string.sample_collection_continue),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        if (!allChecked && enabledTests.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.sample_collection_check_all),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
