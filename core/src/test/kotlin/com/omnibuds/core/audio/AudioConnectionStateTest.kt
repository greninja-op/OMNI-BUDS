package com.omnibuds.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The connection-state vocabulary means what it says: CONNECTED is not
 * ACTIVE, ACTIVE is not PLAYING, and UNKNOWN is not DISCONNECTED.
 * (OB-P10-REQ-004)
 */
class AudioConnectionStateTest {

    @Test
    fun allSevenStatesExistAndAreDistinct() {
        val states = AudioConnectionState.entries
        assertEquals(7, states.size)
        assertEquals(states.size, states.toSet().size)
    }

    @Test
    fun connectedIsNotActive() {
        // The distinction the whole engine is built on: a transport can be
        // connected for hours while audio goes elsewhere.
        assertNotEquals(AudioConnectionState.CONNECTED, AudioConnectionState.ACTIVE)
    }

    @Test
    fun unknownIsNotDisconnected() {
        // Treating "not read yet" as "not connected" fabricates a negative
        // claim (ADR-P0-016).
        assertNotEquals(AudioConnectionState.UNKNOWN, AudioConnectionState.DISCONNECTED)
    }

    @Test
    fun suspendedIsDistinctFromDisconnected() {
        // Suspended is recoverable without reconnecting; disconnected is not.
        assertNotEquals(AudioConnectionState.SUSPENDED, AudioConnectionState.DISCONNECTED)
        assertNotEquals(AudioConnectionState.SUSPENDED, AudioConnectionState.CONNECTED)
    }

    @Test
    fun transitionalStatesExist() {
        assertTrue(AudioConnectionState.entries.contains(AudioConnectionState.CONNECTING))
        assertTrue(AudioConnectionState.entries.contains(AudioConnectionState.DISCONNECTING))
    }

    @Test
    fun directionStatesAreIndependentOfConnection() {
        // Direction and connection are orthogonal axes: every direction is
        // combinable with every connection state, and neither implies the other.
        assertEquals(4, AudioDirection.entries.size)
        assertFalse(AudioDirection.OUTPUT == AudioDirection.INPUT)
        assertTrue(AudioDirection.entries.contains(AudioDirection.UNKNOWN))
    }
}
