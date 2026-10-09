package com.omnibuds.android.widget

import com.omnibuds.core.globalstate.GlobalDeviceId
import com.omnibuds.core.globalstate.GlobalDeviceState
import com.omnibuds.core.globalstate.GlobalDeviceStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Lifecycle-aware widget coordinator.
 *
 * Phase 27 (OB-P27-REQ-001/008): consumes authoritative state, renders per
 * widget instance, deduplicates identical updates, cleans up removed
 * instances. No polling; no permanent service.
 */
class WidgetCoordinator(
    private val repository: GlobalDeviceStateRepository,
    private val actionableFeatures: () -> Map<String, WidgetActionMetadata>,
    private val renderer: WidgetRenderer,
    private val selectedDevice: () -> GlobalDeviceId?,
) {
    private var collectionJob: Job? = null
    private val bindings = mutableMapOf<Int, GlobalDeviceId?>()
    private val lastRendered = mutableMapOf<Int, WidgetState>()
    private val failures = mutableMapOf<Int, String>()

    /** The target bound to a widget instance, for the action dispatcher. */
    fun bindingOf(widgetId: Int): GlobalDeviceId? = bindings[widgetId]

    /**
     * Register a widget instance. Safe to call repeatedly.
     */
    fun register(widgetId: Int, scope: CoroutineScope) {
        bindings.putIfAbsent(widgetId, null)
        ensureCollecting(scope)
    }

    /**
     * Remove a widget instance and its associated state.
     */
    fun unregister(widgetId: Int) {
        bindings.remove(widgetId)
        lastRendered.remove(widgetId)
        failures.remove(widgetId)
        renderer.remove(widgetId)
        if (bindings.isEmpty()) stop()
    }

    /** Start collecting state. */
    fun start(scope: CoroutineScope) {
        ensureCollecting(scope)
    }

    /** Stop collecting and cancel. */
    fun stop() {
        collectionJob?.cancel()
        collectionJob = null
    }

    /** Record a failure for an instance. */
    fun recordFailure(widgetId: Int, reason: String) {
        failures[widgetId] = reason
    }

    /** Clear the failure for an instance. */
    fun clearFailure(widgetId: Int) {
        failures.remove(widgetId)
    }

    private fun ensureCollecting(scope: CoroutineScope) {
        if (collectionJob?.isActive == true) return
        stop()
        collectionJob = scope.launch {
            repository.allDevices.collectLatest { devices -> reconcile(devices) }
        }
    }

    /**
     * Reconcile all registered instances against the latest states.
     */
    fun reconcile(devices: Map<GlobalDeviceId, GlobalDeviceState>) {
        for (widgetId in bindings.keys.toList()) {
            val bound = bindings[widgetId]
            val resolution = WidgetTargetResolver.resolve(devices, selectedDevice(), bound)
            val targetId = (resolution as? WidgetTargetResolution.Target)?.deviceId
            if (targetId != null) bindings[widgetId] = targetId
            val state = targetId?.let { devices[it] }
            val next = WidgetStateMapper.map(widgetId, state, actionableFeatures(), failures[widgetId])

            // Deduplication: identical updates are not re-rendered.
            if (next == lastRendered[widgetId]) continue

            renderer.render(next)
            lastRendered[widgetId] = next
        }
    }
}

/**
 * Platform widget rendering (implemented by the Android layer).
 */
interface WidgetRenderer {
    fun render(state: WidgetState)
    fun remove(widgetId: Int)
}
