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
fun ManualDuplicateCheckScreen(
    navController: NavController,
    database: SurveyDatabase,
    surveyId: String,
    couponCode: String = ""
) {
    val scope = rememberCoroutineScope()
    var reEnrollmentDays by remember { mutableStateOf(90) }
    var isLoading by remember { mutableStateOf(true) }

    // Load re-enrollment period from database
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            val surveyConfig = database.surveyConfigDao().getSurveyConfig()
            val days = surveyConfig?.reEnrollmentDays ?: 90
            withContext(Dispatchers.Main) {
                reEnrollmentDays = days
                isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Participant Check") },
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
                // Main question
                Text(
                    text = "Has this person participated in this survey within the last $reEnrollmentDays days?",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Instruction text
                Text(
                    text = "If you are unsure, check with other staff members.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 48.dp)
                )

                // Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // "Participated or unsure" button - returns to menu
                    Button(
                        onClick = {
                            // Return to menu
                            navController.navigate(AppDestinations.MENU) {
                                popUpTo(AppDestinations.COUPON) { inclusive = true }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(
                            text = "Participated or Unsure",
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                    }

                    // "Never participated" button - continues to language selection
                    Button(
                        onClick = {
                            val destination = "${AppDestinations.LANGUAGE_SELECTION}/$surveyId?couponCode=$couponCode"
                            navController.navigate(destination) {
                                popUpTo(AppDestinations.COUPON) { inclusive = true }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = "Never Participated",
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
