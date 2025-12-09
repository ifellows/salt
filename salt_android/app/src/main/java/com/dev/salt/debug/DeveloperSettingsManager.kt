package com.dev.salt.debug

import android.content.Context

/**
 * Manages developer/debug settings stored in SharedPreferences.
 * These settings are intended for development and testing purposes only.
 */
object DeveloperSettingsManager {
    private const val PREFS_NAME = "salt_developer_prefs"
    private const val KEY_DEBUG_JEXL = "debug_jexl_enabled"

    /**
     * Check if JEXL debug mode is enabled.
     * When enabled, all JEXL evaluations will show an interactive debug dialog.
     */
    fun isJexlDebugEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DEBUG_JEXL, false)
    }

    /**
     * Enable or disable JEXL debug mode.
     */
    fun setJexlDebugEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DEBUG_JEXL, enabled).apply()
    }
}
