package com.omnibuds.core.platform

/**
 * What the platform currently reports about one device's *link*, independent of OmniBuds' own work.
 *
 * This is deliberately not `com.omnibuds.core.state.ConnectionState`. That type is a session
 * position - its own KDoc says `IDENTIFYING` and `CAPABILITY_DISCOVERY` are OmniBuds' work - while
 * this one reports what a Bluetooth stack says about a link it did not create. Merging them would
 * put two facts in one field, so a transition could not say which one moved (ADR-P3-001; Phase 1
 * prompt section 24).
 *
 * [CONNECTING] and [DISCONNECTING] exist because Android's profile state constants name them.
 * Whether a given observation mechanism can actually *deliver* an intermediate state is a platform
 * question, and Phase 3 prompt section 6 forbids inventing one: a mechanism that only ever reports
 * the two settled states will only ever produce the two settled states here, and no code path may
 * synthesise the missing one.
 *
 * [UNKNOWN] is not [DISCONNECTED]. It means no report was obtained - permission refused, adapter
 * unreadable, device not enumerated - and rendering it as disconnected tells a user their earbuds
 * are off when all that is known is that nothing was seen (ADR-P0-016).
 */
enum class DeviceConnectionState {
    UNKNOWN,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    DISCONNECTED,
}

/** The one state that means audio-or-data transport is actually up for this device right now. */
fun DeviceConnectionState.isConnected(): Boolean = this == DeviceConnectionState.CONNECTED

/**
 * True only when the platform positively reported the link down.
 *
 * The asymmetry with [isConnected] is the point: `UNKNOWN` is neither, and a predicate that treated
 * absence as absence-of-connection would turn a permission refusal into a disconnect event
 * (ADR-P2-012's rule, applied one layer up).
 */
fun DeviceConnectionState.isProvablyDisconnected(): Boolean = this == DeviceConnectionState.DISCONNECTED

/** True when no settled state is known, whether because nothing was read or because a transition is mid-flight. */
fun DeviceConnectionState.isUnsettled(): Boolean =
    this == DeviceConnectionState.UNKNOWN ||
    this == DeviceConnectionState.CONNECTING ||
    this == DeviceConnectionState.DISCONNECTING

/** Whether the platform reported anything at all about this link. */
fun DeviceConnectionState.isKnown(): Boolean = this != DeviceConnectionState.UNKNOWN

/**
 * The legal moves between platform link states, as documentation that can fail a build.
 *
 * Phase 1 established the pattern with `ConnectionStateTransitions`: a table is what stops a stale
 * callback from asserting a state the device was never in. Two properties differ from the session
 * table, both of them the platform's rather than ours. `UNKNOWN` may be followed by any state,
 * because the first report about a device is whatever the stack happens to say. And two moves are
 * refused because the platform does not make them: `CONNECTING` never becomes `DISCONNECTING` - a
 * connection attempt reports connected or disconnected, not the other transition - and
 * `DISCONNECTING` never becomes `CONNECTED` without a settled report in between.
 *
 * A repeat of the current state is idempotent rather than illegal, because broadcast delivery is
 * not reliable enough to promise uniqueness and an observation engine that treats a duplicate as a
 * defect will spend its time inventing failures (Phase 3 prompt section 9).
 *
 * The table constrains reports, not inferences. An engine that has decided a device is "probably
 * connecting now" has not received a transition; it has fabricated one, and prompt section 6 forbids
 * that specifically for the intermediate states.
 */
object DeviceConnectionStateTransitions {

    private val legal: Map<DeviceConnectionState, Set<DeviceConnectionState>> = mapOf(
        DeviceConnectionState.UNKNOWN to setOf(
            DeviceConnectionState.CONNECTING,
            DeviceConnectionState.CONNECTED,
            DeviceConnectionState.DISCONNECTING,
            DeviceConnectionState.DISCONNECTED,
        ),

        DeviceConnectionState.CONNECTING to setOf(
            DeviceConnectionState.CONNECTED,
            DeviceConnectionState.DISCONNECTED,
            DeviceConnectionState.UNKNOWN,
        ),

        DeviceConnectionState.CONNECTED to setOf(
            DeviceConnectionState.DISCONNECTING,
            DeviceConnectionState.DISCONNECTED,
            DeviceConnectionState.UNKNOWN,
        ),

        DeviceConnectionState.DISCONNECTING to setOf(
            DeviceConnectionState.DISCONNECTED,
            DeviceConnectionState.UNKNOWN,
        ),

        DeviceConnectionState.DISCONNECTED to setOf(
            DeviceConnectionState.CONNECTING,
            DeviceConnectionState.CONNECTED,
            DeviceConnectionState.UNKNOWN,
        ),
    )

    /** Whether `from -> to` is a move the platform could report, with a same-state repeat allowed. */
    fun isLegal(from: DeviceConnectionState, to: DeviceConnectionState): Boolean =
        from == to || legal.getValue(from).contains(to)

    /**
     * Why a move was refused, or null when it is allowed.
     *
     * A structured reason rather than a boolean, because the engine has to report a refused
     * transition as a fact about what it saw - not silently drop it, and not apply it anyway
     * (Phase 3 prompt section 11: handle repeated events idempotently, do not swallow).
     */
    fun refusalReason(from: DeviceConnectionState, to: DeviceConnectionState): String? =
        if (isLegal(from, to)) {
            null
        } else {
            "the platform cannot report $from becoming $to; the observation was kept at $from"
        }

    /** Every state the table can leave, so a new enum member cannot arrive without a row. */
    val coveredStates: Set<DeviceConnectionState>
        get() = legal.keys
}
