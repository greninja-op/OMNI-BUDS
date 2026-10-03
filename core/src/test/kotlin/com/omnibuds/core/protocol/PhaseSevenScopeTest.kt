package com.omnibuds.core.protocol

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What Phase 7 was and was not allowed to reach, checked as data (the `PhaseFour/FiveScopeTest` shape).
 *
 * The two load-bearing properties for a protocol *framework* that ships no protocol: nothing in `src/main`
 * implements the runtime interfaces (a production implementer would be a fake hardware protocol —
 * ADR-P7-010), and resolution/registry code never opens a channel or sends a command (prompt §10). Both are
 * asserted, not assumed.
 *
 * Tier T1.
 */
class PhaseSevenScopeTest {

    @Test
    fun noProductionSourceImplementsTheRuntimeProtocolInterfaces() {
        // A scripted session/adapter belongs in test source only; a `: ProtocolSession` in main would be a
        // channel pretending to speak a protocol with no radio and no verified facts behind it.
        val markers = listOf(": ProtocolSession", ": ProtocolTransportAdapter")
        val offenders = protocolMainSources().flatMap { file ->
            codeLinesOf(file)
                .filter { line -> markers.any { marker -> line.contains(marker) } }
                .map { line -> "${file.name}: $line" }
        }

        assertEquals(emptyList(), offenders, "a production protocol session/adapter exists; Phase 7 ships the contract only")
    }

    @Test
    fun theProtocolPackageImportsNoAndroidClass() {
        // Prompt §16: protocols depend on transport abstractions, never Android Bluetooth classes. The layer
        // map already blocks the upward edge; this pins that no framework import slipped in anyway.
        val offenders = protocolMainSources().flatMap { file ->
            file.readLines()
                .filter { line -> line.startsWith("import ") && line.contains("android.") }
                .map { line -> "${file.name}: $line" }
        }

        assertEquals(emptyList(), offenders, "an Android class was imported into the core protocol package")
    }

    @Test
    fun resolutionAndRegistryCodeNeverOpenOrExchangeAChannel() {
        // The resolver and registry read gathered evidence and index records; a `.open()` / `.exchange(` call
        // would be auto-connecting or executing during resolution, which prompt §10 forbids.
        val forbidden = listOf(".open(", ".exchange(", "connectGatt", "createRfcommSocket")
        val scanned = listOf("ProtocolResolver.kt", "ProtocolRegistry.kt")
        val offenders = protocolMainSources()
            .filter { file -> file.name in scanned }
            .flatMap { file ->
                codeLinesOf(file)
                    .filter { line -> forbidden.any { token -> line.contains(token) } }
                    .map { line -> "${file.name}: $line" }
            }

        assertEquals(emptyList(), offenders, "resolution or registry code reached a transport operation")
    }

    @Test
    fun theShippedRegistryHoldsNoProtocol() {
        // The ADR-P1-013 / ADR-P5-006 discipline, restated for Phase 7: the framework ships empty, so the
        // resolver answers UNKNOWN for every device until a reviewed record is registered.
        assertTrue(ProtocolRegistry.empty().isEmpty)
        assertEquals(0, ProtocolRegistry.empty().size)
        assertFalse(
            ProtocolRegistry.empty().candidatesFor(
                com.omnibuds.core.device.DeviceFingerprint(protocolCandidates = listOf("anything")),
            ).isNotEmpty(),
            "the empty registry named a candidate",
        )
    }

    /** Comment/KDoc lines removed, so documenting a forbidden thing is not doing it. */
    private fun codeLinesOf(file: File): List<String> =
        file.readLines().map { line -> line.trim() }.filter { line ->
            line.isNotEmpty() &&
                !line.startsWith("//") &&
                !line.startsWith("*") &&
                !line.startsWith("/*") &&
                !line.startsWith("@/")
        }

    private fun protocolMainSources(): List<File> {
        val root = File("src/main/kotlin/com/omnibuds/core/protocol")
        assertTrue(root.isDirectory, "the protocol area moved or vanished: ${root.invariantSeparatorsPath}")
        val files = root.walk().filter { file -> file.isFile && file.extension == "kt" }.toList()
        assertTrue(files.isNotEmpty(), "no protocol sources found; the scan would be vacuous")
        return files
    }
}
