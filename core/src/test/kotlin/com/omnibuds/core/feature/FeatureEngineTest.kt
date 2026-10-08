package com.omnibuds.core.feature

import com.omnibuds.core.capability.CapabilityAvailability
import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.config.ConfigurationValue
import com.omnibuds.core.state.CapabilityState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The feature engine's operation lifecycle.
 *
 * Every guarantee in the engine's KDoc is exercised here against a scripted
 * port: requested never becoming confirmed without device evidence, the
 * mandatory read-back, timeout-never-resent, stale-response handling, external
 * device updates winning over in-flight requests, cancellation restoring, and
 * session invalidation poisoning in-flight work. No device is implied; the
 * ceiling is IMPLEMENTED.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FeatureEngineTest {

    private val anc = StandardFeatures.ANC.feature
    private val on = ConfigurationValue.BooleanValue(true)
    private val off = ConfigurationValue.BooleanValue(false)

    private fun engine(port: ScriptedFeaturePort) = FeatureEngine(
        definitions = StandardFeatures.asMap,
        port = port,
    )

    private fun supportedSnapshot() = FeatureTestFixtures.snapshot(
        FeatureTestFixtures.record(anc, CapabilityState.SUPPORTED_VOLATILE),
    )

    private fun error(category: OmniBudsErrorCategory, id: String, detail: String) =
        OmniBudsError.of(category, id, detail)

    /** A port whose writes are accepted and whose reads report the last written value. */
    private fun healthyPort(): ScriptedFeaturePort {
        val port = ScriptedFeaturePort()
        port.deviceValues[anc] = off
        port.writeHandler = { feature, value ->
            port.deviceValues[feature] = value
            OperationOutcome.Success(Unit)
        }
        port.readHandler = { feature ->
            val value = port.deviceValues[feature]
            if (value != null) OperationOutcome.Success(value)
            else OperationOutcome.Failure(error(OmniBudsErrorCategory.READ_FAILED, "no-value", "nothing scripted"))
        }
        return port
    }

    @Test
    fun adoptSnapshotSeedsAvailableUnknownAndUnavailable() = runTest {
        val transparency = StandardFeatures.TRANSPARENCY.feature
        val engine = engine(ScriptedFeaturePort())
        engine.adoptSnapshot(
            FeatureTestFixtures.snapshot(
                FeatureTestFixtures.record(anc, CapabilityState.SUPPORTED_VOLATILE),
                FeatureTestFixtures.record(transparency, CapabilityState.SUPPORTED_VOLATILE),
                availability = mapOf(transparency to CapabilityAvailability.UNAVAILABLE),
            ),
        )
        assertIs<FeatureState.Available>(engine.states.value[anc])
        val t = assertIs<FeatureState.Unavailable>(engine.states.value[transparency])
        assertTrue(t.reason.isNotBlank())
        // The equalizer has no record: unknown, not unsupported.
        assertIs<FeatureState.Unknown>(engine.states.value[StandardFeatures.EQUALIZER.feature])
    }

    @Test
    fun aSuccessfulWriteGoesPendingThenConfirmed() = runTest {
        val port = healthyPort()
        val engine = engine(port)
        engine.adoptSnapshot(supportedSnapshot())

        val outcome = engine.write(anc, on, supportedSnapshot(), "op-1")

        val confirmed = assertIs<OperationOutcome.Success<FeatureState>>(outcome)
        assertEquals(on, (confirmed.value as FeatureState.Confirmed).value)
        // The write was sent once, then the mandatory read-back — never more writes.
        assertEquals(1, port.writesOf(anc))
        assertEquals(1, port.readsOf(anc))
        assertIs<FeatureState.Confirmed>(engine.states.value[anc])
    }

    @Test
    fun aWriteThatTheDeviceRejectsFailsAndKeepsTheLastConfirmed() = runTest {
        val port = healthyPort()
        val engine = engine(port)
        engine.adoptSnapshot(supportedSnapshot())
        // Establish a confirmed value first, while the device is cooperating.
        val established = engine.write(anc, off, supportedSnapshot(), "op-0")
        assertIs<OperationOutcome.Success<FeatureState>>(established)

        // Now the device starts refusing.
        port.writeHandler = { _, _ ->
            OperationOutcome.Failure(error(OmniBudsErrorCategory.WRITE_REJECTED, "op-1", "device said no"))
        }
        val outcome = engine.write(anc, on, supportedSnapshot(), "op-1")

        val failure = assertIs<OperationOutcome.Failure>(outcome)
        assertEquals(OmniBudsErrorCategory.WRITE_REJECTED, failure.error.category)
        val state = assertIs<FeatureState.Failed>(engine.states.value[anc])
        assertEquals(off, state.lastConfirmed, "a rejection must not erase the last confirmed value")
        assertEquals(2, port.writesOf(anc), "op-0 once, op-1 once: a rejected write is never retried")
    }

    @Test
    fun aWriteAcceptedButReadBackDifferentConfirmsTheDeviceValueAndFails() = runTest {
        val port = healthyPort()
        // The device applies something other than requested: a stubborn device.
        port.writeHandler = { feature, _ ->
            port.deviceValues[feature] = off
            OperationOutcome.Success(Unit)
        }
        val engine = engine(port)
        engine.adoptSnapshot(supportedSnapshot())

        val outcome = engine.write(anc, on, supportedSnapshot(), "op-1")

        // The goal was not achieved — but the truth is known, not guessed.
        val failure = assertIs<OperationOutcome.Failure>(outcome)
        assertEquals(OmniBudsErrorCategory.VERIFICATION_FAILED, failure.error.category)
        val state = assertIs<FeatureState.Confirmed>(engine.states.value[anc])
        assertEquals(off, state.value)
    }

    @Test
    fun aWriteAcceptedButReadBackFailedLeavesTheStateUnknown() = runTest {
        val port = ScriptedFeaturePort()
        port.writeHandler = { _, _ -> OperationOutcome.Success(Unit) }
        port.readHandler = { _ ->
            OperationOutcome.Failure(error(OmniBudsErrorCategory.READ_FAILED, "op-1", "read-back failed"))
        }
        val engine = engine(port)
        engine.adoptSnapshot(supportedSnapshot())

        val outcome = engine.write(anc, on, supportedSnapshot(), "op-1")

        val failure = assertIs<OperationOutcome.Failure>(outcome)
        assertEquals(OmniBudsErrorCategory.READ_FAILED, failure.error.category)
        // Unknown — never a guess that the write applied.
        assertIs<FeatureState.Unknown>(engine.states.value[anc])
    }

    @Test
    fun aTimedOutWriteIsNeverResentAndReadsBackInstead() = runTest {
        val port = ScriptedFeaturePort()
        port.writeHandler = { _, _ ->
            delay(60_000) // longer than the operation bound
            OperationOutcome.Success(Unit)
        }
        // The device actually applied the value despite the timeout.
        port.deviceValues[anc] = on
        port.readHandler = { feature ->
            OperationOutcome.Success(port.deviceValues.getValue(feature))
        }
        val engine = engine(port)
        engine.adoptSnapshot(supportedSnapshot())

        val outcome = engine.write(anc, on, supportedSnapshot(), "op-1", timeoutMillis = 100)

        assertEquals(1, port.writesOf(anc), "a timed-out write must never be re-sent (PROTO-ERR-002)")
        val success = assertIs<OperationOutcome.Success<FeatureState>>(outcome)
        assertIs<FeatureState.Confirmed>(success.value)
    }

    @Test
    fun aTimedOutWriteWithADivergentReadBackConfirmsTheDeviceValue() = runTest {
        val port = ScriptedFeaturePort()
        port.writeHandler = { _, _ ->
            delay(60_000)
            OperationOutcome.Success(Unit)
        }
        port.deviceValues[anc] = off // the device holds something else
        port.readHandler = { feature ->
            OperationOutcome.Success(port.deviceValues.getValue(feature))
        }
        val engine = engine(port)
        engine.adoptSnapshot(supportedSnapshot())

        val outcome = engine.write(anc, on, supportedSnapshot(), "op-1", timeoutMillis = 100)

        val failure = assertIs<OperationOutcome.Failure>(outcome)
        assertEquals(OmniBudsErrorCategory.TIMEOUT, failure.error.category)
        val state = assertIs<FeatureState.Confirmed>(engine.states.value[anc])
        assertEquals(off, state.value)
        assertEquals(1, port.writesOf(anc), "still exactly one write attempt")
    }

    @Test
    fun aTimedOutWriteWithAFailedReadBackLeavesTheStateUnknown() = runTest {
        val port = ScriptedFeaturePort()
        port.writeHandler = { _, _ ->
            delay(60_000)
            OperationOutcome.Success(Unit)
        }
        port.readHandler = { _ ->
            OperationOutcome.Failure(error(OmniBudsErrorCategory.READ_FAILED, "op-1", "no read-back"))
        }
        val engine = engine(port)
        engine.adoptSnapshot(supportedSnapshot())

        val outcome = engine.write(anc, on, supportedSnapshot(), "op-1", timeoutMillis = 100)

        val failure = assertIs<OperationOutcome.Failure>(outcome)
        assertEquals(OmniBudsErrorCategory.TIMEOUT, failure.error.category)
        assertIs<FeatureState.Unknown>(engine.states.value[anc])
    }

    @Test
    fun aValidationFailureSendsNothingAndRecordsFailed() = runTest {
        val port = healthyPort()
        val engine = engine(port)
        engine.adoptSnapshot(supportedSnapshot())

        // ANC is boolean; a string value is invalid.
        val outcome = engine.write(anc, ConfigurationValue.StringValue("loud"), supportedSnapshot(), "op-1")

        val failure = assertIs<OperationOutcome.Failure>(outcome)
        assertEquals(OmniBudsErrorCategory.INVALID_STATE, failure.error.category)
        assertTrue(port.calls.isEmpty(), "a refused operation must not touch the port")
        assertIs<FeatureState.Failed>(engine.states.value[anc])
    }

    @Test
    fun aReadConfirmsTheDeviceValue() = runTest {
        val port = healthyPort()
        val engine = engine(port)
        engine.adoptSnapshot(supportedSnapshot())

        val outcome = engine.read(anc, supportedSnapshot(), "op-1")

        val success = assertIs<OperationOutcome.Success<FeatureState>>(outcome)
        assertEquals(off, (success.value as FeatureState.Confirmed).value)
        assertEquals(1, port.readsOf(anc))
        assertEquals(0, port.writesOf(anc))
    }

    @Test
    fun aFailedReadKeepsTheLastConfirmedValueAsStaleKnowledge() = runTest {
        val port = healthyPort()
        val engine = engine(port)
        engine.adoptSnapshot(supportedSnapshot())
        engine.write(anc, on, supportedSnapshot(), "op-0")

        port.readHandler = { _ ->
            OperationOutcome.Failure(error(OmniBudsErrorCategory.READ_FAILED, "op-1", "transient"))
        }
        val outcome = engine.read(anc, supportedSnapshot(), "op-1")

        val failure = assertIs<OperationOutcome.Failure>(outcome)
        assertEquals(OmniBudsErrorCategory.READ_FAILED, failure.error.category)
        val state = assertIs<FeatureState.Failed>(engine.states.value[anc])
        assertEquals(on, state.lastConfirmed, "a transient read failure must not erase device truth")
    }

    @Test
    fun aMalformedDeviceValueNeverBecomesState() = runTest {
        val port = healthyPort()
        // The device returns a string where the ANC boolean belongs.
        port.readHandler = { _ ->
            OperationOutcome.Success(ConfigurationValue.StringValue("maybe"))
        }
        val engine = engine(port)
        engine.adoptSnapshot(supportedSnapshot())

        val outcome = engine.read(anc, supportedSnapshot(), "op-1")

        val failure = assertIs<OperationOutcome.Failure>(outcome)
        assertEquals(OmniBudsErrorCategory.INVALID_STATE, failure.error.category)
        // The uninterpretable value is refused; the state records the failure.
        assertIs<FeatureState.Failed>(engine.states.value[anc])
    }

    @Test
    fun aDeviceReportedUpdateOverridesAPendingRequest() = runTest {
        val port = ScriptedFeaturePort()
        port.writeHandler = { _, _ ->
            delay(50)
            OperationOutcome.Success(Unit)
        }
        port.readHandler = { _ -> OperationOutcome.Success(on) }
        val engine = engine(port)
        engine.adoptSnapshot(supportedSnapshot())

        val writeResult = async {
            engine.write(anc, on, supportedSnapshot(), "op-1", timeoutMillis = 5_000)
        }
        delay(10)
        // The user pressed the earbud mid-write: the device reports off.
        engine.onDeviceReported(anc, off)
        val outcome = writeResult.await()

        // The write's completion is superseded: the state holds the newer device truth,
        // and the outcome carries it rather than claiming the write's result.
        val state = assertIs<FeatureState.Confirmed>(engine.states.value[anc])
        assertEquals(off, state.value)
        val success = assertIs<OperationOutcome.Success<FeatureState>>(outcome)
        assertIs<FeatureState.Confirmed>(success.value)
    }

    @Test
    fun deviceReportedUpdatesFromAnyStateBecomeConfirmed() = runTest {
        val engine = engine(ScriptedFeaturePort())
        engine.adoptSnapshot(supportedSnapshot())

        engine.onDeviceReported(anc, on)
        assertEquals(on, (engine.states.value[anc] as FeatureState.Confirmed).value)

        // Reports for undefined features are ignored: no contract, no meaning.
        val unknownFeature = FeatureId.of("noise-control", "never-defined")
        engine.onDeviceReported(unknownFeature, on)
        assertNull(engine.states.value[unknownFeature])

        // Malformed reports never become state.
        engine.onDeviceReported(anc, ConfigurationValue.StringValue("maybe"))
        assertEquals(on, (engine.states.value[anc] as FeatureState.Confirmed).value)
    }

    @Test
    fun sessionInvalidationPoisonsInFlightWorkAndUnknowsEveryFeature() = runTest {
        val port = ScriptedFeaturePort()
        port.writeHandler = { _, _ ->
            delay(5_000)
            OperationOutcome.Success(Unit)
        }
        port.readHandler = { _ -> OperationOutcome.Success(on) }
        val engine = engine(port)
        engine.adoptSnapshot(supportedSnapshot())
        engine.onDeviceReported(anc, on)

        val writeResult = async {
            engine.write(anc, off, supportedSnapshot(), "op-1", timeoutMillis = 30_000)
        }
        delay(10)
        engine.onSessionInvalidated()
        val outcome = writeResult.await()

        val failure = assertIs<OperationOutcome.Failure>(outcome)
        assertEquals(OmniBudsErrorCategory.DEVICE_DISCONNECTED, failure.error.category)
        val state = assertIs<FeatureState.Unknown>(engine.states.value[anc])
        assertEquals(on, state.lastConfirmed, "the last confirmed value survives as stale knowledge")
        assertEquals(1, port.writesOf(anc), "the write was attempted once; the session died around it")
    }

    @Test
    fun cancellationRestoresThePreviousState() = runTest {
        val port = ScriptedFeaturePort()
        port.writeHandler = { _, _ ->
            delay(5_000)
            OperationOutcome.Success(Unit)
        }
        val engine = engine(port)
        engine.adoptSnapshot(supportedSnapshot())
        engine.onDeviceReported(anc, on)

        val writeJob = launch {
            engine.write(anc, off, supportedSnapshot(), "op-1", timeoutMillis = 30_000)
        }
        delay(10)
        assertIs<FeatureState.Pending>(engine.states.value[anc])
        writeJob.cancel()
        writeJob.join()

        // Untouched by definition: the state is what it was before the attempt.
        val restored = assertIs<FeatureState.Confirmed>(engine.states.value[anc])
        assertEquals(on, restored.value)
    }

    @Test
    fun aWriteAfterInvalidationReassertsAvailabilityFirst() = runTest {
        val port = healthyPort()
        val engine = engine(port)
        engine.adoptSnapshot(supportedSnapshot())
        engine.onSessionInvalidated()
        assertIs<FeatureState.Unknown>(engine.states.value[anc])

        // The capability is still established in the fresh snapshot, so the write
        // may proceed: Unknown -> Available -> Pending -> Confirmed.
        val outcome = engine.write(anc, on, supportedSnapshot(), "op-1")

        assertIs<OperationOutcome.Success<FeatureState>>(outcome)
        assertIs<FeatureState.Confirmed>(engine.states.value[anc])
    }

    @Test
    fun observeEmitsStateChanges() = runTest {
        val port = healthyPort()
        // Hold the read-back open so the Pending state is observable: without
        // the gate the write runs to Confirmed before the collector is
        // scheduled, and StateFlow conflation would hide the intermediate.
        val readGate = CompletableDeferred<ConfigurationValue>()
        port.readHandler = { OperationOutcome.Success(readGate.await()) }
        val engine = engine(port)
        engine.adoptSnapshot(supportedSnapshot())

        val seen = mutableListOf<FeatureState?>()
        val collectJob = launch {
            engine.observe(anc).collect { seen += it }
        }
        runCurrent() // let the collector see the seeded Available

        val writeJob = launch {
            engine.write(anc, on, supportedSnapshot(), "op-1")
        }
        // Wait until Pending is actually observed, then release the read-back.
        withTimeout(5_000) {
            while (seen.none { it is FeatureState.Pending }) delay(10)
        }
        readGate.complete(on)
        writeJob.join()
        runCurrent()
        collectJob.cancel()

        val kinds = seen.filterNotNull().map { it::class.simpleName }
        assertTrue(kinds.contains("Available"), "expected Available in $kinds")
        assertTrue(kinds.contains("Pending"), "expected Pending in $kinds")
        assertTrue(kinds.contains("Confirmed"), "expected Confirmed in $kinds")
    }
}
