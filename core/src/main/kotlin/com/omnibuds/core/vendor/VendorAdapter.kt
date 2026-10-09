package com.omnibuds.core.vendor

import com.omnibuds.core.device.DeviceFingerprint
import com.omnibuds.core.protocol.ProtocolDefinition

/**
 * A vendor-specific protocol adapter.
 *
 * Phase 19: the integration point for evidence-backed vendor protocols.
 * Adapters declare what they support; unsupported operations are never
 * invented. When no vendor adapter matches, the unknown-device fallback
 * applies (existing behavior, unchanged).
 */
interface VendorAdapter {

    /** Stable adapter identifier, e.g. `bose.bmap`. */
    val adapterId: String

    /** Human-facing name; presentation only. */
    val displayName: String

    /**
     * Determine whether this adapter supports the given device.
     * Must be deterministic and specific. Ambiguous → [MatchResult.Ambiguous].
     */
    fun match(fingerprint: DeviceFingerprint): MatchResult

    /** The protocol definition this adapter implements. */
    val protocol: ProtocolDefinition

    /**
     * Firmware versions this adapter is verified against, or null when
     * the adapter does not depend on firmware version.
     */
    val supportedFirmware: Set<String>?
}

/** The result of attempting to match a device to a vendor adapter. */
sealed interface MatchResult {
    /** Definite match; the adapter may be used. */
    data class Matched(val adapterId: String, val evidence: String) : MatchResult

    /** No match; this adapter does not support the device. */
    data object NotMatched : MatchResult

    /**
     * Ambiguous: the device might be supported but identity is insufficient.
     * Write operations must remain disabled.
     */
    data class Ambiguous(val reason: String) : MatchResult
}
