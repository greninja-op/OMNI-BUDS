package com.omnibuds.core.session

import com.omnibuds.core.common.OmniBudsError

/**
 * An edge the engine observed in its own state, not a message about a device.
 *
 * Seven of prompt section 13's eight candidates existed at the end of Phase 4, and the missing one is
 * the decision: `SESSION_ACTIVATED` is refused, because "activated" reads as a device becoming
 * usable, which is the claim ADR-P4-003 makes unreachable in this phase - a session moving
 * to [com.omnibuds.core.state.ConnectionState.CONNECTED] is the phone reporting a link, and
 * first arrival is already [SessionCreated] while a return is already [SessionReconnected].
 * An event whose meaning is a subset of two other events is how a UI ends up displaying a
 * readiness nobody verified (ADR-P4-008). [SessionIdentityEnriched] is the eighth, and it was added
 * by Phase 5 rather than derived from that list: it is the edge form of an identification arriving,
 * which prompt section 14 requires to be visible without being a connection transition.
 *
 * Three rules hold for every case, and they are the reason each payload is as small as it is:
 *
 *  - **No device text.** A payload carries the engine's [sessionId] and, where it matters, the
 *    *number* of identity fields known, never the name itself and never a key's content. A
 *    display name is user-chosen text and an address is a handle on the user's belongings; an
 *    event is the one shape a consumer is most likely to log (SEC-LOG-002, SEC-ID-003).
 *  - **No restatement.** An update that changed neither the connection state nor the identity
 *    publishes nothing, so a flapping profile cannot produce an event storm and a duplicate
 *    disconnect is silent the second time (ADR-P4-008's duplicate suppression at the edge).
 *  - **Not a history.** Published on a `SharedFlow` with `replay = 0`, so a collector that was
 *    not running misses the edge and reads [DeviceSessionSnapshot] instead. The state flow is
 *    authoritative and this is notification only (Phase 0 `specs.md` rules 5.5-5.6), which is
 *    also why nothing here is ever stored.
 *
 * [atEpochMillis] is the engine's reading of the clock at publication and may be null when no
 * clock was injected; it is metadata for ordering with other events, never a claim about when
 * the device did anything.
 */
sealed interface DeviceSessionEvent {
    /** A session the engine created for a device it had not tracked before. */
    data class SessionCreated(
        val sessionId: String,
        val basis: SessionIdentityBasis,
        val atEpochMillis: Long?,
    ) : DeviceSessionEvent

    /** The authoritative connection state moved, and this is where it moved from and to. */
    data class SessionUpdated(
        val sessionId: String,
        val from: com.omnibuds.core.state.ConnectionState,
        val to: com.omnibuds.core.state.ConnectionState,
        val atEpochMillis: Long?,
    ) : DeviceSessionEvent

    /**
     * A device stopped being reported as connected.
     *
     * [evidence] keeps the two ways this happens apart, because they mean different things to a
     * consumer: the platform said the link is down, versus the device was absent from a union in
     * which every profile answered (ADR-P3-015 rule 4). Absence from an unanswered or refused
     * round produces no event at all, because it is not evidence.
     */
    data class SessionDisconnected(
        val sessionId: String,
        val evidence: DisconnectEvidence,
        val atEpochMillis: Long?,
    ) : DeviceSessionEvent

    /** A session still inside its grace was reported connected again, on the same id. */
    data class SessionReconnected(
        val sessionId: String,
        val atEpochMillis: Long?,
    ) : DeviceSessionEvent

    /** The engine stopped tracking this device, for one of the four reasons in [SessionTermination]. */
    data class SessionEnded(
        val sessionId: String,
        val reason: SessionTermination,
        val atEpochMillis: Long?,
    ) : DeviceSessionEvent

    /**
     * The platform reported something new about which device this is.
     *
     * Counts rather than values: [knownFieldCountBefore] and [knownFieldCountAfter] say the
     * identity got better without putting a name into an event stream. Merging is
     * fill-only by [com.omnibuds.core.device.DeviceIdentity.mergedWith], so a restated name is
     * not this event's subject - only a newly filled field is.
     */
    data class SessionIdentityChanged(
        val sessionId: String,
        val knownFieldCountBefore: Int,
        val knownFieldCountAfter: Int,
        val atEpochMillis: Long?,
    ) : DeviceSessionEvent

    /**
     * The observation refused, with Phase 3's error and its category intact.
     *
     * There is no session id, because this is not about a device: it is the engine reporting
     * that it was told nothing. Existing sessions are unchanged by it (ADR-P4-007), and a
     * consumer that reads this event as "devices went away" has made the mistake the type
     * exists to prevent.
     */
    data class SessionObservationFailed(
        val error: OmniBudsError,
        val atEpochMillis: Long?,
    ) : DeviceSessionEvent

    /**
     * Phase 5 attached a product identity to a session that already existed.
     *
     * Added with [com.omnibuds.core.device.IdentificationResult] (ADR-P5-009), and deliberately not
     * folded into [SessionIdentityChanged]: that event counts *reported* identity fields, whereas
     * this edge says a *conclusion* arrived and the reported identity may not have moved at all.
     * A consumer that treated them as one event would read a matched model as though the device had
     * reported it. [isIdentified] is the only content beyond the rung - which manufacturer or model
     * was decided is readable from the authoritative snapshot, and repeating it here would put
     * device text into the one shape a consumer is most likely to log (SEC-LOG-002).
     */
    data class SessionIdentityEnriched(
        val sessionId: String,
        val confidence: com.omnibuds.core.device.IdentificationConfidence,
        val isIdentified: Boolean,
        val atEpochMillis: Long?,
    ) : DeviceSessionEvent
}

/** How a disconnect was established, since the difference survives into what a consumer may say. */
enum class DisconnectEvidence {
    /** The platform positively reported no link. */
    REPORTED,

    /** Absent from a union in which every profile answered. */
    ABSENT_FROM_COMPLETE_UNION,
}
