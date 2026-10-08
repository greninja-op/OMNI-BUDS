package com.omnibuds.core.audio

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Machine-checked Phase 10 scope boundaries.
 *
 * Phase 10 is observation only. These tests fail the build if a later commit
 * smuggles media-audio processing, codec configuration, routing control, or
 * Android framework coupling into the audio transport engine. They complement
 * DependencyDirectionTest (which owns the layer graph) by owning the
 * vocabulary bans specific to this phase (OB-P10-REQ-003, OB-P10-REQ-011,
 * OB-P10-REQ-020).
 */
class PhaseTenScopeTest {

    private val audioMainRoot = File("src/main/kotlin/com/omnibuds/core/audio")

    private fun audioSources(): List<File> {
        if (!audioMainRoot.isDirectory) {
            fail("Phase 10 audio sources missing at ${audioMainRoot.path}")
        }
        return audioMainRoot.walk()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
            .also { if (it.isEmpty()) fail("No audio sources found; the scan would be vacuous.") }
    }

    /** Comment lines and KDoc stripped: documenting a ban is not violating it. */
    private fun codeOf(file: File): String {
        val text = file.readText()
        // Strip block comments and line comments crudely but sufficiently for
        // vocabulary scanning.
        return text
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("//.*"), "")
    }

    @Test
    fun noAndroidImportsInCoreAudio() {
        val violations = audioSources().filter { file ->
            codeOf(file).contains(Regex("^import\\s+android\\.", RegexOption.MULTILINE))
        }
        assertTrue(
            violations.isEmpty(),
            "Android framework imports in core audio: ${violations.map { it.name }}. " +
                "The core stays platform-independent; Android lives in platform/android.",
        )
    }

    @Test
    fun noMediaCaptureVocabulary() {
        // OmniBuds never captures, decodes, re-encodes or intercepts media audio.
        val banned = listOf(
            "AudioRecord", "MediaRecorder", "MediaProjection", "AudioCapture",
            "startRecording", "readAudio", "decodeAudio", "encodeAudio",
        )
        val violations = audioSources().flatMap { file ->
            val code = codeOf(file)
            banned.filter { word -> code.contains(word) }.map { word -> "${file.name}: $word" }
        }
        assertTrue(
            violations.isEmpty(),
            "Media-audio processing vocabulary in the audio engine: $violations. " +
                "Phase 10 observes transport state; it never touches the media stream.",
        )
    }

    @Test
    fun noCodecConfigurationVocabulary() {
        // Codec selection/configuration is Phase 11. The engine may name
        // transports, never configure codecs.
        val banned = listOf(
            "setCodec", "selectCodec", "codecPriority", "setCodecPriority",
            "configureCodec", "enableCodec", "disableCodec",
        )
        val violations = audioSources().flatMap { file ->
            val code = codeOf(file)
            banned.filter { word -> code.contains(word) }.map { word -> "${file.name}: $word" }
        }
        assertTrue(
            violations.isEmpty(),
            "Codec-configuration vocabulary in the audio engine: $violations. " +
                "Codec work belongs to Phase 11.",
        )
    }

    @Test
    fun noRoutingControlVocabulary() {
        // Observation is not control: no routing APIs, even by name.
        val banned = listOf(
            "setCommunicationDevice", "startBluetoothSco", "stopBluetoothSco",
            "setBluetoothScoOn", "setSpeakerphoneOn",
        )
        val violations = audioSources().flatMap { file ->
            val code = codeOf(file)
            banned.filter { word -> code.contains(word) }.map { word -> "${file.name}: $word" }
        }
        assertTrue(
            violations.isEmpty(),
            "Routing-control vocabulary in the audio engine: $violations. " +
                "Android owns the audio route; OmniBuds observes it.",
        )
    }

    @Test
    fun noMicrophonePermissionVocabulary() {
        // RECORD_AUDIO must never be requested merely to observe transport state.
        val violations = audioSources().filter { file ->
            codeOf(file).contains("RECORD_AUDIO")
        }
        assertTrue(
            violations.isEmpty(),
            "RECORD_AUDIO referenced in core audio: ${violations.map { it.name }}. " +
                "Transport observation needs no microphone access.",
        )
    }

    @Test
    fun noVendorProtocolLiterals() {
        // The audio engine knows nothing of vendor commands or packet formats.
        val banned = listOf("0x", "GATT_WRITE", "vendorCommand", "opcode")
        val violations = audioSources().flatMap { file ->
            val code = codeOf(file)
            banned.filter { word -> code.contains(word) }.map { word -> "${file.name}: $word" }
        }
        assertTrue(
            violations.isEmpty(),
            "Vendor-protocol vocabulary in the audio engine: $violations.",
        )
    }

    @Test
    fun engineOwnsExactlyOneSnapshotFlow() {
        // One authoritative snapshot store (OB-P10-REQ-013): the engine file
        // must declare the snapshots StateFlow, and no other audio file may
        // declare a competing MutableStateFlow of snapshots.
        val engineFile = File(audioMainRoot, "AudioTransportEngine.kt")
        assertTrue(engineFile.isFile, "AudioTransportEngine.kt missing")
        val engineCode = codeOf(engineFile)
        assertTrue(
            engineCode.contains("MutableStateFlow(AudioTransportSnapshot"),
            "The engine must own the single authoritative snapshot StateFlow.",
        )
        val others = audioSources()
            .filter { it.name != "AudioTransportEngine.kt" }
            .filter { codeOf(it).contains("MutableStateFlow(AudioTransportSnapshot") }
        assertTrue(
            others.isEmpty(),
            "Competing snapshot stores: ${others.map { it.name }}. One engine, one flow.",
        )
    }

    @Test
    fun reconcilerStaysAPureFunction() {
        // The reconciler takes all inputs as parameters: no clock reads, no
        // randomness, no I/O. Deterministic and unit-testable.
        val reconciler = File(audioMainRoot, "AudioReconciler.kt")
        assertTrue(reconciler.isFile, "AudioReconciler.kt missing")
        val code = codeOf(reconciler)
        assertFalse(
            code.contains("System.currentTimeMillis") || code.contains("Clock.System"),
            "The reconciler must not read the clock; timestamps arrive as parameters.",
        )
        assertFalse(
            code.contains("kotlin.random") || code.contains("Random.next"),
            "The reconciler must not use randomness.",
        )
    }
}
