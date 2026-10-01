package com.omnibuds.core.capability

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.state.CapabilityState
import com.omnibuds.core.state.VerificationLevel
import com.omnibuds.core.state.isControllable
import com.omnibuds.core.common.TransportKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * All six capability states must be expressible, and no illegal combination of a state
 * with an affordance or a verification tier may be constructible at all
 * (P1-DOM-002, phase-1 prompt sections 13, 36 "Capability" and 53).
 *
 * `noise-control.anc` is used as the subject throughout: a functional identity, so no
 * test here leans on a brand.
 */
class FeatureCapabilityTest {

    private val anc: FeatureId = FeatureId.of("noise-control", "anc")

    private fun record(
        state: CapabilityState,
        readable: Boolean,
        writable: Boolean,
        verification: VerificationLevel,
        protocolId: String? = "phase-1-fixture-protocol",
    ): FeatureCapability = FeatureCapability(
        feature = anc,
        state = state,
        readable = readable,
        writable = writable,
        transport = TransportKind.GATT,
        protocolId = protocolId,
        requiresConnection = true,
        verification = verification,
    )

    /** The only affordance pattern each state is allowed to carry. */
    private fun legalAffordances(state: CapabilityState): Pair<Boolean, Boolean> = when (state) {
        CapabilityState.UNKNOWN,
        CapabilityState.UNSUPPORTED,
        -> false to false

        CapabilityState.READ_ONLY -> true to false

        CapabilityState.SUPPORTED_VOLATILE,
        CapabilityState.SUPPORTED_PERSISTENT,
        CapabilityState.PERSISTENCE_VERIFIED,
        -> true to true
    }

    /** The lowest verification tier that does not contradict [state]. */
    private fun legalVerification(state: CapabilityState): VerificationLevel = when (state) {
        CapabilityState.PERSISTENCE_VERIFIED -> VerificationLevel.PERSISTENCE_VERIFIED
        else -> VerificationLevel.HARDWARE_VERIFIED
    }

    private fun assertRejected(
        state: CapabilityState,
        readable: Boolean,
        writable: Boolean,
        verification: VerificationLevel = legalVerification(state),
    ) {
        assertFailsWith<IllegalArgumentException> {
            record(state = state, readable = readable, writable = writable, verification = verification)
        }
    }

    /** Every state is reachable with its own honest affordances, and each stays distinct. */
    @Test
    fun allSixStatesAreConstructibleAndRemainDistinct() {
        val records = CapabilityState.entries.map { state ->
            val affordances = legalAffordances(state)
            record(
                state = state,
                readable = affordances.first,
                writable = affordances.second,
                verification = legalVerification(state),
            )
        }

        assertEquals(CapabilityState.entries.size, records.size, "one record per state")
        assertEquals(
            CapabilityState.entries.toSet(),
            records.map { it.state }.toSet(),
            "no state is missing and none stands in for another",
        )
        assertEquals(records.map { it.state }.distinct().size, records.size, "states are distinct")
    }

    @Test
    fun unknownCarriesNoAffordanceBecauseNothingIsEstablished() {
        val unknown = record(
            state = CapabilityState.UNKNOWN,
            readable = false,
            writable = false,
            verification = VerificationLevel.INFERRED,
        )

        assertEquals(CapabilityState.UNKNOWN, unknown.state)
        assertFalse(unknown.readable)
        assertFalse(unknown.writable)
        assertFalse(unknown.isControllable)
    }

    @Test
    fun unsupportedAndUnknownAreDifferentStatesWithTheSameAbsenceOfAffordance() {
        val unsupported = record(
            state = CapabilityState.UNSUPPORTED,
            readable = false,
            writable = false,
            verification = VerificationLevel.HARDWARE_VERIFIED,
        )

        assertEquals(CapabilityState.UNSUPPORTED, unsupported.state)
        assertFalse(unsupported.readable)
        assertFalse(unsupported.writable)
    }

    @Test
    fun readOnlyIsReadableAndRefusesToWrite() {
        val readOnly = record(
            state = CapabilityState.READ_ONLY,
            readable = true,
            writable = false,
            verification = VerificationLevel.HARDWARE_VERIFIED,
        )

        assertTrue(readOnly.readable)
        assertFalse(readOnly.writable)
        assertFalse(readOnly.isControllable, "a reading is not a control")
    }

    @Test
    fun everyControllableStateIsBothReadableAndWritable() {
        listOf(
            CapabilityState.SUPPORTED_VOLATILE,
            CapabilityState.SUPPORTED_PERSISTENT,
            CapabilityState.PERSISTENCE_VERIFIED,
        ).forEach { state ->
            val control = record(
                state = state,
                readable = true,
                writable = true,
                verification = legalVerification(state),
            )

            assertTrue(control.isControllable, "$state should be controllable")
            assertTrue(control.readable, "$state must be readable")
            assertTrue(control.writable, "$state must be writable")
        }
    }

    @Test
    fun unknownRefusesAnAffordanceItHasNeverEstablished() {
        assertRejected(CapabilityState.UNKNOWN, readable = true, writable = false)
        assertRejected(CapabilityState.UNKNOWN, readable = false, writable = true)
        assertRejected(CapabilityState.UNKNOWN, readable = true, writable = true)
    }

    @Test
    fun unsupportedRefusesAnAffordanceItDenies() {
        assertRejected(CapabilityState.UNSUPPORTED, readable = false, writable = true)
        assertRejected(CapabilityState.UNSUPPORTED, readable = true, writable = false)
        assertRejected(CapabilityState.UNSUPPORTED, readable = true, writable = true)
    }

    @Test
    fun readOnlyRefusesBothSilenceAndWriteAccess() {
        assertRejected(CapabilityState.READ_ONLY, readable = false, writable = false)
        assertRejected(CapabilityState.READ_ONLY, readable = true, writable = true)
        assertRejected(CapabilityState.READ_ONLY, readable = false, writable = true)
    }

    @Test
    fun volatileSupportRefusesAnIncompleteAffordancePair() {
        assertRejected(CapabilityState.SUPPORTED_VOLATILE, readable = false, writable = true)
        assertRejected(CapabilityState.SUPPORTED_VOLATILE, readable = true, writable = false)
        assertRejected(CapabilityState.SUPPORTED_VOLATILE, readable = false, writable = false)
    }

    @Test
    fun persistentSupportRefusesAnIncompleteAffordancePair() {
        assertRejected(CapabilityState.SUPPORTED_PERSISTENT, readable = false, writable = true)
        assertRejected(CapabilityState.SUPPORTED_PERSISTENT, readable = true, writable = false)
    }

    @Test
    fun persistenceVerifiedRefusesAnUnwritableControl() {
        assertRejected(
            state = CapabilityState.PERSISTENCE_VERIFIED,
            readable = true,
            writable = false,
            verification = VerificationLevel.PERSISTENCE_VERIFIED,
        )
    }

    @Test
    fun aPersistenceClaimCannotRestOnLabEvidence() {
        listOf(
            VerificationLevel.INFERRED,
            VerificationLevel.IMPLEMENTED,
            VerificationLevel.LAB_TESTED,
        ).forEach { tier ->
            assertRejected(
                state = CapabilityState.SUPPORTED_PERSISTENT,
                readable = true,
                writable = true,
                verification = tier,
            )
        }
    }

    @Test
    fun observedSurvivalIsTheOnlyEvidenceForPersistenceVerified() {
        listOf(
            VerificationLevel.INFERRED,
            VerificationLevel.IMPLEMENTED,
            VerificationLevel.LAB_TESTED,
            VerificationLevel.HARDWARE_VERIFIED,
        ).forEach { tier ->
            assertRejected(
                state = CapabilityState.PERSISTENCE_VERIFIED,
                readable = true,
                writable = true,
                verification = tier,
            )
        }

        val verified = record(
            state = CapabilityState.PERSISTENCE_VERIFIED,
            readable = true,
            writable = true,
            verification = VerificationLevel.PERSISTENCE_VERIFIED,
        )
        assertEquals(VerificationLevel.PERSISTENCE_VERIFIED, verified.verification)
    }

    @Test
    fun aHardwareVerifiedControlIsLegalAtTheVolatileRungWithoutAnyPersistenceClaim() {
        val volatile = record(
            state = CapabilityState.SUPPORTED_VOLATILE,
            readable = true,
            writable = true,
            verification = VerificationLevel.HARDWARE_VERIFIED,
        )

        assertTrue(volatile.isControllable)
        assertEquals(CapabilityState.SUPPORTED_VOLATILE, volatile.state)
    }

    @Test
    fun anUnknownProtocolIsRecordedAsAbsentRatherThanInvented() {
        val noProtocol = record(
            state = CapabilityState.READ_ONLY,
            readable = true,
            writable = false,
            verification = VerificationLevel.INFERRED,
            protocolId = null,
        )

        assertNull(noProtocol.protocolId, "no governing protocol identified is a legitimate answer")
    }

    @Test
    fun aBlankProtocolIdIsRefusedBecauseItIsIndistinguishableFromNone() {
        assertFailsWith<IllegalArgumentException> {
            record(
                state = CapabilityState.READ_ONLY,
                readable = true,
                writable = false,
                verification = VerificationLevel.HARDWARE_VERIFIED,
                protocolId = "   ",
            )
        }
    }

    /** Controllability is the kernel's rule about the state, never a caller's opinion. */
    @Test
    fun controllabilityIsDerivedFromStateAndNeverFromTheFlags() {
        CapabilityState.entries.forEach { state ->
            val affordances = legalAffordances(state)
            val built = record(
                state = state,
                readable = affordances.first,
                writable = affordances.second,
                verification = legalVerification(state),
            )

            assertEquals(state.isControllable(), built.isControllable, "controllability for state $state")
        }
    }

    @Test
    fun theFactoryRecordsForUnknownAndUnsupportedSayWhatTheirNamesSay() {
        val unknown = FeatureCapability.unknown(anc)
        assertEquals(CapabilityState.UNKNOWN, unknown.state)
        assertFalse(unknown.readable)
        assertFalse(unknown.writable)
        assertFalse(unknown.isControllable)

        val unsupported = FeatureCapability.unsupported(anc, "phase-1-fixture-protocol", TransportKind.RFCOMM)
        assertEquals(CapabilityState.UNSUPPORTED, unsupported.state)
        assertFalse(unsupported.isControllable)
        assertEquals(TransportKind.RFCOMM, unsupported.transport)
    }

    /**
     * Section 53 in both directions: absence of a record is not absence of capability, so
     * the two factory records are distinct values and neither is interchangeable with the
     * other.
     */
    @Test
    fun unknownIsNotUnsupportedAndCannotBeComparedAsIfItWere() {
        val unknown = FeatureCapability.unknown(anc)
        val unsupported = FeatureCapability.unsupported(anc, "phase-1-fixture-protocol", TransportKind.UNKNOWN)

        assertEquals(CapabilityState.UNKNOWN, unknown.state)
        assertEquals(CapabilityState.UNSUPPORTED, unsupported.state)
        assertFalse(unknown == unsupported, "an unasked question and a negative answer are not equal")
    }
}
