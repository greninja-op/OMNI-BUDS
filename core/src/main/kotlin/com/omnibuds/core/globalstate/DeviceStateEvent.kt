package com.omnibuds.core.globalstate

/**
 * Typed events from subsystems.
 *
 * Phase 24 (OB-P24-REQ-007/008): every event carries device correlation,
 * session correlation, and a monotonic sequence number for ordering.
 */
sealed interface DeviceStateEvent {
    val deviceId: GlobalDeviceId
    val sessionId: String?
    val sequence: Long
    val sourceId: String

    data class IdentityUpdated(
        override val deviceId: GlobalDeviceId,
        override val sessionId: String?,
        override val sequence: Long,
        override val sourceId: String,
        val identity: IdentityState,
    ) : DeviceStateEvent

    data class ConnectionChanged(
        override val deviceId: GlobalDeviceId,
        override val sessionId: String?,
        override val sequence: Long,
        override val sourceId: String,
        val connection: ConnectionState,
        val generation: Long,
    ) : DeviceStateEvent

    data class ProtocolResolved(
        override val deviceId: GlobalDeviceId,
        override val sessionId: String?,
        override val sequence: Long,
        override val sourceId: String,
        val protocol: ProtocolState,
    ) : DeviceStateEvent

    data class CapabilitiesUpdated(
        override val deviceId: GlobalDeviceId,
        override val sessionId: String?,
        override val sequence: Long,
        override val sourceId: String,
        val capabilities: CapabilityState,
    ) : DeviceStateEvent

    data class FeatureDesiredChanged(
        override val deviceId: GlobalDeviceId,
        override val sessionId: String?,
        override val sequence: Long,
        override val sourceId: String,
        val featureId: String,
        val value: ObservedValue<String>,
    ) : DeviceStateEvent

    data class FeatureOperationChanged(
        override val deviceId: GlobalDeviceId,
        override val sessionId: String?,
        override val sequence: Long,
        override val sourceId: String,
        val featureId: String,
        val status: OperationStatus,
    ) : DeviceStateEvent

    data class FeatureObserved(
        override val deviceId: GlobalDeviceId,
        override val sessionId: String?,
        override val sequence: Long,
        override val sourceId: String,
        val featureId: String,
        val value: ObservedValue<String>,
    ) : DeviceStateEvent

    data class BatteryUpdated(
        override val deviceId: GlobalDeviceId,
        override val sessionId: String?,
        override val sequence: Long,
        override val sourceId: String,
        val battery: BatteryState,
    ) : DeviceStateEvent

    data class AudioChanged(
        override val deviceId: GlobalDeviceId,
        override val sessionId: String?,
        override val sequence: Long,
        override val sourceId: String,
        val audio: AudioState,
    ) : DeviceStateEvent

    data class PersistenceUpdated(
        override val deviceId: GlobalDeviceId,
        override val sessionId: String?,
        override val sequence: Long,
        override val sourceId: String,
        val persistence: PersistenceState,
    ) : DeviceStateEvent

    data class VendorFeatureObserved(
        override val deviceId: GlobalDeviceId,
        override val sessionId: String?,
        override val sequence: Long,
        override val sourceId: String,
        val featureId: String,
        val value: ObservedValue<String>,
        val extensionId: String,
    ) : DeviceStateEvent
}

/**
 * Reason an event was rejected.
 */
sealed interface EventRejection {
    data class StaleSequence(val expected: Long, val got: Long) : EventRejection
    data class OldSession(val eventSession: String?, val currentSession: String?) : EventRejection
    data class UnknownDevice(val deviceId: String) : EventRejection
    data class Duplicate(val sequence: Long) : EventRejection
}

/**
 * The result of ingesting an event.
 */
sealed interface IngestResult {
    data object Applied : IngestResult
    data class Rejected(val reason: EventRejection) : IngestResult
    data object Duplicate : IngestResult
}
