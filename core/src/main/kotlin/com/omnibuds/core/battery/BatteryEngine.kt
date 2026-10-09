package com.omnibuds.core.battery

import com.omnibuds.core.device.DeviceIdentity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The Battery & Power State Engine.
 *
 * Phase 16 (OB-P16-REQ-011, OB-P16-REQ-019): per-device observation
 * repository publishing immutable [BatterySnapshot]s via [StateFlow].
 *
 * Rules:
 * - Updates from an older session generation are rejected (stale session).
 * - Omitted fields never disturb existing component state.
 * - Identical snapshots are deduplicated (no redundant emissions).
 * - Disconnect marks components stale and invalidates charging assumptions;
 *   battery is never zeroed on disconnect.
 * - No polling, no retries, no global state — one flow per device.
 */
class BatteryEngine(
    private val clock: () -> Long = { System.currentTimeMillis() },
    /** After this age a CURRENT observation becomes STALE. Documented policy. */
    private val staleAfterMillis: Long = 5 * 60 * 1000L,
) {
    private val mutex = Mutex()
    private val snapshots = mutableMapOf<DeviceIdentity, BatterySnapshot>()
    private val flows = mutableMapOf<DeviceIdentity, MutableStateFlow<BatterySnapshot>>()
    private val snapshotIds = mutableMapOf<DeviceIdentity, Long>()
    private val generations = mutableMapOf<DeviceIdentity, Long>()

    /**
     * Observe battery snapshots for a device. The flow always replays the
     * latest snapshot (initially an empty unknown snapshot).
     */
    fun observe(device: DeviceIdentity): StateFlow<BatterySnapshot> {
        val flow = flows.getOrPut(device) {
            MutableStateFlow(
                BatterySnapshot.empty(
                    snapshotId = 0,
                    device = device,
                    sessionGeneration = generations.getOrDefault(device, 0),
                    observedAtMillis = clock(),
                ),
            )
        }
        return flow.asStateFlow()
    }

    /** The latest snapshot for a device, or null when never observed. */
    fun latest(device: DeviceIdentity): BatterySnapshot? = snapshots[device]

    /**
     * Apply a partial update from a source.
     * @return the new snapshot, or null when the update was rejected
     * (stale session) or carried no new information.
     */
    suspend fun applyUpdate(device: DeviceIdentity, update: BatteryUpdate): BatterySnapshot? =
        mutex.withLock {
            val currentGeneration = generations.getOrDefault(device, 0)
            if (update.sessionGeneration < currentGeneration) {
                return@withLock null // Stale session: reject.
            }
            if (update.sessionGeneration > currentGeneration) {
                generations[device] = update.sessionGeneration
            }
            if (update.isEmpty) return@withLock null

            val current = snapshots[device]
            val existing = current?.components?.get(update.component)
                ?: ComponentBatteryState.unknown(update.component)

            val newLevel = when (val f = update.level) {
                is UpdateField.Omitted -> existing.level
                is UpdateField.ExplicitUnknown -> null
                is UpdateField.Set -> f.value
            }
            val newCharging = when (val f = update.charging) {
                is UpdateField.Omitted -> existing.charging
                is UpdateField.ExplicitUnknown -> ChargingState.UNKNOWN
                is UpdateField.Set -> f.value
            }

            // Deduplicate: no meaningful change → no new snapshot.
            if (existing.level == newLevel && existing.charging == newCharging &&
                existing.freshness == BatteryFreshness.CURRENT
            ) {
                return@withLock null
            }

            val newComponent = existing.copy(
                level = newLevel,
                charging = newCharging,
                observedAtMillis = update.observedAtMillis,
                freshness = BatteryFreshness.CURRENT,
                evidence = update.evidence ?: existing.evidence,
            )
            val newComponents = (current?.components ?: emptyMap()) + (update.component to newComponent)
            val id = (snapshotIds[device] ?: 0) + 1
            snapshotIds[device] = id
            val snapshot = BatterySnapshot(
                snapshotId = id,
                device = device,
                sessionGeneration = update.sessionGeneration,
                observedAtMillis = update.observedAtMillis,
                components = newComponents,
                overallFreshness = BatteryFreshness.CURRENT,
            )
            snapshots[device] = snapshot
            flows.getOrPut(device) {
                MutableStateFlow(snapshot)
            }.value = snapshot
            snapshot
        }

    /**
     * Handle device disconnection: bump the session generation (old
     * observations can no longer overwrite new-session state), mark
     * components stale, and invalidate charging assumptions. Battery levels
     * are preserved as stale history — never zeroed.
     */
    suspend fun onDisconnect(device: DeviceIdentity): BatterySnapshot? = mutex.withLock {
        val newGeneration = generations.getOrDefault(device, 0) + 1
        generations[device] = newGeneration
        val current = snapshots[device] ?: return@withLock null

        val staleComponents = current.components.mapValues { (_, c) ->
            c.copy(
                freshness = BatteryFreshness.STALE,
                charging = ChargingState.UNKNOWN, // Charging assumptions invalidated.
            )
        }
        val id = (snapshotIds[device] ?: 0) + 1
        snapshotIds[device] = id
        val snapshot = current.copy(
            snapshotId = id,
            sessionGeneration = newGeneration,
            observedAtMillis = clock(),
            components = staleComponents,
            overallFreshness = BatteryFreshness.STALE,
        )
        snapshots[device] = snapshot
        flows[device]?.value = snapshot
        snapshot
    }

    /**
     * Age out stale observations per the freshness policy. Components older
     * than [staleAfterMillis] transition CURRENT → STALE.
     */
    suspend fun applyFreshnessPolicy(device: DeviceIdentity): BatterySnapshot? = mutex.withLock {
        val current = snapshots[device] ?: return@withLock null
        val now = clock()
        var changed = false
        val aged = current.components.mapValues { (_, c) ->
            if (c.freshness == BatteryFreshness.CURRENT &&
                c.observedAtMillis != null &&
                now - c.observedAtMillis > staleAfterMillis
            ) {
                changed = true
                c.copy(freshness = BatteryFreshness.STALE)
            } else {
                c
            }
        }
        if (!changed) return@withLock null
        val id = (snapshotIds[device] ?: 0) + 1
        snapshotIds[device] = id
        val snapshot = current.copy(
            snapshotId = id,
            observedAtMillis = now,
            components = aged,
            overallFreshness = BatteryFreshness.STALE,
        )
        snapshots[device] = snapshot
        flows[device]?.value = snapshot
        snapshot
    }
}
