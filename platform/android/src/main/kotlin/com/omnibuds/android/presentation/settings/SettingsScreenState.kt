package com.omnibuds.android.presentation.settings

import com.omnibuds.android.presentation.theme.AndroidThemeMode

/**
 * Immutable settings state for the Android application.
 */
data class SettingsScreenState(
    val themeMode: AndroidThemeMode = AndroidThemeMode.SYSTEM,
    val isHighContrast: Boolean = false,
    val discoveryTimeoutSeconds: Int = 12,
    val diagnosticVerbosity: String = "NORMAL",
    val notificationsEnabled: Boolean = true,
    val widgetAutoRefresh: Boolean = true,
    val redactSensitiveIdentifiers: Boolean = true,
    val isSaving: Boolean = false,
    val statusMessage: String? = null,
) {
    init {
        require(discoveryTimeoutSeconds in 5..60) {
            "discoveryTimeoutSeconds must be between 5 and 60 seconds, was $discoveryTimeoutSeconds"
        }
    }
}
