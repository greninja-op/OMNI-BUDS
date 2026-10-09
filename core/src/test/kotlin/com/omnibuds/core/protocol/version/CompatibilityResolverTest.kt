package com.omnibuds.core.protocol.version

import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.device.DeviceFingerprint
import com.omnibuds.core.device.IdentificationConfidence
import com.omnibuds.core.device.IdentificationResult
import com.omnibuds.core.device.ManufacturerIdentity
import com.omnibuds.core.device.ModelIdentity
import com.omnibuds.core.state.VerificationLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompatibilityResolverTest {

    private val resolver = CompatibilityResolver()
    private val fingerprint = DeviceFingerprint()
    private val knownIdentity = IdentificationResult.Exact(
        manufacturer = ManufacturerIdentity("acme", "Acme"),
        model = ModelIdentity("acme", "buds", "Buds Pro"),
        confidence = IdentificationConfidence.HIGH,
        matchedRuleIds = listOf("rule-1"),
        evidence = emptyList(),
        registryVersion = 1,
        ruleSetVersion = 1,
        limitations = "none",
    )

    private fun createProtocol(
        id: String,
        version: ProtocolVersion,
        constraint: VersionConstraint = VersionConstraint.Exact(version),
        transport: TransportKind = TransportKind.RFCOMM,
        firmware: Set<String>? = null,
        schema: ProtocolSchemaVersion = ProtocolSchemaVersion.CURRENT,
        limitations: List<String> = emptyList(),
    ) = ProtocolIdentity(
        protocolId = id,
        vendorNamespace = "acme",
        version = version,
        schemaVersion = schema,
        transport = transport,
        versionConstraint = constraint,
        verifiedFirmware = firmware,
        confidence = VerificationLevel.LAB_TESTED,
        limitations = limitations,
    )

    @Test
    fun exactVersionMatchResolvesCompatible() {
        val proto = createProtocol("acme.buds.v1", ProtocolVersion.Semantic(1, 0, 0))
        val resolution = resolver.resolve(
            candidates = listOf(proto),
            fingerprint = fingerprint,
            identification = knownIdentity,
            observedVersion = ProtocolVersion.Semantic(1, 0, 0),
            observedFirmware = null,
            availableTransports = setOf(TransportKind.RFCOMM),
        )

        assertEquals(CompatibilityStatus.COMPATIBLE, resolution.status)
        assertEquals(proto, resolution.selectedProtocol)
        assertTrue(resolution.canAuthorizeMutatingOperations)
    }

    @Test
    fun versionMismatchResolvesIncompatible() {
        val proto = createProtocol("acme.buds.v1", ProtocolVersion.Semantic(1, 0, 0))
        val resolution = resolver.resolve(
            candidates = listOf(proto),
            fingerprint = fingerprint,
            identification = knownIdentity,
            observedVersion = ProtocolVersion.Semantic(2, 0, 0),
            observedFirmware = null,
            availableTransports = setOf(TransportKind.RFCOMM),
        )

        assertEquals(CompatibilityStatus.INCOMPATIBLE, resolution.status)
        assertNull(resolution.selectedProtocol)
        assertFalse(resolution.canAuthorizeMutatingOperations)
        assertEquals("ERR_VERSION_MISMATCH", resolution.reasonCode)
    }

    @Test
    fun unknownDeviceVersionResolvesUnknownVersionAndBlocksWrites() {
        val proto = createProtocol("acme.buds.v1", ProtocolVersion.Semantic(1, 0, 0))
        val resolution = resolver.resolve(
            candidates = listOf(proto),
            fingerprint = fingerprint,
            identification = knownIdentity,
            observedVersion = ProtocolVersion.Unknown,
            observedFirmware = null,
            availableTransports = setOf(TransportKind.RFCOMM),
        )

        assertEquals(CompatibilityStatus.UNKNOWN_VERSION, resolution.status)
        assertNull(resolution.selectedProtocol)
        assertFalse(resolution.canAuthorizeMutatingOperations)
    }

    @Test
    fun transportMismatchResolvesIncompatible() {
        val proto = createProtocol("acme.buds.v1", ProtocolVersion.Semantic(1, 0, 0), transport = TransportKind.RFCOMM)
        val resolution = resolver.resolve(
            candidates = listOf(proto),
            fingerprint = fingerprint,
            identification = knownIdentity,
            observedVersion = ProtocolVersion.Semantic(1, 0, 0),
            observedFirmware = null,
            availableTransports = setOf(TransportKind.GATT),
        )

        assertEquals(CompatibilityStatus.INCOMPATIBLE, resolution.status)
        assertEquals("ERR_TRANSPORT_UNSUPPORTED", resolution.reasonCode)
    }

    @Test
    fun firmwareMismatchResolvesIncompatible() {
        val proto = createProtocol(
            id = "acme.buds.v1",
            version = ProtocolVersion.Semantic(1, 0, 0),
            firmware = setOf("1.2.0", "1.2.1"),
        )
        val resolution = resolver.resolve(
            candidates = listOf(proto),
            fingerprint = fingerprint,
            identification = knownIdentity,
            observedVersion = ProtocolVersion.Semantic(1, 0, 0),
            observedFirmware = "2.0.0",
            availableTransports = setOf(TransportKind.RFCOMM),
        )

        assertEquals(CompatibilityStatus.INCOMPATIBLE, resolution.status)
        assertEquals("ERR_FIRMWARE_INCOMPATIBLE", resolution.reasonCode)
    }

    @Test
    fun ambiguousCandidatesSelectsNoWinner() {
        val protoA = createProtocol(
            id = "acme.buds.candidateA",
            version = ProtocolVersion.Semantic(1, 0, 0),
            constraint = VersionConstraint.AnyKnown,
        )
        val protoB = createProtocol(
            id = "acme.buds.candidateB",
            version = ProtocolVersion.Semantic(1, 1, 0),
            constraint = VersionConstraint.AnyKnown,
        )

        val resolution = resolver.resolve(
            candidates = listOf(protoA, protoB),
            fingerprint = fingerprint,
            identification = knownIdentity,
            observedVersion = ProtocolVersion.Semantic(1, 0, 5),
            observedFirmware = null,
            availableTransports = setOf(TransportKind.RFCOMM),
        )

        assertEquals(CompatibilityStatus.AMBIGUOUS, resolution.status)
        assertNull(resolution.selectedProtocol)
        assertEquals(2, resolution.candidateProtocols.size)
        assertFalse(resolution.canAuthorizeMutatingOperations)
    }

    @Test
    fun compatibleWithLimitationsAllowsReadOnlyOnly() {
        val proto = createProtocol(
            id = "acme.buds.v1",
            version = ProtocolVersion.Semantic(1, 0, 0),
            limitations = listOf("read-only telemetry supported"),
        )
        val resolution = resolver.resolve(
            candidates = listOf(proto),
            fingerprint = fingerprint,
            identification = knownIdentity,
            observedVersion = ProtocolVersion.Semantic(1, 0, 0),
            observedFirmware = null,
            availableTransports = setOf(TransportKind.RFCOMM),
        )

        assertEquals(CompatibilityStatus.COMPATIBLE_WITH_LIMITATIONS, resolution.status)
        assertNotNull(resolution.selectedProtocol)
        assertFalse(resolution.canAuthorizeMutatingOperations)
        assertTrue(resolution.canAuthorizeReadOnlyOperations)
    }
}
