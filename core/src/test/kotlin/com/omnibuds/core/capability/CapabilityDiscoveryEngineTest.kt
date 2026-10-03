package com.omnibuds.core.capability

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.platform.TimeProvider
import com.omnibuds.core.state.CapabilityState
import com.omnibuds.core.state.VerificationLevel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The discovery coordinator's behaviour, driven only through the read-only [CapabilityDiscoverySource] seam
 * (§9, ADR-P8-005). Everything here is a scripted source living in test source — no device is implied, and the
 * ceiling is [VerificationLevel.IMPLEMENTED] (ADR-P8-010).
 *
 * The guarantees under test are the ones the whole phase exists to protect: failure isolation, partial ≠
 * complete ≠ failed, cancellation preserving gathered evidence, unknown never becoming unsupported, a weak
 * read never silently overwriting a strong one, malformed input not crashing the pass, and determinism.
 * Tier T1.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CapabilityDiscoveryEngineTest {

    private val anc = FeatureId.of("noise-control", "anc")
    private val eq = FeatureId.of("equalization", "equalizer")
    private val battery = FeatureId.of("power", "battery")
    private val spatialAudio = FeatureId.of("audio", "spatial-audio")
    private val headTracking = FeatureId.of("audio", "head-tracking")

    private fun cap(feature: FeatureId, state: CapabilityState): FeatureCapability = when (state) {
        CapabilityState.UNKNOWN -> FeatureCapability.unknown(feature)
        CapabilityState.UNSUPPORTED -> FeatureCapability.unsupported(feature, "engine-test", TransportKind.GATT)
        CapabilityState.READ_ONLY -> FeatureCapability(
            feature, state, readable = true, writable = false, transport = TransportKind.GATT,
            protocolId = "engine-test", requiresConnection = true, verification = VerificationLevel.HARDWARE_VERIFIED,
        )

        CapabilityState.SUPPORTED_VOLATILE -> FeatureCapability(
            feature, state, readable = true, writable = true, transport = TransportKind.GATT,
            protocolId = "engine-test", requiresConnection = true, verification = VerificationLevel.IMPLEMENTED,
        )

        else -> FeatureCapability(
            feature, state, readable = true, writable = true, transport = TransportKind.GATT,
            protocolId = "engine-test", requiresConnection = true,
            verification = if (state == CapabilityState.PERSISTENCE_VERIFIED) {
                VerificationLevel.PERSISTENCE_VERIFIED
            } else {
                VerificationLevel.HARDWARE_VERIFIED
            },
        )
    }

    private fun evidence(kind: EvidenceKind = EvidenceKind.EXPLICIT_DEVICE_RESPONSE): CapabilityEvidence =
        CapabilityEvidence(
            kind = kind,
            source = "engine-test",
            atEpochMillis = null,
            protocolId = "engine-test",
            protocolVersion = "1",
            verification = when (kind) {
                EvidenceKind.INFERRED_MODEL,
                EvidenceKind.UNKNOWN_OR_INCOMPLETE,
                -> VerificationLevel.INFERRED

                EvidenceKind.VERIFIED_PROTOCOL_DESCRIPTOR -> VerificationLevel.IMPLEMENTED
                else -> VerificationLevel.HARDWARE_VERIFIED
            },
        )

    private fun reading(
        feature: FeatureId,
        state: CapabilityState,
        availability: CapabilityAvailability = CapabilityAvailability.AVAILABLE,
        kind: EvidenceKind = EvidenceKind.EXPLICIT_DEVICE_RESPONSE,
    ): DiscoveryReading = DiscoveryReading(cap(feature, state), availability, evidence(kind))

    private fun failOutcome(category: OmniBudsErrorCategory): OperationOutcome<DiscoveryReading> =
        OperationOutcome.Failure(OmniBudsError.of(category, "discovery"))

    /** A test-only source: answers per (feature, nth read of that feature). Never shipped as production support. */
    private class ScriptedSource(
        private val features: List<FeatureId>,
        private val responder: (FeatureId, Int) -> OperationOutcome<DiscoveryReading>,
    ) : CapabilityDiscoverySource {
        private val counts = HashMap<FeatureId, Int>()
        var readCalls = 0
        val readOrder = mutableListOf<FeatureId>()
        override suspend fun attemptableFeatures(): List<FeatureId> = features
        override suspend fun read(feature: FeatureId): OperationOutcome<DiscoveryReading> {
            val nth = counts.getOrDefault(feature, 0)
            counts[feature] = nth + 1
            readCalls++
            readOrder += feature
            return responder(feature, nth)
        }
    }

    private fun engineFor(
        source: CapabilityDiscoverySource,
        dependencies: List<CapabilityDependency> = emptyList(),
        time: TimeProvider? = null,
    ) = CapabilityDiscoveryEngine(source, dependencies, time)

    @Test
    fun aSuccessfulPassFoldsEveryReadingIntoEstablishedState() = runTest {
        val source = ScriptedSource(listOf(anc, battery)) { feature, _ ->
            OperationOutcome.Success(
                reading(
                    feature,
                    if (feature == anc) CapabilityState.SUPPORTED_VOLATILE else CapabilityState.READ_ONLY,
                ),
            )
        }

        val snapshot = engineFor(source).discover("subject-1", "engine-test", "1")

        assertEquals(DiscoveryState.COMPLETE, snapshot.completion)
        assertEquals(CapabilityState.SUPPORTED_VOLATILE, snapshot.capabilities.stateOf(anc))
        assertEquals(CapabilityState.READ_ONLY, snapshot.capabilities.stateOf(battery))
        assertEquals(2, snapshot.evidence.size)
        assertEquals(CapabilityAvailability.AVAILABLE, snapshot.availabilityOf(anc))
    }

    @Test
    fun aFailedReadIsIsolatedAndKeepsThatFeatureUnknownNotUnsupported() = runTest {
        // §10: one failed capability must not invalidate the rest, and a failed read is UNKNOWN not UNSUPPORTED.
        val source = ScriptedSource(listOf(anc, eq)) { feature, _ ->
            if (feature == anc) {
                OperationOutcome.Success(reading(anc, CapabilityState.SUPPORTED_VOLATILE))
            } else {
                failOutcome(OmniBudsErrorCategory.READ_FAILED)
            }
        }

        val snapshot = engineFor(source).discover("subject-1", "engine-test", "1")

        assertEquals(DiscoveryState.PARTIALLY_COMPLETE, snapshot.completion)
        assertEquals(CapabilityState.SUPPORTED_VOLATILE, snapshot.capabilities.stateOf(anc))
        assertEquals(CapabilityState.UNKNOWN, snapshot.capabilities.stateOf(eq))
        assertSame(CapabilityState.UNKNOWN, snapshot.capabilities.stateOf(eq))
        assertTrue(eq !in snapshot.capabilities.unsupported, "a failed read is not a positive absence")
        assertEquals(listOf(PartialFailure(eq, OmniBudsErrorCategory.READ_FAILED)), snapshot.partialFailures)
    }

    @Test
    fun aPassThatEstablishesNothingFailsRatherThanReportsPartialSuccess() = runTest {
        // §14 FAILED vs PARTIALLY_COMPLETE: with no successes at all the pass did not "partly work".
        val source = ScriptedSource(listOf(anc, eq)) { _, _ -> failOutcome(OmniBudsErrorCategory.CONNECTION_UNAVAILABLE) }

        val snapshot = engineFor(source).discover("subject-1", "engine-test", "1")

        assertEquals(DiscoveryState.FAILED, snapshot.completion)
        assertTrue(snapshot.capabilities.features.isEmpty())
        assertEquals(2, snapshot.partialFailures.size)
        assertEquals(setOf(anc, eq), snapshot.failedFeatures)
    }

    @Test
    fun anUnresolvedProtocolSurfacesThroughAnExistingCategoryNotANewOne() = runTest {
        // §15 PROTOCOL_UNRESOLVED reaches the snapshot as PROTOCOL_MISMATCH (ADR-P8-004); the L2 engine cannot
        // import the protocol layer, so it reports what the source told it.
        val source = ScriptedSource(listOf(anc)) { _, _ -> failOutcome(OmniBudsErrorCategory.PROTOCOL_MISMATCH) }

        val snapshot = engineFor(source).discover("subject-1", null, null)

        assertEquals(DiscoveryState.FAILED, snapshot.completion)
        assertEquals(listOf(PartialFailure(anc, OmniBudsErrorCategory.PROTOCOL_MISMATCH)), snapshot.partialFailures)
    }

    @Test
    fun cancellationEndsThePassAndPreservesEverythingGatheredBeforeIt() = runTest {
        val a = FeatureId.of("noise-control", "a-anc")
        val b = FeatureId.of("noise-control", "b-transparency")
        val c = FeatureId.of("noise-control", "c-adaptive")
        val source = ScriptedSource(listOf(a, b, c)) { feature, _ ->
            when (feature) {
                a -> OperationOutcome.Success(reading(a, CapabilityState.SUPPORTED_VOLATILE))
                b -> OperationOutcome.Cancelled
                else -> OperationOutcome.Success(reading(c, CapabilityState.SUPPORTED_VOLATILE))
            }
        }

        val snapshot = engineFor(source).discover("subject-1", "engine-test", "1")

        assertEquals(DiscoveryState.CANCELLED, snapshot.completion)
        assertEquals(CapabilityState.SUPPORTED_VOLATILE, snapshot.capabilities.stateOf(a), "gathered before cancel is kept")
        assertEquals(CapabilityState.UNKNOWN, snapshot.capabilities.stateOf(c), "nothing after the cancel is invented")
        assertEquals(1, snapshot.evidence.size)
        assertEquals(2, source.readCalls, "the pass stopped at the cancellation; c was never read")
    }

    @Test
    fun anEmptyAttemptableSetIsACompletePassThatClaimsNothing() = runTest {
        // A pass over zero features honestly finished; it does not fabricate capabilities and is not a "success"
        // in the sense of having discovered anything (§9 "do not claim success when no evidence exists").
        val snapshot = engineFor(ScriptedSource(emptyList()) { _, _ -> failOutcome(OmniBudsErrorCategory.UNKNOWN_FAILURE) })
            .discover("subject-1", "engine-test", "1")

        assertEquals(DiscoveryState.COMPLETE, snapshot.completion)
        assertTrue(snapshot.capabilities.features.isEmpty())
        assertEquals(CapabilityState.UNKNOWN, snapshot.capabilities.stateOf(anc))
        assertTrue(snapshot.evidence.isEmpty())
    }

    @Test
    fun featureOrderFromTheSourceDoesNotChangeTheSnapshot() = runTest {
        val byFeature = mapOf(
            anc to OperationOutcome.Success(reading(anc, CapabilityState.SUPPORTED_VOLATILE)),
            battery to OperationOutcome.Success(reading(battery, CapabilityState.READ_ONLY, kind = EvidenceKind.DEVICE_FEATURE_FLAG)),
            eq to failOutcome(OmniBudsErrorCategory.TIMEOUT),
        )
        val forward = engineFor(ScriptedSource(listOf(anc, battery, eq)) { f, _ -> byFeature.getValue(f) })
            .discover("subject-1", "engine-test", "1")
        val backward = engineFor(ScriptedSource(listOf(eq, battery, anc)) { f, _ -> byFeature.getValue(f) })
            .discover("subject-1", "engine-test", "1")

        assertEquals(forward.capabilities, backward.capabilities)
        assertEquals(forward.evidence, backward.evidence)
        assertEquals(forward.partialFailures, backward.partialFailures)
        assertEquals(forward, backward, "the same evidence always yields the identical snapshot")
    }

    @Test
    fun aWeakSecondReadCannotOverwriteAnEstablishedStrongerOne() = runTest {
        // §8: folding through the evidence ladder means a later weaker record never quietly erases durability.
        val source = ScriptedSource(listOf(anc, anc)) { _, nth ->
            OperationOutcome.Success(
                reading(anc, if (nth == 0) CapabilityState.SUPPORTED_PERSISTENT else CapabilityState.SUPPORTED_VOLATILE),
            )
        }

        val snapshot = engineFor(source).discover("subject-1", "engine-test", "1")

        assertEquals(CapabilityState.SUPPORTED_PERSISTENT, snapshot.capabilities.stateOf(anc))
    }

    @Test
    fun twoDisagreeingReadsAreSurfacedAsAnUnresolvedConflictNotResolvedSilently() = runTest {
        // §8: a device "yes" and a protocol "no" for one feature are kept and flagged; neither is dropped.
        val source = ScriptedSource(listOf(anc, anc)) { _, nth ->
            OperationOutcome.Success(
                if (nth == 0) {
                    reading(anc, CapabilityState.SUPPORTED_VOLATILE, kind = EvidenceKind.EXPLICIT_DEVICE_RESPONSE)
                } else {
                    reading(anc, CapabilityState.UNSUPPORTED, kind = EvidenceKind.VERIFIED_PROTOCOL_DESCRIPTOR)
                },
            )
        }

        val snapshot = engineFor(source).discover("subject-1", "engine-test", "1")

        assertEquals(1, snapshot.unresolvedConflicts.size)
        val conflict = snapshot.unresolvedConflicts.single()
        assertEquals(anc, conflict.feature)
        assertEquals(
            listOf(EvidenceKind.EXPLICIT_DEVICE_RESPONSE, EvidenceKind.VERIFIED_PROTOCOL_DESCRIPTOR),
            conflict.kinds,
        )
        assertEquals(CapabilityState.SUPPORTED_VOLATILE, snapshot.capabilities.stateOf(anc), "higher rung retained")
    }

    @Test
    fun aSingleReadNeverFabricatesAConflictOutOfItself() = runTest {
        val source = ScriptedSource(listOf(anc)) { _, _ -> OperationOutcome.Success(reading(anc, CapabilityState.SUPPORTED_VOLATILE)) }

        val snapshot = engineFor(source).discover("subject-1", "engine-test", "1")

        assertTrue(snapshot.unresolvedConflicts.isEmpty())
    }

    @Test
    fun aMislabeledReadBecomesAMalformedFailureAndCannotCrashThePass() = runTest {
        // §17: an untrusted source answering about the wrong feature is refused, not filed and not thrown.
        val source = ScriptedSource(listOf(anc)) { _, _ ->
            OperationOutcome.Success(reading(eq, CapabilityState.SUPPORTED_VOLATILE)) // record is about eq
        }

        val snapshot = engineFor(source).discover("subject-1", "engine-test", "1")

        assertEquals(CapabilityState.UNKNOWN, snapshot.capabilities.stateOf(anc), "the mislabeled record was refused")
        assertEquals(
            listOf(PartialFailure(anc, CapabilityDiscoveryEngine.MALFORMED_RESPONSE_CATEGORY)),
            snapshot.partialFailures,
        )
        assertEquals(DiscoveryState.FAILED, snapshot.completion)
    }

    @Test
    fun aBlockedFeatureBecomesUnavailableButStaysSupported() = runTest {
        // §11: a missing prerequisite removes availability, never support. headTracking is genuinely settable;
        // its spatial-audio prerequisite is positively unsupported, so the control is gated, not denied.
        val source = ScriptedSource(listOf(spatialAudio, headTracking)) { feature, _ ->
            OperationOutcome.Success(
                if (feature == spatialAudio) {
                    reading(spatialAudio, CapabilityState.UNSUPPORTED)
                } else {
                    reading(headTracking, CapabilityState.SUPPORTED_VOLATILE)
                },
            )
        }
        val dependency = CapabilityDependency(headTracking, spatialAudio, protocolId = "engine-test")

        val snapshot = engineFor(source, listOf(dependency)).discover("subject-1", "engine-test", "1")

        assertEquals(CapabilityAvailability.UNAVAILABLE, snapshot.availabilityOf(headTracking))
        assertEquals(CapabilityState.SUPPORTED_VOLATILE, snapshot.capabilities.stateOf(headTracking))
        assertTrue(headTracking in snapshot.capabilities.controllable, "gating availability does not erase support")
    }

    @Test
    fun aSupportedFeatureThatIsTemporarilyUnavailableIsStillOfferableAsSupport() = runTest {
        // §7: availability is a separate axis. A supported-but-mid-calibration feature is not "unsupported".
        val source = ScriptedSource(listOf(anc)) { _, _ ->
            OperationOutcome.Success(reading(anc, CapabilityState.SUPPORTED_VOLATILE, availability = CapabilityAvailability.TEMPORARILY_UNAVAILABLE))
        }

        val snapshot = engineFor(source).discover("subject-1", "engine-test", "1")

        assertEquals(CapabilityState.SUPPORTED_VOLATILE, snapshot.capabilities.stateOf(anc))
        assertEquals(CapabilityAvailability.TEMPORARILY_UNAVAILABLE, snapshot.availabilityOf(anc))
    }

    @Test
    fun aMissingClockYieldsNoTimestampRatherThanAnInventedOne() = runTest {
        val source = ScriptedSource(listOf(anc)) { _, _ -> OperationOutcome.Success(reading(anc, CapabilityState.SUPPORTED_VOLATILE)) }

        val noClock = engineFor(source).discover("subject-1", "engine-test", "1")
        val withClock = engineFor(source, time = TimeProvider { 1_700_000_000_000L }).discover("subject-1", "engine-test", "1")

        kotlin.test.assertNull(noClock.discoveredAtEpochMillis, "core reads no wall clock; null means unknown")
        assertEquals(1_700_000_000_000L, withClock.discoveredAtEpochMillis)
    }
}
