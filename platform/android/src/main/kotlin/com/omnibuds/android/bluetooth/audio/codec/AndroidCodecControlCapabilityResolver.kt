package com.omnibuds.android.bluetooth.audio.codec

import android.os.Build
import com.omnibuds.core.audio.Codec
import com.omnibuds.core.audio.CodecEvidence
import com.omnibuds.core.audio.CodecEvidenceSource
import com.omnibuds.core.audio.EvidenceConfidence
import com.omnibuds.core.codec.CodecControlCapability
import com.omnibuds.core.codec.CodecControlCapabilityResolver
import com.omnibuds.core.device.DeviceIdentity

/**
 * Resolves codec control capabilities on Android's public API surface.
 *
 * Phase 12 (OB-P12-REQ-014, OB-P12-REQ-034): the honest matrix.
 *
 * - observable: partial — the Phase 11 observation source reports local
 *   platform capabilities where `getSupportedCodecTypes()` is reachable
 *   (API 35+, and in practice requires BLUETOOTH_PRIVILEGED); the active
 *   codec is never observable.
 * - supported: as reported by the Phase 11 observation (local platform
 *   support; per-device support is UNKNOWN).
 * - selectable: false — no public API selects a codec.
 * - configurable: false — no public API configures codec parameters.
 * - verifiable: false — with no observable active codec, no change can be
 *   verified through the platform.
 *
 * Every `false` carries PLATFORM_LIMITATION evidence so the reason is
 * attributable, not a silent default.
 */
class AndroidCodecControlCapabilityResolver(
    private val observationSource: AndroidCodecObservationSource,
    private val apiLevel: Int = Build.VERSION.SDK_INT,
) : CodecControlCapabilityResolver {

    override suspend fun resolve(device: DeviceIdentity, codec: Codec): CodecControlCapability {
        val capabilities = observationSource.readCapabilities(device)
        val reported = capabilities.firstOrNull { it.codec == codec }
        val supported = reported?.state == com.omnibuds.core.audio.CodecState.SUPPORTED ||
            reported?.state == com.omnibuds.core.audio.CodecState.AVAILABLE

        val platformLimitation = CodecEvidence(
            source = CodecEvidenceSource.ANDROID_FRAMEWORK,
            confidence = EvidenceConfidence.OBSERVED,
            detail = "No public Android API (API $apiLevel) exposes codec selection, " +
                "configuration, or active-codec observation to third-party apps.",
        )

        return CodecControlCapability(
            codec = codec,
            observable = reported != null,
            supported = supported,
            selectable = false,
            configurable = false,
            verifiable = false,
            evidence = platformLimitation,
        )
    }
}
