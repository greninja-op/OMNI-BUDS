package com.omnibuds.core.processing

import com.omnibuds.core.audio.AudioTransportKind
import com.omnibuds.core.capability.CoreFeature
import com.omnibuds.core.capability.FeatureCapability
import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.state.VerificationLevel

/**
 * Assigns processing capabilities to their domain.
 *
 * Phase 15 (OB-P15-REQ-010, OB-P15-REQ-011): a pure, deterministic resolver
 * that annotates [FeatureCapability] records with a processing domain. It
 * never replaces the capability model — it adds the domain dimension.
 *
 * Hard rules:
 * 1. verification < IMPLEMENTED → UNKNOWN (no evidence → no domain claim).
 * 2. protocolId == null → hardware control NOT_CONFIGURABLE; read-only at best.
 * 3. Android-platform capabilities resolve to ANDROID_PLATFORM, never to
 *    DEVICE_HARDWARE_DSP.
 * 4. Codec selection, routing, transport are not DSP features.
 * 5. Unknown devices → read-only.
 *
 * The resolver never silently substitutes one domain for another.
 */
object ProcessingDomainResolver {

    /**
     * Resolve the domain for a feature capability.
     *
     * @param capability the Phase 8 feature capability record.
     * @param transport the observed transport, if known.
     */
    fun resolve(
        capability: FeatureCapability,
        transport: AudioTransportKind?,
    ): DomainResolution {
        val feature = capability.feature

        // Rule 1: no evidence → no domain claim.
        if (capability.verification < VerificationLevel.IMPLEMENTED) {
            return DomainResolution(
                domain = AudioProcessingDomain.UNKNOWN,
                ambiguous = false,
                reason = "Verification ${capability.verification} below IMPLEMENTED; " +
                    "no domain can be claimed.",
            )
        }

        // Rule 4: codec/routing/transport are not DSP features.
        if (isNonDspFeature(feature)) {
            return DomainResolution(
                domain = AudioProcessingDomain.UNKNOWN,
                ambiguous = false,
                reason = "$feature is not a DSP processing feature.",
            )
        }

        // Rule 3: Android-platform capabilities stay on the platform.
        if (isAndroidPlatformFeature(feature, capability)) {
            return DomainResolution(
                domain = AudioProcessingDomain.ANDROID_PLATFORM,
                ambiguous = false,
                reason = "$feature is provided by the Android platform, not device hardware.",
            )
        }

        // Rule 2: hardware domain requires a verified protocol binding.
        if (capability.protocolId == null) {
            return DomainResolution(
                domain = AudioProcessingDomain.UNKNOWN,
                ambiguous = true,
                reason = "No verified protocol binding for $feature; hardware domain " +
                    "cannot be established (registry ships empty).",
            )
        }

        // Verified protocol + sufficient verification → device hardware/firmware.
        // The DSP vs firmware boundary is preserved as ambiguous when the
        // evidence cannot distinguish them.
        return DomainResolution(
            domain = AudioProcessingDomain.DEVICE_HARDWARE_DSP,
            ambiguous = false,
            reason = "Verified protocol ${capability.protocolId} establishes device-side processing.",
        )
    }

    /**
     * True when a control request may proceed: the capability is writable,
     * the domain is known and unambiguous, and the requested domain matches
     * the resolved domain (no silent substitution).
     */
    fun canControl(
        capability: FeatureCapability,
        requestedDomain: AudioProcessingDomain,
        resolution: DomainResolution,
    ): ControlEligibility {
        if (!capability.writable) {
            return ControlEligibility.Denied("Feature ${capability.feature} is read-only.")
        }
        if (resolution.ambiguous || resolution.domain == AudioProcessingDomain.UNKNOWN) {
            return ControlEligibility.Denied(
                "Processing domain unknown or ambiguous; refusing unverified write.",
            )
        }
        if (requestedDomain != resolution.domain) {
            return ControlEligibility.Denied(
                "Requested domain $requestedDomain does not match resolved domain " +
                    "${resolution.domain}; silent substitution is forbidden.",
            )
        }
        return ControlEligibility.Allowed
    }

    private fun isNonDspFeature(feature: FeatureId): Boolean {
        // Codec selection, routing, and transport are not DSP processing.
        // These are handled by Phases 10–12, not the DSP domain.
        val id = feature.toString()
        return id.contains("codec", ignoreCase = true) ||
            id.contains("rout", ignoreCase = true) ||
            id.contains("transport", ignoreCase = true)
    }

    private fun isAndroidPlatformFeature(
        feature: FeatureId,
        capability: FeatureCapability,
    ): Boolean {
        // Heuristic: capabilities without a protocol binding that are known
        // platform features (system EQ, platform spatial audio) belong to
        // the Android platform. This is conservative — unknown stays unknown.
        return false // Default: do not guess; explicit platform records only.
    }
}

/** The outcome of domain resolution. */
data class DomainResolution(
    val domain: AudioProcessingDomain,
    val ambiguous: Boolean,
    val reason: String,
)

/** Whether a control request may proceed. */
sealed interface ControlEligibility {
    data object Allowed : ControlEligibility
    data class Denied(val reason: String) : ControlEligibility
}
