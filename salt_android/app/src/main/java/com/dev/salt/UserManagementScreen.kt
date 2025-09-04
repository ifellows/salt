package com.dev.salt

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dev.salt.data.User
import com.dev.salt.viewmodel.UserManagementViewModel
import com.dev.salt.ui.LogoutButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(
    viewModel: UserManagementViewModel,
    onLogout: (() -> Unit)? = null,
    showLogout: Boolean = true
) {
    val state = viewModel.state

    // Show error dialog if there's an error
    if (state.error != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Error") },
            text = { Text(state.error) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK")
                }
            }
        )
    }

    // Add User Dialog
    if (viewModel.showAddUserDialog) {
        AddUserDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.hideAddUserDialog() }
        )
    }

    // Delete Confirmation Dialog
    if (viewModel.showDeleteConfirmDialog && viewModel.userToDelete != null) {
        DeleteUserConfirmDialog(
            user = viewModel.userToDelete!!,
            onConfirm = { viewModel.deleteUser(viewModel.userToDelete!!) },
            onDismiss = { viewModel.hideDeleteConfirmDialog() }
        )
    }

    // Biometric Enroll Confirmation Dialog
    if (viewModel.showBiometricEnrollDialog && viewModel.userForBiometricAction != null) {
        BiometricEnrollConfirmDialog(
            user = viewModel.userForBiometricAction!!,
            onConfirm = { viewModel.enrollUserBiometric(viewModel.userForBiometricAction!!) },
            onDismiss = { viewModel.hideBiometricEnrollDialog() }
        )
    }

    // Biometric Disable Confirmation Dialog
    if (viewModel.showBiometricDisableDialog && viewModel.userForBiometricAction != null) {
        BiometricDisableConfirmDialog(
            user = viewModel.userForBiometricAction!!,
            onConfirm = { viewModel.disableUserBiometric(viewModel.userForBiometricAction!!) },
            onDismiss = { viewModel.hideBiometricDisableDialog() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("User Management") },
                actions = {
                    if (onLogout != null) {
                        LogoutButton(
                            onLogout = onLogout,
                            isVisible = showLogout
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showAddUserDialog() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add User")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.users) { user ->
                        UserCard(
                            user = user,
                            onDeleteClick = { viewModel.showDeleteConfirmDialog(user) },
                            onRoleChange = { newRole -> viewModel.updateUserRole(user, newRole) },
                            onBiometricEnroll = { viewModel.showBiometricEnrollDialog(user) },
                            onBiometricDisable = { viewModel.showBiometricDisableDialog(user) },
                            isBiometricSupported = viewModel.isBiometricSupported()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UserCard(
    user: User,
    onDeleteClick: () -> Unit,
    onRoleChange: (String) -> Unit,
    onBiometricEnroll: () -> Unit,
    onBiometricDisable: () -> Unit,
    isBiometricSupported: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = user.fullName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "@${user.userName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete User",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Role:",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                var expanded by remember { mutableStateOf(false) }
                val roles = listOf("SURVEY_STAFF", "ADMINISTRATOR")
                
                Box {
                    OutlinedTextField(
                        value = user.role,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { expanded = !expanded }) {
                                Icon(
                                    if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = "Dropdown"
                                )
                            }
                        },
                        modifier = Modifier
                            .width(200.dp)
                            .clickable { expanded = !expanded }
                    )
                    
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        roles.forEach { role ->
                            DropdownMenuItem(
                                text = { Text(role) },
                                onClick = {
                                    onRoleChange(role)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
            
            // Biometric status and controls
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Fingerprint,
                        contentDescription = "Biometric",
                        tint = if (user.biometricEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (user.biometricEnabled) "Biometric Enabled" else "Biometric Disabled",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (user.biometricEnabled) {
                            user.biometricEnrolledDate?.let { enrolledDate ->
                                val date = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                                    .format(java.util.Date(enrolledDate))
                                Text(
                                    text = "Enrolled: $date",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                
                // Biometric control buttons
                if (isBiometricSupported) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (user.biometricEnabled) {
                            // Re-enroll button
                            OutlinedButton(
                                onClick = onBiometricEnroll,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Re-enroll", style = MaterialTheme.typography.bodySmall)
                            }
                            
                            // Disable button
                            OutlinedButton(
                                onClick = onBiometricDisable,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Disable", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            // Enable button
                            Button(
                                onClick = onBiometricEnroll,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Enable Biometric", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Biometric not supported",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun AddUserDialog(
    viewModel: UserManagementViewModel,
    onDismiss: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New User") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = viewModel.newUserName,
                    onValueChange = { viewModel.newUserName = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = viewModel.newUserFullName,
                    onValueChange = { viewModel.newUserFullName = it },
                    label = { Text("Full Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = viewModel.newUserPassword,
                    onValueChange = { viewModel.newUserPassword = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        val image = if (passwordVisible)
                            Icons.Filled.VisibilityOff
                        else Icons.Filled.Visibility
                        val description = if (passwordVisible) "Hide password" else "Show password"
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, description)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                var expanded by remember { mutableStateOf(false) }
                val roles = listOf("SURVEY_STAFF", "ADMINISTRATOR")
                
                Box {
                    OutlinedTextField(
                        value = viewModel.newUserRole,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Role") },
                        trailingIcon = {
                            IconButton(onClick = { expanded = !expanded }) {
                                Icon(
                                    if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = "Dropdown"
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                    )
                    
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        roles.forEach { role ->
                            DropdownMenuItem(
                                text = { Text(role) },
                                onClick = {
                                    viewModel.newUserRole = role
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.addUser() },
                enabled = !viewModel.state.isAddingUser
            ) {
                if (viewModel.state.isAddingUser) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Add User")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeleteUserConfirmDialog(
    user: User,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete User") },
        text = { 
            Text("Are you sure you want to delete user \"${user.fullName}\" (@${user.userName})? This action cannot be undone.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun BiometricEnrollConfirmDialog(
    user: User,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = if (user.biometricEnabled) "Re-enroll Biometric" else "Enable Biometric"
    val message = if (user.biometricEnabled) {
        "Are you sure you want to re-enroll biometric authentication for \"${user.fullName}\"? This will replace the current biometric data."
    } else {
        "Are you sure you want to enable biometric authentication for \"${user.fullName}\"? This will allow them to login using biometric authentication."
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Fingerprint,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(title)
            }
        },
        text = { 
            Text(message)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Enable")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun BiometricDisableConfirmDialog(
    user: User,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Fingerprint,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Disable Biometric")
            }
        },
        text = { 
            Text("Are you sure you want to disable biometric authentication for \"${user.fullName}\"? They will no longer be able to login using biometric authentication.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Disable")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}