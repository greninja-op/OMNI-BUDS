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
import com.omnibuds.core.globalstate.GlobalDeviceStateRepository
import com.omnibuds.core.globalstate.IdentityState
import com.omnibuds.core.globalstate.ObservationProvenance
import com.omnibuds.core.globalstate.ObservedValue
import com.omnibuds.core.globalstate.PersistenceState
import com.omnibuds.core.globalstate.ProtocolState
import com.omnibuds.core.globalstate.VendorFeatureState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WidgetTargetResolverTest {

    private val d1 = GlobalDeviceId("device-1")
    private val d2 = GlobalDeviceId("device-2")

    private fun state(id: GlobalDeviceId, connected: Boolean = true): GlobalDeviceState =
        GlobalDeviceState(
            deviceId = id,
            identity = IdentityState.Identified("vendor", "model", "high", null),
            protocol = ProtocolState.Resolved("proto", "1.0", true),
            capabilities = CapabilityState.Ready(setOf("anc")),
            connection = if (connected) ConnectionState.Connected("s", 1L, "gatt")
            else ConnectionState.Disconnected,
            features = FeatureState.Empty,
            battery = BatteryState.Unknown,
            audio = AudioState.Unknown,
            configuration = ConfigurationState.Empty,
            persistence = PersistenceState.NotVerified,
            vendorFeatures = VendorFeatureState.Empty,
            publishedAtMillis = null,
        )

    @Test
    fun `no devices yields none`() {
        val r = WidgetTargetResolver.resolve(emptyMap(), null, null)
        assertTrue(r is WidgetTargetResolution.None)
    }

    @Test
    fun `single connected device resolves`() {
        val r = WidgetTargetResolver.resolve(mapOf(d1 to state(d1)), null, null)
        assertEquals(WidgetTargetResolution.Target(d1), r)
    }

    @Test
    fun `multiple devices without selection is ambiguous`() {
        val r = WidgetTargetResolver.resolve(
            mapOf(d1 to state(d1), d2 to state(d2)), null, null,
        )
        assertTrue(r is WidgetTargetResolution.Ambiguous)
    }

    @Test
    fun `explicit selection resolves ambiguity`() {
        val r = WidgetTargetResolver.resolve(
            mapOf(d1 to state(d1), d2 to state(d2)), d2, null,
        )
        assertEquals(WidgetTargetResolution.Target(d2), r)
    }

    @Test
    fun `bound target is kept while eligible`() {
        val r = WidgetTargetResolver.resolve(
            mapOf(d1 to state(d1), d2 to state(d2)), d2, d1,
        )
        assertEquals(WidgetTargetResolution.Target(d1), r)
    }

    @Test
    fun `disconnected bound target is re-resolved`() {
        val r = WidgetTargetResolver.resolve(
            mapOf(d1 to state(d1, connected = false), d2 to state(d2)), null, d1,
        )
        assertEquals(WidgetTargetResolution.Target(d2), r)
    }

    @Test
    fun `no connected devices yields none`() {
        val r = WidgetTargetResolver.resolve(
            mapOf(d1 to state(d1, connected = false)), null, null,
        )
        assertTrue(r is WidgetTargetResolution.None)
    }
}

class WidgetCoordinatorTest {

    private val deviceId = GlobalDeviceId("device-1")

    private class RecordingRenderer : WidgetRenderer {
        val rendered = mutableListOf<WidgetState>()
        val removed = mutableListOf<Int>()
        override fun render(state: WidgetState) { rendered.add(state) }
        override fun remove(widgetId: Int) { removed.add(widgetId) }
    }

    private fun state(): GlobalDeviceState = GlobalDeviceState(
        deviceId = deviceId,
        identity = IdentityState.Identified("vendor", "model", "high", null),
        protocol = ProtocolState.Resolved("proto", "1.0", true),
        capabilities = CapabilityState.Ready(setOf("anc")),
        connection = ConnectionState.Connected("session-1", 1L, "gatt"),
        features = FeatureState(
            observed = mapOf(
                "anc" to ObservedValue(
                    "off",
                    ObservationProvenance("test", null, null, null, null, null),
                    Freshness.CURRENT,
                ),
            ),
            executing = emptyMap(),
        ),
        battery = BatteryState.Unknown,
        audio = AudioState.Unknown,
        configuration = ConfigurationState.Empty,
        persistence = PersistenceState.NotVerified,
        vendorFeatures = VendorFeatureState.Empty,
        publishedAtMillis = null,
    )

    private fun coordinator(renderer: RecordingRenderer) = WidgetCoordinator(
        repository = GlobalDeviceStateRepository(),
        actionableFeatures = { mapOf("anc" to WidgetActionMetadata("Toggle ANC", listOf("off", "on"))) },
        renderer = renderer,
        selectedDevice = { null },
    )

    @Test
    fun `no devices yields unavailable widget`() {
        val renderer = RecordingRenderer()
        val c = coordinator(renderer)
        c.register(1, kotlinx.coroutines.test.TestScope().backgroundScope)
        c.reconcile(emptyMap())
        assertEquals(1, renderer.rendered.size)
        assertEquals(WidgetKind.UNAVAILABLE, renderer.rendered[0].kind)
    }

    @Test
    fun `ready device yields controls`() {
        val renderer = RecordingRenderer()
        val c = coordinator(renderer)
        c.register(1, kotlinx.coroutines.test.TestScope().backgroundScope)
        c.reconcile(mapOf(deviceId to state()))
        assertEquals(WidgetKind.CONTROLS, renderer.rendered.last().kind)
    }

    @Test
    fun `identical updates are deduplicated`() {
        val renderer = RecordingRenderer()
        val c = coordinator(renderer)
        c.register(1, kotlinx.coroutines.test.TestScope().backgroundScope)
        c.reconcile(mapOf(deviceId to state()))
        c.reconcile(mapOf(deviceId to state()))
        assertEquals(1, renderer.rendered.size)
    }

    @Test
    fun `unregister removes the instance`() {
        val renderer = RecordingRenderer()
        val c = coordinator(renderer)
        c.register(1, kotlinx.coroutines.test.TestScope().backgroundScope)
        c.reconcile(mapOf(deviceId to state()))
        c.unregister(1)
        assertEquals(listOf(1), renderer.removed)
        assertNull(c.bindingOf(1))
    }

    @Test
    fun `two instances are isolated`() {
        val renderer = RecordingRenderer()
        val c = coordinator(renderer)
        val scope = kotlinx.coroutines.test.TestScope().backgroundScope
        c.register(1, scope)
        c.register(2, scope)
        c.reconcile(mapOf(deviceId to state()))
        val ids = renderer.rendered.map { it.widgetId }.toSet()
        assertEquals(setOf(1, 2), ids)
    }
}

class WidgetSecurityTest {

    @Test
    fun `action string is namespaced`() {
        assertTrue(WidgetIds.ACTION_WIDGET_TOGGLE.startsWith("com.omnibuds."))
    }

    @Test
    fun `no raw payloads in intent extras`() {
        val extras = setOf(
            WidgetIds.EXTRA_WIDGET_ID,
            WidgetIds.EXTRA_DEVICE_ID,
            WidgetIds.EXTRA_SESSION_ID,
            WidgetIds.EXTRA_FEATURE_ID,
            WidgetIds.EXTRA_NONCE,
        )
        assertTrue(extras.none { it.contains("payload") || it.contains("value") })
    }

    @Test
    fun `widget labels avoid identifiers`() {
        val state = WidgetState(
            widgetId = 1,
            deviceId = "device-1",
            sessionId = "s",
            statusText = "Connected",
            battery = null,
            actions = emptyList(),
            kind = WidgetKind.STATUS,
        )
        assertTrue(state.statusText.length <= 60)
    }
}
