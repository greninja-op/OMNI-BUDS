package com.omnibuds.core.battery

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.asSequence
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * Phase 16 (§24, OB-P16-REQ-018, OB-P16-REQ-022): scope guards.
 * No fabricated readings, no inference, no invented protocols, no UI.
 */
class BatteryScopeTest {

    private val batteryDir = Paths.get("src/main/kotlin/com/omnibuds/core/battery")

    private fun sources(): Sequence<String> {
        if (!Files.isDirectory(batteryDir)) return emptySequence()
        return Files.walk(batteryDir).asSequence()
            .filter { it.toString().endsWith(".kt") }
            .map { Files.readString(it) }
    }

    @Test
    fun `no fabricated production readings`() {
        // Production code must not contain hard-coded demo percentages.
        // (Test fixtures live in src/test, not here.)
        val banned = listOf("demo", "fake", "fixture", "sampleBattery", "mockLevel")
        for (src in sources()) {
            for (term in banned) {
                assertFalse(src.contains(term, ignoreCase = true), "banned term: $term")
            }
        }
    }

    @Test
    fun `no inference vocabulary`() {
        val banned = listOf("inferCharging", "assumeCharging", "guessLevel", "estimateBattery")
        for (src in sources()) {
            for (term in banned) {
                assertFalse(src.contains(term), "banned inference term: $term")
            }
        }
    }

    @Test
    fun `no invented protocol literals`() {
        val banned = listOf("opcode", "UUID(")
        for (src in sources()) {
            for (term in banned) {
                assertFalse(src.contains(term), "banned protocol term: $term")
            }
        }
    }

    @Test
    fun `no UI imports`() {
        val banned = listOf("androidx.compose", "android.widget", "android.view.View")
        for (src in sources()) {
            for (term in banned) {
                assertFalse(src.contains(term), "banned UI term: $term")
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
