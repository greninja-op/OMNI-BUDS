package com.omnibuds.core.session

import com.omnibuds.core.audio.AudioTransportState
import com.omnibuds.core.capability.DeviceCapabilities
import com.omnibuds.core.capability.FeatureCapability
import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.device.BatteryState
import com.omnibuds.core.device.DeviceIdentity
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.state.CapabilityState
import com.omnibuds.core.state.ConnectionState
import com.omnibuds.core.state.VerificationLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The single authoritative device state, and the anti-staleness guarantees that come
 * with owning it.
 *
 * These tests exist because the Phase 1 architecture review initially answered NO to
 * question 8 — two types held connection state for the same device — and because
 * `applyIfNewer` and `revision` were written but never exercised. An unexercised
 * safety mechanism is not a safety mechanism (Phase 1 prompt sections 30, 36, 54, and
 * `docs/phases/phase-0/testing-governance.md`).
 */
class DeviceStateTest {

    private val identity = DeviceIdentity(
        manufacturer = "Example Audio",
        model = "Buds One",
        displayName = null,
        modelId = null,
    )

    private val anc = FeatureId.of("noise-control", "anc")

    private fun unknownCapability(feature: FeatureId = anc) = FeatureCapability(
        feature = feature,
        state = CapabilityState.UNKNOWN,
        readable = false,
        writable = false,
        transport = TransportKind.UNKNOWN,
        protocolId = null,
        requiresConnection = true,
        verification = VerificationLevel.INFERRED,
    )

    @Test
    fun anInitialStateAssertsNothingAboutAnything() {
        val state = DeviceState.initial(SESSION, identity)

        assertEquals(ConnectionState.UNKNOWN, state.connection)
        assertEquals(DeviceState.INITIAL_REVISION, state.revision)
        assertNull(state.lastUpdatedEpochMillis)
        assertFalse(state.isOperational)
        assertTrue(state.battery.hasAnyKnownLevel.not())
        assertTrue(state.audio.isFullyObserved.not())
    }

    @Test
    fun anAbsentFeatureReadsAsUnknownAndNotUnsupported() {
        val state = DeviceState.initial(SESSION, identity)

        assertEquals(CapabilityState.UNKNOWN, state.capabilities.stateOf(anc))
        assertFalse(state.capabilities.unsupported.contains(anc))
    }

    @Test
    fun aLegalConnectionMoveAdvancesTheRevision() {
        val initial = DeviceState.initial(SESSION, identity)

        val outcome = initial.attemptConnection(ConnectionState.CONNECTED, AT)

        val moved = assertIs<OperationOutcome.Success<DeviceState>>(outcome)
        assertEquals(ConnectionState.CONNECTED, moved.value.connection)
        assertEquals(1L, moved.value.revision)
        assertEquals(AT, moved.value.lastUpdatedEpochMillis)
        assertEquals(initial, DeviceState.initial(SESSION, identity))
    }

    @Test
    fun anIllegalConnectionMoveIsRefusedWithStructuredErrorAndChangesNothing() {
        val disconnected = DeviceState.initial(SESSION, identity)
            .attemptConnection(ConnectionState.DISCONNECTED, AT)
            .let { assertIs<OperationOutcome.Success<DeviceState>>(it).value }

        val outcome = disconnected.attemptConnection(ConnectionState.CONTROL_SESSION, AT)

        val failure = assertIs<OperationOutcome.Failure>(outcome)
        assertEquals(OmniBudsErrorCategory.INVALID_STATE, failure.error.category)
        assertEquals(ConnectionState.DISCONNECTED, disconnected.connection)
        assertEquals(1L, disconnected.revision, "a refused move must not bump the revision")
    }

    @Test
    fun everyStateCanFallIntoErrorAndASelfMoveIsIdempotent() {
        ConnectionState.entries.forEach { from ->
            val state = DeviceState.initial(SESSION, identity).forceConnection(from)

            assertIs<OperationOutcome.Success<DeviceState>>(
                state.attemptConnection(ConnectionState.ERROR, AT),
                "every state must be able to reach ERROR, but $from could not",
            )
            assertIs<OperationOutcome.Success<DeviceState>>(
                state.attemptConnection(from, AT),
                "staying in $from must not be treated as an illegal move",
            )
        }
    }

    @Test
    fun aStaleResponseCannotOverwriteNewerState() {
        val current = DeviceState.initial(SESSION, identity)
            .attemptConnection(ConnectionState.CONNECTED, AT)
            .let { assertIs<OperationOutcome.Success<DeviceState>>(it).value }
        val stale = current.copy(connection = ConnectionState.DISCONNECTED, revision = current.revision - 1)

        assertEquals(current, current.applyIfNewer(stale))
    }

    @Test
    fun anEqualRevisionIsNotNewerInformation() {
        val current = DeviceState.initial(SESSION, identity)

        assertEquals(current, current.applyIfNewer(current.copy(revision = current.revision)))
    }

    @Test
    fun aNewerStateFromADifferentSessionIsNeverAdopted() {
        val current = DeviceState.initial(SESSION, identity)
        val other = DeviceState.initial("other-session", identity).copy(revision = 9L)

        assertEquals(current, current.applyIfNewer(other))
    }

    @Test
    fun aNewerStateFromTheSameSessionIsAdopted() {
        val current = DeviceState.initial(SESSION, identity)
        val newer = current.copy(revision = current.revision + 1, connection = ConnectionState.READY)

        assertEquals(newer, current.applyIfNewer(newer))
    }

    @Test
    fun eachUpdateMovesTheRevisionForwardAndLeavesTheOriginalAlone() {
        val start = DeviceState.initial(SESSION, identity)

        val withCapability = start.withCapability(anc, unknownCapability(), AT)
        val withBattery = withCapability.withBattery(BatteryState(leftLevel = 80, rightLevel = 74), AT)
        val withAudio = withBattery.withAudio(AudioTransportState.unobserved(), AT)
        val withCapabilities = withAudio.withCapabilities(DeviceCapabilities.empty(), AT)

        assertEquals(1L, withCapability.revision)
        assertEquals(2L, withBattery.revision)
        assertEquals(3L, withAudio.revision)
        assertEquals(4L, withCapabilities.revision)
        assertEquals(DeviceState.INITIAL_REVISION, start.revision)
        assertEquals(CapabilityState.UNKNOWN, withCapability.capabilities.stateOf(anc))
    }

    @Test
    fun disconnectingClearsLiveReadingsButKeepsEstablishedCapabilityKnowledge() {
        val ready = DeviceState.initial(SESSION, identity)
            .attemptConnection(ConnectionState.CONNECTED, AT)
            .let { assertIs<OperationOutcome.Success<DeviceState>>(it).value }
            .attemptConnection(ConnectionState.IDENTIFYING, AT)
            .let { assertIs<OperationOutcome.Success<DeviceState>>(it).value }
            .attemptConnection(ConnectionState.CAPABILITY_DISCOVERY, AT)
            .let { assertIs<OperationOutcome.Success<DeviceState>>(it).value }
            .attemptConnection(ConnectionState.READY, AT)
            .let { assertIs<OperationOutcome.Success<DeviceState>>(it).value }
            .withBattery(BatteryState(leftLevel = 62, rightLevel = 61), AT)
            .withCapability(anc, unknownCapability(), AT)

        val afterDisconnect = ready.invalidatedForDisconnect(AT)

        assertEquals(ConnectionState.DISCONNECTED, afterDisconnect.connection)
        assertNull(afterDisconnect.battery.leftLevel)
        assertEquals(ready.capabilities, afterDisconnect.capabilities)
        assertTrue(afterDisconnect.revision > ready.revision)
    }

    @Test
    fun operationalOnlyWhileReadyOrInAControlSession() {
        val connected = DeviceState.initial(SESSION, identity)
            .attemptConnection(ConnectionState.CONNECTED, AT)
            .let { assertIs<OperationOutcome.Success<DeviceState>>(it).value }

        assertFalse(connected.isOperational)

        val ready = connected
            .attemptConnection(ConnectionState.IDENTIFYING, AT)
            .let { assertIs<OperationOutcome.Success<DeviceState>>(it).value }
            .attemptConnection(ConnectionState.CAPABILITY_DISCOVERY, AT)
            .let { assertIs<OperationOutcome.Success<DeviceState>>(it).value }
            .attemptConnection(ConnectionState.READY, AT)
            .let { assertIs<OperationOutcome.Success<DeviceState>>(it).value }

        assertTrue(ready.isOperational)
        val controlling = ready
            .attemptConnection(ConnectionState.CONTROL_SESSION, AT)
            .let { assertIs<OperationOutcome.Success<DeviceState>>(it).value }
        assertTrue(controlling.isOperational)
    }

    private fun DeviceState.forceConnection(state: ConnectionState): DeviceState =
        copy(connection = state)
}

private const val SESSION = "session-under-test"
private const val AT = 1_700_000_000_000L
