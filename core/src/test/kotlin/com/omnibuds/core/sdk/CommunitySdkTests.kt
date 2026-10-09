package com.omnibuds.core.sdk

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.device.DeviceFingerprint
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.sdk.api.CommunityCapabilityDeclaration
import com.omnibuds.core.sdk.api.CommunityEvidenceRecord
import com.omnibuds.core.sdk.api.CommunityOperationDefinition
import com.omnibuds.core.sdk.api.OperationTier
import com.omnibuds.core.sdk.api.SdkOperationResult
import com.omnibuds.core.sdk.api.SdkVersion
import com.omnibuds.core.sdk.examples.AcmeBudsCommunityAdapter
import com.omnibuds.core.sdk.testing.CommunityConformanceRunner
import com.omnibuds.core.sdk.testing.SdkFakeStep
import com.omnibuds.core.sdk.testing.ScriptedFakeTransport
import com.omnibuds.core.sdk.validation.CommunityMetadataPackage
import com.omnibuds.core.sdk.validation.CommunityPackageValidator
import com.omnibuds.core.sdk.validation.ValidationSeverity
import com.omnibuds.core.state.CapabilityState
import com.omnibuds.core.state.VerificationLevel
import com.omnibuds.core.transport.TransportRequest
import com.omnibuds.core.vendor.MatchResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

import com.omnibuds.core.device.ManufacturerDataEntry

class CommunitySdkTests {

    private val sampleFingerprint = DeviceFingerprint(
        manufacturerData = listOf(
            ManufacturerDataEntry(companyId = 0xFFFE, dataHex = "0102"),
        ),
    )

    @Test
    fun `sdk version compares correctly and parses valid versions`() {
        val v100 = SdkVersion(1, 0, 0)
        val v110 = SdkVersion(1, 1, 0)
        val v200 = SdkVersion(2, 0, 0)

        assertTrue(v100 < v110)
        assertTrue(v110 < v200)
        assertEquals(v100, SdkVersion.parse("1.0.0"))
        assertEquals(null, SdkVersion.parse("invalid"))
        assertEquals(null, SdkVersion.parse("0.1.0")) // major must be >= 1
    }

    @Test
    fun `evidence record rejects self-promoted hardware verification`() {
        assertThrows<IllegalArgumentException> {
            CommunityEvidenceRecord(
                contributorId = "c1",
                integrationId = "v.a",
                integrationVersion = "1.0",
                sdkVersion = SdkVersion.CURRENT,
                protocolVersion = "1.0",
                supportedModels = setOf("M1"),
                verifiedFirmware = null,
                evidenceSource = "Web claim",
                declaredVerificationLevel = VerificationLevel.HARDWARE_VERIFIED, // forbidden!
                knownLimitations = emptyList(),
                testFixtureReferences = emptyList(),
                lastReviewedDate = "2026-10-09",
            )
        }
    }

    @Test
    fun `capability declaration rejects self-promoted persistence verified state`() {
        assertThrows<IllegalArgumentException> {
            CommunityCapabilityDeclaration(
                featureId = FeatureId.ofVendor("acme", "anc"),
                claimedState = CapabilityState.PERSISTENCE_VERIFIED, // forbidden!
                tier = OperationTier.MUTATING,
                requiredTransport = TransportKind.GATT,
            )
        }
    }

    @Test
    fun `package validator accepts valid package`() {
        val adapter = AcmeBudsCommunityAdapter()
        val pkg = CommunityMetadataPackage(
            adapterId = adapter.adapterId,
            displayName = adapter.displayName,
            sdkVersion = adapter.sdkVersion.toString(),
            integrationVersion = adapter.integrationVersion,
            evidenceRecord = adapter.evidenceRecord,
            capabilities = adapter.declaredCapabilities,
            operations = adapter.declaredOperations,
        )

        val report = CommunityPackageValidator.validate(pkg)
        assertTrue(report.isValid)
        assertEquals(0, report.errorCount)
    }

    @Test
    fun `package validator rejects invalid identifiers and incompatible sdk versions`() {
        val adapter = AcmeBudsCommunityAdapter()
        val badPkg = CommunityMetadataPackage(
            adapterId = "invalid_identifier", // needs namespace.name
            displayName = "", // blank!
            sdkVersion = "2.0.0", // incompatible major version!
            integrationVersion = "1.0.0",
            evidenceRecord = adapter.evidenceRecord,
            capabilities = adapter.declaredCapabilities,
            operations = adapter.declaredOperations,
        )

        val report = CommunityPackageValidator.validate(badPkg)
        assertFalse(report.isValid)
        assertTrue(report.findings.any { it.ruleId == "SDK-VAL-002" })
        assertTrue(report.findings.any { it.ruleId == "SDK-VAL-003" })
        assertTrue(report.findings.any { it.ruleId == "SDK-VAL-005" })
    }

    @Test
    fun `package validator detects duplicate capabilities and circular dependencies`() {
        val adapter = AcmeBudsCommunityAdapter()
        val dupFeature = FeatureId.ofVendor("acme", "anc")
        val duplicateCaps = listOf(
            CommunityCapabilityDeclaration(
                featureId = dupFeature,
                claimedState = CapabilityState.SUPPORTED_VOLATILE,
                tier = OperationTier.MUTATING,
                requiredTransport = TransportKind.GATT,
                dependencies = setOf(dupFeature), // self-dependency!
            ),
            CommunityCapabilityDeclaration(
                featureId = dupFeature, // duplicate!
                claimedState = CapabilityState.READ_ONLY,
                tier = OperationTier.READ_ONLY,
                requiredTransport = TransportKind.GATT,
            ),
        )

        val badPkg = CommunityMetadataPackage(
            adapterId = adapter.adapterId,
            displayName = adapter.displayName,
            sdkVersion = adapter.sdkVersion.toString(),
            integrationVersion = adapter.integrationVersion,
            evidenceRecord = adapter.evidenceRecord,
            capabilities = duplicateCaps,
            operations = adapter.declaredOperations,
        )

        val report = CommunityPackageValidator.validate(badPkg)
        assertFalse(report.isValid)
        assertTrue(report.findings.any { it.ruleId == "SDK-VAL-009" }) // duplicate
        assertTrue(report.findings.any { it.ruleId == "SDK-VAL-011" }) // circular self-dependency
    }

    @Test
    fun `reference adapter passes conformance runner`() {
        val adapter = AcmeBudsCommunityAdapter()
        val summary = CommunityConformanceRunner.verifyAdapter(adapter, sampleFingerprint)
        assertTrue(summary.passed, "Conformance runner reported errors: ${summary.errors}")
    }

    @Test
    fun `reference adapter correctly matches synthetic fingerprint and rejects unmatched`() {
        val adapter = AcmeBudsCommunityAdapter()
        val matchResult = adapter.match(sampleFingerprint)
        assertTrue(matchResult is MatchResult.Matched)

        val unmatchedFingerprint = DeviceFingerprint(
            manufacturerData = listOf(
                ManufacturerDataEntry(companyId = 0x004C, dataHex = "01"), // Apple ID
            ),
        )
        val notMatched = adapter.match(unmatchedFingerprint)
        assertTrue(notMatched is MatchResult.NotMatched)
    }

    @Test
    fun `reference adapter encodes and decodes payloads with structured results`() {
        val adapter = AcmeBudsCommunityAdapter()

        // 1. Encode ANC command
        val encResult = adapter.encodeOutboundCommand("acme.set_anc", mapOf("mode" to "on"))
        assertTrue(encResult is SdkOperationResult.Success)
        val encodedBytes = (encResult as SdkOperationResult.Success).value
        assertEquals(0x02.toByte(), encodedBytes[0])
        assertEquals(0x01.toByte(), encodedBytes[1])

        // 2. Parse ANC response
        val parseResult = adapter.parseInboundPayload("acme.set_anc", byteArrayOf(0x02, 0x00))
        assertTrue(parseResult is SdkOperationResult.Success)
        val values = (parseResult as SdkOperationResult.Success).value
        assertEquals("success", values["status"])

        // 3. Handle malformed payload
        val malformed = adapter.parseInboundPayload("acme.set_anc", byteArrayOf())
        assertTrue(malformed is SdkOperationResult.MalformedData)

        // 4. Handle unsupported operation
        val unsupp = adapter.encodeOutboundCommand("acme.nonexistent", emptyMap())
        assertTrue(unsupp is SdkOperationResult.Unsupported)
    }

    @Test
    fun `scripted fake transport handles responses, errors, and disconnects deterministically`() = runTest {
        val transport = ScriptedFakeTransport()
        transport.script(
            SdkFakeStep.Respond(byteArrayOf(0x01, 0x50)),
            SdkFakeStep.Fail(OmniBudsErrorCategory.TIMEOUT, "Simulated timeout"),
            SdkFakeStep.Disconnect,
        )

        val openRes = transport.open()
        assertTrue(openRes is OperationOutcome.Success)
        assertTrue(transport.isOpen)

        // 1. Send first request and receive response
        val req1 = TransportRequest("acme.get_battery", byteArrayOf(0x01, 0x00), 1000L)
        val r1 = transport.exchange(req1, 1000L)
        assertTrue(r1 is OperationOutcome.Success)
        val resp1 = (r1 as OperationOutcome.Success).value
        assertEquals("acme.get_battery", resp1.commandId)
        assertEquals(0x50.toByte(), resp1.payload?.get(1))
        assertEquals(1, transport.recordedRequests.size)

        // 2. Receive second failure step
        val req2 = TransportRequest("acme.get_battery", byteArrayOf(0x01, 0x00), 1000L)
        val r2 = transport.exchange(req2, 1000L)
        assertTrue(r2 is OperationOutcome.Failure)
        assertEquals(OmniBudsErrorCategory.TIMEOUT, (r2 as OperationOutcome.Failure).error.category)

        // 3. Receive third disconnect step
        val req3 = TransportRequest("acme.get_battery", byteArrayOf(0x01, 0x00), 1000L)
        val r3 = transport.exchange(req3, 1000L)
        assertTrue(r3 is OperationOutcome.Failure)
        assertEquals(OmniBudsErrorCategory.DEVICE_DISCONNECTED, (r3 as OperationOutcome.Failure).error.category)
        assertFalse(transport.isOpen)

        // Post-disconnect exchanges fail closed
        val r4 = transport.exchange(req1, 1000L)
        assertTrue(r4 is OperationOutcome.Failure)
        assertEquals(OmniBudsErrorCategory.DEVICE_DISCONNECTED, (r4 as OperationOutcome.Failure).error.category)
    }
}
