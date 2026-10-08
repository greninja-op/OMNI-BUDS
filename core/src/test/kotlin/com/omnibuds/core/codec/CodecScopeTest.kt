package com.omnibuds.core.codec

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Phase 11 architectural scope guard: the codec package is observation-only.
 *
 * Machine-checked prohibitions:
 * - no codec switching/forcing vocabulary,
 * - no media-audio interception vocabulary,
 * - no Android framework imports in core codec code,
 * - no fake-hardware claims (production code reports only observed facts).
 */
class CodecScopeTest {

    private fun codecSources(): List<File> {
        val root = File("src/main/kotlin/com/omnibuds/core/codec")
        require(root.isDirectory) { "codec package not found at ${root.absolutePath}" }
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private fun audioCodecSources(): List<File> {
        val root = File("src/main/kotlin/com/omnibuds/core/audio")
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.name.startsWith("Codec") || it.name.startsWith("Evidence") }
            .toList()
    }

    @Test
    fun noCodecSwitchingVocabulary() {
        val banned = listOf(
            "switchCodec", "forceCodec", "setCodec", "selectCodec",
            "switch_codec", "force_codec",
            "LDAC_MODE", "setQualityMode", "forceQuality",
        )
        val hits = (codecSources() + audioCodecSources()).flatMap { file ->
            banned.filter { word -> word in file.readText() }.map { word -> "${file.name}: $word" }
        }
        assertTrue(hits.isEmpty(), "codec switching vocabulary forbidden: $hits")
    }

    @Test
    fun noMediaInterceptionVocabulary() {
        val banned = listOf(
            "MediaProjection", "AudioRecord", "AudioCapture",
            "intercept", "pcm", "PCM", "decode(", "re-encod", "reencod",
            "proxyAudio", "RECORD_AUDIO",
        )
        val hits = (codecSources() + audioCodecSources()).flatMap { file ->
            banned.filter { word -> word in file.readText() }.map { word -> "${file.name}: $word" }
        }
        assertTrue(hits.isEmpty(), "media interception vocabulary forbidden: $hits")
    }

    @Test
    fun noAndroidImportsInCoreCodec() {
        val hits = codecSources().filter { "import android." in it.readText() }
        assertTrue(hits.isEmpty(), "android imports forbidden in core codec: ${hits.map { it.name }}")
    }

    @Test
    fun noHardcodedBitrateInvention() {
        // A bitrate literal tied to a codec identity would be invention.
        // (Exact() constructions in tests are fine; this scans production.)
        val hits = codecSources().flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                val lower = line.lowercase()
                if (("ldac" in lower || "aptx" in lower) && "990" in line && "test" !in file.name.lowercase()) {
                    "${file.name}:${index + 1}"
                } else {
                    null
                }
            }
        }
        assertTrue(hits.isEmpty(), "invented bitrates forbidden: $hits")
    }
}
