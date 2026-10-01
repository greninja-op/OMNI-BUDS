package com.omnibuds.core.device

import com.omnibuds.core.state.SessionClassification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Session identity, evidence merging and the save/forget choice.
 *
 * Connection-state behaviour moved to `DeviceStateTest` when the duplicated
 * `connectionState` field was removed from this type: a session record and a state
 * record cannot both be authoritative about the same device (Phase 1 prompt section
 * 24, ADR-P1-003).
 */
class DeviceSessionTest {

    private val identity = DeviceIdentity(
        manufacturer = "Example Audio",
        model = "Buds One",
        displayName = "Example Buds",
        modelId = null,
    )

    @Test
    fun afreshSessionIsTemporaryUntilTheUserSaysOtherwise() {
        val session = DeviceSession.temporary(sessionId = "s-1", identity = identity)

        assertEquals(SessionClassification.TEMPORARY, session.classification)
        assertFalse(session.isSaved)
    }

    @Test
    fun savingChangesNothingExceptTheClassification() {
        val session = DeviceSession.temporary(
            sessionId = "s-1",
            identity = identity,
            createdAtEpochMillis = START,
        )

        val saved = session.save()

        assertTrue(saved.isSaved)
        assertEquals(SessionClassification.SAVED, saved.classification)
        assertEquals(session.sessionId, saved.sessionId)
        assertEquals(session.identity, saved.identity)
        assertEquals(session.createdAtEpochMillis, saved.createdAtEpochMillis)
    }

    @Test
    fun forgettingDropsTheSavedChoiceAndClaimsNothingAboutTheHardware() {
        val saved = DeviceSession.temporary(sessionId = "s-1", identity = identity).save()

        val forgotten = saved.forget()

        assertEquals(SessionClassification.TEMPORARY, forgotten.classification)
        assertFalse(forgotten.isSaved)
        // Unpairing, pairing records and stored rows are platform duties this type
        // cannot honestly claim to have performed (SEC-ID-007).
        assertEquals(saved.identity, forgotten.identity)
    }

    @Test
    fun evidenceFillsUnknownsWithoutOverwritingWhatIsAlreadyKnown() {
        val session = DeviceSession.temporary(sessionId = "s-1", identity = identity)
        val later = DeviceIdentity(
            manufacturer = "Wrong Manufacturer",
            model = "Wrong Model",
            displayName = null,
            modelId = "MB-001",
        )

        val updated = session.withEvidence(later)

        assertEquals("Example Audio", updated.identity.manufacturer)
        assertEquals("Buds One", updated.identity.model)
        assertEquals("MB-001", updated.identity.modelId)
    }

    @Test
    fun evidenceWithNoIdentityChangeLeavesTheSessionUntouched() {
        val session = DeviceSession.temporary(sessionId = "s-1", identity = identity)

        val updated = session.withEvidence(identity)

        assertEquals(session, updated)
    }

    @Test
    fun aNewFingerprintReplacesTheOldOneRatherThanMergingTwoObservations() {
        val first = DeviceFingerprint(serviceUuids = setOf("service-alpha"))
        val second = DeviceFingerprint(serviceUuids = setOf("service-beta"))
        val session = DeviceSession.temporary(
            sessionId = "s-1",
            identity = identity,
            fingerprint = first,
        )

        assertEquals(second, session.withEvidence(identity, second).fingerprint)
        assertEquals(first, session.withEvidence(identity).fingerprint)
    }

    @Test
    fun anUnrecordedCreationTimeStaysUnrecorded() {
        val session = DeviceSession.temporary(sessionId = "s-1", identity = identity)

        assertNull(session.createdAtEpochMillis)
        assertFalse(session.createdAtEpochMillis == 0L)
    }

    private companion object {
        const val START = 1_700_000_000_000L
    }
}
