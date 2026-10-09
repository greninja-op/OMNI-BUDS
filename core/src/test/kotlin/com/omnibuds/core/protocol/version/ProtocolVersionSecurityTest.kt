package com.omnibuds.core.protocol.version

import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.device.DeviceFingerprint
import com.omnibuds.core.device.IdentificationConfidence
import com.omnibuds.core.device.IdentificationResult
import com.omnibuds.core.device.ManufacturerIdentity
import com.omnibuds.core.device.ModelIdentity
import com.omnibuds.core.state.VerificationLevel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ProtocolVersionSecurityTest {

    private val resolver = CompatibilityResolver()
    private val fingerprint = DeviceFingerprint()
    private val recognized = IdentificationResult.Exact(
        manufacturer = ManufacturerIdentity("acme", "Acme"),
        model = ModelIdentity("acme", "buds", "Buds Pro"),
        confidence = IdentificationConfidence.HIGH,
        matchedRuleIds = listOf("rule-1"),
        evidence = emptyList(),
        registryVersion = 1,
        ruleSetVersion = 1,
        limitations = "none",
    )

    private val protocol = ProtocolIdentity(
        protocolId = "acme.buds.v1",
        vendorNamespace = "acme",
        version = ProtocolVersion.Semantic(1, 0, 0),
        schemaVersion = ProtocolSchemaVersion.CURRENT,
        transport = TransportKind.RFCOMM,
        versionConstraint = VersionConstraint.Exact(ProtocolVersion.Semantic(1, 0, 0)),
        confidence = VerificationLevel.LAB_TESTED,
    )

    @Test
    fun unknownProtocolVersionCannotAuthorizeMutatingCommands() {
        val resolution = resolver.resolve(
            candidates = listOf(protocol),
            fingerprint = fingerprint,
            identification = recognized,
            observedVersion = ProtocolVersion.Unknown,
            observedFirmware = null,
            availableTransports = setOf(TransportKind.RFCOMM),
        )

        assertFalse(resolution.canAuthorizeMutatingOperations, "mutating commands must be forbidden on unknown versions")
        assertNull(resolution.selectedProtocol)
    }

    @Test
    fun ambiguousCandidatesCannotAuthorizeMutatingCommands() {
        val altProtocol = protocol.copy(
            protocolId = "acme.buds.alt",
            versionConstraint = VersionConstraint.AnyKnown,
        )
        val baseProtocol = protocol.copy(
            versionConstraint = VersionConstraint.AnyKnown,
        )

        val resolution = resolver.resolve(
            candidates = listOf(baseProtocol, altProtocol),
            fingerprint = fingerprint,
            identification = recognized,
            observedVersion = ProtocolVersion.Semantic(1, 2, 0),
            observedFirmware = null,
            availableTransports = setOf(TransportKind.RFCOMM),
        )

        assertFalse(resolution.canAuthorizeMutatingOperations, "mutating commands must be forbidden on ambiguous resolution")
        assertNull(resolution.selectedProtocol, "no arbitrary candidate can be selected when ambiguous")
    }

    @Test
    fun incompatibleVersionCannotAuthorizeMutatingCommands() {
        val resolution = resolver.resolve(
            candidates = listOf(protocol),
            fingerprint = fingerprint,
            identification = recognized,
            observedVersion = ProtocolVersion.Semantic(3, 0, 0),
            observedFirmware = null,
            availableTransports = setOf(TransportKind.RFCOMM),
        )

        assertFalse(resolution.canAuthorizeMutatingOperations)
        assertNull(resolution.selectedProtocol)
    }
}
