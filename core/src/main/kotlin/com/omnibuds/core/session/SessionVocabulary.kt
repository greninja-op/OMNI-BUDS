package com.omnibuds.core.session

/**
 * Where the session engine itself is, as distinct from where any device is.
 *
 * This is engine bookkeeping, not a second lifecycle axis: ADR-P4-002 refuses a
 * [com.omnibuds.core.state.ConnectionState] look-alike, and nothing here describes a
 * device. [RUNNING] says the engine has applied at least one projection; whether that
 * projection said anything about anybody is a separate question owned by
 * [SessionObservationStatus], which is why a stopped engine and an unconfirmed one are
 * different values rather than one boolean.
 */
enum class SessionEngineStage {
    /** Created, nothing applied. */
    NOT_STARTED,

    /** Applying published projections. */
    RUNNING,

    /** Ended by request. Sessions were cleared with a reason, and a restart is legal. */
    STOPPED,
}

/**
 * How confidently a session can be attributed to one device across rounds.
 *
 * Two values, and [AMBIGUOUS] is the whole of how this engine answers prompt section 8's
 * instruction to "represent ambiguity instead of silently merging devices": a device the
 * platform named but did not identify is published as a session, counted, and never
 * matched to anything - not merged with a similar-looking session, and not carried into a
 * later round as though it had been recognised. ADR-P3-015 rule 6 applies the same rule
 * to projection records; applying it here is where it actually matters, because a session
 * is the thing a UI would merge.
 *
 * The absence of a key is not a ranking. An [AMBIGUOUS] session is as real as a
 * [KEYED] one while it lasts; what it cannot do is last.
 */
enum class SessionIdentityBasis {
    /** Attributable to one device across rounds, because the platform gave a key. */
    KEYED,

    /** Reported by the platform with nothing to attribute it by. Never matched, never merged. */
    AMBIGUOUS,
}

/**
 * Why the engine stopped tracking a device, in engine terms only.
 *
 * Four reasons because the difference between them is the question a later phase will
 * ask. Two of them are statements about the observation rather than about a device -
 * [OBSERVATION_STOPPED] and [ENGINE_CANCELLED] - and that is the point: the engine
 * clearing its own list because it was cancelled is not evidence that the devices went
 * away, and prompt section 11 forbids presenting a device as disconnected when the
 * actual fact is that nobody is looking any more.
 *
 * Nothing here claims to know why a device left. [ABSENT_FROM_COMPLETE_UNION] says the
 * phone enumerated every profile it could and this device was in none of them, which is
 * the most a census can support (ADR-P3-015 rule 4); it does not say the headset was
 * switched off, put away or unpaired.
 */
enum class SessionTermination {
    /** Absent from one complete union, which moved the session to disconnected. */
    ABSENT_FROM_COMPLETE_UNION,

    /** Already disconnected by a completed union, and absent from the one after it. */
    PROVEN_DISCONNECT_PAST_GRACE,

    /**
     * An unattributed session reaching the end of the one projection it could exist in.
     *
     * Not evidence that the device went away: nothing can tell an unkeyed session from a new
     * unkeyed session, so carrying one forward would be the merge ADR-P3-010 refuses, and
     * prompt section 8 asks for ambiguity to be represented rather than resolved. The reason
     * says which of the two it was.
     */
    AMBIGUOUS_SESSION_EXPIRED,

    /** The projection stopped observing, so no session outlives the observation. */
    OBSERVATION_STOPPED,

    /** The engine's own caller went away. */
    ENGINE_CANCELLED,
}
