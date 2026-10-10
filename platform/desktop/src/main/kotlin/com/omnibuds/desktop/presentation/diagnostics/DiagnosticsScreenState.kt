package com.omnibuds.desktop.presentation.diagnostics

import com.omnibuds.core.diagnostics.DiagnosticSeverity
import com.omnibuds.core.platform.PlatformDescriptor
import com.omnibuds.core.platform.desktop.DesktopBluetoothAvailability

/**
 * Visual model of a single diagnostic event in the UI.
 */
data class DiagnosticEventItem(
    val sequence: Long,
    val timestampEpochMillis: Long,
    val severity: DiagnosticSeverity,
    val category: String,
    val message: String,
    val correlationId: String?,
)

/**
 * Screen state for the Diagnostics dashboard.
 */
data class DiagnosticsScreenState(
    val applicationVersion: String = "1.0.0",
    val platformDescriptor: PlatformDescriptor = PlatformDescriptor.unobserved(),
    val adapterAvailability: DesktopBluetoothAvailability = DesktopBluetoothAvailability.UNKNOWN,
    val events: List<DiagnosticEventItem> = emptyList(),
    val droppedEventsCount: Long = 0L,
    val filterSeverity: DiagnosticSeverity? = null,
    val exportResult: String? = null,
    val isExporting: Boolean = false,
) {
    val totalEventCount: Int get() = events.size
}

/**
 * User actions supported in the Diagnostics view.
 */
sealed interface DiagnosticsAction {
    data class SetSeverityFilter(val severity: DiagnosticSeverity?) : DiagnosticsAction
    data object ClearEvents : DiagnosticsAction
    data object ExportSanitizedJson : DiagnosticsAction
    data object ClearExportStatus : DiagnosticsAction
}
