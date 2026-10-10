package com.omnibuds.desktop.presentation.workspace

import com.omnibuds.core.platform.desktop.DesktopConnectionSessionState
import com.omnibuds.desktop.presentation.audio.AudioPresentationModel
import com.omnibuds.desktop.presentation.battery.BatteryPresentationModel

/**
 * Status of an in-flight control operation on a hardware feature.
 */
enum class ControlExecutionStatus {
    IDLE,
    PENDING,
    SUCCEEDED,
    REJECTED,
    TIMED_OUT,
    AMBIGUOUS,
    FAILED,
}

/**
 * Visual model for a specific hardware control.
 */
data class HardwareControlItem(
    val featureId: String,
    val displayName: String,
    val description: String,
    val isSupported: Boolean,
    val isActionable: Boolean,
    val observedValue: String?,
    val requestedValue: String?,
    val acknowledgedValue: String?,
    val executionStatus: ControlExecutionStatus = ControlExecutionStatus.IDLE,
    val options: List<String> = emptyList(),
    val rejectionOrFailureReason: String? = null,
) {
    val isPending: Boolean get() = executionStatus == ControlExecutionStatus.PENDING
}

/**
 * Overview section facts.
 */
data class DeviceOverviewModel(
    val deviceIdentifier: String,
    val displayName: String,
    val manufacturer: String?,
    val model: String?,
    val firmwareVersion: String?,
    val identityConfidence: String,
    val isIdentified: Boolean,
    val connectionSessionState: DesktopConnectionSessionState,
    val isReadOnly: Boolean,
    val capabilityDiscoveryStatus: String,
)

/**
 * Workspace screen state.
 */
data class WorkspaceScreenState(
    val deviceIdentifier: String,
    val isLoading: Boolean = false,
    val overview: DeviceOverviewModel? = null,
    val controls: List<HardwareControlItem> = emptyList(),
    val battery: BatteryPresentationModel = BatteryPresentationModel.unavailable(),
    val audio: AudioPresentationModel = AudioPresentationModel.unavailable(),
    val errorBanner: String? = null,
    val isRecoverableError: Boolean = false,
    val limitations: List<String> = emptyList(),
) {
    val isDeviceConnected: Boolean
        get() = overview?.connectionSessionState?.isConnectedAtOsLevel == true

    val isVendorControllable: Boolean
        get() = overview?.connectionSessionState?.isVendorControllable == true

    val hasUnidentifiedWarning: Boolean
        get() = overview?.isIdentified == false || overview?.isReadOnly == true
}

/**
 * User actions supported in the Device Workspace.
 */
sealed interface WorkspaceAction {
    data class SubmitControlOperation(
        val featureId: String,
        val targetValue: String,
    ) : WorkspaceAction

    data object RefreshState : WorkspaceAction
    data object ClearError : WorkspaceAction
}
