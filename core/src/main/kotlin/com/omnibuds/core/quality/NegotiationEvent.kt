package com.omnibuds.core.quality

import com.omnibuds.core.audio.AudioTransportKind
import com.omnibuds.core.audio.Codec
import com.omnibuds.core.audio.CodecEvidence
import com.omnibuds.core.device.DeviceIdentity

/**
 * Immutable events for meaningful audio-state transitions.
 *
 * Phase 13 (OB-P13-REQ-005): events represent externally relevant changes —
 * not every trivial internal operation. Each event carries the device, a
 * timestamp, and evidence. Events are values; the engine derives them from
 * observed state transitions.
 */
sealed interface NegotiationEvent {

    val device: DeviceIdentity
    val timestampMillis: Long
    val evidence: CodecEvidence

    /** An audio transport was detected for the device. */
    data class AudioTransportDetected(
        override val device: DeviceIdentity,
        override val timestampMillis: Long,
        override val evidence: CodecEvidence,
        val transport: AudioTransportKind,
    ) : NegotiationEvent

    /** An audio device became available. */
    data class AudioDeviceAvailable(
        override val device: DeviceIdentity,
        override val timestampMillis: Long,
        override val evidence: CodecEvidence,
    ) : NegotiationEvent

    /** The audio route changed. */
    data class AudioRouteChanged(
        override val device: DeviceIdentity,
        override val timestampMillis: Long,
        override val evidence: CodecEvidence,
        val routeActive: Boolean,
    ) : NegotiationEvent

    /** Codec negotiation was observed starting. */
    data class CodecNegotiationStarted(
        override val device: DeviceIdentity,
        override val timestampMillis: Long,
        override val evidence: CodecEvidence,
    ) : NegotiationEvent

    /** Codec negotiation completed with a result. */
    data class CodecNegotiationCompleted(
        override val device: DeviceIdentity,
        override val timestampMillis: Long,
        override val evidence: CodecEvidence,
        val negotiatedCodec: Codec,
        val success: Boolean,
    ) : NegotiationEvent

    /** The active codec changed meaningfully. */
    data class CodecChanged(
        override val device: DeviceIdentity,
        override val timestampMillis: Long,
        override val evidence: CodecEvidence,
        val previousCodec: Codec,
        val newCodec: Codec,
    ) : NegotiationEvent

    /** Codec parameters changed meaningfully. */
    data class CodecParametersChanged(
        override val device: DeviceIdentity,
        override val timestampMillis: Long,
        override val evidence: CodecEvidence,
        val codec: Codec,
        val description: String,
    ) : NegotiationEvent

    /** Audio became active on the route. */
    data class AudioBecameActive(
        override val device: DeviceIdentity,
        override val timestampMillis: Long,
        override val evidence: CodecEvidence,
    ) : NegotiationEvent

    /** Audio became inactive on the route. */
    data class AudioBecameInactive(
        override val device: DeviceIdentity,
        override val timestampMillis: Long,
        override val evidence: CodecEvidence,
    ) : NegotiationEvent

    /** The audio device disconnected. */
    data class AudioDeviceDisconnected(
        override val device: DeviceIdentity,
        override val timestampMillis: Long,
        override val evidence: CodecEvidence,
    ) : NegotiationEvent

    /** The audio state became stale. */
    data class AudioStateBecameStale(
        override val device: DeviceIdentity,
        override val timestampMillis: Long,
        override val evidence: CodecEvidence,
        val reason: String,
    ) : NegotiationEvent

    /** Negotiation failed. */
    data class NegotiationFailed(
        override val device: DeviceIdentity,
        override val timestampMillis: Long,
        override val evidence: CodecEvidence,
        val reason: String,
    ) : NegotiationEvent
}
