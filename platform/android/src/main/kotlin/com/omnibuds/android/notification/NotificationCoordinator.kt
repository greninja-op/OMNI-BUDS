package com.omnibuds.android.notification

import com.omnibuds.core.globalstate.GlobalDeviceId
import com.omnibuds.core.globalstate.GlobalDeviceState
import com.omnibuds.core.globalstate.GlobalDeviceStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Lifecycle-aware notification coordinator.
 *
 * Phase 26 (OB-P26-REQ-001): consumes authoritative state, builds
 * notifications from immutable snapshots, deduplicates, cleans up on
 * disconnect. No polling; no permanent service.
 */
class NotificationCoordinator(
    private val repository: GlobalDeviceStateRepository,
    private val actionableFeatures: () -> Map<String, ActionMetadata>,
    private val notifier: Notifier,
    private val permissionCheck: () -> Boolean,
) {
    private var collectionJob: Job? = null
    private var lastRendered: NotificationState? = null
    private var lastFailure: String? = null
    private var selectedDevice: GlobalDeviceId? = null

    /**
     * Start reconciling. Safe to call repeatedly.
     */
    fun start(scope: CoroutineScope) {
        stop()
        collectionJob = scope.launch {
            repository.allDevices.collectLatest { devices ->
                reconcile(devices)
            }
        }
    }

    /** Stop reconciling and cancel collection. */
    fun stop() {
        collectionJob?.cancel()
        collectionJob = null
    }

    /**
     * Reconcile notifications against the latest device states.
     */
    fun reconcile(devices: Map<GlobalDeviceId, GlobalDeviceState>) {
        if (!permissionCheck()) {
            // Permission denied → remove notifications, keep architecture correct.
            if (lastRendered?.visible == true) {
                notifier.cancel(NotificationIds.NOTIFICATION_DEVICE_STATUS)
                lastRendered = null
            }
            return
        }

        val targetId = resolveTarget(devices)
        val state = targetId?.let { devices[it] }
        val next = NotificationStateMapper.map(state, actionableFeatures(), lastFailure)

        // Deduplication: identical updates are not re-posted.
        if (next == lastRendered) return

        if (!next.visible) {
            if (lastRendered?.visible == true) {
                notifier.cancel(next.notificationId)
            }
        } else {
            notifier.notify(next)
        }
        lastRendered = next
    }

    /** Record a failure to surface. */
    fun recordFailure(reason: String) {
        lastFailure = reason
    }

    /** Clear the failure surface. */
    fun clearFailure() {
        lastFailure = null
    }

    /** Set the explicitly selected device. */
    fun selectDevice(deviceId: GlobalDeviceId?) {
        selectedDevice = deviceId
    }

    private fun resolveTarget(
        devices: Map<GlobalDeviceId, GlobalDeviceState>,
    ): GlobalDeviceId? {
        if (devices.isEmpty()) return null
        // Reuse the tile target policy: explicit selection preferred,
        // single eligible resolves, multiple → null (no notification actions).
        val selected = selectedDevice?.let { devices[it] }
        if (selected != null &&
            selected.connection is com.omnibuds.core.globalstate.ConnectionState.Connected
        ) {
            return selected.deviceId
        }
        val eligible = devices.values.filter {
            it.connection is com.omnibuds.core.globalstate.ConnectionState.Connected
        }
        return if (eligible.size == 1) eligible[0].deviceId else null
    }
}

/**
 * Platform notification operations (implemented by the Android layer).
 */
interface Notifier {
    fun notify(state: NotificationState)
    fun cancel(notificationId: Int)
}
