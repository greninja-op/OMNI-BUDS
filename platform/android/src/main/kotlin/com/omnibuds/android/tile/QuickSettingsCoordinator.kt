package com.omnibuds.android.tile

import com.omnibuds.core.globalstate.GlobalDeviceId
import com.omnibuds.core.globalstate.GlobalDeviceState
import com.omnibuds.core.globalstate.GlobalDeviceStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Lifecycle-aware coordinator between the tile and the state engine.
 *
 * Phase 25 (OB-P25-REQ-009/021/024): reads latest state on start-listening,
 * updates while permitted, cancels on stop. No permanent background service;
 * no duplicate collectors.
 */
class QuickSettingsCoordinator(
    private val repository: GlobalDeviceStateRepository,
    private val dispatcher: TileActionDispatcher,
    private val controllableFeatures: () -> Set<String>,
) {
    private var collectionJob: Job? = null
    private var lastFailure: String? = null
    private var selectedDevice: GlobalDeviceId? = null

    /**
     * Start observing. Calls [render] with the latest [TileState] whenever
     * the underlying state changes. Safe to call repeatedly — previous
     * collection is cancelled first.
     */
    fun startListening(scope: CoroutineScope, render: (TileState) -> Unit) {
        stopListening()
        collectionJob = scope.launch {
            repository.allDevices.collectLatest { devices ->
                val target = TileTargetResolver.resolve(devices, selectedDevice)
                val deviceId = (target as? TargetResolution.Resolved)?.deviceId
                val state: GlobalDeviceState? = deviceId?.let { devices[it] }
                render(TileStateMapper.map(state, controllableFeatures(), lastFailure))
            }
        }
    }

    /** Stop observing and cancel in-flight work. */
    fun stopListening() {
        collectionJob?.cancel()
        collectionJob = null
    }

    /**
     * Handle a tile click. Returns the dispatch outcome.
     *
     * @param devices the latest device states.
     * @param featureId the feature to toggle.
     * @param modes the feature's real mode set.
     * @param writable whether the feature is writable.
     */
    suspend fun onClick(
        devices: Map<GlobalDeviceId, GlobalDeviceState>,
        featureId: String,
        modes: List<String>,
        writable: Boolean,
    ): TileDispatchOutcome {
        lastFailure = null
        val target = TileTargetResolver.resolve(devices, selectedDevice)
        val deviceId = (target as? TargetResolution.Resolved)?.deviceId
            ?: return TileDispatchOutcome.Refused(
                when (target) {
                    is TargetResolution.NoDevice ->
                        TileDispatchRefusal.NoTarget(target.reason)
                    is TargetResolution.Ambiguous ->
                        TileDispatchRefusal.AmbiguousTarget(target.candidates)
                    is TargetResolution.Unresolvable ->
                        TileDispatchRefusal.NoTarget(target.reason)
                    is TargetResolution.Resolved -> error("unreachable")
                },
            )
        val state = devices[deviceId]
            ?: return TileDispatchOutcome.Refused(
                TileDispatchRefusal.NoTarget("target device state unavailable"),
            )
        val outcome = dispatcher.prepareToggle(state, featureId, modes, writable)
        if (outcome is TileDispatchOutcome.Refused) {
            lastFailure = outcome.reason.toString()
        }
        return outcome
    }

    /** Set the explicitly selected device, or null to clear. */
    fun selectDevice(deviceId: GlobalDeviceId?) {
        selectedDevice = deviceId
    }

    /** Record a failure to surface on the next render. */
    fun recordFailure(reason: String) {
        lastFailure = reason
    }

    /** Clear the failure surface. */
    fun clearFailure() {
        lastFailure = null
    }
}
