package com.omnibuds.core.testkit.crossdevice

import com.omnibuds.core.testkit.TestCase
import com.omnibuds.core.testkit.TestCategory
import com.omnibuds.core.testkit.TestEvidenceLevel
import com.omnibuds.core.testkit.TestResult
import com.omnibuds.core.testkit.TestResultCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private fun profile(
    id: String = "p1",
    evidence: ProfileEvidence = ProfileEvidence.SYNTHETIC,
    capabilities: Set<String> = emptySet(),
    manufacturer: String? = null,
    model: String? = null,
) = DeviceProfile(
    profileId = id,
    manufacturer = manufacturer,
    model = model,
    capabilities = capabilities,
    evidence = evidence,
)

private fun test(
    id: String,
    tags: Set<String> = emptySet(),
    fixtures: List<String> = emptyList(),
) = TestCase(id, "name $id", TestCategory.UNIT, tags = tags, fixtures = fixtures)

private fun result(id: String, category: TestResultCategory) =
    TestResult(id, "s1", category)

class DeviceProfileTest {

    @Test
    fun `valid synthetic profile passes`() {
        assertTrue(DeviceProfileValidator.validate(profile()) is ProfileValidation.Valid)
    }

    @Test
    fun `unknown values are preserved`() {
        val p = profile()
        assertTrue(p.manufacturer == null && p.model == null && p.firmwareVersion == null)
    }

    @Test
    fun `hardware-verified profile needs identity`() {
        val p = profile(evidence = ProfileEvidence.HARDWARE_VERIFIED)
        val v = DeviceProfileValidator.validate(p)
        assertTrue(v is ProfileValidation.Invalid)
        val identified = profile(
            evidence = ProfileEvidence.HARDWARE_VERIFIED,
            manufacturer = "acme",
            model = "buds-1",
        )
        assertTrue(DeviceProfileValidator.validate(identified) is ProfileValidation.Valid)
    }

    @Test
    fun `unsupported schema version fails`() {
        val p = profile().copy(schemaVersion = 99)
        assertTrue(DeviceProfileValidator.validate(p) is ProfileValidation.Invalid)
    }
}

class CampaignPlannerTest {

    @Test
    fun `capability filter marks not-applicable`() {
        val t = test("t1", tags = setOf("requires-capability:anc"))
        val plan = CampaignPlanner.plan("c1", listOf(profile()), listOf(t))
        assertEquals(TestDisposition.NOT_APPLICABLE, plan.planned[0].disposition)
        assertTrue(plan.executable.isEmpty())
    }

    @Test
    fun `capable profile executes`() {
        val t = test("t1", tags = setOf("requires-capability:anc"))
        val plan = CampaignPlanner.plan(
            "c1",
            listOf(profile(capabilities = setOf("anc"))),
            listOf(t),
        )
        assertEquals(TestDisposition.EXECUTED, plan.planned[0].disposition)
    }

    @Test
    fun `missing fixture blocks`() {
        val t = test("t1", fixtures = listOf("missing-fixture"))
        val plan = CampaignPlanner.plan("c1", listOf(profile()), listOf(t))
        assertEquals(TestDisposition.BLOCKED, plan.planned[0].disposition)
    }

    @Test
    fun `planning is deterministic`() {
        val profiles = listOf(profile("b"), profile("a"))
        val tests = listOf(test("t2"), test("t1"))
        val p1 = CampaignPlanner.plan("c1", profiles, tests)
        val p2 = CampaignPlanner.plan("c1", profiles.reversed(), tests.reversed())
        assertEquals(
            p1.planned.map { it.testCase.id to it.profile.profileId },
            p2.planned.map { it.testCase.id to it.profile.profileId },
        )
    }
}

class CompatibilityEvaluatorTest {

    @Test
    fun `all synthetic passes are synthetic-only`() {
        val p = profile()
        val t = test("t1")
        val planned = listOf(PlannedTest(t, p, TestDisposition.EXECUTED))
        val verdict = CompatibilityEvaluator.evaluate(p, planned, listOf(result("t1", TestResultCategory.PASSED)))
        assertEquals(CompatibilityClass.SYNTHETIC_ONLY, verdict.classification)
        assertEquals(TestEvidenceLevel.SIMULATED, verdict.evidenceLevel)
    }

    @Test
    fun `any failure means incompatible`() {
        val p = profile()
        val planned = listOf(
            PlannedTest(test("t1"), p, TestDisposition.EXECUTED),
            PlannedTest(test("t2"), p, TestDisposition.EXECUTED),
        )
        val verdict = CompatibilityEvaluator.evaluate(
            p, planned,
            listOf(
                result("t1", TestResultCategory.PASSED),
                result("t2", TestResultCategory.FAILED),
            ),
        )
        assertEquals(CompatibilityClass.INCOMPATIBLE, verdict.classification)
    }

    @Test
    fun `skipped tests mean partial validation`() {
        val p = profile()
        val planned = listOf(
            PlannedTest(test("t1"), p, TestDisposition.EXECUTED),
            PlannedTest(test("t2"), p, TestDisposition.SKIPPED, "no fixture"),
        )
        val verdict = CompatibilityEvaluator.evaluate(
            p, planned, listOf(result("t1", TestResultCategory.PASSED)),
        )
        assertEquals(CompatibilityClass.PARTIALLY_VALIDATED, verdict.classification)
    }

    @Test
    fun `no executable tests means not-applicable`() {
        val p = profile()
        val planned = listOf(
            PlannedTest(test("t1"), p, TestDisposition.NOT_APPLICABLE, "no cap"),
        )
        val verdict = CompatibilityEvaluator.evaluate(p, planned, emptyList())
        assertEquals(CompatibilityClass.NOT_APPLICABLE, verdict.classification)
    }

    @Test
    fun `scope includes model firmware and protocol`() {
        val p = DeviceProfile(
            profileId = "p1",
            manufacturer = "acme",
            model = "buds-1",
            firmwareVersion = "2.1.0",
            protocolVersion = "1.4",
        )
        val verdict = CompatibilityEvaluator.evaluate(p, emptyList(), emptyList())
        assertTrue(verdict.scope.contains("acme"))
        assertTrue(verdict.scope.contains("buds-1"))
        assertTrue(verdict.scope.contains("2.1.0"))
        assertTrue(verdict.scope.contains("1.4"))
    }

    @Test
    fun `hardware-verified profile with passes is verified-compatible`() {
        val p = profile(
            evidence = ProfileEvidence.HARDWARE_VERIFIED,
            manufacturer = "acme",
            model = "buds-1",
        )
        val planned = listOf(PlannedTest(test("t1"), p, TestDisposition.EXECUTED))
        val verdict = CompatibilityEvaluator.evaluate(
            p, planned, listOf(result("t1", TestResultCategory.PASSED)),
        )
        assertEquals(CompatibilityClass.VERIFIED_COMPATIBLE, verdict.classification)
        assertEquals(TestEvidenceLevel.HARDWARE_OBSERVED, verdict.evidenceLevel)
    }
}

class RegressionComparatorTest {

    private fun baseline(vararg pairs: Pair<String, TestResultCategory>) =
        RegressionBaseline(
            baselineId = "b1",
            sourceRevision = "abc123",
            profileVersions = mapOf("p1" to 1),
            fixtureVersions = emptyMap(),
            runnerVersion = "1",
            expected = pairs.toMap(),
        )

    @Test
    fun `new failure is a regression`() {
        val findings = RegressionComparator.compare(
            baseline("t1" to TestResultCategory.PASSED),
            listOf(result("t1", TestResultCategory.FAILED)),
        )
        assertTrue(findings.any { it is RegressionFinding.NewFailure })
        assertTrue(RegressionComparator.hasRegressions(findings))
    }

    @Test
    fun `identical results have no findings`() {
        val findings = RegressionComparator.compare(
            baseline("t1" to TestResultCategory.PASSED),
            listOf(result("t1", TestResultCategory.PASSED)),
        )
        assertTrue(findings.isEmpty())
        assertFalse(RegressionComparator.hasRegressions(findings))
    }

    @Test
    fun `unbaselined test is flagged`() {
        val findings = RegressionComparator.compare(
            baseline(),
            listOf(result("t9", TestResultCategory.PASSED)),
        )
        assertTrue(findings.any { it is RegressionFinding.Unbaselined })
        assertFalse(RegressionComparator.hasRegressions(findings))
    }

    @Test
    fun `new skip is flagged but not a regression`() {
        val findings = RegressionComparator.compare(
            baseline("t1" to TestResultCategory.PASSED),
            listOf(result("t1", TestResultCategory.SKIPPED)),
        )
        assertTrue(findings.any { it is RegressionFinding.NewSkipOrBlock })
        assertFalse(RegressionComparator.hasRegressions(findings))
    }
}
