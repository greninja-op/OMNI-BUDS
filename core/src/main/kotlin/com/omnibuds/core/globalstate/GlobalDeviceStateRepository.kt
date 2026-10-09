package com.omnibuds.core.globalstate

import com.omnibuds.core.platform.TimeProvider
import com.omnibuds.core.platform.NoTimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reason a device's state was removed.
 */
enum class DeviceStateRemovalReason {
    USER_REMOVED,
    FORGOTTEN,
    REPLACED,
}

/**
 * Global device state repository.
 *
 * Phase 24 (OB-P24-REQ-013): typed observation APIs. StateFlow-based
 * (conflated — slow collectors receive the latest snapshot, never a
 * backlog). One aggregator per device guarantees isolation.
 */
class GlobalDeviceStateRepository(
    private val time: TimeProvider = NoTimeProvider,
) {
    private val mutex = Mutex()
    private val aggregators = mutableMapOf<String, DeviceStateAggregator>()
    private val removed = mutableSetOf<String>()

    private val _allDevices =
        MutableStateFlow<Map<GlobalDeviceId, GlobalDeviceState>>(emptyMap())
    /** Aggregate multi-device observation. */
    val allDevices: Flow<Map<GlobalDeviceId, GlobalDeviceState>> = _allDevices.asStateFlow()

    /** Observe a single device; null when never observed or removed. */
    fun observeDevice(deviceId: GlobalDeviceId): Flow<GlobalDeviceState?> =
        _allDevices.map { it[deviceId] }

    /** Current snapshot for a device, or null. */
    suspend fun getDevice(deviceId: GlobalDeviceId): GlobalDeviceState? =
        mutex.withLock { aggregators[deviceId.value]?.snapshot() }

    /**
     * Ingest an event for a device. Creates the aggregator on first event.
     * Events for removed devices are rejected.
     */
    suspend fun ingest(event: DeviceStateEvent): IngestResult {
        val aggregator = mutex.withLock {
            if (event.deviceId.value in removed) {
                return IngestResult.Rejected(
                    EventRejection.UnknownDevice(event.deviceId.value),
                )
            }
            aggregators.getOrPut(event.deviceId.value) {
                DeviceStateAggregator(event.deviceId, time)
            }
        }
        val result = aggregator.ingest(event)
        if (result == IngestResult.Applied) {
            publish()
        }
        return result
    }

    /**
     * Remove a device's state. Disconnected ≠ removed: removal is explicit.
     */
    suspend fun removeDeviceState(
        deviceId: GlobalDeviceId,
        reason: DeviceStateRemovalReason,
    ) {
        mutex.withLock {
            aggregators.remove(deviceId.value)
            removed.add(deviceId.value)
        }
        publish()
    }

    /** Whether a device id was explicitly removed. */
    suspend fun isRemoved(deviceId: GlobalDeviceId): Boolean =
        mutex.withLock { deviceId.value in removed }

    private suspend fun publish() {
        val snapshot = mutex.withLock {
            aggregators.mapValues { (id, agg) -> GlobalDeviceId(id) to agg.snapshot() }
                .mapKeys { it.value.first }
                .mapValues { it.value.second }
        }
        _allDevices.value = snapshot
    }
}
