package com.omnibuds.android.bluetooth.audio.mapping

import android.bluetooth.BluetoothProfile
import android.media.AudioDeviceInfo
import com.omnibuds.android.bluetooth.audio.RawAudioDevice
import com.omnibuds.core.audio.AudioConnectionState
import com.omnibuds.core.audio.AudioDeviceType
import com.omnibuds.core.audio.AudioDirection
import com.omnibuds.core.audio.AudioTransportKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The single translation point between Android's audio numbers and the domain.
 *
 * These tests run on a plain JVM: the mapping takes only primitives and
 * compile-time constants, so no framework instance is ever needed. What stays
 * unproven here is the framework call itself (the system handle), which is
 * exercised on hardware per the Phase 2 convention.
 */
class AudioStateMappingTest {

    @Test
    fun profileStatesMapToDomainStates() {
        assertEquals(AudioConnectionState.CONNECTED, audioConnectionStateOf(BluetoothProfile.STATE_CONNECTED))
        assertEquals(AudioConnectionState.CONNECTING, audioConnectionStateOf(BluetoothProfile.STATE_CONNECTING))
        assertEquals(AudioConnectionState.DISCONNECTING, audioConnectionStateOf(BluetoothProfile.STATE_DISCONNECTING))
        assertEquals(AudioConnectionState.DISCONNECTED, audioConnectionStateOf(BluetoothProfile.STATE_DISCONNECTED))
    }

    @Test
    fun nullAndUnknownProfileStatesBecomeUnknown() {
        assertEquals(AudioConnectionState.UNKNOWN, audioConnectionStateOf(null))
        assertEquals(AudioConnectionState.UNKNOWN, audioConnectionStateOf(999))
    }

    @Test
    fun headsetAudioConnectedMapsToActive() {
        // SCO audio "connected" is the platform's closest approach to ACTIVE
        // for the HFP path — but the mapping does not claim a call is up.
        assertEquals(AudioConnectionState.ACTIVE, headsetAudioStateOf(BluetoothProfile.STATE_CONNECTED))
        assertEquals(AudioConnectionState.DISCONNECTED, headsetAudioStateOf(BluetoothProfile.STATE_DISCONNECTED))
        assertNull(headsetAudioStateOf(null))
        assertEquals(AudioConnectionState.UNKNOWN, headsetAudioStateOf(999))
    }

    @Test
    fun deviceTypesMapToDomainTypes() {
        assertEquals(AudioDeviceType.BLUETOOTH_A2DP, audioDeviceTypeOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
        assertEquals(AudioDeviceType.BLUETOOTH_SCO, audioDeviceTypeOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO))
        assertEquals(AudioDeviceType.BLE_HEADSET, audioDeviceTypeOf(AudioDeviceInfo.TYPE_BLE_HEADSET))
        assertEquals(AudioDeviceType.BLE_SPEAKER, audioDeviceTypeOf(AudioDeviceInfo.TYPE_BLE_SPEAKER))
        assertEquals(AudioDeviceType.BUILTIN_SPEAKER, audioDeviceTypeOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        assertEquals(AudioDeviceType.WIRED_HEADSET, audioDeviceTypeOf(AudioDeviceInfo.TYPE_WIRED_HEADSET))
        assertEquals(AudioDeviceType.WIRED_HEADSET, audioDeviceTypeOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
        assertEquals(AudioDeviceType.UNKNOWN, audioDeviceTypeOf(999))
    }

    @Test
    fun directionComesFromSinkSourceNotFromType() {
        val sink = RawAudioDevice(1, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "Buds", isSink = true, isSource = false)
        val source = RawAudioDevice(2, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "Buds", isSink = false, isSource = true)
        val both = RawAudioDevice(3, AudioDeviceInfo.TYPE_BLE_HEADSET, "Buds", isSink = true, isSource = true)
        val neither = RawAudioDevice(4, 999, "Mystery", isSink = false, isSource = false)

        assertEquals(AudioDirection.OUTPUT, observedAudioDeviceOf(sink, false, null, "test").direction)
        assertEquals(AudioDirection.INPUT, observedAudioDeviceOf(source, false, null, "test").direction)
        assertEquals(AudioDirection.BIDIRECTIONAL, observedAudioDeviceOf(both, false, null, "test").direction)
        assertEquals(AudioDirection.UNKNOWN, observedAudioDeviceOf(neither, false, null, "test").direction)
    }

    @Test
    fun blankProductNameBecomesNull() {
        val raw = RawAudioDevice(1, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "   ", isSink = true, isSource = false)
        assertNull(observedAudioDeviceOf(raw, false, null, "test").productName)
    }

    @Test
    fun bluetoothProfilesMapToAudioKinds() {
        assertEquals(AudioTransportKind.CLASSIC_A2DP, audioTransportKindOf(BluetoothProfile.A2DP))
        assertEquals(AudioTransportKind.HFP, audioTransportKindOf(BluetoothProfile.HEADSET))
        assertEquals(AudioTransportKind.UNKNOWN, audioTransportKindOf(999))
        assertEquals(AudioTransportKind.UNKNOWN, audioTransportKindOf(999))
    }
}
