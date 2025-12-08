package com.dev.salt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.dev.salt.AppDestinations
import android.util.Log
import androidx.compose.ui.res.stringResource
import com.dev.salt.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CouponIssuedScreen(
    navController: NavController,
    generatedCoupons: List<String>,
    surveyId: String? = null,
    database: com.dev.salt.data.SurveyDatabase? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val actualDatabase = database ?: com.dev.salt.data.SurveyDatabase.getInstance(context)

    // State for actual coupons - use parameter if available, otherwise load from database
    var actualCoupons by remember { mutableStateOf(generatedCoupons) }

    // Load coupons from database if parameter is empty
    LaunchedEffect(surveyId, generatedCoupons) {
        Log.d("CouponIssuedScreen", "=== COUPON ISSUED SCREEN LOADED ===")
        Log.d("CouponIssuedScreen", "surveyId: $surveyId")
        Log.d("CouponIssuedScreen", "generatedCoupons parameter: $generatedCoupons")

        if (generatedCoupons.isEmpty() && !surveyId.isNullOrBlank()) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val dbCoupons = actualDatabase.couponDao().getCouponsIssuedToSurvey(surveyId)
                Log.d("CouponIssuedScreen", "Loading from database - found ${dbCoupons.size} coupons: ${dbCoupons.map { it.couponCode }}")
                if (dbCoupons.isNotEmpty()) {
                    actualCoupons = dbCoupons.map { it.couponCode }
                }
            }
        } else {
            actualCoupons = generatedCoupons
        }
    }

    Scaffold(
        topBar = {
            SaltTopAppBar(
                title = stringResource(R.string.coupon_issued_title),
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
            
            // Success icon
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = stringResource(R.string.cd_success),
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            // Thank you message
            Text(
                text = stringResource(R.string.coupon_issued_thank_you),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                text = stringResource(R.string.coupon_issued_message),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            
            // Coupons section
            if (actualCoupons.isNotEmpty()) {
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
                            text = stringResource(R.string.coupon_issued_referral_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        
                        Text(
                            text = stringResource(R.string.coupon_issued_instructions, actualCoupons.size),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        
                        // Display each coupon with a number
                        actualCoupons.forEachIndexed { index, couponCode ->
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(R.string.coupon_issued_number, index + 1),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(bottom = 4.dp)
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
                                        text = couponCode,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 4.sp
                                        ),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        
                        Text(
                            text = stringResource(R.string.coupon_issued_usage),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            // Continue to payment button
            Button(
                onClick = {
                    if (!surveyId.isNullOrBlank()) {
                        scope.launch {
                            val surveyConfig = actualDatabase.surveyConfigDao().getSurveyConfig()
                            val facilityConfig = actualDatabase.facilityConfigDao().getFacilityConfig()
                            val survey = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                actualDatabase.surveyDao().getSurveyById(surveyId)
                            }

                            // Check if we should show walk-in recruitment payment instructions
                            val isWalkIn = survey?.referralCouponCode == null
                            val isFingerprintDisabled = surveyConfig?.fingerprintEnabled == false
                            val hasRecruitmentPayment = (facilityConfig?.recruitmentPaymentAmount ?: 0.0) > 0.0
                            val shouldShowWalkInInstructions = isWalkIn && isFingerprintDisabled && hasRecruitmentPayment

                            Log.d("CouponIssuedScreen", "Navigation decision:")
                            Log.d("CouponIssuedScreen", "  isWalkIn: $isWalkIn")
                            Log.d("CouponIssuedScreen", "  isFingerprintDisabled: $isFingerprintDisabled")
                            Log.d("CouponIssuedScreen", "  hasRecruitmentPayment: $hasRecruitmentPayment")
                            Log.d("CouponIssuedScreen", "  shouldShowWalkInInstructions: $shouldShowWalkInInstructions")

                            if (shouldShowWalkInInstructions) {
                                // Navigate to walk-in recruitment payment instructions
                                navController.navigate("${AppDestinations.WALKIN_RECRUITMENT_PAYMENT}/$surveyId") {
                                    popUpTo(AppDestinations.COUPON_ISSUED) { inclusive = true }
                                }
                            } else {
                                // Original logic: check for HIV test or go directly to payment
                                val isHivTestEnabled = surveyConfig?.hivRapidTestEnabled == true

                                if (isHivTestEnabled) {
                                    // Navigate to HIV test result screen
                                    navController.navigate("${AppDestinations.HIV_TEST_RESULT}/$surveyId") {
                                        popUpTo(AppDestinations.COUPON_ISSUED) { inclusive = true }
                                    }
                                } else {
                                    // Navigate to payment screen
                                    navController.navigate("${AppDestinations.SUBJECT_PAYMENT}/$surveyId?coupons=${actualCoupons.joinToString(",")}") {
                                        popUpTo(AppDestinations.COUPON_ISSUED) { inclusive = true }
                                    }
                                }
                            }
                        }
                    } else {
                        // No surveyId provided, error state
                        Log.e("CouponIssuedScreen", "No survey ID provided")
                    }
                },
                enabled = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = stringResource(R.string.coupon_issued_continue_payment),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}