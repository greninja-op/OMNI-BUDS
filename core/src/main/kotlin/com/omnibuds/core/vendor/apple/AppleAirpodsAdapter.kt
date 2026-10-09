package com.omnibuds.core.vendor.apple

import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.device.DeviceFingerprint
import com.omnibuds.core.protocol.ProtocolDefinition
import com.omnibuds.core.state.VerificationLevel
import com.omnibuds.core.vendor.MatchResult
import com.omnibuds.core.vendor.VendorAdapter
import com.omnibuds.core.vendor.VendorEvidence

/**
 * Conservative AirPods vendor adapter (Phase 42).
 *
 * Scope is deliberately narrow:
 * - Family-level identity from standard Bluetooth evidence
 *   (Apple company ID + audio class-of-device, loaded as data
 *   from [AirpodsIdentityEvidence]).
 * - Read-only observations of Android-exposed metadata only.
 * - No Apple-proprietary (AAP) protocol implementation.
 * - All Apple-specific controls are UNSUPPORTED.
 *
 * Why no AAP: the reverse-engineered Apple Accessory Protocol
 * requires spoofing Apple's BLE device identity and typically root
 * on Android (see Phase 42 research register). Spoofing a vendor
 * identity to bypass an access control is a hard stop condition.
 * Available third-party sources are GPL-encumbered and the protocol
 * is firmware-fragile. Nothing here invents packet formats.
 */
class AppleAirpodsAdapter(
    private val identityEvidence: AirpodsIdentityEvidence? =
        AirpodsIdentityEvidence.loadDefault(),
) : VendorAdapter {

    override val adapterId: String = ADAPTER_ID
    override val displayName: String = "Apple AirPods (family, read-only)"

    override fun match(fingerprint: DeviceFingerprint): MatchResult {
        // Without identity evidence the adapter cannot identify
        // anything; fail closed rather than guess.
        val evidence = identityEvidence ?: return MatchResult.NotMatched
        if (fingerprint.isEntirelyUnobserved) return MatchResult.NotMatched
        val hasAppleCompanyId = fingerprint.manufacturerData.any {
            it.companyId == evidence.appleCompanyId
        }
        if (!hasAppleCompanyId) return MatchResult.NotMatched

        val deviceClass = fingerprint.deviceClass
        val isAudioClass = deviceClass != null &&
            (deviceClass and evidence.codMajorMask) ==
            evidence.codMajorAudioVideo

        return if (isAudioClass) {
            MatchResult.Matched(
                adapterId,
                "Apple company ID with audio class-of-device; " +
                    "AirPods family, exact model unresolved",
            )
        } else {
            MatchResult.Ambiguous(
                "Apple manufacturer data present but class-of-device is " +
                    "not audio; model family unresolved, writes disabled",
            )
        }
    }

    override val protocol: ProtocolDefinition = ProtocolDefinition(
        protocolId = "vendor.apple.airpods.metadata",
        displayName = "AirPods standard-Bluetooth metadata",
        vendor = "apple",
        // Standard Android Bluetooth stack behavior; no proprietary channel.
        transport = TransportKind.CLASSIC_BLUETOOTH,
        version = null,
        commands = emptyMap(),
        responses = emptyMap(),
        capabilityMappings = emptyMap(),
        confidence = VerificationLevel.INFERRED,
    )

    override val supportedFirmware: Set<String>? = null

    companion object {
        const val ADAPTER_ID = "vendor.apple.airpods"

        /** Evidence backing this adapter's claims. */
        val evidence: List<VendorEvidence> = listOf(
            VendorEvidence(
                integrationId = ADAPTER_ID,
                source = "Apple support: AirPods with non-Apple devices " +
                    "(listen and talk; Siri unavailable)",
                scope = "Standard Bluetooth audio/call behavior",
                level = VerificationLevel.INFERRED,
                reviewedDate = "2026-10-09",
            ),
            VendorEvidence(
                integrationId = ADAPTER_ID,
                source = "Third-party compatibility reporting " +
                    "(multiple outlets, 2021-2026)",
                scope = "ANC/Transparency via on-device controls; " +
                    "battery pop-up, ear detection, spatial audio, " +
                    "firmware updates unavailable on Android",
                level = VerificationLevel.INFERRED,
                reviewedDate = "2026-10-09",
            ),
            VendorEvidence(
                integrationId = ADAPTER_ID,
                source = "LibrePods project (GPLv3, reverse-engineered " +
                    "AAP; Android requires root/device-ID spoofing)",
                scope = "Why the Apple-proprietary control protocol is " +
                    "NOT implemented: access-control bypass + licensing",
                level = VerificationLevel.INFERRED,
                reviewedDate = "2026-10-09",
            ),
        )
    }
}
