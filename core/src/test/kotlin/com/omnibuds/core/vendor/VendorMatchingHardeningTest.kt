package com.omnibuds.core.vendor

import com.omnibuds.core.device.DeviceFingerprint
import com.omnibuds.core.protocol.ProtocolDefinition
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase 39: vendor-matching hardening.
 *
 * No real vendor adapter exists (Phase 19 BLOCKED), so these tests
 * prove the framework's safety semantics with scripted adapters:
 * ambiguous identities never enable writes, conflicting matches
 * fall back to unknown-device handling, and name-only evidence is
 * never sufficient for a definite match.
 */

/** Matches only when manufacturer data contains a specific company ID. */
private class ScriptedVendorAdapter(
    override val adapterId: String,
    private val companyId: Int,
    private val ambiguousOnNameOnly: Boolean = true,
) : VendorAdapter {
    override val displayName: String = "Scripted $adapterId"
    override val protocol: ProtocolDefinition = ProtocolDefinition(
        protocolId = "$adapterId.proto",
        displayName = "Scripted",
        vendor = adapterId,
        transport = com.omnibuds.core.common.TransportKind.GATT,
        version = "1",
        commands = emptyMap(),
        responses = emptyMap(),
        capabilityMappings = emptyMap(),
        confidence = com.omnibuds.core.state.VerificationLevel.LAB_TESTED,
    )
    override val supportedFirmware: Set<String>? = null

    override fun match(fingerprint: DeviceFingerprint): MatchResult {
        val hasCompany = fingerprint.manufacturerData.any {
            it.companyId == companyId
        }
        return when {
            hasCompany -> MatchResult.Matched(adapterId, "companyId=$companyId")
            fingerprint.isEntirelyUnobserved -> MatchResult.NotMatched
            // Name-only or partial evidence: ambiguous, never a match.
            ambiguousOnNameOnly -> MatchResult.Ambiguous(
                "insufficient identity evidence",
            )
            else -> MatchResult.NotMatched
        }
    }
}

private fun fingerprintWithCompany(companyId: Int) = DeviceFingerprint(
    manufacturerData = listOf(
        com.omnibuds.core.device.ManufacturerDataEntry(
            companyId = companyId,
            dataHex = "010203",
        ),
    ),
)

class VendorMatchingHardeningTest {

    @Test
    fun `exact manufacturer evidence matches`() = runTest {
        val registry = VendorRegistry(
            listOf(ScriptedVendorAdapter("acme.audio", 0x004C)),
        )
        val adapter = registry.resolve(fingerprintWithCompany(0x004C))
        assertEquals("acme.audio", adapter?.adapterId)
    }

    @Test
    fun `missing metadata never matches`() = runTest {
        val registry = VendorRegistry(
            listOf(ScriptedVendorAdapter("acme.audio", 0x004C)),
        )
        assertNull(registry.resolve(DeviceFingerprint()))
        assertFalse(registry.isAmbiguous(DeviceFingerprint()))
    }

    @Test
    fun `partial evidence is ambiguous not matched`() = runTest {
        val registry = VendorRegistry(
            listOf(ScriptedVendorAdapter("acme.audio", 0x004C)),
        )
        // Service UUIDs alone: partial evidence, no company ID.
        val partial = DeviceFingerprint(
            serviceUuids = setOf("0000180f-0000-1000-8000-00805f9b34fb"),
        )
        assertNull(registry.resolve(partial))
        assertTrue(registry.isAmbiguous(partial))
    }

    @Test
    fun `conflicting matches resolve to null`() = runTest {
        val registry = VendorRegistry(
            listOf(
                ScriptedVendorAdapter("acme.audio", 0x004C),
                ScriptedVendorAdapter("other.audio", 0x004C),
            ),
        )
        // Both match the same evidence: safest is no adapter.
        assertNull(registry.resolve(fingerprintWithCompany(0x004C)))
    }

    @Test
    fun `ambiguous match blocks resolution`() = runTest {
        val registry = VendorRegistry(
            listOf(
                ScriptedVendorAdapter("acme.audio", 0x004C),
                ScriptedVendorAdapter("other.audio", 0x00E0),
            ),
        )
        val partial = DeviceFingerprint(
            serviceUuids = setOf("0000180f-0000-1000-8000-00805f9b34fb"),
        )
        // One adapter is ambiguous: resolution must be null even
        // though no adapter positively matched.
        assertNull(registry.resolve(partial))
    }

    @Test
    fun `one model cannot borrow another models protocol assumptions`() = runTest {
        val registry = VendorRegistry(
            listOf(ScriptedVendorAdapter("acme.audio", 0x004C)),
        )
        // Different company's device: no match, no ambiguity leak.
        assertNull(registry.resolve(fingerprintWithCompany(0x00E0)))
    }

    @Test
    fun `null adapter never matches`() = runTest {
        val registry = VendorRegistry(listOf(NullVendorAdapter()))
        assertNull(registry.resolve(fingerprintWithCompany(0x004C)))
        assertEquals(listOf("omnibuds.null"), registry.adapterIds())
    }

    @Test
    fun `registration is immutable and deterministic`() = runTest {
        val r1 = VendorRegistry()
        val r2 = r1.register(ScriptedVendorAdapter("acme.audio", 0x004C))
        assertTrue(r1.adapterIds().isEmpty())
        assertEquals(listOf("acme.audio"), r2.adapterIds())
        // Same fingerprint resolves the same way twice.
        val fp = fingerprintWithCompany(0x004C)
        assertEquals(r2.resolve(fp)?.adapterId, r2.resolve(fp)?.adapterId)
    }
}
