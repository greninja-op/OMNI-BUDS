package com.omnibuds.core.firmware

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.device.DeviceFingerprint
import com.omnibuds.core.device.IdentificationConfidence
import com.omnibuds.core.device.IdentificationResult
import com.omnibuds.core.device.ManufacturerIdentity
import com.omnibuds.core.device.ModelIdentity
import com.omnibuds.core.protocol.version.ProtocolIdentity
import com.omnibuds.core.protocol.version.ProtocolSchemaVersion
import com.omnibuds.core.protocol.version.ProtocolVersion
import com.omnibuds.core.protocol.version.VersionConstraint
import com.omnibuds.core.state.VerificationLevel

class FirmwareCompatibilityResolverTest {

    private val sampleFingerprint = DeviceFingerprint()

    private val sampleIdentification = IdentificationResult.Exact(
        manufacturer = ManufacturerIdentity("omni", "OmniVendor"),
        model = ModelIdentity("omni", "buds", "OmniBuds Pro"),
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
        firmware: Set<String>? = null,
    ) = ProtocolIdentity(
        protocolId = id,
        vendorNamespace = "omni",
        version = version,
        schemaVersion = ProtocolSchemaVersion.CURRENT,
        transport = TransportKind.RFCOMM,
        versionConstraint = VersionConstraint.Exact(version),
        verifiedFirmware = firmware,
        confidence = VerificationLevel.LAB_TESTED,
    )

    private val baseProtocol = createProtocol(
        id = "omni.buds.v1",
        version = ProtocolVersion.Semantic(1, 0, 0),
        firmware = setOf("1.0.0", "1.1.0"),
    )

    @Test
    fun compatibleFirmwareResolvesSuccessfully() {
        val resolver = FirmwareCompatibilityResolver()
        val obs = FirmwareObservation.create(
            deviceId = "dev-1",
            rawVersion = "1.0.0",
            source = FirmwareObservationSource.DEVICE_DIS_AUTHORITATIVE,
            observedAtMs = 1000L,
            verificationLevel = VerificationLevel.LAB_TESTED,
        )

        val res = resolver.resolve(
            candidates = listOf(baseProtocol),
            fingerprint = sampleFingerprint,
            identification = sampleIdentification,
            observedVersion = ProtocolVersion.Semantic(1, 0, 0),
            observation = obs,
            availableTransports = setOf(TransportKind.RFCOMM),
        )

        assertEquals(FirmwareCompatibilityStatus.COMPATIBLE, res.status)
        assertTrue(res.canAuthorizeMutatingOperations)
        assertTrue(res.canAuthorizeReadOnlyOperations)
    }

    @Test
    fun incompatibleFirmwareFailsResolution() {
        val resolver = FirmwareCompatibilityResolver()
        val obs = FirmwareObservation.create(
            deviceId = "dev-1",
            rawVersion = "2.0.0", // Not in verifiedFirmware set of baseProtocol
            source = FirmwareObservationSource.DEVICE_DIS_AUTHORITATIVE,
            observedAtMs = 1000L,
            verificationLevel = VerificationLevel.LAB_TESTED,
        )

        val res = resolver.resolve(
            candidates = listOf(baseProtocol),
            fingerprint = sampleFingerprint,
            identification = sampleIdentification,
            observedVersion = ProtocolVersion.Semantic(1, 0, 0),
            observation = obs,
            availableTransports = setOf(TransportKind.RFCOMM),
        )

        assertEquals(FirmwareCompatibilityStatus.INCOMPATIBLE, res.status)
        assertFalse(res.canAuthorizeMutatingOperations)
        assertFalse(res.canAuthorizeReadOnlyOperations)
    }

    @Test
    fun explicitDenylistRuleTrumpsGeneralSupport() {
        val denyRule = FirmwareCompatibilityRule(
            ruleId = "RULE-BUGGY-1.1.0",
            manufacturer = "omni",
            firmwareConstraint = FirmwareConstraint.Exact(FirmwareVersion.parse("1.1.0")),
            outcome = FirmwareRuleOutcome.INCOMPATIBLE,
            evidenceReference = "ISSUE-110-CORRUPT",
            rationale = "Firmware 1.1.0 crashes on BLE commands",
        )

        val resolver = FirmwareCompatibilityResolver(rules = listOf(denyRule))
        val obs = FirmwareObservation.create(
            deviceId = "dev-1",
            rawVersion = "1.1.0",
            source = FirmwareObservationSource.DEVICE_DIS_AUTHORITATIVE,
            observedAtMs = 1000L,
            verificationLevel = VerificationLevel.LAB_TESTED,
        )

        val res = resolver.resolve(
            candidates = listOf(baseProtocol),
            fingerprint = sampleFingerprint,
            identification = sampleIdentification,
            observedVersion = ProtocolVersion.Semantic(1, 0, 0),
            observation = obs,
            availableTransports = setOf(TransportKind.RFCOMM),
        )

        assertEquals(FirmwareCompatibilityStatus.INCOMPATIBLE, res.status)
        assertEquals("ERR_FIRMWARE_UNSUPPORTED", res.reasonCode)
        assertFalse(res.canAuthorizeMutatingOperations)
    }

    @Test
    fun staleFirmwareObservationIsRejected() {
        val resolver = FirmwareCompatibilityResolver(maxFreshnessMs = 5000L)
        val obs = FirmwareObservation.create(
            deviceId = "dev-1",
            rawVersion = "1.0.0",
            source = FirmwareObservationSource.DEVICE_DIS_AUTHORITATIVE,
            observedAtMs = 1000L,
            verificationLevel = VerificationLevel.LAB_TESTED,
        )

        val res = resolver.resolve(
            candidates = listOf(baseProtocol),
            fingerprint = sampleFingerprint,
            identification = sampleIdentification,
            observedVersion = ProtocolVersion.Semantic(1, 0, 0),
            observation = obs,
            availableTransports = setOf(TransportKind.RFCOMM),
            currentTimeMs = 10000L, // 9000ms old, exceeds 5000ms maxFreshness
        )

        assertEquals(FirmwareCompatibilityStatus.STALE_FIRMWARE_OBSERVATION, res.status)
        assertFalse(res.canAuthorizeMutatingOperations)
    }

    @Test
    fun unknownFirmwareYieldsUnknownStatus() {
        val resolver = FirmwareCompatibilityResolver()
        val obs = FirmwareObservation.missing(
            deviceId = "dev-1",
            timestampMs = 1000L,
        )

        val res = resolver.resolve(
            candidates = listOf(baseProtocol),
            fingerprint = sampleFingerprint,
            identification = sampleIdentification,
            observedVersion = ProtocolVersion.Semantic(1, 0, 0),
            observation = obs,
            availableTransports = setOf(TransportKind.RFCOMM),
        )

        assertEquals(FirmwareCompatibilityStatus.UNKNOWN_FIRMWARE, res.status)
        assertFalse(res.canAuthorizeMutatingOperations)
    }

    @Test
    fun compatibleWithLimitationsAllowsReadOnly() {
        val limitRule = FirmwareCompatibilityRule(
            ruleId = "RULE-READONLY-1.0.0",
            manufacturer = "omni",
            firmwareConstraint = FirmwareConstraint.Exact(FirmwareVersion.parse("1.0.0")),
            outcome = FirmwareRuleOutcome.COMPATIBLE_WITH_LIMITATIONS,
            evidenceReference = "EVID-SAFE-READONLY",
            limitations = listOf("telemetry only"),
            rationale = "Mutations untested on 1.0.0",
        )

        val resolver = FirmwareCompatibilityResolver(rules = listOf(limitRule))
        val obs = FirmwareObservation.create(
            deviceId = "dev-1",
            rawVersion = "1.0.0",
            source = FirmwareObservationSource.DEVICE_DIS_AUTHORITATIVE,
            observedAtMs = 1000L,
            verificationLevel = VerificationLevel.LAB_TESTED,
        )

        val res = resolver.resolve(
            candidates = listOf(baseProtocol),
            fingerprint = sampleFingerprint,
            identification = sampleIdentification,
            observedVersion = ProtocolVersion.Semantic(1, 0, 0),
            observation = obs,
            availableTransports = setOf(TransportKind.RFCOMM),
        )

        assertEquals(FirmwareCompatibilityStatus.COMPATIBLE_WITH_LIMITATIONS, res.status)
        assertFalse(res.canAuthorizeMutatingOperations)
        assertTrue(res.canAuthorizeReadOnlyOperations)
    }
}
