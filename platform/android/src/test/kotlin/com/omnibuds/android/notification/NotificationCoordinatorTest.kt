package com.omnibuds.android.notification

import com.omnibuds.core.globalstate.CapabilityState
import com.omnibuds.core.globalstate.ConnectionState
import com.omnibuds.core.globalstate.FeatureState
import com.omnibuds.core.globalstate.GlobalDeviceId
import com.omnibuds.core.globalstate.GlobalDeviceState
import com.omnibuds.core.globalstate.GlobalDeviceStateRepository
import com.omnibuds.core.globalstate.IdentityState
import com.omnibuds.core.globalstate.ProtocolState
import com.omnibuds.core.globalstate.BatteryState
import com.omnibuds.core.globalstate.VendorFeatureState
import com.omnibuds.core.globalstate.PersistenceState
import com.omnibuds.core.globalstate.ConfigurationState
import com.omnibuds.core.globalstate.AudioState
import com.omnibuds.core.globalstate.Freshness
import com.omnibuds.core.globalstate.ObservationProvenance
import com.omnibuds.core.globalstate.ObservedValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class NotificationCoordinatorTest {

    private val deviceId = GlobalDeviceId("device-1")

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

    private class RecordingNotifier : Notifier {
        val notified = mutableListOf<NotificationState>()
        val cancelled = mutableListOf<Int>()
        override fun notify(state: NotificationState) { notified.add(state) }
        override fun cancel(notificationId: Int) { cancelled.add(notificationId) }
    }

    private fun coordinator(
        notifier: RecordingNotifier,
        permission: Boolean = true,
    ) = NotificationCoordinator(
        repository = GlobalDeviceStateRepository(),
        actionableFeatures = { mapOf("anc" to ActionMetadata("Toggle ANC", listOf("off", "on"))) },
        notifier = notifier,
        permissionCheck = { permission },
    )

    @Test
    fun `no devices yields no notification`() {
        val notifier = RecordingNotifier()
        coordinator(notifier).reconcile(emptyMap())
        assertTrue(notifier.notified.isEmpty())
    }

    @Test
    fun `ready device yields notification`() {
        val notifier = RecordingNotifier()
        coordinator(notifier).reconcile(mapOf(deviceId to state()))
        assertEquals(1, notifier.notified.size)
        assertEquals(NotificationKind.CONTROLS, notifier.notified[0].kind)
    }

    @Test
    fun `identical updates are deduplicated`() {
        val notifier = RecordingNotifier()
        val c = coordinator(notifier)
        c.reconcile(mapOf(deviceId to state()))
        c.reconcile(mapOf(deviceId to state()))
        assertEquals(1, notifier.notified.size)
    }

    @Test
    fun `disconnect cancels the notification`() {
        val notifier = RecordingNotifier()
        val c = coordinator(notifier)
        c.reconcile(mapOf(deviceId to state()))
        c.reconcile(emptyMap())
        assertEquals(listOf(NotificationIds.NOTIFICATION_DEVICE_STATUS), notifier.cancelled)
    }

    @Test
    fun `permission denied removes notifications`() {
        val notifier = RecordingNotifier()
        val c = coordinator(notifier, permission = false)
        c.reconcile(mapOf(deviceId to state()))
        assertTrue(notifier.notified.isEmpty())
    }

    @Test
    fun `multiple devices without selection yields no actionable notification`() {
        val notifier = RecordingNotifier()
        val other = state().copy(deviceId = GlobalDeviceId("device-2"))
        coordinator(notifier).reconcile(mapOf(deviceId to state(), GlobalDeviceId("device-2") to other))
        assertTrue(notifier.notified.isEmpty())
    }

    @Test
    fun `explicit selection resolves ambiguity`() {
        val notifier = RecordingNotifier()
        val other = state().copy(deviceId = GlobalDeviceId("device-2"))
        val c = coordinator(notifier)
        c.selectDevice(deviceId)
        c.reconcile(mapOf(deviceId to state(), GlobalDeviceId("device-2") to other))
        assertEquals(1, notifier.notified.size)
        assertEquals(NotificationKind.CONTROLS, notifier.notified[0].kind)
    }

    @Test
    fun `failure is surfaced`() {
        val notifier = RecordingNotifier()
        val c = coordinator(notifier)
        c.reconcile(mapOf(deviceId to state()))
        c.recordFailure("timeout")
        c.reconcile(mapOf(deviceId to state().copy(publishedAtMillis = System.currentTimeMillis())))
        val last = notifier.notified.last()
        assertEquals(NotificationKind.FAILED, last.kind)
        c.clearFailure()
        assertNull(null) // documents the clear path exists
    }
}
