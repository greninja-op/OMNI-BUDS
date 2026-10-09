package com.omnibuds.core.testkit.crossdevice

import com.omnibuds.core.testkit.TestEvidenceLevel
import com.omnibuds.core.testkit.TestResult
import com.omnibuds.core.testkit.TestResultCategory

/**
 * Compatibility classification for one profile.
 *
 * Phase 31 (OB-P31-REQ-005): never a bare Boolean. Every classification
 * carries its scope and evidence.
 */
enum class CompatibilityClass {
    /** Verified within the declared scope. */
    VERIFIED_COMPATIBLE,

    /** Passes under synthetic fixtures only. */
    SYNTHETIC_ONLY,

    /** Some suites pass; validation incomplete. */
    PARTIALLY_VALIDATED,

    /** Demonstrated incompatible. */
    INCOMPATIBLE,

    /** Not enough evidence to classify. */
    UNKNOWN,

    /** Blocked by missing evidence/infrastructure. */
    BLOCKED,

    /** No applicable tests for this profile. */
    NOT_APPLICABLE,
}

/**
 * A compatibility verdict for one profile.
 */
data class CompatibilityVerdict(
    val profileId: String,
    val classification: CompatibilityClass,
    /** Exact scope: model, firmware, protocol version tested. */
    val scope: String,
    val passed: Int,
    val failed: Int,
    val skipped: Int,
    val blocked: Int,
    val notApplicable: Int,
    val evidenceLevel: TestEvidenceLevel,
    val limitations: List<String>,
)

/**
 * Deterministic compatibility evaluator.
 *
 * Phase 31: synthetic evidence never becomes hardware verification
 * (OB-P31-REQ-007); skipped tests are never passes.
 */
object CompatibilityEvaluator {

    /**
     * Evaluate one profile from its planned tests and results.
     */
    fun evaluate(
        profile: DeviceProfile,
        planned: List<PlannedTest>,
        results: List<TestResult>,
    ): CompatibilityVerdict {
        val byTest = results.associateBy { it.testId }
        var passed = 0
        var failed = 0
        var skipped = 0
        var blocked = 0
        var notApplicable = 0
        val limitations = mutableListOf<String>()

        for (p in planned) {
            when (p.disposition) {
                TestDisposition.NOT_APPLICABLE -> notApplicable++
                TestDisposition.SKIPPED -> {
                    skipped++
                    limitations.add("skipped ${p.testCase.id}: ${p.reason}")
                }
                TestDisposition.BLOCKED -> {
                    blocked++
                    limitations.add("blocked ${p.testCase.id}: ${p.reason}")
                }
                TestDisposition.EXECUTED -> {
                    when (byTest[p.testCase.id]?.category) {
                        TestResultCategory.PASSED -> passed++
                        TestResultCategory.FAILED -> failed++
                        TestResultCategory.SKIPPED -> skipped++
                        TestResultCategory.BLOCKED,
                        TestResultCategory.INFRASTRUCTURE_ERROR,
                        -> blocked++
                        TestResultCategory.CANCELLED,
                        TestResultCategory.INVALID_DEFINITION,
                        null,
                        -> {
                            blocked++
                            limitations.add("no valid result for ${p.testCase.id}")
                        }
                    }
                }
            }
        }

        val scope = listOfNotNull(
            profile.manufacturer,
            profile.model,
            profile.firmwareVersion?.let { "fw=$it" },
            profile.protocolVersion?.let { "proto=$it" },
        ).joinToString("/").ifEmpty { "unidentified profile" }

        val evidenceLevel = when (profile.evidence) {
            ProfileEvidence.SYNTHETIC, ProfileEvidence.FIXTURE_DERIVED ->
                TestEvidenceLevel.SIMULATED
            ProfileEvidence.LAB_TESTED -> TestEvidenceLevel.DOCUMENTED
            ProfileEvidence.HARDWARE_VERIFIED, ProfileEvidence.PERSISTENCE_VERIFIED ->
                TestEvidenceLevel.HARDWARE_OBSERVED
        }

        val classification = when {
            failed > 0 -> CompatibilityClass.INCOMPATIBLE
            planned.none { it.disposition == TestDisposition.EXECUTED } ->
                CompatibilityClass.NOT_APPLICABLE
            blocked > 0 -> CompatibilityClass.BLOCKED
            passed == 0 -> CompatibilityClass.UNKNOWN
            skipped > 0 -> CompatibilityClass.PARTIALLY_VALIDATED
            profile.evidence == ProfileEvidence.SYNTHETIC ||
                profile.evidence == ProfileEvidence.FIXTURE_DERIVED ->
                CompatibilityClass.SYNTHETIC_ONLY
            else -> CompatibilityClass.VERIFIED_COMPATIBLE
        }

        return CompatibilityVerdict(
            profileId = profile.profileId,
            classification = classification,
            scope = scope,
            passed = passed,
            failed = failed,
            skipped = skipped,
            blocked = blocked,
            notApplicable = notApplicable,
            evidenceLevel = evidenceLevel,
            limitations = limitations.sorted(),
        )
    }
}
