package com.omnibuds.core.session

/**
 * The engine's own record of when a session's four moments happened.
 *
 * Timestamps are nullable epoch-millis numbers rather than a date-time type, unchanged
 * from Phase 1's rule for every time in `:core` (ADR-P1-012, ADR-P0-008): the same source
 * has to compile for a non-JVM target, and a clock reading is a platform concern that
 * belongs outside the domain. `null` always means "no reading was taken", never zero,
 * because a zero would claim the session began at the epoch and a UI cannot tell the two
 * apart.
 *
 * Each field has one documented producer, and that is the whole of prompt section 6's
 * "documented semantics" requirement:
 *
 *  - [startedAtEpochMillis] - set once, when the engine created the session. It is the
 *    engine's decision time, not the device's connection time, and it can be later than
 *    either: the phone reports a device, and the session begins when the engine first
 *    attributes it.
 *  - [lastObservedAtEpochMillis] - restated whenever the platform said something about
 *    this device, whether or not anything changed. It is a freshness reading, so a device
 *    that stays connected still moves this field on every round, and a session whose value
 *    is null was created before any round carried a time.
 *  - [disconnectedAtEpochMillis] - set when the session moved to
 *    [com.omnibuds.core.state.ConnectionState.DISCONNECTED] and *cleared* when the same
 *    session resumed (ADR-P4-006's grace), so a resumed session does not carry a stale
 *    disconnect time beside a connected state. Once the session ends, the value records
 *    the disconnect that preceded the end, not the end itself.
 * A session's *end* has no timestamp field, deliberately: the engine drops a terminated
 * session in the same round it publishes `SessionEnded`, so there is no live value that could read
 * one, and a field with no producer is vocabulary standing in for a capability - the same refusal
 * Phase 3 recorded against its own `DEDUPED` kind. The end's *reason* travels on the event instead.
 *
 * ADR-P1-012's other consequence is honoured by construction: the engine never compares
 * these numbers to decide an order. Round sequence is carried by the projection it was
 * read from and by [DeviceState]-level revision, because two readings of a handset clock
 * are not evidence about which came first (research U-4).
 */
data class SessionTimeline(
    val startedAtEpochMillis: Long?,
    val lastObservedAtEpochMillis: Long?,
    val disconnectedAtEpochMillis: Long? = null,
) {
    /** Opens the timeline at the moment the engine created the session. */
    fun openedAt(atEpochMillis: Long?): SessionTimeline =
        copy(startedAtEpochMillis = atEpochMillis, lastObservedAtEpochMillis = atEpochMillis)

    /** Restates freshness. Never touches a terminal or disconnect time. */
    fun restatedAt(atEpochMillis: Long?): SessionTimeline = if (atEpochMillis == null) {
        this
    } else {
        copy(lastObservedAtEpochMillis = atEpochMillis)
    }

    /** Marks the moment the session last lost its link, so a grace window has a start. */
    fun markedDisconnected(atEpochMillis: Long?): SessionTimeline =
        copy(disconnectedAtEpochMillis = disconnectedAtEpochMillis ?: atEpochMillis)

    /** Clears the disconnect mark, because the session resumed inside its grace. */
    fun resumed(): SessionTimeline = copy(disconnectedAtEpochMillis = null)

    companion object {
        /** A timeline for a session that has not yet been opened by a clock reading. */
        fun unopened(): SessionTimeline = SessionTimeline(
            startedAtEpochMillis = null,
            lastObservedAtEpochMillis = null,
        )
    }
}
