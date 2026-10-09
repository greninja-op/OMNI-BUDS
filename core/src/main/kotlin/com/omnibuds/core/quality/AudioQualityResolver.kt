package com.omnibuds.core.quality

import com.omnibuds.core.audio.AudioTransportKind
import com.omnibuds.core.audio.ChannelMode
import com.omnibuds.core.audio.Codec
import com.omnibuds.core.audio.CodecBitrate
import com.omnibuds.core.audio.CodecEvidence
import com.omnibuds.core.audio.CodecEvidenceSource
import com.omnibuds.core.audio.CodecFreshness
import com.omnibuds.core.audio.CodecObservability
import com.omnibuds.core.audio.CodecState
import com.omnibuds.core.audio.EvidenceConfidence
import com.omnibuds.core.audio.QualityMode
import com.omnibuds.core.codec.CodecControlState
import com.omnibuds.core.codec.CodecSnapshot
import com.omnibuds.core.device.DeviceIdentity

/**
 * Deterministic resolver: observations in, [AudioQualityState] out.
 *
 * Phase 13 (OB-P13-REQ-018, OB-P13-REQ-020): a pure function. It never invents
 * information. On conflicting sources it applies [SourcePrecedence] and sets
 * [AudioQualityState.hasConflict] rather than silently choosing.
 *
 * Precedence applied here:
 * - Active codec: the codec snapshot's ACTIVE-rung codec wins
 *   (PLATFORM_RUNTIME_METADATA). A Phase 12 VERIFIED control state outranks it
 *   (VERIFIED_RUNTIME_OBSERVATION).
 * - Negotiated codec: the highest non-ACTIVE rung (NEGOTIATED > ENABLED >
 *   AVAILABLE > SUPPORTED) from the codec snapshot.
 * - Transport/route: the transport observation, when present.
 * - Parameters: only from OBSERVED-confidence evidence; capability-declared
 *   values populate the `supported*` fields, never the `active*` fields.
 *
 * Everything the platform cannot expose stays UNKNOWN / NOT_OBSERVABLE.
 */
object AudioQualityResolver {

    /**
     * Resolve one device's quality state from the available observations.
     *
     * @param codecSnapshot the Phase 11 per-device codec snapshot (may be null).
     * @param controlState the Phase 12 per-device control state (may be null).
     * @param transport the observed transport kind (null when unknown).
     * @param routeActive whether the audio route is live (null when unknown).
     * @param transportConnected whether the transport reports connected.
     * @param nowMillis the resolution timestamp.
     */
    fun resolve(
        device: DeviceIdentity,
        codecSnapshot: CodecSnapshot?,
        controlState: CodecControlState?,
        transport: AudioTransportKind?,
        routeActive: Boolean?,
        transportConnected: Boolean?,
        nowMillis: Long,
    ): AudioQualityState {
        val capabilities = codecSnapshot?.capabilities.orEmpty()

        // Active codec: VERIFIED control state outranks the snapshot's ACTIVE rung.
        val verifiedActive = controlState?.takeIf { it.isCurrent }?.confirmedCodec
            ?.takeIf { it != Codec.UNKNOWN }
        val snapshotActive = capabilities
            .firstOrNull { it.state == CodecState.ACTIVE }
            ?.codec
            .takeIf { it != Codec.UNKNOWN }
        val (activeCodec, activeConflict) = when {
            verifiedActive != null && snapshotActive != null && verifiedActive != snapshotActive ->
                verifiedActive to true // verified runtime observation wins; flag conflict
            verifiedActive != null -> verifiedActive to false
            else -> (snapshotActive ?: Codec.UNKNOWN) to false
        }

        // Negotiated codec: highest rung below ACTIVE.
        val rungOrder = listOf(
            CodecState.NEGOTIATED,
            CodecState.ENABLED,
            CodecState.AVAILABLE,
            CodecState.SUPPORTED,
        )
        val negotiatedCodec = rungOrder
            .firstNotNullOfOrNull { rung ->
                capabilities.firstOrNull { it.state == rung }?.codec
            }
            ?.takeIf { it != Codec.UNKNOWN }
            ?: Codec.UNKNOWN

        // Codec state: the active codec's rung, else the negotiated rung.
        val codecState = capabilities
            .firstOrNull { it.codec == activeCodec && it.codec != Codec.UNKNOWN }
            ?.state
            ?: capabilities.firstOrNull { it.codec == negotiatedCodec }?.state
            ?: CodecState.UNKNOWN

        // Parameters: only OBSERVED-confidence evidence populates active fields.
        // The codec snapshot's metadata carries values; the capability's
        // evidence carries the confidence. Capability-declared values stay in
        // the supported* fields (empty here — the snapshot does not declare
        // numeric capabilities on public APIs).
        val activeCapability = capabilities
            .firstOrNull { it.codec == activeCodec }
        val activeMeta = activeCapability
            ?.takeIf { it.evidence.confidence == EvidenceConfidence.OBSERVED }
            ?.metadata

        val observability = when {
            codecSnapshot == null -> CodecObservability.UNKNOWN
            capabilities.any { it.observability == CodecObservability.OBSERVABLE } ->
                CodecObservability.PARTIALLY_OBSERVABLE
            capabilities.all { it.observability == CodecObservability.NOT_OBSERVABLE } ->
                CodecObservability.NOT_OBSERVABLE
            else -> CodecObservability.UNKNOWN
        }

        val freshness = codecSnapshot?.runtimeState?.freshness ?: CodecFreshness.UNKNOWN

        val negotiationState = deriveNegotiationState(
            transportConnected = transportConnected,
            routeActive = routeActive,
            codecState = codecState,
            freshness = freshness,
        )

        return AudioQualityState(
            device = device,
            transport = transport ?: AudioTransportKind.UNKNOWN,
            routeActive = routeActive,
            transportConnected = transportConnected,
            negotiationState = negotiationState,
            negotiatedCodec = negotiatedCodec,
            activeCodec = activeCodec,
            codecState = codecState,
            activeSampleRateHz = activeMeta?.sampleRateHz,
            activeBitDepth = activeMeta?.bitsPerSample,
            observedBitrate = activeMeta?.bitrate ?: CodecBitrate.Unknown,
            channelMode = activeMeta?.channelMode ?: ChannelMode.UNKNOWN,
            observedQualityMode = activeMeta?.qualityMode ?: QualityMode.UNKNOWN,
            adaptiveState = when (activeMeta?.qualityMode) {
                QualityMode.ADAPTIVE -> AdaptiveState.ADAPTIVE
                QualityMode.UNKNOWN, null -> AdaptiveState.UNKNOWN
                else -> AdaptiveState.FIXED
            },
            evidence = CodecEvidence(
                source = CodecEvidenceSource.PLATFORM_CODEC_METADATA,
                confidence = if (activeCodec != Codec.UNKNOWN) {
                    EvidenceConfidence.OBSERVED
                } else {
                    EvidenceConfidence.UNKNOWN
                },
                observedAtMillis = nowMillis,
                detail = "AudioQualityResolver: transport=${transport ?: "unknown"}, " +
                    "active=$activeCodec, negotiated=$negotiatedCodec",
            ),
            observability = observability,
            freshness = freshness,
            hasConflict = activeConflict,
            timestampMillis = nowMillis,
        )
    }

    /**
     * Derive the negotiation state from observable facts.
     * This is derived, never protocol-observed: the platform exposes no
     * negotiation events.
     */
    fun deriveNegotiationState(
        transportConnected: Boolean?,
        routeActive: Boolean?,
        codecState: CodecState,
        freshness: CodecFreshness,
    ): NegotiationState = when {
        transportConnected == false -> NegotiationState.DISCONNECTED
        freshness == CodecFreshness.STALE -> NegotiationState.STALE
        codecState == CodecState.ACTIVE && routeActive == true -> NegotiationState.ACTIVE
        codecState == CodecState.NEGOTIATED -> NegotiationState.NEGOTIATED
        codecState == CodecState.ENABLED || codecState == CodecState.AVAILABLE -> NegotiationState.NEGOTIATING
        codecState == CodecState.SUPPORTED -> NegotiationState.PREPARING
        transportConnected == true -> NegotiationState.IDLE
        else -> NegotiationState.UNKNOWN
    }
}
