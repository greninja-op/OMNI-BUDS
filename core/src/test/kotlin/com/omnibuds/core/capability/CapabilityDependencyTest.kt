package com.omnibuds.core.capability

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.state.CapabilityState
import com.omnibuds.core.state.VerificationLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The dependency edges are protocol/firmware facts resolved against established capability state, and the
 * validator never infers or enables anything (§11, ADR-P8-007).
 *
 * The properties that carry the rule: a missing prerequisite is explicit, an *unknown* prerequisite keeps the
 * dependent unknown rather than blocked-unsupported (unknown is never a negative), and a cycle is reported
 * instead of quietly broken. Tier T1.
 */
class CapabilityDependencyTest {

    private val anc = FeatureId.of("noise-control", "anc")
    private val adaptiveAnc = FeatureId.of("noise-control", "adaptive-anc")
    private val spatialAudio = FeatureId.of("audio", "spatial-audio")
    private val headTracking = FeatureId.of("audio", "head-tracking")
    private val eq = FeatureId.of("equalization", "equalizer")

    private fun record(feature: FeatureId, state: CapabilityState): FeatureCapability = when (state) {
        CapabilityState.UNKNOWN -> FeatureCapability.unknown(feature)
        CapabilityState.UNSUPPORTED -> FeatureCapability.unsupported(feature, "dep-test", TransportKind.GATT)
        CapabilityState.READ_ONLY -> FeatureCapability(
            feature, state, readable = true, writable = false, transport = TransportKind.GATT,
            protocolId = "dep-test", requiresConnection = true, verification = VerificationLevel.HARDWARE_VERIFIED,
        )

        else -> FeatureCapability(
            feature, state, readable = true, writable = true, transport = TransportKind.GATT,
            protocolId = "dep-test", requiresConnection = true,
            verification = if (state == CapabilityState.SUPPORTED_VOLATILE) {
                VerificationLevel.IMPLEMENTED
            } else {
                VerificationLevel.HARDWARE_VERIFIED
            },
        )
    }

    private fun capabilitiesOf(vararg pairs: Pair<FeatureId, CapabilityState>): DeviceCapabilities {
        var caps = DeviceCapabilities.empty()
        for ((feature, state) in pairs) caps = caps.with(feature, record(feature, state))
        return caps
    }

    @Test
    fun aFeatureMayNotDependOnItself() {
        // A self-edge is a cycle of length one; refusing it at construction keeps the graph search simple.
        assertFailsWith<IllegalArgumentException> { CapabilityDependency(anc, anc) }
    }

    @Test
    fun anEstablishedPrerequisiteSatisfiesTheEdge() {
        val edge = CapabilityDependency(headTracking, spatialAudio)
        val report = DependencyValidator.validate(listOf(edge), capabilitiesOf(spatialAudio to CapabilityState.READ_ONLY))

        assertEquals(DependencyStatus.SATISFIED, report.statuses[edge])
        assertTrue(report.isFullySatisfied(headTracking))
        assertTrue(report.cycles.isEmpty())
        assertTrue(report.blockedFeatures.isEmpty())
    }

    @Test
    fun aPositivelyUnsupportedPrerequisiteBlocksTheDependent() {
        val edge = CapabilityDependency(headTracking, spatialAudio)
        val report = DependencyValidator.validate(
            listOf(edge),
            capabilitiesOf(spatialAudio to CapabilityState.UNSUPPORTED),
        )

        assertEquals(DependencyStatus.MISSING_PREREQUISITE, report.statuses[edge])
        assertFalse(report.isFullySatisfied(headTracking))
        assertEquals(setOf(headTracking), report.blockedFeatures)
    }

    @Test
    fun anUnknownPrerequisiteLeavesTheDependentUnknownNotBlocked() {
        // This is the §11 rule that keeps unknown from becoming negative: an unanswered prerequisite does NOT
        // earn the dependent a MISSING_PREREQUISITE, so a caller cannot render "not supported" from silence.
        val edge = CapabilityDependency(headTracking, spatialAudio)
        val report = DependencyValidator.validate(listOf(edge), DeviceCapabilities.empty())

        assertEquals(DependencyStatus.UNKNOWN_PREREQUISITE, report.statuses[edge])
        assertFalse(headTracking in report.blockedFeatures, "unknown prerequisite is not a blocking negative")
        assertFalse(report.isFullySatisfied(headTracking))
    }

    @Test
    fun aDependencyCycleIsReportedAndItsMembersAreNotOffered() {
        val aRequiresB = CapabilityDependency(eq, headTracking)
        val bRequiresA = CapabilityDependency(headTracking, eq)
        val report = DependencyValidator.validate(listOf(aRequiresB, bRequiresA), DeviceCapabilities.empty())

        assertEquals(DependencyStatus.CYCLE, report.statuses[aRequiresB])
        assertEquals(DependencyStatus.CYCLE, report.statuses[bRequiresA])
        assertEquals(1, report.cycles.size, "the two-edge loop is reported once")
        assertEquals(setOf(eq, headTracking), report.blockedFeatures)
    }

    @Test
    fun cycleDiscoveryIsDeterministicForTheSameGraph() {
        val edges = listOf(
            CapabilityDependency(adaptiveAnc, anc),
            CapabilityDependency(anc, adaptiveAnc),
        )
        val first = DependencyValidator.validate(edges, DeviceCapabilities.empty())
        val second = DependencyValidator.validate(edges.reversed(), DeviceCapabilities.empty())

        assertEquals(first.cycles, second.cycles, "input order must not change the reported cycle")
    }

    @Test
    fun validatingNeverEnablesAPrerequisiteNorMutatesCapabilities() {
        // §11: the validator reads established state and returns a report. Dependent being SUPPORTED with an
        // unknown prerequisite does NOT make the prerequisite supported; the contradiction is surfaced, not
        // silently repaired.
        val edge = CapabilityDependency(headTracking, spatialAudio)
        val caps = capabilitiesOf(headTracking to CapabilityState.SUPPORTED_VOLATILE)
        val report = DependencyValidator.validate(listOf(edge), caps)

        assertEquals(CapabilityState.UNKNOWN, caps.stateOf(spatialAudio), "a dependent claim did not back-propagate")
        assertEquals(DependencyStatus.UNKNOWN_PREREQUISITE, report.statuses[edge])
    }

    @Test
    fun aFeatureWithNoEdgesIsTriviallySatisfied() {
        val report = DependencyValidator.validate(emptyList(), capabilitiesOf(anc to CapabilityState.SUPPORTED_VOLATILE))

        assertEquals(DependencyReport.EMPTY, report)
        assertTrue(report.isFullySatisfied(anc), "no obligation means nothing to fail")
    }

    @Test
    fun aVendorPrerequisiteIsResolvedLikeAnyOther() {
        // A dependency can point at a vendor-namespaced feature; the validator keys on identity, not brand.
        val vendorPrereq = FeatureId.ofVendor("sony", "adaptive-sound-control")
        val edge = CapabilityDependency(anc, vendorPrereq)
        val report = DependencyValidator.validate(
            listOf(edge),
            capabilitiesOf(vendorPrereq to CapabilityState.SUPPORTED_PERSISTENT),
        )

        assertEquals(DependencyStatus.SATISFIED, report.statuses[edge])
    }
}
