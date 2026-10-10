package com.omnibuds.android.presentation.settings

import com.omnibuds.android.presentation.theme.AndroidThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Storage persistence port for settings values.
 */
interface AndroidSettingsStoragePort {
    suspend fun readString(key: String, default: String): String
    suspend fun readBoolean(key: String, default: Boolean): Boolean
    suspend fun readInt(key: String, default: Int): Int
    suspend fun writeString(key: String, value: String)
    suspend fun writeBoolean(key: String, value: Boolean)
    suspend fun writeInt(key: String, value: Int)
}

/**
 * Presentation controller for Android Application Settings.
 */
class SettingsViewModel(
    private val scope: CoroutineScope,
    private val storage: AndroidSettingsStoragePort? = null,
    initialState: SettingsScreenState = SettingsScreenState(),
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<SettingsScreenState> = _state.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        val s = storage ?: return
        scope.launch {
            mutex.withLock {
                val themeStr = s.readString("theme_mode", "SYSTEM")
                val themeMode = runCatching { AndroidThemeMode.valueOf(themeStr) }.getOrDefault(AndroidThemeMode.SYSTEM)
                val highContrast = s.readBoolean("high_contrast", false)
                val discoveryTimeout = s.readInt("discovery_timeout", 12).coerceIn(5, 60)
                val verbosity = s.readString("diagnostic_verbosity", "NORMAL")
                val notifications = s.readBoolean("notifications_enabled", true)
                val widgetRefresh = s.readBoolean("widget_auto_refresh", true)
                val redact = s.readBoolean("redact_identifiers", true)

                _state.value = SettingsScreenState(
                    themeMode = themeMode,
                    isHighContrast = highContrast,
                    discoveryTimeoutSeconds = discoveryTimeout,
                    diagnosticVerbosity = verbosity,
                    notificationsEnabled = notifications,
                    widgetAutoRefresh = widgetRefresh,
                    redactSensitiveIdentifiers = redact,
                )
            }
        }
    }

    fun setThemeMode(mode: AndroidThemeMode) {
        _state.update { it.copy(themeMode = mode, statusMessage = "Theme updated to $mode") }
        persist { it.writeString("theme_mode", mode.name) }
    }

    fun setHighContrast(enabled: Boolean) {
        _state.update { it.copy(isHighContrast = enabled, statusMessage = if (enabled) "High contrast enabled" else "High contrast disabled") }
        persist { it.writeBoolean("high_contrast", enabled) }
    }

    fun setDiscoveryTimeout(seconds: Int) {
        val clamped = seconds.coerceIn(5, 60)
        _state.update { it.copy(discoveryTimeoutSeconds = clamped) }
        persist { it.writeInt("discovery_timeout", clamped) }
    }

    fun setDiagnosticVerbosity(verbosity: String) {
        _state.update { it.copy(diagnosticVerbosity = verbosity) }
        persist { it.writeString("diagnostic_verbosity", verbosity) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _state.update { it.copy(notificationsEnabled = enabled) }
        persist { it.writeBoolean("notifications_enabled", enabled) }
    }

    fun setWidgetAutoRefresh(enabled: Boolean) {
        _state.update { it.copy(widgetAutoRefresh = enabled) }
        persist { it.writeBoolean("widget_auto_refresh", enabled) }
    }

    fun setRedactSensitiveIdentifiers(enabled: Boolean) {
        _state.update { it.copy(redactSensitiveIdentifiers = enabled) }
        persist { it.writeBoolean("redact_identifiers", enabled) }
    }

    fun clearStatusMessage() {
        _state.update { it.copy(statusMessage = null) }
    }

    private fun persist(block: suspend (AndroidSettingsStoragePort) -> Unit) {
        val s = storage ?: return
        scope.launch {
            mutex.withLock {
                _state.update { it.copy(isSaving = true) }
                try {
                    block(s)
                } finally {
                    _state.update { it.copy(isSaving = false) }
                }
            }
        }
    }
}
