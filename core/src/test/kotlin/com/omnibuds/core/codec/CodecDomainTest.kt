package com.omnibuds.core.codec

import com.omnibuds.core.audio.Codec
import com.omnibuds.core.audio.CodecFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 11 §32: every codec identity is represented, with the correct
 * transport family. LC3 belongs to LE Audio; everything else classic is
 * CLASSIC_A2DP; UNKNOWN carries no family claim.
 */
class CodecDomainTest {

    @Test
    fun allRequiredCodecIdentitiesExist() {
        val ids = Codec.entries.map { it.name }.toSet()
        assertTrue(Codec.SBC in Codec.entries)
        assertTrue(Codec.AAC in Codec.entries)
        assertTrue(Codec.APTX in Codec.entries)
        assertTrue(Codec.APTX_HD in Codec.entries)
        assertTrue(Codec.APTX_ADAPTIVE in Codec.entries)
        assertTrue(Codec.APTX_LOSSLESS in Codec.entries)
        assertTrue(Codec.LDAC in Codec.entries)
        assertTrue(Codec.LC3 in Codec.entries)
        assertTrue(Codec.UNKNOWN in Codec.entries)
        assertEquals(10, ids.size, "expected 10 identities (9 codecs + OPUS + UNKNOWN)")
    }

    @Test
    fun aptXVariantsAreSeparateIdentities() {
        // Supporting aptX evidences nothing about aptX HD/Adaptive/Lossless
        // (AUD-REG-002): four distinct enum entries, no collapsing.
        val aptx = setOf(Codec.APTX, Codec.APTX_HD, Codec.APTX_ADAPTIVE, Codec.APTX_LOSSLESS)
        assertEquals(4, aptx.size)
    }

    @Test
    fun lc3BelongsToLeAudioNeverA2dp() {
        assertEquals(CodecFamily.LE_AUDIO, Codec.LC3.family)
    }

    @Test
    fun classicCodecsBelongToClassicA2dp() {
        listOf(
            Codec.SBC, Codec.AAC, Codec.APTX, Codec.APTX_HD,
            Codec.APTX_ADAPTIVE, Codec.APTX_LOSSLESS, Codec.LDAC, Codec.OPUS,
        ).forEach { codec ->
            assertEquals(
                CodecFamily.CLASSIC_A2DP, codec.family,
                "${codec.name} must be CLASSIC_A2DP",
            )
        }
    }

    @Test
    fun unknownCodecCarriesNoFamilyClaim() {
        assertEquals(CodecFamily.UNKNOWN, Codec.UNKNOWN.family)
    }

    @Test
    fun displayNameIsNotIdentity() {
        // A marketing label is not an identity (AUD-REG-005).
        assertEquals("aptX HD", Codec.APTX_HD.displayName)
        assertTrue(Codec.APTX_HD.name != Codec.APTX_HD.displayName)
    }
}
