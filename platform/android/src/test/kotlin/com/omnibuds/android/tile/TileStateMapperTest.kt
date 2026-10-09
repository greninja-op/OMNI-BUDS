package com.omnibuds.android.tile

import com.omnibuds.core.globalstate.BatteryState
import com.omnibuds.core.globalstate.CapabilityState
import com.omnibuds.core.globalstate.ConnectionState
import com.omnibuds.core.globalstate.FeatureState
import com.omnibuds.core.globalstate.Freshness
import com.omnibuds.core.globalstate.GlobalDeviceId
import com.omnibuds.core.globalstate.GlobalDeviceState
import com.omnibuds.core.globalstate.IdentityState
import com.omnibuds.core.globalstate.ObservationProvenance
import com.omnibuds.core.globalstate.ObservedValue
import com.omnibuds.core.globalstate.OperationStatus
import com.omnibuds.core.globalstate.ProtocolState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Phase 25: tile-state mapping tests.
 */
class TileStateMapperTest {

    private fun readyState(): GlobalDeviceState =
        GlobalDeviceState.empty(GlobalDeviceId("dev-1")).copy(
            identity = IdentityState.Identified("m-1", "model-1", "high", "3.1"),
            connection = ConnectionState.Connected("s-1", 1L, "rfcomm"),
            protocol = ProtocolState.Resolved("p-1", "1.0", true),
            capabilities = CapabilityState.Ready(setOf("anc")),
        )

    @Test
    fun `null state maps to no device`() {
        val tile = TileStateMapper.map(null)
        assertEquals(TileKind.NO_DEVICE, tile.kind)
        assertFalse(tile.active)
        assertFalse(tile.clickable)
    }

    @Test
    fun `disconnected maps honestly`() {
        val tile = TileStateMapper.map(GlobalDeviceState.empty(GlobalDeviceId("dev-1")))
        assertEquals(TileKind.DISCONNECTED, tile.kind)
        assertFalse(tile.clickable)
    }

    @Test
    fun `connected but unidentified`() {
        val state = GlobalDeviceState.empty(GlobalDeviceId("dev-1")).copy(
            connection = ConnectionState.Connected("s-1", 1L, "rfcomm"),
        )
        val tile = TileStateMapper.map(state)
        assertEquals(TileKind.UNIDENTIFIED, tile.kind)
    }

    @Test
    fun `discovering capabilities`() {
        val state = readyState().copy(capabilities = CapabilityState.Discovering("scanning"))
        val tile = TileStateMapper.map(state)
        assertEquals(TileKind.DISCOVERING, tile.kind)
    }

    @Test
    fun `ready without controllable feature`() {
        val tile = TileStateMapper.map(readyState(), emptySet())
        assertEquals(TileKind.READY_NO_ACTION, tile.kind)
        assertTrue(tile.active)
        assertFalse(tile.clickable)
    }

    @Test
    fun `ready with controllable feature`() {
        val tile = TileStateMapper.map(readyState(), setOf("anc"))
        assertEquals(TileKind.READY_WITH_ACTION, tile.kind)
        assertTrue(tile.clickable)
    }

    @Test
    fun `operation pending`() {
        val state = readyState().copy(
            features = FeatureState(
                executing = mapOf("anc" to OperationStatus.Pending(1000L)),
            ),
        )
        val tile = TileStateMapper.map(state, setOf("anc"))
        assertEquals(TileKind.OPERATION_PENDING, tile.kind)
        assertFalse(tile.clickable)
    }

    @Test
    fun `battery shown when known`() {
        val state = readyState().copy(
            battery = BatteryState.Known(75, false, null),
        )
        val tile = TileStateMapper.map(state, emptySet())
        assertTrue(tile.subtitle?.contains("75") == true)
    }

    @Test
    fun `missing battery never shown as zero`() {
        val tile = TileStateMapper.map(readyState(), emptySet())
        // Subtitle is "Connected", not "Battery 0%".
        assertFalse(tile.subtitle?.contains("0%") == true)
    }

    @Test
    fun `failure surfaces honestly`() {
        val tile = TileStateMapper.map(readyState(), setOf("anc"), lastFailure = "timeout")
        assertEquals(TileKind.FAILED, tile.kind)
    }

    @Test
    fun `incompatible protocol unavailable`() {
        val state = readyState().copy(protocol = ProtocolState.Incompatible("nope"))
        val tile = TileStateMapper.map(state, setOf("anc"))
        assertEquals(TileKind.UNAVAILABLE, tile.kind)
        assertFalse(tile.clickable)
    }
}
