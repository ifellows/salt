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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.dev.salt.AppDestinations
import com.dev.salt.data.SurveyDatabase
import com.dev.salt.viewmodel.CouponViewModel
import com.dev.salt.viewmodel.CouponViewModelFactory
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.GlobalScope
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CouponScreen(
    navController: NavController,
    database: SurveyDatabase
) {
    val viewModel: CouponViewModel = viewModel(
        factory = CouponViewModelFactory(database)
    )
    
    // State for facility configuration
    var facilityConfig by remember { mutableStateOf<com.dev.salt.data.FacilityConfig?>(null) }
    var isSyncing by remember { mutableStateOf(true) }
    
    // Sync facility configuration when the screen loads
    LaunchedEffect(Unit) {
        // Facility config is now synced at login, just load from database
        facilityConfig = database.facilityConfigDao().getFacilityConfig() 
            ?: com.dev.salt.data.FacilityConfig()
        isSyncing = false
    }
    
    val allowNonCoupon = facilityConfig?.allowNonCouponParticipants ?: true
    
    val hasCoupon by viewModel.hasCoupon.collectAsState()
    val couponCode by viewModel.couponCode.collectAsState()
    val validationError by viewModel.validationError.collectAsState()
    val isValidating by viewModel.isValidating.collectAsState()
    val focusManager = LocalFocusManager.current
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Survey Registration") },
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
            // Show loading indicator while syncing
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = "Syncing facility settings...",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            } else {
                // Coupon question
                Text(
                    text = "Does this person have a coupon?",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 32.dp)
                )
            
            // Show error if non-coupon participants not allowed
            if (hasCoupon == false && !allowNonCoupon && validationError != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "Participation requires a valid coupon code. Please ask for assistance.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                
                // Add back button
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { 
                        navController.popBackStack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text("Back to Menu", style = MaterialTheme.typography.titleMedium)
                }
            }
            
            // Yes/No buttons
            if (hasCoupon == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = { viewModel.setHasCoupon(true) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                            .height(56.dp)
                    ) {
                        Text("Yes", style = MaterialTheme.typography.titleMedium)
                    }
                    
                    Button(
                        onClick = { 
                            viewModel.setHasCoupon(false)
                            if (allowNonCoupon) {
                                // Create a new survey and navigate to language selection
                                GlobalScope.launch(Dispatchers.IO) {
                                    val surveyId = UUID.randomUUID().toString()
                                    val survey = com.dev.salt.data.Survey(
                                        id = surveyId,
                                        subjectId = UUID.randomUUID().toString(),
                                        startDatetime = System.currentTimeMillis(),
                                        language = "en", // Will be updated in language selection
                                        referralCouponCode = null
                                    )
                                    database.surveyDao().insertSurvey(survey)
                                    
                                    withContext(Dispatchers.Main) {
                                        navController.navigate("${AppDestinations.LANGUAGE_SELECTION}/$surveyId?couponCode=") {
                                            popUpTo(AppDestinations.COUPON) { inclusive = true }
                                        }
                                    }
                                }
                            } else {
                                // Show error message or navigate back
                                viewModel.setNoCouponError()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (allowNonCoupon) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("No", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            
            // Coupon code entry
            if (hasCoupon == true) {
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    text = "Enter Coupon Code",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                OutlinedTextField(
                    value = couponCode,
                    onValueChange = { 
                        viewModel.setCouponCode(it.uppercase())
                        viewModel.clearError()
                    },
                    label = { Text("Coupon Code") },
                    placeholder = { Text("Enter 6-character code") },
                    isError = validationError != null,
                    supportingText = {
                        if (validationError != null) {
                            Text(
                                text = validationError ?: "",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            viewModel.validateCoupon { isValid ->
                                if (isValid) {
                                    val couponCode = viewModel.validatedCouponCode ?: ""
                                    // Create a new survey and navigate to language selection
                                    GlobalScope.launch(Dispatchers.IO) {
                                        val surveyId = UUID.randomUUID().toString()
                                        val survey = com.dev.salt.data.Survey(
                                            id = surveyId,
                                            subjectId = UUID.randomUUID().toString(),
                                            startDatetime = System.currentTimeMillis(),
                                            language = "en", // Will be updated in language selection
                                            referralCouponCode = couponCode
                                        )
                                        database.surveyDao().insertSurvey(survey)
                                        
                                        withContext(Dispatchers.Main) {
                                            navController.navigate("${AppDestinations.LANGUAGE_SELECTION}/$surveyId?couponCode=$couponCode") {
                                                popUpTo(AppDestinations.COUPON) { inclusive = true }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    OutlinedButton(
                        onClick = { 
                            viewModel.setHasCoupon(null)
                            viewModel.setCouponCode("")
                            viewModel.clearError()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                            .height(48.dp)
                    ) {
                        Text("Back")
                    }
                    
                    Button(
                        onClick = {
                            viewModel.validateCoupon { isValid ->
                                if (isValid) {
                                    val couponCode = viewModel.validatedCouponCode ?: ""
                                    // Create a new survey and navigate to language selection
                                    GlobalScope.launch(Dispatchers.IO) {
                                        val surveyId = UUID.randomUUID().toString()
                                        val survey = com.dev.salt.data.Survey(
                                            id = surveyId,
                                            subjectId = UUID.randomUUID().toString(),
                                            startDatetime = System.currentTimeMillis(),
                                            language = "en", // Will be updated in language selection
                                            referralCouponCode = couponCode
                                        )
                                        database.surveyDao().insertSurvey(survey)
                                        
                                        withContext(Dispatchers.Main) {
                                            navController.navigate("${AppDestinations.LANGUAGE_SELECTION}/$surveyId?couponCode=$couponCode") {
                                                popUpTo(AppDestinations.COUPON) { inclusive = true }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        enabled = couponCode.isNotBlank() && !isValidating,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp)
                            .height(48.dp)
                    ) {
                        if (isValidating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Continue")
                        }
                    }
                }
            }
            } // Close the else block for isSyncing
        }
    }
}