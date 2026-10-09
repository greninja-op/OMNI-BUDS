package com.omnibuds.core.validation

import com.omnibuds.core.audio.AudioTransportKind
import com.omnibuds.core.audio.Codec
import com.omnibuds.core.audio.CodecFreshness
import com.omnibuds.core.audio.CodecState
import com.omnibuds.core.device.DeviceIdentity
import com.omnibuds.core.quality.AdaptiveState
import com.omnibuds.core.quality.AudioQualityState
import com.omnibuds.core.quality.NegotiationState

/**
 * One coherent observation bundle for a single device and session
 * generation.
 *
 * Phase 14: rules evaluate this, never raw engine internals. The bundle is
 * assembled by [AudioPathValidationEngine] from the Phase 10/11/12/13
 * observations under a single lock acquisition, so rules see a consistent
 * cut. [sessionGeneration] gates obsolete events: a rule input whose
 * generation does not match the engine's current generation is discarded,
 * never evaluated.
 *
 * All fields are nullable-or-unknown by design: a rule that needs a missing
 * fact returns INCONCLUSIVE/NOT_OBSERVABLE rather than guessing.
 */
data class ValidationInput(
    val device: DeviceIdentity,
    val sessionGeneration: Long,
    val timestampMillis: Long,
    // --- Transport / route (Phase 10) ---
    val transport: AudioTransportKind?,
    val transportConnected: Boolean?,
    val routeActive: Boolean?,
    val audioDeviceAvailable: Boolean?,
    val isCommunicationRoute: Boolean?,
    // --- Codec (Phase 11/12) ---
    val codec: Codec?,
    val codecState: CodecState?,
    val codecFreshness: CodecFreshness?,
    val codecSupported: Boolean?,
    // --- Quality / negotiation (Phase 13) ---
    val qualityState: AudioQualityState?,
    val negotiationState: NegotiationState?,
    // --- Parameters ---
    val sampleRateHz: Int?,
    val bitDepth: Int?,
    val channelMode: String?,
    val adaptiveState: AdaptiveState?,
) {
    init {
        require(timestampMillis >= 0) { "timestampMillis must be non-negative" }
        require(sessionGeneration >= 0) { "sessionGeneration must be non-negative" }
    }
}
