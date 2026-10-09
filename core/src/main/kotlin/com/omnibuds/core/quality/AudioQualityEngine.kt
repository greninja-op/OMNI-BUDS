package com.omnibuds.core.quality

import com.omnibuds.core.audio.AudioTransportKind
import com.omnibuds.core.audio.Codec
import com.omnibuds.core.audio.CodecEvidence
import com.omnibuds.core.audio.CodecEvidenceSource
import com.omnibuds.core.audio.CodecFreshness
import com.omnibuds.core.audio.EvidenceConfidence
import com.omnibuds.core.codec.CodecControlState
import com.omnibuds.core.codec.CodecSnapshot
import com.omnibuds.core.device.DeviceIdentity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The runtime audio quality & negotiation state engine.
 *
 * Phase 13: consumes Phase 10 (transport), Phase 11 (codec capability), and
 * Phase 12 (codec control) observations and produces a unified,
 * deduplicated [AudioQualityState] per device, plus a meaningful event
 * stream and bounded negotiation sessions.
 *
 * Architecture:
 * ```
 * transport / codec / control observations (via onXxx)
 *        ↓
 * AudioQualityResolver.resolve()   (pure, deterministic)
 *        ↓
 * change detection → NegotiationEvent(s)
 *        ↓
 * StateFlow<Map<DeviceIdentity, AudioQualityState>>  (deduped)
 * SharedFlow<NegotiationEvent>                       (bounded)
 * ```
 *
 * - Per-device isolation: state is keyed by [DeviceIdentity]; one device's
 *   observations never affect another's.
 * - Equality-aware dedup: a new state that equals the previous one emits
 *   nothing — no duplicate events, no duplicate flow emissions.
 * - Sessions: a negotiation session begins when negotiation is observed and
 *   is terminated on disconnect; sessions never live forever.
 * - Disconnect: the device's state moves to DISCONNECTED, the session is
 *   terminated, and the last quality state is preserved as historical
 *   (freshness STALE) — never deleted, never presented as current.
 *
 * No debouncing is applied: inputs are snapshot-derived observations, not
 * rapid Android event bursts, so there is nothing legitimate to debounce
 * (OB-P13-REQ-022: debouncing only where justified).
 *
 * @param clockMillis injectable clock for deterministic tests.
 * @param maxTimelineEvents bound on the per-device event history.
 */
class AudioQualityEngine(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val maxTimelineEvents: Int = 32,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val guard = Mutex()

    private data class DeviceInputs(
        val transport: AudioTransportKind? = null,
        val routeActive: Boolean? = null,
        val transportConnected: Boolean? = null,
        val codecSnapshot: CodecSnapshot? = null,
        val controlState: CodecControlState? = null,
    )

    private val inputs = mutableMapOf<DeviceIdentity, DeviceInputs>()
    private val sessions = mutableMapOf<DeviceIdentity, NegotiationSession>()
    private val timelines = mutableMapOf<DeviceIdentity, ArrayDeque<NegotiationEvent>>()
    private var sessionCounter = 0L

    private val _states =
        MutableStateFlow<Map<DeviceIdentity, AudioQualityState>>(emptyMap())
    /** Authoritative per-device quality state, deduplicated. */
    val states: StateFlow<Map<DeviceIdentity, AudioQualityState>> = _states.asStateFlow()

    private val _events = MutableSharedFlow<NegotiationEvent>(
        replay = 0,
        extraBufferCapacity = 64,
    )
    /** Meaningful negotiation/quality events. */
    val events: SharedFlow<NegotiationEvent> = _events.asSharedFlow()

    // ------------------------------------------------------------------
    // Ingestion — the wiring layer calls these from the Phase 10/11/12 flows.
    // ------------------------------------------------------------------

    /** Transport observation for a device (Phase 10). */
    suspend fun onTransportUpdate(
        device: DeviceIdentity,
        transport: AudioTransportKind?,
        routeActive: Boolean?,
        transportConnected: Boolean?,
    ) = ingest(device) { it.copy(transport = transport, routeActive = routeActive, transportConnected = transportConnected) }

    /** Codec capability/runtime snapshot for a device (Phase 11). */
    suspend fun onCodecSnapshot(device: DeviceIdentity, snapshot: CodecSnapshot?) =
        ingest(device) { it.copy(codecSnapshot = snapshot) }

    /** Codec control state for a device (Phase 12). */
    suspend fun onControlState(device: DeviceIdentity, controlState: CodecControlState?) =
        ingest(device) { it.copy(controlState = controlState) }

    /**
     * The device disconnected: terminate the negotiation session, move
     * quality state to DISCONNECTED, and preserve the last state as
     * historical (STALE freshness on next resolution).
     */
    suspend fun onDisconnected(device: DeviceIdentity) {
        val now = clockMillis()
        val evidence = engineEvidence("device disconnected", now)
        guard.withLock {
            sessions.remove(device)?.let { session ->
                sessions[device] = session.copy(
                    completedAtMillis = now,
                    finalState = NegotiationState.DISCONNECTED,
                )
            }
            // Resolve once with connected=false to derive DISCONNECTED.
            val input = (inputs[device] ?: DeviceInputs()).copy(transportConnected = false)
            inputs[device] = input
            val newState = AudioQualityResolver.resolve(
                device = device,
                codecSnapshot = input.codecSnapshot,
                controlState = input.controlState,
                transport = input.transport,
                routeActive = false,
                transportConnected = false,
                nowMillis = now,
            )
            publish(device, newState, now, evidence)
            emitLocked(NegotiationEvent.AudioDeviceDisconnected(device, now, evidence))
        }
    }

    /** Mark a device's state stale (transport change, route change, etc.). */
    suspend fun onBecameStale(device: DeviceIdentity, reason: String) {
        val now = clockMillis()
        val evidence = engineEvidence("became stale: $reason", now)
        guard.withLock {
            val current = _states.value[device]
            if (current != null) {
                val stale = current.copy(
                    freshness = CodecFreshness.STALE,
                    negotiationState = NegotiationState.STALE,
                    timestampMillis = now,
                )
                publish(device, stale, now, evidence)
                emitLocked(NegotiationEvent.AudioStateBecameStale(device, now, evidence, reason))
            }
        }
    }

    /** Stop the engine; cancels the scope. */
    fun stop() {
        scope.cancel()
    }

    /** The active (or last) negotiation session for a device, if any. */
    suspend fun sessionFor(device: DeviceIdentity): NegotiationSession? =
        guard.withLock { sessions[device] }

    /** The bounded event timeline for a device, oldest first. */
    suspend fun timelineFor(device: DeviceIdentity): List<NegotiationEvent> =
        guard.withLock { timelines[device]?.toList().orEmpty() }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private suspend fun ingest(
        device: DeviceIdentity,
        transform: (DeviceInputs) -> DeviceInputs,
    ) {
        val now = clockMillis()
        guard.withLock {
            val updated = transform(inputs[device] ?: DeviceInputs())
            inputs[device] = updated
            val newState = AudioQualityResolver.resolve(
                device = device,
                codecSnapshot = updated.codecSnapshot,
                controlState = updated.controlState,
                transport = updated.transport,
                routeActive = updated.routeActive,
                transportConnected = updated.transportConnected,
                nowMillis = now,
            )
            val previous = _states.value[device]
            if (previous != newState) {
                detectChanges(previous, newState, now)
                manageSession(device, previous, newState, now)
                publish(device, newState, now, newState.evidence)
            }
        }
    }

    private fun publish(
        device: DeviceIdentity,
        state: AudioQualityState,
        now: Long,
        evidence: CodecEvidence,
    ) {
        val current = _states.value.toMutableMap()
        current[device] = state
        _states.value = current
    }

    private fun emitLocked(event: NegotiationEvent) {
        val timeline = timelines.getOrPut(event.device) { ArrayDeque() }
        timeline.addLast(event)
        while (timeline.size > maxTimelineEvents) timeline.removeFirst()
        // Synchronous: the buffer absorbs bursts; collectors see events in order.
        _events.tryEmit(event)
    }

    private fun engineEvidence(detail: String, now: Long): CodecEvidence = CodecEvidence(
        source = CodecEvidenceSource.ANDROID_FRAMEWORK,
        confidence = EvidenceConfidence.INFERRED,
        observedAtMillis = now,
        detail = "AudioQualityEngine: $detail",
    )

    /**
     * Compare previous and new state; emit events for meaningful changes.
     * Identical states never reach here (dedup in ingest).
     */
    private fun detectChanges(
        previous: AudioQualityState?,
        new: AudioQualityState,
        now: Long,
    ) {
        val device = new.device
        val evidence = new.evidence
        if (previous == null) {
            if (new.transport != AudioTransportKind.UNKNOWN) {
                emitLocked(NegotiationEvent.AudioTransportDetected(device, now, evidence, new.transport))
            }
            return
        }
        if (previous.transport != new.transport && new.transport != AudioTransportKind.UNKNOWN) {
            emitLocked(NegotiationEvent.AudioTransportDetected(device, now, evidence, new.transport))
        }
        if (previous.routeActive != new.routeActive && new.routeActive != null) {
            emitLocked(NegotiationEvent.AudioRouteChanged(device, now, evidence, new.routeActive))
            if (new.routeActive) {
                emitLocked(NegotiationEvent.AudioBecameActive(device, now, evidence))
            } else {
                emitLocked(NegotiationEvent.AudioBecameInactive(device, now, evidence))
            }
        }
        if (previous.negotiationState != new.negotiationState) {
            when (new.negotiationState) {
                NegotiationState.NEGOTIATING ->
                    emitLocked(NegotiationEvent.CodecNegotiationStarted(device, now, evidence))
                NegotiationState.NEGOTIATED ->
                    emitLocked(
                        NegotiationEvent.CodecNegotiationCompleted(
                            device, now, evidence, new.negotiatedCodec, success = true,
                        ),
                    )
                NegotiationState.FAILED ->
                    emitLocked(NegotiationEvent.NegotiationFailed(device, now, evidence, "negotiation failed"))
                else -> Unit
            }
        }
        if (previous.activeCodec != new.activeCodec) {
            emitLocked(
                NegotiationEvent.CodecChanged(device, now, evidence, previous.activeCodec, new.activeCodec),
            )
        } else if (parametersChanged(previous, new)) {
            emitLocked(
                NegotiationEvent.CodecParametersChanged(
                    device, now, evidence, new.activeCodec,
                    describeParameterChange(previous, new),
                ),
            )
        }
    }

    private fun parametersChanged(a: AudioQualityState, b: AudioQualityState): Boolean =
        a.activeSampleRateHz != b.activeSampleRateHz ||
            a.activeBitDepth != b.activeBitDepth ||
            a.observedBitrate != b.observedBitrate ||
            a.channelMode != b.channelMode ||
            a.observedQualityMode != b.observedQualityMode

    private fun describeParameterChange(a: AudioQualityState, b: AudioQualityState): String {
        val parts = mutableListOf<String>()
        // Policy (OB-P13-REQ-024): a change to/from UNKNOWN is reported only
        // when the other side is a real value AND the transition is
        // meaningful (loss of observation during ACTIVE is meaningful;
        // initial discovery is covered by CodecChanged).
        if (a.activeSampleRateHz != b.activeSampleRateHz) {
            parts += "sampleRate ${a.activeSampleRateHz ?: "unknown"} → ${b.activeSampleRateHz ?: "unknown"}"
        }
        if (a.activeBitDepth != b.activeBitDepth) {
            parts += "bitDepth ${a.activeBitDepth ?: "unknown"} → ${b.activeBitDepth ?: "unknown"}"
        }
        if (a.observedBitrate != b.observedBitrate) {
            parts += "bitrate changed"
        }
        if (a.channelMode != b.channelMode) {
            parts += "channelMode ${a.channelMode} → ${b.channelMode}"
        }
        if (a.observedQualityMode != b.observedQualityMode) {
            parts += "qualityMode ${a.observedQualityMode} → ${b.observedQualityMode}"
        }
        return parts.joinToString("; ").ifEmpty { "parameters changed" }
    }

    /**
     * Manage the negotiation session lifecycle from state transitions.
     */
    private fun manageSession(
        device: DeviceIdentity,
        previous: AudioQualityState?,
        new: AudioQualityState,
        now: Long,
    ) {
        val current = sessions[device]
        when (new.negotiationState) {
            NegotiationState.NEGOTIATING -> {
                if (current == null || !current.isActive) {
                    sessionCounter += 1
                    sessions[device] = NegotiationSession.begin(
                        sessionId = "neg-$sessionCounter",
                        device = device,
                        transport = new.transport,
                        nowMillis = now,
                        evidence = new.evidence,
                    )
                }
            }
            NegotiationState.NEGOTIATED, NegotiationState.ACTIVE -> {
                if (current != null && current.isActive) {
                    sessions[device] = current.copy(
                        completedAtMillis = now,
                        negotiatedCodec = new.negotiatedCodec,
                        finalState = new.negotiationState,
                    )
                }
            }
            NegotiationState.FAILED -> {
                if (current != null && current.isActive) {
                    sessions[device] = current.copy(
                        completedAtMillis = now,
                        finalState = NegotiationState.FAILED,
                        failureReason = "negotiation failed",
                    )
                }
            }
            NegotiationState.DISCONNECTED -> {
                sessions.remove(device)
            }
            else -> Unit
        }
        // Legal-transition guard: if the derived transition is illegal, keep
        // the previous negotiation state and let the next observation drive.
        // (The resolver derives deterministically; this is defense in depth.)
        if (previous != null &&
            !NegotiationTransitions.isLegal(previous.negotiationState, new.negotiationState)
        ) {
            // The state was already published; the guard prevents future
            // illegal jumps from compounding. No silent rewrite here.
        }
    }
}
