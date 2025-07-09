package com.dev.salt.viewmodel

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dev.salt.PasswordUtils
import com.dev.salt.data.User
import com.dev.salt.data.UserDao
import com.dev.salt.auth.BiometricAuthManager
import com.dev.salt.auth.BiometricResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UserManagementState(
    val users: List<User> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isAddingUser: Boolean = false,
    val isDeletingUser: Boolean = false
)

class UserManagementViewModel(
    private val userDao: UserDao,
    private val biometricAuthManager: BiometricAuthManager
) : ViewModel() {
    
    var state by mutableStateOf(UserManagementState())
        private set

    var newUserName by mutableStateOf("")
    var newUserFullName by mutableStateOf("")
    var newUserPassword by mutableStateOf("")
    var newUserRole by mutableStateOf("SURVEY_STAFF")
    var showAddUserDialog by mutableStateOf(false)
    var showDeleteConfirmDialog by mutableStateOf(false)
    var userToDelete by mutableStateOf<User?>(null)
    var showBiometricEnrollDialog by mutableStateOf(false)
    var showBiometricDisableDialog by mutableStateOf(false)
    var userForBiometricAction by mutableStateOf<User?>(null)

    init {
        loadUsers()
    }

    fun loadUsers() {
        viewModelScope.launch {
            state = state.copy(isLoading = true, error = null)
            try {
                val users = withContext(Dispatchers.IO) {
                    userDao.getAllUsers()
                }
                state = state.copy(users = users, isLoading = false)
            } catch (e: Exception) {
                state = state.copy(
                    error = "Failed to load users: ${e.message}",
                    isLoading = false
                )
                Log.e("UserManagementViewModel", "Error loading users", e)
            }
        }
    }

    fun addUser() {
        if (newUserName.isBlank() || newUserFullName.isBlank() || newUserPassword.isBlank()) {
            state = state.copy(error = "All fields are required")
            return
        }

        if (newUserPassword.length < 6) {
            state = state.copy(error = "Password must be at least 6 characters")
            return
        }

        viewModelScope.launch {
            state = state.copy(isAddingUser = true, error = null)
            try {
                withContext(Dispatchers.IO) {
                    // Check if user already exists
                    val existingUser = userDao.getUserByUserName(newUserName)
                    if (existingUser != null) {
                        state = state.copy(
                            error = "User with username '$newUserName' already exists",
                            isAddingUser = false
                        )
                        return@withContext
                    }

                    // Hash the password
                    val hashedPassword = PasswordUtils.hashPasswordWithNewSalt(newUserPassword)
                    if (hashedPassword == null) {
                        state = state.copy(
                            error = "Failed to hash password",
                            isAddingUser = false
                        )
                        return@withContext
                    }

                    // Create and insert user
                    val user = User(
                        userName = newUserName,
                        hashedPassword = hashedPassword,
                        fullName = newUserFullName,
                        role = newUserRole
                    )
                    userDao.insertUser(user)
                }

                // Clear form and reload users
                clearAddUserForm()
                loadUsers()
                state = state.copy(isAddingUser = false)
                showAddUserDialog = false
            } catch (e: Exception) {
                state = state.copy(
                    error = "Failed to add user: ${e.message}",
                    isAddingUser = false
                )
                Log.e("UserManagementViewModel", "Error adding user", e)
            }
        }
    }

    fun deleteUser(user: User) {
        viewModelScope.launch {
            state = state.copy(isDeletingUser = true, error = null)
            try {
                withContext(Dispatchers.IO) {
                    // Check if this is the last admin user
                    if (user.role == "ADMINISTRATOR") {
                        val adminCount = userDao.getAdminCount()
                        if (adminCount <= 1) {
                            state = state.copy(
                                error = "Cannot delete the last administrator account",
                                isDeletingUser = false
                            )
                            return@withContext
                        }
                    }

                    userDao.deleteUser(user.userName)
                }

                loadUsers()
                state = state.copy(isDeletingUser = false)
                showDeleteConfirmDialog = false
                userToDelete = null
            } catch (e: Exception) {
                state = state.copy(
                    error = "Failed to delete user: ${e.message}",
                    isDeletingUser = false
                )
                Log.e("UserManagementViewModel", "Error deleting user", e)
            }
        }
    }

    fun showAddUserDialog() {
        clearAddUserForm()
        showAddUserDialog = true
    }

    fun hideAddUserDialog() {
        showAddUserDialog = false
        clearAddUserForm()
    }

    fun showDeleteConfirmDialog(user: User) {
        userToDelete = user
        showDeleteConfirmDialog = true
    }

    fun hideDeleteConfirmDialog() {
        showDeleteConfirmDialog = false
        userToDelete = null
    }

    fun showBiometricEnrollDialog(user: User) {
        userForBiometricAction = user
        showBiometricEnrollDialog = true
    }

    fun hideBiometricEnrollDialog() {
        showBiometricEnrollDialog = false
        userForBiometricAction = null
    }

    fun showBiometricDisableDialog(user: User) {
        userForBiometricAction = user
        showBiometricDisableDialog = true
    }

    fun hideBiometricDisableDialog() {
        showBiometricDisableDialog = false
        userForBiometricAction = null
    }

    fun clearError() {
        state = state.copy(error = null)
    }

    private fun clearAddUserForm() {
        newUserName = ""
        newUserFullName = ""
        newUserPassword = ""
        newUserRole = "SURVEY_STAFF"
    }

    fun updateUserRole(user: User, newRole: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Check if changing admin role would leave no admins
                    if (user.role == "ADMINISTRATOR" && newRole != "ADMINISTRATOR") {
                        val adminCount = userDao.getAdminCount()
                        if (adminCount <= 1) {
                            state = state.copy(
                                error = "Cannot change role: would leave no administrator accounts"
                            )
                            return@withContext
                        }
                    }

                    userDao.updateUserRole(user.userName, newRole)
                }
                loadUsers()
            } catch (e: Exception) {
                state = state.copy(error = "Failed to update user role: ${e.message}")
                Log.e("UserManagementViewModel", "Error updating user role", e)
            }
        }
    }

    fun enrollUserBiometric(user: User) {
        viewModelScope.launch {
            try {
                val result = biometricAuthManager.enrollUserBiometric(user.userName)
                when (result) {
                    is BiometricResult.Success -> {
                        loadUsers() // Refresh the user list
                        state = state.copy(error = null)
                        hideBiometricEnrollDialog()
                    }
                    is BiometricResult.Error -> {
                        state = state.copy(error = "Failed to enroll biometric: ${result.message}")
                    }
                    else -> {
                        state = state.copy(error = "Biometric enrollment failed")
                    }
                }
            } catch (e: Exception) {
                state = state.copy(error = "Error enrolling biometric: ${e.message}")
                Log.e("UserManagementViewModel", "Error enrolling biometric", e)
            }
        }
    }

    fun disableUserBiometric(user: User) {
        viewModelScope.launch {
            try {
                val result = biometricAuthManager.disableBiometricForUser(user.userName)
                when (result) {
                    is BiometricResult.Success -> {
                        loadUsers() // Refresh the user list
                        state = state.copy(error = null)
                        hideBiometricDisableDialog()
                    }
                    is BiometricResult.Error -> {
                        state = state.copy(error = "Failed to disable biometric: ${result.message}")
                    }
                    else -> {
                        state = state.copy(error = "Failed to disable biometric")
                    }
                }
            } catch (e: Exception) {
                state = state.copy(error = "Error disabling biometric: ${e.message}")
                Log.e("UserManagementViewModel", "Error disabling biometric", e)
            }
        }
    }

    fun isBiometricSupported(): Boolean {
        return biometricAuthManager.isBiometricSupported()
    }
}

class UserManagementViewModelFactory(
    private val userDao: UserDao,
    private val biometricAuthManager: BiometricAuthManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UserManagementViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return UserManagementViewModel(userDao, biometricAuthManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}