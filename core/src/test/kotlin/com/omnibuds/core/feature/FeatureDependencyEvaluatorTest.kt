package com.omnibuds.core.feature

import com.omnibuds.core.capability.FeatureCategory
import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.config.ConfigurationValue
import com.omnibuds.core.state.CapabilityState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The dependency and conflict evaluator.
 *
 * Requires-edges reuse [com.omnibuds.core.capability.DependencyValidator], so
 * "requires" means one thing in discovery and in feature control. The richer
 * relations — requires-one-of, conflicts, mutual exclusion, implications,
 * vendor exceptions — are evaluated here against capability truth plus live
 * control state, and every verdict carries its explanation.
 */
class FeatureDependencyEvaluatorTest {

    private val anc = FeatureId.of("noise-control", "anc")
    private val ancLevel = FeatureId.of("noise-control", "anc-level")
    private val transparency = FeatureId.of("noise-control", "transparency")
    private val adaptive = FeatureId.of("noise-control", "adaptive-anc")

    private fun capabilities(vararg states: Pair<FeatureId, CapabilityState>) =
        FeatureTestFixtures.snapshot(
            *states.map { (feature, state) -> FeatureTestFixtures.record(feature, state) }.toTypedArray(),
        ).capabilities

    private fun definition(
        feature: FeatureId,
        relations: List<FeatureRelation>,
    ) = FeatureDefinition(
        feature = feature,
        displayName = feature.localName,
        category = FeatureCategory.NOISE_CONTROL,
        valueType = FeatureValueType.BOOLEAN,
        relations = relations,
    )

    @Test
    fun aDefinitionWithoutRelationsHasNothingToEvaluate() {
        val report = FeatureDependencyEvaluator.evaluate(
            StandardFeatures.TRANSPARENCY,
            capabilities(),
            emptyMap(),
        )
        assertTrue(report.mayRead)
        assertTrue(report.mayWrite)
        assertTrue(report.issues.isEmpty())
    }

    @Test
    fun aSatisfiedRequiresAllowsTheWrite() {
        val report = FeatureDependencyEvaluator.evaluate(
            StandardFeatures.ANC_LEVEL,
            capabilities(
                ancLevel to CapabilityState.SUPPORTED_VOLATILE,
                anc to CapabilityState.SUPPORTED_VOLATILE,
            ),
            emptyMap(),
        )
        assertTrue(report.mayWrite, "expected no blocking issues, got ${report.issues}")
    }

    @Test
    fun aMissingPrerequisiteBlocksBothReadAndWrite() {
        val report = FeatureDependencyEvaluator.evaluate(
            StandardFeatures.ANC_LEVEL,
            capabilities(
                ancLevel to CapabilityState.SUPPORTED_VOLATILE,
                anc to CapabilityState.UNSUPPORTED,
            ),
            emptyMap(),
        )
        assertFalse(report.mayWrite)
        assertFalse(report.mayRead)
        val issue = report.issues.single { it.blocksWrite }
        assertEquals(FeatureErrorCode.DEPENDENCY_NOT_SATISFIED, issue.code)
        assertTrue(issue.detail.contains("anc"))
    }

    @Test
    fun anUnknownPrerequisiteBlocksWritesButNotReads() {
        val report = FeatureDependencyEvaluator.evaluate(
            StandardFeatures.ANC_LEVEL,
            capabilities(ancLevel to CapabilityState.SUPPORTED_VOLATILE),
            emptyMap(),
        )
        assertFalse(report.mayWrite, "unknown is not satisfied")
        assertTrue(report.mayRead, "reads change nothing")
    }

    @Test
    fun aCrossDefinitionCycleIsDetectedAndBlocks() {
        val a = FeatureId.of("x", "a")
        val b = FeatureId.of("x", "b")
        val defA = definition(a, listOf(FeatureRelation.Requires(a, b)))
        val defB = definition(b, listOf(FeatureRelation.Requires(b, a)))
        val all = defA.relations + defB.relations
        val caps = capabilities(
            a to CapabilityState.SUPPORTED_VOLATILE,
            b to CapabilityState.SUPPORTED_VOLATILE,
        )

        val reportA = FeatureDependencyEvaluator.evaluate(a, defA.relations, all, caps, emptyMap())
        assertTrue(reportA.cycles.isNotEmpty(), "expected the a→b→a cycle to be reported")
        assertFalse(reportA.mayWrite, "a cycle is a modelling error, not a resolvable graph")
        assertFalse(reportA.mayRead)

        val reportB = FeatureDependencyEvaluator.evaluate(b, defB.relations, all, caps, emptyMap())
        assertFalse(reportB.mayWrite)
    }

    @Test
    fun theSingleDefinitionOverloadSeesOnlyItsOwnEdges() {
        val a = FeatureId.of("x", "a")
        val b = FeatureId.of("x", "b")
        // One edge alone is not a cycle; the convenience overload is for simple
        // callers, and it must not invent cycles it cannot see.
        val report = FeatureDependencyEvaluator.evaluate(
            definition(a, listOf(FeatureRelation.Requires(a, b))),
            capabilities(
                a to CapabilityState.SUPPORTED_VOLATILE,
                b to CapabilityState.SUPPORTED_VOLATILE,
            ),
            emptyMap(),
        )
        assertTrue(report.cycles.isEmpty())
        assertTrue(report.mayWrite)
    }

    @Test
    fun requiresOneOfIsSatisfiedByAnyEstablishedOption() {
        val feature = FeatureId.of("x", "f")
        val o1 = FeatureId.of("x", "o1")
        val o2 = FeatureId.of("x", "o2")
        val def = definition(
            feature,
            listOf(FeatureRelation.RequiresOneOf(feature, listOf(o1, o2))),
        )
        val satisfied = FeatureDependencyEvaluator.evaluate(
            def,
            capabilities(
                feature to CapabilityState.SUPPORTED_VOLATILE,
                o1 to CapabilityState.UNSUPPORTED,
                o2 to CapabilityState.SUPPORTED_VOLATILE,
            ),
            emptyMap(),
        )
        assertTrue(satisfied.mayWrite)

        val noneSatisfied = FeatureDependencyEvaluator.evaluate(
            def,
            capabilities(feature to CapabilityState.SUPPORTED_VOLATILE),
            emptyMap(),
        )
        assertFalse(noneSatisfied.mayWrite)
        assertTrue(noneSatisfied.mayRead, "all options unknown still permits reads")
    }

    @Test
    fun anActiveConflictBlocksTheWriteButAnInactiveOneDoesNot() {
        val mode = StandardFeatures.ANC_MODE.feature
        val blocking = FeatureDependencyEvaluator.evaluate(
            StandardFeatures.ANC_MODE,
            capabilities(
                mode to CapabilityState.SUPPORTED_VOLATILE,
                anc to CapabilityState.SUPPORTED_VOLATILE,
            ),
            mapOf(anc to FeatureState.Confirmed(anc, ConfigurationValue.BooleanValue(true))),
        )
        assertFalse(blocking.mayWrite)
        assertTrue(blocking.mayRead)
        assertEquals(FeatureErrorCode.FEATURE_CONFLICT, blocking.issues.single().code)

        val quiet = FeatureDependencyEvaluator.evaluate(
            StandardFeatures.ANC_MODE,
            capabilities(
                mode to CapabilityState.SUPPORTED_VOLATILE,
                anc to CapabilityState.SUPPORTED_VOLATILE,
            ),
            mapOf(anc to FeatureState.Confirmed(anc, ConfigurationValue.BooleanValue(false))),
        )
        assertTrue(quiet.mayWrite, "an inert conflicting value is not a conflict")
    }

    @Test
    fun aPendingActiveWriteOnTheOtherFeatureCountsAsAConflict() {
        val mode = StandardFeatures.ANC_MODE.feature
        val report = FeatureDependencyEvaluator.evaluate(
            StandardFeatures.ANC_MODE,
            capabilities(
                mode to CapabilityState.SUPPORTED_VOLATILE,
                anc to CapabilityState.SUPPORTED_VOLATILE,
            ),
            mapOf(
                anc to FeatureState.Pending(anc, ConfigurationValue.BooleanValue(true), null, "op-9"),
            ),
        )
        assertFalse(report.mayWrite, "a racing write on the conflicting feature must not interleave")
    }

    @Test
    fun mutualExclusionPermitsOnlyOneActiveMember() {
        val group = listOf(FeatureId.of("x", "m1"), FeatureId.of("x", "m2"))
        val def = definition(
            group[0],
            listOf(
                FeatureRelation.MutuallyExclusive(group[0], listOf(group[1]), "one write path"),
            ),
        )
        val blocked = FeatureDependencyEvaluator.evaluate(
            def,
            capabilities(
                group[0] to CapabilityState.SUPPORTED_VOLATILE,
                group[1] to CapabilityState.SUPPORTED_VOLATILE,
            ),
            mapOf(group[1] to FeatureState.Confirmed(group[1], ConfigurationValue.BooleanValue(true))),
        )
        assertFalse(blocked.mayWrite)

        val free = FeatureDependencyEvaluator.evaluate(
            def,
            capabilities(
                group[0] to CapabilityState.SUPPORTED_VOLATILE,
                group[1] to CapabilityState.SUPPORTED_VOLATILE,
            ),
            emptyMap(),
        )
        assertTrue(free.mayWrite)
    }

    @Test
    fun anImplicationAgainstAnUnsupportedFeatureIsAWarningNotABlock() {
        val def = definition(
            adaptive,
            listOf(FeatureRelation.Implies(adaptive, transparency)),
        )
        val report = FeatureDependencyEvaluator.evaluate(
            def,
            capabilities(
                adaptive to CapabilityState.SUPPORTED_VOLATILE,
                transparency to CapabilityState.UNSUPPORTED,
            ),
            emptyMap(),
        )
        assertTrue(report.mayWrite, "implications never block; they warn")
        assertTrue(report.mayRead)
        assertTrue(report.issues.any { it.detail.contains("modelling warning") })
    }

    @Test
    fun vendorExceptionsAreRecordedAndNeverBlock() {
        val def = definition(
            adaptive,
            listOf(
                FeatureRelation.Requires(adaptive, anc),
                FeatureRelation.VendorException(adaptive, "example-vendor", "no ANC prerequisite there"),
            ),
        )
        val report = FeatureDependencyEvaluator.evaluate(
            def,
            capabilities(
                adaptive to CapabilityState.SUPPORTED_VOLATILE,
                anc to CapabilityState.SUPPORTED_VOLATILE,
            ),
            emptyMap(),
        )
        assertTrue(report.mayWrite)
        assertEquals(1, report.vendorNotes.size)
        assertEquals("example-vendor", report.vendorNotes.single().vendor)
    }

    @Test
    fun relationConstructionRefusesModellingErrors() {
        // Self-edges are cycles of length one.
        assertFailsWith<IllegalArgumentException> {
            FeatureRelation.Requires(anc, anc)
        }
        assertFailsWith<IllegalArgumentException> {
            FeatureRelation.ConflictsWith(anc, anc, "nonsense")
        }
    }
}
