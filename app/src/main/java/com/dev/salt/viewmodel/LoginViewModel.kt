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

class LoginViewModel(private val userDao: UserDao) : ViewModel() { // Inject UserDao

    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var isLoading by mutableStateOf(false)
        private set // Only ViewModel should modify this
    var loginError by mutableStateOf<String?>(null)
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
}


// --- ViewModel Factory ---
// This factory is needed to create LoginViewModel instances with the UserDao dependency.
class LoginViewModelFactory(
    private val userDao: UserDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LoginViewModel(userDao) as T
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