package com.omnibuds.core.quality

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.asSequence
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * Phase 13 (OB-P13-REQ-028, OB-P13-REQ-032, §56): scope guards.
 *
 * - No codec quality scoring (LDAC=10 style).
 * - No media-audio interception/decoding/encoding vocabulary.
 * - No polling loops in the quality package.
 */
class AudioQualityScopeTest {

    private val qualityDir = Paths.get("src/main/kotlin/com/omnibuds/core/quality")

    private fun sources(): Sequence<String> {
        if (!Files.isDirectory(qualityDir)) return emptySequence()
        return Files.walk(qualityDir).asSequence()
            .filter { it.toString().endsWith(".kt") }
            .map { Files.readString(it) }
    }

    @Test
    fun `no codec quality scores`() {
        val banned = listOf("qualityScore", "QualityScore", "codecRank", "rankCodec")
        for (src in sources()) {
            for (term in banned) {
                assertFalse(src.contains(term), "banned scoring term: $term")
            }
        }
    }

    @Test
    fun `no subjective quality claims`() {
        val banned = listOf("better quality", "worse quality", "best codec", "highest quality")
        for (src in sources()) {
            for (term in banned) {
                assertFalse(src.contains(term), "banned subjective claim: $term")
            }
        }
    }

    @Test
    fun `no media audio interception vocabulary`() {
        val banned = listOf(
            "decode(", "decodeAudio", "encode(", "re-encode",
            "intercept", "AudioRecord", "MediaCodec", "virtual device",
        )
        for (src in sources()) {
            for (term in banned) {
                assertFalse(src.contains(term), "banned media-path term: $term")
            }
        }
    }

    @Test
    fun `no polling loops`() {
        val banned = listOf("while (true)", "Thread.sleep", "delay(1000)", "fixedRateTimer")
        for (src in sources()) {
            for (term in banned) {
                assertFalse(src.contains(term), "banned polling term: $term")
            }
        }
    }

    @Test
    fun `no hidden API or shell usage`() {
        val banned = listOf("getDeclaredMethod", "setAccessible", "Runtime.getRuntime().exec", "su -c")
        for (src in sources()) {
            for (term in banned) {
                assertFalse(src.contains(term), "banned term: $term")
            }
        }
    }
}
