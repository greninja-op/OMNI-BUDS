package com.omnibuds.core.session

import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.device.DeviceFingerprint
import com.omnibuds.core.device.DeviceIdentityRegistry
import com.omnibuds.core.device.IdentificationConfidence
import com.omnibuds.core.device.IdentificationResult
import com.omnibuds.core.device.IdentityEngine
import com.omnibuds.core.device.IdentitySignal
import com.omnibuds.core.device.IdentitySignalKind
import com.omnibuds.core.device.SignalReliability
import com.omnibuds.core.device.SignalSource
import com.omnibuds.core.platform.ConnectedDeviceSnapshot
import com.omnibuds.core.platform.DeviceAvailability
import com.omnibuds.core.platform.DeviceBondState
import com.omnibuds.core.platform.DeviceConnectionState
import com.omnibuds.core.platform.DeviceObservation
import com.omnibuds.core.platform.DeviceObservationKey
import com.omnibuds.core.platform.ObservationArrival
import com.omnibuds.core.platform.ObservationStage
import com.omnibuds.core.platform.ObservedProfile
import com.omnibuds.core.state.ConnectionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Prompt §14 and ADR-P5-009: enriching a session with a product identity must not recreate it, must
 * not move the connection, and must keep the matched conclusion separate from the reported identity.
 *
 * Tier T1. The identification is produced by the empty production registry, so every result is an
 * honest `Unknown` - no fake model match is planted to make an assertion easier.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhaseFiveSessionEnrichmentTest {

    private val key: DeviceObservationKey = requireNotNull(DeviceObservationKey.ofReportedAddress("00:11:22:AA:BB:CC"))

    private val identityEngine = IdentityEngine(DeviceIdentityRegistry.empty())

    private fun projection(): ConnectedDeviceSnapshot = ConnectedDeviceSnapshot(
        stage = ObservationStage.OBSERVING,
        records = listOf(
            DeviceObservation.reported(
                key = key,
                link = DeviceConnectionState.CONNECTED,
                bond = DeviceBondState.NONE,
                availability = DeviceAvailability.AVAILABLE,
                displayName = "Example Buds",
                observedProfiles = setOf(ObservedProfile.A2DP),
                arrival = ObservationArrival.SNAPSHOT,
                observedAtEpochMillis = 1_000L,
            ),
        ),
        pairedDevices = emptyList(),
        unansweredProfiles = emptySet(),
        profileSupportConsulted = true,
        restsOnCompletedRound = true,
        deviceRoundRefusal = null,
        restsOnCompletedPairedRound = false,
        pairedRoundRefusal = null,
        refusedTransitions = emptyList(),
        observedAtEpochMillis = 1_000L,
    )

    private fun unknownResult(): IdentificationResult = identityEngine.identify(
        listOf(
            IdentitySignal.observed(
                kind = IdentitySignalKind.REPORTED_NAME,
                value = "Example Buds",
                source = SignalSource.PLATFORM,
                reliability = SignalReliability.VENDOR_REPORTED_TEXT,
            ),
        ),
    )

    @Test
    fun enrichmentAttachesTheProductIdentityAndLeavesTheSessionIntact() = runTest {
        val sessions = DeviceSessionEngine()
        sessions.apply(projection())
        val sessionId = sessions.snapshot.value.sessions.single().sessionId

        val outcome = sessions.enrichIdentity(sessionId, DeviceFingerprint.empty(), unknownResult())

        assertIs<OperationOutcome.Success<*>>(outcome)
        val tracked = sessions.snapshot.value.sessions.single()
        assertEquals(sessionId, tracked.sessionId, "enrichment must not re-mint the session")
        assertNotNull(tracked.productIdentity)
        assertEquals(IdentificationConfidence.UNKNOWN, tracked.productIdentity?.confidence)
    }

    @Test
    fun enrichmentNeverMovesTheConnectionOrBumpsTheStateRevision() = runTest {
        val sessions = DeviceSessionEngine()
        sessions.apply(projection())
        val before = sessions.snapshot.value.sessions.single()
        val revisionBefore = before.state.revision
        assertEquals(ConnectionState.CONNECTED, before.connectionState)

        sessions.enrichIdentity(before.sessionId, DeviceFingerprint.empty(), unknownResult())

        val after = sessions.snapshot.value.sessions.single()
        assertEquals(ConnectionState.CONNECTED, after.connectionState, "identification is not a transition")
        assertEquals(revisionBefore, after.state.revision, "an enrichment must not beat a real change")
    }

    @Test
    fun aMatchedManufacturerNeverOverwritesAReportedOne() = runTest {
        val sessions = DeviceSessionEngine()
        sessions.apply(projection())
        val sessionId = sessions.snapshot.value.sessions.single().sessionId

        sessions.enrichIdentity(sessionId, DeviceFingerprint.empty(), unknownResult())

        val tracked = sessions.snapshot.value.sessions.single()
        assertNull(tracked.session.identity.manufacturer, "the platform reported no manufacturer")
        assertIs<IdentificationResult.Unknown>(tracked.productIdentity)
    }

    @Test
    fun enrichingAnUnknownSessionIdIsRefusedAndCreatesNothing() = runTest {
        val sessions = DeviceSessionEngine()
        sessions.apply(projection())
        val countBefore = sessions.snapshot.value.sessions.size

        val outcome = sessions.enrichIdentity("session-does-not-exist", DeviceFingerprint.empty(), unknownResult())

        assertIs<OperationOutcome.Failure>(outcome)
        assertEquals(countBefore, sessions.snapshot.value.sessions.size, "a refusal must not open a session")
    }

    @Test
    fun enrichmentPublishesItsEdgeWithoutAConnectionMove() = runTest {
        val sessions = DeviceSessionEngine()
        val observed = mutableListOf<DeviceSessionEvent>()
        val collector: Job = launch { sessions.events.collect { event -> observed += event } }
        runCurrent()
        sessions.apply(projection())
        val sessionId = sessions.snapshot.value.sessions.single().sessionId

        sessions.enrichIdentity(sessionId, DeviceFingerprint.empty(), unknownResult())
        runCurrent()
        collector.cancel()

        val enriched = observed.filterIsInstance<DeviceSessionEvent.SessionIdentityEnriched>()
        assertEquals(1, enriched.size, "the enrichment published exactly one edge")
        assertEquals(sessionId, enriched.single().sessionId)
        assertTrue(enriched.single().isIdentified.not(), "an Unknown result must not read as identified")
        assertTrue(
            observed.none { event -> event is DeviceSessionEvent.SessionUpdated },
            "an enrichment is not a connection move",
        )
    }

    @Test
    fun theFingerprintCarriesNoDimensionPhaseFiveCannotSee() {
        val fingerprint = identityEngine.buildFingerprint(
            listOf(
                IdentitySignal.observed(
                    kind = IdentitySignalKind.SERVICE_UUID,
                    value = "0000180f-0000-1000-8000-00805f9b34fb",
                    source = SignalSource.PLATFORM,
                    reliability = SignalReliability.PLATFORM_ATTRIBUTE,
                ),
            ),
        )

        assertTrue(fingerprint.serviceUuids.isNotEmpty())
        assertTrue(fingerprint.manufacturerData.isEmpty())
        assertTrue(fingerprint.characteristicUuids.isEmpty())
    }
}
