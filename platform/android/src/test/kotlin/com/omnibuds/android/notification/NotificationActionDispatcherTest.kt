package com.omnibuds.android.notification

import com.omnibuds.core.globalstate.CapabilityState
import com.omnibuds.core.globalstate.ConnectionState
import com.omnibuds.core.globalstate.FeatureState
import com.omnibuds.core.globalstate.GlobalDeviceId
import com.omnibuds.core.globalstate.GlobalDeviceState
import com.omnibuds.core.globalstate.Freshness
import com.omnibuds.core.globalstate.IdentityState
import com.omnibuds.core.globalstate.ObservationProvenance
import com.omnibuds.core.globalstate.ObservedValue
import com.omnibuds.core.globalstate.ProtocolState
import com.omnibuds.core.globalstate.AudioState
import com.omnibuds.core.globalstate.BatteryState
import com.omnibuds.core.globalstate.ConfigurationState
import com.omnibuds.core.globalstate.PersistenceState
import com.omnibuds.core.globalstate.VendorFeatureState
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class NotificationActionDispatcherTest {

    private val deviceId = GlobalDeviceId("device-1")
    private val modes = mapOf("anc" to listOf("off", "on", "transparency"))

    private fun state(
        sessionId: String = "session-1",
        observed: String = "off",
        usable: Boolean = true,
    ): Map<GlobalDeviceId, GlobalDeviceState> = mapOf(
        deviceId to GlobalDeviceState(
            deviceId = deviceId,
            identity = IdentityState.Identified("vendor", "model", "high", null),
            protocol = ProtocolState.Resolved("proto", "1.0", true),
            capabilities = CapabilityState.Ready(setOf("anc")),
            connection = ConnectionState.Connected(sessionId, 1L, "gatt"),
            features = FeatureState(
                observed = mapOf("anc" to ObservedValue(
                    observed,
                    ObservationProvenance("test", null, null, null, null, null),
                    if (usable) Freshness.CURRENT else Freshness.STALE,
                )),
                executing = emptyMap(),
            ),
            battery = BatteryState.Unknown,
            audio = AudioState.Unknown,
            configuration = ConfigurationState.Empty,
            persistence = PersistenceState.NotVerified,
            vendorFeatures = VendorFeatureState.Empty,
            publishedAtMillis = null,
        ),
    )

    private fun request(
        nonce: String = "n1",
        deviceId: String = "device-1",
        sessionId: String? = "session-1",
        featureId: String = "anc",
    ) = ActionRequest("toggle-anc", deviceId, sessionId, featureId, nonce)

    private fun dispatcher(
        allowed: Boolean = true,
        executed: MutableList<ValidatedAction> = mutableListOf(),
    ) = NotificationActionDispatcher(
        accessCheck = { _, _ -> allowed },
        writeExecutor = { action -> executed.add(action); true },
    )

    @Test
    fun `valid action dispatches with next mode`() = runTest {
        val executed = mutableListOf<ValidatedAction>()
        val outcome = dispatcher(executed = executed).dispatch(request(), state(), modes)
        assertTrue(outcome is ActionOutcome.Dispatched)
        val dispatched = (outcome as ActionOutcome.Dispatched).action
        assertEquals("on", dispatched.nextValue)
        assertEquals("off", dispatched.basedOn)
        assertEquals("session-1", dispatched.sessionId)
        assertEquals(1, executed.size)
    }

    @Test
    fun `mode cycles through the real mode set`() = runTest {
        val d = dispatcher()
        val outcome = d.dispatch(request(), state(observed = "transparency"), modes)
        val dispatched = (outcome as ActionOutcome.Dispatched).action
        assertEquals("off", dispatched.nextValue) // wraps around
    }

    @Test
    fun `blank fields are malformed`() = runTest {
        val outcome = dispatcher().dispatch(
            ActionRequest("", "", null, "", ""),
            state(), modes,
        )
        assertTrue(outcome is ActionOutcome.Rejected)
        assertTrue((outcome as ActionOutcome.Rejected).reason is ActionRejection.Malformed)
    }

    @Test
    fun `replayed nonce is rejected`() = runTest {
        val d = dispatcher()
        d.dispatch(request(nonce = "dup"), state(), modes)
        val outcome = d.dispatch(request(nonce = "dup"), state(), modes)
        assertTrue(outcome is ActionOutcome.Rejected)
        assertTrue((outcome as ActionOutcome.Rejected).reason is ActionRejection.Duplicate)
    }

    @Test
    fun `unknown device is rejected`() = runTest {
        val outcome = dispatcher().dispatch(request(deviceId = "ghost"), state(), modes)
        assertTrue((outcome as ActionOutcome.Rejected).reason is ActionRejection.UnknownDevice)
    }

    @Test
    fun `stale session is rejected`() = runTest {
        val outcome = dispatcher().dispatch(
            request(sessionId = "old-session"),
            state(sessionId = "new-session"),
            modes,
        )
        assertTrue((outcome as ActionOutcome.Rejected).reason is ActionRejection.StaleSession)
    }

    @Test
    fun `unsupported feature is rejected`() = runTest {
        val outcome = dispatcher().dispatch(request(featureId = "nope"), state(), modes)
        assertTrue((outcome as ActionOutcome.Rejected).reason is ActionRejection.Unsupported)
    }

    @Test
    fun `stale feature state is rejected`() = runTest {
        val outcome = dispatcher().dispatch(
            request(), state(usable = false), modes,
        )
        assertTrue((outcome as ActionOutcome.Rejected).reason is ActionRejection.StaleState)
    }

    @Test
    fun `observed mode outside the mode set is rejected`() = runTest {
        val outcome = dispatcher().dispatch(
            request(), state(observed = "weird"), modes,
        )
        assertTrue((outcome as ActionOutcome.Rejected).reason is ActionRejection.UnknownState)
    }

    @Test
    fun `access policy denial is rejected`() = runTest {
        val outcome = dispatcher(allowed = false).dispatch(request(), state(), modes)
        assertTrue((outcome as ActionOutcome.Rejected).reason is ActionRejection.AccessDenied)
    }

    @Test
    fun `duplicate in-flight operation is rejected`() = runTest {
        val d = dispatcher()
        d.dispatch(request(nonce = "a"), state(), modes)
        val outcome = d.dispatch(request(nonce = "b"), state(), modes)
        assertTrue((outcome as ActionOutcome.Rejected).reason is ActionRejection.Duplicate)
    }

    @Test
    fun `completion releases the in-flight guard`() = runTest {
        val d = dispatcher()
        d.dispatch(request(nonce = "a"), state(), modes)
        d.complete("anc")
        val outcome = d.dispatch(request(nonce = "b"), state(), modes)
        assertTrue(outcome is ActionOutcome.Dispatched)
    }

    @Test
    fun `executor refusal is rejected`() = runTest {
        val d = NotificationActionDispatcher(
            accessCheck = { _, _ -> true },
            writeExecutor = { _ -> false },
        )
        val outcome = d.dispatch(request(), state(), modes)
        assertTrue(outcome is ActionOutcome.Rejected)
    }
}
