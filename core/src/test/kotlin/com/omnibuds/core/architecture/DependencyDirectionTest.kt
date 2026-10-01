package com.omnibuds.core.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Machine-checked architecture rules for the platform-independent core.
 *
 * Phase 0 wrote several hundred rules in prose; prose cannot stop a later commit from
 * importing an Android class into the domain. These checks enforce the subset that can be
 * verified mechanically: dependency direction, the absence of any Bluetooth or audio
 * framework usage, the absence of placeholder implementations, and the absence of magic
 * protocol literals (Phase 1 prompt sections 32, 37, 51, 53; master sections 51, 52).
 *
 * Test sources legitimately use `java.io` to read the source tree. The rules below apply to
 * main sources only, which is what `:core` ships and what must stay Kotlin-Multiplatform-safe.
 *
 * Every check fails loudly when its inputs are missing: a scan that silently finds nothing
 * because the source directory moved would otherwise be a passing test that proves nothing.
 */
class DependencyDirectionTest {

    private val mainSourceRoot = File("src/main/kotlin")

    private val importPattern = Regex("^import\\s+([A-Za-z0-9_.]+)")

    /** Internal areas and the layers they may depend on. Lower is more foundational. */
    private val areaLayer = mapOf(
        "common" to 0,
        "state" to 1,
        "transport" to 1,
        "device" to 2,
        "capability" to 2,
        "audio" to 2,
        "config" to 2,
        "diagnostics" to 2,
        "session" to 3,
        "persistence" to 3,
        "protocol" to 4,
    )

    private fun mainSources(): List<File> {
        if (!mainSourceRoot.isDirectory) {
            fail(
                "Source scan is meaningless without the main source root. Expected " +
                    "'${mainSourceRoot.invariantSeparatorsPath}' relative to working directory " +
                    "'${File("").absolutePath}'. Fix the scan path rather than letting these " +
                    "architecture rules pass by finding nothing.",
            )
        }
        return mainSourceRoot.walk()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
            .also { sources ->
                if (sources.isEmpty()) fail("No Kotlin main sources found; the scan would be vacuous.")
            }
    }

    private fun importsOf(file: File): List<String> =
        file.readLines().mapNotNull { line -> importPattern.find(line.trim())?.groupValues?.get(1) }

    /** Comment lines and KDoc stripped, so documenting a forbidden thing is not a violation. */
    private fun codeLinesOf(file: File): List<String> =
        file.readLines().map { it.trim() }.filter { line ->
            line.isNotEmpty() &&
                !line.startsWith("//") &&
                !line.startsWith("*") &&
                !line.startsWith("/*") &&
                !line.startsWith("@/")
        }

    private fun relativePath(file: File): String = file.invariantSeparatorsPath.substringAfter("kotlin/")

    private fun areaOf(file: File): String? {
        val path = file.invariantSeparatorsPath
        if (!path.contains("omnibuds/core/")) return null
        val segment = path.substringAfter("omnibuds/core/").substringBefore("/")
        return segment.takeIf { !it.contains('.') }
    }

    private fun assertClean(rule: String, reason: String, violations: List<String>) {
        if (violations.isNotEmpty()) {
            fail("$rule\n$reason\n" + violations.distinct().joinToString("\n"))
        }
    }

    // ---- Rule 1: the core may not touch platform or JVM-only frameworks --------

    @Test
    fun coreMainSourcesDoNotImportAndroidFrameworks() {
        val forbidden = listOf("android.", "androidx.", "com.omnibuds.android")
        val violations = mainSources().flatMap { file ->
            importsOf(file)
                .filter { import -> forbidden.any { prefix -> import.startsWith(prefix) } }
                .map { import -> "${relativePath(file)} imports $import" }
        }
        assertClean(
            "Android is forbidden in :core (ADR-P0-008, Phase 1 prompt section 32).",
            "The core must stay free of Android so it can later compile for desktop:",
            violations,
        )
    }

    @Test
    fun coreMainSourcesDoNotImportJvmOnlyLibraries() {
        val forbidden = listOf("java.", "javax.")
        val violations = mainSources().flatMap { file ->
            importsOf(file)
                .filter { import -> forbidden.any { prefix -> import.startsWith(prefix) } }
                .map { import -> "${relativePath(file)} imports $import" }
        }
        assertClean(
            "JVM-only libraries are forbidden in :core (Phase 1 prompt section 8).",
            "A java.* or javax.* import silently ends Kotlin Multiplatform support:",
            violations,
        )
    }

    // ---- Rule 2: nothing Bluetooth or media-pipeline related exists yet -------

    @Test
    fun coreReferencesNoBluetoothOrAudioFrameworkTypes() {
        val pattern = Regex("\\b(Bluetooth[A-Za-z0-9_]*|AudioTrack|AudioManager|AudioRecord|MediaPlayer)\\b")
        val violations = mainSources().flatMap { file ->
            codeLinesOf(file)
                .filter { line -> pattern.containsMatchIn(line) }
                .map { line -> "${relativePath(file)}: $line" }
        }
        assertClean(
            "No Bluetooth or media-audio implementation may exist in Phase 1 (prompt sections 2, 51).",
            "These belong to Phases 2, 6 and 10:",
            violations,
        )
    }

    // ---- Rule 3: no placeholder that could be mistaken for working code ------

    @Test
    fun coreMainSourcesContainNoPlaceholderImplementations() {
        val markers = listOf("TODO(", "NotImplementedError", "error(\"")
        val violations = mainSources().flatMap { file ->
            codeLinesOf(file)
                .filter { line -> markers.any { marker -> line.contains(marker) } }
                .map { line -> "${relativePath(file)}: $line" }
        }
        assertClean(
            "Main source may contain no stub (Phase 1 prompt sections 26, 53).",
            "A throwing stub reads as an implemented path, which is how fake support begins:",
            violations,
        )
    }

    @Test
    fun productionSourcesDefineNoTestDoubles() {
        val pattern = Regex("^\\s*(class|object)\\s+(Fake|Mock|Stub|Dummy|Test)[A-Z]")
        val violations = mainSources().flatMap { file ->
            file.readLines()
                .filter { line -> pattern.containsMatchIn(line) }
                .map { line -> "${relativePath(file)}: $line" }
        }
        assertClean(
            "Test doubles may not live in main source (Phase 1 prompt section 27).",
            "A double reachable from production code could report hardware behavior that does not exist:",
            violations,
        )
    }

    @Test
    fun noProductionClassImplementsTheProtocolOrRepositoryContracts() {
        val contracts = listOf("EarbudProtocol", "DeviceRepository", "CapabilityRepository", "TransportContract")
        val pattern = Regex("^\\s*(class|object)\\s+\\w+[^{]*:\\s*(?:[A-Za-z0-9_.]*\\.)?(${contracts.joinToString("|")})\\b")
        val violations = mainSources().flatMap { file ->
            codeLinesOf(file)
                .filter { line -> pattern.containsMatchIn(line) }
                .map { line -> "${relativePath(file)}: $line" }
        }
        assertClean(
            "Phase 1 ships contracts only (Phase 1 prompt sections 26, 53).",
            "Any production implementation of a transport, protocol or repository contract would be a fabricated capability:",
            violations,
        )
    }

    // ---- Rule 4: dependency direction inside the core ------------------------

    @Test
    fun coreAreasDependOnlyOnMoreFoundationalAreas() {
        val violations = mainSources().flatMap { file ->
            val area = areaOf(file) ?: return@flatMap emptyList<String>()
            val sourceLayer = areaLayer[area]
                ?: fail("Area '$area' is not registered in the layer map of this test.")
            importsOf(file)
                .mapNotNull { import -> internalAreaOf(import) }
                .distinct()
                .filter { target -> target != area && (areaLayer[target] ?: Int.MAX_VALUE) >= sourceLayer }
                .map { target -> "$area -> $target" }
        }
        assertClean(
            "Dependencies must point downward only (design.md section 3).",
            "Offending upward or sideways edges:",
            violations,
        )
    }

    @Test
    fun everyCoreAreaIsRegisteredInTheLayerMap() {
        val unregistered = mainSources().mapNotNull(::areaOf).filter { area -> area !in areaLayer }.distinct()
        if (unregistered.isNotEmpty()) {
            fail("A new area must be placed in the layer map deliberately, not by accident: " + unregistered.joinToString())
        }
    }

    private fun internalAreaOf(import: String): String? {
        val stripped = import.removePrefix("com.omnibuds.core.")
        return stripped.takeIf { it != import }?.substringBefore(".")
    }

    // ---- Rule 5: no magic protocol facts in code ----------------------------

    @Test
    fun coreContainsNoHardCodedProtocolLiterals() {
        val hex = Regex("\\b0[xX][0-9a-fA-F]{2,}\\b")
        val uuid = Regex("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b")
        val violations = mainSources().flatMap { file ->
            codeLinesOf(file)
                .filter { line -> hex.containsMatchIn(line) || uuid.containsMatchIn(line) }
                .map { line -> "${relativePath(file)}: $line" }
        }
        assertClean(
            "Protocol bytes and service UUIDs are evidence-bearing data, not code literals (master section 52).",
            "Magic values scattered through code are forbidden by ADR-P0-003 and section 52:",
            violations,
        )
    }

    // ---- Rule 6: package statements match the directory layout --------------

    @Test
    fun packageStatementsMatchSourceDirectories() {
        val violations = mainSources().mapNotNull { file ->
            val declared = file.readLines()
                .firstOrNull { line -> line.trim().startsWith("package ") }
                ?.trim()
                ?.removePrefix("package ")
                ?.trim()
            val expected = file.invariantSeparatorsPath
                .substringAfter("kotlin/")
                .substringBeforeLast("/")
                .replace('/', '.')
            if (declared == expected) null else "${relativePath(file)}: declared '$declared', expected '$expected'"
        }
        assertClean(
            "Package drift breaks every import-based rule above.",
            "Mismatched packages:",
            violations,
        )
    }

    // ---- Rule 7: the Android boundary is still empty in Phase 1 --------------

    @Test
    fun platformAndroidModuleStillContainsNoSources() {
        val androidMain = File("../platform/android/src/main")
        if (!androidMain.isDirectory) {
            fail("Expected the Android boundary at '${androidMain.invariantSeparatorsPath}'.")
        }
        val sources = androidMain.walk().filter { file -> file.isFile && file.extension == "kt" }.toList()
        assertTrue(sources.isEmpty(), "Phase 1 forbids Bluetooth, permissions, notifications, Quick Settings and UI: " + sources.joinToString { it.name })
    }
}
