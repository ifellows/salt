package com.dev.salt.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.dev.salt.R
import androidx.activity.compose.BackHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandTabletBackScreen(
    navController: NavController,
    surveyId: String
) {
    // Disable hardware back button during survey flow
    BackHandler(enabled = true) {
        // Intentionally empty - back button is disabled during survey flow
    }

    Scaffold(
        topBar = {
            SaltTopAppBar(
                title = stringResource(R.string.hand_tablet_title),
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
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Tablet icon
            Icon(
                imageVector = Icons.Default.Tablet,
                contentDescription = stringResource(R.string.cd_tablet),
                modifier = Modifier.size(120.dp),
                tint = MaterialTheme.colorScheme.tertiary
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Main instruction
            Text(
                text = stringResource(R.string.hand_tablet_instruction),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                fontSize = 28.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Additional instruction
            Text(
                text = stringResource(R.string.hand_tablet_description),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Continue Survey button
            Button(
                onClick = {
                    // Navigate back to the survey (pop back to survey screen)
                    navController.popBackStack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Text(
                    text = stringResource(R.string.hand_tablet_continue_survey),
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = 20.sp
                )
            }
        }
    }
}