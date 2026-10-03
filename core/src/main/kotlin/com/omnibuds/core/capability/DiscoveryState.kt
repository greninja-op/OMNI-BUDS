package com.omnibuds.core.capability

/**
 * Where one capability-discovery pass has got to — its own lifecycle, not the session's or a channel's.
 *
 * This is a fourth axis beside [com.omnibuds.core.state.ConnectionState] (the device link),
 * [com.omnibuds.core.transport.TransportState] (a channel) and
 * [com.omnibuds.core.protocol.ProtocolState] (a protocol session), and it is kept separate for the same
 * reason those are: a discovery pass can be [PARTIALLY_COMPLETE] while the link is connected and the
 * protocol is ready, and reading one as another would hide exactly the partial-failure state prompt §10/§14
 * insist stays visible.
 *
 * [COMPLETE] means every *attemptable* operation reached a conclusion — it never means "every feature is
 * known", and it is never reached while a mandatory step is unfinished (§14). [PARTIALLY_COMPLETE] is the
 * honest middle: some capabilities settled, some failed or timed out, and — crucially — it is not the same
 * as [COMPLETE] and not the same as [FAILED].
 */
enum class DiscoveryState {
    /** Nothing has been asked yet. */
    NOT_STARTED,

    /** The pass is setting up (resolving what to ask); no capability has been read. */
    INITIALIZING,

    /** Capability reads are in flight. */
    DISCOVERING,

    /** Some capabilities concluded, at least one did not; the successful evidence is kept (§10). */
    PARTIALLY_COMPLETE,

    /** Every attemptable operation reached a conclusion — successful, partial, or an explicit unsupported. */
    COMPLETE,

    /** The pass could not proceed at all (session gone, protocol unresolved); prior results are untouched. */
    FAILED,

    /** The caller cancelled; whatever was gathered before the cancel is preserved, nothing is invented. */
    CANCELLED,
}

/**
 * The legal moves of a discovery pass, modelled on the other three state machines (a forward map, the
 * unavoidable sinks, idempotent self-transitions).
 *
 * The invariants that carry prompt §14: a pass reaches [COMPLETE] or [PARTIALLY_COMPLETE] only *through*
 * [DISCOVERING] (nothing finishes without having run), [FAILED] and [CANCELLED] are reachable from any
 * in-flight state (a session drop cannot be hidden), and a finished pass does not silently restart — a
 * re-run is a new pass the owner begins, not a transition out of [COMPLETE].
 */
object DiscoveryStateTransitions {

    private val forward: Map<DiscoveryState, Set<DiscoveryState>> = mapOf(
        DiscoveryState.NOT_STARTED to setOf(DiscoveryState.INITIALIZING),
        DiscoveryState.INITIALIZING to setOf(DiscoveryState.DISCOVERING),
        DiscoveryState.DISCOVERING to setOf(
            DiscoveryState.COMPLETE,
            DiscoveryState.PARTIALLY_COMPLETE,
        ),
        // A terminal pass has no outgoing edge; re-running is a new pass, not a revival (§14).
        DiscoveryState.COMPLETE to setOf(),
        DiscoveryState.PARTIALLY_COMPLETE to setOf(),
        DiscoveryState.FAILED to setOf(),
        DiscoveryState.CANCELLED to setOf(),
    )

    /** Where a pass may go next: its successors, plus the universal failure/cancel sinks, plus staying put. */
    fun allowedNext(from: DiscoveryState): Set<DiscoveryState> =
        forward[from].orEmpty() +
            setOf(DiscoveryState.FAILED, DiscoveryState.CANCELLED) +
            from

    fun canTransition(from: DiscoveryState, to: DiscoveryState): Boolean = to in allowedNext(from)

    /** True once a pass has stopped moving; a re-run is a fresh pass, not a transition out of terminal. */
    fun isTerminal(state: DiscoveryState): Boolean =
        state == DiscoveryState.COMPLETE ||
            state == DiscoveryState.PARTIALLY_COMPLETE ||
            state == DiscoveryState.FAILED ||
            state == DiscoveryState.CANCELLED

    /** True where the pass produced evidence a caller may merge into device state. */
    fun producedEvidence(state: DiscoveryState): Boolean =
        state == DiscoveryState.COMPLETE || state == DiscoveryState.PARTIALLY_COMPLETE
}
