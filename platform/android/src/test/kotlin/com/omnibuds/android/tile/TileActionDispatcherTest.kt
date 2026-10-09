package com.omnibuds.android.tile

import com.omnibuds.core.globalstate.ConnectionState
import com.omnibuds.core.globalstate.FeatureState
import com.omnibuds.core.globalstate.Freshness
import com.omnibuds.core.globalstate.GlobalDeviceId
import com.omnibuds.core.globalstate.GlobalDeviceState
import com.omnibuds.core.globalstate.IdentityState
import com.omnibuds.core.globalstate.ObservationProvenance
import com.omnibuds.core.globalstate.ObservedValue
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Phase 25: action-dispatcher tests.
 */
class TileActionDispatcherTest {

    private fun prov() = ObservationProvenance("feature", 1L, 1L, "s-1", 1L, null)

    private fun stateWithAnc(observed: String?, freshness: Freshness = Freshness.CURRENT): GlobalDeviceState {
        val observedMap = if (observed != null) {
            mapOf("anc" to ObservedValue(observed, prov(), freshness))
        } else {
            emptyMap()
        }
        return GlobalDeviceState.empty(GlobalDeviceId("dev-1")).copy(
            identity = IdentityState.Identified("m-1", "model-1", "high", "3.1"),
            connection = ConnectionState.Connected("s-1", 1L, "rfcomm"),
            features = FeatureState(observed = observedMap),
        )
    }

    private fun dispatcher(
        accessAllowed: Boolean = true,
        writeOutcome: WriteOutcome = WriteOutcome.Accepted,
        onWrite: ((TileAction) -> Unit)? = null,
    ) = TileActionDispatcher(
        accessCheck = { _, _ -> AccessCheckResult(accessAllowed, if (accessAllowed) "ok" else "denied") },
        writeExecutor = { action ->
            onWrite?.invoke(action)
            writeOutcome
        },
    )

    @Test
    fun `valid toggle dispatches next mode`() = runTest {
        var written: TileAction? = null
        val d = dispatcher(onWrite = { written = it })
        val outcome = d.prepareToggle(
            stateWithAnc("off"), "anc", listOf("off", "on"), writable = true,
        )
        assertTrue(outcome is TileDispatchOutcome.Dispatched)
        assertTrue(written?.nextValue == "on")
        assertTrue(written?.basedOn == "off")
        // Target bound at dispatch.
        assertTrue(written?.deviceId?.value == "dev-1")
        assertTrue(written?.sessionId == "s-1")
    }

    @Test
    fun `mode cycles correctly`() = runTest {
        var written: TileAction? = null
        val d = dispatcher(onWrite = { written = it })
        d.prepareToggle(stateWithAnc("transparency"), "anc", listOf("off", "transparency", "on"), true)
        assertTrue(written?.nextValue == "on")
    }

    @Test
    fun `unknown state refused`() = runTest {
        val d = dispatcher()
        val outcome = d.prepareToggle(stateWithAnc(null), "anc", listOf("off", "on"), true)
        assertTrue(outcome is TileDispatchOutcome.Refused)
        assertTrue((outcome as TileDispatchOutcome.Refused).reason is TileDispatchRefusal.UnknownState)
    }

    @Test
    fun `stale state refused`() = runTest {
        val d = dispatcher()
        val outcome = d.prepareToggle(
            stateWithAnc("off", Freshness.STALE), "anc", listOf("off", "on"), true,
        )
        assertTrue(outcome is TileDispatchOutcome.Refused)
        assertTrue((outcome as TileDispatchOutcome.Refused).reason is TileDispatchRefusal.StaleState)
    }

    @Test
    fun `read-only refused`() = runTest {
        val d = dispatcher()
        val outcome = d.prepareToggle(stateWithAnc("off"), "anc", listOf("off", "on"), false)
        assertTrue((outcome as TileDispatchOutcome.Refused).reason is TileDispatchRefusal.ReadOnly)
    }

    @Test
    fun `access denied refused`() = runTest {
        val d = dispatcher(accessAllowed = false)
        val outcome = d.prepareToggle(stateWithAnc("off"), "anc", listOf("off", "on"), true)
        assertTrue(outcome is TileDispatchOutcome.Refused)
        assertTrue((outcome as TileDispatchOutcome.Refused).reason is TileDispatchRefusal.AccessDenied)
    }

    @Test
    fun `disconnected refused`() = runTest {
        val d = dispatcher()
        val state = GlobalDeviceState.empty(GlobalDeviceId("dev-1"))
        val outcome = d.prepareToggle(state, "anc", listOf("off", "on"), true)
        assertTrue((outcome as TileDispatchOutcome.Refused).reason is TileDispatchRefusal.SessionInvalid)
    }

    @Test
    fun `duplicate click refused`() = runTest {
        val d = dispatcher()
        val state = stateWithAnc("off")
        val first = d.prepareToggle(state, "anc", listOf("off", "on"), true)
        assertTrue(first is TileDispatchOutcome.Dispatched)
        val second = d.prepareToggle(state, "anc", listOf("off", "on"), true)
        assertTrue((second as TileDispatchOutcome.Refused).reason is TileDispatchRefusal.DuplicateClick)
        d.complete("anc")
        val third = d.prepareToggle(state, "anc", listOf("off", "on"), true)
        assertTrue(third is TileDispatchOutcome.Dispatched)
    }

    @Test
    fun `observed mode outside mode set refused`() = runTest {
        val d = dispatcher()
        val outcome = d.prepareToggle(
            stateWithAnc("turbo"), "anc", listOf("off", "on"), true,
        )
        assertTrue((outcome as TileDispatchOutcome.Refused).reason is TileDispatchRefusal.UnknownState)
    }
}
