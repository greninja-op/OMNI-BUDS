package com.omnibuds.core.capability

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.state.CapabilityState
import com.omnibuds.core.state.VerificationLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The snapshot wraps Phase 1's [DeviceCapabilities] rather than replacing it, and its shape refuses to let a
 * partial pass read as a complete one (§13, ADR-P8-008).
 *
 * Support and availability stay two different axes on purpose: a feature can be established as supported and
 * still be unavailable right now, and that is never the same as being unsupported. Tier T1.
 */
class CapabilitySnapshotTest {

    private val anc = FeatureId.of("noise-control", "anc")
    private val eq = FeatureId.of("equalization", "equalizer")

    private fun supported(feature: FeatureId, state: CapabilityState): FeatureCapability = when (state) {
        CapabilityState.SUPPORTED_PERSISTENT -> FeatureCapability(
            feature, state, readable = true, writable = true, transport = TransportKind.GATT,
            protocolId = "snap-test", requiresConnection = true, verification = VerificationLevel.HARDWARE_VERIFIED,
        )

        CapabilityState.UNSUPPORTED -> FeatureCapability.unsupported(feature, "snap-test", TransportKind.GATT)

        else -> FeatureCapability(
            feature, state, readable = true, writable = true, transport = TransportKind.GATT,
            protocolId = "snap-test", requiresConnection = true, verification = VerificationLevel.IMPLEMENTED,
        )
    }

    private fun snapshot(
        completion: DiscoveryState,
        capabilities: DeviceCapabilities = DeviceCapabilities.empty(),
        availability: Map<FeatureId, CapabilityAvailability> = emptyMap(),
        partialFailures: List<PartialFailure> = emptyList(),
        conflicts: List<UnresolvedConflict> = emptyList(),
    ): CapabilitySnapshot = CapabilitySnapshot(
        subjectRef = "session-1",
        protocolId = "snap-test",
        protocolVersion = "1",
        discoveredAtEpochMillis = null,
        completion = completion,
        capabilities = capabilities,
        availability = availability,
        evidence = emptyList(),
        partialFailures = partialFailures,
        unresolvedConflicts = conflicts,
        schemaVersion = CapabilitySnapshot.SCHEMA_VERSION,
    )

    @Test
    fun aCompleteSnapshotMayNotCarryPartialFailures() {
        // §14: COMPLETE and PARTIALLY_COMPLETE are not interchangeable, and the constructor refuses the mix so
        // no caller can quietly report a lossy pass as clean.
        assertFailsWith<IllegalArgumentException> {
            snapshot(DiscoveryState.COMPLETE, partialFailures = listOf(PartialFailure(eq, OmniBudsErrorCategory.TIMEOUT)))
        }
    }

    @Test
    fun aPartialSnapshotMustCarryTheFailureThatMadeItPartial() {
        assertFailsWith<IllegalArgumentException> {
            snapshot(DiscoveryState.PARTIALLY_COMPLETE, partialFailures = emptyList())
        }
    }

    @Test
    fun aFeatureAbsentFromAvailabilityReadsAsUnknownNotUnavailable() {
        val snap = snapshot(
            completion = DiscoveryState.COMPLETE,
            capabilities = DeviceCapabilities.empty().with(anc, supported(anc, CapabilityState.SUPPORTED_VOLATILE)),
        )

        assertEquals(CapabilityAvailability.UNKNOWN, snap.availabilityOf(eq), "an un-asked feature has no reading")
        assertEquals(0, snap.failedFeatures.size)
    }

    @Test
    fun supportedAndUnavailableAreIndependentAxesAndNeitherMeansUnsupported() {
        // The exact conflation §7 forbids: a feature established as persistently settable, currently UNAVAILABLE
        // because a prerequisite is off, stays SUPPORTED in capability state and never becomes UNSUPPORTED.
        val snap = snapshot(
            completion = DiscoveryState.COMPLETE,
            capabilities = DeviceCapabilities.empty().with(anc, supported(anc, CapabilityState.SUPPORTED_PERSISTENT)),
            availability = mapOf(anc to CapabilityAvailability.UNAVAILABLE),
        )

        assertEquals(CapabilityState.SUPPORTED_PERSISTENT, snap.capabilities.stateOf(anc))
        assertEquals(CapabilityAvailability.UNAVAILABLE, snap.availabilityOf(anc))
        assertFalse(anc in snap.capabilities.unsupported, "unavailable is not unsupported")
        assertTrue(anc in snap.capabilities.controllable, "support is not erased by a momentary unavailability")
    }

    @Test
    fun failedFeaturesSurfaceTheirIdentitiesForTheNextPass() {
        val snap = snapshot(
            completion = DiscoveryState.PARTIALLY_COMPLETE,
            partialFailures = listOf(
                PartialFailure(eq, OmniBudsErrorCategory.TIMEOUT),
                PartialFailure(anc, OmniBudsErrorCategory.CONNECTION_UNAVAILABLE),
            ),
        )

        assertEquals(setOf(anc, eq), snap.failedFeatures)
    }

    @Test
    fun anEmptySnapshotIsANotStartedPassThatEstablishesNothing() {
        val empty = CapabilitySnapshot.empty()

        assertEquals(DiscoveryState.NOT_STARTED, empty.completion)
        assertNull(empty.subjectRef)
        assertEquals(CapabilityState.UNKNOWN, empty.capabilities.stateOf(anc))
        assertEquals(CapabilityAvailability.UNKNOWN, empty.availabilityOf(anc))
        assertTrue(empty.evidence.isEmpty())
        assertTrue(empty.partialFailures.isEmpty())
        assertTrue(empty.unresolvedConflicts.isEmpty())
    }

    @Test
    fun theSchemaVersionIsPositiveAndPinned() {
        // A bumped-never-reused version lets a later reader migrate an old snapshot instead of misreading it.
        assertEquals(1, CapabilitySnapshot.SCHEMA_VERSION)
        assertFailsWith<IllegalArgumentException> {
            snapshot(DiscoveryState.COMPLETE).copy(schemaVersion = 0)
        }
    }

    @Test
    fun aConflictNeedsAtLeastTwoDisagreeingEvidenceKinds() {
        assertFailsWith<IllegalArgumentException> {
            UnresolvedConflict(anc, listOf(EvidenceKind.EXPLICIT_DEVICE_RESPONSE))
        }
        val conflict = UnresolvedConflict(
            anc,
            listOf(EvidenceKind.EXPLICIT_DEVICE_RESPONSE, EvidenceKind.VERIFIED_PROTOCOL_DESCRIPTOR),
        )
        assertEquals(2, conflict.kinds.size)
    }

    @Test
    fun aPartialFailureRecordsAnExistingCategoryNotARawException() {
        // §15: a failure keeps its diagnostic shape (an existing OmniBudsErrorCategory, ADR-P8-004) rather than
        // smuggling an Android exception through the core.
        val failure = PartialFailure(eq, OmniBudsErrorCategory.INVALID_STATE)

        assertEquals(eq, failure.feature)
        assertEquals(OmniBudsErrorCategory.INVALID_STATE, failure.reason)
    }
}
