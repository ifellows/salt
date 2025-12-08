package com.dev.salt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.dev.salt.AppDestinations
import com.dev.salt.R
import com.dev.salt.data.SurveyDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalkInRecruitmentPaymentScreen(
    navController: NavController,
    surveyId: String,
    database: SurveyDatabase? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val actualDatabase = database ?: SurveyDatabase.getInstance(context)

    var subjectId by remember { mutableStateOf<String?>(null) }

    // Load survey to get subject ID
    LaunchedEffect(surveyId) {
        Log.d("WalkInRecruitmentPayment", "Loading survey: $surveyId")
        withContext(Dispatchers.IO) {
            val survey = actualDatabase.surveyDao().getSurveyById(surveyId)
            subjectId = survey?.subjectId
            Log.d("WalkInRecruitmentPayment", "Subject ID: $subjectId")
        }
    }

    Scaffold(
        topBar = {
            SaltTopAppBar(
                title = stringResource(R.string.walkin_recruitment_title),
                navController = navController,
                showBackButton = false,
                showHomeButton = true
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Info icon
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = stringResource(R.string.cd_info),
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            // Instructions text
            Text(
                text = stringResource(R.string.walkin_recruitment_instructions),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            // Subject ID card
            if (subjectId != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.walkin_recruitment_your_code),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = subjectId!!,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 4.sp
                                ),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Continue button
            Button(
                onClick = {
                    scope.launch {
                        val config = actualDatabase.surveyConfigDao().getSurveyConfig()
                        val isHivTestEnabled = config?.hivRapidTestEnabled == true

                        if (isHivTestEnabled) {
                            // Navigate to HIV test result screen
                            navController.navigate("${AppDestinations.HIV_TEST_RESULT}/$surveyId") {
                                popUpTo(AppDestinations.WALKIN_RECRUITMENT_PAYMENT) { inclusive = true }
                            }
                        } else {
                            // Navigate to payment screen
                            navController.navigate("${AppDestinations.SUBJECT_PAYMENT}/$surveyId") {
                                popUpTo(AppDestinations.WALKIN_RECRUITMENT_PAYMENT) { inclusive = true }
                            }
                        }
                    }
                },
                enabled = subjectId != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = stringResource(R.string.walkin_recruitment_continue),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
