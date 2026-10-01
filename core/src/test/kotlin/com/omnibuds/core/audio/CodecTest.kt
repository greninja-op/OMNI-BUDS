package com.omnibuds.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The codec vocabulary itself (REQ-P1-007, AUD-REG-001/002/003, master sections 19
 * and 20).
 */
class CodecTest {

    /** aptX, aptX HD, aptX Adaptive and aptX Lossless are four capabilities, never one flag. */
    @Test
    fun aptxVariantsAreFourDistinctCodecs() {
        val variants = listOf(Codec.APTX, Codec.APTX_HD, Codec.APTX_ADAPTIVE, Codec.APTX_LOSSLESS)

        assertEquals(variants.size, variants.distinct().size)
        assertEquals(4, variants.toSet().size)
        assertNotEquals(Codec.APTX, Codec.APTX_HD)
        assertNotEquals(Codec.APTX, Codec.APTX_ADAPTIVE)
        assertNotEquals(Codec.APTX_HD, Codec.APTX_LOSSLESS)
        assertNotEquals(Codec.APTX_ADAPTIVE, Codec.APTX_LOSSLESS)
        assertEquals(
            listOf("aptX", "aptX HD", "aptX Adaptive", "aptX Lossless"),
            variants.map { it.displayName },
        )
        variants.forEach { assertEquals(CodecFamily.CLASSIC_A2DP, it.family, "${it.displayName} is classic A2DP") }
    }

    /** LC3 is LE Audio, not another A2DP codec; LDAC and AAC are classic A2DP (master section 20). */
    @Test
    fun lc3IsLeAudioWhileLdacAndAacAreClassicA2dp() {
        assertEquals(CodecFamily.LE_AUDIO, Codec.LC3.family)
        assertEquals(CodecFamily.CLASSIC_A2DP, Codec.LDAC.family)
        assertEquals(CodecFamily.CLASSIC_A2DP, Codec.AAC.family)
    }

    /** Only the entries the ecosystem runs over LE Audio claim that family. */
    @Test
    fun leAudioFamilyContainsLc3Alone() {
        assertEquals(listOf(Codec.LC3), Codec.entries.filter { it.family == CodecFamily.LE_AUDIO })
    }

    /** A codec the platform lacks stays representable, so the model is not the platform's limit list. */
    @Test
    fun everyPromptNamedCodecHasAnEntry() {
        val promptNamed = listOf(
            Codec.SBC,
            Codec.AAC,
            Codec.APTX,
            Codec.APTX_HD,
            Codec.APTX_ADAPTIVE,
            Codec.APTX_LOSSLESS,
            Codec.LDAC,
            Codec.LC3,
        )

        assertTrue(promptNamed.all { it in CodecRegistry.all })
        assertEquals(promptNamed.size, promptNamed.map { it.displayName }.distinct().size)
    }

    /** `UNKNOWN` is a value, so "not determined" needs no null chains (AUD-TERM-001). */
    @Test
    fun unknownIsAnEntryRatherThanAnAbsence() {
        assertTrue(Codec.entries.contains(Codec.UNKNOWN))
        assertEquals(CodecFamily.UNKNOWN, Codec.UNKNOWN.family)
        assertNotEquals(Codec.UNKNOWN, Codec.SBC)
    }

    /** An unrecognised label resolves to nothing at all, not to the nearest real codec. */
    @Test
    fun unknownLabelIsNotGuessedOntoARealCodec() {
        assertNull(CodecRegistry.byName("definitely-not-a-codec"))
        assertNull(CodecRegistry.byName(""))
        assertNull(CodecRegistry.byName("   "))
        // A vendor label for LDAC: close, still refused (AUD-REG-005).
        assertNull(CodecRegistry.byName("LDH"))
    }
}
