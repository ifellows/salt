package com.dev.salt.ui.theme


import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Define your light color scheme using the colors from Color.kt
private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    // You can override other default colors here if needed
    // background = Color(0xFFFFFBFE),
    // surface = Color(0xFFFFFBFE),
    // onPrimary = Color.White,
    // onSecondary = Color.White,
    // onTertiary = Color.White,
    // onBackground = Color(0xFF1C1B1F),
    // onSurface = Color(0xFF1C1B1F),
)

// Define your dark color scheme
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    // You can override other default colors here if needed
    // background = Color(0xFF1C1B1F),
    // surface = Color(0xFF1C1B1F),
    // onPrimary = Purple40,
    // onSecondary = PurpleGrey40,
    // onTertiary = Pink40,
    // onBackground = Color(0xFFE6E1E5),
    // onSurface = Color(0xFFE6E1E5),
)

@Composable
fun SALTTheme( // This is the Composable you were trying to import
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    // Set to false if you want to use your custom LightColorScheme/DarkColorScheme
    // without Material You's dynamic theming.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // This part is for setting the status bar and navigation bar colors to match the theme.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb() // Or colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            // You can also set navigation bar color if needed
            // window.navigationBarColor = colorScheme.surface.toArgb()
            // WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography, // From Type.kt
        // shapes = Shapes, // You can define a Shapes.kt file too if you need custom shapes
        content = content
    )
}