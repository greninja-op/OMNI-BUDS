package com.omnibuds.core.validation

import com.omnibuds.core.audio.AudioTransportKind
import com.omnibuds.core.audio.Codec
import com.omnibuds.core.device.DeviceIdentity
import com.omnibuds.core.quality.AudioQualityEngine
import com.omnibuds.core.quality.NegotiationState
import com.omnibuds.core.validation.rules.CodecActiveClaimRule
import com.omnibuds.core.validation.rules.CodecTransportAssociationRule
import com.omnibuds.core.validation.rules.DeviceIdentityMatchRule
import com.omnibuds.core.validation.rules.DisconnectedRouteRule
import com.omnibuds.core.validation.rules.FreshnessCoherenceRule
import com.omnibuds.core.validation.rules.ParameterDomainRule
import com.omnibuds.core.validation.rules.RouteConsistencyRule
import com.omnibuds.core.validation.rules.SessionGenerationRule
import com.omnibuds.core.validation.rules.StaleCodecRule
import com.omnibuds.core.validation.rules.TransportCoherenceRule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The audio path validation engine.
 *
 * Phase 14: consumes Phase 10/11/12/13 observations (via the
 * [AudioQualityEngine] plus transport inputs) and produces immutable,
 * deduplicated [AudioPathValidationSnapshot]s per device.
 *
 * Pipeline: Observations → Normalization → Evidence Collection → Rule
 * Evaluation → Results → Diagnostic Summary (OB-P14-REQ-011).
 *
 * - Session generations: each device has a monotonically increasing
 *   generation, bumped on disconnect/reconnect. Inputs tagged with an older
 *   generation are discarded before rules run — a late event from an
 *   obsolete session can never restore stale state (OB-P14-REQ-017).
 * - Deterministic: the same input bundle always yields the same snapshot.
 * - Deduplicated: identical snapshots are not re-emitted.
 * - Event-driven: [validate] is called when inputs change; no polling.
 *
 * Signal-path honesty (OB-P14-REQ-020): VALID means "observations are
 * internally consistent", never "sound confirmed at the speaker".
 */
class AudioPathValidationEngine(
    private val qualityEngine: AudioQualityEngine,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val rules: List<ValidationRule> = defaultRules(),
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val guard = Mutex()

    private val generations = mutableMapOf<DeviceIdentity, Long>()
    private var snapshotCounter = 0L

    private val _snapshots =
        MutableStateFlow<Map<DeviceIdentity, AudioPathValidationSnapshot>>(emptyMap())
    /** Authoritative per-device validation snapshots, deduplicated. */
    val snapshots: StateFlow<Map<DeviceIdentity, AudioPathValidationSnapshot>> =
        _snapshots.asStateFlow()

    /**
     * Validate one device's current observations.
     *
     * @param expectedGeneration the caller-observed session generation; if it
     * does not match the engine's current generation the input is obsolete
     * and is discarded (returns null).
     */
    suspend fun validate(
        device: DeviceIdentity,
        expectedGeneration: Long? = null,
        transport: AudioTransportKind? = null,
        transportConnected: Boolean? = null,
        routeActive: Boolean? = null,
        audioDeviceAvailable: Boolean? = null,
        isCommunicationRoute: Boolean? = null,
    ): AudioPathValidationSnapshot? {
        val now = clockMillis()
        return guard.withLock {
            val currentGeneration = generations.getOrPut(device) { 0L }
            if (expectedGeneration != null && expectedGeneration != currentGeneration) {
                // Obsolete session: discard, never evaluate.
                return@withLock null
            }
            val quality = qualityEngine.states.value[device]
            val input = ValidationInput(
                device = device,
                sessionGeneration = currentGeneration,
                timestampMillis = now,
                transport = transport ?: quality?.transport,
                transportConnected = transportConnected ?: quality?.transportConnected,
                routeActive = routeActive ?: quality?.routeActive,
                audioDeviceAvailable = audioDeviceAvailable,
                isCommunicationRoute = isCommunicationRoute,
                codec = quality?.activeCodec?.takeIf { it != Codec.UNKNOWN },
                codecState = quality?.codecState,
                codecFreshness = quality?.freshness,
                codecSupported = null,
                qualityState = quality,
                negotiationState = quality?.negotiationState,
                sampleRateHz = quality?.activeSampleRateHz,
                bitDepth = quality?.activeBitDepth,
                channelMode = quality?.channelMode?.name,
                adaptiveState = quality?.adaptiveState,
            )
            val results = rules.map { it.evaluate(input) }
            val overall = ValidationAggregator.aggregate(results)
            val previous = _snapshots.value[device]
            // Content comparison excludes the snapshot id: identical
            // observations must not produce a "new" snapshot.
            val candidate = AudioPathValidationSnapshot(
                snapshotId = previous?.snapshotId ?: "val-${++snapshotCounter}",
                device = device,
                sessionGeneration = currentGeneration,
                timestampMillis = now,
                observedTransport = input.transport ?: AudioTransportKind.UNKNOWN,
                routeActive = input.routeActive,
                codec = input.codec ?: Codec.UNKNOWN,
                negotiationState = input.negotiationState ?: NegotiationState.UNKNOWN,
                freshnessSummary = input.codecFreshness?.name ?: "UNKNOWN",
                results = results,
                overallStatus = overall,
                evidenceReferences = results.flatMap { it.evidenceReferences }.distinct(),
                limitations = results.flatMap { it.limitations }.distinct(),
            )
            if (previous != candidate) {
                snapshotCounter += 1
                val snapshot = candidate.copy(snapshotId = "val-$snapshotCounter")
                _snapshots.value = _snapshots.value + (device to snapshot)
                return@withLock snapshot
            }
            return@withLock previous
        }
    }

    /**
     * The device disconnected: bump the session generation so any in-flight
     * or late observations from the old session are discarded.
     */
    suspend fun onDisconnected(device: DeviceIdentity) {
        guard.withLock {
            generations[device] = (generations[device] ?: 0L) + 1
        }
    }

    /** The current session generation for a device. */
    suspend fun generationFor(device: DeviceIdentity): Long =
        guard.withLock { generations.getOrPut(device) { 0L } }

    fun stop() {
        scope.cancel()
    }

    companion object {
        fun defaultRules(): List<ValidationRule> = listOf(
            DeviceIdentityMatchRule,
            DisconnectedRouteRule,
            StaleCodecRule,
            TransportCoherenceRule,
            CodecTransportAssociationRule,
            CodecActiveClaimRule,
            RouteConsistencyRule,
            ParameterDomainRule,
            FreshnessCoherenceRule,
            SessionGenerationRule,
        )
    }
}
