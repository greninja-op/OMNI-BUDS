package com.omnibuds.core.sdk.examples

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.device.DeviceFingerprint
import com.omnibuds.core.device.ManufacturerDataEntry
import com.omnibuds.core.protocol.ProtocolDefinition
import com.omnibuds.core.sdk.api.CommunityCapabilityDeclaration
import com.omnibuds.core.sdk.api.CommunityEvidenceRecord
import com.omnibuds.core.sdk.api.CommunityOperationDefinition
import com.omnibuds.core.sdk.api.CommunityProtocolAdapter
import com.omnibuds.core.sdk.api.OperationTier
import com.omnibuds.core.sdk.api.SdkOperationResult
import com.omnibuds.core.sdk.api.SdkVersion
import com.omnibuds.core.state.CapabilityState
import com.omnibuds.core.state.VerificationLevel
import com.omnibuds.core.vendor.MatchResult

/**
 * Deterministic synthetic reference integration for the Community Protocol SDK.
 *
 * Demonstrates:
 * - Versioned metadata & provenance
 * - Device identity matching against data-loaded assigned numbers
 * - Safe capability declarations without hardware self-promotion
 * - Request encoding and response parsing with structured error classification
 * - No direct hardware or raw Bluetooth writes
 */
class AcmeBudsCommunityAdapter(
    private val fixtureData: AcmeFixtureData = AcmeFixtureData.loadDefault(),
) : CommunityProtocolAdapter {

    override val adapterId: String = "community.acme.buds"
    override val displayName: String = "Acme Buds (Synthetic Reference)"
    override val sdkVersion: SdkVersion = SdkVersion.CURRENT
    override val integrationVersion: String = "1.0.0"

    override val supportedFirmware: Set<String>? = setOf("1.0.0", "1.1.0")

    override val evidenceRecord: CommunityEvidenceRecord = CommunityEvidenceRecord(
        contributorId = "contributor.acme",
        integrationId = adapterId,
        integrationVersion = integrationVersion,
        sdkVersion = sdkVersion,
        protocolVersion = "1.0",
        supportedModels = setOf("Acme Buds Pro", "Acme Buds Standard"),
        verifiedFirmware = supportedFirmware,
        evidenceSource = fixtureData.provenance,
        declaredVerificationLevel = VerificationLevel.LAB_TESTED,
        knownLimitations = listOf("Synthetic example only — no physical hardware connection"),
        testFixtureReferences = listOf("fixtures/acme_buds_v1.json"),
        lastReviewedDate = "2026-10-09",
    )

    private val ancFeature = FeatureId.ofVendor("acme", "anc")
    private val batteryFeature = FeatureId.ofVendor("acme", "battery")

    override val declaredCapabilities: List<CommunityCapabilityDeclaration> = listOf(
        CommunityCapabilityDeclaration(
            featureId = ancFeature,
            claimedState = CapabilityState.SUPPORTED_VOLATILE,
            tier = OperationTier.MUTATING,
            requiredTransport = TransportKind.GATT,
            timeoutMillis = 2000L,
            notes = "Active Noise Cancellation mode control",
        ),
        CommunityCapabilityDeclaration(
            featureId = batteryFeature,
            claimedState = CapabilityState.READ_ONLY,
            tier = OperationTier.READ_ONLY,
            requiredTransport = TransportKind.GATT,
            timeoutMillis = 1500L,
            notes = "Battery percentage query",
        ),
    )

    override val declaredOperations: List<CommunityOperationDefinition> = listOf(
        CommunityOperationDefinition(
            operationId = "acme.set_anc",
            targetFeature = ancFeature,
            tier = OperationTier.MUTATING,
            defaultTimeoutMillis = 2000L,
            description = "Set ANC state: 0=off, 1=on, 2=transparency",
        ),
        CommunityOperationDefinition(
            operationId = "acme.get_battery",
            targetFeature = batteryFeature,
            tier = OperationTier.READ_ONLY,
            defaultTimeoutMillis = 1500L,
            description = "Read battery level",
        ),
    )

    override val protocol: ProtocolDefinition = ProtocolDefinition(
        protocolId = "community.acme.proto",
        displayName = "Acme Synthetic Protocol",
        vendor = "acme",
        transport = TransportKind.GATT,
        version = "1.0",
        commands = emptyMap(),
        responses = emptyMap(),
        capabilityMappings = emptyMap(),
        confidence = VerificationLevel.LAB_TESTED,
    )

    override fun match(fingerprint: DeviceFingerprint): MatchResult {
        if (fingerprint.isEntirelyUnobserved) return MatchResult.NotMatched
        val hasMatch = fingerprint.manufacturerData.any { it.companyId == fixtureData.syntheticCompanyId }
        return if (hasMatch) {
            MatchResult.Matched(
                adapterId = adapterId,
                evidence = "Matched synthetic Acme company identifier from fixture",
            )
        } else {
            MatchResult.NotMatched
        }
    }

    override fun parseInboundPayload(
        operationId: String,
        payload: ByteArray,
    ): SdkOperationResult<Map<String, String>> {
        if (payload.isEmpty()) {
            return SdkOperationResult.MalformedData("Payload is empty")
        }

        return when (operationId) {
            "acme.get_battery" -> {
                if (payload.size < 2 || payload[0] != fixtureData.batteryOpHeader) {
                    return SdkOperationResult.MalformedData("Expected battery header with 2 bytes")
                }
                val percent = payload[1].toInt() and 255
                SdkOperationResult.Success(mapOf("battery_percent" to percent.toString()))
            }
            "acme.set_anc" -> {
                if (payload.size < 2 || payload[0] != fixtureData.ancOpHeader) {
                    return SdkOperationResult.MalformedData("Expected ANC response header with status byte")
                }
                val statusCode = payload[1].toInt()
                val status = if (statusCode == 0) "success" else "failed"
                SdkOperationResult.Success(mapOf("status" to status))
            }
            else -> SdkOperationResult.Unsupported("Unknown operationId: $operationId")
        }
    }

    override fun encodeOutboundCommand(
        operationId: String,
        parameters: Map<String, String>,
    ): SdkOperationResult<ByteArray> {
        return when (operationId) {
            "acme.get_battery" -> {
                val header = fixtureData.batteryOpHeader
                val param: Byte = 0
                SdkOperationResult.Success(byteArrayOf(header, param))
            }
            "acme.set_anc" -> {
                val mode = parameters["mode"]
                    ?: return SdkOperationResult.MalformedData("Missing required parameter 'mode'")
                val modeByte: Byte = when (mode) {
                    "off" -> 0
                    "on" -> 1
                    "transparency" -> 2
                    else -> return SdkOperationResult.MalformedData("Invalid mode value: $mode")
                }
                val header = fixtureData.ancOpHeader
                SdkOperationResult.Success(byteArrayOf(header, modeByte))
            }
            else -> SdkOperationResult.Unsupported("Unknown operationId: $operationId")
        }
    }
}
