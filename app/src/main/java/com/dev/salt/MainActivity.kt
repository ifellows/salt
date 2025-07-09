package com.dev.salt


import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
// If you are using by viewModels() for Activity-level ViewModels, keep this:
// import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.dev.salt.data.SurveyDatabase
import com.dev.salt.ui.theme.SALTTheme
import com.dev.salt.viewmodel.LoginViewModel
import com.dev.salt.viewmodel.SurveyViewModel
import com.dev.salt.viewmodel.UserRole
import androidx.compose.runtime.LaunchedEffect
import com.dev.salt.viewmodel.LoginViewModelFactory
import android.util.Log
// Import your screen Composables if they are in separate files
// e.g., import com.dev.salt.ui.WelcomeScreen
// e.g., import com.dev.salt.ui.LoginScreen
// e.g., import com.dev.salt.ui.MenuScreen
// e.g., import com.dev.salt.ui.AdminDashboardScreen

object AppDestinations {
    const val WELCOME_SCREEN = "welcome"
    const val LOGIN_SCREEN = "login"
    const val MENU_SCREEN = "menu" // Assuming this is where survey_staff goes
    const val ADMIN_DASHBOARD_SCREEN = "admin_dashboard" // For administrators
    const val SURVEY_SCREEN = "survey" // For survey-related screens
    // ... other destinations
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
        val loginViewModelFactory = LoginViewModelFactory(userDao)

        setContent {
            SALTTheme {
                // Create a NavHostController
                val navController = rememberNavController()



                // Set up the NavHost
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
                        MenuScreen(navController = navController) // Pass ViewModel if needed
                    }
                    composable(AppDestinations.ADMIN_DASHBOARD_SCREEN) {
                        AdminDashboardScreen(/* pass necessary ViewModels or parameters */)
                    }

                    composable(AppDestinations.SURVEY_SCREEN) {
                        val context: Context = LocalContext.current
                        // Pass navController to SurveyScreen
                        val database = SurveyDatabase.getInstance(context)
                        val viewModel: SurveyViewModel = viewModel { SurveyViewModel(database) }
                        val coroutineScope = rememberCoroutineScope()
                        SurveyScreen(viewModel, coroutineScope)
                    }
                    // Add other composables for your survey, admin dashboard, etc.
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
fun MenuScreen(navController: NavHostController/* surveyViewModel: SurveyViewModel */) { // Example parameter
    Scaffold(
        topBar = { TopAppBar(title = { Text("Survey Staff Menu") }) }
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
            Button(onClick = {
                navController.navigate(AppDestinations.SURVEY_SCREEN)
            }) {
                Text("Start New Survey")
            }
            // Add other menu items
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AdminDashboardScreen() {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Administrator Dashboard") }) }
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
            Button(onClick = { /* Navigate to User Management, etc. */ }) {
                Text("Manage Users")
            }
            // Add other admin functions
        }
    }
}

