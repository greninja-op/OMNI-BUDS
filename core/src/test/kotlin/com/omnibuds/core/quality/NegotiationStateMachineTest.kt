package com.omnibuds.core.quality

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase 13 (OB-P13-REQ-003, §37): the negotiation state machine transitions.
 */
class NegotiationStateMachineTest {

    @Test
    fun `unknown can go idle or disconnected`() {
        assertTrue(NegotiationTransitions.isLegal(NegotiationState.UNKNOWN, NegotiationState.IDLE))
        assertTrue(NegotiationTransitions.isLegal(NegotiationState.UNKNOWN, NegotiationState.DISCONNECTED))
    }

    @Test
    fun `happy path idle to active`() {
        assertTrue(NegotiationTransitions.isLegal(NegotiationState.IDLE, NegotiationState.PREPARING))
        assertTrue(NegotiationTransitions.isLegal(NegotiationState.PREPARING, NegotiationState.NEGOTIATING))
        assertTrue(NegotiationTransitions.isLegal(NegotiationState.NEGOTIATING, NegotiationState.NEGOTIATED))
        assertTrue(NegotiationTransitions.isLegal(NegotiationState.NEGOTIATED, NegotiationState.ACTIVE))
    }

    @Test
    fun `negotiating can fail`() {
        assertTrue(NegotiationTransitions.isLegal(NegotiationState.NEGOTIATING, NegotiationState.FAILED))
        assertTrue(NegotiationTransitions.isLegal(NegotiationState.PREPARING, NegotiationState.FAILED))
        assertTrue(NegotiationTransitions.isLegal(NegotiationState.NEGOTIATED, NegotiationState.FAILED))
    }

    @Test
    fun `any active state can disconnect`() {
        for (from in listOf(
            NegotiationState.IDLE,
            NegotiationState.PREPARING,
            NegotiationState.NEGOTIATING,
            NegotiationState.NEGOTIATED,
            NegotiationState.ACTIVE,
            NegotiationState.FAILED,
            NegotiationState.STALE,
        )) {
            assertTrue(
                NegotiationTransitions.isLegal(from, NegotiationState.DISCONNECTED),
                "$from should be able to disconnect",
            )
        }
    }

    @Test
    fun `active can go stale`() {
        assertTrue(NegotiationTransitions.isLegal(NegotiationState.ACTIVE, NegotiationState.STALE))
    }

    @Test
    fun `disconnected can reconnect to idle`() {
        assertTrue(NegotiationTransitions.isLegal(NegotiationState.DISCONNECTED, NegotiationState.IDLE))
        assertTrue(NegotiationTransitions.isLegal(NegotiationState.DISCONNECTED, NegotiationState.UNKNOWN))
    }

    @Test
    fun `stale can recover`() {
        assertTrue(NegotiationTransitions.isLegal(NegotiationState.STALE, NegotiationState.PREPARING))
        assertTrue(NegotiationTransitions.isLegal(NegotiationState.STALE, NegotiationState.IDLE))
    }

    @Test
    fun `invalid transitions are rejected`() {
        assertFalse(NegotiationTransitions.isLegal(NegotiationState.UNKNOWN, NegotiationState.ACTIVE))
        assertFalse(NegotiationTransitions.isLegal(NegotiationState.IDLE, NegotiationState.NEGOTIATED))
        assertFalse(NegotiationTransitions.isLegal(NegotiationState.DISCONNECTED, NegotiationState.ACTIVE))
        assertFalse(NegotiationTransitions.isLegal(NegotiationState.FAILED, NegotiationState.ACTIVE))
        assertFalse(NegotiationTransitions.isLegal(NegotiationState.UNKNOWN, NegotiationState.NEGOTIATING))
    }

    @Test
    fun `self transition is legal`() {
        for (state in NegotiationState.entries) {
            assertTrue(NegotiationTransitions.isLegal(state, state))
        }
    }
}
