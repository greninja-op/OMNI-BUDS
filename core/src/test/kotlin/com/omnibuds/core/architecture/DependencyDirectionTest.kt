package com.omnibuds.core.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Machine-checked architecture rules for the platform-independent core.
 *
 * Phase 0 wrote several hundred rules in prose; prose cannot stop a later commit from importing an
 * Android class into the domain. These checks enforce the subset that can be verified
 * mechanically: dependency direction, the absence of Android framework coupling, the absence of
 * placeholder implementations, the confinement of test doubles, and the absence of magic protocol
 * literals (Phase 1 prompt sections 32, 37, 51, 53; Phase 2 prompt sections 5, 6, 7; master
 * sections 51, 52).
 *
 * Test sources legitimately use `java.io` to read the source tree. The rules below apply to main
 * sources only, which is what `:core` ships and what must stay Kotlin-Multiplatform-safe.
 *
 * Every check fails loudly when its inputs are missing: a scan that silently finds nothing because
 * the source directory moved would otherwise be a passing test that proves nothing.
 */
class DependencyDirectionTest {

    private val mainSourceRoot = File("src/main/kotlin")

    private val platformSourceRoot = File("../platform/android/src/main")

    private val importPattern = Regex("^import\\s+([A-Za-z0-9_.]+)")

    /** Internal areas and the layers they may depend on. Lower is more foundational. */
    private val areaLayer = mapOf(
        // `common` and `state` are both layer 0: an enum of states is vocabulary, not a policy layer,
        // and placing it above `common` would make every foundational area import upward.
        "common" to 0,
        "state" to 0,
        "transport" to 1,
        "platform" to 1,
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

    private fun violationsByImport(prefixes: List<String>): List<String> =
        mainSources().flatMap { file ->
            importsOf(file)
                .filter { import -> prefixes.any { prefix -> import.startsWith(prefix) } }
                .map { import -> "${relativePath(file)} imports $import" }
        }

    private fun violationsByCodeToken(tokens: List<String>): List<String> {
        val pattern = Regex("\\b(${tokens.joinToString("|")})\\b")
        return mainSources().flatMap { file ->
            codeLinesOf(file)
                .filter { line -> pattern.containsMatchIn(line) }
                .map { line -> "${relativePath(file)}: $line" }
        }
    }

    private fun platformSources(): List<File> {
        if (!platformSourceRoot.isDirectory) {
            fail("Expected the Android boundary at '${platformSourceRoot.invariantSeparatorsPath}'.")
        }
        return platformSourceRoot.walk().filter { file -> file.isFile && file.extension == "kt" }.toList()
    }

    // ---- Rule 1: the core may not touch platform or JVM-only frameworks --------

    @Test
    fun coreMainSourcesDoNotImportAndroidFrameworks() {
        assertClean(
            "Android is forbidden in :core (ADR-P0-008, Phase 1 prompt section 32).",
            "The core must stay free of Android so it can later compile for desktop:",
            violationsByImport(listOf("android.", "androidx.", "com.omnibuds.android")),
        )
    }

    @Test
    fun coreMainSourcesDoNotImportJvmOnlyLibraries() {
        assertClean(
            "JVM-only libraries are forbidden in :core (Phase 1 prompt section 8).",
            "A java.* or javax.* import silently ends Kotlin Multiplatform support:",
            violationsByImport(listOf("java.", "javax.")),
        )
    }

    // ---- Rule 2: no Android framework type may leak into the domain ------------
    //
    // Named as the framework's own classes, deliberately not as "any identifier beginning with
    // Bluetooth". Phase 2 legitimately introduces core vocabulary such as BluetoothAdapterState and
    // BluetoothOperation; a prefix ban would either fail honest code or have to be widened until it
    // meant nothing.

    @Test
    fun coreReferencesNoAndroidFrameworkTypes() {
        assertClean(
            "Android framework types may not appear in :core code (Phase 2 prompt sections 5.1, 32).",
            "They belong behind the :platform:android boundary:",
            violationsByCodeToken(
                listOf(
                    "BluetoothAdapter", "BluetoothManager", "BluetoothDevice", "BluetoothProfile",
                    "BluetoothGatt", "BluetoothGattCallback", "BluetoothSocket", "BluetoothServerSocket",
                    "BluetoothLeScanner", "BluetoothLeAudio", "BluetoothA2dp", "BluetoothHeadset",
                    "AudioTrack", "AudioManager", "AudioRecord", "MediaPlayer",
                ),
            ),
        )
    }

    // ---- Rule 3: no placeholder that could be mistaken for working code -------

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
            "No production implementation of a transport, protocol or repository contract may exist yet " +
                "(Phase 1 prompt sections 26, 53; Phase 2 prompt section 6).",
            "Such an implementation would be a fabricated capability:",
            violations,
        )
    }

    // ---- Rule 4: dependency direction inside the core -------------------------

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
            "Dependencies must point downward only (Phase 1 design.md section 3, ADR-P1-003).",
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

    // ---- Rule 5: no magic protocol facts in code ------------------------------

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
            "Magic values scattered through code are forbidden by ADR-P0-003 and master section 52:",
            violations,
        )
    }

    // ---- Rule 6: package statements match the directory layout ----------------

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

    // ---- Rule 7: the platform module holds no capability it was not given -----
    //
    // Phase 1 asserted this module was empty. Phase 2 opens it, so deleting the emptiness check
    // would have removed a guard; instead it is replaced by a scope check that survives the boundary
    // gaining code and still fails if this phase's authorisation is exceeded.

    @Test
    fun platformModuleContainsNoUnauthorisedCapabilities() {
        val forbidden = listOf(
            "connectGatt", "BluetoothGatt", "writeCharacteristic", "startDiscovery",
            "BluetoothLeScanner", "startScan", "createRfcommSocket", "listenUsingRfcomm",
            "TileService", "AppWidgetProvider", "NotificationListenerService",
            "androidx.appcompat", "androidx.compose", "setContentView", "ComponentActivity",
        )
        val pattern = Regex("\\b(${forbidden.joinToString("|")})\\b")
        val violations = platformSources().flatMap { file ->
            codeLinesOf(file)
                .filter { line -> pattern.containsMatchIn(line) }
                .map { line -> "${file.name}: $line" }
        }

        assertClean(
            "Phase 2 authorises adapter inspection, permission status and capability reporting only " +
                "(Phase 2 prompt sections 5, 6, 7).",
            "GATT or RFCOMM traffic, discovery, UI, Quick Settings and widgets belong to later phases:",
            violations,
        )
    }

    // ---- Rule 8: the media audio path is untouched ----------------------------

    @Test
    fun neitherModuleTouchesTheMediaAudioPath() {
        val forbidden = listOf("AudioRecord", "MediaCodec", "MediaExtractor", "MediaMuxer", "MediaSession")
        val pattern = Regex("\\b(${forbidden.joinToString("|")})\\b")

        val violations = (
            mainSources().map { file -> relativePath(file) to file } +
                platformSources().map { file -> "platform/${file.name}" to file }
            )
            .flatMap { (label, file) ->
                codeLinesOf(file)
                    .filter { line -> pattern.containsMatchIn(line) }
                    .map { line -> "$label: $line" }
            }

        assertClean(
            "OmniBuds stays outside the media audio path (ADR-P0-002, Phase 2 prompt section 7).",
            "Capture, decode or re-encode of media audio must not appear in either module:",
            violations,
        )
    }

    // ---- Rule 9: no UI framework surface exists before its phase ---------------

    @Test
    fun neitherModuleReferencesUiFrameworks() {
        val forbidden = listOf("Activity", "Fragment", "ViewModel", "Intent", "ContextThemeWrapper")
        val pattern = Regex("\\b(${forbidden.joinToString("|")})\\b")

        val violations = (
            mainSources().map { file -> relativePath(file) to file } +
                platformSources().map { file -> "platform/${file.name}" to file }
            )
            .flatMap { (label, file) ->
                codeLinesOf(file)
                    .filter { line -> pattern.containsMatchIn(line) }
                    .map { line -> "$label: $line" }
            }

        assertClean(
            "Phase 2 forbids UI screens (Phase 2 prompt section 6); the first UI is Phase 49.",
            "UI framework references found:",
            violations,
        )
    }

    // ---- Rule 10: module dependency direction is one way ----------------------

    @Test
    fun theAndroidModuleDependsOnCoreAndNotTheReverse() {
        val coreBuild = File("build.gradle.kts")
        if (!coreBuild.isFile) fail("Expected :core's build script at '${coreBuild.invariantSeparatorsPath}'.")

        val reverseDependency = coreBuild.readLines()
            .map { it.trim() }
            .filter { line -> line.contains("project(") && line.contains("android") }

        assertClean(
            ":core must never depend on :platform:android (Phase 2 prompt section 5.1).",
            "Reverse dependency declared in :core's build script:",
            reverseDependency,
        )

        val platformBuild = File("../platform/android/build.gradle.kts")
        if (!platformBuild.isFile) fail("Expected the platform module's build script.")
        assertTrue(
            platformBuild.readLines().any { line -> line.contains("project(\":core\")") },
            ":platform:android must depend on :core abstractions (Phase 2 prompt section 5.1).",
        )
    }
}
