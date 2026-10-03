package com.omnibuds.core.device

import com.omnibuds.core.state.VerificationLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The ADR-P5-006 discipline that the shipped registry is empty, that no `src/main` source declares a
 * rule, and that a rule refuses to be built from invented evidence. Prompt §11's "do not invent real
 * device signatures / do not add models to grow the registry" made data, not prose.
 *
 * Tier T1.
 */
class PhaseFiveRegistryTest {

    @Test
    fun theProductionRegistryShipsEmpty() {
        val registry = DeviceIdentityRegistry.empty()

        assertTrue(registry.rules.isEmpty(), "Phase 5 documented no device signatures; a rule here is invented")
        assertEquals(
            listOf(ManufacturerIdentity.UNKNOWN_ID),
            registry.manufacturers.map { entry -> entry.canonicalId },
        )
    }

    @Test
    fun noMainSourceDeclaresARuleOrAMatchCondition() {
        // The emptiness above is a companion-object fact a caller could bypass by building a populated
        // registry inline. This scan is the reason the first real rule must arrive with a citation and
        // a diff, not smuggled into a DI module.
        //
        // It looks for *constructions* (a call site has an opening paren), never type references: the
        // registry file declares `data class IdentificationRule(...)` and the engine writes
        // `is MatchCondition.NormalizedNameEquals ->`, both of which name the type without building a
        // device signature. Comments and declaration lines are stripped, mirroring PhaseFourScopeTest.
        val root = mainKotlinRoot()

        val offenders = root.walkTopDown()
            .filter { file -> file.isFile && file.extension == "kt" }
            .flatMap { file ->
                codeLines(file)
                    .filterNot { line -> line.startsWith("class ") || line.startsWith("data class ") }
                    .filter { line -> RULE_CONSTRUCTION.any { token -> line.contains(token) } }
                    .map { line -> "${file.name}: $line" }
            }
            .toList()

        assertEquals(emptyList(), offenders, "a Phase 5 identity rule is constructed outside the test fixtures")
    }

    @Test
    fun aRuleBuiltFromInventedEvidenceIsRefused() {
        // ASSIGNED_INTERNALLY is the shape a fabricated signature takes; the constructor refuses it.
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            IdentificationRule(
                ruleId = "made-up",
                manufacturer = ManufacturerIdentity("example", "Example"),
                model = null,
                conditions = listOf(MatchCondition.NormalizedNameEquals("example buds")),
                confidence = IdentificationConfidence.MODERATE,
                ruleVersion = 1,
                evidence = EvidenceSource.ASSIGNED_INTERNALLY,
                evidenceReference = "we felt like it",
                knownLimitations = "no citation",
            )
        }
    }

    @Test
    fun aRuleAskingForVerifiedConfidenceIsRefused() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            IdentificationRule(
                ruleId = "overclaiming",
                manufacturer = ManufacturerIdentity("example", "Example"),
                model = null,
                conditions = listOf(MatchCondition.NormalizedNameEquals("example buds")),
                confidence = IdentificationConfidence.VERIFIED,
                ruleVersion = 1,
                evidence = EvidenceSource.VENDOR_PUBLICATION,
                evidenceReference = "fixture",
                knownLimitations = "fixture",
            )
        }
    }

    @Test
    fun aRuleWithNoConditionsIsRefusedBecauseItWouldMatchEverything() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            IdentificationRule(
                ruleId = "matches-all",
                manufacturer = ManufacturerIdentity("example", "Example"),
                model = null,
                conditions = emptyList(),
                confidence = IdentificationConfidence.LOW,
                ruleVersion = 1,
                evidence = EvidenceSource.VENDOR_PUBLICATION,
                evidenceReference = "fixture",
                knownLimitations = "fixture",
            )
        }
    }

    @Test
    fun verifiedIsDeclaredButUnreachableFromThisPhase() {
        // ADR-P5-004: VERIFIED needs hardware evidence the deferred device work is the only source of.
        assertFalse(IdentificationConfidence.VERIFIED.reachableInPhaseFive)
        assertEquals(VerificationLevel.HARDWARE_VERIFIED, IdentificationConfidence.VERIFIED.minimumEvidence)
        assertTrue(IdentificationConfidence.HIGH.reachableInPhaseFive)
    }

    private fun codeLines(file: java.io.File): List<String> =
        file.readLines().map { line -> line.trim() }.filter { line ->
            line.isNotEmpty() &&
                !line.startsWith("//") &&
                !line.startsWith("*") &&
                !line.startsWith("/*")
        }

    private fun mainKotlinRoot(): java.io.File {
        val root = java.io.File("src/main/kotlin/com/omnibuds/core")
        assertTrue(root.isDirectory, "the core main sources moved: ${root.invariantSeparatorsPath}")
        assertTrue(root.walkTopDown().any { file -> file.isFile && file.extension == "kt" }, "scan would be vacuous")
        return root
    }

    private companion object {
        /** Call sites only: the opening paren distinguishes building a rule from naming its type. */
        val RULE_CONSTRUCTION = listOf("IdentificationRule(", "MatchCondition.NormalizedNameEquals(")
    }
}
