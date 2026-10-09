package com.omnibuds.android.bluetooth.audio.quality

import com.omnibuds.core.audio.AudioDeviceType
import com.omnibuds.core.audio.AudioTransportEngine
import com.omnibuds.core.audio.AudioTransportKind
import com.omnibuds.core.audio.ObservedAudioDevice
import com.omnibuds.core.codec.CodecCapabilityEngine
import com.omnibuds.core.codec.CodecControlEngine
import com.omnibuds.core.device.DeviceIdentity
import com.omnibuds.core.quality.AudioQualityEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Wires the Phase 10/11/12 engines into the Phase 13 [AudioQualityEngine].
 *
 * Phase 13 (OB-P13-REQ-029): observation only, public APIs only. This bridge
 * collects the transport, codec capability, and codec control flows and feeds
 * the quality engine. It invents nothing:
 *
 * - Transport kind comes from the [ObservedAudioDevice.type].
 * - The [DeviceIdentity] correlation is caller-supplied ([correlate]); when a
 *   device cannot be correlated, its codec fields stay UNKNOWN rather than
 *   guessed.
 * - A device that disappears from the transport snapshot is reported as
 *   disconnected to the quality engine.
 *
 * No hidden APIs, no reflection, no shell.
 */
class AndroidAudioQualityBridge(
    private val transportEngine: AudioTransportEngine,
    private val codecEngine: CodecCapabilityEngine,
    private val controlEngine: CodecControlEngine,
    private val qualityEngine: AudioQualityEngine,
    private val correlate: (ObservedAudioDevice) -> DeviceIdentity?,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var started = false

    /** Start collecting the engine flows into the quality engine. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(
                transportEngine.snapshots,
                codecEngine.snapshots,
                controlEngine.states,
            ) { transport, codecs, controls ->
                Triple(transport, codecs, controls)
            }.collect { (transport, codecs, controls) ->
                val seen = mutableSetOf<DeviceIdentity>()
                for (observed in transport.devices) {
                    val device = correlate(observed) ?: continue
                    seen += device
                    qualityEngine.onTransportUpdate(
                        device = device,
                        transport = transportKindOf(observed),
                        routeActive = observed.isActive,
                        transportConnected = true,
                    )
                    qualityEngine.onCodecSnapshot(device, codecs[device])
                    qualityEngine.onControlState(device, controls[device])
                }
                // Devices that vanished from the transport snapshot: report
                // disconnect so sessions terminate and state goes stale.
                val previous = qualityEngine.states.value.keys
                for (device in previous - seen) {
                    qualityEngine.onDisconnected(device)
                }
            }
        }
    }

    /** Stop collecting. The quality engine itself is left running. */
    fun stop() {
        scope.cancel()
    }

    companion object {
        /**
         * Derive the transport kind from the observed device type.
         * LE Audio devices map to LE_AUDIO; everything else Bluetooth maps to
         * A2DP unless the type says otherwise. Unknown stays unknown.
         */
        fun transportKindOf(device: ObservedAudioDevice): AudioTransportKind = when (device.type) {
            AudioDeviceType.BLE_HEADSET, AudioDeviceType.BLE_SPEAKER -> AudioTransportKind.LE_AUDIO
            AudioDeviceType.BLUETOOTH_A2DP -> AudioTransportKind.CLASSIC_A2DP
            AudioDeviceType.BLUETOOTH_SCO -> AudioTransportKind.HFP
            else -> AudioTransportKind.UNKNOWN
        }
    }
}
