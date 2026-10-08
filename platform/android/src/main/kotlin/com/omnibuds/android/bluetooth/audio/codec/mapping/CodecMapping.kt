package com.omnibuds.android.bluetooth.audio.codec.mapping

import com.omnibuds.core.audio.Codec

/**
 * Translate platform codec identifiers to domain [Codec].
 *
 * This is the ONLY place where Android codec constants are interpreted.
 * Unknown ids map to null (the caller records UNKNOWN with NOT_OBSERVABLE),
 * never to a guessed codec.
 *
 * Two constant families exist and must not be confused:
 * - `BluetoothCodecType.CODEC_ID_*` (API 35+): long ids.
 * - `BluetoothCodecConfig.SOURCE_CODEC_TYPE_*` (API 33+): int ids.
 */

/** `BluetoothCodecType.CODEC_ID_*` values from API 35 (verified via android.jar). */
private const val CODEC_ID_SBC: Long = 0L
private const val CODEC_ID_AAC: Long = 2L
private const val CODEC_ID_APTX: Long = 16797695L
private const val CODEC_ID_APTX_HD: Long = 604035071L
private const val CODEC_ID_LDAC: Long = -1442763265L
private const val CODEC_ID_OPUS: Long = 16834815L

/**
 * Translate a `BluetoothCodecType.getCodecId()` value.
 * Returns null for unrecognized ids — including ids for codecs the domain
 * knows (aptX Adaptive, aptX Lossless, LC3) but the platform family does not
 * define. Null means "untranslatable", not "unsupported".
 */
fun codecFromCodecId(codecId: Long): Codec? = when (codecId) {
    CODEC_ID_SBC -> Codec.SBC
    CODEC_ID_AAC -> Codec.AAC
    CODEC_ID_APTX -> Codec.APTX
    CODEC_ID_APTX_HD -> Codec.APTX_HD
    CODEC_ID_LDAC -> Codec.LDAC
    CODEC_ID_OPUS -> Codec.OPUS
    else -> null
}

/** `BluetoothCodecConfig.SOURCE_CODEC_TYPE_*` values (verified via android.jar). */
private const val SOURCE_CODEC_TYPE_SBC: Long = 0L
private const val SOURCE_CODEC_TYPE_AAC: Long = 1L
private const val SOURCE_CODEC_TYPE_APTX: Long = 2L
private const val SOURCE_CODEC_TYPE_APTX_HD: Long = 3L
private const val SOURCE_CODEC_TYPE_LDAC: Long = 4L
private const val SOURCE_CODEC_TYPE_LC3: Long = 5L
private const val SOURCE_CODEC_TYPE_OPUS: Long = 6L

/**
 * Translate a `BluetoothCodecConfig.getCodecType()` value.
 * LC3 maps to [Codec.LC3] (LE Audio family) — never to an A2DP codec.
 */
fun codecFromSourceCodecType(codecType: Long): Codec? = when (codecType) {
    SOURCE_CODEC_TYPE_SBC -> Codec.SBC
    SOURCE_CODEC_TYPE_AAC -> Codec.AAC
    SOURCE_CODEC_TYPE_APTX -> Codec.APTX
    SOURCE_CODEC_TYPE_APTX_HD -> Codec.APTX_HD
    SOURCE_CODEC_TYPE_LDAC -> Codec.LDAC
    SOURCE_CODEC_TYPE_LC3 -> Codec.LC3
    SOURCE_CODEC_TYPE_OPUS -> Codec.OPUS
    else -> null
}
