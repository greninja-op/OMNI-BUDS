package com.omnibuds.core.audio

import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The Phase 10 audio transport engine: the single authoritative observer of
 * Bluetooth audio transport state.
 *
 * The engine owns exactly one [StateFlow] of [AudioTransportSnapshot]
 * (OB-P10-REQ-013): it merges the [AudioProfileSource] and [AudioDeviceSource]
 * ports, reconciles them through [AudioReconciler], and publishes the result.
 * Consumers observe; nobody else keeps audio state. The engine itself is
 * read-only toward the platform — it registers callbacks and reads proxies,
 * and the day it moves a transport between states is the day it stops being
 * an observer (master Phase 10 rule 2).
 *
 * Concurrency and lifecycle (OB-P10-REQ-017, OB-P10-REQ-022):
 *
 * - All state transitions run behind [mutex]; start/stop/observation updates
 *   cannot interleave into a half-registered observer.
 * - Observation work runs in a private [SupervisorJob] scope, so a failing
 *   device-event flow cannot cancel the engine itself — the failure is caught,
 *   recorded as a diagnostic, and observation continues from the last good
 *   profile read.
 * - [stop] cancels the scope and joins it: no leaked callbacks, no leaked
 *   profile proxies, no collection surviving teardown. Cancellation is
 *   cooperative and deterministic.
 * - Nothing here touches the main thread beyond what the platform ports do
 *   internally; the engine performs no blocking I/O.
 * - Snapshots are recomputed only when an input changes (profile poll result,
 *   device event, capabilities read) — never on a timer. Callbacks, not
 *   polling, drive observation.
 *
 * @param profileSource answers "what do the Bluetooth profiles report".
 * @param deviceSource answers "which audio devices exist".
 * @param capabilities the platform's audio capabilities, read once by the host.
 * @param clockMillis wall-clock source, injected for deterministic tests.
 * @param dispatcher dispatcher for observation work; injected so tests can
 *   drive the engine on a test scheduler instead of real threads.
 */
class AudioTransportEngine(
    private val profileSource: AudioProfileSource,
    private val deviceSource: AudioDeviceSource,
    private val capabilities: AudioPlatformCapabilities,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val mutex = Mutex()
    private val _lifecycle = MutableStateFlow(AudioObserverLifecycle.STOPPED)
    /** The observer lifecycle; mirrors the state machine in [AudioObserverLifecycle]. */
    val lifecycle: StateFlow<AudioObserverLifecycle> = _lifecycle.asStateFlow()

    private val _snapshots = MutableStateFlow(AudioTransportSnapshot.initial(clockMillis()))
    /** The authoritative audio transport snapshot stream. */
    val snapshots: StateFlow<AudioTransportSnapshot> = _snapshots.asStateFlow()

    private var scope: CoroutineScope? = null
    private var deviceTimestamps = mutableMapOf<Int, Long>()

    /**
     * Starts observation. Safe to call repeatedly: starting while
     * [AudioObserverLifecycle.OBSERVING] or `STARTING` is a no-op success, and
     * a failed start always lands back in [AudioObserverLifecycle.STOPPED]
     * with the error in the outcome — never a half-registered observer.
     */
    suspend fun start(): OperationOutcome<Unit> = mutex.withLock {
        when (_lifecycle.value) {
            AudioObserverLifecycle.OBSERVING,
            AudioObserverLifecycle.STARTING,
            -> return OperationOutcome.Success(Unit)
            AudioObserverLifecycle.STOPPING -> return OperationOutcome.Failure(
                OmniBudsError.of(
                    OmniBudsErrorCategory.INVALID_STATE,
                    operationId = "audio-observer-start",
                    detail = "observer is stopping; wait for STOPPED before starting",
                ),
            )
            AudioObserverLifecycle.STOPPED -> Unit
        }
        _lifecycle.value = AudioObserverLifecycle.STARTING
        try {
            val childScope = CoroutineScope(SupervisorJob() + dispatcher)
            scope = childScope
            val initial = readAndReconcile(emptyMap())
            _snapshots.value = initial
            childScope.launch {
                deviceSource.observeDeviceEvents()
                    .catch { cause ->
                        // A failing event flow must not kill observation: record
                        // it and keep the last good snapshot. The profile read
                        // below still runs on the next trigger.
                        if (cause is CancellationException) throw cause
                        recordDiagnostic(
                            "DEVICE_OBSERVATION_FAILED",
                            "audio-device event flow failed (${cause::class.simpleName}); " +
                                "keeping last snapshot until the next update.",
                        )
                    }
                    .collect { event -> mutex.withLock { applyDeviceEvent(event) } }
            }
            _lifecycle.value = AudioObserverLifecycle.OBSERVING
            OperationOutcome.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            scope?.cancel()
            scope = null
            _lifecycle.value = AudioObserverLifecycle.STOPPED
            OperationOutcome.Failure(
                OmniBudsError.of(
                    OmniBudsErrorCategory.AUDIO_OBSERVATION_FAILED,
                    operationId = "audio-observer-start",
                    detail = "observation failed to start: ${e::class.simpleName}",
                ),
            )
        }
    }

    /**
     * Stops observation and releases every platform registration. Safe to call
     * repeatedly; stopping a stopped observer is a no-op success. Returns only
     * after the observation scope is cancelled and joined, so when this
     * returns, no callback can fire afterwards.
     */
    suspend fun stop(): OperationOutcome<Unit> {
        val toCancel = mutex.withLock {
            when (_lifecycle.value) {
                AudioObserverLifecycle.STOPPED -> return OperationOutcome.Success(Unit)
                AudioObserverLifecycle.STOPPING -> return OperationOutcome.Success(Unit)
                AudioObserverLifecycle.STARTING,
                AudioObserverLifecycle.OBSERVING,
                -> {
                    _lifecycle.value = AudioObserverLifecycle.STOPPING
                    val s = scope
                    scope = null
                    s
                }
            }
        }
        try {
            toCancel?.cancel()
            (toCancel?.coroutineContext?.get(Job))?.join()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Cancellation is best-effort; the lifecycle still returns to STOPPED.
        } finally {
            mutex.withLock {
                deviceTimestamps.clear()
                _lifecycle.value = AudioObserverLifecycle.STOPPED
            }
        }
        return OperationOutcome.Success(Unit)
    }

    /**
     * Triggers a fresh profile read and re-reconciliation, e.g. after the host
     * learns (through its own Bluetooth session observation) that something
     * changed. This is a read, not a poll loop: the engine never schedules it
     * by itself.
     */
    suspend fun refreshProfiles(): OperationOutcome<Unit> = mutex.withLock {
        if (_lifecycle.value != AudioObserverLifecycle.OBSERVING) {
            return OperationOutcome.Failure(
                OmniBudsError.of(
                    OmniBudsErrorCategory.INVALID_STATE,
                    operationId = "audio-observer-refresh",
                    detail = "cannot refresh profiles while ${_lifecycle.value}",
                ),
            )
        }
        return try {
            _snapshots.value = readAndReconcile(_snapshots.value.profileStates)
            OperationOutcome.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordDiagnostic(
                "PROFILE_READ_FAILED",
                "profile re-read failed (${e::class.simpleName}); keeping last snapshot.",
            )
            OperationOutcome.Failure(
                OmniBudsError.of(
                    OmniBudsErrorCategory.AUDIO_OBSERVATION_FAILED,
                    operationId = "audio-observer-refresh",
                    detail = "profile re-read failed: ${e::class.simpleName}",
                ),
            )
        }
    }

    private suspend fun readAndReconcile(
        previousProfiles: Map<AudioTransportKind, AudioProfileState>,
    ): AudioTransportSnapshot {
        val now = clockMillis()
        // Diagnostics from this read are accumulated locally, not written to
        // _snapshots directly: the reconciled snapshot replaces _snapshots at
        // the end, and a direct write now would be overwritten and lost.
        val pending = mutableListOf<AudioDiagnostic>()
        fun diagnose(code: String, message: String) {
            if (pending.size < AudioTransportSnapshot.MAX_DIAGNOSTICS) {
                pending += AudioDiagnostic(code, message, now)
            }
        }
        val profiles = try {
            profileSource.readProfileStates()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A failed read keeps the previous profile states rather than
            // fabricating DISCONNECTED everywhere; the diagnostic says why.
            diagnose(
                "PROFILE_READ_FAILED",
                "profile read failed (${e::class.simpleName}); reusing previous profile states.",
            )
            previousProfiles
        }
        val devices = try {
            deviceSource.readDevices()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            diagnose(
                "DEVICE_READ_FAILED",
                "audio-device read failed (${e::class.simpleName}); reusing previous devices.",
            )
            _snapshots.value.devices
        }
        devices.forEach { deviceTimestamps[it.platformDeviceId] = now }
        val hint = try {
            profileSource.readActiveTransportHint()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val reconciled = AudioReconciler.reconcile(
            profileStates = profiles,
            devices = devices,
            capabilities = capabilities,
            activeTransportHint = hint,
            timestampMillis = now,
            deviceTimestampsMillis = deviceTimestamps.toMap(),
        )
        // Read-phase diagnostics first (they explain the inputs), then the
        // reconciler's, bounded.
        val merged = (pending + reconciled.diagnostics).take(AudioTransportSnapshot.MAX_DIAGNOSTICS)
        return reconciled.copy(diagnostics = merged)
    }

    private suspend fun applyDeviceEvent(event: AudioDeviceEvent) {
        val now = clockMillis()
        val current = _snapshots.value
        val devices = when (event) {
            is AudioDeviceEvent.Added -> {
                event.devices.forEach { deviceTimestamps[it.platformDeviceId] = now }
                val byId = current.devices.associateBy { it.platformDeviceId }.toMutableMap()
                event.devices.forEach { byId[it.platformDeviceId] = it }
                byId.values.toList()
            }
            is AudioDeviceEvent.Removed -> {
                event.platformDeviceIds.forEach { deviceTimestamps.remove(it) }
                current.devices.filterNot { it.platformDeviceId in event.platformDeviceIds }
            }
            is AudioDeviceEvent.Resynchronized -> {
                deviceTimestamps = event.devices.associate { it.platformDeviceId to now }.toMutableMap()
                event.devices
            }
        }
        _snapshots.value = AudioReconciler.reconcile(
            profileStates = current.profileStates,
            devices = devices,
            capabilities = capabilities,
            activeTransportHint = current.activeTransport,
            timestampMillis = now,
            deviceTimestampsMillis = deviceTimestamps.toMap(),
        )
    }

    private fun recordDiagnostic(code: String, message: String) {
        val now = clockMillis()
        val current = _snapshots.value
        if (current.diagnostics.size >= AudioTransportSnapshot.MAX_DIAGNOSTICS) return
        _snapshots.value = current.copy(
            diagnostics = listOf(AudioDiagnostic(code, message, now)) + current.diagnostics,
        )
    }

    /** Diagnostic stream of lifecycle transitions, for tests and host logging. */
    fun lifecycleChanges(): Flow<AudioObserverLifecycle> = lifecycle
}