package com.omnibuds.core.quality

import com.omnibuds.core.audio.AudioTransportKind
import com.omnibuds.core.audio.Codec
import com.omnibuds.core.audio.CodecEvidence
import com.omnibuds.core.codec.CodecConfiguration
import com.omnibuds.core.device.DeviceIdentity

/**
 * A bounded negotiation session.
 *
 * Phase 13 (OB-P13-REQ-006): a session scopes one negotiation attempt in time.
 * It never lives forever — disconnect terminates/invalidates it. The session
 * records what was negotiated, not what is currently active (see
 * OB-P13-REQ-004: negotiated ≠ active).
 *
 * @param sessionId caller-supplied correlation id, never minted from
 * wall-clock in core.
 */
data class NegotiationSession(
    val sessionId: String,
    val device: DeviceIdentity,
    val transport: AudioTransportKind,
    val startedAtMillis: Long,
    val completedAtMillis: Long? = null,
    val negotiatedCodec: Codec = Codec.UNKNOWN,
    val negotiatedConfiguration: CodecConfiguration? = null,
    val finalState: NegotiationState = NegotiationState.NEGOTIATING,
    val failureReason: String? = null,
    val evidence: CodecEvidence,
) {
    init {
        require(sessionId.isNotBlank()) { "sessionId must identify the session" }
        require(startedAtMillis >= 0) { "startedAtMillis must be non-negative" }
        require(completedAtMillis == null || completedAtMillis >= startedAtMillis) {
            "completedAtMillis must not precede startedAtMillis"
        }
    }

    /** True while the session is still in flight. */
    val isActive: Boolean
        get() = completedAtMillis == null &&
            finalState != NegotiationState.DISCONNECTED &&
            finalState != NegotiationState.FAILED

    companion object {
        fun begin(
            sessionId: String,
            device: DeviceIdentity,
            transport: AudioTransportKind,
            nowMillis: Long,
            evidence: CodecEvidence,
        ): NegotiationSession = NegotiationSession(
            sessionId = sessionId,
            device = device,
            transport = transport,
            startedAtMillis = nowMillis,
            evidence = evidence,
        )
    }
}
