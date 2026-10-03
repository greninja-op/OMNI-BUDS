package com.omnibuds.core.capability

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The discovery pass has its own lifecycle, kept apart from the link, channel and protocol machines
 * (§14, ADR-P8-008).
 *
 * The invariants that make partial discovery honest: a pass reaches a completion state only *through*
 * [DiscoveryState.DISCOVERING], a terminal pass does not silently revive (a re-run is a fresh pass), and the
 * failure/cancel sinks are always reachable so a dropped session cannot be hidden. Tier T1.
 */
class DiscoveryStateTest {

    private val all = DiscoveryState.entries.toList()

    @Test
    fun completionIsReachableOnlyThroughDiscovering() {
        // Nothing finishes without having run (§14: "Do not mark discovery complete if mandatory discovery work
        // has not finished"). Only DISCOVERING may move to COMPLETE or PARTIALLY_COMPLETE.
        assertFalse(DiscoveryStateTransitions.canTransition(DiscoveryState.NOT_STARTED, DiscoveryState.COMPLETE))
        assertFalse(DiscoveryStateTransitions.canTransition(DiscoveryState.INITIALIZING, DiscoveryState.COMPLETE))
        assertFalse(DiscoveryStateTransitions.canTransition(DiscoveryState.NOT_STARTED, DiscoveryState.PARTIALLY_COMPLETE))
        assertTrue(DiscoveryStateTransitions.canTransition(DiscoveryState.DISCOVERING, DiscoveryState.COMPLETE))
        assertTrue(DiscoveryStateTransitions.canTransition(DiscoveryState.DISCOVERING, DiscoveryState.PARTIALLY_COMPLETE))
    }

    @Test
    fun theHappyPathWalksTheWholeLifecycle() {
        assertTrue(DiscoveryStateTransitions.canTransition(DiscoveryState.NOT_STARTED, DiscoveryState.INITIALIZING))
        assertTrue(DiscoveryStateTransitions.canTransition(DiscoveryState.INITIALIZING, DiscoveryState.DISCOVERING))
        assertTrue(DiscoveryStateTransitions.canTransition(DiscoveryState.DISCOVERING, DiscoveryState.COMPLETE))
    }

    @Test
    fun failureAndCancellationAreReachableFromEveryInFlightState() {
        val inFlight = listOf(DiscoveryState.NOT_STARTED, DiscoveryState.INITIALIZING, DiscoveryState.DISCOVERING)
        for (state in inFlight) {
            assertTrue(
                DiscoveryStateTransitions.canTransition(state, DiscoveryState.FAILED),
                "$state must be able to fail",
            )
            assertTrue(
                DiscoveryStateTransitions.canTransition(state, DiscoveryState.CANCELLED),
                "$state must be able to cancel",
            )
        }
    }

    @Test
    fun terminalStatesHaveNoOutgoingForwardEdgeAndDoNotRevive() {
        // A finished pass does not restart itself; the owner begins a new pass rather than transitioning out of
        // terminal (§14 snapshot replacement).
        val terminal = listOf(DiscoveryState.COMPLETE, DiscoveryState.PARTIALLY_COMPLETE, DiscoveryState.FAILED, DiscoveryState.CANCELLED)
        for (state in terminal) {
            assertTrue(DiscoveryStateTransitions.isTerminal(state), "$state is terminal")
            assertFalse(DiscoveryStateTransitions.canTransition(state, DiscoveryState.DISCOVERING), "$state must not revive")
            assertFalse(DiscoveryStateTransitions.canTransition(state, DiscoveryState.INITIALIZING))
        }
        // Terminal states stay put (idempotent) but otherwise do not move.
        assertTrue(DiscoveryStateTransitions.canTransition(DiscoveryState.COMPLETE, DiscoveryState.COMPLETE))
    }

    @Test
    fun onlyCompletedAndPartialPassesOweMergableEvidence() {
        assertTrue(DiscoveryStateTransitions.producedEvidence(DiscoveryState.COMPLETE))
        assertTrue(DiscoveryStateTransitions.producedEvidence(DiscoveryState.PARTIALLY_COMPLETE))
        assertFalse(DiscoveryStateTransitions.producedEvidence(DiscoveryState.CANCELLED))
        assertFalse(DiscoveryStateTransitions.producedEvidence(DiscoveryState.FAILED))
        assertFalse(DiscoveryStateTransitions.producedEvidence(DiscoveryState.NOT_STARTED))
    }

    @Test
    fun partialAndCompleteAndFailedRemainDistinctStates() {
        // The whole reason this machine exists: three different outcomes of "not everything resolved" stay
        // three different values a consumer can tell apart (prompt sections 10, 14).
        assertEquals(7, DiscoveryState.entries.size)
        assertNotEqualsSame(DiscoveryState.COMPLETE, DiscoveryState.PARTIALLY_COMPLETE)
        assertNotEqualsSame(DiscoveryState.PARTIALLY_COMPLETE, DiscoveryState.FAILED)
    }

    private fun assertNotEqualsSame(a: DiscoveryState, b: DiscoveryState) =
        assertTrue(a !== b, "$a and $b must be distinct states")
}
