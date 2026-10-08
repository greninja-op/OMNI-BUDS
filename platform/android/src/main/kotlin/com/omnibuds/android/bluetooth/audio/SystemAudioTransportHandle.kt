package com.omnibuds.android.bluetooth.audio

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The production [AudioTransportHandle]: the thin edge that touches the framework.
 *
 * This class is deliberately boring. It binds profile proxies for one read at
 * a time, extracts primitives from `AudioDeviceInfo`, forwards raw integers
 * untouched, and registers/unregisters the `AudioDeviceCallback` — and nothing
 * else. All interpretation lives in `audio/mapping` and all policy lives in
 * `:core`. That split is what makes everything above this class testable on a
 * JVM with no radio and no audio hardware; what stays unproven here is the
 * framework call itself, and `docs/phases/phase-10/validation.md` says so
 * rather than implying the unit suite covers it (the Phase 2 convention,
 * ADR-P2-003).
 *
 * Profile-proxy lifecycle: [readRawProfiles] binds A2DP and HEADSET, waits
 * for the async binds (bounded), reads states and addresses, then releases
 * both proxies before returning. Proxies are never held across reads, so
 * there is no IPC binding to leak if the observer is torn down mid-read. A
 * bind that does not complete within [BIND_TIMEOUT_MS] yields unknown for
 * that profile — a slow binder is not evidence of anything.
 *
 * Threading: binder calls run on the calling thread; the source calls this
 * handle off the main thread. `AudioManager` callbacks arrive on the main
 * looper (or [callbackHandler]); the callback does no work itself, it just
 * calls `emit` and the source re-reads on its own dispatcher.
 *
 * Permissions: profile-proxy reads require BLUETOOTH_CONNECT on API 31+; the
 * *source* above this handle checks standing before calling. A
 * SecurityException mid-read becomes unknown readings, never a crash.
 * Audio-device reads need no Bluetooth permission. This class never requests
 * anything, and it never touches RECORD_AUDIO.
 */
class SystemAudioTransportHandle(
    private val context: Context,
    private val audioManager: AudioManager,
    private val adapter: BluetoothAdapter?,
    private val callbackHandler: Handler? = null,
) : AudioTransportHandle {

    override fun readRawProfiles(): AudioTransportHandle.RawProfileRead {
        val a = adapter ?: return AudioTransportHandle.RawProfileRead(emptyMap(), emptyMap(), null)
        val states = mutableMapOf<Int, Int?>()
        val addresses = mutableMapOf<Int, List<String>>()
        var headsetAudioState: Int? = null
        try {
            bind(a, BluetoothProfile.A2DP)?.let { proxy ->
                try {
                    val a2dp = proxy as BluetoothA2dp
                    val connected = runCatching { a2dp.connectedDevices }.getOrDefault(emptyList())
                    states[BluetoothProfile.A2DP] = if (connected.isNotEmpty()) {
                        BluetoothProfile.STATE_CONNECTED
                    } else {
                        BluetoothProfile.STATE_DISCONNECTED
                    }
                    addresses[BluetoothProfile.A2DP] =
                        connected.mapNotNull { runCatching { it.address }.getOrNull() }
                } finally {
                    runCatching { a.closeProfileProxy(BluetoothProfile.A2DP, proxy) }
                }
            }
            bind(a, BluetoothProfile.HEADSET)?.let { proxy ->
                try {
                    val headset = proxy as BluetoothHeadset
                    val connected = runCatching { headset.connectedDevices }.getOrDefault(emptyList())
                    states[BluetoothProfile.HEADSET] = if (connected.isNotEmpty()) {
                        BluetoothProfile.STATE_CONNECTED
                    } else {
                        BluetoothProfile.STATE_DISCONNECTED
                    }
                    addresses[BluetoothProfile.HEADSET] =
                        connected.mapNotNull { runCatching { it.address }.getOrNull() }
                    // SCO audio state is per-device; report the first
                    // connected device's audio state where available.
                    // isAudioConnected is the public SCO query; true maps to
                    // the platform's STATE_CONNECTED audio value.
                    headsetAudioState = connected.firstOrNull()?.let { device ->
                        runCatching {
                            if (headset.isAudioConnected(device)) {
                                BluetoothProfile.STATE_CONNECTED
                            } else {
                                BluetoothProfile.STATE_DISCONNECTED
                            }
                        }.getOrNull()
                    }
                } finally {
                    runCatching { a.closeProfileProxy(BluetoothProfile.HEADSET, proxy) }
                }
            }
        } catch (e: SecurityException) {
            // Permission revoked mid-read: whatever was gathered stands, the
            // rest stays unknown. The source maps absence to unknown profiles.
        }
        return AudioTransportHandle.RawProfileRead(states, addresses, headsetAudioState)
    }

    /**
     * Binds one profile proxy, waiting at most [BIND_TIMEOUT_MS] for the
     * async service connection. Returns null when the bind failed or timed
     * out; the caller treats null as "profile not answerable".
     */
    private fun bind(adapter: BluetoothAdapter, profile: Int): BluetoothProfile? {
        val latch = CountDownLatch(1)
        val box = arrayOfNulls<BluetoothProfile>(1)
        val bound = try {
            adapter.getProfileProxy(
                context,
                object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                        box[0] = proxy
                        latch.countDown()
                    }

                    override fun onServiceDisconnected(p: Int) = Unit
                },
                profile,
            )
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            false
        }
        if (!bound) return null
        latch.await(BIND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return box[0]
    }

    override fun readRawAudioDevices(): List<RawAudioDevice> {
        val infos = runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_ALL)
        }.getOrDefault(emptyArray())
        return infos.map { info: AudioDeviceInfo ->
            RawAudioDevice(
                id = info.id,
                rawType = info.type,
                productName = runCatching { info.productName?.toString() }.getOrNull(),
                isSink = info.isSink,
                isSource = info.isSource,
            )
        }
    }

    override fun readActiveDeviceIds(): Set<Int> {
        // The platform exposes the active *communication* device (API 31+) but
        // no "active media device" id. Rather than infer activity from the
        // device list, this edge reports what the platform names and nothing
        // more; the reconciler treats an empty set as "unreported".
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val device = audioManager.communicationDevice
                if (device != null) setOf(device.id) else emptySet()
            } else {
                emptySet()
            }
        }.getOrDefault(emptySet())
    }

    override fun openAudioDeviceChanges(emit: () -> Unit): AutoCloseable {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                emit()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                emit()
            }
        }
        audioManager.registerAudioDeviceCallback(callback, callbackHandler ?: Handler(Looper.getMainLooper()))
        var closed = false
        return AutoCloseable {
            // Idempotent: a racing teardown closes once.
            if (!closed) {
                closed = true
                runCatching { audioManager.unregisterAudioDeviceCallback(callback) }
            }
        }
    }

    companion object {
        /** A binder that has not answered by now is not going to; unknown, not a hang. */
        const val BIND_TIMEOUT_MS: Long = 800L
    }
}
