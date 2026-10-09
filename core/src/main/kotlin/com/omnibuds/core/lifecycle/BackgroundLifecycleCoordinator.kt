package com.omnibuds.core.lifecycle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Background lifecycle coordinator.
 *
 * Phase 28: owns lifecycle orchestration. Consumes typed lifecycle events,
 * plans recovery on process start, applies the reconnect policy, and
 * releases resources. Never fabricates device state; never bypasses the
 * access policy.
 */
class BackgroundLifecycleCoordinator(
    private val stateMachine: LifecycleStateMachine = LifecycleStateMachine(),
    private val recoveryPlanner: ProcessRecoveryPlanner = ProcessRecoveryPlanner(),
    private val reconnectPolicy: ReconnectPolicy = ReconnectPolicy(),
    private val resources: ResourceLifecycleRegistry = ResourceLifecycleRegistry(),
    private val hooks: LifecycleHooks,
) {
    private var collectionJob: Job? = null
    private val reconnectAttempts = mutableMapOf<String, Int>()
    private val reconnectJobs = mutableMapOf<String, Job>()
    private val seenEvents = LinkedHashSet<String>()
    private val seenLock = Any()

    companion object {
        const val MAX_SEEN_EVENTS = 500
    }

    /**
     * Start coordinating. Safe to call repeatedly.
     */
    fun start(scope: CoroutineScope, events: kotlinx.coroutines.flow.Flow<LifecycleEvent>) {
        stop()
        collectionJob = scope.launch {
            events.collectLatest { event -> handle(event, scope) }
        }
    }

    /** Stop coordinating and cancel owned work. */
    fun stop() {
        collectionJob?.cancel()
        collectionJob = null
        reconnectJobs.values.forEach { it.cancel() }
        reconnectJobs.clear()
    }

    private suspend fun handle(event: LifecycleEvent, scope: CoroutineScope) {
        // Deduplicate identical recovery triggers.
        val key = event.toString()
        synchronized(seenLock) {
            if (key in seenEvents) return
            seenEvents.add(key)
            if (seenEvents.size > MAX_SEEN_EVENTS) seenEvents.remove(seenEvents.first())
        }

        when (event) {
            is LifecycleEvent.ProcessStarted -> {
                stateMachine.apply(event)
                recoverAll()
            }
            is LifecycleEvent.ForegroundEntered -> {
                stateMachine.apply(event)
                hooks.onForeground()
            }
            is LifecycleEvent.BackgroundEntered -> {
                stateMachine.apply(event)
                hooks.onBackground()
            }
            is LifecycleEvent.AdapterDisabled -> {
                hooks.onAdapterDisabled()
                cancelReconnects("adapter disabled")
            }
            is LifecycleEvent.PermissionRevoked -> {
                hooks.onPermissionRevoked()
                cancelReconnects("permission revoked")
            }
            is LifecycleEvent.DeviceDisconnected -> {
                hooks.onDeviceDisconnected(event.deviceId)
                maybeReconnect(event.deviceId, ReconnectTrigger.PLATFORM_EVENT, scope)
            }
            is LifecycleEvent.DeviceReconnected -> {
                reconnectAttempts.remove(event.deviceId)
                reconnectJobs.remove(event.deviceId)?.cancel()
                hooks.onDeviceReconnected(event.deviceId)
            }
            is LifecycleEvent.ProcessTerminating -> {
                stateMachine.apply(event)
                resources.releaseAll()
                hooks.onTerminating()
            }
        }
    }

    private suspend fun recoverAll() {
        val devices = hooks.knownDevices()
        for (deviceId in devices) {
            val input = hooks.recoveryInput(deviceId)
            val plan = recoveryPlanner.plan(input)
            hooks.applyRecovery(plan)
        }
    }

    private fun maybeReconnect(
        deviceId: String,
        trigger: ReconnectTrigger,
        scope: CoroutineScope,
    ) {
        val attempts = reconnectAttempts.getOrDefault(deviceId, 0)
        val context = hooks.reconnectContext(deviceId, trigger, attempts)
        when (val decision = reconnectPolicy.shouldAttempt(context)) {
            is ReconnectDecision.Denied -> {
                hooks.recordDiagnostic("reconnect denied for $deviceId: ${decision.reason}")
            }
            is ReconnectDecision.Allowed -> {
                reconnectAttempts[deviceId] = attempts + 1
                val job = scope.launch {
                    if (decision.backoffMillis > 0) delay(decision.backoffMillis)
                    hooks.attemptReconnect(deviceId)
                }
                reconnectJobs[deviceId]?.cancel()
                reconnectJobs[deviceId] = job
                resources.register(
                    "reconnect:$deviceId",
                    OwnedResource.CoroutineJob(job),
                )
            }
        }
    }

    private fun cancelReconnects(reason: String) {
        reconnectJobs.values.forEach { it.cancel() }
        reconnectJobs.clear()
        hooks.recordDiagnostic("reconnects cancelled: $reason")
    }
}

/**
 * Platform hooks implemented by the host.
 */
interface LifecycleHooks {
    /** Device ids known from persistence. */
    suspend fun knownDevices(): List<String>

    /** Recovery input for a device. */
    suspend fun recoveryInput(deviceId: String): RecoveryInput

    /** Apply a recovery plan. */
    suspend fun applyRecovery(plan: RecoveryPlan)

    /** Reconnect context for a device. */
    fun reconnectContext(
        deviceId: String,
        trigger: ReconnectTrigger,
        attemptCount: Int,
    ): ReconnectContext

    /** Attempt a reconnect through the transport layer. */
    suspend fun attemptReconnect(deviceId: String)

    /** Privacy-safe diagnostic record. */
    fun recordDiagnostic(message: String)

    suspend fun onForeground()
    suspend fun onBackground()
    suspend fun onAdapterDisabled()
    suspend fun onPermissionRevoked()
    suspend fun onDeviceDisconnected(deviceId: String)
    suspend fun onDeviceReconnected(deviceId: String)
    suspend fun onTerminating()
}
