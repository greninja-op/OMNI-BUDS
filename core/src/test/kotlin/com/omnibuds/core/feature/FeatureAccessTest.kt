package com.omnibuds.core.feature

import com.omnibuds.core.capability.CapabilityAvailability
import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.state.CapabilityState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Access derivation: support vs. availability vs. readability, kept apart.
 *
 * The derivation answers "may a control be offered / an operation attempted
 * *now*". The support verdict itself stays in the capability record; this
 * vocabulary only restates affordances for the feature layer.
 */
class FeatureAccessTest {

    private val feature = FeatureId.of("noise-control", "anc")

    private fun access(
        state: CapabilityState,
        availability: CapabilityAvailability = CapabilityAvailability.AVAILABLE,
    ): FeatureAccess = accessOf(
        FeatureTestFixtures.record(feature, state),
        availability,
    )

    @Test
    fun unknownCapabilityIsUnknownAccess() {
        assertEquals(FeatureAccess.UNKNOWN, access(CapabilityState.UNKNOWN))
    }

    @Test
    fun readOnlyCapabilityIsReadOnlyAccess() {
        assertEquals(FeatureAccess.READ_ONLY, access(CapabilityState.READ_ONLY))
    }

    @Test
    fun controllableRungsAreReadWriteAccess() {
        assertEquals(FeatureAccess.READ_WRITE, access(CapabilityState.SUPPORTED_VOLATILE))
        assertEquals(FeatureAccess.READ_WRITE, access(CapabilityState.SUPPORTED_PERSISTENT))
        assertEquals(FeatureAccess.READ_WRITE, access(CapabilityState.PERSISTENCE_VERIFIED))
    }

    @Test
    fun unavailabilityWinsOverAnySupportRung() {
        assertEquals(
            FeatureAccess.UNAVAILABLE,
            access(CapabilityState.SUPPORTED_VOLATILE, CapabilityAvailability.UNAVAILABLE),
        )
        assertEquals(
            FeatureAccess.UNAVAILABLE,
            access(CapabilityState.SUPPORTED_VOLATILE, CapabilityAvailability.TEMPORARILY_UNAVAILABLE),
        )
        // Unknown availability is not unavailability: nothing was observed either way.
        assertEquals(
            FeatureAccess.READ_WRITE,
            access(CapabilityState.SUPPORTED_VOLATILE, CapabilityAvailability.UNKNOWN),
        )
    }

    @Test
    fun unsupportedMapsToUnavailableButTheGateRejectsItFirst() {
        // The validator rejects UNSUPPORTED before access is consulted; this arm
        // exists only to keep the derivation total.
        assertEquals(FeatureAccess.UNAVAILABLE, access(CapabilityState.UNSUPPORTED))
    }

    @Test
    fun permitsEnforcesReadOnly() {
        assertTrue(FeatureAccess.READ_WRITE.permits(FeatureOperationType.READ))
        assertTrue(FeatureAccess.READ_WRITE.permits(FeatureOperationType.WRITE))
        assertTrue(FeatureAccess.READ_ONLY.permits(FeatureOperationType.READ))
        assertFalse(FeatureAccess.READ_ONLY.permits(FeatureOperationType.WRITE))
        assertFalse(FeatureAccess.UNKNOWN.permits(FeatureOperationType.READ))
        assertFalse(FeatureAccess.UNAVAILABLE.permits(FeatureOperationType.WRITE))
    }

    @Test
    fun unknownTransportDoesNotChangeAccess() {
        // Transport reachability is the validator's step 8, not access's job.
        val record = FeatureTestFixtures.record(
            feature,
            CapabilityState.SUPPORTED_VOLATILE,
            transport = TransportKind.UNKNOWN,
        )
        assertEquals(
            FeatureAccess.READ_WRITE,
            accessOf(record, CapabilityAvailability.AVAILABLE),
        )
    }
}
