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
import com.omnibuds.core.globalstate.PersistenceState
import com.omnibuds.core.globalstate.ProtocolState
import com.omnibuds.core.globalstate.VendorFeatureState
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WidgetActionDispatcherTest {

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
                observed = mapOf(
                    "anc" to ObservedValue(
                        observed,
                        ObservationProvenance("test", null, null, null, null, null),
                        if (usable) Freshness.CURRENT else Freshness.STALE,
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
        ),
    )

    private fun request(
        widgetId: Int = 7,
        nonce: String = "n1",
        deviceId: String = "device-1",
        sessionId: String? = "session-1",
        featureId: String = "anc",
    ) = WidgetActionRequest(widgetId, deviceId, sessionId, featureId, nonce)

    private fun dispatcher(
        allowed: Boolean = true,
        executed: MutableList<ValidatedWidgetAction> = mutableListOf(),
        bindings: Map<Int, GlobalDeviceId> = mapOf(7 to deviceId),
    ) = WidgetActionDispatcher(
        accessCheck = { _, _ -> allowed },
        writeExecutor = { action -> executed.add(action); true },
        bindingOf = { bindings[it] },
    )

    @Test
    fun `valid action dispatches with next mode`() = runTest {
        val executed = mutableListOf<ValidatedWidgetAction>()
        val outcome = dispatcher(executed = executed).dispatch(request(), state(), modes)
        assertTrue(outcome is WidgetActionOutcome.Dispatched)
        val dispatched = (outcome as WidgetActionOutcome.Dispatched).action
        assertEquals("on", dispatched.nextValue)
        assertEquals("off", dispatched.basedOn)
        assertEquals(7, dispatched.widgetId)
        assertEquals(1, executed.size)
    }

    @Test
    fun `mode cycles through the real mode set`() = runTest {
        val outcome = dispatcher().dispatch(request(), state(observed = "transparency"), modes)
        val dispatched = (outcome as WidgetActionOutcome.Dispatched).action
        assertEquals("off", dispatched.nextValue)
    }

    @Test
    fun `blank fields are malformed`() = runTest {
        val outcome = dispatcher().dispatch(
            WidgetActionRequest(7, "", null, "", ""),
            state(), modes,
        )
        assertTrue((outcome as WidgetActionOutcome.Rejected).reason is WidgetActionRejection.Malformed)
    }

    @Test
    fun `unknown widget instance is rejected`() = runTest {
        val outcome = dispatcher(bindings = emptyMap()).dispatch(request(), state(), modes)
        assertTrue((outcome as WidgetActionOutcome.Rejected).reason is WidgetActionRejection.InvalidWidgetId)
    }

    @Test
    fun `target mismatch is rejected without redirection`() = runTest {
        val outcome = dispatcher(
            bindings = mapOf(7 to GlobalDeviceId("device-2")),
        ).dispatch(request(), state(), modes)
        val reason = (outcome as WidgetActionOutcome.Rejected).reason
        assertTrue(reason is WidgetActionRejection.TargetMismatch)
    }

    @Test
    fun `replayed nonce is rejected`() = runTest {
        val d = dispatcher()
        d.dispatch(request(nonce = "dup"), state(), modes)
        val outcome = d.dispatch(request(nonce = "dup"), state(), modes)
        assertTrue((outcome as WidgetActionOutcome.Rejected).reason is WidgetActionRejection.Duplicate)
    }

    @Test
    fun `stale session is rejected`() = runTest {
        val outcome = dispatcher().dispatch(
            request(sessionId = "old-session"),
            state(sessionId = "new-session"),
            modes,
        )
        assertTrue((outcome as WidgetActionOutcome.Rejected).reason is WidgetActionRejection.StaleSession)
    }

    @Test
    fun `unsupported feature is rejected`() = runTest {
        val outcome = dispatcher().dispatch(request(featureId = "nope"), state(), modes)
        assertTrue((outcome as WidgetActionOutcome.Rejected).reason is WidgetActionRejection.Unsupported)
    }

    @Test
    fun `stale feature state is rejected`() = runTest {
        val outcome = dispatcher().dispatch(request(), state(usable = false), modes)
        assertTrue((outcome as WidgetActionOutcome.Rejected).reason is WidgetActionRejection.StaleState)
    }

    @Test
    fun `access policy denial is rejected`() = runTest {
        val outcome = dispatcher(allowed = false).dispatch(request(), state(), modes)
        assertTrue((outcome as WidgetActionOutcome.Rejected).reason is WidgetActionRejection.AccessDenied)
    }

    @Test
    fun `duplicate in-flight operation is rejected`() = runTest {
        val d = dispatcher()
        d.dispatch(request(nonce = "a"), state(), modes)
        val outcome = d.dispatch(request(nonce = "b"), state(), modes)
        assertTrue((outcome as WidgetActionOutcome.Rejected).reason is WidgetActionRejection.Duplicate)
    }

    @Test
    fun `completion releases the in-flight guard`() = runTest {
        val d = dispatcher()
        d.dispatch(request(nonce = "a"), state(), modes)
        d.complete(7, "anc")
        val outcome = d.dispatch(request(nonce = "b"), state(), modes)
        assertTrue(outcome is WidgetActionOutcome.Dispatched)
    }

    @Test
    fun `executor refusal is rejected`() = runTest {
        val d = WidgetActionDispatcher(
            accessCheck = { _, _ -> true },
            writeExecutor = { _ -> false },
            bindingOf = { deviceId },
        )
        val outcome = d.dispatch(request(), state(), modes)
        assertTrue(outcome is WidgetActionOutcome.Rejected)
    }
}
