package com.omnibuds.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Registry resolution and its governance limits (REQ-P1-007, AUD-REG-001..006).
 *
 * The registry is vocabulary: resolving a label proves nothing about a device.
 */
class CodecRegistryTest {

    /** Every entry except [Codec.UNKNOWN] is registered and resolves back to itself, by name and by label. */
    @Test
    fun everyRegisteredCodecResolvesBackToItself() {
        val registrable = Codec.entries.filter { it != Codec.UNKNOWN }

        assertEquals(registrable.size, CodecRegistry.all.size)
        assertTrue(CodecRegistry.all.containsAll(registrable))

        registrable.forEach { codec ->
            assertEquals(codec, CodecRegistry.byName(codec.name), "enum name ${codec.name}")
            assertEquals(codec, CodecRegistry.byName(codec.displayName), "display name ${codec.displayName}")
        }
        assertFalse(CodecRegistry.all.contains(Codec.UNKNOWN))
    }

    /** Matching ignores case and separators, since vendor labels are inconsistent about both. */
    @Test
    fun lookupIgnoresCaseAndSeparators() {
        assertEquals(Codec.LDAC, CodecRegistry.byName("ldac"))
        assertEquals(Codec.LDAC, CodecRegistry.byName("LDAC"))
        assertEquals(Codec.LDAC, CodecRegistry.byName("  Ldac  "))
        assertEquals(Codec.APTX_HD, CodecRegistry.byName("APTX HD"))
        assertEquals(Codec.APTX_HD, CodecRegistry.byName("aptx-hd"))
        assertEquals(Codec.APTX_HD, CodecRegistry.byName("aptX_HD"))
        assertEquals(Codec.APTX_ADAPTIVE, CodecRegistry.byName("aptx adaptive"))
        assertEquals(Codec.APTX_LOSSLESS, CodecRegistry.byName("aptX Lossless"))
        assertEquals(Codec.LC3, CodecRegistry.byName("lc3"))
    }

    /** Nothing recognisable is invented: the answer stays null so the caller keeps the vendor's own string. */
    @Test
    fun unrecognisedLabelsResolveToNullNotToAGuess() {
        assertNull(CodecRegistry.byName("definitely-not-a-codec"))
        assertNull(CodecRegistry.byName("LDH"))
        assertNull(CodecRegistry.byName("aptx-ultra"))
        assertNull(CodecRegistry.byName("Hi-Res Audio"))
        assertNull(CodecRegistry.byName(""))
        assertNull(CodecRegistry.byName("   "))
        // UNKNOWN is the absence of a codec, not a codec, so it resolves to nothing.
        assertNull(CodecRegistry.byName("unknown"))
    }

    /** Family comes from the registry, so LE Audio never has to be special-cased at a call site. */
    @Test
    fun familyOfReportsTheTransportFamily() {
        assertEquals(CodecFamily.CLASSIC_A2DP, CodecRegistry.familyOf(Codec.LDAC))
        assertEquals(CodecFamily.CLASSIC_A2DP, CodecRegistry.familyOf(Codec.AAC))
        assertEquals(CodecFamily.CLASSIC_A2DP, CodecRegistry.familyOf(Codec.APTX_LOSSLESS))
        assertEquals(CodecFamily.LE_AUDIO, CodecRegistry.familyOf(Codec.LC3))
        assertEquals(CodecFamily.UNKNOWN, CodecRegistry.familyOf(Codec.UNKNOWN))
    }

    /** Membership produces no state: resolving a name says nothing about any device (AUD-REG-006). */
    @Test
    fun registrationIsNotEvidenceOfAvailability() {
        CodecRegistry.all.forEach { codec ->
            val unknown = CodecCapability.unknown(codec)
            val unsupported = CodecCapability.unsupported(codec)

            assertFalse(unknown.isUsable)
            assertFalse(unknown.isActive)
            assertFalse(unsupported.isActive)
            assertFalse(unsupported.supportsAtLeast(CodecState.SUPPORTED))
        }
    }

    /** The listing is stable, duplicate-free and in declaration order, so a codec matrix can be built from it. */
    @Test
    fun allIsDeclarationOrderWithoutDuplicates() {
        assertEquals(CodecRegistry.all, CodecRegistry.all.distinct())
        assertEquals(Codec.entries.filter { it != Codec.UNKNOWN }, CodecRegistry.all)
    }
}
