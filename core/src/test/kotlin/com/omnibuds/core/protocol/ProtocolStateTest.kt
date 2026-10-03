package com.omnibuds.core.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The protocol lifecycle machine under prompt §12 and ADR-P7-005 — a third axis, kept distinct from
 * `ConnectionState` and `TransportState`. Tier T1: a pure function over enum states.
 */
class ProtocolStateTest {

    @Test
    fun readyIsReachableOnlyThroughInitializing() {
        assertTrue(ProtocolStateTransitions.canTransition(ProtocolState.INITIALIZING, ProtocolState.READY))
        // No shortcut: RESOLVED/CREATED cannot jump to READY without the init contract (prompt §12).
        assertFalse(ProtocolStateTransitions.canTransition(ProtocolState.CREATED, ProtocolState.READY))
        assertFalse(ProtocolStateTransitions.canTransition(ProtocolState.RESOLVED, ProtocolState.READY))
    }

    @Test
    fun onlyReadyMayExecuteAndOnlyReadyOrDegradedMayRead() {
        assertTrue(ProtocolStateTransitions.canExecute(ProtocolState.READY))
        assertFalse(ProtocolStateTransitions.canExecute(ProtocolState.DEGRADED))
        assertFalse(ProtocolStateTransitions.canExecute(ProtocolState.INITIALIZING))
        assertTrue(ProtocolStateTransitions.canReadState(ProtocolState.DEGRADED))
        assertFalse(ProtocolStateTransitions.canReadState(ProtocolState.INITIALIZING))
    }

    @Test
    fun failureAndCloseAreReachableFromEveryOperationalState() {
        val live = listOf(ProtocolState.CREATED, ProtocolState.INITIALIZING, ProtocolState.READY, ProtocolState.DEGRADED)
        for (state in live) {
            assertTrue(ProtocolStateTransitions.canTransition(state, ProtocolState.FAILED), "from $state")
            assertTrue(ProtocolStateTransitions.canTransition(state, ProtocolState.CLOSING), "from $state")
        }
    }

    @Test
    fun closedIsTerminalAndNotResurrectedByALateCallback() {
        assertFalse(ProtocolStateTransitions.canTransition(ProtocolState.CLOSED, ProtocolState.READY))
        assertFalse(ProtocolStateTransitions.canTransition(ProtocolState.CLOSED, ProtocolState.INITIALIZING))
        assertTrue(ProtocolStateTransitions.isTerminal(ProtocolState.CLOSED))
    }

    @Test
    fun stayingPutIsAlwaysLegal() {
        for (state in ProtocolState.entries) {
            assertTrue(ProtocolStateTransitions.canTransition(state, state), "self-transition of $state")
        }
    }

    @Test
    fun degradedRecoversOnlyByReinitializingNotByClaimingReady() {
        assertTrue(ProtocolStateTransitions.canTransition(ProtocolState.DEGRADED, ProtocolState.INITIALIZING))
        // The "READY only via INITIALIZING" invariant is global, so DEGRADED has no direct edge to READY.
        assertFalse(ProtocolStateTransitions.canTransition(ProtocolState.DEGRADED, ProtocolState.READY))
    }
}
