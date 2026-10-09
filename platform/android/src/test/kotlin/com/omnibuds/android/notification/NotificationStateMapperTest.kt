package com.omnibuds.android.notification

import com.omnibuds.core.globalstate.BatteryState
import com.omnibuds.core.globalstate.VendorFeatureState
import com.omnibuds.core.globalstate.PersistenceState
import com.omnibuds.core.globalstate.ConfigurationState
import com.omnibuds.core.globalstate.AudioState
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class NotificationStateMapperTest {

    private val deviceId = GlobalDeviceId("device-1")

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
        "anc" to ActionMetadata("Toggle ANC", listOf("off", "on")),
    )

    private fun observed(value: String, usable: Boolean = true) =
        ObservedValue(
            value,
            ObservationProvenance("test", null, null, null, null, null),
            if (usable) Freshness.CURRENT else Freshness.STALE,
        )

    @Test
    fun `null state yields hidden notification`() {
        val mapped = NotificationStateMapper.map(null)
        assertFalse(mapped.visible)
        assertEquals(NotificationKind.HIDDEN, mapped.kind)
        assertTrue(mapped.actions.isEmpty())
    }

    @Test
    fun `disconnected device yields hidden notification`() {
        val state = readyState().copy(
            connection = ConnectionState.Disconnected,
        )
        val mapped = NotificationStateMapper.map(state, actionMeta)
        assertFalse(mapped.visible)
        assertEquals(NotificationKind.HIDDEN, mapped.kind)
    }

    @Test
    fun `unidentified device yields status without actions`() {
        val state = readyState().copy(identity = IdentityState.Unknown)
        val mapped = NotificationStateMapper.map(state, actionMeta)
        assertTrue(mapped.visible)
        assertEquals(NotificationKind.STATUS, mapped.kind)
        assertTrue(mapped.actions.isEmpty())
    }

    @Test
    fun `discovering capabilities yields status`() {
        val state = readyState().copy(capabilities = CapabilityState.Discovering("test"))
        val mapped = NotificationStateMapper.map(state, actionMeta)
        assertTrue(mapped.visible)
        assertEquals(NotificationKind.STATUS, mapped.kind)
        assertTrue(mapped.actions.isEmpty())
    }

    @Test
    fun `ready device with actions yields controls`() {
        val state = readyState(observed = mapOf("anc" to observed("off")))
        val mapped = NotificationStateMapper.map(state, actionMeta)
        assertTrue(mapped.visible)
        assertEquals(NotificationKind.CONTROLS, mapped.kind)
        assertEquals(1, mapped.actions.size)
        assertEquals("anc", mapped.actions[0].featureId)
        assertEquals(deviceId.value, mapped.actions[0].deviceId)
        assertEquals("session-1", mapped.actions[0].sessionId)
    }

    @Test
    fun `ready device without verified actions yields status only`() {
        val state = readyState()
        val mapped = NotificationStateMapper.map(state)
        assertTrue(mapped.visible)
        assertEquals(NotificationKind.STATUS, mapped.kind)
        assertTrue(mapped.actions.isEmpty())
    }

    @Test
    fun `operation pending yields progress`() {
        val state = readyState(pending = mapOf("anc" to OperationStatus.Pending(null)))
        val mapped = NotificationStateMapper.map(state, actionMeta)
        assertEquals(NotificationKind.PROGRESS, mapped.kind)
        assertTrue(mapped.visible)
        assertTrue(mapped.actions.isEmpty())
    }

    @Test
    fun `failure yields failed kind`() {
        val state = readyState(observed = mapOf("anc" to observed("off")))
        val mapped = NotificationStateMapper.map(state, actionMeta, lastFailure = "timeout")
        assertEquals(NotificationKind.FAILED, mapped.kind)
        assertTrue(mapped.visible)
    }

    @Test
    fun `missing battery is never fabricated as zero`() {
        val state = readyState()
        val mapped = NotificationStateMapper.map(state)
        assertFalse(mapped.text.contains("0%"))
    }

    @Test
    fun `known battery is shown`() {
        val state = readyState(
            battery = BatteryState.Known(80, null, ObservedValue(Unit, ObservationProvenance("test", null, null, null, null, null), Freshness.CURRENT)),
        )
        val mapped = NotificationStateMapper.map(state)
        assertTrue(mapped.text.contains("80%"))
    }

    @Test
    fun `stale battery is not shown as current`() {
        val battery = BatteryState.Known(
            80,
            null,
            ObservedValue(
                Unit,
                ObservationProvenance("test", null, null, null, null, null),
                Freshness.STALE,
            ),
        )
        val state = readyState(battery = battery)
        val mapped = NotificationStateMapper.map(state)
        assertFalse(mapped.text.contains("80%"))
    }

    @Test
    fun `incompatible protocol yields status without actions`() {
        val state = readyState().copy(protocol = ProtocolState.Resolved("proto", "1.0", false))
        val mapped = NotificationStateMapper.map(state, actionMeta)
        assertEquals(NotificationKind.STATUS, mapped.kind)
        assertTrue(mapped.actions.isEmpty())
    }

    @Test
    fun `actions capped at small set`() {
        val meta = (1..10).associate { i ->
            "f$i" to ActionMetadata("Toggle f$i", listOf("off", "on"))
        }
        val caps = CapabilityState.Ready((1..10).map { "f$it" }.toSet())
        val observed = (1..10).associate { i -> "f$i" to observed("off") }
        val state = readyState(observed = observed).copy(capabilities = caps)
        val mapped = NotificationStateMapper.map(state, meta)
        assertTrue(mapped.actions.size <= NotificationStateMapper.MAX_ACTIONS)
    }
}
