package com.omnibuds.core.transport

import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.state.VerificationLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The safe-unknown resolution behaviour prompt §12 demands: the resolver classifies candidates and selects
 * nothing, orders nothing, and keeps "nothing offered" distinct from "all refused" — because Phase 6 must
 * not implement manufacturer transport-selection and must not auto-connect to a candidate.
 *
 * Tier T1. Fixtures are invented candidates; the only real claim is about the resolver's own logic.
 */
class TransportResolverTest {

    private fun available(kind: TransportKind) =
        TransportBoundary.established(kind, supportEvidence = VerificationLevel.LAB_TESTED)

    private fun refused(kind: TransportKind) =
        TransportBoundary.refused(kind, OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE)

    @Test
    fun noCandidatesIsNoEvidenceAndCarriesNoRefusal() {
        val resolution = UndeterminedTransportResolver.resolve(emptyList())

        assertEquals(ResolutionOutcome.NO_EVIDENCE, resolution.outcome)
        assertNull(resolution.reason, "absence is not a refusal (ADR-P0-016)")
        assertTrue(resolution.isUndetermined)
    }

    @Test
    fun allRefusedIsNoCandidateAvailableWithAReason() {
        val resolution = UndeterminedTransportResolver.resolve(
            listOf(refused(TransportKind.GATT), refused(TransportKind.RFCOMM)),
        )

        assertEquals(ResolutionOutcome.NO_CANDIDATE_AVAILABLE, resolution.outcome)
        assertEquals(OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE, resolution.reason)
    }

    @Test
    fun exactlyOneUsableCandidateIsReportedButNotSelected() {
        val resolution = UndeterminedTransportResolver.resolve(
            listOf(available(TransportKind.GATT), refused(TransportKind.CLASSIC_BLUETOOTH)),
        )

        assertEquals(ResolutionOutcome.SINGLE_CANDIDATE, resolution.outcome)
        assertNull(resolution.negotiation.selected, "the resolver never picks (ADR-P6-005)")
    }

    @Test
    fun severalUsableCandidatesStayAmbiguousWithoutAnOrder() {
        val resolution = UndeterminedTransportResolver.resolve(
            listOf(available(TransportKind.GATT), available(TransportKind.RFCOMM)),
        )

        assertEquals(ResolutionOutcome.CANDIDATES_AMBIGUOUS, resolution.outcome)
        assertNull(resolution.negotiation.selected)
    }

    @Test
    fun aResolutionCannotBeBuiltThatSelectsAChannel() {
        // The construction guard: a Phase 6 resolution naming a winner would be this phase opening a channel.
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            TransportResolution(
                negotiation = TransportNegotiation(
                    candidates = listOf(available(TransportKind.GATT)),
                    selected = TransportKind.GATT,
                ),
                outcome = ResolutionOutcome.SINGLE_CANDIDATE,
                confidence = VerificationLevel.INFERRED,
                reason = null,
            )
        }
    }

    @Test
    fun resolutionConfidenceIsCappedAtInferred() {
        // Nothing was opened, so no resolution may claim more than documentation-level support.
        val resolution = UndeterminedTransportResolver.resolve(listOf(available(TransportKind.GATT)))
        assertEquals(VerificationLevel.INFERRED, resolution.confidence)
    }
}
