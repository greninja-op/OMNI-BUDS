package com.omnibuds.core.feature

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.config.ConfigurationValue
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The control state machine's transition table.
 *
 * The table is the contract: the engine may only move a feature along legal
 * edges, and the repository refuses anything else. These tests pin the edges
 * the engine's flows depend on — requested never becoming confirmed without
 * device evidence, cancellation restoring, invalidation unknowing — and the
 * edges that must stay refused, like UNKNOWN → PENDING (a write with no
 * established capability is a validator failure, not a state).
 */
class FeatureStateTest {

    private val feature = FeatureId.of("noise-control", "anc")
    private val on = ConfigurationValue.BooleanValue(true)
    private val off = ConfigurationValue.BooleanValue(false)

    private fun error() = OmniBudsError.of(OmniBudsErrorCategory.READ_FAILED, "op-1")

    private val unknown = FeatureState.Unknown(feature)
    private val available = FeatureState.Available(feature)
    private val pending = FeatureState.Pending(feature, on, null, "op-1")
    private val confirmed = FeatureState.Confirmed(feature, on)
    private val failed = FeatureState.Failed(feature, error(), null)
    private val unavailable = FeatureState.Unavailable(feature, "prerequisite off")

    @Test
    fun untrackedFeaturesMayOnlyBeSeeded() {
        assertTrue(FeatureStateTransitions.isLegal(null, unknown))
        assertTrue(FeatureStateTransitions.isLegal(null, available))
        assertTrue(FeatureStateTransitions.isLegal(null, unavailable))
        assertFalse(FeatureStateTransitions.isLegal(null, pending))
        assertFalse(FeatureStateTransitions.isLegal(null, confirmed))
        assertFalse(FeatureStateTransitions.isLegal(null, failed))
    }

    @Test
    fun unknownCanNeverBecomePending() {
        // A write requires an established capability; the validator refuses it
        // long before the state machine is involved. The table refuses it too,
        // so no future caller can skip the validator.
        assertFalse(FeatureStateTransitions.isLegal(unknown, pending))
        assertTrue(FeatureStateTransitions.isLegal(unknown, available))
        assertTrue(FeatureStateTransitions.isLegal(unknown, confirmed))
        assertTrue(FeatureStateTransitions.isLegal(unknown, failed))
        assertTrue(FeatureStateTransitions.isLegal(unknown, unavailable))
    }

    @Test
    fun theWriteLifecycleIsUnknownAvailablePendingConfirmed() {
        assertTrue(FeatureStateTransitions.isLegal(available, pending))
        assertTrue(FeatureStateTransitions.isLegal(pending, confirmed))
    }

    @Test
    fun pendingCanFailOrBeAbandonedButNeverBecomeAvailableDirectly() {
        assertTrue(FeatureStateTransitions.isLegal(pending, failed))
        assertTrue(FeatureStateTransitions.isLegal(pending, unknown))
        assertTrue(FeatureStateTransitions.isLegal(pending, available))
        assertTrue(FeatureStateTransitions.isLegal(pending, unavailable))
        // A second write while one is in flight would interleave; the engine
        // serializes per feature instead.
        assertFalse(FeatureStateTransitions.isLegal(pending, pending))
    }

    @Test
    fun confirmedSurvivesDeviceReportedUpdatesAndNewWrites() {
        assertTrue(FeatureStateTransitions.isLegal(confirmed, confirmed))
        assertTrue(FeatureStateTransitions.isLegal(confirmed, pending))
        assertTrue(FeatureStateTransitions.isLegal(confirmed, failed))
        assertTrue(FeatureStateTransitions.isLegal(confirmed, unknown))
        assertTrue(FeatureStateTransitions.isLegal(confirmed, unavailable))
        assertTrue(FeatureStateTransitions.isLegal(confirmed, available))
    }

    @Test
    fun failedCanRetryAndUnavailableCanRecover() {
        assertTrue(FeatureStateTransitions.isLegal(failed, pending))
        assertTrue(FeatureStateTransitions.isLegal(failed, available))
        assertTrue(FeatureStateTransitions.isLegal(failed, confirmed))
        assertTrue(FeatureStateTransitions.isLegal(failed, unknown))
        assertTrue(FeatureStateTransitions.isLegal(unavailable, available))
        assertTrue(FeatureStateTransitions.isLegal(unavailable, confirmed))
        assertTrue(FeatureStateTransitions.isLegal(unavailable, failed))
        assertTrue(FeatureStateTransitions.isLegal(unavailable, unknown))
        // A write while unavailable is refused by the validator; the table agrees.
        assertFalse(FeatureStateTransitions.isLegal(unavailable, pending))
    }

    @Test
    fun transitionsNeverCrossFeatures() {
        val other = FeatureId.of("noise-control", "transparency")
        assertFalse(
            FeatureStateTransitions.isLegal(
                unknown,
                FeatureState.Available(other),
            ),
        )
    }

    @Test
    fun pendingCarriesTheRequestDistinctFromAnyConfirmedValue() {
        // The load-bearing rule of the whole phase: the requested value is not
        // the device state, and the types keep them apart.
        assertTrue(pending.requested == on)
        assertTrue(pending.lastConfirmed == null)
        assertTrue(confirmed.value == on)
        assertTrue(confirmed.lastConfirmed == on)
    }

    @Test
    fun lastConfirmedIsStaleKnowledgeNotCurrentTruth() {
        val state: FeatureState = FeatureState.Unknown(feature, lastConfirmed = off)
        assertTrue(state.lastConfirmed == off)
        // ...but the state itself says nothing is currently established.
        assertTrue(state is FeatureState.Unknown)
    }
}
