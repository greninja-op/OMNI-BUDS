package com.omnibuds.android.presentation.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Presentation controller for Android Diagnostics and safe local log export.
 */
class DiagnosticsViewModel(
    initialState: DiagnosticsScreenState = DiagnosticsScreenState(),
    private val maxEventHistory: Int = 100,
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<DiagnosticsScreenState> = _state.asStateFlow()

    private val macAddressRegex = Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")

    /**
     * Records a diagnostic event, enforcing privacy redaction and history bounding.
     */
    fun recordEvent(
        category: String,
        message: String,
        correlationId: String = "corr-${System.currentTimeMillis() % 10000}",
        isRecoverable: Boolean = true,
        timestampMillis: Long = System.currentTimeMillis(),
    ) {
        val sanitizedMessage = if (_state.value.isRedactionActive) {
            redact(message)
        } else {
            message
        }

        val event = DiagnosticEventModel(
            timestampMillis = timestampMillis,
            correlationId = correlationId,
            category = category,
            message = sanitizedMessage,
            isRecoverable = isRecoverable,
        )

        _state.update { current ->
            val updated = (listOf(event) + current.recentEvents).take(maxEventHistory)
            current.copy(recentEvents = updated)
        }
    }

    fun setRedactionActive(active: Boolean) {
        _state.update { it.copy(isRedactionActive = active) }
    }

    fun updatePlatformInfo(
        apiLevel: Int,
        bluetoothStatus: String,
        permissionStatus: String,
    ) {
        _state.update {
            it.copy(
                androidApiLevel = apiLevel,
                bluetoothStateDescription = bluetoothStatus,
                permissionSummary = permissionStatus,
            )
        }
    }

    /**
     * Generates a safe, privacy-aware diagnostic export string.
     */
    fun generateLocalExport(): String {
        val current = _state.value
        val sb = StringBuilder()
        sb.appendLine("=== OmniBuds Diagnostics Export ===")
        sb.appendLine("App Version: ${current.appVersion}")
        sb.appendLine("Android API: ${current.androidApiLevel}")
        sb.appendLine("Bluetooth State: ${current.bluetoothStateDescription}")
        sb.appendLine("Permissions: ${current.permissionSummary}")
        sb.appendLine("Total Logged Events: ${current.recentEvents.size}")
        sb.appendLine("Redaction Active: ${current.isRedactionActive}")
        sb.appendLine("--- Recent Diagnostic Events ---")
        for (evt in current.recentEvents) {
            sb.appendLine("[${evt.timestampMillis}] [${evt.correlationId}] [${evt.category}] ${evt.message}")
        }
        sb.appendLine("=== End Diagnostics Export ===")
        val result = sb.toString()
        _state.update { it.copy(exportedData = result, statusBanner = "Diagnostics exported locally (${current.recentEvents.size} events)") }
        return result
    }

    fun clearExport() {
        _state.update { it.copy(exportedData = null, statusBanner = null) }
    }

    fun clearEvents() {
        _state.update { it.copy(recentEvents = emptyList(), exportedData = null) }
    }

    private fun redact(input: String): String {
        return macAddressRegex.replace(input) { match ->
            val mac = match.value
            val parts = mac.split(":")
            if (parts.size >= 6) {
                "${parts[0]}:${parts[1]}:${parts[2]}:XX:XX:XX"
            } else {
                "XX:XX:XX:XX:XX:XX"
            }
        }
    }
}
