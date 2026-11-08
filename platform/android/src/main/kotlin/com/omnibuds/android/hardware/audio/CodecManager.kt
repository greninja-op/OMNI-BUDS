@file:Suppress("DEPRECATION")

package com.omnibuds.android.hardware.audio

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CodecDetails(
    val codecName: String,
    val sampleRateHz: Int,
    val bitsPerSample: Int,
    val isHiRes: Boolean,
)

/**
 * Real-Time Bluetooth A2DP Codec and Bitrate Switcher.
 * Queries [BluetoothCodecStatus] and dynamically configures LDAC/LHDC Hi-Res and High-Bitrate AAC.
 */
class CodecManager(private val context: Context) {

    companion object {
        private const val TAG = "CodecManager"

        // Android BluetoothCodecConfig Constants
        const val SOURCE_CODEC_TYPE_SBC = 0
        const val SOURCE_CODEC_TYPE_AAC = 1
        const val SOURCE_CODEC_TYPE_APTX = 2
        const val SOURCE_CODEC_TYPE_APTX_HD = 3
        const val SOURCE_CODEC_TYPE_LDAC = 4
        const val SOURCE_CODEC_TYPE_LC3 = 5
        const val SOURCE_CODEC_TYPE_OPUS = 6
        const val SOURCE_CODEC_TYPE_LHDC = 10
    }

    private var a2dpProxy: BluetoothA2dp? = null

    private val _activeCodecState = MutableStateFlow<Map<String, CodecDetails>>(emptyMap())
    val activeCodecState: StateFlow<Map<String, CodecDetails>> = _activeCodecState.asStateFlow()

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.A2DP) {
                a2dpProxy = proxy as? BluetoothA2dp
                Log.i(TAG, "BluetoothA2dp proxy bound successfully")
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.A2DP) {
                a2dpProxy = null
            }
        }
    }

    init {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            adapter?.getProfileProxy(context.applicationContext, profileListener, BluetoothProfile.A2DP)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get A2DP profile proxy: ${e.message}")
        }
    }

    /**
     * Inspects active BluetoothCodecStatus for [device] via reflection on [BluetoothA2dp].
     */
    @SuppressLint("MissingPermission")
    fun inspectCodec(device: BluetoothDevice): CodecDetails? {
        val proxy = a2dpProxy ?: return null
        return try {
            val getCodecStatusMethod = proxy.javaClass.getMethod("getCodecStatus", BluetoothDevice::class.java)
            val codecStatus = getCodecStatusMethod.invoke(proxy, device) ?: return null

            val getCodecConfigMethod = codecStatus.javaClass.getMethod("getCodecConfig")
            val codecConfig = getCodecConfigMethod.invoke(codecStatus) ?: return null

            val getCodecTypeMethod = codecConfig.javaClass.getMethod("getCodecType")
            val codecType = getCodecTypeMethod.invoke(codecConfig) as? Int ?: 0

            val getSampleRateMethod = codecConfig.javaClass.getMethod("getSampleRate")
            val sampleRateRaw = getSampleRateMethod.invoke(codecConfig) as? Int ?: 0

            val getBitsMethod = codecConfig.javaClass.getMethod("getBitsPerSample")
            val bitsRaw = getBitsMethod.invoke(codecConfig) as? Int ?: 0

            val codecName = when (codecType) {
                SOURCE_CODEC_TYPE_SBC -> "SBC"
                SOURCE_CODEC_TYPE_AAC -> "AAC HD"
                SOURCE_CODEC_TYPE_APTX -> "aptX"
                SOURCE_CODEC_TYPE_APTX_HD -> "aptX HD"
                SOURCE_CODEC_TYPE_LDAC -> "LDAC 990kbps"
                SOURCE_CODEC_TYPE_LC3 -> "LC3 / LE Audio"
                SOURCE_CODEC_TYPE_OPUS -> "Opus"
                SOURCE_CODEC_TYPE_LHDC -> "LHDC 5.0"
                else -> "AAC"
            }

            val sampleRateHz = when (sampleRateRaw) {
                1 -> 44100
                2 -> 48000
                4 -> 88200
                8 -> 96000
                16 -> 192000
                else -> 48000
            }

            val bits = when (bitsRaw) {
                1 -> 16
                2 -> 24
                4 -> 32
                else -> 24
            }

            val isHiRes = codecType == SOURCE_CODEC_TYPE_LDAC || codecType == SOURCE_CODEC_TYPE_LHDC || sampleRateHz >= 96000

            val details = CodecDetails(codecName, sampleRateHz, bits, isHiRes)
            _activeCodecState.value = _activeCodecState.value + (device.address to details)
            details
        } catch (e: Exception) {
            Log.d(TAG, "Codec status query fallback: ${e.message}")
            // Fallback: Default to AAC 48kHz 24-bit
            val fallback = CodecDetails("AAC", 48000, 24, false)
            _activeCodecState.value = _activeCodecState.value + (device.address to fallback)
            fallback
        }
    }

    /**
     * Toggles Hi-Res Mode (LDAC 96kHz / 24-bit or LHDC 5.0).
     */
    @SuppressLint("MissingPermission")
    fun setHiResMode(device: BluetoothDevice, enabled: Boolean): Boolean {
        val proxy = a2dpProxy ?: return false
        return try {
            val codecType = if (enabled) SOURCE_CODEC_TYPE_LDAC else SOURCE_CODEC_TYPE_AAC
            val sampleRate = if (enabled) 8 /* 96kHz */ else 2 /* 48kHz */
            val bits = if (enabled) 2 /* 24-bit */ else 1 /* 16-bit */

            val codecConfigClass = Class.forName("android.bluetooth.BluetoothCodecConfig")
            val constructor = codecConfigClass.getConstructor(
                Int::class.javaPrimitiveType, // codecType
                Int::class.javaPrimitiveType, // codecPriority
                Int::class.javaPrimitiveType, // sampleRate
                Int::class.javaPrimitiveType, // bitsPerSample
                Int::class.javaPrimitiveType, // channelMode
                Long::class.javaPrimitiveType, // codecSpecific1
                Long::class.javaPrimitiveType, // codecSpecific2
                Long::class.javaPrimitiveType, // codecSpecific3
                Long::class.javaPrimitiveType, // codecSpecific4
            )
            val newConfig = constructor.newInstance(codecType, 1000000, sampleRate, bits, 1 /* Stereo */, 0L, 0L, 0L, 0L)

            val setMethod = proxy.javaClass.getMethod(
                "setCodecConfigPreference",
                BluetoothDevice::class.java,
                codecConfigClass,
            )
            setMethod.invoke(proxy, device, newConfig)

            inspectCodec(device)
            Log.i(TAG, "Set Hi-Res Codec Preference to enabled=$enabled for ${device.address}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set Hi-Res codec config: ${e.message}")
            false
        }
    }

    /**
     * Prevents low-bitrate SBC degradation by prioritizing High-Bitrate AAC (256/320 kbps).
     */
    @SuppressLint("MissingPermission")
    fun forceHighBitrateAac(device: BluetoothDevice, enabled: Boolean): Boolean {
        val proxy = a2dpProxy ?: return false
        return try {
            val priority = if (enabled) 1000000 else 1000
            val codecConfigClass = Class.forName("android.bluetooth.BluetoothCodecConfig")
            val constructor = codecConfigClass.getConstructor(
                Int::class.javaPrimitiveType, // codecType
                Int::class.javaPrimitiveType, // codecPriority
                Int::class.javaPrimitiveType, // sampleRate
                Int::class.javaPrimitiveType, // bitsPerSample
                Int::class.javaPrimitiveType, // channelMode
                Long::class.javaPrimitiveType, // codecSpecific1
                Long::class.javaPrimitiveType, // codecSpecific2
                Long::class.javaPrimitiveType, // codecSpecific3
                Long::class.javaPrimitiveType, // codecSpecific4
            )
            val newConfig = constructor.newInstance(SOURCE_CODEC_TYPE_AAC, priority, 2 /* 48kHz */, 2 /* 24-bit */, 1 /* Stereo */, 0L, 0L, 0L, 0L)

            val setMethod = proxy.javaClass.getMethod(
                "setCodecConfigPreference",
                BluetoothDevice::class.java,
                codecConfigClass,
            )
            setMethod.invoke(proxy, device, newConfig)
            inspectCodec(device)
            Log.i(TAG, "Force High-Bitrate AAC set: $enabled for ${device.address}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to force AAC: ${e.message}")
            false
        }
    }

    fun release() {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            a2dpProxy?.let { adapter?.closeProfileProxy(BluetoothProfile.A2DP, it) }
        } catch (_: Exception) {}
        a2dpProxy = null
    }
}
