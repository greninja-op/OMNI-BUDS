package com.omnibuds.core.globalstate

/**
 * Typed per-device state snapshot.
 *
 * Phase 24 (OB-P24-REQ-001/002): immutable snapshot with typed submodels.
 * Unknown/unavailable fields are explicit nulls or `Unknown` states —
 * never fabricated defaults.
 */
data class GlobalDeviceState(
    val deviceId: GlobalDeviceId,
    val identity: IdentityState,
    val connection: ConnectionState,
    val protocol: ProtocolState,
    val capabilities: CapabilityState,
    val features: FeatureState,
    val battery: BatteryState,
    val audio: AudioState,
    val configuration: ConfigurationState,
    val persistence: PersistenceState,
    val vendorFeatures: VendorFeatureState,
    /** Engine-side publication timestamp (not an observation timestamp). */
    val publishedAtMillis: Long?,
    /** Known limitations of this snapshot. */
    val limitations: List<String> = emptyList(),
) {
    companion object {
        /** Empty snapshot for a newly tracked device. */
        fun empty(deviceId: GlobalDeviceId): GlobalDeviceState = GlobalDeviceState(
            deviceId = deviceId,
            identity = IdentityState.Unknown,
            connection = ConnectionState.Disconnected,
            protocol = ProtocolState.Unresolved,
            capabilities = CapabilityState.NotDiscovered,
            features = FeatureState.Empty,
            battery = BatteryState.Unknown,
            audio = AudioState.Unknown,
            configuration = ConfigurationState.Empty,
            persistence = PersistenceState.NotVerified,
            vendorFeatures = VendorFeatureState.Empty,
            publishedAtMillis = null,
        )
    }
}

/** Device identity state. */
sealed interface IdentityState {
    data object Unknown : IdentityState
    data class Ambiguous(val candidates: List<String>, val reason: String) : IdentityState
    data class Identified(
        val manufacturerId: String,
        val modelId: String?,
        val confidence: String,
        val firmwareVersion: String?,
    ) : IdentityState
}

/** Connection and session state. */
sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connecting(val sessionId: String) : ConnectionState
    data class Connected(
        val sessionId: String,
        val generation: Long,
        val transport: String,
    ) : ConnectionState
    data class Disconnecting(val sessionId: String) : ConnectionState
}

/** Protocol resolution state. */
sealed interface ProtocolState {
    data object Unresolved : ProtocolState
    data class Ambiguous(val candidates: List<String>, val reason: String) : ProtocolState
    data class Resolved(
        val protocolId: String,
        val protocolVersion: String,
        val compatible: Boolean,
    ) : ProtocolState
    data class Incompatible(val reason: String) : ProtocolState
}

/** Capability discovery state. */
sealed interface CapabilityState {
    data object NotDiscovered : CapabilityState
    data class Discovering(val reason: String) : CapabilityState
    data class Ready(val capabilityIds: Set<String>) : CapabilityState
    data class Failed(val reason: String) : CapabilityState
}

/**
 * Feature state: requested, executing, acknowledged, observed kept distinct.
 */
data class FeatureState(
    /** Desired values (user configuration). */
    val desired: Map<String, ObservedValue<String>> = emptyMap(),
    /** In-flight operations by feature id. */
    val executing: Map<String, OperationStatus> = emptyMap(),
    /** Last protocol acknowledgements by feature id. */
    val acknowledged: Map<String, ObservedValue<String>> = emptyMap(),
    /** Last valid device-observed values by feature id. */
    val observed: Map<String, ObservedValue<String>> = emptyMap(),
) {
    companion object {
        val Empty = FeatureState()
    }
}

/** Status of an in-flight feature operation. */
sealed interface OperationStatus {
    data class Pending(val startedAtMillis: Long?) : OperationStatus
    data class Succeeded(val atMillis: Long?) : OperationStatus
    data class Failed(val reason: String) : OperationStatus
    data class Unknown(val reason: String) : OperationStatus
    data object Cancelled : OperationStatus
}

/** Battery state. */
sealed interface BatteryState {
    data object Unknown : BatteryState
    data class Known(
        val levelPercent: Int?,
        val charging: Boolean?,
        val observation: ObservedValue<Unit>?,
    ) : BatteryState
}

/** Audio route and codec state. */
data class AudioState(
    val route: ObservedValue<String>? = null,
    val codec: ObservedValue<String>? = null,
    val codecObservable: Boolean = false,
) {
    companion object {
        val Unknown = AudioState()
    }
}

/** Desired user configuration (Phase 17). */
data class ConfigurationState(
    val values: Map<String, ObservedValue<String>> = emptyMap(),
) {
    companion object {
        val Empty = ConfigurationState()
    }
}

/** Persistence verification state (Phase 18). */
sealed interface PersistenceState {
    data object NotVerified : PersistenceState
    data class Verifying(val scope: String) : PersistenceState
    data class Verified(val scope: String, val atMillis: Long?) : PersistenceState
    data class Failed(val scope: String, val reason: String) : PersistenceState
}

/** Vendor-specific feature state. */
data class VendorFeatureState(
    val values: Map<String, ObservedValue<String>> = emptyMap(),
    val extensionIds: Set<String> = emptySet(),
) {
    companion object {
        val Empty = VendorFeatureState()
    }
}
