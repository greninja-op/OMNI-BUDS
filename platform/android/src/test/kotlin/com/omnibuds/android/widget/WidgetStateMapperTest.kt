package com.omnibuds.android.widget

import com.omnibuds.core.globalstate.AudioState
import com.omnibuds.core.globalstate.BatteryState
import com.omnibuds.core.globalstate.CapabilityState
import com.omnibuds.core.globalstate.ConfigurationState
import com.omnibuds.core.globalstate.ConnectionState
import com.omnibuds.core.globalstate.FeatureState
import com.omnibuds.core.globalstate.Freshness
import com.omnibuds.core.globalstate.GlobalDeviceId
import com.omnibuds.core.globalstate.GlobalDeviceState
import com.omnibuds.core.globalstate.IdentityState
import com.omnibuds.core.globalstate.ObservationProvenance
import com.omnibuds.core.globalstate.ObservedValue
import com.omnibuds.core.globalstate.OperationStatus
import com.omnibuds.core.globalstate.PersistenceState
import com.omnibuds.core.globalstate.ProtocolState
import com.omnibuds.core.globalstate.VendorFeatureState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WidgetStateMapperTest {

    private val deviceId = GlobalDeviceId("device-1")

    private fun prov() = ObservationProvenance("test", null, null, null, null, null)

    private fun readyState(
        observed: Map<String, ObservedValue<String>> = emptyMap(),
        battery: BatteryState = BatteryState.Unknown,
        pending: Map<String, OperationStatus> = emptyMap(),
    ): GlobalDeviceState = GlobalDeviceState(
        deviceId = deviceId,
        identity = IdentityState.Identified("vendor", "model", "high", null),
        protocol = ProtocolState.Resolved("proto", "1.0", true),
        capabilities = CapabilityState.Ready(setOf("anc")),
        connection = ConnectionState.Connected("session-1", 1L, "gatt"),
        features = FeatureState(observed = observed, executing = pending),
        battery = battery,
        audio = AudioState.Unknown,
        configuration = ConfigurationState.Empty,
        persistence = PersistenceState.NotVerified,
        vendorFeatures = VendorFeatureState.Empty,
        publishedAtMillis = null,
    )

    private val actionMeta = mapOf(
        "anc" to WidgetActionMetadata("Toggle ANC", listOf("off", "on")),
    )

    private fun observed(value: String, usable: Boolean = true) =
        ObservedValue(
            value,
            prov(),
            if (usable) Freshness.CURRENT else Freshness.STALE,
        )

    @Test
    fun `null state yields unavailable widget`() {
        val mapped = WidgetStateMapper.map(1, null)
        assertEquals(WidgetKind.UNAVAILABLE, mapped.kind)
        assertEquals("No device", mapped.statusText)
        assertTrue(mapped.actions.isEmpty())
        assertNull(mapped.battery)
    }

    @Test
    fun `disconnected device yields unavailable`() {
        val state = readyState().copy(connection = ConnectionState.Disconnected)
        val mapped = WidgetStateMapper.map(1, state, actionMeta)
        assertEquals(WidgetKind.UNAVAILABLE, mapped.kind)
        assertEquals("Disconnected", mapped.statusText)
    }

    @Test
    fun `unidentified device yields status without actions`() {
        val state = readyState().copy(identity = IdentityState.Unknown)
        val mapped = WidgetStateMapper.map(1, state, actionMeta)
        assertEquals(WidgetKind.STATUS, mapped.kind)
        assertTrue(mapped.actions.isEmpty())
    }

    @Test
    fun `discovering capabilities yields status`() {
        val state = readyState().copy(capabilities = CapabilityState.Discovering("test"))
        val mapped = WidgetStateMapper.map(1, state, actionMeta)
        assertEquals(WidgetKind.STATUS, mapped.kind)
        assertTrue(mapped.actions.isEmpty())
    }

    @Test
    fun `ready device with actions yields controls`() {
        val state = readyState(observed = mapOf("anc" to observed("off")))
        val mapped = WidgetStateMapper.map(1, state, actionMeta)
        assertEquals(WidgetKind.CONTROLS, mapped.kind)
        assertEquals(1, mapped.actions.size)
        assertEquals("anc", mapped.actions[0].featureId)
        assertTrue(mapped.actions[0].contentDescription.isNotBlank())
        assertEquals(deviceId.value, mapped.deviceId)
        assertEquals("session-1", mapped.sessionId)
    }

    @Test
    fun `ready device without verified actions yields status only`() {
        val state = readyState()
        val mapped = WidgetStateMapper.map(1, state)
        assertEquals(WidgetKind.STATUS, mapped.kind)
        assertTrue(mapped.actions.isEmpty())
    }

    @Test
    fun `operation pending yields progress`() {
        val state = readyState(pending = mapOf("anc" to OperationStatus.Pending(null)))
        val mapped = WidgetStateMapper.map(1, state, actionMeta)
        assertEquals(WidgetKind.PROGRESS, mapped.kind)
        assertTrue(mapped.actions.isEmpty())
    }

    @Test
    fun `failure yields failed kind`() {
        val state = readyState(observed = mapOf("anc" to observed("off")))
        val mapped = WidgetStateMapper.map(1, state, actionMeta, lastFailure = "timeout")
        assertEquals(WidgetKind.FAILED, mapped.kind)
    }

    @Test
    fun `missing battery is never fabricated as zero`() {
        val state = readyState()
        val mapped = WidgetStateMapper.map(1, state)
        assertNull(mapped.battery)
    }

    @Test
    fun `known battery is shown`() {
        val state = readyState(
            battery = BatteryState.Known(
                80, null,
                ObservedValue(Unit, prov(), Freshness.CURRENT),
            ),
        )
        val mapped = WidgetStateMapper.map(1, state)
        assertEquals(80, mapped.battery?.leftPercent)
    }

    @Test
    fun `stale battery is not shown as current`() {
        val state = readyState(
            battery = BatteryState.Known(
                80, null,
                ObservedValue(Unit, prov(), Freshness.STALE),
            ),
        )
        val mapped = WidgetStateMapper.map(1, state)
        assertNull(mapped.battery)
    }

    @Test
    fun `incompatible protocol yields status without actions`() {
        val state = readyState().copy(protocol = ProtocolState.Resolved("proto", "1.0", false))
        val mapped = WidgetStateMapper.map(1, state, actionMeta)
        assertEquals(WidgetKind.STATUS, mapped.kind)
        assertTrue(mapped.actions.isEmpty())
    }

    @Test
    fun `actions capped at small set`() {
        val meta = (1..5).associate { i ->
            "f$i" to WidgetActionMetadata("Toggle f$i", listOf("off", "on"))
        }
        val caps = CapabilityState.Ready((1..5).map { "f$it" }.toSet())
        val observed = (1..5).associate { i -> "f$i" to observed("off") }
        val state = readyState(observed = observed).copy(capabilities = caps)
        val mapped = WidgetStateMapper.map(1, state, meta)
        assertTrue(mapped.actions.size <= WidgetStateMapper.MAX_ACTIONS)
    }
}
