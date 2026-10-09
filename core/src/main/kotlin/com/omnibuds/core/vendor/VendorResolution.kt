package com.omnibuds.core.vendor

import com.omnibuds.core.device.DeviceFingerprint

/**
 * Typed vendor resolution outcomes with reasons.
 *
 * Phase 40: the registry reports *why* a device resolved the way it
 * did, so diagnostics can explain matching without guessing.
 */
sealed interface VendorResolution {
    /** Exactly one adapter matched with sufficient evidence. */
    data class ExactMatch(
        val adapter: VendorAdapter,
        val evidence: String,
    ) : VendorResolution

    /** A supported family matched but confidence is limited. */
    data class FamilyMatch(
        val adapter: VendorAdapter,
        val reason: String,
    ) : VendorResolution

    /** Identity evidence is insufficient or conflicting. */
    data class Ambiguous(val reason: String) : VendorResolution

    /** The device is known and explicitly unsupported. */
    data class KnownUnsupported(val reason: String) : VendorResolution

    /** No adapter matched; unknown-device handling applies. */
    data object UnknownDevice : VendorResolution

    /** The firmware or protocol version is incompatible. */
    data class IncompatibleVersion(val reason: String) : VendorResolution
}

/**
 * Resolves devices to vendor integrations with typed outcomes.
 *
 * Wraps the deterministic [VendorRegistry] and adds diagnostics.
 * Ambiguous matches never resolve to an adapter.
 */
class VendorResolver(
    private val registry: VendorRegistry,
) {
    /**
     * Resolve a device fingerprint to a typed outcome.
     */
    suspend fun resolve(fingerprint: DeviceFingerprint): VendorResolution {
        if (registry.isAmbiguous(fingerprint)) {
            return VendorResolution.Ambiguous(
                "one or more adapters reported ambiguous identity",
            )
        }
        val adapter = registry.resolve(fingerprint)
        return if (adapter != null) {
            VendorResolution.ExactMatch(
                adapter,
                "exactly one adapter matched with sufficient evidence",
            )
        } else {
            VendorResolution.UnknownDevice
        }
    }
}
