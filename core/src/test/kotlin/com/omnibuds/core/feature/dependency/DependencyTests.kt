package com.omnibuds.core.feature.dependency

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.feature.FeatureRelation
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private val ANC = FeatureId.of("noise-control", "anc")
private val TRANSPARENCY = FeatureId.of("noise-control", "transparency")
private val SPATIAL = FeatureId.of("audio", "spatial")
private val HEAD_TRACKING = FeatureId.of("audio", "head-tracking")

private fun rule(
    relation: FeatureRelation,
    verification: RelationshipVerification = RelationshipVerification.HARDWARE_VERIFIED,
    ruleId: String = "rule-${relation.hashCode()}",
    superseded: Boolean = false,
) = RelationshipRule(
    relation,
    RelationshipProvenance(
        ruleId = ruleId,
        verification = verification,
        scope = RelationshipScope(),
        superseded = superseded,
    ),
)

private fun validGraph(vararg rules: RelationshipRule): RelationshipGraph {
    val result = RelationshipGraph.build(rules.toList())
    assertTrue(result is GraphBuildResult.Valid, "expected valid graph")
    return (result as GraphBuildResult.Valid).graph
}

private fun config(
    enable: Set<FeatureId>,
    availability: Map<FeatureId, FeatureAvailability> = emptyMap(),
) = ProposedConfiguration("d1", enable, availability)

class DependencyProvenanceTest {

    @Test
    fun `superseded rules are inert`() {
        val r = rule(
            FeatureRelation.Requires(HEAD_TRACKING, SPATIAL),
            superseded = true,
        )
        assertFalse(r.isActiveFor(null, null, null))
    }

    @Test
    fun `scope mismatch deactivates the rule`() {
        val r = RelationshipRule(
            FeatureRelation.Requires(HEAD_TRACKING, SPATIAL),
            RelationshipProvenance(
                ruleId = "r1",
                verification = RelationshipVerification.HARDWARE_VERIFIED,
                scope = RelationshipScope(manufacturer = "acme"),
            ),
        )
        assertFalse(r.isActiveFor("other", null, null))
        assertTrue(r.isActiveFor("acme", null, null))
    }

    @Test
    fun `inferred verification never blocks`() {
        assertFalse(RelationshipVerification.INFERRED.mayBlock)
        assertFalse(RelationshipVerification.IMPLEMENTED.mayBlock)
        assertFalse(RelationshipVerification.LAB_TESTED.mayBlock)
        assertTrue(RelationshipVerification.HARDWARE_VERIFIED.mayBlock)
        assertTrue(RelationshipVerification.PERSISTENCE_VERIFIED.mayBlock)
    }
}

class DependencyGraphTest {

    @Test
    fun `duplicate rule ids are rejected`() {
        val r1 = rule(FeatureRelation.Requires(HEAD_TRACKING, SPATIAL), ruleId = "dup")
        val r2 = rule(FeatureRelation.ConflictsWith(ANC, TRANSPARENCY, "test conflict"), ruleId = "dup")
        val result = RelationshipGraph.build(listOf(r1, r2))
        assertTrue(result is GraphBuildResult.Invalid)
        val failures = (result as GraphBuildResult.Invalid).failures
        assertTrue(failures.any { it is GraphFailure.DuplicateRuleId })
    }

    @Test
    fun `dependency cycles are detected`() {
        val r1 = rule(FeatureRelation.Requires(HEAD_TRACKING, SPATIAL), ruleId = "c1")
        val r2 = rule(FeatureRelation.Requires(SPATIAL, HEAD_TRACKING), ruleId = "c2")
        val result = RelationshipGraph.build(listOf(r1, r2))
        assertTrue(result is GraphBuildResult.Invalid)
        val failures = (result as GraphBuildResult.Invalid).failures
        assertTrue(failures.any { it is GraphFailure.DependencyCycle })
    }

    @Test
    fun `contradictory rules are rejected`() {
        val r1 = rule(FeatureRelation.Requires(ANC, SPATIAL), ruleId = "x1")
        val r2 = rule(FeatureRelation.ConflictsWith(ANC, SPATIAL, "test conflict"), ruleId = "x2")
        val result = RelationshipGraph.build(listOf(r1, r2))
        assertTrue(result is GraphBuildResult.Invalid)
        val failures = (result as GraphBuildResult.Invalid).failures
        assertTrue(failures.any { it is GraphFailure.ContradictoryRules })
    }

    @Test
    fun `valid graph builds deterministically`() {
        val r1 = rule(FeatureRelation.Requires(HEAD_TRACKING, SPATIAL), ruleId = "b")
        val r2 = rule(FeatureRelation.ConflictsWith(ANC, TRANSPARENCY, "test conflict"), ruleId = "a")
        val g1 = validGraph(r1, r2)
        val g2 = validGraph(r2, r1)
        assertEquals(
            g1.rules.map { it.provenance.ruleId },
            g2.rules.map { it.provenance.ruleId },
        )
    }
}

class ConflictEvaluatorTest {

    @Test
    fun `verified mutual exclusion is a hard conflict`() {
        val graph = validGraph(
            rule(FeatureRelation.MutuallyExclusive(ANC, listOf(TRANSPARENCY), "test exclusion"), ruleId = "m1"),
        )
        val evaluation = ConflictEvaluator.evaluate(
            graph,
            config(
                setOf(ANC, TRANSPARENCY),
                mapOf(ANC to FeatureAvailability.AVAILABLE, TRANSPARENCY to FeatureAvailability.AVAILABLE),
            ),
            null, null, null,
        )
        assertFalse(evaluation.valid)
        assertFalse(evaluation.mayExecute)
        assertTrue(evaluation.findings.any { it is ConflictFinding.HardConflict })
    }

    @Test
    fun `inferred conflict is advisory not hard`() {
        val graph = validGraph(
            rule(
                FeatureRelation.ConflictsWith(ANC, TRANSPARENCY, "test conflict"),
                verification = RelationshipVerification.INFERRED,
                ruleId = "i1",
            ),
        )
        val evaluation = ConflictEvaluator.evaluate(
            graph,
            config(
                setOf(ANC),
                mapOf(TRANSPARENCY to FeatureAvailability.ENABLED),
            ),
            null, null, null,
        )
        assertTrue(evaluation.valid)
        assertTrue(evaluation.mayExecute)
        assertTrue(evaluation.findings.any { it is ConflictFinding.Advisory })
        assertTrue(evaluation.findings.none { it is ConflictFinding.HardConflict })
    }

    @Test
    fun `unknown prerequisite is unresolved not assumed`() {
        val graph = validGraph(
            rule(FeatureRelation.Requires(HEAD_TRACKING, SPATIAL), ruleId = "p1"),
        )
        val evaluation = ConflictEvaluator.evaluate(
            graph,
            config(setOf(HEAD_TRACKING), emptyMap()),
            null, null, null,
        )
        assertTrue(evaluation.findings.any { it is ConflictFinding.Unresolved })
    }

    @Test
    fun `unsupported prerequisite blocks`() {
        val graph = validGraph(
            rule(FeatureRelation.Requires(HEAD_TRACKING, SPATIAL), ruleId = "p2"),
        )
        val evaluation = ConflictEvaluator.evaluate(
            graph,
            config(
                setOf(HEAD_TRACKING),
                mapOf(SPATIAL to FeatureAvailability.UNSUPPORTED),
            ),
            null, null, null,
        )
        assertFalse(evaluation.mayExecute)
    }

    @Test
    fun `satisfied prerequisite is valid`() {
        val graph = validGraph(
            rule(FeatureRelation.Requires(HEAD_TRACKING, SPATIAL), ruleId = "p3"),
        )
        val evaluation = ConflictEvaluator.evaluate(
            graph,
            config(
                setOf(HEAD_TRACKING),
                mapOf(SPATIAL to FeatureAvailability.ENABLED),
            ),
            null, null, null,
        )
        assertTrue(evaluation.valid)
        assertTrue(evaluation.findings.isEmpty())
    }

    @Test
    fun `no relationships means valid`() {
        val graph = validGraph()
        val evaluation = ConflictEvaluator.evaluate(
            graph,
            config(setOf(ANC), mapOf(ANC to FeatureAvailability.AVAILABLE)),
            null, null, null,
        )
        assertTrue(evaluation.valid)
    }

    @Test
    fun `evaluation is deterministic`() {
        val graph = validGraph(
            rule(FeatureRelation.MutuallyExclusive(ANC, listOf(TRANSPARENCY), "test exclusion"), ruleId = "m2"),
            rule(FeatureRelation.Requires(HEAD_TRACKING, SPATIAL), ruleId = "p4"),
        )
        val c = config(setOf(ANC, TRANSPARENCY, HEAD_TRACKING))
        val e1 = ConflictEvaluator.evaluate(graph, c, null, null, null)
        val e2 = ConflictEvaluator.evaluate(graph, c, null, null, null)
        assertEquals(e1, e2)
    }
}

class OperationPlannerTest {

    private fun planFixture(
        vararg rules: RelationshipRule,
        enable: Set<FeatureId>,
        availability: Map<FeatureId, FeatureAvailability> = emptyMap(),
    ): PlanResult {
        val graph = validGraph(*rules)
        val evaluation = ConflictEvaluator.evaluate(
            graph, config(enable, availability), null, null, null,
        )
        return OperationPlanner.plan(
            planId = "plan-1",
            deviceId = "d1",
            sessionId = "s1",
            requested = enable,
            evaluation = evaluation,
            graph = graph,
            availability = availability,
            stateVersion = 7L,
            ruleSetVersion = 1,
            nowMillis = 1_000L,
        )
    }

    @Test
    fun `hard conflict rejects the plan`() {
        val result = planFixture(
            rule(FeatureRelation.MutuallyExclusive(ANC, listOf(TRANSPARENCY), "test exclusion"), ruleId = "m3"),
            enable = setOf(ANC, TRANSPARENCY),
            availability = mapOf(ANC to FeatureAvailability.AVAILABLE, TRANSPARENCY to FeatureAvailability.AVAILABLE),
        )
        assertTrue(result is PlanResult.Rejected)
    }

    @Test
    fun `unresolved prerequisite rejects the plan`() {
        val result = planFixture(
            rule(FeatureRelation.Requires(HEAD_TRACKING, SPATIAL), ruleId = "p5"),
            enable = setOf(HEAD_TRACKING),
        )
        assertTrue(result is PlanResult.Rejected)
    }

    @Test
    fun `valid plan orders prerequisites first`() {
        val result = planFixture(
            rule(FeatureRelation.Requires(HEAD_TRACKING, SPATIAL), ruleId = "p6"),
            enable = setOf(HEAD_TRACKING, SPATIAL),
            availability = mapOf(SPATIAL to FeatureAvailability.AVAILABLE),
        )
        assertTrue(result is PlanResult.Planned)
        val steps = (result as PlanResult.Planned).plan.steps.map { it.feature }
        assertEquals(listOf(SPATIAL, HEAD_TRACKING), steps)
    }

    @Test
    fun `stale plan is invalidated`() {
        val result = planFixture(enable = setOf(ANC))
        assertTrue(result is PlanResult.Planned)
        val plan = (result as PlanResult.Planned).plan
        val check = OperationPlanner.checkFreshness(plan, 8L, 1, true)
        assertTrue(check is PlanResult.Invalidated)
    }

    @Test
    fun `invalid session invalidates the plan`() {
        val result = planFixture(enable = setOf(ANC))
        assertTrue(result is PlanResult.Planned)
        val plan = (result as PlanResult.Planned).plan
        val check = OperationPlanner.checkFreshness(plan, 7L, 1, false)
        assertTrue(check is PlanResult.Invalidated)
    }

    @Test
    fun `fresh plan passes`() {
        val result = planFixture(enable = setOf(ANC))
        assertTrue(result is PlanResult.Planned)
        val plan = (result as PlanResult.Planned).plan
        val check = OperationPlanner.checkFreshness(plan, 7L, 1, true)
        assertTrue(check is PlanResult.Planned)
    }
}

class DependencyConcurrencyTest {

    @Test
    fun `overlapping plans on one device are rejected`() = runTest {
        val coordinator = DeviceOperationCoordinator()
        val plan1 = OperationPlan("p1", "d1", "s1", listOf(PlannedStep(ANC, "enable anc")), 1L, 1, 0L)
        val plan2 = OperationPlan("p2", "d1", "s1", listOf(PlannedStep(ANC, "enable anc")), 1L, 1, 0L)
        assertTrue(coordinator.admit(plan1))
        assertFalse(coordinator.admit(plan2))
        coordinator.release(plan1)
        assertTrue(coordinator.admit(plan2))
        coordinator.release(plan2)
    }

    @Test
    fun `disjoint plans on one device may coexist`() = runTest {
        val coordinator = DeviceOperationCoordinator()
        val plan1 = OperationPlan("p1", "d1", "s1", listOf(PlannedStep(ANC, "enable anc")), 1L, 1, 0L)
        val plan2 = OperationPlan("p2", "d1", "s1", listOf(PlannedStep(SPATIAL, "enable spatial")), 1L, 1, 0L)
        assertTrue(coordinator.admit(plan1))
        assertTrue(coordinator.admit(plan2))
        assertEquals(2, coordinator.activeCount("d1"))
        coordinator.release(plan1)
        coordinator.release(plan2)
    }

    @Test
    fun `devices are independent`() = runTest {
        val coordinator = DeviceOperationCoordinator()
        val plan1 = OperationPlan("p1", "d1", "s1", listOf(PlannedStep(ANC, "enable anc")), 1L, 1, 0L)
        val plan2 = OperationPlan("p2", "d2", "s2", listOf(PlannedStep(ANC, "enable anc")), 1L, 1, 0L)
        assertTrue(coordinator.admit(plan1))
        assertTrue(coordinator.admit(plan2))
        coordinator.release(plan1)
        coordinator.release(plan2)
    }
}
