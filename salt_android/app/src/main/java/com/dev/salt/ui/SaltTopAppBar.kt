package com.dev.salt.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.dev.salt.AppDestinations
import com.dev.salt.R

/**
 * Reusable TopAppBar for staff screens that includes the Return to Menu button.
 *
 * @param title The title to display in the app bar
 * @param navController Navigation controller for navigation actions
 * @param showBackButton Whether to show a back button in the navigation icon position
 * @param showHomeButton Whether to show the Return to Menu button (default true)
 * @param onBack Custom back action. If null, uses navController.navigateUp()
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaltTopAppBar(
    title: String,
    navController: NavController,
    showBackButton: Boolean = false,
    showHomeButton: Boolean = true,
    onBack: (() -> Unit)? = null
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (showBackButton) {
                IconButton(onClick = { onBack?.invoke() ?: navController.navigateUp() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back)
                    )
                }
            }
        },
        actions = {
            if (showHomeButton) {
                ReturnToMenuButton(
                    onReturnToMenu = {
                        navController.navigate(AppDestinations.MENU_SCREEN) {
                            popUpTo(AppDestinations.MENU_SCREEN) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    isVisible = true
                )
            }
        }
    )
}
