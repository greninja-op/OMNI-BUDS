package com.omnibuds.core.codec

import com.omnibuds.core.audio.ChannelMode
import com.omnibuds.core.audio.CodecBitrate
import com.omnibuds.core.audio.CodecMetadata
import com.omnibuds.core.audio.QualityMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phase 11 §33, §40: unknown metadata stays unknown.
 *
 * A missing sample rate never becomes 0, a missing bit depth never becomes
 * 16, a missing channel mode never becomes stereo, and a missing bitrate is
 * never invented from the codec identity (LDAC ≠ 990 kbps without a read).
 */
class CodecMetadataTest {

    @Test
    fun unknownMetadataClaimsNothing() {
        val meta = CodecMetadata.unknown()
        assertNull(meta.sampleRateHz)
        assertNull(meta.bitsPerSample)
        assertEquals(ChannelMode.UNKNOWN, meta.channelMode)
        assertEquals(CodecBitrate.Unknown, meta.bitrate)
        assertEquals(QualityMode.UNKNOWN, meta.qualityMode)
    }

    @Test
    fun missingSampleRateNeverBecomesZero() {
        val meta = CodecMetadata(bitsPerSample = 16)
        assertNull(meta.sampleRateHz, "absent sample rate must be null, not 0")
    }

    @Test
    fun missingBitDepthNeverBecomesSixteen() {
        val meta = CodecMetadata(sampleRateHz = 44100)
        assertNull(meta.bitsPerSample, "absent bit depth must be null, not 16")
    }

    @Test
    fun missingChannelModeNeverBecomesStereo() {
        val meta = CodecMetadata(sampleRateHz = 48000)
        assertEquals(
            ChannelMode.UNKNOWN, meta.channelMode,
            "absent channel mode must be UNKNOWN, not STEREO",
        )
    }

    @Test
    fun bitrateIsNeverInventedFromCodecIdentity() {
        // LDAC without a bitrate read is Unknown — not 990_000.
        val meta = CodecMetadata(sampleRateHz = 96000, bitsPerSample = 24)
        assertTrue(
            meta.bitrate is CodecBitrate.Unknown,
            "bitrate must not be inferred from codec identity",
        )
    }

    @Test
    fun exactBitrateIsPreserved() {
        val meta = CodecMetadata(bitrate = CodecBitrate.Exact(990_000L))
        assertEquals(CodecBitrate.Exact(990_000L), meta.bitrate)
    }

    @Test
    fun adaptiveBitrateHasNoSingleNumber() {
        val meta = CodecMetadata(bitrate = CodecBitrate.Adaptive)
        assertTrue(meta.bitrate is CodecBitrate.Adaptive)
    }

    @Test
    fun qualityModeDefaultsToUnknown() {
        // A codec with no quality parameter leaves UNKNOWN rather than
        // inheriting a plausible-looking BALANCED.
        val meta = CodecMetadata(sampleRateHz = 44100)
        assertEquals(QualityMode.UNKNOWN, meta.qualityMode)
    }
}
