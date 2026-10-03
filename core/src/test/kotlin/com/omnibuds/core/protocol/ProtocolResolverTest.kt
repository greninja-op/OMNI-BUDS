package com.omnibuds.core.protocol

import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.device.DeviceFingerprint
import com.omnibuds.core.device.IdentificationResult
import com.omnibuds.core.device.IdentitySignalKind
import com.omnibuds.core.state.VerificationLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Protocol resolution under prompt §10 and ADR-P7-006: deterministic, ambiguity-preserving, never selecting
 * from a brand name, never acting. The fixtures are invented candidate records with fake facts; nothing here
 * is a claim about a real protocol (ADR-P7-010). Tier T1.
 */
class ProtocolResolverTest {

    private val resolver = RegistryProtocolResolver()

    private fun definition(id: String, transport: TransportKind, version: String? = "1") =
        ProtocolDefinition(
            protocolId = id,
            displayName = "Fixture $id",
            vendor = null,
            transport = transport,
            version = version,
            commands = emptyMap(),
            responses = emptyMap(),
            capabilityMappings = emptyMap(),
            confidence = VerificationLevel.INFERRED,
        )

    private fun fingerprintNaming(vararg ids: String) = DeviceFingerprint(protocolCandidates = ids.toList())

    private val unknownIdentity = IdentificationResult.Unknown(
        evidence = emptyList(),
        registryVersion = 1,
        ruleSetVersion = 1,
    )

    private val insufficientIdentity = IdentificationResult.InsufficientEvidence(
        missingKinds = listOf(IdentitySignalKind.REPORTED_NAME),
        evidence = emptyList(),
        registryVersion = 1,
        ruleSetVersion = 1,
    )

    @Test
    fun theShippedEmptyRegistryResolvesNothingToUnknown() {
        val resolution = resolver.resolve(
            fingerprint = fingerprintNaming("example.proto"),
            identification = unknownIdentity,
            registry = ProtocolRegistry.empty(),
            availableTransports = setOf(TransportKind.GATT),
        )

        assertEquals(ProtocolResolutionOutcome.UNKNOWN, resolution.outcome)
        assertFalse(resolution.isActionable)
    }

    @Test
    fun thinEvidenceYieldsInsufficientNotUnknown() {
        val resolution = resolver.resolve(
            fingerprintNaming("example.proto"),
            insufficientIdentity,
            ProtocolRegistry.empty(),
            setOf(TransportKind.GATT),
        )

        assertEquals(ProtocolResolutionOutcome.INSUFFICIENT_EVIDENCE, resolution.outcome)
    }

    @Test
    fun aSingleCompatibleCandidateResolvesToIt() {
        val candidate = definition("example.gatt.proto", TransportKind.GATT)
        val registry = ProtocolRegistry(listOf(candidate))

        val resolution = resolver.resolve(
            fingerprintNaming("example.gatt.proto"),
            unknownIdentity,
            registry,
            availableTransports = setOf(TransportKind.GATT, TransportKind.RFCOMM),
        )

        assertEquals(ProtocolResolutionOutcome.RESOLVED, resolution.outcome)
        assertTrue(resolution.isActionable)
        assertEquals(candidate, resolution.selected)
    }

    @Test
    fun severalCompatibleCandidatesStayAmbiguousAndSelectNone() {
        val registry = ProtocolRegistry(
            listOf(
                definition("a.proto", TransportKind.GATT),
                definition("b.proto", TransportKind.GATT),
            ),
        )

        val resolution = resolver.resolve(
            fingerprintNaming("a.proto", "b.proto"),
            unknownIdentity,
            registry,
            setOf(TransportKind.GATT),
        )

        assertEquals(ProtocolResolutionOutcome.AMBIGUOUS, resolution.outcome)
        assertNull(resolution.selected)
        assertEquals(2, resolution.candidates.size)
        assertFalse(resolution.isActionable)
    }

    @Test
    fun aCandidateWhoseTransportIsUnavailableIsUnsupportedNotChosenElsewhere() {
        val registry = ProtocolRegistry(listOf(definition("rfcomm.only", TransportKind.RFCOMM)))

        val resolution = resolver.resolve(
            fingerprintNaming("rfcomm.only"),
            unknownIdentity,
            registry,
            availableTransports = setOf(TransportKind.GATT), // RFCOMM not available
        )

        assertEquals(ProtocolResolutionOutcome.UNSUPPORTED, resolution.outcome)
        assertNull(resolution.selected)
    }

    @Test
    fun aVersionIncompatibleCandidateIsReportedAsIncompatible() {
        val registry = ProtocolRegistry(listOf(definition("old.proto", TransportKind.GATT)))

        val resolution = resolver.resolve(
            fingerprintNaming("old.proto"),
            unknownIdentity,
            registry,
            availableTransports = setOf(TransportKind.GATT),
            versionCompatible = { false }, // caller's firmware check fails
        )

        assertEquals(ProtocolResolutionOutcome.INCOMPATIBLE_VERSION, resolution.outcome)
        assertNull(resolution.selected)
    }

    @Test
    fun resolutionOrderIsDeterministicRegardlessOfRegistryInsertionOrder() {
        val a = definition("a.proto", TransportKind.GATT)
        val b = definition("b.proto", TransportKind.GATT)
        val fingerprint = fingerprintNaming("b.proto", "a.proto")

        val one = resolver.resolve(fingerprint, unknownIdentity, ProtocolRegistry(listOf(a, b)), setOf(TransportKind.GATT))
        val two = resolver.resolve(fingerprint, unknownIdentity, ProtocolRegistry(listOf(b, a)), setOf(TransportKind.GATT))

        assertEquals(one.candidates.map { it.protocolId }, two.candidates.map { it.protocolId })
        assertEquals(listOf("a.proto", "b.proto"), one.candidates.map { candidate -> candidate.protocolId })
    }

    @Test
    fun anEmptyFingerprintCandidateListIsNeverActionable() {
        val resolution = resolver.resolve(
            DeviceFingerprint(), // no protocol candidates named
            unknownIdentity,
            ProtocolRegistry(listOf(definition("x.proto", TransportKind.GATT))),
            setOf(TransportKind.GATT),
        )

        assertEquals(ProtocolResolutionOutcome.UNKNOWN, resolution.outcome)
        assertFalse(resolution.isActionable)
    }
}
