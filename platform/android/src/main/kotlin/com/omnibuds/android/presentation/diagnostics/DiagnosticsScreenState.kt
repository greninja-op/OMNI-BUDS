package com.omnibuds.android.presentation.diagnostics

/**
 * Single diagnostic log entry for Android presentation.
 */
data class DiagnosticEventModel(
    val timestampMillis: Long,
    val correlationId: String,
    val category: String,
    val message: String,
    val isRecoverable: Boolean = true,
)

/**
 * Immutable UI state for the Android Diagnostics screen.
 */
data class DiagnosticsScreenState(
    val appVersion: String = "1.0.0-phase49",
    val androidApiLevel: Int = 35,
    val bluetoothStateDescription: String = "Adapter Available, BLUETOOTH_CONNECT Granted",
    val permissionSummary: String = "BLUETOOTH_CONNECT: GRANTED",
    val recentEvents: List<DiagnosticEventModel> = emptyList(),
    val isRedactionActive: Boolean = true,
    val exportedData: String? = null,
    val statusBanner: String? = null,
) {
    val totalEventCount: Int get() = recentEvents.size
}
