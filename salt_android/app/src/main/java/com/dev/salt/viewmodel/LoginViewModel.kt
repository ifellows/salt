package com.dev.salt.viewmodel

import android.app.Application
import android.content.Context
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
import com.dev.salt.fingerprint.FingerprintManager
import com.dev.salt.data.SurveyDatabase
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import com.dev.salt.session.SessionManager
import com.dev.salt.session.SessionManagerInstance
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
    private val context: Context,
    private val userDao: UserDao,
    private val biometricAuthManager: BiometricAuthManager,
    private val sessionManager: SessionManager = SessionManagerInstance.instance
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
            val result = LoginResult(success = false, errorMessage = context.getString(com.dev.salt.R.string.login_error_empty))
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
                            
                            // Update session times in database
                            val currentTime = System.currentTimeMillis()
                            userDao.updateUserSessionTimes(user.userName, currentTime, currentTime)
                            
                            // Start session with user's custom timeout
                            val sessionTimeoutMs = user.sessionTimeoutMinutes * 60 * 1000L
                            sessionManager.startSession(user.userName, sessionTimeoutMs)
                            
                            LoginResult(success = true, role = role)
                        } else {
                            // Password does not match
                            LoginResult(success = false, errorMessage = context.getString(com.dev.salt.R.string.login_error_invalid_password))
                        }
                    } else {
                        // User not found
                        LoginResult(success = false, errorMessage = context.getString(com.dev.salt.R.string.login_error_invalid_username))
                    }
                } catch (e: Exception) {
                    // Catch any other exceptions during the process (e.g., DB issues)
                    Log.e("LoginViewModel", "Exception during login process for user: $username", e)
                    LoginResult(success = false, errorMessage = context.getString(com.dev.salt.R.string.login_error_unexpected))
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
     * Checks if fingerprint authentication is available for the entered username.
     */
    fun checkBiometricAvailability() {
        if (username.isBlank()) {
            showBiometricOption = false
            return
        }

        viewModelScope.launch {
            val user = withContext(Dispatchers.IO) {
                userDao.getUserByUserName(username)
            }

            // Check if user has a fingerprint template stored
            showBiometricOption = user?.fingerprintTemplate != null
            Log.d("LoginViewModel", "Fingerprint available for $username: $showBiometricOption")
        }
    }

    /**
     * Attempts fingerprint authentication for the entered username.
     */
    fun authenticateWithBiometric(onLoginComplete: (LoginResult) -> Unit) {
        if (username.isBlank()) {
            loginError = context.getString(com.dev.salt.R.string.login_error_invalid_username)
            return
        }

        viewModelScope.launch {
            isBiometricLoading = true
            loginError = null

            try {
                // Get the user and their stored template
                val user = withContext(Dispatchers.IO) {
                    userDao.getUserByUserName(username)
                }

                if (user == null || user.fingerprintTemplate == null) {
                    isBiometricLoading = false
                    loginError = "No fingerprint enrolled for this user"
                    onLoginComplete(LoginResult(success = false, errorMessage = "No fingerprint enrolled"))
                    return@launch
                }

                val database = SurveyDatabase.getInstance(context)
                val fingerprintManager = FingerprintManager(database.subjectFingerprintDao(), context)

                Log.i("LoginViewModel", "Starting fingerprint authentication for $username")

                // Check for USB device and permission
                val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                val deviceList = usbManager.deviceList
                var secugenDevice: UsbDevice? = null

                for ((_, device) in deviceList) {
                    if (device.vendorId == 0x1162) { // SecuGen vendor ID
                        secugenDevice = device
                        break
                    }
                }

                if (secugenDevice == null) {
                    isBiometricLoading = false
                    loginError = "Fingerprint scanner not connected"
                    onLoginComplete(LoginResult(success = false, errorMessage = "Scanner not connected"))
                    return@launch
                }

                if (!usbManager.hasPermission(secugenDevice)) {
                    Log.i("LoginViewModel", "Requesting USB permission")
                    val permissionIntent = PendingIntent.getBroadcast(
                        context,
                        0,
                        Intent("com.dev.salt.USB_PERMISSION").apply {
                            setPackage(context.packageName)
                        },
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            PendingIntent.FLAG_IMMUTABLE
                        } else {
                            0
                        }
                    )
                    usbManager.requestPermission(secugenDevice, permissionIntent)
                    isBiometricLoading = false
                    loginError = "Please grant USB permission and try again"
                    onLoginComplete(LoginResult(success = false, errorMessage = "USB permission required"))
                    return@launch
                }

                // Initialize device
                if (!fingerprintManager.initializeDevice()) {
                    isBiometricLoading = false
                    loginError = "Failed to initialize fingerprint scanner"
                    onLoginComplete(LoginResult(success = false, errorMessage = "Scanner initialization failed"))
                    return@launch
                }

                // Capture fingerprint
                val capturedTemplate = fingerprintManager.captureFingerprint()

                if (capturedTemplate == null) {
                    // Close device in background to avoid blocking UI
                    viewModelScope.launch(Dispatchers.IO) {
                        fingerprintManager.closeDevice()
                    }
                    isBiometricLoading = false
                    loginError = "Failed to capture fingerprint"
                    onLoginComplete(LoginResult(success = false, errorMessage = "Fingerprint capture failed"))
                    return@launch
                }

                // Match templates
                val matchResult = fingerprintManager.matchTemplates(capturedTemplate, user.fingerprintTemplate)

                // Close device in background to avoid blocking UI thread with GC
                viewModelScope.launch(Dispatchers.IO) {
                    fingerprintManager.closeDevice()
                }

                if (matchResult) {
                    Log.i("LoginViewModel", "Fingerprint match successful for $username")

                    // Get user role for successful auth
                    val role = try {
                        UserRole.valueOf(user.role.uppercase())
                    } catch (e: IllegalArgumentException) {
                        UserRole.NONE
                    }

                    // Update session times in database
                    val currentTime = System.currentTimeMillis()
                    userDao.updateUserSessionTimes(user.userName, currentTime, currentTime)

                    // Start session with user's custom timeout
                    val sessionTimeoutMs = user.sessionTimeoutMinutes * 60 * 1000L
                    sessionManager.startSession(user.userName, sessionTimeoutMs)

                    isBiometricLoading = false
                    val loginResult = LoginResult(success = true, role = role)
                    onLoginComplete(loginResult)
                } else {
                    Log.w("LoginViewModel", "Fingerprint match failed for $username")
                    isBiometricLoading = false
                    loginError = "Fingerprint does not match"
                    onLoginComplete(LoginResult(success = false, errorMessage = "Fingerprint authentication failed"))
                }
            } catch (e: Exception) {
                isBiometricLoading = false
                loginError = "Fingerprint authentication error: ${e.message}"
                onLoginComplete(LoginResult(success = false, errorMessage = "Authentication error"))
                Log.e("LoginViewModel", "Fingerprint authentication error", e)
            }
        }
    }
}


// --- ViewModel Factory ---
// This factory is needed to create LoginViewModel instances with the UserDao and BiometricAuthManager dependencies.
class LoginViewModelFactory(
    private val context: Context,
    private val userDao: UserDao,
    private val biometricAuthManager: BiometricAuthManager,
    private val sessionManager: SessionManager = SessionManagerInstance.instance
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LoginViewModel(context, userDao, biometricAuthManager, sessionManager) as T
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