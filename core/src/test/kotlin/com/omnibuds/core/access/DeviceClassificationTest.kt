package com.omnibuds.core.access

import com.omnibuds.core.device.DeviceFingerprint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Phase 21: device classification tests.
 */
class DeviceClassificationTest {

    private val emptyFingerprint = DeviceFingerprint()

    @Test
    fun `unknown device for empty fingerprint`() {
        val state = DeviceClassifier.classify(
            fingerprint = emptyFingerprint,
            matchedAdapters = emptyList(),
            ambiguous = false,
            protocolVerified = false,
        )
        assertEquals(DeviceClassification.UNKNOWN_DEVICE, state.classification)
        assertFalse(state.writeAuthorized)
        assertFalse(state.protocolVerified)
    }

    @Test
    fun `partially identified with manufacturer data`() {
        val fp = DeviceFingerprint(
            manufacturerData = listOf(
                com.omnibuds.core.device.ManufacturerDataEntry(companyId = 0x004C, dataHex = "0102"),
            ),
        )
        val state = DeviceClassifier.classify(fp, emptyList(), false, false)
        assertEquals(DeviceClassification.PARTIALLY_IDENTIFIED, state.classification)
        assertFalse(state.writeAuthorized)
    }

    @Test
    fun `ambiguous identity preserved`() {
        val fp = DeviceFingerprint(deviceClass = 2368)
        val state = DeviceClassifier.classify(fp, listOf("adapter-a"), true, false)
        assertEquals(DeviceClassification.AMBIGUOUS_IDENTITY, state.classification)
        assertFalse(state.writeAuthorized)
    }

    @Test
    fun `multiple matches treated as ambiguous`() {
        val fp = DeviceFingerprint(deviceClass = 2368)
        val state = DeviceClassifier.classify(fp, listOf("a", "b"), false, false)
        assertEquals(DeviceClassification.AMBIGUOUS_IDENTITY, state.classification)
    }

    @Test
    fun `known protocol unverified`() {
        val fp = DeviceFingerprint(deviceClass = 2368)
        val state = DeviceClassifier.classify(fp, listOf("a"), false, false)
        assertEquals(DeviceClassification.KNOWN_PROTOCOL_UNVERIFIED, state.classification)
        assertFalse(state.writeAuthorized)
    }

    @Test
    fun `known device supported`() {
        val fp = DeviceFingerprint(deviceClass = 2368)
        val state = DeviceClassifier.classify(fp, listOf("a"), false, true)
        assertEquals(DeviceClassification.KNOWN_DEVICE_SUPPORTED, state.classification)
        assertTrue(state.protocolVerified)
    }

    @Test
    fun `deterministic classification`() {
        val fp = DeviceFingerprint(deviceClass = 2368)
        val s1 = DeviceClassifier.classify(fp, listOf("a"), false, true)
        val s2 = DeviceClassifier.classify(fp, listOf("a"), false, true)
        assertEquals(s1, s2)
    }

    @Test
    fun `write authorization requires supported classification`() {
        assertFailsWith<IllegalArgumentException> {
            DeviceAccessState(
                classification = DeviceClassification.UNKNOWN_DEVICE,
                identityConfidence = IdentityConfidence.NONE,
                protocolVerified = false,
                writeAuthorized = true,
            )
        }
    }

    @Test
    fun `identity confidence separate from classification`() {
        val state = DeviceAccessState.unknown()
        assertEquals(DeviceClassification.UNKNOWN_DEVICE, state.classification)
        assertEquals(IdentityConfidence.NONE, state.identityConfidence)
    }
}
