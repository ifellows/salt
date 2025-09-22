package com.dev.salt


import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
// If you are using by viewModels() for Activity-level ViewModels, keep this:
// import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel // For viewModel() composable function
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.dev.salt.data.SurveyDatabase
import com.dev.salt.ui.theme.SALTTheme
import com.dev.salt.viewmodel.LoginViewModel
import com.dev.salt.viewmodel.SurveyViewModel
import com.dev.salt.viewmodel.UserRole
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.dev.salt.viewmodel.LoginViewModelFactory
import com.dev.salt.auth.BiometricAuthManager
import com.dev.salt.auth.BiometricAuthManagerFactory
import com.dev.salt.session.SessionManager
import com.dev.salt.session.SessionManagerInstance
import com.dev.salt.session.SessionEvent
import com.dev.salt.session.SurveyStateManagerInstance
import com.dev.salt.ui.SessionTimeoutDialog
import com.dev.salt.ui.SessionExpiredDialog
import com.dev.salt.ui.ActivityDetector
import com.dev.salt.ui.LogoutButton
import com.dev.salt.ui.ServerSettingsScreen
import com.dev.salt.ui.UploadStatusScreen
import com.dev.salt.upload.SurveyUploadWorkManager
import android.util.Log
// Import your screen Composables if they are in separate files
// e.g., import com.dev.salt.ui.WelcomeScreen
// e.g., import com.dev.salt.ui.LoginScreen
// e.g., import com.dev.salt.ui.MenuScreen
// e.g., import com.dev.salt.ui.AdminDashboardScreen

object AppDestinations {
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val MENU = "menu" // Assuming this is where survey_staff goes
    const val ADMIN_DASHBOARD = "admin_dashboard" // For administrators
    const val USER_MANAGEMENT = "user_management" // For user management
    const val SURVEY = "survey" // For survey-related screens
    const val SERVER_SETTINGS = "server_settings" // For server configuration
    const val UPLOAD_STATUS = "upload_status" // For upload status dashboard
    const val COUPON = "coupon" // For coupon validation
    const val COUPON_ISSUED = "coupon_issued" // For displaying issued coupons
    const val CONTACT_CONSENT = "contact_consent" // For asking about future contact
    const val CONTACT_INFO = "contact_info" // For collecting contact information
    const val SEED_RECRUITMENT = "seed_recruitment" // For seed recruitment screen
    const val LANGUAGE_SELECTION = "language_selection" // For language selection screen
    const val FINGERPRINT_SCREENING = "fingerprint_screening" // For fingerprint screening
    const val STAFF_VALIDATION = "staff_validation" // For staff validation/handoff
    const val LAB_COLLECTION = "lab_collection" // For lab sample collection
    const val STAFF_INSTRUCTION = "staff_instruction" // For staff instructions before giving tablet to participant
    const val SUBJECT_PAYMENT = "subject_payment" // For subject payment confirmation

    // Compatibility aliases for existing code
    const val WELCOME_SCREEN = WELCOME
    const val LOGIN_SCREEN = LOGIN
    const val MENU_SCREEN = MENU
    const val ADMIN_DASHBOARD_SCREEN = ADMIN_DASHBOARD
    const val USER_MANAGEMENT_SCREEN = USER_MANAGEMENT
    const val SURVEY_SCREEN = SURVEY
    const val SERVER_SETTINGS_SCREEN = SERVER_SETTINGS
    const val UPLOAD_STATUS_SCREEN = UPLOAD_STATUS
    const val COUPON_SCREEN = COUPON
    const val COUPON_ISSUED_SCREEN = COUPON_ISSUED
}
/*delete the database*/
/*database.clearAllTables()
val viewModel: SurveyViewModel = viewModel { SurveyViewModel(database) }
val surveyApplication = SurveyApplication()
surveyApplication.populateSampleData()
surveyApplication.copyRawFilesToLocalStorage(this)
val coroutineScope = rememberCoroutineScope()
SurveyScreen(viewModel, coroutineScope)*/

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Get DAO instance once here to pass to the factory
        // This assumes SurveyDatabase.getInstance() and userDao() are correctly set up
        val userDao = SurveyDatabase.getInstance(applicationContext).userDao()
        val biometricAuthManager = BiometricAuthManagerFactory.create(applicationContext, userDao)
        val sessionManager = SessionManagerInstance.instance
        val surveyStateManager = SurveyStateManagerInstance.instance
        val loginViewModelFactory = LoginViewModelFactory(userDao, biometricAuthManager, sessionManager)
        
        // Initialize upload work manager and schedule periodic retries
        val uploadWorkManager = SurveyUploadWorkManager(applicationContext)
        uploadWorkManager.schedulePeriodicRetries()

        setContent {
            SALTTheme {
                // Create a NavHostController
                val navController = rememberNavController()

                // Session management states
                val sessionState by sessionManager.sessionState.collectAsState()
                val sessionEvent by sessionManager.sessionEvents.collectAsState()
                val surveyState by surveyStateManager.surveyState.collectAsState()
                var showSessionWarning by remember { mutableStateOf(false) }
                var showSessionExpired by remember { mutableStateOf(false) }
                
                // Logout function
                val handleLogout = {
                    sessionManager.logout()
                    surveyStateManager.endSurvey()
                    navController.navigate(AppDestinations.LOGIN_SCREEN) {
                        popUpTo(navController.graph.startDestinationId) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                }

                // Handle session events
                LaunchedEffect(sessionEvent) {
                    when (sessionEvent) {
                        is SessionEvent.SessionWarning -> {
                            showSessionWarning = true
                        }
                        is SessionEvent.SessionExpired -> {
                            showSessionExpired = true
                            // Navigate to login screen
                            navController.navigate(AppDestinations.LOGIN_SCREEN) {
                                popUpTo(navController.graph.startDestinationId) {
                                    inclusive = true
                                }
                                launchSingleTop = true
                            }
                        }
                        else -> {}
                    }
                }

                // Session timeout warning dialog
                if (showSessionWarning) {
                    SessionTimeoutDialog(
                        timeUntilExpiration = sessionManager.getTimeUntilExpiration(),
                        onExtendSession = {
                            sessionManager.extendSession()
                            showSessionWarning = false
                            sessionManager.clearSessionEvent()
                        },
                        onLogout = {
                            sessionManager.endSession()
                            showSessionWarning = false
                            sessionManager.clearSessionEvent()
                            navController.navigate(AppDestinations.LOGIN_SCREEN) {
                                popUpTo(navController.graph.startDestinationId) {
                                    inclusive = true
                                }
                                launchSingleTop = true
                            }
                        }
                    )
                }

                // Session expired dialog
                if (showSessionExpired) {
                    SessionExpiredDialog(
                        onDismiss = {
                            showSessionExpired = false
                            sessionManager.clearSessionEvent()
                        }
                    )
                }

                // Set up the NavHost wrapped in ActivityDetector
                ActivityDetector {
                    NavHost(navController = navController, startDestination = AppDestinations.WELCOME_SCREEN) {
                    composable(AppDestinations.WELCOME_SCREEN) {
                        // delete the database
                        //database.clearAllTables()
                        val context: Context = LocalContext.current
                        val surveyApplication = SurveyApplication()
                        surveyApplication.populateSampleData()
                        surveyApplication.copyRawFilesToLocalStorage(context)

                        WelcomeScreen(navController = navController)
                    }
                    composable(AppDestinations.LOGIN_SCREEN) {
                        // Pass the LoginViewModel and the navigation callback
                        val loginViewModel: LoginViewModel = viewModel(
                            factory = loginViewModelFactory
                        )
                        LoginScreen(
                            loginViewModel = loginViewModel,
                            onLoginSuccess = { role ->
                                // Navigate to the appropriate screen based on user role
                                // It's good practice to clear the back stack up to the login screen
                                // or even further if login is a one-time entry point to a section.
                                val route = when (role) {
                                    UserRole.SURVEY_STAFF -> AppDestinations.MENU_SCREEN
                                    UserRole.ADMINISTRATOR -> AppDestinations.ADMIN_DASHBOARD_SCREEN
                                    UserRole.NONE -> AppDestinations.LOGIN_SCREEN // Or handle error, stay on login
                                }
                                navController.navigate(route) {
                                    // Pop up to the start destination of the graph to
                                    // avoid building up a large back stack.
                                    // Or popUpTo(AppDestinations.LOGIN_SCREEN) { inclusive = true }
                                    // if you want to remove LoginScreen from backstack.
                                    popUpTo(navController.graph.startDestinationId) {
                                        inclusive = true // Or false depending on whether you want to keep welcome screen
                                    }
                                    launchSingleTop = true // Avoid multiple copies of the destination
                                }
                            }
                        )
                    }
                    composable(AppDestinations.MENU_SCREEN) {
                        // You might need a SurveyViewModel here or other ViewModels
                        // val surveyViewModel: SurveyViewModel = viewModel()
                        MenuScreen(
                            navController = navController,
                            onLogout = handleLogout,
                            showLogout = !surveyState.isActive
                        )
                    }
                    composable(AppDestinations.ADMIN_DASHBOARD_SCREEN) {
                        AdminDashboardScreen(
                            navController = navController,
                            onLogout = handleLogout,
                            showLogout = !surveyState.isActive
                        )
                    }
                    composable(AppDestinations.USER_MANAGEMENT_SCREEN) {
                        val context = LocalContext.current
                        val userDao = SurveyDatabase.getInstance(context).userDao()
                        val biometricAuthManager = BiometricAuthManagerFactory.create(context, userDao)
                        val userManagementViewModel: com.dev.salt.viewmodel.UserManagementViewModel = viewModel {
                            com.dev.salt.viewmodel.UserManagementViewModel(userDao, biometricAuthManager)
                        }
                        UserManagementScreen(
                            viewModel = userManagementViewModel,
                            onLogout = handleLogout,
                            showLogout = !surveyState.isActive
                        )
                    }

                    composable(AppDestinations.COUPON_SCREEN) {
                        val context: Context = LocalContext.current
                        val database = SurveyDatabase.getInstance(context)
                        com.dev.salt.ui.CouponScreen(
                            navController = navController,
                            database = database
                        )
                    }
                    
                    composable(
                        route = AppDestinations.SURVEY_SCREEN + "?couponCode={couponCode}",
                        arguments = listOf(
                            androidx.navigation.navArgument("couponCode") {
                                type = androidx.navigation.NavType.StringType
                                nullable = true
                                defaultValue = null
                            }
                        )
                    ) { backStackEntry ->
                        val context: Context = LocalContext.current
                        // Pass navController to SurveyScreen
                        val database = SurveyDatabase.getInstance(context)
                        // Get the coupon code from navigation arguments
                        val referralCouponCode: String? = backStackEntry.arguments?.getString("couponCode")
                        
                        // Find the most recent survey that matches this referral coupon code
                        val scope = rememberCoroutineScope()
                        var surveyId by remember { mutableStateOf<String?>(null) }
                        var isLoading by remember { mutableStateOf(true) }
                        
                        LaunchedEffect(referralCouponCode) {
                            // Find the most recent survey (it should have been created at language selection)
                            Log.d("MainActivity", "Looking for most recent survey (coupon code passed: $referralCouponCode)")
                            val recentSurvey = database.surveyDao().getMostRecentSurvey()
                            surveyId = recentSurvey?.id
                            Log.d("MainActivity", "Found survey: $surveyId with language: ${recentSurvey?.language}")
                            isLoading = false
                        }
                        
                        if (!isLoading && surveyId != null) {
                            val viewModel: SurveyViewModel = viewModel(key = surveyId) { 
                                com.dev.salt.viewmodel.SurveyViewModelFactory(database, context, referralCouponCode, surveyId).create(SurveyViewModel::class.java)
                            }
                            val coroutineScope = rememberCoroutineScope()
                            SurveyScreen(
                                viewModel = viewModel, 
                                coroutineScope = coroutineScope,
                                onNavigateBack = { 
                                    // Navigate to contact consent screen when survey completes
                                    val generatedCoupons = viewModel.generatedCoupons.value
                                    val innerSurveyId = viewModel.survey?.id ?: ""
                                    Log.d("MainActivity", "Survey completed. Survey ID: $innerSurveyId, Coupons: ${generatedCoupons.size} - $generatedCoupons")
                                    
                                    // Navigate to staff validation first, then to contact consent
                                    val couponsParam = if (generatedCoupons.isNotEmpty()) {
                                        generatedCoupons.joinToString(",")
                                    } else {
                                        ""
                                    }
                                    Log.d("MainActivity", "Navigating to staff validation with coupons: $couponsParam")
                                    navController.navigate("${AppDestinations.STAFF_VALIDATION}/$innerSurveyId?coupons=$couponsParam") {
                                        popUpTo(AppDestinations.SURVEY_SCREEN) { inclusive = false }
                                    }
                                }
                            )
                        } else {
                            // Show loading while finding survey
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    
                    composable(
                        route = AppDestinations.COUPON_ISSUED_SCREEN + "?coupons={coupons}&surveyId={surveyId}",
                        arguments = listOf(
                            androidx.navigation.navArgument("coupons") {
                                type = androidx.navigation.NavType.StringType
                                defaultValue = ""
                            },
                            androidx.navigation.navArgument("surveyId") {
                                type = androidx.navigation.NavType.StringType
                                nullable = true
                                defaultValue = null
                            }
                        )
                    ) { backStackEntry ->
                        val couponsString = backStackEntry.arguments?.getString("coupons") ?: ""
                        val surveyId = backStackEntry.arguments?.getString("surveyId")
                        val coupons = if (couponsString.isNotEmpty()) {
                            couponsString.split(",")
                        } else {
                            emptyList()
                        }
                        com.dev.salt.ui.CouponIssuedScreen(
                            navController = navController,
                            generatedCoupons = coupons,
                            surveyId = surveyId,
                            database = SurveyDatabase.getInstance(this@MainActivity)
                        )
                    }
                    
                    composable(
                        route = "${AppDestinations.STAFF_VALIDATION}/{surveyId}?coupons={coupons}",
                        arguments = listOf(
                            navArgument("surveyId") { type = NavType.StringType },
                            navArgument("coupons") {
                                type = NavType.StringType
                                defaultValue = ""
                            }
                        )
                    ) { backStackEntry ->
                        val surveyId = backStackEntry.arguments?.getString("surveyId") ?: ""
                        val coupons = backStackEntry.arguments?.getString("coupons") ?: ""
                        com.dev.salt.StaffValidationScreen(
                            onValidationSuccess = {
                                // Navigate to contact consent after successful validation
                                navController.navigate("${AppDestinations.CONTACT_CONSENT}/$surveyId?coupons=$coupons") {
                                    popUpTo("${AppDestinations.STAFF_VALIDATION}/$surveyId") { inclusive = true }
                                }
                            },
                            onCancel = {
                                // Go back to menu or survey completion
                                navController.popBackStack()
                            }
                        )
                    }

                    composable(
                        route = "${AppDestinations.CONTACT_CONSENT}/{surveyId}?coupons={coupons}",
                        arguments = listOf(
                            navArgument("surveyId") { type = NavType.StringType },
                            navArgument("coupons") {
                                type = NavType.StringType
                                defaultValue = ""
                            }
                        )
                    ) { backStackEntry ->
                        val surveyId = backStackEntry.arguments?.getString("surveyId") ?: ""
                        val coupons = backStackEntry.arguments?.getString("coupons") ?: ""
                        com.dev.salt.ui.ContactConsentScreen(
                            navController = navController,
                            surveyId = surveyId,
                            coupons = coupons
                        )
                    }
                    
                    composable(
                        route = "${AppDestinations.CONTACT_INFO}/{surveyId}?coupons={coupons}",
                        arguments = listOf(
                            navArgument("surveyId") { type = NavType.StringType },
                            navArgument("coupons") { 
                                type = NavType.StringType
                                defaultValue = ""
                            }
                        )
                    ) { backStackEntry ->
                        val context: Context = LocalContext.current
                        val database = SurveyDatabase.getInstance(context)
                        val surveyId = backStackEntry.arguments?.getString("surveyId") ?: ""
                        val coupons = backStackEntry.arguments?.getString("coupons") ?: ""
                        com.dev.salt.ui.ContactInfoScreen(
                            navController = navController,
                            database = database,
                            surveyId = surveyId,
                            coupons = coupons
                        )
                    }
                    
                    composable(AppDestinations.SERVER_SETTINGS_SCREEN) {
                        ServerSettingsScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                    
                    composable(AppDestinations.UPLOAD_STATUS_SCREEN) {
                        UploadStatusScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }
                    
                    composable(AppDestinations.SEED_RECRUITMENT) {
                        com.dev.salt.ui.SeedRecruitmentScreen(
                            navController = navController
                        )
                    }
                    
                    composable(
                        route = "${AppDestinations.LAB_COLLECTION}/{surveyId}?coupons={coupons}",
                        arguments = listOf(
                            navArgument("surveyId") { type = NavType.StringType },
                            navArgument("coupons") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = ""
                            }
                        )
                    ) { backStackEntry ->
                        val context = LocalContext.current
                        val database = SurveyDatabase.getInstance(context)
                        val scope = rememberCoroutineScope()
                        val surveyId = backStackEntry.arguments?.getString("surveyId") ?: ""
                        val coupons = backStackEntry.arguments?.getString("coupons") ?: ""

                        // Get the survey to get subject ID
                        var subjectId by remember { mutableStateOf<String?>(null) }
                        LaunchedEffect(surveyId) {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                val survey = database.surveyDao().getSurveyById(surveyId)
                                subjectId = survey?.subjectId ?: ""
                                Log.d("MainActivity", "Lab Collection - Survey ID: $surveyId, Subject ID: $subjectId")
                            }
                        }

                        when (subjectId) {
                            null -> {
                                // Loading state
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                            "" -> {
                                // Error state - survey not found
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Error: Survey not found", color = androidx.compose.ui.graphics.Color.Red)
                                }
                            }
                            else -> {
                                // Show the lab collection screen
                                com.dev.salt.ui.LabCollectionScreen(
                                    subjectId = subjectId!!,
                                    onSamplesCollected = {
                                        // Update survey to record samples were collected, then navigate
                                        scope.launch {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                val survey = database.surveyDao().getSurveyById(surveyId)
                                                if (survey != null) {
                                                    val updatedSurvey = survey.copy(sampleCollected = true)
                                                    database.surveyDao().updateSurvey(updatedSurvey)
                                                    Log.d("MainActivity", "Updated survey $surveyId: sampleCollected = true")
                                                }
                                            }
                                            // Navigate to coupon issued screen after database update completes
                                            navController.navigate("${AppDestinations.COUPON_ISSUED}?coupons=$coupons&surveyId=$surveyId") {
                                                popUpTo("${AppDestinations.LAB_COLLECTION}/$surveyId") { inclusive = true }
                                            }
                                        }
                                    },
                                    onCancel = {
                                        // Update survey to record samples were refused, then navigate
                                        scope.launch {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                val survey = database.surveyDao().getSurveyById(surveyId)
                                                if (survey != null) {
                                                    val updatedSurvey = survey.copy(sampleCollected = false)
                                                    database.surveyDao().updateSurvey(updatedSurvey)
                                                    Log.d("MainActivity", "Updated survey $surveyId: sampleCollected = false")
                                                }
                                            }
                                            // Navigate to coupon issued screen after database update completes
                                            navController.navigate("${AppDestinations.COUPON_ISSUED}?coupons=$coupons&surveyId=$surveyId") {
                                                popUpTo("${AppDestinations.LAB_COLLECTION}/$surveyId") { inclusive = true }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    composable(
                        route = "${AppDestinations.FINGERPRINT_SCREENING}/{surveyId}?couponCode={couponCode}",
                        arguments = listOf(
                            navArgument("surveyId") { type = NavType.StringType },
                            navArgument("couponCode") { 
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            }
                        )
                    ) { backStackEntry ->
                        val surveyId = backStackEntry.arguments?.getString("surveyId") ?: ""
                        val couponCode = backStackEntry.arguments?.getString("couponCode")
                        com.dev.salt.ui.FingerprintScreeningScreen(
                            navController = navController,
                            surveyId = surveyId,
                            couponCode = couponCode
                        )
                    }
                    
                    composable(
                        route = "${AppDestinations.LANGUAGE_SELECTION}/{surveyId}?couponCode={couponCode}",
                        arguments = listOf(
                            navArgument("surveyId") { type = NavType.StringType },
                            navArgument("couponCode") { 
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            }
                        )
                    ) { backStackEntry ->
                        val surveyId = backStackEntry.arguments?.getString("surveyId") ?: ""
                        val couponCode = backStackEntry.arguments?.getString("couponCode")
                        com.dev.salt.ui.LanguageSelectionScreen(
                            navController = navController,
                            surveyId = surveyId,
                            couponCode = couponCode
                        )
                    }

                    composable(
                        route = "${AppDestinations.STAFF_INSTRUCTION}/{surveyId}",
                        arguments = listOf(
                            navArgument("surveyId") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val surveyId = backStackEntry.arguments?.getString("surveyId") ?: ""
                        com.dev.salt.ui.StaffInstructionScreen(
                            navController = navController,
                            surveyId = surveyId,
                            onContinue = {
                                // Navigate to the survey screen with the surveyId
                                navController.navigate("${AppDestinations.SURVEY}?couponCode=&surveyId=$surveyId") {
                                    popUpTo(AppDestinations.STAFF_INSTRUCTION) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(
                        route = "${AppDestinations.SUBJECT_PAYMENT}/{surveyId}?coupons={coupons}",
                        arguments = listOf(
                            navArgument("surveyId") { type = NavType.StringType },
                            navArgument("coupons") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = ""
                            }
                        )
                    ) { backStackEntry ->
                        val surveyId = backStackEntry.arguments?.getString("surveyId") ?: ""
                        val coupons = backStackEntry.arguments?.getString("coupons") ?: ""
                        com.dev.salt.ui.SubjectPaymentScreen(
                            navController = navController,
                            surveyId = surveyId,
                            coupons = coupons
                        )
                    }

                    // Add other composables for your survey, admin dashboard, etc.
                    }
                }
            }
        }
    }
}

// --- Placeholder Screen Composables ---
// If these are not in separate files, define them here or import them.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(navController: NavHostController) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Welcome to SALT") }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Welcome!", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { navController.navigate(AppDestinations.LOGIN_SCREEN) }) {
                Text("Login")
            }
            // You can add other buttons like "Register" or "Continue as Guest" if needed
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MenuScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    showLogout: Boolean = true
) {
    val context = LocalContext.current
    val database = remember { SurveyDatabase.getInstance(context) }
    val recruitmentManager = remember { com.dev.salt.util.SeedRecruitmentManager(database) }
    val scope = rememberCoroutineScope()
    var showSeedRecruitment by remember { mutableStateOf(false) }
    
    // Check if seed recruitment is allowed (recheck each time screen is shown)
    LaunchedEffect(navController.currentBackStackEntry) {
        Log.d("MenuScreen", "Checking seed recruitment availability...")
        showSeedRecruitment = recruitmentManager.isRecruitmentAllowed()
        Log.d("MenuScreen", "Show seed recruitment button: $showSeedRecruitment")
    }
    
    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("Survey Staff Menu") },
                actions = {
                    LogoutButton(
                        onLogout = onLogout,
                        isVisible = showLogout
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Survey Staff Area", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    navController.navigate(AppDestinations.COUPON_SCREEN)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start New Survey")
            }
            
            // Show seed recruitment button if allowed
            if (showSeedRecruitment) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        navController.navigate(AppDestinations.SEED_RECRUITMENT)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Recruit Previous Participant")
                }
            }
            // Add other menu items
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AdminDashboardScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    showLogout: Boolean = true
) {
    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("Administrator Dashboard") },
                actions = {
                    LogoutButton(
                        onLogout = onLogout,
                        isVisible = showLogout
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Admin Dashboard", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { navController.navigate(AppDestinations.USER_MANAGEMENT_SCREEN) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Manage Users")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { navController.navigate(AppDestinations.SERVER_SETTINGS_SCREEN) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Server Settings")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { navController.navigate(AppDestinations.UPLOAD_STATUS_SCREEN) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Upload Status")
            }
            // Add other admin functions
        }
    }
}

