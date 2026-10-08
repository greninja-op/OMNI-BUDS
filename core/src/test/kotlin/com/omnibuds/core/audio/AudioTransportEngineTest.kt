package com.omnibuds.core.audio

import com.omnibuds.core.common.OperationOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

/**
 * The engine's lifecycle, observation merging and teardown guarantees.
 * (OB-P10-REQ-013, OB-P10-REQ-017)
 */
class AudioTransportEngineTest {

    private class FakeProfileSource(
        var states: Map<AudioTransportKind, AudioProfileState> = emptyMap(),
        var failReads: Boolean = false,
    ) : AudioProfileSource {
        override suspend fun readProfileStates(): Map<AudioTransportKind, AudioProfileState> {
            if (failReads) throw IllegalStateException("simulated profile failure")
            return states
        }

        override suspend fun readActiveTransportHint(): AudioTransportKind? = null
    }

    private class FakeDeviceSource(
        var devices: List<ObservedAudioDevice> = emptyList(),
    ) : AudioDeviceSource {
        val events = MutableSharedFlow<AudioDeviceEvent>(extraBufferCapacity = 16)
        override suspend fun readDevices(): List<ObservedAudioDevice> = devices
        override fun observeDeviceEvents(): Flow<AudioDeviceEvent> = events.asSharedFlow()
    }

    private val capabilities = AudioPlatformCapabilities(
        leAudioApiAvailable = false,
        apiLevel = 30,
        a2dpSupported = true,
        hfpSupported = true,
        hspSupported = true,
        audioDeviceCallbackSupported = true,
    )

    private fun device(id: Int, active: Boolean = false) = ObservedAudioDevice(
        platformDeviceId = id,
        type = AudioDeviceType.BLUETOOTH_A2DP,
        productName = "Buds",
        bluetoothAddress = null,
        direction = AudioDirection.OUTPUT,
        isActive = active,
        source = "test",
    )

    private fun profile(kind: AudioTransportKind, state: AudioConnectionState) =
        AudioProfileState(
            profile = kind,
            availability = ProfileAvailability.AVAILABLE,
            connectionState = state,
            audioState = null,
            deviceAddress = null,
            source = "test",
        )

    private var now = 1_000L

    /**
     * Builds the engine on this test's scheduler, so the engine's observation
     * work runs on virtual time and advanceTimeBy drives it deterministically.
     */
    private fun kotlinx.coroutines.test.TestScope.engine(
        profiles: FakeProfileSource = FakeProfileSource(),
        devices: FakeDeviceSource = FakeDeviceSource(),
    ) = AudioTransportEngine(
        profiles,
        devices,
        capabilities,
        clockMillis = { now },
        dispatcher = kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
    )

    @Test
    fun startPublishesInitialSnapshotAndReachesObserving() = runTest {
        val e = engine()
        assertEquals(AudioObserverLifecycle.STOPPED, e.lifecycle.value)

        val result = e.start()
        assertIs<OperationOutcome.Success<Unit>>(result)
        assertEquals(AudioObserverLifecycle.OBSERVING, e.lifecycle.value)

        val snapshot = e.snapshots.value
        assertEquals(1, snapshot.schemaVersion)
        e.stop()
    }

    @Test
    fun repeatedStartIsANoOpSuccess() = runTest {
        val e = engine()
        e.start()
        val second = e.start()
        assertIs<OperationOutcome.Success<Unit>>(second)
        assertEquals(AudioObserverLifecycle.OBSERVING, e.lifecycle.value)
        e.stop()
    }

    @Test
    fun stopIsIdempotentAndReturnsToStopped() = runTest {
        val e = engine()
        e.start()
        assertIs<OperationOutcome.Success<Unit>>(e.stop())
        assertEquals(AudioObserverLifecycle.STOPPED, e.lifecycle.value)
        // Stopping a stopped observer is still a success, not an error.
        assertIs<OperationOutcome.Success<Unit>>(e.stop())
    }

    @Test
    fun failedStartLandsBackInStoppedWithAnError() = runTest {
        // The clock works for construction, then breaks: start()'s initial
        // readAndReconcile throws outside any per-source catch, so start must
        // report Failure and land back in STOPPED — never a half-registered
        // observer.
        var calls = 0
        val e = AudioTransportEngine(
            profileSource = FakeProfileSource(),
            deviceSource = FakeDeviceSource(),
            capabilities = capabilities,
            clockMillis = {
                calls++
                if (calls > 1) throw IllegalStateException("clock broken")
                1_000L
            },
        )
        val result = e.start()
        assertIs<OperationOutcome.Failure>(result)
        assertEquals(AudioObserverLifecycle.STOPPED, e.lifecycle.value)
        // And the engine is still usable afterwards.
        assertIs<OperationOutcome.Success<Unit>>(e.stop())
    }

    @Test
    fun deviceEventsAreMergedIntoSnapshots() = runTest {
        val deviceSource = FakeDeviceSource()
        val e = engine(devices = deviceSource)
        e.start()
        // Let the engine's event collector subscribe before emitting: the
        // fake's SharedFlow buffers, but the subscription must be scheduled.
        advanceUntilIdle()

        now += 1_000L
        deviceSource.devices = listOf(device(7, active = true))
        deviceSource.events.emit(AudioDeviceEvent.Resynchronized(deviceSource.devices))

        // Give the collector a chance to process the event.
        advanceUntilIdle()
        val snapshot = e.snapshots.value
        assertEquals(1, snapshot.devices.size)
        assertEquals(7, snapshot.devices.first().platformDeviceId)
        e.stop()
    }

    @Test
    fun deviceRemovalIsReflected() = runTest {
        val deviceSource = FakeDeviceSource(devices = listOf(device(7)))
        val e = engine(devices = deviceSource)
        e.start()
        assertEquals(1, e.snapshots.value.devices.size)
        // Let the engine's event collector subscribe before emitting.
        advanceUntilIdle()

        now += 1_000L
        deviceSource.devices = emptyList()
        deviceSource.events.emit(AudioDeviceEvent.Removed(listOf(7)))

        advanceUntilIdle()
        assertTrue(e.snapshots.value.devices.isEmpty())
        e.stop()
    }

    @Test
    fun refreshProfilesRequiresObservation() = runTest {
        val e = engine()
        val result = e.refreshProfiles()
        assertIs<OperationOutcome.Failure>(result)
        assertEquals(AudioObserverLifecycle.STOPPED, e.lifecycle.value)
    }

    @Test
    fun refreshProfilesReconcilesFreshReads() = runTest {
        val profiles = FakeProfileSource(
            states = mapOf(
                AudioTransportKind.CLASSIC_A2DP to profile(
                    AudioTransportKind.CLASSIC_A2DP,
                    AudioConnectionState.CONNECTED,
                ),
            ),
        )
        val e = engine(profiles = profiles)
        e.start()

        now += 5_000L
        profiles.states = mapOf(
            AudioTransportKind.CLASSIC_A2DP to profile(
                AudioTransportKind.CLASSIC_A2DP,
                AudioConnectionState.DISCONNECTED,
            ),
        )
        assertIs<OperationOutcome.Success<Unit>>(e.refreshProfiles())
        assertEquals(
            AudioConnectionState.DISCONNECTED,
            e.snapshots.value.profileStates[AudioTransportKind.CLASSIC_A2DP]?.connectionState,
        )
        e.stop()
    }

    @Test
    fun failingProfileReadKeepsPreviousSnapshotAndRecordsDiagnostic() = runTest {
        val profiles = FakeProfileSource(
            states = mapOf(
                AudioTransportKind.CLASSIC_A2DP to profile(
                    AudioTransportKind.CLASSIC_A2DP,
                    AudioConnectionState.CONNECTED,
                ),
            ),
        )
        val e = engine(profiles = profiles)
        e.start()

        profiles.failReads = true
        now += 5_000L
        assertIs<OperationOutcome.Success<Unit>>(e.refreshProfiles())
        // Previous states kept, not fabricated as DISCONNECTED.
        assertEquals(
            AudioConnectionState.CONNECTED,
            e.snapshots.value.profileStates[AudioTransportKind.CLASSIC_A2DP]?.connectionState,
        )
        assertTrue(e.snapshots.value.diagnostics.any { it.code == "PROFILE_READ_FAILED" })
        e.stop()
    }

    @Test
    fun lifecycleTransitionsAreObservable() = runTest {
        val e = engine()
        // Synchronous assertions on the StateFlow value: no collector timing
        // involved, so no conflation race on the intermediate states.
        assertEquals(AudioObserverLifecycle.STOPPED, e.lifecycle.value)
        e.start()
        assertEquals(AudioObserverLifecycle.OBSERVING, e.lifecycle.value)
        e.stop()
        assertEquals(AudioObserverLifecycle.STOPPED, e.lifecycle.value)
    }
}
