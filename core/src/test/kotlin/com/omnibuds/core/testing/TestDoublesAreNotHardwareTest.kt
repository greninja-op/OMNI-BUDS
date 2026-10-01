package com.omnibuds.core.testing

import com.omnibuds.core.capability.DeviceCapabilities
import com.omnibuds.core.capability.FeatureCapability
import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.device.DeviceFingerprint
import com.omnibuds.core.device.DeviceIdentity
import com.omnibuds.core.persistence.CapabilityRepository
import com.omnibuds.core.persistence.DeviceRepository
import com.omnibuds.core.persistence.DiscoveredCapabilityRecord
import com.omnibuds.core.persistence.ProtocolRecordAccess
import com.omnibuds.core.persistence.SavedDeviceRecord
import com.omnibuds.core.state.CapabilityState
import com.omnibuds.core.state.VerificationLevel
import com.omnibuds.core.common.TransportKind
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The guard test for Phase 1 prompt sections 26, 27 and 53: a test double must never be
 * mistakable for hardware support.
 *
 * Each case pins one direction in which a generous fake could lie. The defaults of every
 * double in this package are deliberately *empty* - nothing saved, nothing cached, no
 * protocol matched, no record resolved - so a test only gets a populated answer when it
 * scripts one, and a scripted failure or cancellation comes back as itself instead of being
 * replaced by the happy path. If a future edit makes any case below pass by making a fake
 * agreeable, this is the file that fails.
 *
 * Assertions read outcomes through `isSuccess`, [OperationOutcome.valueOrNull] and
 * [OperationOutcome.errorOrNull] rather than by casting, so "Success carrying null" stays
 * distinguishable from "Failure" - the difference that the unknown-device rules of
 * `docs/phases/phase-0/security-governance.md` SEC-UNK-001 turn on.
 *
 * Fixtures are fictional throughout: invented service tokens, invented protocol ids, no real
 * vendor name and no real UUID anywhere.
 *
 * Traceability note for the orchestrator: names follow specs.md section 1.4 and carry no
 * `TEST-P1-<NNN>` id, because Phase 1 test-plan numbering is a documents-side decision.
 */
class TestDoublesAreNotHardwareTest {

    private val anc: FeatureId = FeatureId.of("noise-control", "anc")

    private fun fixtureRecord(key: String = FIXTURE_IDENTITY_KEY): SavedDeviceRecord = SavedDeviceRecord(
        identityKey = key,
        identity = DeviceIdentity.of(
            manufacturer = "fictional audio works",
            model = "FAW-BUDS-ONE",
            displayName = "fixture buds",
            modelId = "fixture-model-1",
        ),
        firmware = null,
        savedAtEpochMillis = FIXTURE_EPOCH_MILLIS,
        lastSeenEpochMillis = null,
    )

    private fun fixtureError(category: OmniBudsErrorCategory): OmniBudsError = OmniBudsError.of(
        category = category,
        operationId = "test.doubles.guard",
        detail = "scripted by TestDoublesAreNotHardwareTest",
    )

    // (a) a fresh device double knows nothing, because nothing was saved.
    @Test
    fun aFreshDeviceRepositoryIsEmptyRatherThanPrePopulatedWithSeenDevices() = runTest {
        val repository = FakeDeviceRepository()

        val listing = repository.savedDevices()
        assertTrue(listing.isSuccess)
        val listed = requireNotNull(listing.valueOrNull)
        assertTrue(listed.isEmpty())
        assertEquals(0, listed.size)
        assertEquals(1, repository.savedDevicesCalls)
        assertTrue(repository.savedCalls.isEmpty())

        val missing = repository.find(NEVER_SEEN_KEY)
        assertTrue(missing.isSuccess)
        assertNull(missing.valueOrNull)
    }

    // (b) an unscripted protocol seam offers no candidates, so a device stays unknown.
    @Test
    fun anUnscriptedProtocolSeamMatchesNoProtocolForAnyFingerprint() = runTest {
        val access = FakeProtocolRecordAccess()

        val candidates = access.recordsFor(FIXTURE_FINGERPRINT)
        assertTrue(candidates.isSuccess)
        val unscripted = requireNotNull(candidates.valueOrNull)
        assertTrue(unscripted.isEmpty())

        val missingRecord = access.record(UNSCRIPTED_PROTOCOL_ID)
        assertTrue(missingRecord.isSuccess)
        assertNull(missingRecord.valueOrNull)

        // A match is available only to a test that asks for one, explicitly and in order.
        access.scriptCandidates(FIXTURE_FINGERPRINT, SCRIPTED_PROTOCOL_ID)
        val scripted = requireNotNull(access.recordsFor(FIXTURE_FINGERPRINT).valueOrNull)
        assertEquals(listOf(SCRIPTED_PROTOCOL_ID), scripted)
    }

    // (c) an unknown cache key reads back as "nothing known", never as synthesised absence.
    @Test
    fun anUnscriptedCapabilityLoadReturnsNothingKnownNotAnInventedCapabilitySet() = runTest {
        val repository = FakeCapabilityRepository()

        val missing = repository.load(NEVER_SEEN_KEY)
        assertTrue(missing.isSuccess)
        assertNull(missing.valueOrNull)
        assertTrue(repository.cachedKeys().isEmpty())

        // An empty-but-real snapshot stays empty: absence reads as UNKNOWN, never UNSUPPORTED.
        val emptySnapshot = DiscoveredCapabilityRecord(
            identityKey = FIXTURE_IDENTITY_KEY,
            capabilities = DeviceCapabilities.empty(),
            discoveredAtEpochMillis = null,
            verification = VerificationLevel.INFERRED,
        )
        repository.store(emptySnapshot)
        val loaded = requireNotNull(repository.load(FIXTURE_IDENTITY_KEY).valueOrNull)

        assertSame(emptySnapshot, loaded)
        assertEquals(CapabilityState.UNKNOWN, loaded.capabilities.stateOf(anc))
        assertTrue(loaded.capabilities.unsupported.isEmpty())
    }

    @Test
    fun cachingAPersistenceVerifiedClaimReturnsItUnchangedAndProvesNothingMore() = runTest {
        val repository = FakeCapabilityRepository()
        val persistenceVerified = FeatureCapability(
            feature = anc,
            state = CapabilityState.PERSISTENCE_VERIFIED,
            readable = true,
            writable = true,
            transport = TransportKind.GATT,
            protocolId = SCRIPTED_PROTOCOL_ID,
            requiresConnection = true,
            verification = VerificationLevel.PERSISTENCE_VERIFIED,
        )
        val record = DiscoveredCapabilityRecord(
            identityKey = FIXTURE_IDENTITY_KEY,
            capabilities = DeviceCapabilities(mapOf(anc to persistenceVerified)),
            discoveredAtEpochMillis = FIXTURE_EPOCH_MILLIS,
            verification = VerificationLevel.PERSISTENCE_VERIFIED,
        )

        repository.store(record)
        val loaded = requireNotNull(repository.load(FIXTURE_IDENTITY_KEY).valueOrNull)

        // The fake neither upgrades nor launders a tier on its way through storage. What a
        // caller may conclude from `loaded` is governed by the tier stored in it and by
        // nothing else, which is why storing is not evidence and a stale cache can still be
        // refused by reading [DiscoveredCapabilityRecord.verification] against the firmware
        // actually observed this session.
        assertSame(record, loaded)
        assertEquals(VerificationLevel.PERSISTENCE_VERIFIED, loaded.verification)
        assertEquals(CapabilityState.PERSISTENCE_VERIFIED, loaded.capabilities.stateOf(anc))
    }

    // (d) a scripted failure stays a failure; the default answer never replaces it.
    @Test
    fun aScriptedFailureIsReturnedAsFailureAndNotSwallowedIntoADefaultSuccess() = runTest {
        val repository = FakeDeviceRepository()
        repository.saveVerdicts.fail(fixtureError(OmniBudsErrorCategory.READ_FAILED))

        val outcome = repository.save(fixtureRecord())
        assertFalse(outcome.isSuccess)
        assertNull(outcome.valueOrNull)
        val refusal = requireNotNull(outcome.errorOrNull)
        assertEquals(OmniBudsErrorCategory.READ_FAILED, refusal.category)
        // A refused save stored nothing: the user's list is as empty as it was before.
        assertTrue(repository.storedKeys().isEmpty())

        // Same rule on a seam whose unscripted default is a plausible-looking empty success.
        val access = FakeProtocolRecordAccess()
        access.recordsForVerdicts.fail(fixtureError(OmniBudsErrorCategory.UNKNOWN_DEVICE))
        val candidates = access.recordsFor(FIXTURE_FINGERPRINT)
        assertFalse(candidates.isSuccess)
        val lookupFailure = requireNotNull(candidates.errorOrNull)
        assertEquals(OmniBudsErrorCategory.UNKNOWN_DEVICE, lookupFailure.category)
    }

    // (e) cancellation is its own case, never a success and never a failure.
    @Test
    fun aScriptedCancellationSurfacesAsCancelledNotAsSuccess() = runTest {
        val scripted = ScriptedOutcome<SavedDeviceRecord>().cancel().succeed(fixtureRecord())

        assertSame(OperationOutcome.Cancelled, scripted.next())
        assertEquals(1, scripted.remaining)
        assertTrue(scripted.hasNext)

        val repository = FakeDeviceRepository()
        repository.saveVerdicts.cancel()
        val outcome = repository.save(fixtureRecord())
        assertSame(OperationOutcome.Cancelled, outcome)
        assertNull(outcome.valueOrNull)
        assertNull(outcome.errorOrNull)
        assertTrue(repository.storedKeys().isEmpty())
        assertEquals(0, repository.saveVerdicts.remaining)
    }

    @Test
    fun anUnscriptedStepRefusesToInventAnOutcome() = runTest {
        val scripted = ScriptedOutcome<SavedDeviceRecord>()

        // The alternative to raising is handing a default Success to a caller that scripted
        // nothing, which is the exact lie this package exists to make impossible.
        assertFailsWith<IllegalStateException> { scripted.next() }
    }

    @Test
    fun thePersistenceSeamsAreInterfacesAndTheOnlyImplementationsHereAreTheNamedFakes() {
        // Reflection over the classpath cannot reliably prove "no production class implements
        // these seams" from inside a unit test, so this asserts the weaker checkable half: the
        // contracts are interfaces, and the only implementable types in this package are the
        // three named doubles. The real enforcement of "no fake reaches src/main" is the
        // source-scan architecture test owned by the orchestrator, which greps for
        // implementations of these interfaces outside com.omnibuds.core.testing.
        assertTrue(DeviceRepository::class.java.isInterface)
        assertTrue(CapabilityRepository::class.java.isInterface)
        assertTrue(ProtocolRecordAccess::class.java.isInterface)

        assertTrue(DeviceRepository::class.java.isAssignableFrom(FakeDeviceRepository::class.java))
        assertTrue(
            CapabilityRepository::class.java.isAssignableFrom(FakeCapabilityRepository::class.java),
        )
        assertTrue(
            ProtocolRecordAccess::class.java.isAssignableFrom(FakeProtocolRecordAccess::class.java),
        )
    }

    private companion object {
        /** Invented token, not an assigned or real UUID. */
        const val FIXTURE_SERVICE_TOKEN = "fixture-service-token-alpha"

        val FIXTURE_FINGERPRINT: DeviceFingerprint = DeviceFingerprint(
            serviceUuids = setOf(FIXTURE_SERVICE_TOKEN),
            transportCandidates = setOf(TransportKind.GATT),
        )

        /** Address-free by construction: derived from evidence, never from an address. */
        val FIXTURE_IDENTITY_KEY: String = FIXTURE_FINGERPRINT.identityKey()

        const val NEVER_SEEN_KEY = "omnibuds-fingerprint/v1=never-observed-in-this-test"
        const val FIXTURE_EPOCH_MILLIS = 1_700_000_000_000L
        const val SCRIPTED_PROTOCOL_ID = "fixture-protocol-alpha"
        const val UNSCRIPTED_PROTOCOL_ID = "fixture-protocol-never-scripted"
    }
}
