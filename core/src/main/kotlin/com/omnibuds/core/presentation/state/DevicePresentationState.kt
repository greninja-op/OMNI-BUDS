package com.omnibuds.core.presentation.state

import com.omnibuds.core.globalstate.CapabilityState
import com.omnibuds.core.globalstate.ConnectionState
import com.omnibuds.core.globalstate.GlobalDeviceState
import com.omnibuds.core.globalstate.IdentityState
import com.omnibuds.core.globalstate.OperationStatus
import com.omnibuds.core.globalstate.PersistenceState
import com.omnibuds.core.globalstate.ProtocolState

/**
 * Standard 17-state device presentation enum for OmniBuds.
 * Shared across Android and Desktop to eliminate conflicting labels.
 */
enum class DevicePresentationState(
    val label: String,
    val description: String,
    val isControllable: Boolean,
) {
    UNKNOWN(
        label = "Unknown",
        description = "Device state has not been observed.",
        isControllable = false,
    ),
    DISCOVERED(
        label = "Discovered",
        description = "Device was discovered advertising nearby.",
        isControllable = false,
    ),
    PAIRED(
        label = "Paired",
        description = "Device is paired in host OS Bluetooth settings.",
        isControllable = false,
    ),
    CONNECTING(
        label = "Connecting…",
        description = "Establishing Bluetooth connection with device.",
        isControllable = false,
    ),
    CONNECTED_IDENTIFYING(
        label = "Connected (Identifying)",
        description = "Bluetooth connection established; identifying device model and protocol capabilities.",
        isControllable = false,
    ),
    CAPABILITY_DISCOVERY_IN_PROGRESS(
        label = "Discovering Capabilities…",
        description = "Probing supported vendor protocol features.",
        isControllable = false,
    ),
    READY(
        label = "Ready",
        description = "Device is connected and ready for hardware feature control.",
        isControllable = true,
    ),
    CONTROL_ACTIVE(
        label = "Control Session Active",
        description = "Vendor protocol session is active and receiving live state.",
        isControllable = true,
    ),
    DISCONNECTING(
        label = "Disconnecting…",
        description = "Terminating active Bluetooth connection.",
        isControllable = false,
    ),
    DISCONNECTED(
        label = "Disconnected",
        description = "Device is disconnected.",
        isControllable = false,
    ),
    TEMPORARILY_UNAVAILABLE(
        label = "Temporarily Unavailable",
        description = "Connected device is temporarily unreachable or out of range.",
        isControllable = false,
    ),
    ADAPTER_UNAVAILABLE(
        label = "Bluetooth Unavailable",
        description = "No Bluetooth adapter detected on this host system.",
        isControllable = false,
    ),
    ADAPTER_DISABLED(
        label = "Bluetooth Disabled",
        description = "Bluetooth radio is disabled in system settings.",
        isControllable = false,
    ),
    PERMISSION_REQUIRED(
        label = "Permission Required",
        description = "Bluetooth permission is required by the host operating system.",
        isControllable = false,
    ),
    PLATFORM_UNSUPPORTED(
        label = "Platform Unsupported",
        description = "Bluetooth operations are unsupported on this operating system.",
        isControllable = false,
    ),
    READ_ONLY(
        label = "Read-Only",
        description = "Device state is observable but hardware features cannot be modified.",
        isControllable = false,
    ),
    OPERATION_PENDING(
        label = "Working…",
        description = "Hardware control operation is executing on the device.",
        isControllable = false,
    ),
    OPERATION_REJECTED(
        label = "Operation Rejected",
        description = "Requested change was rejected by device or access control policy.",
        isControllable = false,
    ),
    OUTCOME_UNKNOWN(
        label = "Outcome Unknown",
        description = "Operation completed with ambiguous or unverified status.",
        isControllable = false,
    ),
    PERSISTENCE_VERIFIED(
        label = "Persistence Verified",
        description = "Feature configuration was written and verified persisted across restarts.",
        isControllable = true,
    );

    val isConnectedAtOsLevel: Boolean
        get() = this in setOf(
            CONNECTED_IDENTIFYING,
            CAPABILITY_DISCOVERY_IN_PROGRESS,
            READY,
            CONTROL_ACTIVE,
            READ_ONLY,
            PERSISTENCE_VERIFIED,
        )
}

/**
 * Pure mapping functions from domain state to unified presentation state.
 */
object DeviceStateMapper {

    /**
     * Maps domain state engine state to unified presentation state.
     */
    fun mapFromGlobalState(state: GlobalDeviceState?): DevicePresentationState {
        if (state == null) return DevicePresentationState.UNKNOWN

        // Check in-flight operations
        if (state.features.executing.values.any { it is OperationStatus.Pending }) {
            return DevicePresentationState.OPERATION_PENDING
        }

        return when (state.connection) {
            is ConnectionState.Disconnected -> DevicePresentationState.DISCONNECTED
            is ConnectionState.Connecting -> DevicePresentationState.CONNECTING
            is ConnectionState.Disconnecting -> DevicePresentationState.DISCONNECTING
            is ConnectionState.Connected -> {
                if (state.identity !is IdentityState.Identified) {
                    return DevicePresentationState.CONNECTED_IDENTIFYING
                }

                val protocol = state.protocol
                if (protocol !is ProtocolState.Resolved || !protocol.compatible) {
                    return DevicePresentationState.READ_ONLY
                }

                when (val caps = state.capabilities) {
                    is CapabilityState.NotDiscovered,
                    is CapabilityState.Discovering -> DevicePresentationState.CAPABILITY_DISCOVERY_IN_PROGRESS
                    is CapabilityState.Failed -> DevicePresentationState.READ_ONLY
                    is CapabilityState.Ready -> {
                        if (caps.capabilityIds.isEmpty()) {
                            DevicePresentationState.READ_ONLY
                        } else if (state.persistence is PersistenceState.Verified) {
                            DevicePresentationState.PERSISTENCE_VERIFIED
                        } else {
                            DevicePresentationState.READY
                        }
                    }
                }
            }
        }
    }
}
