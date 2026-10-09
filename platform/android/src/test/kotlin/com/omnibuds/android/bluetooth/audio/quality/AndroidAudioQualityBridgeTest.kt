package com.omnibuds.android.bluetooth.audio.quality

import com.omnibuds.core.audio.AudioDeviceType
import com.omnibuds.core.audio.AudioDirection
import com.omnibuds.core.audio.AudioTransportKind
import com.omnibuds.core.audio.ObservedAudioDevice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Phase 13 (OB-P13-REQ-029): the Android bridge maps device types to
 * transports honestly.
 */
class AndroidAudioQualityBridgeTest {

    private fun deviceOf(type: AudioDeviceType) = ObservedAudioDevice(
        platformDeviceId = 1,
        type = type,
        productName = "Test",
        bluetoothAddress = null,
        direction = AudioDirection.OUTPUT,
        isActive = true,
        source = "test",
    )

    @Test
    fun `BLE headset maps to LE Audio`() {
        assertEquals(
            AudioTransportKind.LE_AUDIO,
            AndroidAudioQualityBridge.transportKindOf(deviceOf(AudioDeviceType.BLE_HEADSET)),
        )
    }

    @Test
    fun `BLE speaker maps to LE Audio`() {
        assertEquals(
            AudioTransportKind.LE_AUDIO,
            AndroidAudioQualityBridge.transportKindOf(deviceOf(AudioDeviceType.BLE_SPEAKER)),
        )
    }

    @Test
    fun `A2DP maps to classic A2DP`() {
        assertEquals(
            AudioTransportKind.CLASSIC_A2DP,
            AndroidAudioQualityBridge.transportKindOf(deviceOf(AudioDeviceType.BLUETOOTH_A2DP)),
        )
    }

    @Test
    fun `SCO maps to HFP`() {
        assertEquals(
            AudioTransportKind.HFP,
            AndroidAudioQualityBridge.transportKindOf(deviceOf(AudioDeviceType.BLUETOOTH_SCO)),
        )
    }

    @Test
    fun `unknown device type maps to unknown transport`() {
        assertEquals(
            AudioTransportKind.UNKNOWN,
            AndroidAudioQualityBridge.transportKindOf(deviceOf(AudioDeviceType.BUILTIN_SPEAKER)),
        )
    }
}
