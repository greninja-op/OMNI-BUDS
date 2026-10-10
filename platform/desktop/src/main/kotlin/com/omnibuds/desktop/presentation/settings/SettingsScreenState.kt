package com.omnibuds.desktop.presentation.settings

import com.omnibuds.desktop.theme.ThemeMode
import com.omnibuds.desktop.theme.UiDensity

/**
 * Screen state for application-level settings.
 */
data class SettingsScreenState(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val density: UiDensity = UiDensity.COMFORTABLE,
    val discoveryTimeoutSeconds: Int = 15,
    val autoRefreshDevices: Boolean = false,
    val diagnosticRetentionLimit: Int = 512,
    val isDirty: Boolean = false,
    val lastSavedEpochMillis: Long? = null,
    val errorBanner: String? = null,
) {
    init {
        require(discoveryTimeoutSeconds in 5..120) {
            "discoveryTimeoutSeconds must be between 5 and 120, was $discoveryTimeoutSeconds"
        }
        require(diagnosticRetentionLimit in 64..2048) {
            "diagnosticRetentionLimit must be between 64 and 2048, was $diagnosticRetentionLimit"
        }
    }
}

/**
 * User actions supported in Settings.
 */
sealed interface SettingsAction {
    data class UpdateTheme(val themeMode: ThemeMode) : SettingsAction
    data class UpdateDensity(val density: UiDensity) : SettingsAction
    data class UpdateDiscoveryTimeout(val seconds: Int) : SettingsAction
    data class UpdateAutoRefresh(val enabled: Boolean) : SettingsAction
    data class UpdateDiagnosticLimit(val limit: Int) : SettingsAction
    data object SaveSettings : SettingsAction
    data object ResetToDefaults : SettingsAction
    data object ClearError : SettingsAction
}
