package com.omnibuds.core.codec

import com.omnibuds.core.audio.CodecCapability
import com.omnibuds.core.device.DeviceIdentity
import kotlinx.coroutines.flow.Flow

/**
 * The port the [CodecCapabilityEngine] reads codec observations through.
 *
 * The platform adapter answers this port; the engine never touches framework
 * classes. Two read shapes because capabilities and runtime state fail
 * independently: a source that can list supported codecs but cannot observe
 * the active one is honest, not broken.
 *
 * Production implementations report only information supported by real
 * platform evidence (Phase 11 RULE 16). Tests use deterministic fakes.
 */
interface CodecObservationSource {
    /**
     * Read the codec capabilities for [device]: which codecs are known and at
     * which [com.omnibuds.core.audio.CodecState] rung, with evidence,
     * observability, and metadata where the platform exposes them.
     */
    suspend fun readCapabilities(device: DeviceIdentity): List<CodecCapability>

    /**
     * Read the current runtime codec state for [device], or null when the
     * platform exposes nothing observable right now. Null is "unobserved",
     * never "idle" or "unsupported".
     */
    suspend fun readRuntimeState(device: DeviceIdentity): CodecRuntimeState?

    /**
     * Observe runtime state changes for [device]. The flow completes when
     * observation stops; a failing flow must not kill the engine (the engine
     * records a diagnostic and keeps the last good snapshot).
     */
    fun observeRuntimeStates(device: DeviceIdentity): Flow<CodecRuntimeState>
}
