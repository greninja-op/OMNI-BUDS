package com.omnibuds.core.globalstate

import com.omnibuds.core.platform.TimeProvider
import com.omnibuds.core.platform.NoTimeProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Per-device state aggregator.
 *
 * Phase 24 (OB-P24-REQ-007/008/009): validates events, checks ordering,
 * applies property-specific merge, publishes immutable snapshots.
 * One instance owns one device's mutable state; the repository owns
 * one aggregator per device.
 */
class DeviceStateAggregator(
    val deviceId: GlobalDeviceId,
    private val time: TimeProvider = NoTimeProvider,
) {
    private val mutex = Mutex()
    private var state: GlobalDeviceState = GlobalDeviceState.empty(deviceId)
    private var lastSequence: Long = -1L
    private var currentSessionId: String? = null
    private var currentGeneration: Long = 0L
    private val diagnostics = ArrayDeque<StateDiagnostic>(MAX_DIAGNOSTICS)

    companion object {
        const val MAX_DIAGNOSTICS = 100
    }

    /** Current snapshot (immutable). */
    suspend fun snapshot(): GlobalDeviceState = mutex.withLock { state }

    /** Recent diagnostics, newest last, bounded. */
    suspend fun diagnostics(): List<StateDiagnostic> = mutex.withLock { diagnostics.toList() }

    /**
     * Ingest a single event. Returns whether it was applied.
     */
    suspend fun ingest(event: DeviceStateEvent): IngestResult = mutex.withLock {
        // Device correlation.
        if (event.deviceId != deviceId) {
            return IngestResult.Rejected(EventRejection.UnknownDevice(event.deviceId.value))
        }

        // Deduplication: exact sequence repeat.
        if (event.sequence == lastSequence) {
            record(StateDiagnostic.DuplicateEvent(event.sequence, event.sourceId))
            return IngestResult.Duplicate
        }

        // Ordering: older sequences rejected when ordering is known.
        if (event.sequence < lastSequence) {
            record(StateDiagnostic.StaleEvent(event.sequence, lastSequence, event.sourceId))
            return IngestResult.Rejected(
                EventRejection.StaleSequence(expected = lastSequence + 1, got = event.sequence),
            )
        }

        // Session check: events from an old session are rejected once a
        // newer session is established. Sessionless events always apply.
        if (event is DeviceStateEvent.ConnectionChanged) {
            currentSessionId = when (val c = event.connection) {
                is ConnectionState.Connected -> c.sessionId
                is ConnectionState.Connecting -> c.sessionId
                is ConnectionState.Disconnecting -> c.sessionId
                is ConnectionState.Disconnected -> null
            }
            currentGeneration = event.generation
        } else if (event.sessionId != null && currentSessionId != null &&
            event.sessionId != currentSessionId
        ) {
            record(StateDiagnostic.OldSessionEvent(event.sessionId, currentSessionId, event.sourceId))
            return IngestResult.Rejected(
                EventRejection.OldSession(event.sessionId, currentSessionId),
            )
        }

        // Apply the event.
        state = applyEvent(state, event)
        lastSequence = event.sequence
        IngestResult.Applied
    }

    private fun applyEvent(
        current: GlobalDeviceState,
        event: DeviceStateEvent,
    ): GlobalDeviceState {
        val now = time.nowEpochMillis()
        return when (event) {
            is DeviceStateEvent.IdentityUpdated ->
                current.copy(identity = event.identity, publishedAtMillis = now)
            is DeviceStateEvent.ConnectionChanged ->
                current.copy(connection = event.connection, publishedAtMillis = now)
            is DeviceStateEvent.ProtocolResolved ->
                current.copy(protocol = event.protocol, publishedAtMillis = now)
            is DeviceStateEvent.CapabilitiesUpdated ->
                current.copy(capabilities = event.capabilities, publishedAtMillis = now)
            is DeviceStateEvent.FeatureDesiredChanged ->
                current.copy(
                    features = current.features.copy(
                        desired = current.features.desired + (event.featureId to event.value),
                    ),
                    configuration = current.configuration.copy(
                        values = current.configuration.values + (event.featureId to event.value),
                    ),
                    publishedAtMillis = now,
                )
            is DeviceStateEvent.FeatureOperationChanged ->
                current.copy(
                    features = current.features.copy(
                        executing = current.features.executing + (event.featureId to event.status),
                    ),
                    publishedAtMillis = now,
                )
            is DeviceStateEvent.FeatureObserved ->
                // A failed/ambiguous operation never overwrites confirmed state —
                // observation events only carry valid observations.
                current.copy(
                    features = current.features.copy(
                        observed = current.features.observed + (event.featureId to event.value),
                    ),
                    publishedAtMillis = now,
                )
            is DeviceStateEvent.BatteryUpdated ->
                current.copy(battery = event.battery, publishedAtMillis = now)
            is DeviceStateEvent.AudioChanged ->
                current.copy(audio = event.audio, publishedAtMillis = now)
            is DeviceStateEvent.PersistenceUpdated ->
                current.copy(persistence = event.persistence, publishedAtMillis = now)
            is DeviceStateEvent.VendorFeatureObserved ->
                current.copy(
                    vendorFeatures = current.vendorFeatures.copy(
                        values = current.vendorFeatures.values + (event.featureId to event.value),
                        extensionIds = current.vendorFeatures.extensionIds + event.extensionId,
                    ),
                    publishedAtMillis = now,
                )
        }
    }

    private fun record(diagnostic: StateDiagnostic) {
        if (diagnostics.size >= MAX_DIAGNOSTICS) {
            diagnostics.removeFirst()
        }
        diagnostics.addLast(diagnostic)
    }
}

/**
 * Typed diagnostics for the aggregation pipeline.
 */
sealed interface StateDiagnostic {
    data class DuplicateEvent(val sequence: Long, val sourceId: String) : StateDiagnostic
    data class StaleEvent(val sequence: Long, val lastApplied: Long, val sourceId: String) : StateDiagnostic
    data class OldSessionEvent(
        val eventSession: String?,
        val currentSession: String?,
        val sourceId: String,
    ) : StateDiagnostic
    data class ConflictDetected(val property: String, val reason: String) : StateDiagnostic
}
