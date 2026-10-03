package com.omnibuds.core.transport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The transport lifecycle machine under prompt §8 and ADR-P6-002: which moves are legal, which are
 * refused, and the one rule that most matters — a channel is never `CONNECTED` before the platform
 * confirms it, and never resurrected once closed.
 *
 * Tier T1: a pure function over enum states, no radio, no scheduler.
 */
class TransportStateTest {

    @Test
    fun aFreshChannelRestsIdleAndCannotExchange() {
        assertFalse(TransportStateTransitions.canExchange(TransportState.IDLE))
        assertTrue(TransportStateTransitions.canExchange(TransportState.CONNECTED))
    }

    @Test
    fun connectingToConnectedIsTheOnlyWayIn() {
        assertTrue(TransportStateTransitions.canTransition(TransportState.IDLE, TransportState.CONNECTING))
        assertTrue(TransportStateTransitions.canTransition(TransportState.CONNECTING, TransportState.CONNECTED))
        // IDLE -> CONNECTED is illegal: prompt §8's rule, there is no skip past the platform's confirmation.
        assertFalse(TransportStateTransitions.canTransition(TransportState.IDLE, TransportState.CONNECTED))
    }

    @Test
    fun everyLiveStateCanFallIntoFailureOrDrop() {
        val live = listOf(
            TransportState.IDLE,
            TransportState.CONNECTING,
            TransportState.CONNECTED,
            TransportState.CLOSING,
        )
        for (state in live) {
            assertTrue(TransportStateTransitions.canTransition(state, TransportState.FAILED), "from $state")
            assertTrue(TransportStateTransitions.canTransition(state, TransportState.DISCONNECTED), "from $state")
        }
    }

    @Test
    fun aClosedChannelDoesNotReopenItself() {
        // CLOSED is terminal for this object: only a fresh UNKNOWN (a new channel) resets it, never CONNECTING.
        assertFalse(TransportStateTransitions.canTransition(TransportState.CLOSED, TransportState.CONNECTING))
        assertTrue(TransportStateTransitions.canTransition(TransportState.CLOSED, TransportState.UNKNOWN))
        assertTrue(TransportStateTransitions.isTerminal(TransportState.CLOSED))
    }

    @Test
    fun stayingPutIsAlwaysLegal() {
        for (state in TransportState.entries) {
            assertTrue(
                TransportStateTransitions.canTransition(state, state),
                "a repeated $state must be idempotent, not illegal",
            )
        }
    }

    @Test
    fun unavailableIsTerminalButReobservable() {
        assertFalse(TransportStateTransitions.canExchange(TransportState.UNAVAILABLE))
        assertTrue(TransportStateTransitions.canTransition(TransportState.UNAVAILABLE, TransportState.IDLE))
    }
}
