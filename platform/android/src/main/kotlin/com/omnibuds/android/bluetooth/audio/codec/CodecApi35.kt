package com.omnibuds.android.bluetooth.audio.codec

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.omnibuds.android.bluetooth.audio.codec.mapping.codecFromCodecId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * API 35+ codec observation, isolated so the class verifier never resolves
 * [android.bluetooth.BluetoothCodecType] on older runtimes.
 *
 * Loaded only when `Build.VERSION.SDK_INT >= 35`; the constructor throws
 * otherwise, so the absence of an instance is the "unavailable" answer
 * (ADR-P10-005 pattern).
 */
class CodecApi35(context: Context) {
    private val appContext: Context = context.applicationContext

    init {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            "CodecApi35 requires API 35+"
        }
    }

    /**
     * Read the local phone's supported A2DP codec types. One binder session:
     * bind, read, release. Returns empty on timeout, permission denial, or
     * read failure — empty means "unobservable", never "unsupported".
     */
    fun readLocalSupportedCodecIds(adapter: android.bluetooth.BluetoothAdapter): List<RawCodecInfo> {
        val latch = CountDownLatch(1)
        var proxy: BluetoothA2dp? = null
        var result: List<RawCodecInfo> = emptyList()
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, p: BluetoothProfile) {
                proxy = p as? BluetoothA2dp
                result = runCatching {
                    (p as? BluetoothA2dp)
                        ?.getSupportedCodecTypes()
                        ?.mapNotNull { codecType ->
                            if (codecFromCodecId(codecType.codecId) != null) {
                                RawCodecInfo(
                                    platformCodecId = codecType.codecId,
                                    idKind = CodecIdKind.CODEC_ID,
                                    isLocallySupported = true,
                                )
                            } else {
                                null
                            }
                        }
                        .orEmpty()
                }.getOrDefault(emptyList())
                latch.countDown()
            }

            override fun onServiceDisconnected(profile: Int) {
                latch.countDown()
            }
        }
        val bound = try {
            adapter.getProfileProxy(appContext, listener, BluetoothProfile.A2DP)
        } catch (e: SecurityException) {
            false
        }
        return try {
            if (bound) latch.await(800, TimeUnit.MILLISECONDS)
            result
        } finally {
            try {
                adapter.closeProfileProxy(BluetoothProfile.A2DP, proxy)
            } catch (ignored: Exception) {
            }
        }
    }
}
