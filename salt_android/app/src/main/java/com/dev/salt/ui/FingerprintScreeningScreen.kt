package com.dev.salt.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dev.salt.AppDestinations
import com.dev.salt.data.SurveyDatabase
import com.dev.salt.fingerprint.FingerprintManager
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FingerprintScreeningScreen(
    navController: NavController,
    surveyId: String,
    couponCode: String?
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val database = remember { SurveyDatabase.getInstance(context) }
    val fingerprintManager = remember { FingerprintManager(database.subjectFingerprintDao()) }
    val scope = rememberCoroutineScope()
    
    var isCapturing by remember { mutableStateOf(false) }
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var daysUntilReEnrollment by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Load survey configuration
    var reEnrollmentDays by remember { mutableStateOf(90) }
    var facilityId by remember { mutableStateOf<Int?>(null) }
    
    LaunchedEffect(Unit) {
        val surveyConfig = database.surveyConfigDao().getSurveyConfig()
        reEnrollmentDays = surveyConfig?.reEnrollmentDays ?: 90
        // Get facility ID from facility config if needed
        val facilityConfig = database.facilityConfigDao().getFacilityConfig()
        facilityId = facilityConfig?.facilityId
    }
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Fingerprint Screening") },
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
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // Title and Instructions
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = "Fingerprint",
                    modifier = Modifier
                        .size(64.dp)
                        .padding(bottom = 16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = "Fingerprint Verification Required",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Text(
                    text = "Please follow the instructions below to capture the participant's fingerprint",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Visual Diagram showing fingerprint placement
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    FingerprintDiagram()
                }
            }
            
            // Instructions
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InstructionStep(1, "Ensure the SecuGen device is connected and the green light is on")
                InstructionStep(2, "Ask the participant to place their right index finger on the scanner")
                InstructionStep(3, "Press gently and hold until the scan completes")
                InstructionStep(4, "The device will beep when the scan is successful")
            }
            
            // Error message
            errorMessage?.let { message ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // Capture Button
            Button(
                onClick = {
                    scope.launch {
                        isCapturing = true
                        errorMessage = null
                        
                        // Initialize device
                        if (!fingerprintManager.initializeDevice()) {
                            errorMessage = "Failed to initialize fingerprint device"
                            isCapturing = false
                            return@launch
                        }
                        
                        // Capture fingerprint
                        val fingerprintHash = fingerprintManager.captureFingerprint()
                        
                        if (fingerprintHash == null) {
                            errorMessage = "Failed to capture fingerprint. Please try again."
                            isCapturing = false
                            fingerprintManager.closeDevice()
                            return@launch
                        }
                        
                        // Check for duplicate enrollment
                        val duplicate = fingerprintManager.checkDuplicateEnrollment(
                            fingerprintHash,
                            reEnrollmentDays
                        )
                        
                        if (duplicate != null) {
                            // Calculate days until re-enrollment allowed
                            val daysSinceEnrollment = TimeUnit.MILLISECONDS.toDays(
                                System.currentTimeMillis() - duplicate.enrollmentDate
                            )
                            daysUntilReEnrollment = (reEnrollmentDays - daysSinceEnrollment).toInt()
                            showDuplicateDialog = true
                        } else {
                            // Store fingerprint and proceed
                            fingerprintManager.storeFingerprint(surveyId, fingerprintHash, facilityId)
                            
                            // Navigate to language selection
                            val route = "${AppDestinations.LANGUAGE_SELECTION}/$surveyId?couponCode=${couponCode ?: ""}"
                            navController.navigate(route) {
                                popUpTo(AppDestinations.FINGERPRINT_SCREENING) { inclusive = true }
                            }
                        }
                        
                        fingerprintManager.closeDevice()
                        isCapturing = false
                    }
                },
                enabled = !isCapturing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (isCapturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        "Capture Fingerprint",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            
            // Skip button (for testing only - remove in production)
            if (false) { // Set to true for testing
                TextButton(
                    onClick = {
                        navController.navigate("${AppDestinations.LANGUAGE_SELECTION}/$surveyId?couponCode=${couponCode ?: ""}")
                    }
                ) {
                    Text("Skip (Testing Only)")
                }
            }
        }
    }
    
    // Duplicate Enrollment Dialog
    if (showDuplicateDialog) {
        AlertDialog(
            onDismissRequest = { },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    "Duplicate Enrollment Detected",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "This participant has already been enrolled in a survey recently.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        "They are not eligible for re-enrollment for another $daysUntilReEnrollment days.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Navigate back to menu
                        navController.navigate(AppDestinations.MENU) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                ) {
                    Text("Return to Menu")
                }
            }
        )
    }
}

@Composable
fun InstructionStep(number: Int, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            modifier = Modifier.size(32.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = number.toString(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun FingerprintDiagram() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Draw scanner representation
        val scannerWidth = size.width * 0.6f
        val scannerHeight = size.height * 0.7f
        val scannerX = (size.width - scannerWidth) / 2
        val scannerY = (size.height - scannerHeight) / 2
        
        // Scanner body
        drawRoundRect(
            color = Color.Gray,
            topLeft = Offset(scannerX, scannerY),
            size = androidx.compose.ui.geometry.Size(scannerWidth, scannerHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(20f, 20f),
            style = Stroke(width = 3f)
        )
        
        // Scanner glass
        drawRoundRect(
            color = Color.LightGray.copy(alpha = 0.3f),
            topLeft = Offset(scannerX + 20, scannerY + 20),
            size = androidx.compose.ui.geometry.Size(scannerWidth - 40, scannerHeight - 40),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
        )
        
        // Fingerprint representation
        drawCircle(
            color = Color.Blue.copy(alpha = 0.5f),
            radius = 30f,
            center = Offset(size.width / 2, size.height / 2)
        )
        
        // Fingerprint lines
        for (i in 1..3) {
            drawCircle(
                color = Color.Blue.copy(alpha = 0.3f),
                radius = 30f + (i * 8f),
                center = Offset(size.width / 2, size.height / 2),
                style = Stroke(width = 1.5f)
            )
        }
        
        // Arrow pointing to scanner
        val arrowStart = Offset(size.width / 2, scannerY - 30)
        val arrowEnd = Offset(size.width / 2, scannerY)
        drawLine(
            color = Color.Blue,
            start = arrowStart,
            end = arrowEnd,
            strokeWidth = 3f
        )
        
        // Arrow head
        drawLine(
            color = Color.Blue,
            start = arrowEnd,
            end = Offset(arrowEnd.x - 10, arrowEnd.y - 10),
            strokeWidth = 3f
        )
        drawLine(
            color = Color.Blue,
            start = arrowEnd,
            end = Offset(arrowEnd.x + 10, arrowEnd.y - 10),
            strokeWidth = 3f
        )
    }
}