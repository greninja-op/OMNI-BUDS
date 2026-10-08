package com.omnibuds.android.bluetooth.audio.codec

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The narrow seam between the codec observation source and the Android framework.
 *
 * Read-only by construction: it exposes what the platform reports, never a
 * setter. All methods are safe to call from any thread; binder work is
 * confined to this implementation.
 *
 * Honest API reality (verified against android.jar API 35 + api-versions.xml):
 *
 * - API 35+: [BluetoothA2dp.getSupportedCodecTypes] reports the *local* phone's
 *   supported codec types. This is local capability, not per-device state.
 * - API 33–34: [android.bluetooth.BluetoothCodecConfig] exists publicly, but no
 *   public API returns instances of it — the active codec is NOT_OBSERVABLE.
 * - Below API 33: no public codec API at all — NOT_OBSERVABLE.
 * - [android.bluetooth.BluetoothLeAudio] exposes no public codec-status getter
 *   at any API level — LE Audio runtime codec state is NOT_OBSERVABLE.
 */
interface CodecObservationHandle {
    /**
     * Read the local device's supported codec types as raw platform ids.
     * Empty when the API is unavailable, permission is missing, or the read fails.
     */
    suspend fun readLocalSupportedCodecIds(): List<RawCodecInfo>
}

/**
 * System implementation of [CodecObservationHandle].
 *
 * Binds the A2DP profile for one read and releases it before returning —
 * proxies are never held across reads.
 */
class SystemCodecObservationHandle(
    private val context: Context,
    private val adapter: BluetoothAdapter?,
) : CodecObservationHandle {

    override suspend fun readLocalSupportedCodecIds(): List<RawCodecInfo> =
        withContext(Dispatchers.IO) {
            // getSupportedCodecTypes() is API 35+; below that the platform
            // exposes no public codec list — empty means "unobservable", never
            // "unsupported". The API-35 code lives in CodecApi35 so the class
            // verifier never resolves BluetoothCodecType on older runtimes.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                return@withContext emptyList()
            }
            val localAdapter = adapter ?: return@withContext emptyList()
            // BLUETOOTH_CONNECT is required; without it we return empty rather
            // than throwing — the source translates that to NOT_OBSERVABLE.
            if (!hasBluetoothConnect()) return@withContext emptyList()
            runCatching {
                CodecApi35(context).readLocalSupportedCodecIds(localAdapter)
            }.getOrDefault(emptyList())
        }

    private fun hasBluetoothConnect(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}
