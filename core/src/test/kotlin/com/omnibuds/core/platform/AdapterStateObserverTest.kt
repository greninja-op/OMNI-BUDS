package com.omnibuds.core.platform

import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behaviour of the adapter-state observer, tested against a scripted source.
 *
 * Every case here corresponds to a requirement in Phase 2 prompt section 5.4, and the point of
 * testing them through the seam rather than on a phone is that a leaked registration, a duplicate
 * announcement or a swallowed teardown failure is a defect we can prove without hardware. No case
 * here makes any claim about a real device.
 */
// advanceUntilIdle is the only way to say "the concurrent collector has started" without sleeping,
// and it is still marked experimental in the coroutines version this project pins.
@OptIn(ExperimentalCoroutinesApi::class)
class AdapterStateObserverTest {

    private val enabled = OperationOutcome.Success(BluetoothAdapterState.ENABLED)
    private val disabled = OperationOutcome.Success(BluetoothAdapterState.DISABLED)

    private fun outcomes(vararg states: BluetoothAdapterState): List<BluetoothAdapterState> = states.toList()

    @Test
    fun anObservationStartsWithWhatTheAdapterIsRightNow() = runTest {
        val source = FakeAdapterStateSource(enabled)
        val observer = AdapterStateObserver(source.asSource())

        val results = observer.observe().toList()

        assertEquals(1, results.size)
        val first = assertIs<OperationOutcome.Success<AdapterStateObservation>>(results.first())
        assertEquals(BluetoothAdapterState.ENABLED, first.value.state)
        assertEquals(ObservationKind.INITIAL_READ, first.value.kind)
    }

    @Test
    fun aStateThePlatformAnnouncesTwiceIsNotPassedOnTwice() = runTest {
        val source = FakeAdapterStateSource(
            enabled,
            eventScript = listOf(BluetoothAdapterState.ENABLED, BluetoothAdapterState.ENABLED, BluetoothAdapterState.DISABLED),
        )
        val observer = AdapterStateObserver(source.asSource())

        val announced = observer.observe().toList().mapNotNull { it.valueOrNull?.state }

        assertEquals(outcomes(BluetoothAdapterState.ENABLED, BluetoothAdapterState.DISABLED), announced)
    }

    @Test
    fun anOffOnOffSequenceIsPreservedRatherThanCollapsedToTheLastValue() = runTest {
        val source = FakeAdapterStateSource(
            enabled,
            eventScript = listOf(
                BluetoothAdapterState.DISABLED,
                BluetoothAdapterState.ENABLED,
                BluetoothAdapterState.DISABLED,
            ),
        )
        val observer = AdapterStateObserver(source.asSource())

        val results = observer.observe().toList()

        assertEquals(4, results.size)
        val kinds = results.mapNotNull { it.valueOrNull?.kind }
        assertEquals(
            listOf(
                ObservationKind.INITIAL_READ,
                ObservationKind.INITIAL_EVENT,
                ObservationKind.PLATFORM_EVENT,
                ObservationKind.PLATFORM_EVENT,
            ),
            kinds,
        )
    }

    @Test
    fun aSecondConcurrentObserverIsRefusedInsteadOfRegisteringTwice() = runTest {
        val source = FakeAdapterStateSource(
            enabled,
            eventScript = listOf(BluetoothAdapterState.DISABLED),
            keepOpen = true,
        )
        val observer = AdapterStateObserver(source.asSource())

        // Attempted from inside the live collection, so the slot is demonstrably held rather than
        // racing the scheduler: the first observation only releases it in its finally block.
        val secondAttempt = observer.observe()
            .map { observer.observe().first() }
            .first()

        val failure = assertIs<OperationOutcome.Failure>(secondAttempt)
        assertEquals(OmniBudsErrorCategory.RESOURCE_UNAVAILABLE, failure.error.category)
        assertEquals(1, source.openCalls, "the platform must be registered with exactly once")
    }

    @Test
    fun theRegistrationIsTornDownWhenTheStreamEndsNormally() = runTest {
        val source = FakeAdapterStateSource(enabled)
        val observer = AdapterStateObserver(source.asSource())

        observer.observe().toList()

        assertEquals(1, source.disposeCalls)
        assertEquals(0, source.activeRegistrations)
        assertFalse(observer.isObserving)
        assertNull(observer.teardownProblem)
    }

    @Test
    fun cancellationTearsTheRegistrationDownToo() = runTest {
        val source = FakeAdapterStateSource(enabled, keepOpen = true)
        val observer = AdapterStateObserver(source.asSource())

        val collector = launch { observer.observe().toList() }
        advanceUntilIdle()
        collector.cancelAndJoin()

        assertEquals(1, source.disposeCalls)
        assertEquals(0, source.activeRegistrations)
        assertFalse(observer.isObserving)
    }

    @Test
    fun aFailedFirstReadIsReportedAndTheChangeStreamStillRuns() = runTest {
        val readFailure = OperationOutcome.Failure(
            com.omnibuds.core.common.OmniBudsError(
                category = OmniBudsErrorCategory.READ_FAILED,
                operationId = "test.read",
            ),
        )
        val source = FakeAdapterStateSource(
            readFailure,
            eventScript = listOf(BluetoothAdapterState.DISABLED),
        )
        val observer = AdapterStateObserver(source.asSource())

        val results = observer.observe().toList()

        assertIs<OperationOutcome.Failure>(results.first())
        val second = assertIs<OperationOutcome.Success<AdapterStateObservation>>(results[1])
        assertEquals(BluetoothAdapterState.DISABLED, second.value.state)
        // The adapter was never reported as off just because the read failed: the state stays
        // unknown until the platform says otherwise (ADR-P0-016).
        assertEquals(ObservationKind.INITIAL_EVENT, second.value.kind)
    }

    @Test
    fun aTeardownThatThrowsIsRecordedInsteadOfDisappearing() = runTest {
        val source = FakeAdapterStateSource(enabled)
        source.failDisposal = true
        val observer = AdapterStateObserver(source.asSource())

        val results = observer.observe().toList()

        val problem = assertNotNull(observer.teardownProblem)
        assertEquals(OmniBudsErrorCategory.RESOURCE_UNAVAILABLE, problem.category)
        assertEquals(1, results.size, "the observations themselves are still delivered")
    }

    @Test
    fun aRefusedRegistrationOpensNothingAndReportsWhy() = runTest {
        val source = FakeAdapterStateSource(enabled)
        source.failOpen = true
        val observer = AdapterStateObserver(source.asSource())

        val results = observer.observe().toList()

        assertEquals(2, results.size)
        val failure = assertIs<OperationOutcome.Failure>(results[1])
        assertEquals(OmniBudsErrorCategory.RESOURCE_UNAVAILABLE, failure.error.category)
        assertEquals(0, source.activeRegistrations)
    }

    @Test
    fun aSingleReadDoesNotOccupyTheObserverSlot() = runTest {
        val source = FakeAdapterStateSource(enabled, disabled)
        val observer = AdapterStateObserver(source.asSource())

        val outcome = observer.readOnce()

        assertIs<OperationOutcome.Success<AdapterStateObservation>>(outcome)
        assertEquals(ObservationKind.PLATFORM_READ, outcome.value.kind)
        assertFalse(observer.isObserving)
        assertEquals(0, source.openCalls, "a one-shot read must not register with the platform")

        val second = observer.readOnce()
        assertEquals(BluetoothAdapterState.DISABLED, assertIs<OperationOutcome.Success<AdapterStateObservation>>(second).value.state)
    }

    @Test
    fun anUnavailableSourceReportsUnavailableAndNotEnabled() = runTest {
        val observer = AdapterStateObserver(AdapterStateSource.unavailable())

        val outcome = observer.readOnce()

        val failure = assertIs<OperationOutcome.Failure>(outcome)
        assertEquals(OmniBudsErrorCategory.ADAPTER_UNAVAILABLE, failure.error.category)
    }

    @Test
    fun aTimestampIsRecordedOnlyWhenThePlatformSuppliesOne() = runTest {
        val clockSource = FakeAdapterStateSource(enabled)
        val timed = AdapterStateObserver(clockSource.asSource(), TimeProvider { 1_700_000_000_000L })
        val untimed = AdapterStateObserver(FakeAdapterStateSource(enabled).asSource())

        assertEquals(1_700_000_000_000L, assertNotNull(timed.observe().first().valueOrNull).observedAtEpochMillis)
        // No clock reading is not the epoch; it is unknown time (ADR-P1-012).
        assertNull(assertNotNull(untimed.observe().first().valueOrNull).observedAtEpochMillis)
    }

    @Test
    fun onlyUsableAdapterStatesAreReportedUsable() {
        outcomes(BluetoothAdapterState.ENABLED).forEach { state -> assertTrue(state.isUsable()) }
        assertFalse(BluetoothAdapterState.UNKNOWN.isUsable())
        assertFalse(BluetoothAdapterState.UNAVAILABLE.isUsable())

        // "Could not look" must never be rendered as "it is switched off".
        assertFalse(BluetoothAdapterState.UNKNOWN.isProvablyDisabled())
        assertTrue(BluetoothAdapterState.DISABLED.isProvablyDisabled())
        assertTrue(BluetoothAdapterState.UNKNOWN.isIndeterminate())
        assertFalse(BluetoothAdapterState.DISABLED.isIndeterminate())
    }
}
