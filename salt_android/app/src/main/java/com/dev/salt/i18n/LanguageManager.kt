package com.dev.salt.i18n

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * Manages the application language globally across all users.
 * Language preference is stored in SharedPreferences and applied at app startup.
 */
object LanguageManager {
    private const val PREFS_NAME = "salt_language_prefs"
    private const val KEY_LANGUAGE_CODE = "app_language_code"
    private const val DEFAULT_LANGUAGE = "system" // Use system language by default

    // Available languages in the app
    val availableLanguages = listOf(
        LanguageOption("system", "System Default", "Idioma del sistema"),
        LanguageOption("en", "English", "English"),
        LanguageOption("es", "Español", "Spanish")
    )

    data class LanguageOption(
        val code: String,
        val displayName: String,
        val nativeName: String
    )

    /**
     * Get the current language code from preferences
     */
    fun getCurrentLanguageCode(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE_CODE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }

    /**
     * Set the application language globally
     */
    fun setLanguage(context: Context, languageCode: String) {
        // Save to preferences
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE_CODE, languageCode).apply()

        // Apply the language immediately
        applyLanguage(context, languageCode)
    }

    /**
     * Apply the saved language preference to the application
     * This should be called in Application.onCreate() or Activity.attachBaseContext()
     */
    fun applyLanguage(context: Context, languageCode: String = getCurrentLanguageCode(context)): Context {
        if (languageCode == "system") {
            // Use system default, no override needed
            return context
        }

        val locale = when (languageCode) {
            "en" -> Locale.ENGLISH
            "es" -> Locale("es")
            else -> Locale.getDefault()
        }

        return updateResources(context, locale)
    }

    /**
     * Update the app's resources with the specified locale
     */
    private fun updateResources(context: Context, locale: Locale): Context {
        Locale.setDefault(locale)

        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(configuration)
        } else {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
            context
        }
    }

    /**
     * Wrap the activity context to apply the saved language
     * Call this in every Activity's attachBaseContext()
     */
    fun wrapContext(context: Context): Context {
        val languageCode = getCurrentLanguageCode(context)
        return applyLanguage(context, languageCode)
    }

    /**
     * Get the display name for the current language
     */
    fun getCurrentLanguageDisplayName(context: Context): String {
        val currentCode = getCurrentLanguageCode(context)
        return availableLanguages.find { it.code == currentCode }?.displayName ?: "System Default"
    }

    /**
     * Check if the app needs to restart after language change
     * (Required for complete UI update)
     */
    fun requiresRestart(): Boolean = true
}