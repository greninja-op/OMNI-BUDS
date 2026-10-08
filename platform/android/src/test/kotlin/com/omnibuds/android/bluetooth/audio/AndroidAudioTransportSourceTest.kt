package com.omnibuds.android.bluetooth.audio

import android.bluetooth.BluetoothProfile
import android.media.AudioDeviceInfo
import com.omnibuds.core.audio.AudioConnectionState
import com.omnibuds.core.audio.AudioDeviceEvent
import com.omnibuds.core.audio.AudioTransportKind
import com.omnibuds.core.audio.ProfileAvailability
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Android audio source, tested against a scripted handle.
 *
 * The handle is the only part of this path that touches the framework, so
 * replacing it makes the interesting behaviour — what the source does with
 * raw readings, a refused permission, and callback registration — testable on
 * a JVM with no radio and no audio hardware (Phase 2 prompt section 9). What
 * stays unproven here is the framework call itself, and
 * `docs/phases/phase-10/validation.md` says so rather than implying the suite
 * covers it.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AndroidAudioTransportSourceTest {

    private class FakeHandle(
        var profileRead: AudioTransportHandle.RawProfileRead =
            AudioTransportHandle.RawProfileRead(emptyMap(), emptyMap(), null),
        var devices: List<RawAudioDevice> = emptyList(),
        var activeIds: Set<Int> = emptySet(),
        var registrations: Int = 0,
        var unregistrations: Int = 0,
    ) : AudioTransportHandle {
        override fun readRawProfiles(): AudioTransportHandle.RawProfileRead = profileRead
        override fun readRawAudioDevices(): List<RawAudioDevice> = devices
        override fun readActiveDeviceIds(): Set<Int> = activeIds
        override fun openAudioDeviceChanges(emit: () -> Unit): AutoCloseable {
            registrations++
            var closed = false
            return AutoCloseable {
                if (!closed) {
                    closed = true
                    unregistrations++
                }
            }
        }
    }

    private class FakeLeAudio(
        override val isBound: Boolean = true,
        val states: Map<String, Int?> = emptyMap(),
    ) : LeAudioHandle {
        override fun readRawConnectionStates(): Map<String, Int?> = states
    }

    private fun source(
        handle: FakeHandle,
        leAudio: LeAudioHandle? = null,
        granted: Boolean = true,
    ) = AndroidAudioTransportSource(
        handle = handle,
        leAudioHandle = leAudio,
        bluetoothConnectGranted = { granted },
    )

    @Test
    fun profileStatesAreTranslated() = runTest {
        val handle = FakeHandle(
            profileRead = AudioTransportHandle.RawProfileRead(
                states = mapOf(
                    BluetoothProfile.A2DP to BluetoothProfile.STATE_CONNECTED,
                    BluetoothProfile.HEADSET to BluetoothProfile.STATE_DISCONNECTED,
                ),
                connectedAddresses = mapOf(
                    BluetoothProfile.A2DP to listOf("AA:BB:CC:DD:EE:FF"),
                ),
                headsetAudioState = null,
            ),
        )
        val states = source(handle).readProfileStates()

        val a2dp = states[AudioTransportKind.CLASSIC_A2DP]!!
        assertEquals(AudioConnectionState.CONNECTED, a2dp.connectionState)
        assertEquals(ProfileAvailability.AVAILABLE, a2dp.availability)
        assertEquals("AA:BB:CC:DD:EE:FF", a2dp.deviceAddress)

        val hfp = states[AudioTransportKind.HFP]!!
        assertEquals(AudioConnectionState.DISCONNECTED, hfp.connectionState)
        assertNull(hfp.audioState)
    }

    @Test
    fun headsetAudioStateIsTrackedSeparatelyFromProfileConnection() = runTest {
        val handle = FakeHandle(
            profileRead = AudioTransportHandle.RawProfileRead(
                states = mapOf(BluetoothProfile.HEADSET to BluetoothProfile.STATE_CONNECTED),
                connectedAddresses = emptyMap(),
                headsetAudioState = BluetoothProfile.STATE_CONNECTED,
            ),
        )
        val hfp = source(handle).readProfileStates()[AudioTransportKind.HFP]!!
        assertEquals(AudioConnectionState.CONNECTED, hfp.connectionState)
        // The SCO audio path is up: the platform's closest approach to ACTIVE.
        assertEquals(AudioConnectionState.ACTIVE, hfp.audioState)
    }

    @Test
    fun hspIsReportedUnknownNeverCopiedFromHfp() = runTest {
        // Android serves HSP through the HEADSET proxy: there is no
        // HSP-specific reading, so the source must not invent one by copying
        // the HFP state (ADR-P10-004).
        val handle = FakeHandle(
            profileRead = AudioTransportHandle.RawProfileRead(
                states = mapOf(BluetoothProfile.HEADSET to BluetoothProfile.STATE_CONNECTED),
                connectedAddresses = emptyMap(),
                headsetAudioState = null,
            ),
        )
        val hsp = source(handle).readProfileStates()[AudioTransportKind.HSP]!!
        assertEquals(ProfileAvailability.UNKNOWN, hsp.availability)
        assertEquals(AudioConnectionState.UNKNOWN, hsp.connectionState)
    }

    @Test
    fun leAudioComesOnlyFromItsGuardedHandle() = runTest {
        val handle = FakeHandle(
            profileRead = AudioTransportHandle.RawProfileRead(
                states = mapOf(BluetoothProfile.A2DP to BluetoothProfile.STATE_CONNECTED),
                connectedAddresses = emptyMap(),
                headsetAudioState = null,
            ),
        )
        // No LE Audio handle (API < 33): no LE Audio profile in the map, and
        // crucially the A2DP reading is not reinterpreted as LE Audio.
        val without = source(handle, leAudio = null).readProfileStates()
        assertNull(without[AudioTransportKind.LE_AUDIO])

        val with = source(
            handle,
            leAudio = FakeLeAudio(states = mapOf("AA:BB:CC:DD:EE:FF" to BluetoothProfile.STATE_CONNECTED)),
        ).readProfileStates()
        val le = with[AudioTransportKind.LE_AUDIO]!!
        assertEquals(AudioConnectionState.CONNECTED, le.connectionState)
        assertEquals("AA:BB:CC:DD:EE:FF", le.deviceAddress)
    }

    @Test
    fun refusedPermissionYieldsNoProfilesRatherThanAThrow() = runTest {
        val handle = FakeHandle(
            profileRead = AudioTransportHandle.RawProfileRead(
                states = mapOf(BluetoothProfile.A2DP to BluetoothProfile.STATE_CONNECTED),
                connectedAddresses = emptyMap(),
                headsetAudioState = null,
            ),
        )
        // Permission first: a refused BLUETOOTH_CONNECT is unknown profiles,
        // never a SecurityException escaping into the engine.
        assertTrue(source(handle, granted = false).readProfileStates().isEmpty())
    }

    @Test
    fun devicesAreTranslatedWithActiveFlags() = runTest {
        val handle = FakeHandle(
            devices = listOf(
                RawAudioDevice(11, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "Buds", isSink = true, isSource = false),
                RawAudioDevice(12, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, null, isSink = true, isSource = false),
            ),
            activeIds = setOf(11),
        )
        val devices = source(handle).readDevices()
        assertEquals(2, devices.size)
        val buds = devices.first { it.platformDeviceId == 11 }
        assertTrue(buds.isActive)
        assertEquals("Buds", buds.productName)
        assertNull(devices.first { it.platformDeviceId == 12 }.productName)
    }

    @Test
    fun deviceObservationRegistersAndUnregistersDeterministically() = runTest {
        val handle = FakeHandle(
            devices = listOf(
                RawAudioDevice(11, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "Buds", isSink = true, isSource = false),
            ),
        )
        val src = source(handle)

        val collected = mutableListOf<AudioDeviceEvent>()
        val job = launch {
            src.observeDeviceEvents().toList(collected)
        }
        // Let the initial resynchronisation arrive, then cancel: awaitClose
        // must release the callback exactly once.
        advanceTimeBy(100)
        job.cancel()
        job.join()

        assertEquals(1, handle.registrations)
        assertEquals(1, handle.unregistrations)
        val first = collected.first()
        assertIs<AudioDeviceEvent.Resynchronized>(first)
        assertEquals(1, first.devices.size)
    }
}
