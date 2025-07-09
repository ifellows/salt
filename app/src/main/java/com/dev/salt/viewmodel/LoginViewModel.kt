package com.dev.salt.viewmodel

import android.app.Application // Only needed if you don't inject UserDao and use AndroidViewModel as fallback
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.role
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
// Remove direct DB dependency if UserDao is injected:
// import com.dev.salt.db.SurveyDatabase
import com.dev.salt.data.UserDao // Import your UserDao
import com.dev.salt.PasswordUtils // Import the best practice utility
import com.dev.salt.auth.BiometricAuthManager
import com.dev.salt.auth.BiometricResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.text.uppercase

// Define user roles (ensure this matches your application's needs)
enum class UserRole {
    SURVEY_STAFF,
    ADMINISTRATOR,
    NONE // For unauthenticated or error
}

// Define LoginResult (ensure this matches your application's needs)
data class LoginResult(
    val success: Boolean,
    val role: UserRole = UserRole.NONE,
    val errorMessage: String? = null
)

class LoginViewModel(
    private val userDao: UserDao,
    private val biometricAuthManager: BiometricAuthManager
) : ViewModel() {

    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var isLoading by mutableStateOf(false)
        private set // Only ViewModel should modify this
    var loginError by mutableStateOf<String?>(null)
        private set // Only ViewModel should modify this
    var showBiometricOption by mutableStateOf(false)
        private set // Only ViewModel should modify this
    var isBiometricLoading by mutableStateOf(false)
        private set // Only ViewModel should modify this

    fun login(onLoginComplete: (LoginResult) -> Unit) {
        if (username.isBlank() || password.isBlank()) {
            val result = LoginResult(success = false, errorMessage = "Username and password cannot be empty.")
            loginError = result.errorMessage // Update UI state
            onLoginComplete(result)
            return
        }

        viewModelScope.launch {
            isLoading = true
            loginError = null

            val result: LoginResult = withContext(Dispatchers.IO) { // Perform DB operations on IO thread
                try {
                    val user = userDao.getUserByUserName(username) // Fetch user from DB via DAO

                    if (user != null) {
                        // User found, verify password using the stored salt and hash
                        // PasswordUtils.verifyPassword expects "saltBase64:hashBase64" from user.hashedPassword
                        if (PasswordUtils.verifyPassword(password, user.hashedPassword)) {
                            // Password matches
                            val role = try {
                                UserRole.valueOf(user.role.uppercase()) // Convert stored role string to enum
                            } catch (e: IllegalArgumentException) {
                                Log.w("LoginViewModel", "Invalid role string in DB for user ${user.userName}: ${user.role}")
                                UserRole.NONE // Default or error role if string doesn't match enum
                            }
                            LoginResult(success = true, role = role)
                        } else {
                            // Password does not match
                            LoginResult(success = false, errorMessage = "Invalid password.")
                        }
                    } else {
                        // User not found
                        LoginResult(success = false, errorMessage = "Invalid username.")
                    }
                } catch (e: Exception) {
                    // Catch any other exceptions during the process (e.g., DB issues)
                    Log.e("LoginViewModel", "Exception during login process for user: $username", e)
                    LoginResult(success = false, errorMessage = "An unexpected error occurred. Please try again.")
                }
            }

            isLoading = false // Update loading state
            if (!result.success) {
                loginError = result.errorMessage // Update error state for UI
            }
            onLoginComplete(result) // Callback with the result
        }
    }

    fun clearError() {
        loginError = null
    }

    /**
     * Checks if biometric authentication is available for the entered username.
     */
    fun checkBiometricAvailability() {
        if (username.isBlank()) {
            showBiometricOption = false
            return
        }

        viewModelScope.launch {
            val isSupported = biometricAuthManager.isBiometricSupported()
            val isEnabledForUser = biometricAuthManager.isBiometricEnabledForUser(username)
            
            showBiometricOption = isSupported && isEnabledForUser
            Log.d("LoginViewModel", "Biometric available for $username: $showBiometricOption")
        }
    }

    /**
     * Attempts biometric authentication for the entered username.
     */
    fun authenticateWithBiometric(onLoginComplete: (LoginResult) -> Unit) {
        if (username.isBlank()) {
            loginError = "Please enter username first"
            return
        }

        viewModelScope.launch {
            isBiometricLoading = true
            loginError = null

            try {
                // Show biometric prompt (mock implementation)
                biometricAuthManager.showBiometricPrompt(
                    title = "Biometric Authentication",
                    subtitle = "Use your fingerprint to login as $username"
                ) { result ->
                    when (result) {
                        is BiometricResult.Success -> {
                            // Biometric prompt succeeded, now verify against stored key
                            viewModelScope.launch {
                                biometricAuthManager.authenticateUserBiometric(username) { authResult ->
                                    isBiometricLoading = false
                                    
                                    when (authResult) {
                                        is BiometricResult.Success -> {
                                            // Get user role for successful biometric auth
                                            viewModelScope.launch {
                                                val user = withContext(Dispatchers.IO) {
                                                    userDao.getUserByUserName(username)
                                                }
                                                
                                                if (user != null) {
                                                    val role = try {
                                                        UserRole.valueOf(user.role.uppercase())
                                                    } catch (e: IllegalArgumentException) {
                                                        UserRole.NONE
                                                    }
                                                    
                                                    val loginResult = LoginResult(success = true, role = role)
                                                    onLoginComplete(loginResult)
                                                } else {
                                                    loginError = "User not found"
                                                    onLoginComplete(LoginResult(success = false, errorMessage = "User not found"))
                                                }
                                            }
                                        }
                                        is BiometricResult.Error -> {
                                            loginError = authResult.message
                                            onLoginComplete(LoginResult(success = false, errorMessage = authResult.message))
                                        }
                                        else -> {
                                            loginError = "Biometric authentication failed"
                                            onLoginComplete(LoginResult(success = false, errorMessage = "Biometric authentication failed"))
                                        }
                                    }
                                }
                            }
                        }
                        is BiometricResult.Error -> {
                            isBiometricLoading = false
                            loginError = result.message
                            onLoginComplete(LoginResult(success = false, errorMessage = result.message))
                        }
                        is BiometricResult.UserCancelled -> {
                            isBiometricLoading = false
                            // Don't show error for user cancellation
                        }
                        else -> {
                            isBiometricLoading = false
                            loginError = "Biometric authentication not available"
                            onLoginComplete(LoginResult(success = false, errorMessage = "Biometric authentication not available"))
                        }
                    }
                }
            } catch (e: Exception) {
                isBiometricLoading = false
                loginError = "Biometric authentication error: ${e.message}"
                onLoginComplete(LoginResult(success = false, errorMessage = "Authentication error"))
                Log.e("LoginViewModel", "Biometric authentication error", e)
            }
        }
    }
}


// --- ViewModel Factory ---
// This factory is needed to create LoginViewModel instances with the UserDao and BiometricAuthManager dependencies.
class LoginViewModelFactory(
    private val userDao: UserDao,
    private val biometricAuthManager: BiometricAuthManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LoginViewModel(userDao, biometricAuthManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}


/*package com.dev.salt.viewmodel // Or your ViewModel package

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Define user roles
enum class UserRole {
    SURVEY_STAFF,
    ADMINISTRATOR,
    NONE // For unauthenticated or error
}

data class LoginResult(
    val success: Boolean,
    val role: UserRole = UserRole.NONE,
    val errorMessage: String? = null
)



class LoginViewModel : ViewModel() {
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var isLoading by mutableStateOf(false)
        private set
    var loginError by mutableStateOf<String?>(null)
        private set

    // This would typically involve a call to a backend or a local database
    // For this example, we'll simulate it.
    fun login(onLoginComplete: (LoginResult) -> Unit) {
        viewModelScope.launch {
            isLoading = true
            loginError = null

            // --- Replace this with actual authentication logic ---
            val result = when {
                username == "staff" && password == "123" -> {
                    LoginResult(success = true, role = UserRole.SURVEY_STAFF)
                }
                username == "admin" && password == "123" -> {
                    LoginResult(success = true, role = UserRole.ADMINISTRATOR)
                }
                username.isBlank() || password.isBlank() -> {
                    LoginResult(success = false, errorMessage = "Username and password cannot be empty.")
                }
                else -> {
                    LoginResult(success = false, errorMessage = "Invalid username or password.")
                }
            }
            // --- End of simulation ---

            isLoading = false
            if (!result.success) {
                loginError = result.errorMessage
            }
            onLoginComplete(result)
        }
    }

    fun clearError() {
        loginError = null
    }
}*/