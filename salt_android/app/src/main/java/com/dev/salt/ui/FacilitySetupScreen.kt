package com.dev.salt.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.dev.salt.R
import com.dev.salt.viewmodel.FacilitySetupViewModel
import com.dev.salt.viewmodel.FacilitySetupViewModelFactory
import kotlinx.coroutines.launch

@Composable
fun FacilitySetupScreen(
    navController: NavController,
    database: com.dev.salt.data.SurveyDatabase,
    showCancel: Boolean = false
) {
    val viewModel: FacilitySetupViewModel = viewModel(
        factory = FacilitySetupViewModelFactory(database)
    )
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.facility_setup_title),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = stringResource(R.string.facility_setup_instructions),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = uiState.shortCode,
            onValueChange = { viewModel.updateShortCode(it.uppercase()) },
            label = { Text(stringResource(R.string.facility_setup_code_label)) },
            placeholder = { Text(stringResource(R.string.facility_setup_code_placeholder)) },
            singleLine = true,
            enabled = !uiState.isLoading,
            isError = uiState.error != null,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp)
        )

        if (uiState.error != null) {
            Text(
                text = uiState.error!!,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp).padding(horizontal = 48.dp),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                scope.launch {
                    viewModel.setupFacility {
                        // Navigate to welcome screen on success
                        navController.navigate("welcome") {
                            popUpTo("facility_setup") { inclusive = true }
                        }
                    }
                }
            },
            enabled = uiState.shortCode.length >= 6 && !uiState.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 48.dp)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(stringResource(R.string.facility_setup_button))
            }
        }

        // Show cancel button if navigated from admin settings
        if (showCancel) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    navController.popBackStack()
                },
                enabled = !uiState.isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 48.dp)
            ) {
                Text(stringResource(R.string.common_cancel))
            }
        }

        if (uiState.successMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.padding(horizontal = 48.dp)
            ) {
                Text(
                    text = uiState.successMessage!!,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}