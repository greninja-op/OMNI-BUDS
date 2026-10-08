package com.omnibuds.core.codec

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * OB-P12-REQ-015, OB-P12-REQ-031, OB-P12-REQ-033, OB-P12-REQ-039:
 * forbidden vocabulary and patterns in codec control code.
 */
class CodecControlScopeTest {

    private fun controlSources(): List<File> {
        // Matches the Phase 11 scope-test convention: the test working
        // directory is the module root.
        val root = File("src/main/kotlin/com/omnibuds/core/codec")
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
    }

    private fun strippedSources(): String =
        controlSources().joinToString("\n") { file ->
            file.readLines()
                .filter { !it.trimStart().startsWith("*") && !it.trimStart().startsWith("//") }
                .joinToString("\n")
        }

    @Test
    fun `no reflection or hidden-API vocabulary`() {
        val src = strippedSources()
        assertTrue(src.isNotEmpty(), "could not locate codec control sources")
        listOf(
            "java.lang.reflect",
            "Method.invoke",
            "setAccessible",
            "Class.forName",
        ).forEach { banned ->
            assertFalse(src.contains(banned), "forbidden vocabulary in codec control: $banned")
        }
    }

    @Test
    fun `no shell, root, or system-property hacks`() {
        val src = strippedSources()
        listOf(
            "Runtime.getRuntime().exec",
            "ProcessBuilder",
            "su -c",
            "/system/",
            "System.getProperty(\"",
        ).forEach { banned ->
            assertFalse(src.contains(banned), "forbidden vocabulary in codec control: $banned")
        }
    }

    @Test
    fun `no thread blocking primitives`() {
        val src = strippedSources()
        listOf(
            "Thread.sleep",
            "CountDownLatch",
            ".join()",
        ).forEach { banned ->
            assertFalse(src.contains(banned), "blocking primitive in codec control: $banned")
        }
    }

    @Test
    fun `no media interception vocabulary`() {
        val src = strippedSources()
        listOf(
            "AudioRecord",
            "MediaCodec.createDecoder",
            "MediaCodec.createEncoder",
            "RECORD_AUDIO",
        ).forEach { banned ->
            assertFalse(src.contains(banned), "media vocabulary in codec control: $banned")
        }
    }

    @Test
    fun `no fake-success vocabulary`() {
        val src = strippedSources()
        // The engine must never claim success it did not verify.
        listOf(
            "\"SUCCESS\"",
            "forceSuccess",
            "pretendSuccess",
        ).forEach { banned ->
            assertFalse(src.contains(banned), "fake-success vocabulary in codec control: $banned")
        }
    }

    @Test
    fun `no android imports in core codec control`() {
        val src = strippedSources()
        assertFalse(src.contains("import android."), "core codec control must not import android.*")
    }
}
