package com.omnibuds.core.feature

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Phase 9's scope guards, machine-checked.
 *
 * The phase exists to establish the feature-control *architecture*, not to
 * control hardware: no production source may implement the protocol seam (the
 * only implementations are test-only scripted ports), no vendor command or
 * brand-specific behaviour may hide in main sources, and the engine may not
 * reach past its seam into the transport or protocol layers. These checks fail
 * loudly when their inputs move, so a passing suite that scanned nothing is
 * impossible.
 */
class PhaseNineScopeTest {

    private val featureMainRoot = File("src/main/kotlin/com/omnibuds/core/feature")

    private val configMainRoot = File("src/main/kotlin/com/omnibuds/core/config")

    private fun mainFeatureSources(): List<File> {
        if (!featureMainRoot.isDirectory) {
            fail("Expected the feature area at '${featureMainRoot.invariantSeparatorsPath}'.")
        }
        return featureMainRoot.walk()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
            .also { sources ->
                if (sources.isEmpty()) fail("No feature main sources found; the scan would be vacuous.")
            }
    }

    private fun importsOf(file: File): List<String> =
        file.readLines().mapNotNull { line ->
            Regex("^import\\s+([A-Za-z0-9_.]+)").find(line.trim())?.groupValues?.get(1)
        }

    private fun codeOf(file: File): String =
        file.readLines()
            .map { it.trim() }
            .filter { line ->
                line.isNotEmpty() &&
                    !line.startsWith("//") &&
                    !line.startsWith("*") &&
                    !line.startsWith("/*")
            }
            .joinToString("\n")

    @Test
    fun noProductionSourceImplementsTheFeatureProtocolPort() {
        val pattern = Regex("^\\s*(class|object)\\s+\\w+[^{]*:\\s*(?:[A-Za-z0-9_.]*\\.)?FeatureProtocolPort\\b")
        val violations = mainFeatureSources().flatMap { file ->
            file.readLines()
                .filter { line -> pattern.containsMatchIn(line) }
                .map { line -> "${file.name}: $line" }
        }
        assertTrue(
            violations.isEmpty(),
            "Production code must not implement FeatureProtocolPort — the only implementations " +
                "are test-only scripted ports (Phase 7/8 precedent):\n" + violations.joinToString("\n"),
        )
    }

    @Test
    fun theFeatureAreaDoesNotImportTheProtocolLayer() {
        // The engine drives the handed-in FeatureProtocolPort seam; importing the
        // protocol layer would let it bypass the abstractions (ADR-P9-002).
        val violations = mainFeatureSources().flatMap { file ->
            importsOf(file)
                .filter { it.startsWith("com.omnibuds.core.protocol") }
                .map { import -> "${file.name} imports $import" }
        }
        assertTrue(
            violations.isEmpty(),
            "The feature area must reach protocols only through the port seam:\n" +
                violations.joinToString("\n"),
        )
    }

    @Test
    fun theFeatureAreaDoesNotReachPastThePortIntoTransport() {
        val violations = mainFeatureSources().flatMap { file ->
            importsOf(file)
                .filter { it.startsWith("com.omnibuds.core.transport") }
                .map { import -> "${file.name} imports $import" }
        }
        assertTrue(
            violations.isEmpty(),
            "The feature engine must not see the transport layer; the port hides it:\n" +
                violations.joinToString("\n"),
        )
    }

    @Test
    fun noVendorBrandNamesAppearInMainSources() {
        // Illustrative vendor names from prompts and docs must never harden into
        // main-source vocabulary. Tests use only the "example-vendor" placeholder.
        val brands = listOf("sony", "bose", "jbl", "samsung", "apple", "airpods", "soundcore", "anker")
        val pattern = Regex("\\b(${brands.joinToString("|")})\\b", RegexOption.IGNORE_CASE)
        val violations = mainFeatureSources().flatMap { file ->
            codeOf(file).split("\n")
                .filter { line -> pattern.containsMatchIn(line) }
                .map { line -> "${file.name}: $line" }
        }
        assertTrue(
            violations.isEmpty(),
            "Brand names must not appear in feature main sources — shared code never " +
                "branches on a manufacturer (ADR-P0-007):\n" + violations.joinToString("\n"),
        )
    }

    @Test
    fun noFakeHardwareBehaviourExistsInMainSources() {
        // The engine reports device truth; it never invents it. Markers of
        // simulation in production code are refused.
        val markers = listOf("simulate", "fakeDevice", "mockHardware", "pretend")
        val pattern = Regex("\\b(${markers.joinToString("|")})\\b", RegexOption.IGNORE_CASE)
        val violations = mainFeatureSources().flatMap { file ->
            codeOf(file).split("\n")
                .filter { line -> pattern.containsMatchIn(line) }
                .map { line -> "${file.name}: $line" }
        }
        assertTrue(
            violations.isEmpty(),
            "Simulation markers must not appear in feature main sources:\n" +
                violations.joinToString("\n"),
        )
    }

    @Test
    fun newConfigurationValueShapesStayInTheConfigArea() {
        // ADR-P9-001: value shapes are extended on ConfigurationValue, not forked.
        // The new shapes must live beside the existing ones, in the config area.
        if (!configMainRoot.isDirectory) {
            fail("Expected the config area at '${configMainRoot.invariantSeparatorsPath}'.")
        }
        val configSources = configMainRoot.walk()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
        val shapes = listOf("FloatValue", "RangeValue", "StructuredValue", "BitmaskValue", "CustomValue")
        for (shape in shapes) {
            val found = configSources.any { file -> codeOf(file).contains(shape) }
            assertTrue(found, "$shape must be declared in the config area, not forked elsewhere")
        }
        val featureShapes = mainFeatureSources().flatMap { file ->
            codeOf(file).split("\n")
                .filter { line ->
                    shapes.any { shape -> Regex("\\bdata class $shape\\b").containsMatchIn(line) }
                }
                .map { line -> "${file.name}: $line" }
        }
        assertTrue(
            featureShapes.isEmpty(),
            "Value shapes must not be redeclared in the feature area:\n" + featureShapes.joinToString("\n"),
        )
    }
}
