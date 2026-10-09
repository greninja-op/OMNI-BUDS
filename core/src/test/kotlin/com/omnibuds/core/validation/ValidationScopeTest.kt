package com.omnibuds.core.validation

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.asSequence
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * Phase 14 (§21K, OB-P14-REQ-020, OB-P14-REQ-024, OB-P14-REQ-030): scope guards.
 *
 * - No signal-path claims (audible output, waveform, packet loss, latency).
 * - No media-audio interception vocabulary.
 * - No polling.
 * - No quality scores.
 */
class ValidationScopeTest {

    private val validationDir = Paths.get("src/main/kotlin/com/omnibuds/core/validation")

    private fun sources(): Sequence<String> {
        if (!Files.isDirectory(validationDir)) return emptySequence()
        return Files.walk(validationDir).asSequence()
            .filter { it.toString().endsWith(".kt") }
            .map { Files.readString(it) }
    }

    @Test
    fun `no signal-path claims`() {
        val banned = listOf(
            "audible output", "audibleOutput", "end-to-end signal",
            "packet loss", "packetLoss", "acoustic latency",
            "waveform", "sound quality",
        )
        for (src in sources()) {
            for (term in banned) {
                assertFalse(
                    src.contains(term),
                    "banned signal-path term: $term",
                )
            }
        }
    }

    @Test
    fun `no media interception vocabulary`() {
        val banned = listOf(
            "AudioRecord", "MediaCodec", "decode(", "encode(",
            "intercept", "loopback", "capture audio",
        )
        for (src in sources()) {
            for (term in banned) {
                assertFalse(src.contains(term), "banned media-path term: $term")
            }
        }
    }

    @Test
    fun `no polling`() {
        val banned = listOf("while (true)", "Thread.sleep", "fixedRateTimer")
        for (src in sources()) {
            for (term in banned) {
                assertFalse(src.contains(term), "banned polling term: $term")
            }
        }
    }

    @Test
    fun `no quality scores`() {
        val banned = listOf("qualityScore", "QualityScore", "rankCodec")
        for (src in sources()) {
            for (term in banned) {
                assertFalse(src.contains(term), "banned scoring term: $term")
            }
        }
    }

    @Test
    fun `no hidden APIs`() {
        val banned = listOf("getDeclaredMethod", "setAccessible", "Runtime.getRuntime().exec")
        for (src in sources()) {
            for (term in banned) {
                assertFalse(src.contains(term), "banned term: $term")
            }
        }
    }
}
