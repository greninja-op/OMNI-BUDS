package com.omnibuds.android.bluetooth.audio.mapping

import android.bluetooth.BluetoothProfile
import android.media.AudioDeviceInfo
import com.omnibuds.android.bluetooth.audio.RawAudioDevice
import com.omnibuds.core.audio.AudioConnectionState
import com.omnibuds.core.audio.AudioDeviceType
import com.omnibuds.core.audio.AudioDirection
import com.omnibuds.core.audio.AudioTransportKind
import com.omnibuds.core.audio.ObservedAudioDevice

/**
 * Translates Android's audio numbers into the domain's audio vocabulary.
 *
 * This is the only place in the codebase permitted to know what
 * `BluetoothProfile.STATE_CONNECTED` or `AudioDeviceInfo.TYPE_BLE_HEADSET`
 * means. Everything above it deals in [AudioConnectionState] and
 * [ObservedAudioDevice]; everything below it deals in raw framework values.
 * That split is what makes the engine testable on a JVM with no radio and no
 * audio hardware.
 *
 * Unrecognised input becomes UNKNOWN, never a friendlier-looking state. A
 * connection state we did not read is not a state we may report
 * (ADR-P0-016): "disconnected" sends a user to Settings to fix a link that
 * may never have been queried.
 */

/** Maps a `BluetoothProfile.getConnectionState()` integer to the domain state. */
fun audioConnectionStateOf(rawState: Int?): AudioConnectionState = when (rawState) {
    null -> AudioConnectionState.UNKNOWN
    BluetoothProfile.STATE_CONNECTED -> AudioConnectionState.CONNECTED
    BluetoothProfile.STATE_CONNECTING -> AudioConnectionState.CONNECTING
    BluetoothProfile.STATE_DISCONNECTING -> AudioConnectionState.DISCONNECTING
    BluetoothProfile.STATE_DISCONNECTED -> AudioConnectionState.DISCONNECTED
    else -> AudioConnectionState.UNKNOWN
}

/**
 * Maps a `BluetoothHeadset.getAudioState()` integer to the domain state.
 * SCO audio "connected" means the call-audio path is up — the closest the
 * platform gets to ACTIVE for HFP — but it is still not "a call is in
 * progress", and this function does not claim otherwise.
 */
fun headsetAudioStateOf(rawAudioState: Int?): AudioConnectionState? = when (rawAudioState) {
    null -> null
    BluetoothProfile.STATE_CONNECTED -> AudioConnectionState.ACTIVE
    BluetoothProfile.STATE_CONNECTING -> AudioConnectionState.CONNECTING
    BluetoothProfile.STATE_DISCONNECTING -> AudioConnectionState.DISCONNECTING
    BluetoothProfile.STATE_DISCONNECTED -> AudioConnectionState.DISCONNECTED
    else -> AudioConnectionState.UNKNOWN
}

/** Maps an `AudioDeviceInfo.getType()` integer to the domain device type. */
fun audioDeviceTypeOf(rawType: Int): AudioDeviceType = when (rawType) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> AudioDeviceType.BLUETOOTH_A2DP
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> AudioDeviceType.BLUETOOTH_SCO
    AudioDeviceInfo.TYPE_BLE_HEADSET -> AudioDeviceType.BLE_HEADSET
    AudioDeviceInfo.TYPE_BLE_SPEAKER -> AudioDeviceType.BLE_SPEAKER
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioDeviceType.BUILTIN_SPEAKER
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> AudioDeviceType.BUILTIN_EARPIECE
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    -> AudioDeviceType.WIRED_HEADSET
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    -> AudioDeviceType.USB_DEVICE
    else -> AudioDeviceType.UNKNOWN
}

/**
 * Translates one raw audio device into an [ObservedAudioDevice].
 *
 * Direction comes from `isSink`/`isSource` — never from the device type —
 * because the type-to-direction shortcut is exactly the hardcoded assumption
 * OB-P10-REQ-007 forbids. The address is taken only when [addressOrNull]
 * supplies it; the mapping never reaches for it itself, because reading a
 * Bluetooth MAC without BLUETOOTH_CONNECT is both a SecurityException and a
 * privacy defect — the caller decides what it is allowed to know.
 *
 * @param isActive whether the platform currently routes through this device.
 * @param addressOrNull the Bluetooth address where legitimately available.
 */
fun observedAudioDeviceOf(
    raw: RawAudioDevice,
    isActive: Boolean,
    addressOrNull: String?,
    source: String,
): ObservedAudioDevice {
    val direction = when {
        raw.isSink && raw.isSource -> AudioDirection.BIDIRECTIONAL
        raw.isSink -> AudioDirection.OUTPUT
        raw.isSource -> AudioDirection.INPUT
        else -> AudioDirection.UNKNOWN
    }
    return ObservedAudioDevice(
        platformDeviceId = raw.id,
        type = audioDeviceTypeOf(raw.rawType),
        productName = raw.productName?.takeIf { it.isNotBlank() },
        bluetoothAddress = addressOrNull,
        direction = direction,
        isActive = isActive,
        source = source,
    )
}

/** Which domain profile a Bluetooth profile integer belongs to. */
fun audioTransportKindOf(bluetoothProfile: Int): AudioTransportKind = when (bluetoothProfile) {
    BluetoothProfile.A2DP -> AudioTransportKind.CLASSIC_A2DP
    BluetoothProfile.HEADSET -> AudioTransportKind.HFP
    // No BluetoothProfile.HSP exists: Android serves HSP through the HEADSET
    // proxy, so HSP is never produced by this mapping (ADR-P10-004).
    BluetoothProfile.LE_AUDIO -> AudioTransportKind.LE_AUDIO
    else -> AudioTransportKind.UNKNOWN
}
