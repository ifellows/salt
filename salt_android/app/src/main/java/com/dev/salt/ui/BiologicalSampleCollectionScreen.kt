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
import com.dev.salt.data.SurveyDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BiologicalSampleCollectionScreen(
    surveyId: String,
    onSamplesConfirmed: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { SurveyDatabase.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    var enabledTests by remember { mutableStateOf<List<com.dev.salt.data.TestConfiguration>>(emptyList()) }
    var checkedTests by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isLoading by remember { mutableStateOf(true) }

    // Load enabled tests
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val tests = database.testConfigurationDao().getEnabledTestConfigurations(1L)
            enabledTests = tests
            isLoading = false
            Log.d("BiologicalSampleCollection", "Loaded ${tests.size} enabled tests")
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
                        contentDescription = "Sample Collection",
                        modifier = Modifier
                            .size(64.dp)
                            .padding(bottom = 16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )

                    // Title
                    Text(
                        text = "Biological Sample Collection",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Instructions
                    Text(
                        text = "Please take biological samples to perform each of the following tests. Check each one to confirm that they will be performed.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    if (isLoading) {
                        CircularProgressIndicator()
                    } else if (enabledTests.isEmpty()) {
                        Text(
                            text = "No rapid tests configured",
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
                                text = "Continue to Survey",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        if (!allChecked && enabledTests.isNotEmpty()) {
                            Text(
                                text = "Please check all tests to continue",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }

                    // Cancel button
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}
