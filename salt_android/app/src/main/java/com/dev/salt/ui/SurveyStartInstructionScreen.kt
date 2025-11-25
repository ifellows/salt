package com.dev.salt.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dev.salt.AppDestinations
import com.dev.salt.data.SurveyDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyStartInstructionScreen(
    navController: NavController,
    database: SurveyDatabase,
    surveyId: String,
    couponCode: String
) {
    val scope = rememberCoroutineScope()
    var staffEligibilityScreening by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    // Load staff eligibility screening setting from database
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            val surveyConfig = database.surveyConfigDao().getSurveyConfig()
            val screening = surveyConfig?.staffEligibilityScreening ?: false
            withContext(Dispatchers.Main) {
                staffEligibilityScreening = screening
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Survey Instructions") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
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
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Icon or visual indicator
                Surface(
                    modifier = Modifier
                        .size(80.dp)
                        .padding(bottom = 24.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (staffEligibilityScreening) "📋" else "📱",
                            style = MaterialTheme.typography.displayMedium
                        )
                    }
                }

                // Main instruction text - conditional based on staff screening setting
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (staffEligibilityScreening) {
                                "In a private area, the staff member will ask a few initial questions."
                            } else {
                                "In a private area, hand the tablet to the participant to begin the survey."
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Continue button
                Button(
                    onClick = {
                        navController.navigate("${AppDestinations.SURVEY}?couponCode=$couponCode&surveyId=$surveyId") {
                            popUpTo(AppDestinations.SURVEY_START_INSTRUCTION) { inclusive = true }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "Continue",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }
    }
}
