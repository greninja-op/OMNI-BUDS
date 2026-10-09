package com.omnibuds.core.processing

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.asSequence
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * Phase 15 (§19, OB-P15-REQ-018): scope guards.
 * No application-side DSP, no media interception, no invented protocols.
 */
class ProcessingScopeTest {

    private val processingDir = Paths.get("src/main/kotlin/com/omnibuds/core/processing")

    private fun sources(): Sequence<String> {
        if (!Files.isDirectory(processingDir)) return emptySequence()
        return Files.walk(processingDir).asSequence()
            .filter { it.toString().endsWith(".kt") }
            .map { Files.readString(it) }
    }

    @Test
    fun `no application DSP vocabulary`() {
        val banned = listOf(
            "AudioRecord", "MediaCodec", "decode(", "encode(",
            "PCM", "filter(", "virtual ANC", "loopback",
        )
        for (src in sources()) {
            for (term in banned) {
                assertFalse(src.contains(term), "banned DSP term: $term")
            }
        }
    }

    @Test
    fun `no invented protocol literals`() {
        val banned = listOf("0x", "UUID(", "opcode")
        for (src in sources()) {
            for (term in banned) {
                // Allowlist: none — the resolver must not contain packet literals.
                assertFalse(src.contains(term), "banned protocol term: $term")
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
