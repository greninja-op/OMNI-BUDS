package com.omnibuds.core.protocol

/**
 * Where a protocol session has got to, as a lifecycle distinct from the device link and the channel.
 *
 * There are already two lifecycle axes in this project and neither is this one:
 * [com.omnibuds.core.state.ConnectionState] describes the *device link* (Phase 3/4), and
 * [com.omnibuds.core.transport.TransportState] describes a single *channel* (Phase 6). A protocol needs a
 * third because a channel can be `CONNECTED` while its protocol has not been spoken to, initialised, or
 * understood — the boundary prompt §5 insists on ("transport availability does not imply protocol
 * readiness"). Conflating protocol state with either other axis is the same category error ADR-P3-002
 * refuses between a profile and a transport, so this enum has no member borrowed from those.
 *
 * [READY] is reachable only from [INITIALIZING], and only on the initialize contract's own success (ADR-P5
 * … ADR-P7-005; prompt §12's "do not mark a protocol ready before its initialization contract succeeds").
 * A protocol that has not resolved has no session at all — [UNRESOLVED] is the state of an attempt, not of
 * an absent device, and the ordinary state of the shipped empty registry is that resolution never reaches
 * here.
 */
enum class ProtocolState {
    /** No protocol has been chosen for this device yet; nothing has been resolved or created. */
    UNRESOLVED,

    /** A protocol candidate was resolved from evidence; no session object exists yet. */
    RESOLVED,

    /** A session instance was created against a transport; not yet initialized. */
    CREATED,

    /** `initialize()` is in flight; the protocol has not yet proved itself on the channel. */
    INITIALIZING,

    /** Initialization succeeded; operations may be issued. Reached only on the init contract's success. */
    READY,

    /** Usable for some operations but degraded (a transport dropped, a feature unavailable); reads allowed, control gated. */
    DEGRADED,

    /** A fatal protocol or transport fault; no operation is accepted until the owner recreates the session. */
    FAILED,

    /** `close()` is in flight; releasing the session's resources. */
    CLOSING,

    /** The session is closed and accepts nothing. Terminal; not resurrected by a late callback. */
    CLOSED,
}

/**
 * The legal moves between [ProtocolState]s, in one place, mirroring
 * [com.omnibuds.core.state.ConnectionStateTransitions] and
 * [com.omnibuds.core.transport.TransportStateTransitions].
 *
 * Three rules, each mapping to a prompt requirement:
 *  - **No shortcut to `READY`.** It is reachable only from `INITIALIZING`; there is no `CREATED → READY` or
 *    `RESOLVED → READY` edge, so a session cannot claim readiness before its initialize contract succeeds
 *    (prompt §12).
 *  - **Failure and closure from anywhere live.** `FAILED`, `DEGRADED` and `CLOSING` are reachable from every
 *    operational state, so a transport loss cannot be hidden by a missing edge; a `CLOSED` session is
 *    terminal and a stale callback cannot reopen it (prompt §12/§14).
 *  - **Staying put is legal.** A repeated `close()` or `initialize()` on a session already in that state is
 *    idempotent, not an illegal move (prompt §12's "repeated operation" behaviour).
 */
object ProtocolStateTransitions {

    private val forward: Map<ProtocolState, Set<ProtocolState>> = mapOf(
        ProtocolState.UNRESOLVED to setOf(ProtocolState.RESOLVED),
        ProtocolState.RESOLVED to setOf(ProtocolState.CREATED, ProtocolState.UNRESOLVED),
        ProtocolState.CREATED to setOf(ProtocolState.INITIALIZING, ProtocolState.CLOSING),
        ProtocolState.INITIALIZING to setOf(ProtocolState.READY, ProtocolState.DEGRADED, ProtocolState.CLOSING),
        ProtocolState.READY to setOf(ProtocolState.DEGRADED, ProtocolState.CLOSING),
        // A degraded session recovers by re-running the init contract, never by claiming READY directly,
        // so READY stays reachable only from INITIALIZING across the whole machine (ADR-P7-005).
        ProtocolState.DEGRADED to setOf(ProtocolState.INITIALIZING, ProtocolState.CLOSING),
        ProtocolState.FAILED to setOf(ProtocolState.CLOSING),
        ProtocolState.CLOSING to setOf(ProtocolState.CLOSED),
        ProtocolState.CLOSED to setOf(),
    )

    /** Where a session may go next: listed successors, plus the universal sinks, plus staying put. */
    fun allowedNext(from: ProtocolState): Set<ProtocolState> =
        forward[from].orEmpty() + ProtocolState.FAILED + from

    fun canTransition(from: ProtocolState, to: ProtocolState): Boolean = to in allowedNext(from)

    /** True only for the states in which a control operation may be issued. */
    fun canExecute(state: ProtocolState): Boolean = state == ProtocolState.READY

    /** True where the session reads as alive enough to observe state but not necessarily to control. */
    fun canReadState(state: ProtocolState): Boolean =
        state == ProtocolState.READY || state == ProtocolState.DEGRADED

    /** True for the states from which no operation and no revival is accepted. */
    fun isTerminal(state: ProtocolState): Boolean = state == ProtocolState.CLOSED
}
