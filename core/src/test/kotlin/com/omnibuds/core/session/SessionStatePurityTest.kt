package com.omnibuds.core.session

import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.device.DeviceIdentity
import com.omnibuds.core.device.DeviceSession
import com.omnibuds.core.platform.ConnectedDeviceSnapshot
import com.omnibuds.core.platform.DeviceObservation
import com.omnibuds.core.platform.DeviceObservationKey
import com.omnibuds.core.platform.ObservationStage
import com.omnibuds.core.platform.ObservedProfile
import com.omnibuds.core.state.ConnectionState
import com.omnibuds.core.state.SessionClassification
import com.omnibuds.core.state.VerificationLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The invariants the session types hold between themselves, and the claims they refuse to make.
 *
 * Where `DeviceSessionEngineTest` drives behaviour round by round, this file asks the structural
 * questions: can the types disagree with each other, can a state that implies later-phase work be
 * constructed here at all, and can an identifier escape through a `toString()`. These are the
 * checks that fail loudly when someone adds a second lifecycle field, which is the shape of the
 * mistake Phase 1 already made once and deleted (`device/DeviceSession.kt:9-15`).
 */
class SessionStatePurityTest {

    @Test
    fun theEngineCanOnlyReachSixStatesAndNoneOfThemImpliesCapabilityWork() {
        val forbidden = setOf(
            ConnectionState.IDENTIFYING,
            ConnectionState.CAPABILITY_DISCOVERY,
            ConnectionState.READY,
            ConnectionState.CONTROL_SESSION,
        )

        assertEquals(
            setOf(
                ConnectionState.UNKNOWN,
                ConnectionState.DISCOVERED,
                ConnectionState.PAIRED,
                ConnectionState.CONNECTED,
                ConnectionState.TEMPORARILY_UNAVAILABLE,
                ConnectionState.DISCONNECTED,
            ),
            TrackedDeviceSession.reachableStates,
            "the reachable set changed, and ADR-P4-003 has to be re-decided rather than widened",
        )
        assertTrue(forbidden.none { state -> state in TrackedDeviceSession.reachableStates })
        assertFalse(ConnectionState.ERROR in TrackedDeviceSession.reachableStates)
    }

    @Test
    fun aSessionCannotBeBuiltWhereTheStateAndTheRecordDisagreeAboutIdentity() {
        val shared = DeviceIdentity.of(displayName = "Air")
        val record = DeviceSession.temporary("session-1", shared)
        val diverged = DeviceState.initial("session-1", DeviceIdentity.of(displayName = "Other"))

        assertFailsWith<IllegalArgumentException> {
            TrackedDeviceSession(
                sessionId = "session-1",
                key = KEY,
                basis = SessionIdentityBasis.KEYED,
                session = record,
                state = diverged,
                timeline = SessionTimeline.unopened(),
            )
        }
    }

    @Test
    fun aSessionCannotBeBuiltWhereTheTwoIdsDoNotMatch() {
        val identity = DeviceIdentity.of(displayName = "Air")

        assertFailsWith<IllegalArgumentException> {
            TrackedDeviceSession(
                sessionId = "session-1",
                key = KEY,
                basis = SessionIdentityBasis.KEYED,
                session = DeviceSession.temporary("session-2", identity),
                state = DeviceState.initial("session-2", identity),
                timeline = SessionTimeline.unopened(),
            )
        }
    }

    @Test
    fun mergingIdentityUpdatesBothRecordsOrNeither() {
        val identity = DeviceIdentity.of(displayName = "Air")
        val session = tracked(DeviceState.initial("session-1", identity))

        val updated = session.withMergedIdentity(identity.mergedWith(DeviceIdentity.of(model = "X1")))

        assertEquals(updated.session.identity, updated.state.identity)
        assertEquals("X1", updated.state.identity.model)
        assertEquals("Air", updated.session.identity.displayName)
    }

    @Test
    fun aConnectionMoveThatTheTableRefusesReturnsAFailureAndLeavesTheRecordAlone() {
        val identity = DeviceIdentity.of()
        val movedUp = DeviceState.initial("session-1", identity).attemptConnection(ConnectionState.CONNECTED, 1L)
        val connected = tracked((movedUp as OperationOutcome.Success).value)

        val moved = TrackedDeviceSession.moving(connected, ConnectionState.PAIRED, 2L)

        assertIs<OperationOutcome.Failure>(moved)
        assertEquals(OmniBudsErrorCategory.INVALID_STATE, moved.error.category)
        assertEquals(ConnectionState.CONNECTED, connected.connectionState)
    }

    @Test
    fun observationStatusPrecedenceKeepsTheFourCasesExclusive() {
        val refusal = OmniBudsError(
            category = OmniBudsErrorCategory.PERMISSION_DENIED,
            operationId = "device.connected-inspection",
        )

        // `Unread` is the engine's own value before it has seen any projection at all; a projection
        // that has not started still answers the question "did a round complete", and it does not -
        // so it derives Unconfirmed rather than a fifth reading of the same silence.
        assertEquals(SessionObservationStatus.Unread, DeviceSessionSnapshot.notStarted().observation)
        assertIs<SessionObservationStatus.Unconfirmed>(SessionObservationStatus.of(notStartedProjection()))
        assertIs<SessionObservationStatus.Stopped>(
            SessionObservationStatus.of(projection(stage = ObservationStage.STOPPED, refusal = refusal)),
        )
        assertIs<SessionObservationStatus.Refused>(
            SessionObservationStatus.of(projection(refusal = refusal)),
        )
        val unconfirmed = SessionObservationStatus.of(projection(completed = false))
        assertIs<SessionObservationStatus.Unconfirmed>(unconfirmed)
        assertFalse(unconfirmed.isEmptyMeaningful)
        val partial = SessionObservationStatus.of(projection(unanswered = ObservedProfile.entries.toSet()))
        assertIs<SessionObservationStatus.Confirmed>(partial)
        assertFalse(partial.isEmptyMeaningful, "a union that never answered is not a census of nobody")
        assertTrue(SessionObservationStatus.of(projection()).isEmptyMeaningful)
    }

    @Test
    fun anEmptyListIsOnlyEverAFindingUnderACompletedUnion() {
        val statuses: List<SessionObservationStatus> = listOf(
            SessionObservationStatus.Unread,
            SessionObservationStatus.Unconfirmed,
            SessionObservationStatus.Stopped,
            SessionObservationStatus.Refused(
                OmniBudsError(OmniBudsErrorCategory.BLUETOOTH_DISABLED, "device.connected-inspection"),
            ),
            SessionObservationStatus.Confirmed(unionComplete = false),
            SessionObservationStatus.Confirmed(unionComplete = true),
        )

        assertEquals(
            listOf(false, false, false, false, false, true),
            statuses.map { status -> status.isEmptyMeaningful },
        )
    }

    @Test
    fun nothingTheEnginePublishesPrintsADeviceIdentifier() {
        val sessions = listOf(
            tracked(DeviceState.initial("session-1", DeviceIdentity.of(displayName = "Address-Bearing"))),
        )
        val events: List<DeviceSessionEvent> = listOf(
            DeviceSessionEvent.SessionCreated("session-1", SessionIdentityBasis.KEYED, 1L),
            DeviceSessionEvent.SessionUpdated("session-1", ConnectionState.UNKNOWN, ConnectionState.CONNECTED, 1L),
            DeviceSessionEvent.SessionDisconnected(
                "session-1",
                DisconnectEvidence.ABSENT_FROM_COMPLETE_UNION,
                1L,
            ),
            DeviceSessionEvent.SessionReconnected("session-1", 1L),
            DeviceSessionEvent.SessionEnded("session-1", SessionTermination.ENGINE_CANCELLED, 1L),
            DeviceSessionEvent.SessionIdentityChanged("session-1", 0, 1, 1L),
            DeviceSessionEvent.SessionObservationFailed(
                OmniBudsError(OmniBudsErrorCategory.PERMISSION_DENIED, "device.connected-inspection"),
                1L,
            ),
        )

        (sessions + events).forEach { value -> assertNoAddressText(value.toString()) }
        assertNoAddressText(tracked(DeviceState.initial("session-1", DeviceIdentity.of())).key.toString())
    }

    @Test
    fun aTimelineCarriesNoInventedTimeAndResumingClearsTheDisconnectMark() {
        val opened = SessionTimeline.unopened().openedAt(null)
        assertNull(opened.startedAtEpochMillis)
        assertNull(opened.lastObservedAtEpochMillis)

        val disconnected = opened.markedDisconnected(500L)
        assertEquals(500L, disconnected.disconnectedAtEpochMillis)
        assertEquals(500L, disconnected.markedDisconnected(900L).disconnectedAtEpochMillis, "first mark wins")
        assertNull(disconnected.resumed().disconnectedAtEpochMillis)
        assertEquals(700L, disconnected.closedAt(700L).endedAtEpochMillis)
    }

    @Test
    fun noSessionTypeCanClaimVerificationBeyondWhatThePhaseHas() {
        // The ladder is a Phase 1 type this phase consumes rather than extends: a session carries no
        // VerificationLevel at all, because nothing in a session can be hardware-verified (ADR-P4-012).
        val fields = TrackedDeviceSession::class.java.declaredFields.map { field -> field.name }

        assertTrue("state" in fields && "session" in fields)
        assertTrue(fields.none { name -> name == "verification" || name == "verificationLevel" })
        assertEquals(VerificationLevel.entries.sortedBy { level -> level.ordinal }.first(), VerificationLevel.INFERRED)
    }

    @Test
    fun terminationReasonsDescribeEngineDecisionsAndNeverADeviceAct() {
        val reasons = SessionTermination.entries.map { reason -> reason.name }

        assertEquals(
            listOf(
                "ABSENT_FROM_COMPLETE_UNION",
                "PROVEN_DISCONNECT_PAST_GRACE",
                "AMBIGUOUS_SESSION_EXPIRED",
                "OBSERVATION_STOPPED",
                "ENGINE_CANCELLED",
            ),
            reasons,
        )
        assertTrue(reasons.none { reason -> reason.contains("POWERED_OFF") || reason.contains("UNPAIRED") })
    }

    @Test
    fun theSessionEngineStageIsNotADeviceState() {
        assertEquals(3, SessionEngineStage.entries.size)
        assertEquals(
            setOf("NOT_STARTED", "RUNNING", "STOPPED"),
            SessionEngineStage.entries.map { stage -> stage.name }.toSet(),
        )
        // Prompt section 7's ACTIVE/ENDED vocabulary must not have crept in as a second axis.
        assertTrue(
            ConnectionState.entries.none { state -> state.name == "ACTIVE" || state.name == "ENDED" },
        )
    }

    @Test
    fun beingObservedNeverPromotesASessionIntoASavedRecord() {
        val temporary = DeviceSession.temporary("session-1", DeviceIdentity.of(displayName = "Seen"))

        assertEquals(SessionClassification.TEMPORARY, temporary.classification)
        assertFalse(temporary.isSaved)
        // save() exists on the Phase 1 type for a user's own action; the engine has no path to it,
        // which is what DeviceSessionEngineTest asserts from the other side.
        assertEquals(SessionClassification.SAVED, temporary.save().classification)
    }

    private fun tracked(state: DeviceState): TrackedDeviceSession =
        TrackedDeviceSession(
            sessionId = state.sessionId,
            key = KEY,
            basis = SessionIdentityBasis.KEYED,
            session = DeviceSession.temporary(state.sessionId, state.identity),
            state = state,
            timeline = SessionTimeline.unopened().openedAt(1L),
        )

    private fun projection(
        vararg reports: DeviceObservation,
        stage: ObservationStage = ObservationStage.OBSERVING,
        refusal: OmniBudsError? = null,
        completed: Boolean = true,
        unanswered: Set<ObservedProfile> = emptySet(),
    ): ConnectedDeviceSnapshot = ConnectedDeviceSnapshot(
        stage = stage,
        records = reports.toList(),
        pairedDevices = emptyList(),
        unansweredProfiles = unanswered,
        profileSupportConsulted = true,
        restsOnCompletedRound = completed,
        deviceRoundRefusal = refusal,
        restsOnCompletedPairedRound = false,
        pairedRoundRefusal = null,
        refusedTransitions = emptyList(),
        observedAtEpochMillis = 1L,
    )

    private fun notStartedProjection(): ConnectedDeviceSnapshot =
        ConnectedDeviceSnapshot.notStarted(listOf(ObservedProfile.A2DP))

    private fun assertNoAddressText(rendered: String) {
        val macPattern = Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}")
        assertFalse(macPattern.containsMatchIn(rendered), "identifier text leaked: $rendered")
    }

    companion object {
        private val KEY: DeviceObservationKey =
            requireNotNull(DeviceObservationKey.ofReportedAddress("00:11:22:AA:BB:CC"))
    }
}
