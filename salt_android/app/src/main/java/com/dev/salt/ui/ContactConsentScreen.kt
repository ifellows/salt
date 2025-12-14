package com.dev.salt.ui

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dev.salt.AppDestinations
import androidx.compose.ui.res.stringResource
import com.dev.salt.R
import com.dev.salt.data.SurveyDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactConsentScreen(
    navController: NavController,
    surveyId: String,
    coupons: String
) {
    val context = LocalContext.current
    val database = remember { SurveyDatabase.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    var enabledTestsCount by remember { mutableStateOf<Int?>(null) }

    // Check if rapid tests are enabled
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            // Get the actual survey ID from sections
            val sections = database.sectionDao().getAllSections()
            val actualSurveyId = sections.firstOrNull()?.surveyId?.toLong() ?: -1L

            if (actualSurveyId == -1L) {
                Log.e("ContactConsentScreen", "CRITICAL ERROR: No sections found in database - survey not properly synced!")
                Log.e("ContactConsentScreen", "Using fallback survey ID -1, no tests will be found")
            }

            val tests = database.testConfigurationDao().getEnabledTestConfigurations(actualSurveyId)
            enabledTestsCount = tests.size
            Log.d("ContactConsentScreen", "Found ${tests.size} enabled tests for survey ID $actualSurveyId")
        }
    }

    // Helper function to navigate to next screen
    val navigateNext: () -> Unit = {
        coroutineScope.launch {
            val testsEnabled = enabledTestsCount ?: 0
            if (testsEnabled > 0) {
                // Navigate to first test result entry
                navController.navigate("${AppDestinations.RAPID_TEST_RESULT}/$surveyId/0?coupons=$coupons") {
                    popUpTo(AppDestinations.CONTACT_CONSENT) { inclusive = false }
                }
            } else {
                // No tests, go directly to lab collection
                navController.navigate("${AppDestinations.LAB_COLLECTION}/$surveyId?coupons=$coupons") {
                    popUpTo(AppDestinations.CONTACT_CONSENT) { inclusive = false }
                }
            }
        }
    }
    Scaffold(
        topBar = {
            SaltTopAppBar(
                title = stringResource(R.string.contact_consent_title),
                navController = navController,
                showBackButton = true,
                showHomeButton = true
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.ContactPhone,
                contentDescription = stringResource(R.string.cd_contact),
                modifier = Modifier
                    .size(64.dp)
                    .padding(bottom = 24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = stringResource(R.string.contact_consent_question),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = stringResource(R.string.contact_consent_privacy),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            
            // Yes button - proceed to contact info collection
            Button(
                onClick = {
                    navController.navigate("${AppDestinations.CONTACT_INFO}/$surveyId?coupons=$coupons")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    stringResource(R.string.contact_consent_yes),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            // No button - navigate to test results or lab collection
            OutlinedButton(
                onClick = navigateNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    stringResource(R.string.contact_consent_no),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}