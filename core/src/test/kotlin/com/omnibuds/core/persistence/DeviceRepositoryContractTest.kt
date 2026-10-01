package com.omnibuds.core.persistence

import com.omnibuds.core.capability.DeviceCapabilities
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.device.DeviceFingerprint
import com.omnibuds.core.device.DeviceIdentity
import com.omnibuds.core.state.VerificationLevel
import com.omnibuds.core.testing.FakeCapabilityRepository
import com.omnibuds.core.testing.FakeDeviceRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract tests for the two seams in this package, run against the test doubles in
 * `com.omnibuds.core.testing`.
 *
 * These pin the product rules the contracts exist to protect (Phase 1 prompt sections 25 and
 * 27, ADR-P0-004, master section 5): saving is an explicit user act, a detected device is
 * never saved as a side effect, forgetting is local and complete, and a capability cache is
 * discardable in a way the user's list is not. They are the half of "saved and temporarily
 * connected are different things" that can be checked before any storage exists.
 *
 * Fixtures are fictional: invented service tokens and descriptive text, no real vendor name,
 * no real UUID, no address-shaped value anywhere.
 */
class DeviceRepositoryContractTest {

    private val savedIdentity: DeviceIdentity = DeviceIdentity.of(
        manufacturer = "fictional audio works",
        model = "FAW-BUDS-ONE",
        displayName = "fixture buds",
    )

    private fun discoveredFingerprint(): DeviceFingerprint = DeviceFingerprint(
        serviceUuids = setOf("fixture-service-token-alpha", "fixture-service-token-beta"),
        protocolCandidates = listOf("fixture-candidate-family"),
    )

    // Rule 2 of DeviceRepository: a device that was merely detected is never written here.
    @Test
    fun markingAnUnsavedDeviceSeenRefusesInsteadOfQuietlySavingIt() = runTest {
        val repository = FakeDeviceRepository()
        val observedKey = discoveredFingerprint().identityKey()

        val outcome = repository.markSeen(observedKey, FIRST_SEEN_MILLIS)

        assertFalse(outcome.isSuccess)
        val refusal = requireNotNull(outcome.errorOrNull)
        assertEquals(OmniBudsErrorCategory.INVALID_STATE, refusal.category)
        // Nothing entered the user's list, in any form.
        assertTrue(repository.storedKeys().isEmpty())
        assertTrue(requireNotNull(repository.savedDevices().valueOrNull).isEmpty())
    }

    @Test
    fun markingASavedDeviceSeenRestatesOnlyWhenItWasLastSeen() = runTest {
        val repository = FakeDeviceRepository()
        val key = discoveredFingerprint().identityKey()
        repository.save(
            SavedDeviceRecord.of(
                identityKey = key,
                identity = savedIdentity,
                savedAtEpochMillis = SAVED_AT_MILLIS,
            ),
        )

        val outcome = repository.markSeen(key, FIRST_SEEN_MILLIS)

        assertTrue(outcome.isSuccess)
        val updated = requireNotNull(repository.find(key).valueOrNull)
        assertEquals(FIRST_SEEN_MILLIS, updated.lastSeenEpochMillis)
        // A sighting is not an edit: the save stamp and the established identity survive
        // untouched, which is what keeps lastSeen out of the business of renewing a save.
        assertEquals(SAVED_AT_MILLIS, updated.savedAtEpochMillis)
        assertEquals(savedIdentity, updated.identity)
        assertEquals(1, repository.markSeenCalls.size)
    }

    // Rule 1 of DeviceRepository: the list changes only when a caller saves or forgets.
    @Test
    fun nothingButAnExplicitSaveEverEntersTheUsersList() = runTest {
        val repository = FakeDeviceRepository()
        val key = discoveredFingerprint().identityKey()

        repository.find(key)
        repository.find("another-fixture-key")
        repository.markSeen(key, FIRST_SEEN_MILLIS)
        assertTrue(requireNotNull(repository.savedDevices().valueOrNull).isEmpty())
        assertEquals(0, repository.savedCalls.size)

        repository.save(SavedDeviceRecord.of(identityKey = key, identity = savedIdentity))

        val listed = requireNotNull(repository.savedDevices().valueOrNull)
        assertEquals(1, listed.size)
        assertEquals(key, listed.first().identityKey)
        // Every one of those calls is recorded, so a caller cannot hide an attempted write.
        assertEquals(1, repository.savedCalls.size)
        assertEquals(2, repository.findCalls.size)
        assertEquals(1, repository.markSeenCalls.size)
    }

    @Test
    fun forgettingRemovesOnlyOmniBudsOwnDataAndTheDeviceCanBeSeenAgain() = runTest {
        val repository = FakeDeviceRepository()
        val fingerprint = discoveredFingerprint()
        repository.save(SavedDeviceRecord.of(fingerprint.identityKey(), savedIdentity))

        val forgotten = repository.forget(fingerprint.identityKey())

        assertTrue(forgotten.isSuccess)
        assertNull(repository.find(fingerprint.identityKey()).valueOrNull)
        assertTrue(repository.storedKeys().isEmpty())
        // Nothing was claimed about the device itself: the very same evidence still derives
        // the very same key, so a later discovery pass recognises it exactly as readily as
        // before. Forgetting deleted a local row; it unlinked, unpaired and notified nothing
        // (SEC-ID-007).
        assertEquals(fingerprint.identityKey(), discoveredFingerprint().identityKey())

        // And forgetting twice is not a failure: the user's intent is already satisfied.
        assertTrue(repository.forget(fingerprint.identityKey()).isSuccess)
        assertEquals(2, repository.forgottenKeys.size)
    }

    @Test
    fun aBlankIdentityKeyIsRefusedRatherThanStoredByEitherSeam() = runTest {
        val devices = FakeDeviceRepository()
        val capabilities = FakeCapabilityRepository()

        val refusedSave = devices.save(SavedDeviceRecord.of(BLANK_KEY, savedIdentity))
        assertFalse(refusedSave.isSuccess)
        val saveRefusal = requireNotNull(refusedSave.errorOrNull)
        assertEquals(OmniBudsErrorCategory.INVALID_STATE, saveRefusal.category)
        assertTrue(devices.storedKeys().isEmpty())
        assertEquals(1, devices.savedCalls.size)

        val refusedStore = capabilities.store(
            DiscoveredCapabilityRecord(
                identityKey = BLANK_KEY,
                capabilities = DeviceCapabilities.empty(),
                discoveredAtEpochMillis = null,
                verification = VerificationLevel.INFERRED,
            ),
        )
        assertFalse(refusedStore.isSuccess)
        val storeRefusal = requireNotNull(refusedStore.errorOrNull)
        assertEquals(OmniBudsErrorCategory.INVALID_STATE, storeRefusal.category)
        assertTrue(capabilities.cachedKeys().isEmpty())
    }

    // The asymmetry that justifies two seams: the cache may be dropped, the user's list may not.
    @Test
    fun clearingTheCapabilityCacheLeavesTheUsersSavedDeviceAlone() = runTest {
        val devices = FakeDeviceRepository()
        val capabilities = FakeCapabilityRepository()
        val key = discoveredFingerprint().identityKey()
        devices.save(SavedDeviceRecord.of(key, savedIdentity))

        capabilities.clear(key)

        assertTrue(capabilities.load(key).isSuccess)
        assertNull(capabilities.load(key).valueOrNull)
        val survivor = requireNotNull(devices.find(key).valueOrNull)
        assertEquals(key, survivor.identityKey)
    }

    /** A record saved with gaps is still honest: nothing is defaulted into a claim. */
    @Test
    fun aSavedRecordHoldsIdentityAndVersionEvidenceAndNoDiscoveryArchive() {
        val fingerprint = discoveredFingerprint()
        val record = SavedDeviceRecord.of(
            identityKey = fingerprint.identityKey(),
            identity = savedIdentity,
        )

        // The key is the fingerprint-derived one and it is address-free: no MAC can survive
        // being folded through it, because the canonical address separator is stripped by
        // design (SEC-ID-001, SEC-ID-003, SEC-ID-004).
        assertEquals(fingerprint.identityKey(), record.identityKey)
        assertFalse(record.identityKey.contains(ADDRESS_SEPARATOR))
        // Nothing was invented to fill the gaps a save may legitimately have: no firmware
        // placeholder, no sighting stamp, no guessed model id.
        assertNull(record.firmware)
        assertNull(record.lastSeenEpochMillis)
        assertNull(record.identity.modelId)
        assertEquals(3, record.identity.knownFieldCount)

        val savedBlindly = SavedDeviceRecord.of(fingerprint.identityKey(), DeviceIdentity.unknown())
        assertTrue(savedBlindly.identity.isEntirelyUnknown)
    }

    private companion object {
        /** Whitespace is blank, not a key: this is the value a repository must refuse. */
        const val BLANK_KEY = "   "

        const val ADDRESS_SEPARATOR = ":"
        const val SAVED_AT_MILLIS = 1_700_000_000_000L
        const val FIRST_SEEN_MILLIS = 1_700_000_600_000L
    }
}
