package com.omnibuds.core.device

import com.omnibuds.core.state.SessionClassification

/**
 * Which device a session belongs to, what has been observed about it, and whether the
 * user chose to keep it.
 *
 * **Connection state is deliberately not held here.** `DeviceState.connection` in
 * `com.omnibuds.core.session` is the single authoritative place it lives. This type
 * previously carried its own copy, which meant two values could disagree about the
 * same device — exactly the failure Phase 1 prompt section 24 forbids, and what the
 * Phase 1 architecture review rejected before the phase was accepted. What remains
 * here is the part that is genuinely session-level and changes far less often:
 * identity, discovery evidence, and the user's save choice.
 *
 * It is a value: applying an update produces a new session, and the caller that owns
 * the session decides to keep the result (`docs/phases/phase-0/specs.md` section 5.7).
 *
 * Timestamps are nullable epoch-millis numbers rather than a date-time type on
 * purpose: the same source must compile unchanged for a non-JVM target later
 * (Phase 1 prompt section 8, ADR-P0-008, ADR-P1-012), and a clock reading is a
 * platform concern that belongs outside core. A null time means "no observation was
 * recorded" and is never filled in with 0.
 */
data class DeviceSession(
    /** Identifies this session only; it is not a device identity and is not stable across reconnects. */
    val sessionId: String,

    /** Descriptive identity established so far; unknown fields stay unknown. */
    val identity: DeviceIdentity,

    /** Evidence from the discovery passes run so far, or null while none has run. */
    val fingerprint: DeviceFingerprint?,

    /** Whether the user saved this device or it is present only for this session. */
    val classification: SessionClassification,

    /** When the session began, if the platform recorded it. */
    val createdAtEpochMillis: Long? = null,
) {
    /**
     * Records newly gathered evidence against this session.
     *
     * Identity discovery and transport evidence can arrive at any moment, so they are
     * not tied to a state move. [updatedIdentity] can only fill gaps: merging never
     * restates a field this session already knows, because re-observing a name is not
     * evidence that the earlier observation was wrong ([DeviceIdentity.mergedWith]).
     * A non-null [updatedFingerprint] replaces the previous one, because a fingerprint
     * is the snapshot of one discovery pass and merging two passes together would
     * assert that both were true of the device at the same time.
     */
    fun withEvidence(
        updatedIdentity: DeviceIdentity,
        updatedFingerprint: DeviceFingerprint? = null,
    ): DeviceSession = copy(
        identity = identity.mergedWith(updatedIdentity),
        fingerprint = updatedFingerprint ?: fingerprint,
    )

    /**
     * Marks the session as saved, mirroring the user's "Add to My Devices" action
     * (master section 5).
     *
     * Nothing is persisted here and nothing else changes: identity, fingerprint and
     * the creation time are carried through untouched, and storage is the persistence
     * layer's job. Saving is only ever an explicit user action — a device that was
     * merely connected must not promote itself (ADR-P0-004, SEC-ID-005).
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

    /** True only where the user explicitly chose to keep this device. */
    val isSaved: Boolean
        get() = classification == SessionClassification.SAVED

    companion object {
        /** A temporary session for a device nobody chose to keep. */
        fun temporary(
            sessionId: String,
            identity: DeviceIdentity,
            fingerprint: DeviceFingerprint? = null,
            createdAtEpochMillis: Long? = null,
        ): DeviceSession = DeviceSession(
            sessionId = sessionId,
            identity = identity,
            fingerprint = fingerprint,
            classification = SessionClassification.TEMPORARY,
            createdAtEpochMillis = createdAtEpochMillis,
        )
    }
}
