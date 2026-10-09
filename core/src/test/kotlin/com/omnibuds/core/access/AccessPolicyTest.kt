package com.omnibuds.core.access

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Phase 21: access-policy tests.
 */
class AccessPolicyTest {

    private val unknown = DeviceAccessState.unknown()
    private val ambiguous = DeviceAccessState(
        classification = DeviceClassification.AMBIGUOUS_IDENTITY,
        identityConfidence = IdentityConfidence.LOW,
        protocolVerified = false,
        writeAuthorized = false,
    )
    private val unsupported = DeviceAccessState(
        classification = DeviceClassification.IDENTIFIED_UNSUPPORTED,
        identityConfidence = IdentityConfidence.HIGH,
        protocolVerified = false,
        writeAuthorized = false,
    )
    private val unverified = DeviceAccessState(
        classification = DeviceClassification.KNOWN_PROTOCOL_UNVERIFIED,
        identityConfidence = IdentityConfidence.HIGH,
        protocolVerified = false,
        writeAuthorized = false,
    )
    private val supported = DeviceAccessState(
        classification = DeviceClassification.KNOWN_DEVICE_SUPPORTED,
        identityConfidence = IdentityConfidence.HIGH,
        protocolVerified = true,
        writeAuthorized = false,
    )

    @Test
    fun `unknown device cannot execute vendor writes`() {
        val decision = DeviceAccessPolicy.evaluate(
            unknown, OperationCategory.HARDWARE_STATE_WRITE, capabilityId = "anc",
        )
        assertFalse(decision.allowed)
        assertEquals(DenialReason.UNKNOWN_DEVICE_WRITE_DENIED, decision.reason)
    }

    @Test
    fun `ambiguous identity cannot execute model-specific writes`() {
        val decision = DeviceAccessPolicy.evaluate(
            ambiguous, OperationCategory.HARDWARE_STATE_WRITE, capabilityId = "anc",
        )
        assertFalse(decision.allowed)
        assertEquals(DenialReason.AMBIGUOUS_IDENTITY_WRITE_DENIED, decision.reason)
    }

    @Test
    fun `unsupported devices cannot execute proprietary writes`() {
        val decision = DeviceAccessPolicy.evaluate(
            unsupported, OperationCategory.HARDWARE_STATE_WRITE, capabilityId = "anc",
        )
        assertFalse(decision.allowed)
        assertEquals(DenialReason.UNSUPPORTED_DEVICE_WRITE_DENIED, decision.reason)
    }

    @Test
    fun `known but unverified protocols remain restricted`() {
        val decision = DeviceAccessPolicy.evaluate(
            unverified, OperationCategory.HARDWARE_STATE_WRITE, capabilityId = "anc",
        )
        assertFalse(decision.allowed)
        assertEquals(DenialReason.UNVERIFIED_PROTOCOL_WRITE_DENIED, decision.reason)
        assertTrue(decision.missingEvidence.isNotEmpty())
    }

    @Test
    fun `read-only capabilities cannot be written`() {
        // Even on a supported device, an unverified write path denies.
        val decision = DeviceAccessPolicy.evaluate(
            supported, OperationCategory.HARDWARE_STATE_WRITE,
            capabilityId = "anc", capabilityWriteVerified = false,
        )
        assertFalse(decision.allowed)
        assertEquals(DenialReason.MISSING_WRITE_PERMISSION, decision.reason)
    }

    @Test
    fun `arbitrary raw writes are denied`() {
        val decision = DeviceAccessPolicy.evaluate(
            supported, OperationCategory.RAW_TRANSPORT_WRITE,
            capabilityWriteVerified = true,
        )
        assertFalse(decision.allowed)
        assertEquals(DenialReason.RAW_WRITE_DENIED, decision.reason)
    }

    @Test
    fun `configuration reset denied`() {
        val decision = DeviceAccessPolicy.evaluate(
            supported, OperationCategory.CONFIGURATION_RESET,
            capabilityWriteVerified = true,
        )
        assertFalse(decision.allowed)
        assertEquals(DenialReason.CONFIGURATION_RESET_DENIED, decision.reason)
    }

    @Test
    fun `firmware updates denied`() {
        val decision = DeviceAccessPolicy.evaluate(
            supported, OperationCategory.FIRMWARE_UPDATE,
            capabilityWriteVerified = true,
        )
        assertFalse(decision.allowed)
        assertEquals(DenialReason.FIRMWARE_UPDATE_DENIED, decision.reason)
    }

    @Test
    fun `firmware incompatibility denies`() {
        val decision = DeviceAccessPolicy.evaluate(
            supported, OperationCategory.HARDWARE_STATE_WRITE,
            capabilityId = "anc", capabilityWriteVerified = true,
            firmwareCompatible = false,
        )
        assertFalse(decision.allowed)
        assertEquals(DenialReason.FIRMWARE_INCOMPATIBLE, decision.reason)
    }

    @Test
    fun `stale evidence denies`() {
        val decision = DeviceAccessPolicy.evaluate(
            supported, OperationCategory.HARDWARE_STATE_READ, evidenceFresh = false,
        )
        assertFalse(decision.allowed)
        assertEquals(DenialReason.STALE_EVIDENCE, decision.reason)
    }

    @Test
    fun `a read permission does not grant write`() {
        val read = DeviceAccessPolicy.evaluate(
            supported, OperationCategory.HARDWARE_STATE_READ, capabilityId = "anc",
        )
        assertTrue(read.allowed)
        val write = DeviceAccessPolicy.evaluate(
            supported, OperationCategory.HARDWARE_STATE_WRITE, capabilityId = "anc",
        )
        assertFalse(write.allowed)
    }

    @Test
    fun `observations allowed for unknown devices`() {
        val decision = DeviceAccessPolicy.evaluate(
            unknown, OperationCategory.CONNECTION_OBSERVATION,
        )
        assertTrue(decision.allowed)
        assertTrue(decision.isReadOnly)
    }

    @Test
    fun `denied operation returns typed reason`() {
        val decision = DeviceAccessPolicy.evaluate(
            unknown, OperationCategory.HARDWARE_STATE_WRITE, capabilityId = "anc",
        )
        assertFalse(decision.allowed)
        assertEquals(DeviceClassification.UNKNOWN_DEVICE, decision.classification)
        assertEquals("anc", decision.capabilityId)
        assertTrue(decision.reEvaluateAfterChange)
    }

    @Test
    fun `supported device with verified write path allowed`() {
        val decision = DeviceAccessPolicy.evaluate(
            supported, OperationCategory.HARDWARE_STATE_WRITE,
            capabilityId = "anc", capabilityWriteVerified = true,
        )
        assertTrue(decision.allowed)
        assertEquals(DenialReason.ALLOWED, decision.reason)
    }
}
