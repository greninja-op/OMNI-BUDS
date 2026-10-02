package com.omnibuds.core.session

import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.platform.ConnectedDeviceSnapshot
import com.omnibuds.core.platform.DeviceAvailability
import com.omnibuds.core.platform.DeviceBondState
import com.omnibuds.core.platform.DeviceConnectionState
import com.omnibuds.core.platform.DeviceObservation
import com.omnibuds.core.platform.DeviceObservationKey
import com.omnibuds.core.platform.ObservationArrival
import com.omnibuds.core.platform.ObservationStage
import com.omnibuds.core.platform.ObservedProfile
import com.omnibuds.core.platform.TimeProvider
import com.omnibuds.core.state.ConnectionState
import com.omnibuds.core.state.SessionClassification
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The session engine's behaviour, driven entirely through published projections.
 *
 * Every case here is tier T1: the input is a `ConnectedDeviceSnapshot` a test built, so a pass
 * proves the mapping table, the grace rule and the dedup logic are what the decisions say they
 * are, and proves nothing about a handset (ADR-P4-012). Two places a claim could creep in are
 * handled rather than assumed away - the round that omits a device is a *complete union* only when
 * the fixture says it is, and the clock is a fake that can be switched off entirely.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceSessionEngineTest {

    private val clock = TickingClock()
    private val engine = DeviceSessionEngine(time = clock)

    @Test
    fun aConnectedDeviceOpensExactlyOneSession() = runTest {
        engine.apply(projection(record(KEY_LEFT)))

        val published = engine.snapshot.value
        assertEquals(1, published.sessions.size)
        assertEquals(ConnectionState.CONNECTED, published.singleConnection())
        assertEquals(SessionIdentityBasis.KEYED, published.sessions.single().basis)
        assertEquals(SessionEngineStage.RUNNING, published.stage)
        assertIs<SessionObservationStatus.Confirmed>(published.observation)
        assertTrue(published.isEmptyMeaningful, "a completed union is a census even when it found someone")
    }

    @Test
    fun everySessionThisEngineMakesIsTemporary() = runTest {
        engine.apply(projection(record(KEY_LEFT), record(KEY_RIGHT, name = "Ghost Buds")))

        assertEquals(2, engine.snapshot.value.sessions.size)
        assertTrue(
            engine.snapshot.value.sessions.all { session ->
                session.session.classification == SessionClassification.TEMPORARY && session.isSaved.not()
            },
            "a device that was merely connected promoted itself into a saved record",
        )
    }

    @Test
    fun oneDeviceReportedTwiceInOneRoundStillOpensOneSession() = runTest {
        val audio = record(KEY_LEFT, profiles = setOf(ObservedProfile.A2DP))
        val call = record(KEY_LEFT, profiles = setOf(ObservedProfile.HEADSET))

        engine.apply(projection(audio, call))

        assertEquals(1, engine.snapshot.value.sessions.size, "two profiles welded into two devices")
        assertEquals("session-1", engine.snapshot.value.sessions.single().sessionId)
    }

    @Test
    fun twoDevicesCarryingTheSameNameStayTwoSessions() = runTest {
        engine.apply(projection(record(KEY_LEFT, name = "Air Buds"), record(KEY_RIGHT, name = "Air Buds")))

        val sessions = engine.snapshot.value.sessions
        assertEquals(2, sessions.size)
        assertEquals(2, sessions.map { session -> session.sessionId }.distinct().size)
        assertEquals(listOf(KEY_LEFT, KEY_RIGHT), sessions.map { session -> session.key })
    }

    @Test
    fun aDeviceAbsentFromACompleteUnionIsReportedDisconnectedNotDeleted() = runTest {
        engine.apply(projection(record(KEY_LEFT)))
        engine.apply(projection())

        val session = engine.snapshot.value.sessions.single()
        assertEquals(ConnectionState.DISCONNECTED, session.connectionState)
        assertTrue(session.isAwaitingGraceExpiry)
        assertNotNull(session.timeline.disconnectedAtEpochMillis)
    }

    @Test
    fun absenceFromAnUnansweredRoundWithdrawsNothing() = runTest {
        engine.apply(projection(record(KEY_LEFT)))
        engine.apply(projection(partialUnion = true))

        assertEquals(ConnectionState.CONNECTED, engine.snapshot.value.singleConnection())
        assertNull(engine.snapshot.value.sessions.single().timeline.disconnectedAtEpochMillis)
    }

    @Test
    fun aRefusedRoundKeepsEverySessionAndCarriesItsReason() = runTest {
        engine.apply(projection(record(KEY_LEFT)))
        engine.apply(projection(refusal = refusal(OmniBudsErrorCategory.PERMISSION_DENIED)))

        val published = engine.snapshot.value
        assertEquals(1, published.sessions.size)
        assertEquals(ConnectionState.CONNECTED, published.singleConnection())
        val status = published.observation
        assertIs<SessionObservationStatus.Refused>(status)
        assertEquals(OmniBudsErrorCategory.PERMISSION_DENIED, status.error.category)
        assertFalse(status.isEmptyMeaningful, "a refusal is never a census of nobody")
    }

    @Test
    fun aRefusalIsNotTheSameSentenceAsAnEmptyCensus() = runTest {
        engine.apply(projection(record(KEY_LEFT), record(KEY_RIGHT)))
        engine.apply(projection(refusal = refusal(OmniBudsErrorCategory.BLUETOOTH_DISABLED)))
        // Both sessions survive the refusal; the census that follows is the first real finding.
        assertEquals(2, engine.snapshot.value.sessions.size)

        engine.apply(projection())

        assertEquals(2, engine.snapshot.value.sessions.size)
        assertTrue(engine.snapshot.value.sessions.all { session -> session.isAwaitingGraceExpiry })
        assertEquals(0, engine.snapshot.value.activeSessions.size)
    }

    @Test
    fun aDisconnectedDeviceIsHeldForOneRoundAndThenEnded() = runTest {
        val observed = engine.collectingEvents(this)

        engine.apply(projection(record(KEY_LEFT)))
        engine.apply(projection())
        assertEquals(1, engine.snapshot.value.sessions.size)

        engine.apply(projection())
        assertTrue(engine.snapshot.value.sessions.isEmpty())
        assertEquals(SessionTermination.PROVEN_DISCONNECT_PAST_GRACE, observed.last().endReason())
        observed.cancel()
    }

    @Test
    fun aRepeatedDisconnectReportUsesTheSameGraceWindow() = runTest {
        engine.apply(projection(record(KEY_LEFT)))
        engine.apply(projection(record(KEY_LEFT, link = DeviceConnectionState.DISCONNECTED)))
        assertEquals(ConnectionState.DISCONNECTED, engine.snapshot.value.singleConnection())

        engine.apply(projection(record(KEY_LEFT, link = DeviceConnectionState.DISCONNECTED)))

        assertTrue(engine.snapshot.value.sessions.isEmpty())
    }

    @Test
    fun aDeviceThatComesBackInsideTheGraceKeepsItsSessionId() = runTest {
        val observed = engine.collectingEvents(this)
        engine.apply(projection(record(KEY_LEFT)))
        val firstId = engine.snapshot.value.sessions.single().sessionId

        engine.apply(projection())
        engine.apply(projection(record(KEY_LEFT)))

        assertEquals(firstId, engine.snapshot.value.sessions.single().sessionId)
        assertEquals(ConnectionState.CONNECTED, engine.snapshot.value.singleConnection())
        assertNull(engine.snapshot.value.sessions.single().timeline.disconnectedAtEpochMillis)
        assertIs<DeviceSessionEvent.SessionReconnected>(observed.last())
        observed.cancel()
    }

    @Test
    fun aDeviceThatComesBackAfterTheGraceIsANewSessionWithANewId() = runTest {
        engine.apply(projection(record(KEY_LEFT)))
        engine.apply(projection())
        engine.apply(projection())
        assertTrue(engine.snapshot.value.sessions.isEmpty())

        engine.apply(projection(record(KEY_LEFT)))

        assertEquals("session-2", engine.snapshot.value.sessions.single().sessionId)
        assertEquals(ConnectionState.CONNECTED, engine.snapshot.value.singleConnection())
    }

    @Test
    fun anUnkeyedDeviceBecomesOneAmbiguousSessionAndIsNeverCarriedForward() = runTest {
        val observed = engine.collectingEvents(this)
        val unkeyed = record(DeviceObservationKey.NotReported, name = "Unnamed")

        engine.apply(projection(unkeyed))
        val session = engine.snapshot.value.sessions.single()
        assertEquals(SessionIdentityBasis.AMBIGUOUS, session.basis)

        engine.apply(projection(unkeyed))

        assertEquals(1, engine.snapshot.value.sessions.size)
        assertNotEquals(session.sessionId, engine.snapshot.value.sessions.single().sessionId)
        assertEquals(SessionTermination.AMBIGUOUS_SESSION_EXPIRED, observed.secondLast().endReason())
        observed.cancel()
    }

    @Test
    fun twoUnkeyedDevicesInOneRoundStayTwoSessionsBecauseNothingCanTellThemApart() = runTest {
        engine.apply(
            projection(
                record(DeviceObservationKey.NotReported),
                record(DeviceObservationKey.NotReported, name = "Other"),
            ),
        )

        assertEquals(2, engine.snapshot.value.sessions.size)
        assertEquals(2, engine.snapshot.value.ambiguousSessions.size)
    }

    @Test
    fun aNameArrivingLateFillsIdentityAndADifferentNameLaterNeverRestatesIt() = runTest {
        engine.apply(projection(record(KEY_LEFT)))
        assertNull(engine.snapshot.value.sessions.single().displayName)

        engine.apply(projection(record(KEY_LEFT, name = "OmniBuds Air")))
        assertEquals("OmniBuds Air", engine.snapshot.value.sessions.single().displayName)

        engine.apply(projection(record(KEY_LEFT, name = "Renamed Buds")))

        // Identity merge is fill-only (DeviceIdentity.mergedWith), so a renamed alias stays a
        // finding for a human rather than a value a data class silently overwrites.
        assertEquals("OmniBuds Air", engine.snapshot.value.sessions.single().displayName)
    }

    @Test
    fun aSessionCarriesNoEvidenceThisPhaseDoesNotHave() = runTest {
        engine.apply(projection(record(KEY_LEFT, name = "  ", bond = DeviceBondState.BONDED)))

        val session = engine.snapshot.value.sessions.single()
        assertNull(session.session.identity.manufacturer)
        assertNull(session.session.identity.model)
        assertNull(session.session.identity.modelId)
        assertNull(session.session.identity.displayName, "blank reported text is not a name")
        assertTrue(session.session.identity.isEntirelyUnknown)
        assertNull(session.session.fingerprint, "fingerprinting is Phase 5")
        assertEquals(0, session.state.capabilities.features.size, "no capability was ever read")
        assertTrue(session.state.battery.isEntirelyUnknown, "no battery was ever read")
    }

    @Test
    fun aStoppedObservationEndsSessionsWithoutClaimingADisconnect() = runTest {
        val observed = engine.collectingEvents(this)
        engine.apply(projection(record(KEY_LEFT)))

        engine.apply(projection(stage = ObservationStage.STOPPED))

        val published = engine.snapshot.value
        assertTrue(published.sessions.isEmpty())
        assertIs<SessionObservationStatus.Stopped>(published.observation)
        assertEquals(SessionEngineStage.STOPPED, published.stage)
        assertEquals(SessionTermination.OBSERVATION_STOPPED, observed.last().endReason())
        observed.cancel()
    }

    @Test
    fun anEngineWithoutAClockPublishesNullTimesAndNeverZero() = runTest {
        val timeless = DeviceSessionEngine()

        timeless.apply(projection(record(KEY_LEFT, at = null)))

        val session = timeless.snapshot.value.sessions.single()
        assertNull(session.timeline.startedAtEpochMillis)
        assertNull(session.timeline.lastObservedAtEpochMillis)
        assertNull(timeless.snapshot.value.publishedAtEpochMillis)
    }

    @Test
    fun activeSessionsIsAViewOverTheOneValueNotASecondOwner() = runTest {
        engine.apply(projection(record(KEY_LEFT), record(KEY_RIGHT)))
        engine.apply(projection(record(KEY_LEFT)))

        val published = engine.snapshot.value
        assertEquals(2, published.sessions.size)
        assertEquals(listOf("session-1"), published.activeSessions.map { session -> session.sessionId })
        assertEquals(
            listOf("session-2"),
            published.disconnectPendingSessions.map { session -> session.sessionId },
        )
        assertEquals(published.sessions.count { session -> session.isActive }, published.activeSessions.size)
    }

    @Test
    fun aBondedDeviceWithNoLinkReadingIsPairedAndNotConnected() = runTest {
        engine.apply(
            projection(record(KEY_LEFT, link = DeviceConnectionState.UNKNOWN, bond = DeviceBondState.BONDED)),
        )

        assertEquals(ConnectionState.PAIRED, engine.snapshot.value.singleConnection())
    }

    @Test
    fun aLinkOnTheWayOutIsNeverReadAsADisconnect() = runTest {
        engine.apply(projection(record(KEY_LEFT, link = DeviceConnectionState.DISCONNECTING)))

        assertEquals(ConnectionState.TEMPORARILY_UNAVAILABLE, engine.snapshot.value.singleConnection())
    }

    @Test
    fun aLinkStillComingUpIsDiscoveredRatherThanConnected() = runTest {
        engine.apply(projection(record(KEY_LEFT, link = DeviceConnectionState.CONNECTING)))

        assertEquals(ConnectionState.DISCOVERED, engine.snapshot.value.singleConnection())
    }

    @Test
    fun aPhoneWhoseRadioWentQuietMakesTrackedDevicesUnavailableNotGone() = runTest {
        engine.apply(projection(record(KEY_LEFT)))
        engine.apply(
            projection(
                record(
                    KEY_LEFT,
                    link = DeviceConnectionState.UNKNOWN,
                    availability = DeviceAvailability.UNAVAILABLE,
                ),
            ),
        )

        assertEquals(ConnectionState.TEMPORARILY_UNAVAILABLE, engine.snapshot.value.singleConnection())
    }

    @Test
    fun aFirstSightingOfAnUnconnectedDeviceOpensNoSession() = runTest {
        engine.apply(
            projection(
                record(KEY_LEFT, link = DeviceConnectionState.DISCONNECTED),
                record(
                    KEY_RIGHT,
                    link = DeviceConnectionState.UNKNOWN,
                    availability = DeviceAvailability.UNKNOWN,
                ),
            ),
        )

        assertTrue(engine.snapshot.value.sessions.isEmpty())
        assertTrue(engine.snapshot.value.isEmptyMeaningful, "the union answered, and answered with nobody")
    }

    @Test
    fun aMoveTheTransitionTableRefusesIsRecordedAndTheHeldStateKept() = runTest {
        engine.apply(projection(record(KEY_LEFT)))
        // Connected, then a round whose only reading is the bond axis. The table has no legal way
        // back to PAIRED, and inventing one would be this engine claiming a move it was refused.
        engine.apply(
            projection(record(KEY_LEFT, link = DeviceConnectionState.UNKNOWN, bond = DeviceBondState.BONDED)),
        )

        val published = engine.snapshot.value
        assertEquals(ConnectionState.CONNECTED, published.singleConnection())
        val refusal = published.refusedMoves.single()
        assertEquals(ConnectionState.PAIRED, refusal.requested)
        assertEquals(ConnectionState.CONNECTED, refusal.held)
        assertEquals("session-1", refusal.sessionId)
    }

    @Test
    fun theRefusalWindowIsBoundedAndKeepsTheNewestMoves() = runTest {
        repeat(12) {
            engine.apply(projection(record(KEY_LEFT)))
            engine.apply(
                projection(
                    record(KEY_LEFT, link = DeviceConnectionState.UNKNOWN, bond = DeviceBondState.BONDED),
                ),
            )
        }

        assertEquals(8, engine.snapshot.value.refusedMoves.size)
    }

    @Test
    fun everyRoundAdvancesTheRevisionAndAnIdenticalRoundEmitsNoEvents() = runTest {
        val observed = engine.collectingEvents(this)
        val one = projection(record(KEY_LEFT))

        engine.apply(one)
        val afterFirst = engine.snapshot.value.revision
        engine.apply(one)
        engine.apply(one)

        assertEquals(afterFirst + 2, engine.snapshot.value.revision)
        assertEquals(1, observed.count { event -> event is DeviceSessionEvent.SessionCreated })
        assertTrue(observed.none { event -> event is DeviceSessionEvent.SessionUpdated })
        observed.cancel()
    }

    @Test
    fun sessionsArePublishedInCreationOrderWhateverTheRoundSays() = runTest {
        engine.apply(projection(record(KEY_RIGHT)))

        engine.apply(projection(record(KEY_LEFT), record(KEY_RIGHT), record(KEY_MID)))

        assertEquals(
            listOf("session-1", "session-2", "session-3"),
            engine.snapshot.value.sessions.map { session -> session.sessionId },
        )
        assertEquals(KEY_RIGHT, engine.snapshot.value.sessions.first().key)
    }

    @Test
    fun oneRoundPublishesItsEventsInTheOrderTheStateWasBuilt() = runTest {
        val observed = engine.collectingEvents(this)

        engine.apply(projection(record(KEY_LEFT), record(KEY_RIGHT)))

        assertEquals(2, observed.size())
        assertEquals(
            listOf("session-1", "session-2"),
            observed.all().filterIsInstance<DeviceSessionEvent.SessionCreated>().map { event -> event.sessionId },
        )
        observed.cancel()
    }

    @Test
    fun consumeAppliesEveryProjectionAndEndsSessionsWhenCancelled() = runTest {
        val observed = engine.collectingEvents(this)
        val endless = MutableSharedFlow<ConnectedDeviceSnapshot>(extraBufferCapacity = 4)

        val job = launch { engine.consume(endless) }
        runCurrent()
        endless.tryEmit(projection(record(KEY_LEFT)))
        runCurrent()
        assertEquals(1, engine.snapshot.value.sessions.size)

        job.cancel()
        runCurrent()

        assertTrue(engine.snapshot.value.sessions.isEmpty())
        assertEquals(SessionEngineStage.STOPPED, engine.snapshot.value.stage)
        assertIs<SessionObservationStatus.Stopped>(engine.snapshot.value.observation)
        assertEquals(SessionTermination.ENGINE_CANCELLED, observed.last().endReason())
        observed.cancel()
    }

    @Test
    fun consumeEndsWithTheObservationWhenTheFlowCompletesNormally() = runTest {
        val observed = engine.collectingEvents(this)

        engine.consume(flowOf(projection(record(KEY_LEFT))))

        assertEquals(SessionTermination.OBSERVATION_STOPPED, observed.last().endReason())
        assertTrue(engine.snapshot.value.sessions.isEmpty())
        observed.cancel()
    }

    @Test
    fun anEngineReusedAfterAStopStartsFreshSessionsRatherThanResumingGhostOnes() = runTest {
        engine.consume(flowOf(projection(record(KEY_LEFT))))
        assertTrue(engine.snapshot.value.sessions.isEmpty())

        engine.apply(projection(record(KEY_LEFT)))

        assertEquals(1, engine.snapshot.value.sessions.size)
        assertEquals(ConnectionState.CONNECTED, engine.snapshot.value.singleConnection())
        assertEquals(SessionEngineStage.RUNNING, engine.snapshot.value.stage)
    }

    @Test
    fun aSessionIdCarriesNoPartOfTheDeviceThatProducedIt() = runTest {
        engine.apply(projection(record(KEY_LEFT, name = "OmniBuds Air")))

        val session = engine.snapshot.value.sessions.single()
        assertEquals("session-1", session.sessionId)
        assertFalse(session.sessionId.contains("00:11"), "an address leaked into a session id")
        assertFalse(session.sessionId.contains("AA:BB"))
        assertFalse(session.sessionId.contains("Air"))
    }

    @Test
    fun anExplicitStopClearsTheStoreAndSaysItStoppedRatherThanFoundNothing() = runTest {
        engine.apply(projection(record(KEY_LEFT)))

        engine.stop()

        val published = engine.snapshot.value
        assertTrue(published.sessions.isEmpty())
        assertIs<SessionObservationStatus.Stopped>(published.observation)
        assertFalse(published.isEmptyMeaningful)
    }

    private fun DeviceSessionSnapshot.singleConnection(): ConnectionState = sessions.single().connectionState

    private fun DeviceSessionEvent.endReason(): SessionTermination =
        (this as DeviceSessionEvent.SessionEnded).reason

    private fun record(
        key: DeviceObservationKey,
        link: DeviceConnectionState = DeviceConnectionState.CONNECTED,
        bond: DeviceBondState = DeviceBondState.NONE,
        availability: DeviceAvailability = DeviceAvailability.AVAILABLE,
        name: String? = null,
        profiles: Set<ObservedProfile> = setOf(ObservedProfile.A2DP),
        at: Long? = 1_000L,
    ): DeviceObservation = DeviceObservation.reported(
        key = key,
        link = link,
        bond = bond,
        availability = availability,
        displayName = name,
        observedProfiles = profiles,
        arrival = ObservationArrival.SNAPSHOT,
        observedAtEpochMillis = at,
    )

    private fun refusal(category: OmniBudsErrorCategory) = OmniBudsError(
        category = category,
        operationId = "device.connected-inspection",
        detail = "the platform declined the read",
    )

    private fun projection(
        vararg reports: DeviceObservation,
        stage: ObservationStage = ObservationStage.OBSERVING,
        refusal: OmniBudsError? = null,
        partialUnion: Boolean = false,
    ): ConnectedDeviceSnapshot = ConnectedDeviceSnapshot(
        stage = stage,
        records = reports.toList(),
        pairedDevices = emptyList(),
        unansweredProfiles = if (partialUnion) ObservedProfile.entries.toSet() else emptySet(),
        profileSupportConsulted = true,
        restsOnCompletedRound = refusal == null,
        deviceRoundRefusal = refusal,
        restsOnCompletedPairedRound = false,
        pairedRoundRefusal = null,
        refusedTransitions = emptyList(),
        observedAtEpochMillis = 1_000L,
    )

    /**
     * Subscribes before anything is applied, because the event flow is declared with `replay = 0`
     * (ADR-P4-008): a collector that subscribed after the round would prove only that a missed
     * event was never there, which is the one claim this type cannot make.
     */
    private fun DeviceSessionEngine.collectingEvents(scope: TestScope): EventLog = EventLog(scope, this)

    private class TickingClock : TimeProvider {
        private var now: Long = 1_000L
        override fun nowEpochMillis(): Long = now.also { now += 1 }
    }

    /** A live event collector plus the list it fills, so a test can read edges and cancel itself. */
    private inner class EventLog(private val scope: TestScope, engine: DeviceSessionEngine) {
        private val events = mutableListOf<DeviceSessionEvent>()
        private val job: Job

        init {
            job = scope.launch { engine.events.collect { event -> events += event } }
            scope.runCurrent()
        }

        fun last(): DeviceSessionEvent {
            flush()
            return events.last()
        }

        fun secondLast(): DeviceSessionEvent {
            flush()
            return events[events.size - 2]
        }

        fun size(): Int {
            flush()
            return events.size
        }

        fun all(): List<DeviceSessionEvent> {
            flush()
            return events.toList()
        }

        fun count(matching: (DeviceSessionEvent) -> Boolean): Int {
            flush()
            return events.count(matching)
        }

        fun none(matching: (DeviceSessionEvent) -> Boolean): Boolean {
            flush()
            return events.none(matching)
        }

        fun cancel() {
            flush()
            job.cancel()
        }

        /**
         * SharedFlow hands over asynchronously, so an event the engine has already published may not
         * have reached this collector yet. runCurrent() drains the test scheduler first, which keeps
         * the assertion about ordering rather than about a race in the harness.
         */
        private fun flush() {
            scope.runCurrent()
        }
    }

    companion object {
        private val KEY_LEFT: DeviceObservationKey =
            requireNotNull(DeviceObservationKey.ofReportedAddress("00:11:22:AA:BB:CC"))
        private val KEY_RIGHT: DeviceObservationKey =
            requireNotNull(DeviceObservationKey.ofReportedAddress("00:11:22:AA:BB:DD"))
        private val KEY_MID: DeviceObservationKey =
            requireNotNull(DeviceObservationKey.ofReportedAddress("00:11:22:AA:BB:EE"))
    }
}
