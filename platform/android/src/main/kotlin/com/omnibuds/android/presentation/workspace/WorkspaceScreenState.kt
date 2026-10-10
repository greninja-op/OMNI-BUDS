package com.omnibuds.android.presentation.workspace

import com.omnibuds.android.presentation.audio.AudioPresentationModel
import com.omnibuds.android.presentation.battery.BatteryPresentationModel
import com.omnibuds.core.globalstate.ConnectionState
import com.omnibuds.core.presentation.control.ControlExecutionStatus
import com.omnibuds.core.presentation.control.FeatureCapabilityKind

// Re-export core enums for backward compatibility
typealias ControlExecutionStatus = ControlExecutionStatus
typealias FeatureCapabilityKind = FeatureCapabilityKind

enum class WorkspaceTab {
    OVERVIEW,
    CONTROLS,
    BATTERY,
    AUDIO,
    DIAGNOSTICS,
}

data class DeviceOverviewModel(
    val deviceId: String,
    val displayName: String,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val transport: String? = null,
    val protocolId: String? = null,
    val protocolVersion: String? = null,
    val firmwareVersion: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val confidence: String? = null,
    val isReady: Boolean = false,
)

data class HardwareControlModel(
    val featureId: String,
    val displayName: String,
    val category: String = "HARDWARE",
    val capabilityKind: FeatureCapabilityKind = FeatureCapabilityKind.UNKNOWN,
    val currentValue: String? = null,
    val desiredValue: String? = null,
    val acknowledgedValue: String? = null,
    val observedValue: String? = null,
    val executionStatus: ControlExecutionStatus = ControlExecutionStatus.IDLE,
    val availableModes: List<String> = emptyList(),
    val failureReason: String? = null,
    val explanation: String? = null,
) {
    val isActionable: Boolean
        get() = capabilityKind in listOf(
            FeatureCapabilityKind.VOLATILE,
            FeatureCapabilityKind.PERSISTENT,
            FeatureCapabilityKind.PERSISTENCE_VERIFIED,
        ) && executionStatus != ControlExecutionStatus.PENDING

    val isReadOnly: Boolean get() = capabilityKind == FeatureCapabilityKind.READ_ONLY
    val isUnsupported: Boolean get() = capabilityKind == FeatureCapabilityKind.UNSUPPORTED
    val isUnknown: Boolean get() = capabilityKind == FeatureCapabilityKind.UNKNOWN

    val talkBackDescription: String
        get() {
            val parts = mutableListOf<String>()
            parts.add("$displayName control")
            currentValue?.let { parts.add("Current: $it") }
            if (isUnsupported) parts.add("Unsupported by connected device")
            else if (isUnknown) parts.add("Capability unknown or not verified")
            else if (isReadOnly) parts.add("Read only")
            if (executionStatus == ControlExecutionStatus.PENDING) parts.add("Operation pending")
            failureReason?.let { parts.add("Error: $it") }
            return parts.joinToString(", ")
        }
}

/**
 * Immutable UI state for the selected Device Workspace.
 */
data class WorkspaceScreenState(
    val deviceIdentifier: String,
    val isLoading: Boolean = false,
    val selectedTab: WorkspaceTab = WorkspaceTab.OVERVIEW,
    val overview: DeviceOverviewModel? = null,
    val controls: List<HardwareControlModel> = emptyList(),
    val battery: BatteryPresentationModel? = null,
    val audio: AudioPresentationModel? = null,
    val limitations: List<String> = emptyList(),
    val errorBanner: String? = null,
    val isRecoverableError: Boolean = true,
    val pendingOperationId: String? = null,
) {
    val isConnected: Boolean
        get() = overview?.connectionState is ConnectionState.Connected

    val hasActionableControls: Boolean
        get() = controls.any { it.isActionable }
}
