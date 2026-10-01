package com.omnibuds.core.device

import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.RetryClass
import com.omnibuds.core.state.ConnectionState
import com.omnibuds.core.state.SessionClassification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behaviour tests for [DeviceSession]: the state-transition requirements of the Phase
 * 1 prompt (sections 11, 12, 24, 30, 36 "Device state", 49 P1-DOM-005, 53) and the
 * saved-versus-temporary rule of master section 5 and SEC-ID-005.
 *
 * Tier T1: the transition table is exercised as pure logic. Nothing here claims that a
 * device was connected, and no timing value is invented — a session in these tests is
 * a value, built by hand.
 */
class DeviceSessionTest {

    @Test
    fun aLegalTransitionProducesTheNextStateAndCarriesEverythingElseAcross() {
        val session = sessionIn(ConnectionState.PAIRED)

        val outcome = session.transitionedTo(
            ConnectionState.CONNECTED,
            updatedAtEpochMillis = OBSERVED_TIME,
        )

        assertTrue(outcome.isSuccess)
        val updated = assertNotNull(outcome.valueOrNull)
        assertEquals(ConnectionState.CONNECTED, updated.connectionState)
        assertEquals(OBSERVED_TIME, updated.lastStateUpdateEpochMillis)
        assertEquals(session.sessionId, updated.sessionId)
        assertEquals(session.identity, updated.identity)
        assertEquals(session.classification, updated.classification)
    }

    @Test
    fun anIllegalTransitionFailsWithInvalidStateInsteadOfThrowingOrQuietlySucceeding() {
        val session = sessionIn(ConnectionState.DISCONNECTED)

        val outcome = session.transitionedTo(ConnectionState.CONTROL_SESSION)

        assertFalse(outcome.isSuccess)
        assertNull(outcome.valueOrNull)
        val failure = assertNotNull(outcome.errorOrNull)
        assertEquals(OmniBudsErrorCategory.INVALID_STATE, failure.category)
        // A rejected move is never retried: retrying an illegal state move cannot fix
        // the reason it was illegal (docs/phases/phase-0/specs.md section 4).
        assertEquals(RetryClass.NEVER_RETRY, failure.category.retryClass)
        assertEquals(TRANSITION_OPERATION_ID, failure.operationId)
    }

    @Test
    fun errorIsReachableFromEveryConnectionState() {
        for (state in ConnectionState.entries) {
            val outcome = sessionIn(state).transitionedTo(ConnectionState.ERROR)
            assertTrue(outcome.isSuccess, "expected $state to be able to fall into ERROR")
            assertEquals(ConnectionState.ERROR, assertNotNull(outcome.valueOrNull).connectionState)
        }
    }

    @Test
    fun aMoveToTheStateTheSessionIsAlreadyInIsIdempotentForEveryState() {
        for (state in ConnectionState.entries) {
            val session = sessionIn(state)

            val outcome = session.transitionedTo(state, updatedAtEpochMillis = OBSERVED_TIME)
            val message = "expected $state to be self-transitionable"

            assertEquals(session, assertNotNull(outcome.valueOrNull), message)
        }
    }

    @Test
    fun aStaleCallbackCannotPromoteADisconnectedDeviceIntoAControlSession() {
        val disconnected = sessionIn(ConnectionState.DISCONNECTED)

        for (illegal in listOf(ConnectionState.CONTROL_SESSION, ConnectionState.CAPABILITY_DISCOVERY)) {
            val outcome = disconnected.transitionedTo(illegal)
            assertFalse(outcome.isSuccess, "expected DISCONNECTED to refuse a move to $illegal")
        }
    }

    @Test
    fun aTemporarySessionStaysTemporaryAcrossTheWholePathUntilItIsSaved() {
        val discovered = sessionIn(ConnectionState.DISCOVERED)
        val connectedMove = discovered.transitionedTo(
            ConnectionState.CONNECTED,
            updatedAtEpochMillis = OBSERVED_TIME,
        )
        val connected = assertNotNull(connectedMove.valueOrNull)
        val readyMove = connected.transitionedTo(
            ConnectionState.READY,
            updatedAtEpochMillis = OBSERVED_TIME,
        )
        val ready = assertNotNull(readyMove.valueOrNull)

        assertEquals(SessionClassification.TEMPORARY, ready.classification)
        assertTrue(ready.isOperational)
        assertEquals(SessionClassification.SAVED, ready.save().classification)
    }

    @Test
    fun saveOnlyMarksTheSessionAndChangesNoEvidence() {
        val session = sessionIn(ConnectionState.READY)

        val saved = session.save()

        assertEquals(SessionClassification.SAVED, saved.classification)
        assertEquals(session.connectionState, saved.connectionState)
        assertEquals(session.identity, saved.identity)
        assertEquals(session.fingerprint, saved.fingerprint)
        assertEquals(session.createdAtEpochMillis, saved.createdAtEpochMillis)
    }

    @Test
    fun forgetDropsTheSavedClassificationWithoutClaimingAnythingAboutTheHardware() {
        val saved = sessionIn(ConnectionState.CONNECTED).save()

        val forgotten = saved.forget()

        assertEquals(SessionClassification.TEMPORARY, forgotten.classification)
        assertEquals(saved.identity, forgotten.identity)
        assertEquals(saved.fingerprint, forgotten.fingerprint)
        assertEquals(saved.connectionState, forgotten.connectionState)
        // Forgetting is a classification change on a value. The pairing record, the
        // saved device entry and the device itself are out of this type's reach, and
        // it would be a fabricated hardware effect to imply otherwise (SEC-ID-007).
        assertNotEquals(SessionClassification.SAVED, forgotten.classification)
    }

    @Test
    fun aTransitionWithoutAnObservedTimeLeavesTheUpdateUnknownRatherThanStale() {
        val session = sessionIn(ConnectionState.PAIRED, lastUpdate = OBSERVED_TIME)

        val updated = assertNotNull(session.transitionedTo(ConnectionState.CONNECTED).valueOrNull)

        assertNull(updated.lastStateUpdateEpochMillis)
    }

    @Test
    fun aStateUpdateFillsIdentityGapsWithoutRestatingWhatIsAlreadyKnown() {
        val session = sessionIn(ConnectionState.IDENTIFYING)

        val updated = session.withStateUpdate(
            updatedIdentity = DeviceIdentity.of(manufacturer = "Conflict Co", model = "EB-1"),
            updatedFingerprint = DeviceFingerprint(deviceClass = REPORTED_DEVICE_CLASS),
            atEpochMillis = OBSERVED_TIME,
        )

        assertEquals("Example Audio", updated.identity.manufacturer)
        assertEquals("EB-1", updated.identity.model)
        assertEquals(REPORTED_DEVICE_CLASS, updated.fingerprint?.deviceClass)
        assertEquals(ConnectionState.IDENTIFYING, updated.connectionState)
        assertEquals(OBSERVED_TIME, updated.lastStateUpdateEpochMillis)
    }

    @Test
    fun aStateUpdateWithoutNewEvidenceKeepsTheSessionAsItWasObserved() {
        val session = sessionIn(ConnectionState.CAPABILITY_DISCOVERY)

        val updated = session.withStateUpdate(updatedIdentity = DeviceIdentity.unknown())

        assertEquals(session.identity, updated.identity)
        assertEquals(session.fingerprint, updated.fingerprint)
        assertNull(updated.lastStateUpdateEpochMillis)
    }

    @Test
    fun onlyReadyAndControlSessionCountAsOperational() {
        val operational = setOf(ConnectionState.READY, ConnectionState.CONTROL_SESSION)

        for (state in ConnectionState.entries) {
            assertEquals(
                state in operational,
                sessionIn(state).isOperational,
                "expected operational only for $operational, got it for $state",
            )
        }
    }

    private fun sessionIn(
        state: ConnectionState,
        classification: SessionClassification = SessionClassification.TEMPORARY,
        lastUpdate: Long? = OBSERVED_TIME,
    ): DeviceSession = DeviceSession(
        sessionId = "session-under-test",
        identity = DeviceIdentity.of(manufacturer = "Example Audio"),
        fingerprint = DeviceFingerprint.empty(),
        connectionState = state,
        classification = classification,
        createdAtEpochMillis = OBSERVED_TIME,
        lastStateUpdateEpochMillis = lastUpdate,
    )

    private companion object {
        const val OBSERVED_TIME = 1_700_000_000_000L
        const val REPORTED_DEVICE_CLASS = 7936
        const val TRANSITION_OPERATION_ID = "device-session.transition"
    }
}
