package com.omnibuds.core.processing

import com.omnibuds.core.audio.AudioTransportKind
import com.omnibuds.core.audio.CodecEvidence
import com.omnibuds.core.audio.CodecEvidenceSource
import com.omnibuds.core.audio.EvidenceConfidence
import com.omnibuds.core.state.CapabilityState
import com.omnibuds.core.capability.CoreFeature
import com.omnibuds.core.capability.FeatureCapability
import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.device.DeviceIdentity
import com.omnibuds.core.state.VerificationLevel
import com.omnibuds.core.common.TransportKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase 15 (§19): processing-domain resolver tests.
 */
class ProcessingDomainResolverTest {

    private val device = DeviceIdentity(displayName = "Test Buds")

    private fun evidence(confidence: EvidenceConfidence) = CodecEvidence(
        source = CodecEvidenceSource.ANDROID_FRAMEWORK,
        confidence = confidence,
        observedAtMillis = 1000L,
    )

    private fun capability(
        feature: FeatureId = CoreFeature.ANC,
        verification: VerificationLevel = VerificationLevel.IMPLEMENTED,
        protocolId: String? = "test-protocol",
        writable: Boolean = true,
    ) = FeatureCapability(
        feature = feature,
        state = CapabilityState.SUPPORTED_VOLATILE,
        readable = true,
        writable = writable,
        transport = TransportKind.CLASSIC_BLUETOOTH,
        protocolId = protocolId,
        requiresConnection = true,
        verification = verification,
    )

    @Test
    fun `unknown evidence yields unknown domain`() {
        val cap = capability().copy(
            // Simulate no evidence via low verification
            verification = VerificationLevel.INFERRED,
        )
        val r = ProcessingDomainResolver.resolve(cap, AudioTransportKind.CLASSIC_A2DP)
        // INFERRED < IMPLEMENTED → UNKNOWN
        assertEquals(AudioProcessingDomain.UNKNOWN, r.domain)
    }

    @Test
    fun `verified protocol establishes device domain`() {
        val cap = capability(
            verification = VerificationLevel.HARDWARE_VERIFIED,
            protocolId = "vendor-protocol",
        )
        val r = ProcessingDomainResolver.resolve(cap, AudioTransportKind.CLASSIC_A2DP)
        assertEquals(AudioProcessingDomain.DEVICE_HARDWARE_DSP, r.domain)
        assertEquals(false, r.ambiguous)
    }

    @Test
    fun `missing protocol yields ambiguous unknown`() {
        val cap = capability(
            verification = VerificationLevel.IMPLEMENTED,
            protocolId = null,
        )
        val r = ProcessingDomainResolver.resolve(cap, AudioTransportKind.CLASSIC_A2DP)
        assertEquals(AudioProcessingDomain.UNKNOWN, r.domain)
        assertEquals(true, r.ambiguous)
    }

    @Test
    fun `control denied for read-only capability`() {
        val cap = FeatureCapability(
            feature = CoreFeature.ANC,
            state = CapabilityState.READ_ONLY,
            readable = true,
            writable = false,
            transport = TransportKind.CLASSIC_BLUETOOTH,
            protocolId = "vendor-protocol",
            requiresConnection = true,
            verification = VerificationLevel.HARDWARE_VERIFIED,
        )
        val resolution = ProcessingDomainResolver.resolve(cap, null)
        val eligibility = ProcessingDomainResolver.canControl(
            cap, AudioProcessingDomain.DEVICE_HARDWARE_DSP, resolution,
        )
        assertTrue(eligibility is ControlEligibility.Denied)
    }

    @Test
    fun `control denied on domain mismatch`() {
        val cap = capability(
            verification = VerificationLevel.HARDWARE_VERIFIED,
            protocolId = "vendor-protocol",
        )
        val resolution = ProcessingDomainResolver.resolve(cap, null)
        // Request Android platform but resolved to hardware → denied
        val eligibility = ProcessingDomainResolver.canControl(
            cap, AudioProcessingDomain.ANDROID_PLATFORM, resolution,
        )
        assertTrue(eligibility is ControlEligibility.Denied)
    }

    @Test
    fun `control allowed on matching domain`() {
        val cap = capability(
            verification = VerificationLevel.HARDWARE_VERIFIED,
            protocolId = "vendor-protocol",
        )
        val resolution = ProcessingDomainResolver.resolve(cap, null)
        val eligibility = ProcessingDomainResolver.canControl(
            cap, AudioProcessingDomain.DEVICE_HARDWARE_DSP, resolution,
        )
        assertTrue(eligibility is ControlEligibility.Allowed)
    }

    @Test
    fun `all domains are distinct`() {
        assertEquals(5, AudioProcessingDomain.entries.size)
    }
}
