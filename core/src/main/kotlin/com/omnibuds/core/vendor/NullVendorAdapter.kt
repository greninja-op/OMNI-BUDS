package com.omnibuds.core.vendor

import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.device.DeviceFingerprint
import com.omnibuds.core.protocol.ProtocolDefinition

/**
 * Reference adapter that matches nothing.
 *
 * Phase 19: demonstrates the [VendorAdapter] contract without claiming
 * support for any real device. This is the honest default — no vendor
 * protocol is implemented until evidence justifies it.
 *
 * This adapter exists so the registry, matching, and fallback paths have
 * a concrete implementation to test against.
 */
class NullVendorAdapter : VendorAdapter {
    override val adapterId: String = "omnibuds.null"
    override val displayName: String = "Null Adapter (matches nothing)"

    override fun match(fingerprint: DeviceFingerprint): MatchResult =
        MatchResult.NotMatched

    override val protocol: ProtocolDefinition = ProtocolDefinition(
        protocolId = "omnibuds.null",
        displayName = "Null Protocol",
        vendor = null,
        // VENDOR_SPECIFIC: this "protocol" defines no operations, so no
        // specific channel applies; the value satisfies the
        // never-unknown transport invariant without claiming a real transport.
        transport = TransportKind.VENDOR_SPECIFIC,
        version = null,
        commands = emptyMap(),
        responses = emptyMap(),
        capabilityMappings = emptyMap(),
        confidence = com.omnibuds.core.state.VerificationLevel.INFERRED,
    )

    override val supportedFirmware: Set<String>? = null
}
