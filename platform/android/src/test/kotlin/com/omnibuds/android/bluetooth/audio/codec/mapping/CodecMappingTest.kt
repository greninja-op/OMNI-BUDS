package com.omnibuds.android.bluetooth.audio.codec.mapping

import com.omnibuds.core.audio.Codec
import com.omnibuds.core.audio.CodecFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Phase 11 §34, §37: platform codec ids translate to domain identities;
 * unknown ids translate to null (never a guess); LC3 never becomes A2DP.
 */
class CodecMappingTest {

    @Test
    fun codecIdsMapToDomainCodecs() {
        assertEquals(Codec.SBC, codecFromCodecId(0L))
        assertEquals(Codec.AAC, codecFromCodecId(2L))
        assertEquals(Codec.APTX, codecFromCodecId(16797695L))
        assertEquals(Codec.APTX_HD, codecFromCodecId(604035071L))
        assertEquals(Codec.LDAC, codecFromCodecId(-1442763265L))
        assertEquals(Codec.OPUS, codecFromCodecId(16834815L))
    }

    @Test
    fun sourceCodecTypesMapToDomainCodecs() {
        assertEquals(Codec.SBC, codecFromSourceCodecType(0L))
        assertEquals(Codec.AAC, codecFromSourceCodecType(1L))
        assertEquals(Codec.APTX, codecFromSourceCodecType(2L))
        assertEquals(Codec.APTX_HD, codecFromSourceCodecType(3L))
        assertEquals(Codec.LDAC, codecFromSourceCodecType(4L))
    }

    @Test
    fun lc3MapsToLeAudioFamily() {
        val codec = codecFromSourceCodecType(5L)
        assertEquals(Codec.LC3, codec)
        assertEquals(CodecFamily.LE_AUDIO, codec?.family)
    }

    @Test
    fun lc3IsNeverNormalizedToA2dp() {
        // §34: LC3 + A2DP must not be a valid generic combination.
        val codec = codecFromSourceCodecType(5L)
        assertTrue(codec?.family != CodecFamily.CLASSIC_A2DP)
    }

    @Test
    fun unknownIdsMapToNullNeverAGuess() {
        assertNull(codecFromCodecId(999_999L))
        assertNull(codecFromCodecId(-1L))
        assertNull(codecFromSourceCodecType(999L))
        assertNull(codecFromSourceCodecType(1000000L)) // SOURCE_CODEC_TYPE_INVALID
    }

    @Test
    fun aptXAdaptiveAndLosslessHaveNoPlatformConstant() {
        // §37: graceful degradation — the domain knows these codecs, but the
        // platform constant families do not define them, so no id maps to them.
        // They stay NOT_OBSERVABLE rather than being fabricated.
        val allMapped = listOf(0L, 2L, 16797695L, 604035071L, -1442763265L, 16834815L)
            .mapNotNull { codecFromCodecId(it) } +
            listOf(0L, 1L, 2L, 3L, 4L, 5L).mapNotNull { codecFromSourceCodecType(it) }
        assertTrue(Codec.APTX_ADAPTIVE !in allMapped)
        assertTrue(Codec.APTX_LOSSLESS !in allMapped)
    }

    private fun assertTrue(value: Boolean, message: String = "") {
        kotlin.test.assertTrue(value, message)
    }
}
