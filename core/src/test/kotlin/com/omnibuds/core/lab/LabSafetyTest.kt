package com.omnibuds.core.lab

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.asSequence
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * Phase 20: safety and scope guards.
 */
class LabSafetyTest {

    private val labDir = Paths.get("src/main/kotlin/com/omnibuds/core/lab")

    private fun sources(): Sequence<String> {
        if (!Files.isDirectory(labDir)) return emptySequence()
        return Files.walk(labDir).asSequence()
            .filter { it.toString().endsWith(".kt") }
            .map { Files.readString(it) }
    }

    @Test
    fun `no device-control vocabulary`() {
        // The lab analyzes; it never transmits.
        val banned = listOf(
            "BluetoothGatt.write",
            "createRfcommSocket",
            ".transmit(",
            "sendCommand(",
        )
        for (src in sources()) {
            for (term in banned) {
                assertFalse(src.contains(term), "banned control term: $term")
            }
        }
    }

    @Test
    fun `no UI vocabulary`() {
        val banned = listOf("androidx.compose", "android.widget")
        for (src in sources()) {
            for (term in banned) {
                assertFalse(src.contains(term), "banned UI term: $term")
            }
        }
    }

    @Test
    fun `no script execution`() {
        val banned = listOf("Runtime.getRuntime().exec", "ProcessBuilder", "eval(")
        for (src in sources()) {
            for (term in banned) {
                assertFalse(src.contains(term), "banned execution term: $term")
            }
        }
    }

    @Test
    fun `no network`() {
        val banned = listOf("java.net.URL", "HttpClient", "OkHttp")
        for (src in sources()) {
            for (term in banned) {
                assertFalse(src.contains(term), "banned network term: $term")
            }
        }
    }

    @Test
    fun `no fabrication vocabulary`() {
        val banned = listOf("assumeMeaning", "inferCommand", "guessProtocol")
        for (src in sources()) {
            for (term in banned) {
                assertFalse(src.contains(term), "banned fabrication term: $term")
            }
        }
    }
}
