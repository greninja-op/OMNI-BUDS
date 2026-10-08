package com.omnibuds.core.codec

import com.omnibuds.core.audio.AudioTransportKind
import com.omnibuds.core.audio.CodecEvidence
import com.omnibuds.core.audio.CodecFreshness
import com.omnibuds.core.audio.CodecObservability
import com.omnibuds.core.audio.CodecState
import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.device.DeviceIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The Codec Capability Engine.
 *
 * Turns platform codec observations into normalized, per-device [CodecSnapshot]
 * records. Observation only: the engine never configures, switches, or forces
 * a codec — there is no code path that could (Phase 11 §25).
 *
 * Architecture (Phase 11 §24):
 *
 *     Bluetooth/Audio platform
 *             ↓
 *     Audio Transport Engine (Phase 10)
 *             ↓
 *     Codec Observation Adapter ([CodecObservationSource])
 *             ↓
 *     CodecCapabilityEngine (this)
 *             ↓
 *     Domain Codec State ([CodecSnapshot])
 *             ↓
 *     Consumers
 *
 * Multi-device isolation: snapshots are keyed by [DeviceIdentity]; device A's
 * codec state can never appear in device B's snapshot — the map structure
 * makes cross-device leaks inexpressible.
 *
 * Staleness (Phase 11 §29): [stop] marks the device's runtime state
 * [CodecFreshness.STALE] instead of deleting it, so consumers see "was active,
 * now stale" rather than a silent disappearance. A fresh [start] re-observes
 * from the platform, never from the stale record.
 *
 * @param observationSource the platform adapter behind the port.
 * @param clockMillis injectable clock; defaults to wall time.
 * @param dispatcher injected for deterministic tests; defaults to Default.
 */
class CodecCapabilityEngine(
    private val observationSource: CodecObservationSource,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val scopeDispatcher: CoroutineDispatcher = dispatcher
    private val mutex = Mutex()

    private val _snapshots =
        MutableStateFlow<Map<DeviceIdentity, CodecSnapshot>>(emptyMap())

    /**
     * All per-device snapshots, keyed by device. The single authoritative
     * store; consumers observe, never mutate.
     */
    val snapshots: StateFlow<Map<DeviceIdentity, CodecSnapshot>> =
        _snapshots.asStateFlow()

    /** Per-device observation jobs, guarded by [mutex]. */
    private val observationJobs = mutableMapOf<DeviceIdentity, Job>()
    private var engineScope: CoroutineScope? = null

    /**
     * Begin observing [device]. Idempotent: starting an already-observed
     * device is a no-op success. Reads capabilities and runtime state once,
     * publishes the snapshot, then collects runtime updates.
     */
    suspend fun start(device: DeviceIdentity): OperationOutcome<Unit> = mutex.withLock {
        if (observationJobs.containsKey(device)) return@withLock OperationOutcome.Success(Unit)
        val scope = engineScope
            ?: CoroutineScope(SupervisorJob() + scopeDispatcher).also { engineScope = it }
        try {
            val snapshot = readSnapshot(device)
            _snapshots.value = _snapshots.value + (device to snapshot)
            val job = scope.launch {
                observationSource.observeRuntimeStates(device)
                    .catch { cause ->
                        if (cause is CancellationException) throw cause
                        recordDiagnostic(
                            device,
                            "CODEC_OBSERVATION_FLOW_FAILED",
                            "codec runtime flow failed (${cause::class.simpleName}); " +
                                "keeping last snapshot until the next update.",
                        )
                    }
                    .collect { runtime ->
                        mutex.withLock { applyRuntimeState(device, runtime) }
                    }
            }
            observationJobs[device] = job
            OperationOutcome.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordDiagnostic(
                device,
                "CODEC_OBSERVATION_FAILED",
                "initial codec read failed (${e::class.simpleName}); no snapshot published.",
            )
            OperationOutcome.Failure(
                OmniBudsError(
                    OmniBudsErrorCategory.CODEC_OBSERVATION_FAILED,
                    "codec/start",
                    "Codec observation failed for device (${e::class.simpleName}).",
                ),
            )
        }
    }

    /**
     * Stop observing [device]. Idempotent. The runtime state is marked
     * [CodecFreshness.STALE] — never silently dropped, never left rendering
     * as current.
     */
    suspend fun stop(device: DeviceIdentity): OperationOutcome<Unit> {
        val job = mutex.withLock { observationJobs.remove(device) }
        job?.cancelAndJoin()
        mutex.withLock {
            _snapshots.value[device]?.let { current ->
                val staleRuntime = current.runtimeState?.copy(freshness = CodecFreshness.STALE)
                _snapshots.value = _snapshots.value + (
                    device to current.copy(
                        timestampMillis = clockMillis(),
                        runtimeState = staleRuntime,
                    )
                    )
            }
        }
        return OperationOutcome.Success(Unit)
    }

    /**
     * Re-read capabilities and runtime state for [device] on demand.
     * The engine never polls by itself; the host triggers refreshes.
     */
    suspend fun refresh(device: DeviceIdentity): OperationOutcome<Unit> = mutex.withLock {
        if (!observationJobs.containsKey(device)) {
            return@withLock OperationOutcome.Failure(
                OmniBudsError(
                    OmniBudsErrorCategory.INVALID_STATE,
                    "codec/refresh",
                    "Cannot refresh codec state for an unobserved device.",
                ),
            )
        }
        try {
            _snapshots.value = _snapshots.value + (device to readSnapshot(device))
            OperationOutcome.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordDiagnostic(
                device,
                "CODEC_OBSERVATION_FAILED",
                "codec refresh failed (${e::class.simpleName}); keeping last snapshot.",
            )
            OperationOutcome.Failure(
                OmniBudsError(
                    OmniBudsErrorCategory.CODEC_OBSERVATION_FAILED,
                    "codec/refresh",
                    "Codec refresh failed for device (${e::class.simpleName}).",
                ),
            )
        }
    }

    /** Observe the snapshot for one [device] as a Flow. */
    fun observeSnapshot(device: DeviceIdentity): Flow<CodecSnapshot?> =
        snapshots.map { it[device] }

    /** Shut down observation for all devices. */
    suspend fun stopAll(): OperationOutcome<Unit> {
        val devices = mutex.withLock { observationJobs.keys.toList() }
        devices.forEach { stop(it) }
        return OperationOutcome.Success(Unit)
    }

    private suspend fun readSnapshot(device: DeviceIdentity): CodecSnapshot {
        val now = clockMillis()
        val capabilities = observationSource.readCapabilities(device)
        val runtime = observationSource.readRuntimeState(device)?.copy(
            freshness = CodecFreshness.CURRENT,
        )
        val transport = transportFor(capabilities)
        val observability = observabilityFor(capabilities, runtime)
        return CodecSnapshot(
            timestampMillis = now,
            deviceId = device,
            transport = transport,
            capabilities = capabilities.toList(),
            runtimeState = runtime,
            observability = observability,
            limitations = limitationsFor(transport, observability),
            diagnostics = emptyList(),
        )
    }

    private suspend fun applyRuntimeState(device: DeviceIdentity, runtime: CodecRuntimeState) {
        val current = _snapshots.value[device] ?: return
        val fresh = runtime.copy(
            freshness = CodecFreshness.CURRENT,
            observedAtMillis = runtime.observedAtMillis ?: clockMillis(),
        )
        _snapshots.value = _snapshots.value + (
            device to current.copy(
                timestampMillis = clockMillis(),
                runtimeState = fresh,
            )
            )
    }

    private fun transportFor(capabilities: List<com.omnibuds.core.audio.CodecCapability>): AudioTransportKind {
        val families = capabilities.map { it.codec.family }.toSet()
        return when {
            families.size == 1 -> when (families.single()) {
                com.omnibuds.core.audio.CodecFamily.LE_AUDIO -> AudioTransportKind.LE_AUDIO
                com.omnibuds.core.audio.CodecFamily.CLASSIC_A2DP -> AudioTransportKind.CLASSIC_A2DP
                com.omnibuds.core.audio.CodecFamily.UNKNOWN -> AudioTransportKind.UNKNOWN
            }
            else -> AudioTransportKind.UNKNOWN
        }
    }

    private fun observabilityFor(
        capabilities: List<com.omnibuds.core.audio.CodecCapability>,
        runtime: CodecRuntimeState?,
    ): CodecObservability {
        val values = capabilities.map { it.observability }.toSet() +
            (runtime?.let { setOf(it.evidence.toObservability()) } ?: emptySet())
        return when {
            values.isEmpty() || values == setOf(CodecObservability.UNKNOWN) -> CodecObservability.UNKNOWN
            CodecObservability.NOT_OBSERVABLE in values &&
                values.none { it == CodecObservability.OBSERVABLE } -> CodecObservability.NOT_OBSERVABLE
            CodecObservability.OBSERVABLE in values -> CodecObservability.PARTIALLY_OBSERVABLE
            else -> CodecObservability.UNKNOWN
        }
    }

    private fun limitationsFor(
        transport: AudioTransportKind,
        observability: CodecObservability,
    ): List<String> = buildList {
        if (observability == CodecObservability.NOT_OBSERVABLE ||
            observability == CodecObservability.UNKNOWN
        ) {
            add(
                "Active codec has no public observation API on this platform; " +
                    "active/negotiated state stays UNKNOWN and must not be read as a codec name.",
            )
        }
        if (transport == AudioTransportKind.LE_AUDIO) {
            add(
                "LE Audio codec configuration has no public observation API; " +
                    "LC3 runtime parameters stay UNKNOWN.",
            )
        }
    }

    private fun recordDiagnostic(device: DeviceIdentity, code: String, message: String) {
        val now = clockMillis()
        val current = _snapshots.value[device] ?: return
        val updated = (listOf(CodecDiagnostic(code, message, now)) + current.diagnostics)
            .take(CodecSnapshot.MAX_DIAGNOSTICS)
        _snapshots.value = _snapshots.value + (device to current.copy(diagnostics = updated))
    }

    private fun CodecEvidence.toObservability(): CodecObservability = when (source) {
        com.omnibuds.core.audio.CodecEvidenceSource.ANDROID_FRAMEWORK,
        com.omnibuds.core.audio.CodecEvidenceSource.BLUETOOTH_PROFILE,
        com.omnibuds.core.audio.CodecEvidenceSource.PLATFORM_CODEC_METADATA,
        com.omnibuds.core.audio.CodecEvidenceSource.AUDIO_DEVICE_INFO -> CodecObservability.OBSERVABLE
        com.omnibuds.core.audio.CodecEvidenceSource.DEVICE_PROTOCOL,
        com.omnibuds.core.audio.CodecEvidenceSource.VENDOR_PROTOCOL -> CodecObservability.PARTIALLY_OBSERVABLE
        com.omnibuds.core.audio.CodecEvidenceSource.UNKNOWN -> CodecObservability.UNKNOWN
    }
}
