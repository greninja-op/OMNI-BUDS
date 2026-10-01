package com.omnibuds.core.protocol

import com.omnibuds.core.device.DeviceFingerprint
import com.omnibuds.core.state.VerificationLevel
import com.omnibuds.core.common.TransportKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Phase 1 state of protocol knowledge, and the immutability of [ProtocolRegistry].
 *
 * The first test here is the machine-checked proof that no vendor protocol has been
 * implemented in Phase 1 (Phase 1 prompt sections 2, 26, 51 and 53): a registry built with
 * no arguments holds nothing, and nothing in `:core` can hand it a record.
 *
 * Tier T1. Fictional ids only.
 */
class ProtocolRegistryTest {

    @Test
    fun noProtocolRecordShipsInPhase1() {
        val registry = ProtocolRegistry()

        assertTrue(registry.isEmpty)
        assertEquals(0, registry.size)
        assertEquals(emptyList(), registry.records)
        assertEquals(emptySet(), registry.protocolIds)
        assertTrue(ProtocolRegistry.empty().isEmpty)
    }

    @Test
    fun anEmptyRegistryAssertsNothingAboutAnyProtocolId() {
        val registry = ProtocolRegistry()

        assertNull(registry.find("example-vendor.test-protocol"))
        assertNull(registry.find(""))
        assertNull(registry.find("no-protocol-matched"))
    }

    @Test
    fun anEmptyRegistryOffersNoCandidatesForAnyFingerprint() {
        val registry = ProtocolRegistry()

        assertTrue(registry.candidatesFor(DeviceFingerprint.empty()).isEmpty())
        assertTrue(
            registry.candidatesFor(
                DeviceFingerprint(
                    transportCandidates = setOf(TransportKind.GATT, TransportKind.RFCOMM),
                    protocolCandidates = listOf("example-vendor.test-protocol", "example-vendor.other"),
                    deviceClass = 7936,
                ),
            ).isEmpty(),
        )
        // Empty here means "no record names this evidence", which is unknown, not
        // unsupported: the caller must not read it as an absence of protocol (PROTO-DB-002).
        assertEquals(
            emptyList(),
            registry.candidatesFor(DeviceFingerprint(protocolCandidates = listOf("unlisted-protocol-name"))),
        )
    }

    @Test
    fun registeringReturnsANewValueAndLeavesTheOriginalUntouched() {
        val original = ProtocolRegistry()
        val withOne = original.register(definition(FIRST_ID))

        assertTrue(original.isEmpty)
        assertEquals(0, original.size)
        assertEquals(1, withOne.size)
        assertFalse(withOne.isEmpty)
        assertNotNull(withOne.find(FIRST_ID))
        assertNull(withOne.find(SECOND_ID))
        assertNotEquals(original, withOne)
    }

    @Test
    fun oneProtocolIdCannotCarryTwoRecords() {
        val registry = ProtocolRegistry().register(definition(FIRST_ID))

        assertFailsWith<IllegalArgumentException> { registry.register(definition(FIRST_ID)) }
    }

    @Test
    fun aDuplicateIdInTheStartingSetIsRefusedRatherThanSilentlyMerged() {
        assertFailsWith<IllegalArgumentException> {
            ProtocolRegistry(listOf(definition(FIRST_ID), definition(FIRST_ID)))
        }
    }

    @Test
    fun aCandidateIsReturnedOnlyWhenTheFingerprintNamesItExactly() {
        val registry = ProtocolRegistry().register(definition(FIRST_ID)).register(definition(SECOND_ID))

        val oneNamed = registry.candidatesFor(DeviceFingerprint(protocolCandidates = listOf(FIRST_ID)))
        val bothNamed = registry.candidatesFor(DeviceFingerprint(protocolCandidates = listOf(SECOND_ID, FIRST_ID)))
        val nearMiss = registry.candidatesFor(DeviceFingerprint(protocolCandidates = listOf("$FIRST_ID-ish")))
        val repeated = registry.candidatesFor(DeviceFingerprint(protocolCandidates = listOf(FIRST_ID, FIRST_ID)))

        assertEquals(listOf(FIRST_ID), oneNamed.map { it.protocolId })
        assertEquals(listOf(SECOND_ID, FIRST_ID), bothNamed.map { it.protocolId })
        assertTrue(nearMiss.isEmpty())
        assertEquals(listOf(FIRST_ID), repeated.map { it.protocolId })
    }

    @Test
    fun removingARecordProducesANewValueAndLeavesTheOriginalIntact() {
        val two = ProtocolRegistry(listOf(definition(FIRST_ID), definition(SECOND_ID)))
        val one = two.without(FIRST_ID)

        assertEquals(2, two.size)
        assertEquals(1, one.size)
        assertNull(one.find(FIRST_ID))
        assertNotNull(one.find(SECOND_ID))
        assertEquals(two, two.without("never-registered"))
    }

    @Test
    fun theListingIsOrderedByIdSoTwoRegistriesPrintIdentically() {
        val registry = ProtocolRegistry(listOf(definition(SECOND_ID), definition(FIRST_ID)))

        // "other-test-protocol" sorts before "test-protocol"; the insertion order above is
        // deliberately the reverse, so a listing that merely echoed insertion would fail.
        val expectedOrder = listOf(SECOND_ID, FIRST_ID)

        assertEquals(expectedOrder, registry.protocolIds.toList().sorted())
        assertEquals(
            expectedOrder,
            registry.records.map { it.protocolId },
            "records must be listed in a reproducible order",
        )
    }

    @Test
    fun aRegisteredRecordKeepsItsOwnConfidenceAndGatesNothingByItself() {
        val registry = ProtocolRegistry().register(definition(FIRST_ID))
        val record = assertNotNull(registry.find(FIRST_ID))

        // Registering knowledge is not hardware evidence: the record keeps the tier it was
        // authored with (PROTO-VERIFY-001, PROTO-DB-002).
        assertEquals(VerificationLevel.INFERRED, record.confidence)
        assertFalse(record.supportsWrites)
    }

    private fun definition(protocolId: String): ProtocolDefinition = ProtocolDefinition(
        protocolId = protocolId,
        displayName = "Example test protocol",
        vendor = "example-vendor",
        transport = TransportKind.GATT,
        version = null,
        commands = emptyMap(),
        responses = emptyMap(),
        capabilityMappings = emptyMap(),
        confidence = VerificationLevel.INFERRED,
    )

    private companion object {
        const val FIRST_ID = "example-vendor.test-protocol"
        const val SECOND_ID = "example-vendor.other-test-protocol"
    }
}
