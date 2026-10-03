package com.omnibuds.core.capability

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What Phase 8 was and was not allowed to reach, checked as data (the `PhaseFour/Five/SevenScopeTest` shape).
 *
 * The load-bearing properties for a discovery *engine* that ships no protocol and touches no device: no
 * production source implements the read-only [CapabilityDiscoverySource] (that would be a fake hardware
 * discovery path — ADR-P8-010), the engine performs no write/open/command (§9, §19), the capability area
 * imports nothing upward (the L2→L4 ban that keeps the engine off the protocol layer — ADR-P8-005), and no
 * wall clock is read in the core (§17, ADR-P1-012). All asserted, none assumed. Tier T1.
 */
class PhaseEightScopeTest {

    private fun codeLinesOf(file: File): List<String> =
        file.readLines().map { line -> line.trim() }.filter { line ->
            line.isNotEmpty() &&
                !line.startsWith("//") &&
                !line.startsWith("*") &&
                !line.startsWith("/*") &&
                !line.startsWith("@/")
        }

    private fun coreMainSources(): List<File> {
        val root = File("src/main/kotlin")
        assertTrue(root.isDirectory, "the core main source root moved: ${root.invariantSeparatorsPath}")
        return root.walk().filter { file -> file.isFile && file.extension == "kt" }.toList().also {
            assertTrue(it.isNotEmpty(), "no core main sources; the scan would be vacuous")
        }
    }

    private fun capabilityMainSources(): List<File> {
        val root = File("src/main/kotlin/com/omnibuds/core/capability")
        assertTrue(root.isDirectory, "the capability area moved or vanished: ${root.invariantSeparatorsPath}")
        return root.walk().filter { file -> file.isFile && file.extension == "kt" }.toList().also {
            assertTrue(it.isNotEmpty(), "no capability sources; the scan would be vacuous")
        }
    }

    @Test
    fun noProductionSourceImplementsTheCapabilityDiscoverySource() {
        // A real device/protocol-bound discovery source belongs at layer 3/4 and would speak for hardware that
        // has never been read (ADR-P8-010). Only test source may implement the seam.
        val classHeader = Regex("\\b(class|object)\\b.*\\bCapabilityDiscoverySource\\b")
        val supertypeAfterParams = Regex("^\\).*:\\s*.*\\bCapabilityDiscoverySource\\b")
        val offenders = coreMainSources().flatMap { file ->
            codeLinesOf(file)
                .filter { line -> classHeader.containsMatchIn(line) || supertypeAfterParams.containsMatchIn(line) }
                .map { line -> "${file.name}: $line" }
        }

        assertEquals(emptyList(), offenders, "a production CapabilityDiscoverySource exists; Phase 8 ships the seam only")
    }

    @Test
    fun theDiscoveryEngineOnlyReadsAndNeverWritesOrOpens() {
        // §9/§19: discovery issues read-only operations and no side-effecting command, ever.
        val forbidden = listOf(".open(", ".exchange(", ".write(", "sendCommand", "connectGatt", "createRfcommSocket", "startScan")
        val engineFiles = coreMainSources().filter { file ->
            file.name in listOf("CapabilityDiscoveryEngine.kt")
        }
        val offenders = engineFiles.flatMap { file ->
            codeLinesOf(file)
                .filter { line -> forbidden.any { token -> line.contains(token) } }
                .map { line -> "${file.name}: $line" }
        }

        assertEquals(emptyList(), offenders, "the discovery engine reached a write, open, or command path")
    }

    @Test
    fun theCapabilityAreaImportsNothingUpward() {
        // ADR-P8-005: capability is layer 2; it may not import the protocol layer (4) nor a same-or-higher
        // sibling like session/persistence (3) or device/audio (2 sideways). The engine is handed a source
        // precisely so it never needs `protocol`.
        val forbiddenPackages = listOf("com.omnibuds.core.protocol", "com.omnibuds.core.session", "com.omnibuds.core.persistence")
        val offenders = capabilityMainSources().flatMap { file ->
            file.readLines()
                .map { line -> line.trim() }
                .filter { line -> line.startsWith("import ") && forbiddenPackages.any { pkg -> line.contains(pkg) } }
                .map { line -> "${file.name}: $line" }
        }

        assertEquals(emptyList(), offenders, "the capability area imported an upward-layer package")
    }

    @Test
    fun theCapabilityAreaReadsNoWallClockOrAndroidClass() {
        // §17, ADR-P1-012: time arrives through the injected TimeProvider; a direct clock read would make a test
        // depend on wall time and would end Kotlin-Multiplatform support.
        val forbidden = listOf("System.currentTimeMillis", "Instant.", "java.util.Date", "LocalDateTime", "android.", "com.omnibuds.android")
        val offenders = capabilityMainSources().flatMap { file ->
            codeLinesOf(file)
                .filter { line -> forbidden.any { token -> line.contains(token) } }
                .map { line -> "${file.name}: $line" }
        }

        assertEquals(emptyList(), offenders, "the capability area read a clock or reached Android directly")
    }
}
