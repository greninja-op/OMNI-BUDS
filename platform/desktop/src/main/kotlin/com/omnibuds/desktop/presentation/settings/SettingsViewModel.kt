package com.omnibuds.desktop.presentation.settings

import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.platform.PlatformStoragePort
import com.omnibuds.desktop.theme.ThemeMode
import com.omnibuds.desktop.theme.UiDensity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Presentation controller for application settings.
 * Persists user preferences safely through [PlatformStoragePort].
 */
class SettingsViewModel(
    private val storage: PlatformStoragePort,
    private val scope: CoroutineScope,
) {
    companion object {
        const val KEY_THEME = "desktop.pref.theme"
        const val KEY_DENSITY = "desktop.pref.density"
        const val KEY_DISCOVERY_TIMEOUT = "desktop.pref.discovery_timeout_seconds"
        const val KEY_AUTO_REFRESH = "desktop.pref.auto_refresh"
        const val KEY_DIAG_LIMIT = "desktop.pref.diagnostic_retention_limit"
    }

    private val mutex = Mutex()
    private val _state = MutableStateFlow(SettingsScreenState())
    val state: StateFlow<SettingsScreenState> = _state.asStateFlow()

    init {
        scope.launch {
            loadSettings()
        }
    }

    suspend fun loadSettings() {
        mutex.withLock {
            val theme = when (val res = storage.get(KEY_THEME)) {
                is OperationOutcome.Success -> parseTheme(res.value)
                else -> ThemeMode.DARK
            }

            val density = when (val res = storage.get(KEY_DENSITY)) {
                is OperationOutcome.Success -> parseDensity(res.value)
                else -> UiDensity.COMFORTABLE
            }

            val timeout = when (val res = storage.get(KEY_DISCOVERY_TIMEOUT)) {
                is OperationOutcome.Success -> res.value?.toIntOrNull()?.coerceIn(5, 120) ?: 15
                else -> 15
            }

            val autoRefresh = when (val res = storage.get(KEY_AUTO_REFRESH)) {
                is OperationOutcome.Success -> res.value?.toBooleanStrictOrNull() ?: false
                else -> false
            }

            val diagLimit = when (val res = storage.get(KEY_DIAG_LIMIT)) {
                is OperationOutcome.Success -> res.value?.toIntOrNull()?.coerceIn(64, 2048) ?: 512
                else -> 512
            }

            _state.value = SettingsScreenState(
                themeMode = theme,
                density = density,
                discoveryTimeoutSeconds = timeout,
                autoRefreshDevices = autoRefresh,
                diagnosticRetentionLimit = diagLimit,
                isDirty = false,
            )
        }
    }

    fun updateTheme(mode: ThemeMode) {
        _state.update { it.copy(themeMode = mode, isDirty = true) }
    }

    fun updateDensity(density: UiDensity) {
        _state.update { it.copy(density = density, isDirty = true) }
    }

    fun updateDiscoveryTimeout(seconds: Int) {
        val clamped = seconds.coerceIn(5, 120)
        _state.update { it.copy(discoveryTimeoutSeconds = clamped, isDirty = true) }
    }

    fun updateAutoRefresh(enabled: Boolean) {
        _state.update { it.copy(autoRefreshDevices = enabled, isDirty = true) }
    }

    fun updateDiagnosticLimit(limit: Int) {
        val clamped = limit.coerceIn(64, 2048)
        _state.update { it.copy(diagnosticRetentionLimit = clamped, isDirty = true) }
    }

    suspend fun saveSettings() {
        mutex.withLock {
            val current = _state.value
            val outcomes = listOf(
                storage.set(KEY_THEME, current.themeMode.name),
                storage.set(KEY_DENSITY, current.density.name),
                storage.set(KEY_DISCOVERY_TIMEOUT, current.discoveryTimeoutSeconds.toString()),
                storage.set(KEY_AUTO_REFRESH, current.autoRefreshDevices.toString()),
                storage.set(KEY_DIAG_LIMIT, current.diagnosticRetentionLimit.toString()),
            )

            val hasFailure = outcomes.any { it is OperationOutcome.Failure }
            if (hasFailure) {
                _state.update {
                    it.copy(
                        errorBanner = "Failed to persist application preferences.",
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        isDirty = false,
                        lastSavedEpochMillis = System.currentTimeMillis(),
                        errorBanner = null,
                    )
                }
            }
        }
    }

    suspend fun resetToDefaults() {
        _state.value = SettingsScreenState(isDirty = true)
        saveSettings()
    }

    fun clearError() {
        _state.update { it.copy(errorBanner = null) }
    }

    private fun parseTheme(raw: String?): ThemeMode = try {
        raw?.let { ThemeMode.valueOf(it) } ?: ThemeMode.DARK
    } catch (_: Exception) {
        ThemeMode.DARK
    }

    private fun parseDensity(raw: String?): UiDensity = try {
        raw?.let { UiDensity.valueOf(it) } ?: UiDensity.COMFORTABLE
    } catch (_: Exception) {
        UiDensity.COMFORTABLE
    }
}
