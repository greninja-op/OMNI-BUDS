package com.omnibuds.core.session

import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.platform.ConnectedDeviceSnapshot
import com.omnibuds.core.platform.ObservationStage

/**
 * What the engine is entitled to believe about the observation it is reading.
 *
 * This exists because prompt section 12 lists six things a session API must not conflate,
 * and four of them are answers to "why is the list the way it is" rather than answers about
 * any device: not started, permission required, phone off, failed, stopped. The engine's
 * own list can be empty in five different worlds, and a consumer that reads
 * `sessions.isEmpty()` as "no earbuds connected" has made a hardware claim out of an
 * observation status - which is ADR-P3-005's rule applied one layer up, and the reason this
 * type has four cases instead of a boolean and a nullable error.
 *
 * The four are derived from one projection by [of], in one place, so no consumer can
 * re-derive them differently:
 *
 *  - a round that refused is [Refused] and carries Phase 3's own [OmniBudsError], whose
 *    category already distinguishes permission-denied from bluetooth-disabled from
 *    unsupported (ADR-P3-006), so Phase 4 mints no second error vocabulary;
 *  - a round that answered is [Confirmed], and emptiness only means "nobody is connected"
 *    when [unionComplete][Confirmed.unionComplete] says every profile in the union
 *    answered (ADR-P3-015 rule 9);
 *  - announcements without any completed round are [Unconfirmed] - devices may be listed,
 *    and the list is not a census of anything;
 *  - an observation that ended is [Stopped], which is neither a failure nor an empty room.
 *
 * A cancellation is deliberately absent, for ADR-P1-004's reason: a called-off read is not
 * a finding about the device, and Phase 3 does not record one either
 * (`ConnectedDeviceObserver`'s paired-round rule), so deriving a status from a projection
 * cannot invent one here.
 */
sealed interface SessionObservationStatus {
    /** Nothing has been applied yet. An empty list here is the absence of information. */
    data object Unread : SessionObservationStatus

    /**
     * The projection carries devices, but no round has completed to say whether the list is
     * everything the phone can see.
     */
    data object Unconfirmed : SessionObservationStatus

    /**
     * A round answered.
     *
     * [unionComplete] is the only licence for reading an empty session list as "no devices
     * are connected". It is carried rather than recomputed because the projection's own
     * completeness rule - consulted profile support, completed round, nothing left
     * unanswered - is Phase 3's decision and not this layer's to duplicate.
     */
    data class Confirmed(val unionComplete: Boolean) : SessionObservationStatus

    /**
     * The last link round refused, with the platform's reason.
     *
     * [error]'s category is the distinction prompt section 12 asks for; the sessions the
     * engine already holds are unaffected, because a refusal restates nothing
     * (ADR-P3-009) - which is also why this is a status and not a cleared list.
     */
    data class Refused(val error: OmniBudsError) : SessionObservationStatus

    /** The projection stopped observing. Sessions did not go anywhere; nobody is looking. */
    data object Stopped : SessionObservationStatus

    /**
     * True only where an empty session list is a real census: a round answered, and every
     * profile in the union answered within it.
     */
    val isEmptyMeaningful: Boolean
        get() = this is Confirmed && unionComplete

    companion object {
        /**
         * Derives the status from one projection, in the one precedence order that keeps
         * the cases exclusive: an ended observation outranks a refusal, a refusal outranks
         * a completed round, and a completed round outranks announcements that never
         * settled anything.
         */
        fun of(snapshot: ConnectedDeviceSnapshot): SessionObservationStatus {
            val refusal = snapshot.deviceRoundRefusal
            return when {
                snapshot.stage == ObservationStage.STOPPED -> Stopped
                refusal != null -> Refused(refusal)
                snapshot.restsOnCompletedRound -> Confirmed(unionComplete = snapshot.isUnionComplete)
                else -> Unconfirmed
            }
        }
    }
}
