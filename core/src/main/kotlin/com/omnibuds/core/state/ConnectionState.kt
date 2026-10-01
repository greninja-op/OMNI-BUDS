package com.omnibuds.core.state

/**
 * Where a device has got to, from the platform's point of view.
 *
 * This models the Android flow of master section 4: the system Bluetooth stack owns
 * pairing and connecting, and OmniBuds attaches to a device that is already there.
 * [IDENTIFYING] and [CAPABILITY_DISCOVERY] are OmniBuds' own work, which is why they
 * sit after [CONNECTED] rather than before it.
 *
 * [UNKNOWN] is not "disconnected": it means the adapter or device state has not been
 * read, and must not be rendered as an absence (ADR-P0-016).
 */
enum class ConnectionState {
    UNKNOWN,
    DISCOVERED,
    PAIRED,
    CONNECTED,
    IDENTIFYING,
    CAPABILITY_DISCOVERY,
    READY,
    CONTROL_SESSION,
    DISCONNECTED,
    TEMPORARILY_UNAVAILABLE,
    ERROR,
}

/**
 * The legal moves between connection states.
 *
 * Enforced so that a stale callback cannot move a disconnected device straight into
 * CONTROL_SESSION (Phase 1 prompt section 30). Every state may fall into [ConnectionState.ERROR],
 * and staying in the same state is idempotent rather than illegal.
 */
object ConnectionStateTransitions {

    private val forward: Map<ConnectionState, Set<ConnectionState>> = mapOf(
        ConnectionState.UNKNOWN to setOf(
            ConnectionState.DISCOVERED,
            ConnectionState.PAIRED,
            ConnectionState.CONNECTED,
            ConnectionState.DISCONNECTED,
            ConnectionState.TEMPORARILY_UNAVAILABLE,
        ),
        ConnectionState.DISCOVERED to setOf(
            ConnectionState.PAIRED,
            ConnectionState.CONNECTED,
            ConnectionState.DISCONNECTED,
        ),
        ConnectionState.PAIRED to setOf(
            ConnectionState.CONNECTED,
            ConnectionState.DISCONNECTED,
        ),
        ConnectionState.CONNECTED to setOf(
            ConnectionState.IDENTIFYING,
            ConnectionState.READY,
            ConnectionState.DISCONNECTED,
            ConnectionState.TEMPORARILY_UNAVAILABLE,
        ),
        ConnectionState.IDENTIFYING to setOf(
            ConnectionState.CAPABILITY_DISCOVERY,
            ConnectionState.READY,
            ConnectionState.DISCONNECTED,
            ConnectionState.TEMPORARILY_UNAVAILABLE,
        ),
        ConnectionState.CAPABILITY_DISCOVERY to setOf(
            ConnectionState.READY,
            ConnectionState.DISCONNECTED,
            ConnectionState.TEMPORARILY_UNAVAILABLE,
        ),
        ConnectionState.READY to setOf(
            ConnectionState.CONTROL_SESSION,
            ConnectionState.DISCONNECTED,
            ConnectionState.TEMPORARILY_UNAVAILABLE,
        ),
        ConnectionState.CONTROL_SESSION to setOf(
            ConnectionState.READY,
            ConnectionState.DISCONNECTED,
            ConnectionState.TEMPORARILY_UNAVAILABLE,
        ),
        ConnectionState.DISCONNECTED to setOf(
            ConnectionState.DISCOVERED,
            ConnectionState.PAIRED,
            ConnectionState.CONNECTED,
            ConnectionState.UNKNOWN,
        ),
        ConnectionState.TEMPORARILY_UNAVAILABLE to setOf(
            ConnectionState.CONNECTED,
            ConnectionState.DISCONNECTED,
            ConnectionState.UNKNOWN,
        ),
        ConnectionState.ERROR to setOf(
            ConnectionState.UNKNOWN,
            ConnectionState.DISCONNECTED,
            ConnectionState.DISCOVERED,
            ConnectionState.CONNECTED,
        ),
    )

    fun allowedNext(from: ConnectionState): Set<ConnectionState> =
        forward[from].orEmpty() + ConnectionState.ERROR + from

    fun canTransition(from: ConnectionState, to: ConnectionState): Boolean =
        to in allowedNext(from)

    /** True while a control session is genuinely usable. */
    fun isOperational(state: ConnectionState): Boolean =
        state == ConnectionState.READY || state == ConnectionState.CONTROL_SESSION
}
