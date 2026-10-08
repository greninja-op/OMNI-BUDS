package com.omnibuds.android.bluetooth.audio

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothLeAudio
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build

/**
 * The API-33-only LE Audio handle implementation.
 *
 * This is the single class in the codebase that references
 * `android.bluetooth.BluetoothLeAudio`, and it is annotated [RequiresApi] and
 * instantiated only behind the [com.omnibuds.core.audio.leAudioSupport] guard
 * (ADR-P10-005, OB-P10-REQ-019). On API < 33 this class is never loaded, so
 * the verifier never attempts to resolve `BluetoothLeAudio` and there is no
 * `NoClassDefFoundError` / `VerifyError` — the absence of an instance *is* the
 * "LE Audio unavailable" answer the engine reconciles.
 *
 * The proxy lifecycle follows the platform contract exactly: `getProfileProxy`
 * binds asynchronously through [BluetoothProfile.ServiceListener], and
 * [release] calls `closeProfileProxy`. A leaked LE Audio proxy holds an IPC
 * binding open for the life of the process; the owner of this handle (the
 * audio source's host) must call [release] deterministically, mirroring the
 * engine's own stop() guarantee.
 *
 * Nothing here configures LE Audio, joins groups, or touches broadcast audio.
 * The reads below are connection-state observations; group/broadcast
 * membership is deliberately not surfaced in Phase 10.
 *
 * API-33 ONLY: instantiating this class on an older API level throws. The
 * host must check [com.omnibuds.core.audio.leAudioSupport] first; the check
 * in `init` is the backstop, not the plan.
 */
class LeAudioApi33(
    private val context: Context,
    private val adapter: BluetoothAdapter,
) : LeAudioHandle {

    init {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            "LeAudioApi33 requires API 33+; check leAudioSupport() before instantiating"
        }
    }

    @Volatile
    private var proxy: BluetoothLeAudio? = null

    @Volatile
    private var bound = false

    override val isBound: Boolean get() = bound && proxy != null

    /**
     * Binds the LE Audio profile proxy. Suspends until the service connects or
     * the bind fails; returns false when the proxy could not be obtained.
     * Must be called before [readRawConnectionStates].
     */
    suspend fun bind(): Boolean {
        if (isBound) return true
        return suspendCancellableBind()
    }

    private suspend fun suspendCancellableBind(): Boolean =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val listener = object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    this@LeAudioApi33.proxy = proxy as BluetoothLeAudio
                    bound = true
                    cont.resume(true) { _, _, _ -> }
                }

                override fun onServiceDisconnected(profile: Int) {
                    bound = false
                    this@LeAudioApi33.proxy = null
                    if (cont.isActive) cont.resume(false) { _, _, _ -> }
                }
            }
            val ok = adapter.getProfileProxy(context, listener, BluetoothProfile.LE_AUDIO)
            if (!ok && cont.isActive) cont.resume(false) { _, _, _ -> }
            cont.invokeOnCancellation {
                runCatching { adapter.closeProfileProxy(BluetoothProfile.LE_AUDIO, proxy) }
                bound = false
                proxy = null
            }
        }

    override fun readRawConnectionStates(): Map<String, Int?> {
        val p = proxy ?: return emptyMap()
        return runCatching {
            p.connectedDevices.associate { device: BluetoothDevice ->
                device.address to runCatching { p.getConnectionState(device) }.getOrNull()
            }
        }.getOrDefault(emptyMap())
    }

    /** Releases the proxy. Idempotent; safe to call more than once. */
    fun release() {
        val p = proxy
        proxy = null
        bound = false
        if (p != null) {
            runCatching { adapter.closeProfileProxy(BluetoothProfile.LE_AUDIO, p) }
        }
    }
}
