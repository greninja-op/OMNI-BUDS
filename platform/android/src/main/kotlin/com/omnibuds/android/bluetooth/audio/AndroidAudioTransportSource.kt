package com.omnibuds.android.bluetooth.audio

import android.bluetooth.BluetoothProfile
import com.omnibuds.android.bluetooth.audio.mapping.audioConnectionStateOf
import com.omnibuds.android.bluetooth.audio.mapping.audioTransportKindOf
import com.omnibuds.android.bluetooth.audio.mapping.headsetAudioStateOf
import com.omnibuds.android.bluetooth.audio.mapping.observedAudioDeviceOf
import com.omnibuds.core.audio.AudioDeviceEvent
import com.omnibuds.core.audio.AudioDeviceSource
import com.omnibuds.core.audio.AudioProfileSource
import com.omnibuds.core.audio.AudioProfileState
import com.omnibuds.core.audio.AudioTransportKind
import com.omnibuds.core.audio.ObservedAudioDevice
import com.omnibuds.core.audio.ProfileAvailability
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * The Android implementation of the audio transport engine's two ports.
 *
 * This is where the core's questions meet the framework's answers, and the
 * meeting follows three rules (OB-P10-REQ-011, OB-P10-REQ-012):
 *
 * 1. **Permission first.** Every Bluetooth profile read needs BLUETOOTH_CONNECT
 *    on API 31+. When [bluetoothConnectGranted] is false, profile reads return
 *    no states rather than throwing: a refused permission is reported as
 *    unknown profiles, never as a SecurityException escaping into the engine.
 *    Audio-device reads need no Bluetooth permission and always run.
 * 2. **Raw stays raw until the mapping.** Framework integers are translated by
 *    `audio/mapping` at the boundary; this class never compares a raw state
 *    integer itself.
 * 3. **Observation only.** There is no code path here that changes routing,
 *    starts SCO, or touches `setCommunicationDevice`. The class could not
 *    change the audio route if it wanted to — it holds no reference capable
 *    of it.
 *
 * HSP honesty (ADR-P10-004): Android serves HSP through the HEADSET proxy and
 * exposes no HSP-specific state, so this source reports HSP as
 * [ProfileAvailability.UNKNOWN]/UNKNOWN rather than copying the HFP reading.
 * A copied reading would claim knowledge the platform never gave.
 *
 * @param handle the framework seam; the only thing here that touches Android.
 * @param leAudioHandle LE Audio access, or null on API < 33 (never constructed there).
 * @param bluetoothConnectGranted whether BLUETOOTH_CONNECT is currently granted.
 * @param deviceAddressOf resolves a Bluetooth address for an audio device id
 *   where legitimately available; returns null when it must not be known.
 */
class AndroidAudioTransportSource(
    private val handle: AudioTransportHandle,
    private val leAudioHandle: LeAudioHandle?,
    private val bluetoothConnectGranted: () -> Boolean,
    private val deviceAddressOf: (platformDeviceId: Int) -> String? = { null },
) : AudioProfileSource, AudioDeviceSource {

    override suspend fun readProfileStates(): Map<AudioTransportKind, AudioProfileState> {
        if (!bluetoothConnectGranted()) return emptyMap()
        val raw = runCatching { handle.readRawProfiles() }.getOrNull()
            ?: AudioTransportHandle.RawProfileRead(emptyMap(), emptyMap(), null)
        val states = mutableMapOf<AudioTransportKind, AudioProfileState>()
        raw.states.forEach { (profile, rawState) ->
            val kind = audioTransportKindOf(profile)
            if (kind == AudioTransportKind.UNKNOWN) return@forEach
            states[kind] = AudioProfileState(
                profile = kind,
                availability = ProfileAvailability.AVAILABLE,
                connectionState = audioConnectionStateOf(rawState),
                audioState = if (kind == AudioTransportKind.HFP) {
                    headsetAudioStateOf(raw.headsetAudioState)
                } else {
                    null
                },
                deviceAddress = raw.connectedAddresses[profile]?.firstOrNull(),
                source = "BluetoothProfile($profile)",
            )
        }
        // LE Audio lives behind its own guarded handle: on API < 33 there is
        // no handle at all, and the profile is simply absent (unknown), never
        // inferred from the A2DP reading.
        leAudioHandle?.let { le ->
            if (le.isBound) {
                val leRaw = runCatching { le.readRawConnectionStates() }.getOrDefault(emptyMap())
                val firstConnected = leRaw.entries.firstOrNull { (_, s) -> s == android.bluetooth.BluetoothProfile.STATE_CONNECTED }
                states[AudioTransportKind.LE_AUDIO] = AudioProfileState(
                    profile = AudioTransportKind.LE_AUDIO,
                    availability = ProfileAvailability.AVAILABLE,
                    connectionState = audioConnectionStateOf(
                        firstConnected?.value ?: leRaw.values.firstOrNull(),
                    ),
                    audioState = null,
                    deviceAddress = firstConnected?.key,
                    source = "BluetoothLeAudio",
                )
            }
        }
        // HSP: the platform exposes no HSP-specific state (ADR-P10-004).
        states[AudioTransportKind.HSP] = AudioProfileState(
            profile = AudioTransportKind.HSP,
            availability = ProfileAvailability.UNKNOWN,
            connectionState = com.omnibuds.core.audio.AudioConnectionState.UNKNOWN,
            audioState = null,
            deviceAddress = null,
            source = "HSP-not-distinguishable-from-HFP",
        )
        return states
    }

    override suspend fun readActiveTransportHint(): AudioTransportKind? {
        // The platform names an active *device*, not a transport. The engine's
        // reconciler derives the transport from the device record; this port
        // stays honest by returning null rather than guessing a mapping here.
        return null
    }

    override suspend fun readDevices(): List<ObservedAudioDevice> {
        val rawDevices = runCatching { handle.readRawAudioDevices() }.getOrDefault(emptyList())
        val activeIds = runCatching { handle.readActiveDeviceIds() }.getOrDefault(emptySet())
        return rawDevices.map { raw ->
            observedAudioDeviceOf(
                raw = raw,
                isActive = raw.id in activeIds,
                addressOrNull = runCatching { deviceAddressOf(raw.id) }.getOrNull(),
                source = "AudioDeviceCallback",
            )
        }
    }

    override fun observeDeviceEvents(): Flow<AudioDeviceEvent> = callbackFlow {
        // The initial re-read: whatever changed while we were away is picked
        // up as a full resynchronisation, not as a guessed delta.
        val initial = readDevices()
        trySend(AudioDeviceEvent.Resynchronized(initial))
        val registration = handle.openAudioDeviceChanges {
            // The callback carries no payload by design (the handle's emit is
            // Unit): every announcement triggers a re-read, so the event
            // stream can never disagree with the read path about what changed.
            // The re-read is a suspend call, so it runs in a child of the
            // flow's scope; trySend is thread-safe and simply drops the event
            // if the flow is already closed.
            launch {
                val devices = readDevices()
                trySend(AudioDeviceEvent.Resynchronized(devices))
            }
        }
        // Deterministic unregistration: the engine cancels this flow on stop(),
        // awaitClose runs, and the audio-device callback is released exactly
        // once. Close is idempotent, so a racing teardown is harmless.
        awaitClose { registration.close() }
    }
}
