package com.omnibuds.core.session

import com.omnibuds.core.audio.AudioTransportState
import com.omnibuds.core.capability.DeviceCapabilities
import com.omnibuds.core.capability.FeatureCapability
import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.device.BatteryState
import com.omnibuds.core.device.DeviceIdentity
import com.omnibuds.core.state.CapabilityState
import com.omnibuds.core.state.ConnectionState
import com.omnibuds.core.state.ConnectionStateTransitions

/**
 * The single authoritative record of what is currently known about one device.
 *
 * Phase 1 prompt section 24 forbids the three-way disagreement it describes - UI
 * believing ANC is on while the backend says off and the notification says unknown.
 * That is only preventable if exactly one value is the truth and every surface reads
 * it, so this type is that value: state flows
 * transport/protocol -&gt; [DeviceState] -&gt; application state -&gt; UI, never sideways.
 *
 * Instances are immutable and every mutation returns a new value with a higher
 * [revision]. [applyIfNewer] is what makes a late callback harmless: a stale response
 * cannot overwrite a newer fact, because the ordering is carried by the data rather
 * than trusted from the caller.
 */
data class DeviceState(
    val sessionId: String,
    val identity: DeviceIdentity,
    val connection: ConnectionState,
    val capabilities: DeviceCapabilities,
    val battery: BatteryState,
    val audio: AudioTransportState,
    val revision: Long,
    val lastUpdatedEpochMillis: Long?,
) {
    init {
        require(revision >= INITIAL_REVISION) {
            "revision must start at $INITIAL_REVISION, was $revision"
        }
    }

    /**
     * True only while every surface may treat reads and writes as meaningful.
     */
    val isOperational: Boolean
        get() = ConnectionStateTransitions.isOperational(connection)

    fun withCapability(
        feature: FeatureId,
        capability: FeatureCapability,
        atEpochMillis: Long?,
    ): DeviceState = DeviceState(
        sessionId = sessionId,
        identity = identity,
        connection = connection,
        capabilities = capabilities.with(feature, capability),
        battery = battery,
        audio = audio,
        revision = revision + 1,
        lastUpdatedEpochMillis = atEpochMillis,
    )

    fun withCapabilities(next: DeviceCapabilities, atEpochMillis: Long?): DeviceState =
        copy(capabilities = next, revision = revision + 1, lastUpdatedEpochMillis = atEpochMillis)

    fun withBattery(next: BatteryState, atEpochMillis: Long?): DeviceState =
        copy(battery = next, revision = revision + 1, lastUpdatedEpochMillis = atEpochMillis)

    fun withAudio(next: AudioTransportState, atEpochMillis: Long?): DeviceState =
        copy(audio = next, revision = revision + 1, lastUpdatedEpochMillis = atEpochMillis)

    /**
     * Moves connection state, refusing an illegal move as a structured failure.
     *
     * This is the only place connection state is held, which is what makes
     * [com.omnibuds.core.device.DeviceSession] a record about identity and saving
     * rather than a second competing opinion about where the device has got to
     * (Phase 1 prompt section 24). The refusal returns
     * [OmniBudsErrorCategory.INVALID_STATE] instead of throwing or silently ignoring
     * the move: a callback arriving after a disconnect must not be able to drag a
     * device into [ConnectionState.CONTROL_SESSION] (prompt section 30), and the
     * caller keeps its error model rather than an exception.
     */
    fun attemptConnection(
        next: ConnectionState,
        atEpochMillis: Long?,
    ): OperationOutcome<DeviceState> {
        if (!ConnectionStateTransitions.canTransition(connection, next)) {
            return OperationOutcome.Failure(
                OmniBudsError(
                    category = OmniBudsErrorCategory.INVALID_STATE,
                    operationId = CONNECTION_OPERATION_ID,
                    detail = "cannot move session state from $connection to $next",
                ),
            )
        }
        return OperationOutcome.Success(
            copy(
                connection = next,
                revision = revision + 1,
                lastUpdatedEpochMillis = atEpochMillis,
            ),
        )
    }

    /**
     * Adopts [candidate] only when it is genuinely newer for the same session.
     *
     * This is the model-level answer to a stale response arriving after a user
     * command: the discarded value is not an error, it is simply older, and the
     * caller can tell the difference because the returned revision did not move.
     */
    fun applyIfNewer(candidate: DeviceState): DeviceState =
        if (candidate.sessionId == sessionId && candidate.revision > revision) candidate else this

    /**
     * Discards every device-derived fact except identity, because the session is no
     * longer real. Called when a device disconnects: capabilities that were
     * [CapabilityState.PERSISTENCE_VERIFIED] keep their verification history, but any
     * live reading (battery, audio) becomes unknown rather than last-known.
     */
    fun invalidatedForDisconnect(atEpochMillis: Long?): DeviceState = DeviceState(
        sessionId = sessionId,
        identity = identity,
        connection = ConnectionState.DISCONNECTED,
        capabilities = capabilities,
        battery = BatteryState.unknown(),
        audio = AudioTransportState.unobserved(),
        revision = revision + 1,
        lastUpdatedEpochMillis = atEpochMillis,
    )

    companion object {
        const val INITIAL_REVISION = 0L

        private const val CONNECTION_OPERATION_ID = "device-state.attemptConnection"

        /**
         * A session before anything is known. Nothing here is zeroed or asserted:
         * capabilities are empty, which reads back as unknown for every feature, and
         * audio and battery are explicitly unobserved.
         */
        fun initial(sessionId: String, identity: DeviceIdentity): DeviceState = DeviceState(
            sessionId = sessionId,
            identity = identity,
            connection = ConnectionState.UNKNOWN,
            capabilities = DeviceCapabilities.empty(),
            battery = BatteryState.unknown(),
            audio = AudioTransportState.unobserved(),
            revision = INITIAL_REVISION,
            lastUpdatedEpochMillis = null,
        )
    }
}
