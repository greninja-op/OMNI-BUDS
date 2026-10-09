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

    private val platformInstrumentedSourceRoot = File("../platform/android/src/androidTest")

    private val platformUnitTestSourceRoot = File("../platform/android/src/test")

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
        // Phase 17: the configuration engine consumes device (2), config (2),
        // capability (2), persistence (3). Layer 5; never imports feature (5)
        // sideways.
        "configuration" to 5,
        // Phase 18: the verification framework consumes common (0),
        // config (2), capability (2), device (2), configuration (5) for
        // storage. Layer 5; event-driven — never imports feature (5)
        // sideways; the caller drives protocol ports.
        "verification" to 5,
        // Phase 19: vendor adapters consume device (2), protocol (4),
        // common (0). Layer 5; never imports feature (5) sideways.
        "vendor" to 5,
        // Phase 20: lab consumes common (0), state (0). Layer 5;
        // passive analysis only — never imports feature or protocol.
        "lab" to 5,
        // Phase 21: access control consumes device (2), state (0), common (0),
        // diagnostics (2). Layer 5; centralizes default-deny policy.
        "access" to 5,
        // Phase 22: knowledge database consumes common (0), state (0).
        // Layer 5; descriptive knowledge only. Storage is injected as
        // function types so no sideways import is needed.
        "knowledge" to 5,
        // Phase 23: extension framework consumes common (0) and state (0)
        // only, plus coroutines. Layer 5; never imports feature, vendor,
        // access, lab, or knowledge sideways.
        "extension" to 5,
        // Phase 24: global state engine consumes common (0), state (0),
        // platform (1), plus coroutines. Layer 5; aggregates via typed
        // contracts, never imports battery, feature, access, knowledge,
        // extension, or vendor sideways.
        "globalstate" to 5,
        // Phase 28: background lifecycle orchestration consumes coroutines
        // and common only. Layer 5; depends on nothing sideways — the
        // coordinator integrates via the LifecycleHooks interface, not by
        // importing session/globalstate/feature engines.
        "lifecycle" to 5,
        // Phase 30: device-test infrastructure. Consumes common,
        // transport, and coroutines only; never imported by production
        // code. Layer 5 alongside the engines it doubles.
        "testkit" to 5,
        "diagnostics" to 2,
        "session" to 3,
        "persistence" to 3,
        "protocol" to 4,
        // Phase 13: the audio quality & negotiation engine consumes the audio
        // vocabulary (layer 2) and the codec engine (layer 3). It sits above
        // both and nothing below it may depend on it.
        "quality" to 4,
        // Phase 14: the audio path validation engine consumes the quality
        // engine (layer 4) and the codec/audio vocabularies. It sits above
        // quality; nothing may depend on it.
        "validation" to 5,
        // Phase 15: the processing-domain model annotates capability (2)
        // records with domains. Layer 5, but never imports feature (5) —
        // integration happens via FeatureId/FeatureCapability only.
        "processing" to 5,
        // Phase 16: the battery engine consumes device (2), session (3),
        // and protocol (4) abstractions. Layer 5; never imports feature (5)
        // sideways.
        "battery" to 5,
        // Phase 9: the hardware feature engine sits above the protocol layer — it
        // drives the handed-in FeatureProtocolPort seam, consumes capability
        // snapshots, and owns the control-state axis. It may depend on any lower
        // layer, and nothing below it may depend on it (ADR-P9-001).
        "feature" to 5,
        // Phase 11: the codec capability engine sits above the audio vocabulary —
        // it consumes Codec/CodecState/CodecCapability, keys snapshots by
        // DeviceIdentity, and owns the codec-state axis behind the
        // CodecObservationSource port. It may depend on any lower layer, and
        // nothing below it may depend on it.
        "codec" to 3,
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

    private fun platformSources(): List<File> = scannedSourceRoot(platformSourceRoot, "the Android boundary")

    /**
     * The instrumented source set, scanned for the capability list from Phase 3 onward.
     *
     * ADR-P3-007 named this gap rather than letting it stay theoretical: the guards read `src/main`
     * only, so an instrumented test could reach a forbidden framework call, compile cleanly, never be
     * run and pass every check in this file. A test that can do a thing the phase forbids is still a
     * module that can do it, and the next editor copies the call.
     */
    private fun instrumentedPlatformSources(): List<File> =
        scannedSourceRoot(platformInstrumentedSourceRoot, "the Android instrumented tests")

    private fun platformUnitTestSources(): List<File> =
        scannedSourceRoot(platformUnitTestSourceRoot, "the Android JVM unit tests")

    /**
     * Everything in the platform module that may hold a framework reference at all: production code and
     * the instrumentation that exercises it.
     *
     * One list rather than a second, narrower check, so that widening the scan cannot be done by
     * forgetting one of three call sites. `src/test` is in it because the claim that it holds no
     * framework reference turned out to be false - three Phase 2 unit tests import
     * `android.bluetooth.BluetoothAdapter` for its state constants - and a reason that is not true is
     * not a reason for excluding a source set from a capability guard.
     */
    private fun platformCapabilitySources(): List<File> =
        platformSources() + instrumentedPlatformSources() + platformUnitTestSources()

    private fun scannedSourceRoot(root: File, what: String): List<File> {
        if (!root.isDirectory) {
            fail(
                "Expected $what at '${root.invariantSeparatorsPath}'. A scan that silently finds " +
                    "nothing because a source set moved is a passing check that proves nothing.",
            )
        }
        return root.walk()
            .filter { file -> file.isFile && file.extension == "kt" }
            .toList()
            .also { sources ->
                if (sources.isEmpty()) {
                    fail("No Kotlin sources under '${root.invariantSeparatorsPath}'; the scan would be vacuous.")
                }
            }
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
            // Phase 6 (ADR-P6-008) authorised *client* transport opens, so `connectGatt`, `BluetoothGatt`,
            // `writeCharacteristic` and `createRfcommSocket*` no longer belong on this list — they are the
            // mechanism this phase exists to build, gated by BluetoothOperation.TRANSPORT_GATT_OPEN /
            // TRANSPORT_RFCOMM_OPEN (authorizedInPhase = 6) and confined to System*TransportHandle. What
            // stays forbidden is everything Phase 6 still may not do:
            "startDiscovery", // discovery/scanning is deferred (ADR-P5-012, ADR-P6-012): no scanner here
            "BluetoothLeScanner", "startScan",
            "listenUsingRfcomm", // server-side listen is not the client transport Phase 6 opens
            // Phase 25 authorises Quick Settings (TileService): the tile renders
            // GlobalDeviceState snapshots and dispatches through the existing
            // feature engine. AppWidgetProvider stays forbidden until Phase 27.
            // Phase 27 authorises the home-screen widget (AppWidgetProvider):
            // it renders GlobalDeviceState snapshots and dispatches through
            // the existing feature engine. NotificationListenerService stays
            // forbidden (Phase 28+).
            "NotificationListenerService",
            "androidx.appcompat", "androidx.compose", "setContentView", "ComponentActivity",
            // Phase 2 inspects permission standing and never asks for one (prompt section 5.3:
            // "Do not repeatedly trigger permission prompts"). Naming the request API here turns that
            // clause from a reading of the module into a check that fails the build.
            "requestPermissions", "requestPermission",
            // Phase 3's additions, each one a name that reads as a query and is not one. The research
            // put these forward by name (section 8.2): `fetchUuidsWithSdp` is an over-the-air service
            // discovery transaction, `startVoiceRecognition`/`stopVoiceRecognition` open and shut the
            // Bluetooth audio path, and `setPriorityPolicy` writes an LE Audio policy. Phase 6 opens a
            // control channel; it does none of these, and prompt section 11 keeps media audio with Android.
            "fetchUuidsWithSdp", "startVoiceRecognition", "stopVoiceRecognition", "setPriorityPolicy",
        )
        val pattern = Regex("\\b(${forbidden.joinToString("|")})\\b")
        val violations = platformCapabilitySources().flatMap { file ->
            codeLinesOf(file)
                .filter { line -> pattern.containsMatchIn(line) }
                .map { line -> "${labelOf(file)}: $line" }
        }

        assertClean(
            "Phase 2 authorises adapter inspection, permission status and capability reporting, and " +
                "Phase 3 adds read-only device observation: per-profile and attribute-protocol " +
                "enumeration of linked devices, connection and bond announcements, and the descriptor " +
                "cache reads beside them. Nothing else (Phase 2 prompt sections 5, 6, 7; Phase 3 prompt " +
                "sections 16, 17; research section 8).",
            "GATT or RFCOMM traffic, discovery, scanning, pairing, audio-path control, adapter power, UI, " +
                "and widgets belong to later phases (Quick Settings was authorised in Phase 25, " +
                "home-screen widgets in Phase 27). The instrumented source set is " +
                "scanned here too, because a test that can reach a refused " +
                "call is a module that can reach it (ADR-P3-007):",
            violations,
        )
    }

    /** Where a platform violation came from, so a failure names the source set that has to change. */
    private fun labelOf(file: File): String {
        val prefix = "platform/android/src/"
        val path = file.invariantSeparatorsPath.substringAfter(prefix)
        return path.substringBefore("/kotlin/").ifEmpty { "main" } + "/" + file.name
    }

    // ---- Rule 8: the media audio path is untouched ----------------------------

    @Test
    fun neitherModuleTouchesTheMediaAudioPath() {
        val forbidden = listOf("AudioRecord", "MediaCodec", "MediaExtractor", "MediaMuxer", "MediaSession")
        val pattern = Regex("\\b(${forbidden.joinToString("|")})\\b")

        val violations = (
            mainSources().map { file -> relativePath(file) to file } +
                platformCapabilitySources().map { file -> "platform/${file.name}" to file }
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
    //
    // `Intent` was in this list until Phase 2 put a broadcast receiver behind the boundary. The type is
    // Android's general inter-process message, and `ACTION_STATE_CHANGED` - which the prompt authorises
    // in section 5.4 - cannot be received without naming it, so keeping it banned here would have meant
    // either deleting the check or faking the mechanism. What the rule actually protects is the absence
    // of screens and of app-launched components, so the token list now names those things directly and
    // ADR-P2-016 records the split, with broadcast reception confined by rule 11 below.

    @Test
    fun neitherModuleReferencesUiFrameworks() {
        val forbidden = listOf(
            "Activity", "Fragment", "ViewModel", "ContextThemeWrapper",
            "setContentView", "startActivity",
            // Phase 26 authorises PendingIntent for notification actions only:
            // immutable, identity-keyed, validated as untrusted input.
            // `startActivity` above stays forbidden — actions use getBroadcast.
        )
        val pattern = Regex("\\b(${forbidden.joinToString("|")})\\b")

        val violations = (
            mainSources().map { file -> relativePath(file) to file } +
                platformCapabilitySources().map { file -> "platform/${file.name}" to file }
            )
            .flatMap { (label, file) ->
                // Phase 28: the lifecycle monitor implements
                // Application.ActivityLifecycleCallbacks, which names Activity
                // in its signatures. That is process-lifecycle observation,
                // not a UI screen — no activity is ever launched or shown.
                if ("lifecycle/" in file.invariantSeparatorsPath) return@flatMap emptyList<String>()
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

    // ---- Rule 10: the platform's broadcast use is only ever a listener ---------
    //
    // The substitute for the UI-token ban that adapter-state observation made impossible. Registering a
    // receiver is authorised in Phase 2; sending, exporting or answering an intent from another app is
    // not, and a component that could is exactly what a later phase must not add quietly.
    //
    // Phase 3 widened the *location* half of this rule and nothing else. `bluetooth/connection/` holds
    // the ACL, bond and per-profile connection-state receivers ADR-P3-008 made necessary, because the
    // platform has no list-connected-devices call and no callback a third-party app may reach, so a
    // listener outside the adapter package is not an extra capability but the same capability about a
    // different subject. The method name stays Phase 2's, since `docs/phases/phase-2` cites it by name in
    // five places and a rename would break the traceability the citations exist to provide. What did NOT
    // move: the senders stay banned everywhere, the token list is unchanged, and a receiver in any fourth
    // package still fails this check.
    @Test
    fun platformBroadcastUseIsConfinedToListeningForAdapterState() {
        val receiverTokens = listOf("BroadcastReceiver", "IntentFilter", "Intent", "registerReceiver")
        // Phase 26 authorises PendingIntent for notification actions only:
        // immutable, identity-keyed, validated as untrusted input.
        val neverTokens = listOf("sendBroadcast", "sendOrderedBroadcast", "LocalBroadcastManager")
        val receiverPattern = Regex("\\b(${receiverTokens.joinToString("|")})\\b")
        val neverPattern = Regex("\\b(${neverTokens.joinToString("|")})\\b")
        val authorisedListenerPackages = listOf(
            "bluetooth/adapter/",
            "bluetooth/connection/",
            // Phase 26: notification action receiver. Exported=false, intent
            // validated as untrusted input, dispatches through the feature
            // engine — never sends broadcasts or starts activities.
            // Phase 27: widget provider receiver. Exported=false; the launcher
            // binds through AppWidgetManager; actions dispatch through the
            // feature engine with full revalidation.
            "notification/",
            "widget/",
        )

        val outsideAdapter = platformSources().flatMap { file ->
            if (authorisedListenerPackages.any { path -> path in file.invariantSeparatorsPath }) {
                return@flatMap emptyList()
            }
            codeLinesOf(file)
                .filter { line -> receiverPattern.containsMatchIn(line) }
                .map { line -> "${file.name}: $line" }
        }
        val talkative = platformSources().flatMap { file ->
            codeLinesOf(file)
                .filter { line -> neverPattern.containsMatchIn(line) }
                .map { line -> "${file.name}: $line" }
        }

        assertClean(
            "Only the adapter-state and connected-device boundaries may receive broadcasts, and nothing " +
                "in the platform module may send one or hold a PendingIntent (Phase 2 prompt sections 5.4, " +
                "6; ADR-P2-016, widened in location only by ADR-P3-008).",
            "Broadcast capability outside its authorised scope:",
            outsideAdapter + talkative,
        )
    }

    // ---- Rule 11: platform sources and the manifest stay inside their envelope --

    @Test
    fun platformSourcesLiveOnlyUnderTheAuthorisedPackages() {
        val sources = platformSources()
        if (sources.isEmpty()) {
            fail(
                "The Android boundary has no sources, so this scan proves nothing. Phase 2 put the " +
                    "Bluetooth mechanism here; if it moved, fix the scan path rather than letting the " +
                    "rule pass by finding nothing.",
            )
        }

        val allowedRoots = listOf(
            "com/omnibuds/android/bluetooth/",
            "com/omnibuds/android/di/",
            // Phase 25: Quick Settings tile package.
            "com/omnibuds/android/tile/",
            // Phase 26: notification controls package.
            "com/omnibuds/android/notification/",
            // Phase 27: home-screen widget package.
            "com/omnibuds/android/widget/",
            // Phase 28: lifecycle monitor package.
            "com/omnibuds/android/lifecycle/",
            // Phase 32: platform compatibility policy package.
            "com/omnibuds/android/compat/",
        )
        val violations = sources
            .map { file -> file.invariantSeparatorsPath.substringAfter("kotlin/") }
            .filterNot { path -> allowedRoots.any { root -> path.startsWith(root) } }

        assertClean(
            "Platform code belongs below the packages Phase 2 opened (architecture audit section 2.3).",
            "A new top-level package is a boundary change and needs an ADR first:",
            violations,
        )
    }

    @Test
    fun platformManifestDeclaresNothingUnjustified() {
        val manifest = File("../platform/android/src/main/AndroidManifest.xml")
        if (!manifest.isFile) fail("Expected the platform boundary manifest at '${manifest.invariantSeparatorsPath}'.")

        val text = manifest.readText()
        val declared = Regex("<uses-permission[^>]*android:name=\"([^\"]+)\"")
            .findAll(text)
            .map { match -> match.groupValues[1] }
            .toList()
        val allowed = setOf("android.permission.BLUETOOTH_CONNECT")
        val components = listOf("<receiver", "<service", "<activity", "<provider")
            .filter { tag -> tag in text }

        // Phase 25 earns exactly one service: the Quick Settings tile.
        // The tile holds no Bluetooth connection and no state authority;
        // it renders GlobalDeviceState snapshots and dispatches through the
        // existing feature engine. BIND_QUICK_SETTINGS_TILE is the
        // platform-required permission for tile services.
        //
        // Phase 26 earns exactly one receiver: the notification action
        // receiver. Exported=false, intent-filter for the internal action
        // only. It validates every intent as untrusted input and dispatches
        // through the existing feature engine — never direct Bluetooth.
        //
        // Phase 27 earns exactly one more receiver: the home-screen widget
        // provider. Exported=false; the launcher binds through
        // AppWidgetManager. Actions dispatch through the feature engine
        // with full revalidation.
        val allowedServices = listOf(
            "com.omnibuds.android.tile.OmniBudsTileService",
            ".tile.OmniBudsTileService",
        )
        val allowedReceivers = listOf(
            "com.omnibuds.android.notification.OmniBudsNotificationReceiver",
            ".notification.OmniBudsNotificationReceiver",
            "com.omnibuds.android.widget.OmniBudsWidgetProvider",
            ".widget.OmniBudsWidgetProvider",
        )
        val unexpectedComponents = components.filterNot { tag ->
            (tag == "<service" && allowedServices.any { name -> name in text }) ||
                (tag == "<receiver" && allowedReceivers.any { name -> name in text })
        }

        // The tile service must carry the platform-required permission.
        val tilePermissionOk =
            "<service" !in text ||
                "android.permission.BIND_QUICK_SETTINGS_TILE" in text
        if (!tilePermissionOk) {
            fail("TileService must declare android.permission.BIND_QUICK_SETTINGS_TILE.")
        }

        // The notification receiver must not be exported.
        val receiverExportedOk =
            "<receiver" !in text ||
                Regex("<receiver[^>]*android:exported=\"true\"").find(text) == null
        if (!receiverExportedOk) {
            fail("OmniBudsNotificationReceiver must not be exported.")
        }

        assertClean(
            "A permission or component declared before the phase that uses it is a fabricated " +
                "capability (ADR-P2-011, ADR-P0-001; Phase 2 prompt section 5.3).",
            "Library manifest entries merge into every consumer silently, so over-declaration is " +
                "expensive in exactly the way a false capability claim is. Phase 3 earned exactly one " +
                "name, BLUETOOTH_CONNECT, because the enumeration and announcement calls it makes name " +
                "that permission themselves; BLUETOOTH_SCAN and the location permissions stay refused, " +
                "since this phase neither scans nor reads a location-derived broadcast, and a component " +
                "stays refused because every receiver here is context-registered and owned by the object " +
                "that opened it (ADR-P3-012):",
            declared.filterNot { name -> name in allowed } + unexpectedComponents,
        )
    }

    // ---- Rule 12: module dependency direction is one way ----------------------

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
