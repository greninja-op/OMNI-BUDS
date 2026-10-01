package com.omnibuds.core.device

import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.state.ConnectionState
import com.omnibuds.core.state.ConnectionStateTransitions
import com.omnibuds.core.state.SessionClassification

/**
 * One OmniBuds session with one device: what it is, what has been observed about it,
 * where it has got to, and whether the user chose to keep it.
 *
 * This is the single authoritative place a session's connection state lives
 * (Phase 1 prompt section 24). Nothing else holds a copy of it, so a UI surface, a
 * future notification and a future state engine all read the same value instead of
 * each keeping its own opinion. It is a value: applying an update produces a new
 * session, and the caller that owns the session decides to keep the result
 * (`docs/phases/phase-0/specs.md` section 5.7).
 *
 * Timestamps are nullable epoch-millis numbers rather than a date-time type on
 * purpose: the same source must compile unchanged for a non-JVM target later
 * (Phase 1 prompt section 8, ADR-P0-008), and a clock reading is a platform concern
 * that belongs outside core. A null time means "no observation was recorded" and is
 * never filled in with 0.
 */
data class DeviceSession(
    /** Identifies this session only; it is not a device identity and is not stable across reconnects. */
    val sessionId: String,

    /** Descriptive identity established so far; unknown fields stay unknown. */
    val identity: DeviceIdentity,

    /** Evidence from the discovery passes run so far, or null while none has run. */
    val fingerprint: DeviceFingerprint?,

    /** The one authoritative connection state of this session. */
    val connectionState: ConnectionState,

    /** Whether the user saved this device or it is present only for this session. */
    val classification: SessionClassification,

    /** When the session began, if the platform recorded it. */
    val createdAtEpochMillis: Long? = null,

    /** When [connectionState] last changed, if the platform recorded it. */
    val lastStateUpdateEpochMillis: Long? = null,
) {
    /** Whether a control session is genuinely usable right now. */
    val isOperational: Boolean
        get() = ConnectionStateTransitions.isOperational(connectionState)

    /**
     * Moves the session to [next], refusing an illegal move instead of performing it.
     *
     * The refusal is a structured `Failure` with
     * [OmniBudsErrorCategory.INVALID_STATE], not an exception and not a silent
     * success: a stale callback must not be able to drive a disconnected device
     * straight into [ConnectionState.CONTROL_SESSION] (Phase 1 prompt section 30),
     * and a state machine that throws takes the caller's error model away from it.
     * Legality is decided by [ConnectionStateTransitions], the kernel's single
     * authority, so that every state can fall into [ConnectionState.ERROR] and a
     * self-transition stays idempotent.
     *
     * [updatedAtEpochMillis] is whatever the platform's clock said at the moment of
     * the move, or null when no reading was taken. Passing null leaves
     * [lastStateUpdateEpochMillis] unknown rather than inheriting the previous stamp,
     * because that stamp describes a different state at a different moment; carrying
     * it forward would report an observation time that was never observed.
     */
    fun transitionedTo(
        next: ConnectionState,
        updatedAtEpochMillis: Long? = null,
    ): OperationOutcome<DeviceSession> {
        if (!ConnectionStateTransitions.canTransition(connectionState, next)) {
            return OperationOutcome.Failure(
                OmniBudsError(
                    category = OmniBudsErrorCategory.INVALID_STATE,
                    operationId = TRANSITION_OPERATION_ID,
                    detail = "cannot move a session from $connectionState to $next",
                ),
            )
        }
        return OperationOutcome.Success(
            copy(connectionState = next, lastStateUpdateEpochMillis = updatedAtEpochMillis),
        )
    }

    /**
     * Records newly gathered evidence against this session without moving its state.
     *
     * Identity discovery and transport evidence can arrive at any state, so folding
     * them into [transitionedTo] would imply that evidence only arrives with a legal
     * state move. [updatedIdentity] can only fill gaps — merge never restates a field
     * this session already knows ([DeviceIdentity.mergedWith] explains why). A
     * non-null [updatedFingerprint] replaces the previous one, because a fingerprint
     * is the snapshot of one discovery pass and merging two passes together would
     * assert that both were true of the device at the same time.
     *
     * [atEpochMillis] follows the same rule as [transitionedTo]: null keeps the update
     * time unknown instead of reusing a stamp from an earlier moment.
     */
    fun withStateUpdate(
        updatedIdentity: DeviceIdentity,
        updatedFingerprint: DeviceFingerprint? = null,
        atEpochMillis: Long? = null,
    ): DeviceSession = copy(
        identity = identity.mergedWith(updatedIdentity),
        fingerprint = updatedFingerprint ?: fingerprint,
        lastStateUpdateEpochMillis = atEpochMillis,
    )

    /**
     * Marks the session as saved, mirroring the user's "Add to My Devices" action
     * (master section 5).
     *
     * Nothing is persisted here and nothing else changes: [connectionState],
     * [identity], [fingerprint] and the timestamps are carried through untouched, and
     * storage is the persistence layer's job. Saving is only ever an explicit user
     * action — a device that was merely connected must not promote itself
     * (ADR-P0-004, SEC-ID-005).
     */
    fun save(): DeviceSession = copy(classification = SessionClassification.SAVED)

    /**
     * Drops the saved classification, returning the session to
     * [SessionClassification.TEMPORARY].
     *
     * The scope is stated precisely on purpose: this says only that *this session
     * value* is no longer marked as saved. It does not unpair the device, does not
     * clear the phone's Bluetooth pairing record, does not delete the saved device
     * record held elsewhere, and does not assert anything about the hardware — which
     * OmniBuds has no way to change from here (Phase 1 prompt sections 52 and 53).
     * Complete deletion across identifier, fingerprint and cached observations is
     * SEC-ID-007's requirement on the persistence and platform layers, not a property
     * this type can honestly claim.
     */
    fun forget(): DeviceSession = copy(classification = SessionClassification.TEMPORARY)

    companion object {
        private const val TRANSITION_OPERATION_ID = "device-session.transition"
    }
}
