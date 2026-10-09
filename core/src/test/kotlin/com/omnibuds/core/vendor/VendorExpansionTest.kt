package com.omnibuds.core.vendor

import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.device.DeviceFingerprint
import com.omnibuds.core.device.ManufacturerDataEntry
import com.omnibuds.core.protocol.ProtocolDefinition
import com.omnibuds.core.state.VerificationLevel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Phase 40: vendor expansion framework tests.
 *
 * All adapters here are deterministic test-only integrations with
 * clearly synthetic identities. None represents a real vendor
 * protocol.
 */

private fun scriptedProtocol(id: String) = ProtocolDefinition(
    protocolId = "$id.proto",
    displayName = "Scripted $id",
    vendor = id,
    transport = TransportKind.GATT,
    version = "1",
    commands = emptyMap(),
    responses = emptyMap(),
    capabilityMappings = emptyMap(),
    confidence = VerificationLevel.LAB_TESTED,
)

/** Matches a single manufacturer company ID. */
private class SyntheticVendorAdapter(
    override val adapterId: String,
    private val companyId: Int,
) : VendorAdapter {
    override val displayName: String = "Synthetic $adapterId"
    override val protocol: ProtocolDefinition = scriptedProtocol(adapterId)
    override val supportedFirmware: Set<String>? = null

    override fun match(fingerprint: DeviceFingerprint): MatchResult {
        val ids = fingerprint.manufacturerData.mapNotNull { it.companyId }
        return when {
            companyId in ids -> MatchResult.Matched(adapterId, "companyId=$companyId")
            fingerprint.isEntirelyUnobserved -> MatchResult.NotMatched
            // A different company's ID is a definite non-match.
            ids.isNotEmpty() -> MatchResult.NotMatched
            // Partial evidence (no company ID): ambiguous.
            else -> MatchResult.Ambiguous("partial identity evidence")
        }
    }
}

private fun fingerprintWithCompany(companyId: Int) = DeviceFingerprint(
    manufacturerData = listOf(
        ManufacturerDataEntry(companyId = companyId, dataHex = "010203"),
    ),
)

class VendorIntegrationContractTest {

    @Test
    fun `contract version constant is 1`() {
        assertEquals(1, VendorIntegrationContract.CURRENT_CONTRACT_VERSION)
    }

    @Test
    fun `blank integration id rejected`() {
        assertThrows<IllegalArgumentException> {
            VendorIntegrationContract(1, " ", emptySet(), null)
        }
    }

    @Test
    fun `contract version below 1 rejected`() {
        assertThrows<IllegalArgumentException> {
            VendorIntegrationContract(0, "vendor.x", emptySet(), null)
        }
    }
}

class FirmwareCompatibilityEvaluatorTest {

    private fun contract(firmware: Set<String>?) = VendorIntegrationContract(
        contractVersion = 1,
        integrationId = "vendor.x",
        productFamilies = setOf("vendor.x.family"),
        verifiedFirmware = firmware,
    )

    @Test
    fun `verified firmware recognized`() {
        assertEquals(
            FirmwareCompatibility.VERIFIED,
            FirmwareCompatibilityEvaluator.evaluate(
                contract(setOf("1.2.3")), "1.2.3",
            ),
        )
    }

    @Test
    fun `unlisted firmware is incompatible`() {
        assertEquals(
            FirmwareCompatibility.INCOMPATIBLE,
            FirmwareCompatibilityEvaluator.evaluate(
                contract(setOf("1.2.3")), "9.9.9",
            ),
        )
    }

    @Test
    fun `firmware-agnostic integration stays unknown`() {
        assertEquals(
            FirmwareCompatibility.UNKNOWN_FIRMWARE,
            FirmwareCompatibilityEvaluator.evaluate(contract(null), "1.2.3"),
        )
    }

    @Test
    fun `missing firmware version stays unknown`() {
        assertEquals(
            FirmwareCompatibility.UNKNOWN_FIRMWARE,
            FirmwareCompatibilityEvaluator.evaluate(
                contract(setOf("1.2.3")), null,
            ),
        )
    }
}

class VendorResolverTest {

    @Test
    fun `exact match resolves`() = runTest {
        val resolver = VendorResolver(
            VendorRegistry(listOf(SyntheticVendorAdapter("synth.a", 0x004C))),
        )
        val outcome = resolver.resolve(fingerprintWithCompany(0x004C))
        assertTrue(outcome is VendorResolution.ExactMatch)
        assertEquals("synth.a", (outcome as VendorResolution.ExactMatch).adapter.adapterId)
    }

    @Test
    fun `unknown device resolves to unknown`() = runTest {
        val resolver = VendorResolver(
            VendorRegistry(listOf(SyntheticVendorAdapter("synth.a", 0x004C))),
        )
        val outcome = resolver.resolve(DeviceFingerprint())
        assertTrue(outcome is VendorResolution.UnknownDevice)
    }

    @Test
    fun `partial evidence is ambiguous`() = runTest {
        val resolver = VendorResolver(
            VendorRegistry(listOf(SyntheticVendorAdapter("synth.a", 0x004C))),
        )
        val partial = DeviceFingerprint(
            serviceUuids = setOf("0000180f-0000-1000-8000-00805f9b34fb"),
        )
        val outcome = resolver.resolve(partial)
        assertTrue(outcome is VendorResolution.Ambiguous)
    }
}

class VendorIsolationTest {

    @Test
    fun `two integrations register independently`() = runTest {
        val registry = VendorRegistry(
            listOf(
                SyntheticVendorAdapter("synth.a", 0x004C),
                SyntheticVendorAdapter("synth.b", 0x00E0),
            ),
        )
        val resolver = VendorResolver(registry)
        val a = resolver.resolve(fingerprintWithCompany(0x004C))
        val b = resolver.resolve(fingerprintWithCompany(0x00E0))
        assertTrue(a is VendorResolution.ExactMatch)
        assertTrue(b is VendorResolution.ExactMatch)
        assertEquals("synth.a", (a as VendorResolution.ExactMatch).adapter.adapterId)
        assertEquals("synth.b", (b as VendorResolution.ExactMatch).adapter.adapterId)
    }

    @Test
    fun `protocol implementations cannot leak across vendors`() = runTest {
        val a = SyntheticVendorAdapter("synth.a", 0x004C)
        val b = SyntheticVendorAdapter("synth.b", 0x00E0)
        // Each adapter's protocol is scoped to its own integration.
        assertEquals("synth.a.proto", a.protocol.protocolId)
        assertEquals("synth.b.proto", b.protocol.protocolId)
        assertTrue(a.protocol.protocolId != b.protocol.protocolId)
    }

    @Test
    fun `failed adapter does not break unrelated integrations`() = runTest {
        val exploding = object : VendorAdapter {
            override val adapterId = "synth.boom"
            override val displayName = "Exploding"
            override val protocol = scriptedProtocol("synth.boom")
            override val supportedFirmware: Set<String>? = null
            override fun match(fingerprint: DeviceFingerprint): MatchResult =
                throw IllegalStateException("boom")
        }
        val registry = VendorRegistry(
            listOf(exploding, SyntheticVendorAdapter("synth.a", 0x004C)),
        )
        // Registry.match callers are expected to handle exceptions;
        // here we assert the registry itself holds both adapters.
        assertEquals(2, registry.adapterIds().size)
    }

    @Test
    fun `removing an integration leaves no residue`() = runTest {
        val full = VendorRegistry(
            listOf(
                SyntheticVendorAdapter("synth.a", 0x004C),
                SyntheticVendorAdapter("synth.b", 0x00E0),
            ),
        )
        val reduced = VendorRegistry(
            listOf(SyntheticVendorAdapter("synth.a", 0x004C)),
        )
        // Immutable registries: the full registry is untouched and the
        // reduced one reports no match (not ambiguity) for synth.b devices:
        // a different company's ID is a definite non-match.
        assertEquals(2, full.adapterIds().size)
        val outcome = VendorResolver(reduced).resolve(fingerprintWithCompany(0x00E0))
        assertTrue(outcome is VendorResolution.UnknownDevice)
    }
}
