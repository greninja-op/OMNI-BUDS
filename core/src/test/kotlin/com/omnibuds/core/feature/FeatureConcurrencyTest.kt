package com.omnibuds.core.feature

import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.config.ConfigurationValue
import com.omnibuds.core.state.CapabilityState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Concurrency, ordering, cancellation and disconnect behaviour of the engine.
 *
 * The rules under test: one operation per feature at a time (a second write
 * waits, then validates against fresh state), different features proceed
 * concurrently, a disconnect mid-operation poisons the attempt without
 * corrupting state, and cancellation never leaves a feature stranded in
 * [FeatureState.Pending].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeatureConcurrencyTest {

    private val anc = StandardFeatures.ANC.feature
    private val transparency = StandardFeatures.TRANSPARENCY.feature
    private val on = ConfigurationValue.BooleanValue(true)
    private val off = ConfigurationValue.BooleanValue(false)

    private fun engine(port: ScriptedFeaturePort) = FeatureEngine(
        definitions = StandardFeatures.asMap,
        port = port,
    )

    private fun snapshot() = FeatureTestFixtures.snapshot(
        FeatureTestFixtures.record(anc, CapabilityState.SUPPORTED_VOLATILE),
        FeatureTestFixtures.record(transparency, CapabilityState.SUPPORTED_VOLATILE),
    )

    @Test
    fun twoWritesToTheSameFeatureSerializeAndTheSecondSeesFreshState() = runTest {
        val port = ScriptedFeaturePort()
        val order = mutableListOf<String>()
        port.writeHandler = { _, value ->
            order += "write-$value-start"
            delay(50)
            order += "write-$value-end"
            port.deviceValues[anc] = value
            OperationOutcome.Success(Unit)
        }
        port.readHandler = { feature ->
            OperationOutcome.Success(port.deviceValues.getValue(feature))
        }
        val engine = engine(port)
        engine.adoptSnapshot(snapshot())

        val first = async { engine.write(anc, on, snapshot(), "op-1", timeoutMillis = 5_000) }
        val second = async { engine.write(anc, off, snapshot(), "op-2", timeoutMillis = 5_000) }
        first.await()
        second.await()

        // Serialized: the first write fully completes (including read-back)
        // before the second begins.
        val firstWriteEnd = order.indexOf("write-BooleanValue(value=true)-end")
        val secondWriteStart = order.indexOf("write-BooleanValue(value=false)-start")
        assertTrue(firstWriteEnd >= 0 && secondWriteStart > firstWriteEnd, "writes interleaved: $order")
        assertEquals(2, port.writesOf(anc))
        // The last writer wins, confirmed by read-back.
        val state = assertIs<FeatureState.Confirmed>(engine.states.value[anc])
        assertEquals(off, state.value)
    }

    @Test
    fun writesToDifferentFeaturesProceedConcurrently() = runTest {
        val port = ScriptedFeaturePort()
        var concurrent = 0
        var maxConcurrent = 0
        port.writeHandler = { feature, value ->
            concurrent++
            if (concurrent > maxConcurrent) maxConcurrent = concurrent
            delay(50)
            concurrent--
            port.deviceValues[feature] = value
            OperationOutcome.Success(Unit)
        }
        port.readHandler = { feature ->
            OperationOutcome.Success(port.deviceValues.getValue(feature))
        }
        val engine = engine(port)
        engine.adoptSnapshot(snapshot())

        val a = async { engine.write(anc, on, snapshot(), "op-1", timeoutMillis = 5_000) }
        val b = async { engine.write(transparency, on, snapshot(), "op-2", timeoutMillis = 5_000) }
        a.await()
        b.await()

        assertEquals(2, maxConcurrent, "different features must not serialize on each other")
    }

    @Test
    fun aDisconnectDuringTheReadBackPoisonsTheWrite() = runTest {
        val port = ScriptedFeaturePort()
        port.writeHandler = { _, _ ->
            delay(20)
            OperationOutcome.Success(Unit)
        }
        port.readHandler = { _ ->
            delay(5_000) // the read-back never returns before the session dies
            OperationOutcome.Success(on)
        }
        val engine = engine(port)
        engine.adoptSnapshot(snapshot())

        val result = async {
            engine.write(anc, on, snapshot(), "op-1", timeoutMillis = 30_000)
        }
        delay(40) // the write was accepted; the read-back is now stuck
        engine.onSessionInvalidated()
        val outcome = result.await()

        val failure = assertIs<OperationOutcome.Failure>(outcome)
        assertEquals(OmniBudsErrorCategory.DEVICE_DISCONNECTED, failure.error.category)
        // The invalidation moved the feature to Unknown; the poisoned completion
        // must not touch it afterwards.
        assertIs<FeatureState.Unknown>(engine.states.value[anc])
    }

    @Test
    fun cancellationNeverLeavesAFeatureStrandedInPending() = runTest {
        val port = ScriptedFeaturePort()
        port.writeHandler = { _, _ ->
            delay(5_000)
            OperationOutcome.Success(Unit)
        }
        val engine = engine(port)
        engine.adoptSnapshot(snapshot())

        val job = async {
            engine.write(anc, on, snapshot(), "op-1", timeoutMillis = 30_000)
        }
        delay(10)
        assertIs<FeatureState.Pending>(engine.states.value[anc])
        job.cancel()
        try {
            job.await()
        } catch (_: kotlinx.coroutines.CancellationException) {
            // Expected: cancellation propagates to the caller.
        }

        val state = engine.states.value[anc]
        assertTrue(state !is FeatureState.Pending, "a cancelled write left $state behind")
        assertIs<FeatureState.Available>(state)
    }

    @Test
    fun aFailedWriteDoesNotBlockTheNextAttempt() = runTest {
        val port = ScriptedFeaturePort()
        var attempts = 0
        port.writeHandler = { feature, value ->
            attempts++
            if (attempts == 1) {
                OperationOutcome.Failure(
                    com.omnibuds.core.common.OmniBudsError.of(
                        OmniBudsErrorCategory.WRITE_REJECTED,
                        "op-1",
                        "first attempt rejected",
                    ),
                )
            } else {
                port.deviceValues[feature] = value
                OperationOutcome.Success(Unit)
            }
        }
        port.readHandler = { feature ->
            OperationOutcome.Success(port.deviceValues.getValue(feature))
        }
        val engine = engine(port)
        engine.adoptSnapshot(snapshot())

        val first = engine.write(anc, on, snapshot(), "op-1")
        assertIs<OperationOutcome.Failure>(first)
        assertIs<FeatureState.Failed>(engine.states.value[anc])

        val second = engine.write(anc, on, snapshot(), "op-2")
        assertIs<OperationOutcome.Success<FeatureState>>(second)
        assertIs<FeatureState.Confirmed>(engine.states.value[anc])
        assertEquals(2, attempts)
    }
}
