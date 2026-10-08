package com.omnibuds.android.bluetooth.audio.codec

/**
 * One codec as raw platform primitives.
 *
 * The mapping layer translates this — never the framework object — so the
 * translation stays unit-testable on a JVM with no Bluetooth hardware.
 */
data class RawCodecInfo(
    /**
     * Platform codec identifier: a `BluetoothCodecType.CODEC_ID_*` long on
     * API 35+, or a `BluetoothCodecConfig.SOURCE_CODEC_TYPE_*` int widened to
     * long on API 33–34. [CodecIdKind] says which.
     */
    val platformCodecId: Long,
    /** Which platform constant family [platformCodecId] came from. */
    val idKind: CodecIdKind,
    /** True when this codec was reported as locally supported. */
    val isLocallySupported: Boolean,
)

/**
 * Which platform constant family a raw codec id came from.
 *
 * The two families overlap (SBC=0 in both) but diverge (LC3=5 exists only in
 * `SOURCE_CODEC_TYPE_*`; aptX=16797695 only in `CODEC_ID_*`), so the mapping
 * must know which family it is translating.
 */
enum class CodecIdKind {
    /** `BluetoothCodecType.CODEC_ID_*` (API 35+). */
    CODEC_ID,

    /** `BluetoothCodecConfig.SOURCE_CODEC_TYPE_*` (API 33+). */
    SOURCE_CODEC_TYPE,
}
