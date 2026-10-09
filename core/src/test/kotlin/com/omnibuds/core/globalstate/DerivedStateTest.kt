package com.omnibuds.core.globalstate

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Phase 24: derived-state, separation, and consistency tests.
 */
class DerivedStateTest {

    private fun connectedState(): GlobalDeviceState {
        val id = GlobalDeviceId("dev-1")
        return GlobalDeviceState.empty(id).copy(
            identity = IdentityState.Identified("m-1", "model-1", "high", "3.1"),
            connection = ConnectionState.Connected("s-1", 1L, "rfcomm"),
            protocol = ProtocolState.Resolved("p-1", "1.0", true),
            capabilities = CapabilityState.Ready(setOf("anc", "battery")),
        )
    }

    @Test
    fun `control permitted when all ready`() {
        val state = connectedState()
        assertTrue(DerivedState.isConnected(state))
        assertTrue(DerivedState.isIdentityComplete(state))
        assertTrue(DerivedState.isProtocolCompatible(state))
        assertTrue(DerivedState.isCapabilitiesReady(state))
        assertTrue(DerivedState.isControlPermitted(state))
    }

    @Test
    fun `control not permitted when disconnected`() {
        val state = connectedState().copy(connection = ConnectionState.Disconnected)
        assertFalse(DerivedState.isControlPermitted(state))
    }

    @Test
    fun `per-capability readiness`() {
        val state = connectedState()
        assertTrue(DerivedState.isCapabilityReady(state, "anc"))
        assertFalse(DerivedState.isCapabilityReady(state, "eq"))
    }

    @Test
    fun `desired differs from observed`() {
        val prov = ObservationProvenance("config", 1L, 1L, null, null, null)
        val state = connectedState().copy(
            features = FeatureState(
                desired = mapOf("anc" to ObservedValue("on", prov, Freshness.CURRENT)),
                observed = mapOf("anc" to ObservedValue("off", prov, Freshness.CURRENT)),
            ),
        )
        assertTrue(DerivedState.desiredDiffersFromObserved(state) == setOf("anc"))
    }

    @Test
    fun `stale observations detected`() {
        val prov = ObservationProvenance("feature", 1L, 1L, null, null, null)
        val state = connectedState().copy(
            features = FeatureState(
                observed = mapOf("anc" to ObservedValue("on", prov, Freshness.STALE)),
            ),
        )
        assertTrue(DerivedState.hasStaleObservations(state))
    }
}

/**
 * Phase 24: requested/executing/observed/persisted separation.
 */
class StateSeparationTest {

    @Test
    fun `desired does not imply observed`() = runTest {
        val repo = GlobalDeviceStateRepository()
        val prov = ObservationProvenance("config", 1L, 1L, null, null, null)
        repo.ingest(
            DeviceStateEvent.FeatureDesiredChanged(
                deviceId = GlobalDeviceId("dev-1"), sessionId = null,
                sequence = 0, sourceId = "config",
                featureId = "anc",
                value = ObservedValue("on", prov, Freshness.CURRENT),
            ),
        )
        val state = repo.getDevice(GlobalDeviceId("dev-1"))!!
        // Desired is set; observed remains empty. No automatic write.
        assertTrue(state.features.desired.containsKey("anc"))
        assertTrue(state.features.observed.isEmpty())
        assertTrue(state.features.executing.isEmpty())
    }

    @Test
    fun `ambiguous operation does not overwrite confirmed state`() = runTest {
        val repo = GlobalDeviceStateRepository()
        val prov = ObservationProvenance("feature", 1L, 1L, null, null, null)
        // Confirmed observation first.
        repo.ingest(
            DeviceStateEvent.FeatureObserved(
                deviceId = GlobalDeviceId("dev-1"), sessionId = null,
                sequence = 0, sourceId = "feature",
                featureId = "anc",
                value = ObservedValue("off", prov, Freshness.CURRENT),
            ),
        )
        // Ambiguous operation outcome.
        repo.ingest(
            DeviceStateEvent.FeatureOperationChanged(
                deviceId = GlobalDeviceId("dev-1"), sessionId = null,
                sequence = 1, sourceId = "feature",
                featureId = "anc",
                status = OperationStatus.Unknown("timeout after submission"),
            ),
        )
        val state = repo.getDevice(GlobalDeviceId("dev-1"))!!
        // Confirmed observation stands; operation marked unknown.
        assertTrue(state.features.observed["anc"]?.value == "off")
        assertTrue(state.features.executing["anc"] is OperationStatus.Unknown)
    }
}

/**
 * Phase 24: consistency validation.
 */
class ConsistencyTest {

    @Test
    fun `detects stale observation`() {
        val prov = ObservationProvenance("feature", 1L, 1L, null, null, null)
        val state = GlobalDeviceState.empty(GlobalDeviceId("dev-1")).copy(
            features = FeatureState(
                observed = mapOf("anc" to ObservedValue("on", prov, Freshness.STALE)),
            ),
        )
        val violations = StateConsistencyValidator.validate(state)
        assertTrue(violations.any { it.code == "STALE_OBSERVATION" })
    }

    @Test
    fun `detects ambiguous identity with resolved protocol`() {
        val state = GlobalDeviceState.empty(GlobalDeviceId("dev-1")).copy(
            identity = IdentityState.Ambiguous(listOf("a", "b"), "two candidates"),
            protocol = ProtocolState.Resolved("p-1", "1.0", true),
        )
        val violations = StateConsistencyValidator.validate(state)
        assertTrue(violations.any { it.code == "AMBIGUOUS_IDENTITY_WITH_RESOLVED_PROTOCOL" })
    }

    @Test
    fun `clean state has no violations`() {
        val state = GlobalDeviceState.empty(GlobalDeviceId("dev-1"))
        assertTrue(StateConsistencyValidator.validate(state).isEmpty())
    }
}
