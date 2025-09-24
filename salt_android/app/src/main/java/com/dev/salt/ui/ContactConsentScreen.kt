package com.dev.salt.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dev.salt.AppDestinations
import androidx.compose.ui.res.stringResource
import com.dev.salt.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactConsentScreen(
    navController: NavController,
    surveyId: String,
    coupons: String
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.contact_consent_title)) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
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
            
            // No button - go to lab collection
            OutlinedButton(
                onClick = {
                    navController.navigate("${AppDestinations.LAB_COLLECTION}/$surveyId?coupons=$coupons") {
                        popUpTo(AppDestinations.CONTACT_CONSENT) { inclusive = false }
                    }
                },
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