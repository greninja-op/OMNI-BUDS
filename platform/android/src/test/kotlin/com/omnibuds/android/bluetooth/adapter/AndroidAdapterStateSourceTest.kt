package com.omnibuds.android.bluetooth.adapter

import android.bluetooth.BluetoothAdapter
import com.omnibuds.android.bluetooth.mapping.RAW_STATE_UNREADABLE
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.platform.AdapterStateChangeChannel
import com.omnibuds.core.platform.BluetoothAdapterState
import com.omnibuds.core.platform.PlatformRegistration
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Android adapter-state source, tested against a scripted handle.
 *
 * The handle is the only part of this path that touches the framework, so replacing it makes the
 * interesting behaviour - what the source does with an answer, an absence and a failure - testable on
 * a JVM with no radio (Phase 2 prompt section 9). What stays unproven here is the framework call
 * itself, and `docs/phases/phase-2/validation.md` says so rather than implying the suite covers it.
 */
class AndroidAdapterStateSourceTest {

    @Test
    fun aReadIsTranslatedIntoTheDomainState() = runTest {
        val source = AndroidAdapterStateSource(FakeHandle(raw = BluetoothAdapter.STATE_ON))

        assertEquals(OperationOutcome.Success(BluetoothAdapterState.ENABLED), source.readState())
    }

    @Test
    fun anAbsentAdapterIsReportedAsUnavailable() = runTest {
        val source = AndroidAdapterStateSource(FakeHandle(present = false, raw = null))

        assertEquals(OperationOutcome.Success(BluetoothAdapterState.UNAVAILABLE), source.readState())
    }

    @Test
    fun anUnreadableValueIsUnknownRatherThanDisabled() = runTest {
        val source = AndroidAdapterStateSource(FakeHandle(raw = RAW_STATE_UNREADABLE))

        assertEquals(OperationOutcome.Success(BluetoothAdapterState.UNKNOWN), source.readState())
    }

    @Test
    fun aThrowingReadBecomesAStructuredPlatformFailure() = runTest {
        val source = AndroidAdapterStateSource(
            FakeHandle(readFailure = SecurityException("the platform refused")),
        )

        val outcome = source.readState()

        assertIs<OperationOutcome.Failure>(outcome)
        assertEquals(OmniBudsErrorCategory.PLATFORM_EXCEPTION, outcome.error.category)
        assertTrue(
            outcome.error.detail.orEmpty().contains("SecurityException"),
            "the failure should name the exception class for diagnostics, was ${outcome.error.detail}",
        )
        // A message that quotes the platform's own text could carry an identifier or a permission
        // name out of the process, so only the class is surfaced (SEC-LOG-001).
        assertFalse(outcome.error.detail.orEmpty().contains("the platform refused"))
    }

    @Test
    fun anEmittedStateReachesTheStreamAlreadyTranslated() = runTest {
        val handle = FakeHandle(raw = BluetoothAdapter.STATE_OFF)
        val source = AndroidAdapterStateSource(handle)
        val channel = assertOnSuccess(source.openStateChanges())

        val received = mutableListOf<BluetoothAdapterState>()
        val collector = launch { channel.states.collect { state -> received += state } }
        yield()

        handle.emit?.invoke(BluetoothAdapter.STATE_ON)
        yield()

        assertEquals(listOf(BluetoothAdapterState.ENABLED), received)
        collector.cancelAndJoin()
    }

    @Test
    fun aRegistrationOpenedWithoutCollectingIsStillDisposalReady() = runTest {
        val handle = FakeHandle()
        val source = AndroidAdapterStateSource(handle)
        val channel = assertOnSuccess(source.openStateChanges())

        assertTrue(channel.registration.isActive, "opening should have registered with the platform")

        channel.registration.dispose()

        assertFalse(channel.registration.isActive)
        assertEquals(1, handle.disposeCount)
    }

    @Test
    fun disposingTwiceReachesThePlatformOnce() = runTest {
        val handle = FakeHandle()
        val source = AndroidAdapterStateSource(handle)
        val channel = assertOnSuccess(source.openStateChanges())

        channel.registration.dispose()
        channel.registration.dispose()

        assertEquals(1, handle.disposeCount, "unregistering twice would throw on the real platform")
    }

    @Test
    fun disposalEndsTheStreamSoNoCollectorHangs() = runTest {
        val source = AndroidAdapterStateSource(FakeHandle())
        val channel = assertOnSuccess(source.openStateChanges())

        val collected = launch { channel.states.toList() }
        yield()
        channel.registration.dispose()
        collected.join()

        assertTrue(collected.isCompleted, "a disposed registration must close its stream")
    }

    @Test
    fun aFailedRegistrationIsReportedAndHandsBackNothingToDispose() = runTest {
        val handle = FakeHandle(registerFailure = IllegalStateException("no receiver could be made"))
        val source = AndroidAdapterStateSource(handle)

        val outcome = source.openStateChanges()

        assertIs<OperationOutcome.Failure>(outcome)
        assertEquals(OmniBudsErrorCategory.PLATFORM_EXCEPTION, outcome.error.category)
        assertNull(handle.emit, "a failed registration must not leave an emission path behind")
    }

    private suspend fun assertOnSuccess(
        outcome: OperationOutcome<AdapterStateChangeChannel>,
    ): AdapterStateChangeChannel {
        assertIs<OperationOutcome.Success<AdapterStateChangeChannel>>(outcome)
        return outcome.value
    }

    /** A handle that does exactly what the test says, and records what it was asked to do. */
    private class FakeHandle(
        var present: Boolean = true,
        var raw: Int? = BluetoothAdapter.STATE_ON,
        var readFailure: Throwable? = null,
        var registerFailure: Throwable? = null,
    ) : BluetoothAdapterHandle {
        var emit: ((Int) -> Unit)? = null
        var disposeCount: Int = 0

        override val adapterPresent: Boolean
            get() = present

        override fun readRawState(): Int? {
            readFailure?.let { problem -> throw problem }
            return raw
        }

        override fun openStateChanges(emit: (Int) -> Unit): PlatformRegistration {
            registerFailure?.let { problem -> throw problem }
            this.emit = emit
            return object : PlatformRegistration {
                private var active = true

                override val isActive: Boolean
                    get() = active

                override suspend fun dispose() {
                    if (active) {
                        active = false
                        disposeCount += 1
                    }
                }
            }
        }
    }
}
