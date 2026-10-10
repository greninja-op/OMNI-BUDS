package com.omnibuds.core.firmware

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.state.CapabilityState

class FirmwareOperationGateAndMigrationTest {

    private val ancFeature = FeatureId.of("noise-control", "anc")

    @Test
    fun mutatingOperationAuthorizedOnlyWhenCompatible() {
        val compatResult = FirmwareCompatibilityResult(
            status = FirmwareCompatibilityStatus.COMPATIBLE,
            matchedRule = null,
            protocolResolution = null,
            reasonCode = "OK_COMPATIBLE",
            message = "firmware is verified",
        )

        val decision = FirmwareOperationGate.authorizeOperation(
            isMutating = true,
            featureId = ancFeature,
            compatibilityResult = compatResult,
            capabilityState = CapabilityState.SUPPORTED_VOLATILE,
        )
        assertIs<FirmwareAuthorizationDecision.Authorized>(decision)
    }

    @Test
    fun mutatingOperationDeniedOnReadOnlyOrIncompatible() {
        val compatResult = FirmwareCompatibilityResult(
            status = FirmwareCompatibilityStatus.COMPATIBLE_WITH_LIMITATIONS,
            matchedRule = null,
            protocolResolution = null,
            reasonCode = "OK_LIMITATIONS",
            message = "read-only mode",
        )

        val decision = FirmwareOperationGate.authorizeOperation(
            isMutating = true,
            featureId = ancFeature,
            compatibilityResult = compatResult,
            capabilityState = CapabilityState.READ_ONLY,
        )
        assertIs<FirmwareAuthorizationDecision.Denied>(decision)

        // Read-only should succeed
        val readDecision = FirmwareOperationGate.authorizeOperation(
            isMutating = false,
            featureId = ancFeature,
            compatibilityResult = compatResult,
            capabilityState = CapabilityState.READ_ONLY,
        )
        assertIs<FirmwareAuthorizationDecision.Authorized>(readDecision)
    }

    @Test
    fun mutatingOperationDeniedOnUnknownFirmware() {
        val unknownResult = FirmwareCompatibilityResult(
            status = FirmwareCompatibilityStatus.UNKNOWN_FIRMWARE,
            matchedRule = null,
            protocolResolution = null,
            reasonCode = "ERR_UNKNOWN_FW",
            message = "firmware unknown",
        )

        val decision = FirmwareOperationGate.authorizeOperation(
            isMutating = true,
            featureId = ancFeature,
            compatibilityResult = unknownResult,
            capabilityState = CapabilityState.SUPPORTED_VOLATILE,
        )
        assertIs<FirmwareAuthorizationDecision.Denied>(decision)
    }

    @Test
    fun migrationParsesValidRecord() {
        val record = FirmwareCompatibilityRuleRecord(
            ruleId = "RULE-SONY-01",
            manufacturer = "sony",
            applicableModels = listOf("WF-1000XM4"),
            constraintType = "AT_LEAST",
            constraintValue = "2.0.0",
            targetScope = "ANC",
            outcome = "COMPATIBLE",
            evidenceReference = "EVID-SONY-200",
            verificationLevel = "LAB_TESTED",
            limitations = emptyList(),
            rationale = "Supported on 2.0.0+",
        )

        val rule = FirmwareMetadataMigration.parseRecord(record)
        assertTrue(rule != null)
        assertTrue(rule.matches("WF-1000XM4", FirmwareVersion.parse("2.0.1"), "ANC"))
    }

    @Test
    fun migrationRejectsCorruptRecordSafely() {
        val corrupt = FirmwareCompatibilityRuleRecord(
            ruleId = "",
            manufacturer = "sony",
            applicableModels = emptyList(),
            constraintType = "INVALID_TYPE",
            constraintValue = "",
            targetScope = null,
            outcome = "UNKNOWN_OUTCOME",
            evidenceReference = "",
            verificationLevel = "BAD_LEVEL",
            limitations = emptyList(),
            rationale = "",
        )

        val rule = FirmwareMetadataMigration.parseRecord(corrupt)
        assertNull(rule)
    }
}
