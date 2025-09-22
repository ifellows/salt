package com.dev.salt.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dev.salt.AppDestinations
import com.dev.salt.data.SurveyDatabase
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactInfoScreen(
    navController: NavController,
    database: SurveyDatabase,
    surveyId: String,
    coupons: String
) {
    var contactType by remember { mutableStateOf("phone") }
    var phoneNumber by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Contact Information") },
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
            Text(
                text = "Please provide your contact information",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Text(
                text = "You can provide either a phone number or email address",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            
            // Contact type selection
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FilterChip(
                    selected = contactType == "phone",
                    onClick = { 
                        contactType = "phone"
                        showError = false
                    },
                    label = { Text("Phone Number") },
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                
                FilterChip(
                    selected = contactType == "email",
                    onClick = { 
                        contactType = "email"
                        showError = false
                    },
                    label = { Text("Email Address") },
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
            }
            
            // Phone number input
            if (contactType == "phone") {
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { 
                        phoneNumber = it
                        showError = false
                    },
                    label = { Text("Phone Number") },
                    placeholder = { Text("Enter your phone number") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    singleLine = true,
                    isError = showError,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // Email input
            if (contactType == "email") {
                OutlinedTextField(
                    value = email,
                    onValueChange = { 
                        email = it
                        showError = false
                    },
                    label = { Text("Email Address") },
                    placeholder = { Text("Enter your email address") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() }
                    ),
                    singleLine = true,
                    isError = showError,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            if (showError) {
                Text(
                    text = if (contactType == "phone") 
                        "Please enter a valid phone number" 
                    else 
                        "Please enter a valid email address",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Submit button
            Button(
                onClick = {
                    val contactInfo = if (contactType == "phone") phoneNumber else email
                    
                    if (contactInfo.isBlank()) {
                        showError = true
                        return@Button
                    }
                    
                    // Basic validation
                    val isValid = if (contactType == "phone") {
                        phoneNumber.length >= 10 && phoneNumber.any { it.isDigit() }
                    } else {
                        email.contains("@") && email.contains(".")
                    }
                    
                    if (!isValid) {
                        showError = true
                        return@Button
                    }
                    
                    // Save contact info
                    coroutineScope.launch {
                        isSaving = true
                        try {
                            // Store contact info in survey
                            val survey = database.surveyDao().getSurveyById(surveyId)
                            if (survey != null) {
                                val updatedSurvey = if (contactType == "phone") {
                                    survey.copy(contactPhone = phoneNumber, contactConsent = true)
                                } else {
                                    survey.copy(contactEmail = email, contactConsent = true)
                                }
                                database.surveyDao().updateSurvey(updatedSurvey)
                                
                                android.util.Log.d("ContactInfo", "Saved contact info for survey $surveyId: $contactType = $contactInfo")
                            }
                            
                            // Navigate to lab collection screen
                            navController.navigate("${AppDestinations.LAB_COLLECTION}/$surveyId?coupons=$coupons") {
                                popUpTo(AppDestinations.CONTACT_INFO) { inclusive = false }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ContactInfo", "Error saving contact info", e)
                            showError = true
                        } finally {
                            isSaving = false
                        }
                    }
                },
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        "Submit & Continue",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            
            // Skip button
            TextButton(
                onClick = {
                    navController.navigate("${AppDestinations.LAB_COLLECTION}/$surveyId?coupons=$coupons") {
                        popUpTo(AppDestinations.CONTACT_INFO) { inclusive = false }
                    }
                },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Skip")
            }
        }
    }
}