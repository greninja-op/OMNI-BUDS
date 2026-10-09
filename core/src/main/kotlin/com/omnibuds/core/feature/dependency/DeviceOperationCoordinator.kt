package com.omnibuds.core.feature.dependency

import com.omnibuds.core.common.FeatureId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Device-scoped operation coordination.
 *
 * Phase 29 (OB-P29-REQ-012): prevents incompatible operations from racing
 * on the same device while preserving concurrency across devices. No
 * global locks are held across Bluetooth I/O — the mutex only guards
 * plan admission, not execution.
 */
class DeviceOperationCoordinator {
    private val lock = Any()
    private val deviceLocks = mutableMapOf<String, Mutex>()
    private val activePlans = mutableMapOf<String, MutableSet<String>>()
    private val planFeaturesById = mutableMapOf<String, Set<FeatureId>>()

    private fun mutexFor(deviceId: String): Mutex = synchronized(lock) {
        deviceLocks.getOrPut(deviceId) { Mutex() }
    }

    /**
     * Admit a plan for execution. Returns false when an incompatible plan
     * is already active for the device.
     */
    suspend fun admit(plan: OperationPlan): Boolean {
        val mutex = mutexFor(plan.deviceId)
        return mutex.withLock {
            val active = activePlans.getOrPut(plan.deviceId) { mutableSetOf() }
            // Incompatible when the feature sets overlap.
            val planFeatures = plan.steps.map { it.feature }.toSet()
            for (activePlanId in active) {
                // Overlap check needs the active plan's features; the
                // coordinator tracks feature sets per active plan.
                val activeFeatures = planFeaturesById[activePlanId].orEmpty()
                if (activeFeatures.intersect(planFeatures).isNotEmpty()) {
                    return@withLock false
                }
            }
            active.add(plan.planId)
            planFeaturesById[plan.planId] = planFeatures
            true
        }
    }

    /**
     * Release a plan after execution completes or is cancelled.
     */
    suspend fun release(plan: OperationPlan) {
        val mutex = mutexFor(plan.deviceId)
        mutex.withLock {
            activePlans[plan.deviceId]?.remove(plan.planId)
            planFeaturesById.remove(plan.planId)
        }
    }

    /** Number of active plans for a device (for tests/diagnostics). */
    suspend fun activeCount(deviceId: String): Int {
        val mutex = mutexFor(deviceId)
        return mutex.withLock { activePlans[deviceId]?.size ?: 0 }
    }
}
