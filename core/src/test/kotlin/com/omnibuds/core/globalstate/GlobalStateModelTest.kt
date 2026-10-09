package com.omnibuds.core.globalstate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phase 24: domain model tests.
 */
class GlobalStateModelTest {

    @Test
    fun `empty snapshot has explicit unknowns`() {
        val state = GlobalDeviceState.empty(GlobalDeviceId("dev-1"))
        assertTrue(state.identity is IdentityState.Unknown)
        assertTrue(state.connection is ConnectionState.Disconnected)
        assertTrue(state.protocol is ProtocolState.Unresolved)
        assertTrue(state.battery is BatteryState.Unknown)
        assertTrue(state.features.desired.isEmpty())
        assertTrue(state.features.observed.isEmpty())
        assertNull(state.publishedAtMillis)
    }

    @Test
    fun `snapshots are immutable data`() {
        val a = GlobalDeviceState.empty(GlobalDeviceId("dev-1"))
        val b = a.copy(connection = ConnectionState.Disconnected)
        assertEquals(a, b) // copy with same values is equal
        assertFalse(a === b) // but a different instance
    }

    @Test
    fun `observation usability`() {
        val prov = ObservationProvenance(
            sourceId = "battery-engine",
            observedAtMillis = 1000L,
            receivedAtMillis = 1001L,
            sessionId = "s-1",
            connectionGeneration = 1L,
            protocolVersion = null,
        )
        val current = ObservedValue("50", prov, Freshness.CURRENT)
        val stale = ObservedValue("50", prov, Freshness.STALE)
        assertTrue(current.isUsable)
        assertFalse(stale.isUsable)
    }

    @Test
    fun `device id rejects blank`() {
        try {
            GlobalDeviceId("")
            assertTrue(false, "should have thrown")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("blank"))
        }
    }

    @Test
    fun `partial state explicit`() {
        val state = GlobalDeviceState.empty(GlobalDeviceId("dev-1")).copy(
            battery = BatteryState.Known(
                levelPercent = 80,
                charging = null, // unknown charging state stays unknown
                observation = null,
            ),
        )
        val battery = state.battery as BatteryState.Known
        assertEquals(80, battery.levelPercent)
        assertNull(battery.charging)
        // Audio remains unknown — not defaulted.
        assertTrue(state.audio.route == null)
    }
}
