package com.omnibuds.core.transport

/**
 * Where one control channel has got to, stated as a lifecycle rather than a boolean.
 *
 * [BluetoothTransport] used to expose only `isOpen`, which could say "on" or "off" but nothing about the
 * in-between states the platform actually reports — a channel that is mid-connection, one that is being
 * torn down, one that failed. Prompt §8 asks for that lifecycle, and prompt §8's rule "do not report a
 * transport as connected before the underlying transport confirms it" is the whole reason [CONNECTED] is
 * its own state rather than a synonym for "a `connect()` was called": only [CONNECTING] means an attempt
 * is in flight, and only the platform's own success moves the channel into [CONNECTED].
 *
 * The machine mirrors [com.omnibuds.core.state.ConnectionStateTransitions] on purpose, but models a
 * *single channel*, not the device link: there is deliberately no `IDENTIFYING`/`READY`/`CONTROL_SESSION`
 * here, because those are the device session's phases (ADR-P4-003) and a channel owns none of them. A
 * device can be `DISCONNECTED` at the session level while a channel is `CLOSED` cleanly, and conflating
 * the two axes is the same category error ADR-P3-002 refuses between a profile and a transport.
 */
enum class TransportState {
    /** This platform cannot provide the channel at all: no adapter, no permission, no API. */
    UNAVAILABLE,

    /** The channel object exists and could be opened, but has not been. The resting state. */
    IDLE,

    /** An `open` has been requested and the platform has not yet confirmed or refused it. */
    CONNECTING,

    /** The platform confirmed the channel is attached. Reached only on its success, never on a guess. */
    CONNECTED,

    /** A `close` is in flight; the platform is releasing the handle. */
    CLOSING,

    /** The channel is closed and will not carry further operations. Terminal for this object. */
    CLOSED,

    /** The device stopped reporting the link, after having had it. Distinct from a clean [CLOSED]. */
    DISCONNECTED,

    /** The platform refused or dropped the channel with an error. Reachable from any live state. */
    FAILED,

    /** Nothing is known about this channel yet. Not "closed" (ADR-P0-016), and never reported as usable. */
    UNKNOWN,
}

/**
 * The legal moves between [TransportState]s, in one place, so an illegal resurrection is refused rather
 * than compiled.
 *
 * Three rules shape the table and each maps to a prompt requirement:
 *  - **A terminal state does not come back on its own.** [CLOSED] and [UNAVAILABLE] accept no incoming
 *    move except a fresh `open` from the owner; a stale platform callback cannot move a closed channel to
 *    [CONNECTED], which is prompt §14's "a closed transport must not accept new operations" expressed as a
 *    transition rather than a check at every call site.
 *  - **Failure is unavoidable and from anywhere.** Like [com.omnibuds.core.state.ConnectionStateTransitions]'s
 *    `ERROR`, [FAILED] and [DISCONNECTED] are reachable from every live state, so no code path can hide a
 *    drop by pretending there is no edge to it.
 *  - **Staying put is legal.** A repeated `close` on an already-closed channel is idempotent (prompt §8's
 *    "repeated operation behavior"), so a self-transition is allowed everywhere.
 */
object TransportStateTransitions {

    private val forward: Map<TransportState, Set<TransportState>> = mapOf(
        TransportState.UNKNOWN to setOf(
            TransportState.UNAVAILABLE,
            TransportState.IDLE,
            TransportState.CONNECTING,
            TransportState.CONNECTED,
        ),
        TransportState.UNAVAILABLE to setOf(
            TransportState.IDLE,
        ),
        TransportState.IDLE to setOf(
            TransportState.CONNECTING,
            TransportState.UNAVAILABLE,
            TransportState.CLOSED,
        ),
        TransportState.CONNECTING to setOf(
            TransportState.CONNECTED,
            TransportState.DISCONNECTED,
            TransportState.CLOSING,
        ),
        TransportState.CONNECTED to setOf(
            TransportState.DISCONNECTED,
            TransportState.CLOSING,
        ),
        TransportState.CLOSING to setOf(
            TransportState.CLOSED,
            TransportState.DISCONNECTED,
        ),
        TransportState.DISCONNECTED to setOf(
            TransportState.CONNECTING,
            TransportState.IDLE,
            TransportState.CLOSED,
            TransportState.UNKNOWN,
        ),
        TransportState.CLOSED to setOf(
            TransportState.UNKNOWN,
        ),
        TransportState.FAILED to setOf(
            TransportState.CONNECTING,
            TransportState.IDLE,
            TransportState.CLOSING,
            TransportState.CLOSED,
            TransportState.UNKNOWN,
        ),
    )

    /** Where a channel may go next: its listed successors, plus failure, plus staying put. */
    fun allowedNext(from: TransportState): Set<TransportState> =
        forward[from].orEmpty() + TransportState.FAILED + TransportState.DISCONNECTED + from

    fun canTransition(from: TransportState, to: TransportState): Boolean = to in allowedNext(from)

    /**
     * True only for the one state that may carry an operation.
     *
     * Named for what it licenses rather than reusing [isTerminal]'s inverse: a channel that is
     * [CONNECTING] is not terminal and still cannot be written to, and reading "not terminal" as
     * "ready" is exactly the premature-connection claim prompt §8 forbids.
     */
    fun canExchange(state: TransportState): Boolean = state == TransportState.CONNECTED

    /** True for the states from which no operation and no revival is accepted. */
    fun isTerminal(state: TransportState): Boolean =
        state == TransportState.CLOSED || state == TransportState.UNAVAILABLE
}
