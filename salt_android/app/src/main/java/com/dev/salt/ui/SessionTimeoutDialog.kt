package com.dev.salt.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun SessionTimeoutDialog(
    timeUntilExpiration: Long,
    onExtendSession: () -> Unit,
    onLogout: () -> Unit
) {
    var remainingTime by remember { mutableStateOf(timeUntilExpiration) }
    
    // Update remaining time every second
    LaunchedEffect(timeUntilExpiration) {
        remainingTime = timeUntilExpiration
        while (remainingTime > 0) {
            delay(1000)
            remainingTime -= 1000
        }
    }
    
    val minutes = (remainingTime / 60000).toInt()
    val seconds = ((remainingTime % 60000) / 1000).toInt()
    
    AlertDialog(
        onDismissRequest = { }, // Prevent dismissing by clicking outside
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = MaterialTheme.colorScheme.error
                )
                Text("Session Expiring")
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Your session will expire in:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = String.format("%02d:%02d", minutes, seconds),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Would you like to extend your session?",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onExtendSession,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Extend Session")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onLogout,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Logout")
            }
        }
    )
}

@Composable
fun SessionExpiredDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = MaterialTheme.colorScheme.error
                )
                Text("Session Expired")
            }
        },
        text = {
            Text(
                text = "Your session has expired for security reasons. Please login again to continue.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Login Again")
            }
        }
    )
}