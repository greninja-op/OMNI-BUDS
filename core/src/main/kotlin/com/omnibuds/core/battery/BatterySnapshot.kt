package com.omnibuds.core.battery

import com.omnibuds.core.device.DeviceIdentity

/**
 * An immutable aggregate of one device's battery truth at one moment.
 *
 * Phase 16 (OB-P16-REQ-005): contains ONLY components actually observed.
 * Never manufactures missing components to look complete.
 */
data class BatterySnapshot(
    val snapshotId: Long,
    val device: DeviceIdentity,
    /** Session generation; observations from older generations are rejected. */
    val sessionGeneration: Long,
    val observedAtMillis: Long,
    /** Only components with at least one observation. */
    val components: Map<BatteryComponent, ComponentBatteryState>,
    val overallFreshness: BatteryFreshness,
    /** Structured warnings, e.g. conflicting sources, rejected stale update. */
    val warnings: List<String> = emptyList(),
) {
    init {
        require(snapshotId >= 0) { "snapshotId must be non-negative" }
        require(observedAtMillis >= 0) { "observedAtMillis must be non-negative" }
        require(components.keys.all { it != BatteryComponent.UNKNOWN }) {
            "Snapshot components must be identified; UNKNOWN components are not published"
        }
    }

    /** True when at least one component has a known level. */
    val hasAnyKnownLevel: Boolean
        get() = components.values.any { it.level != null }

    /** True when nothing at all is known. */
    val isEntirelyUnknown: Boolean
        get() = components.isEmpty()

    companion object {
        /** The honest empty snapshot. */
        fun empty(
            snapshotId: Long,
            device: DeviceIdentity,
            sessionGeneration: Long,
            observedAtMillis: Long,
        ): BatterySnapshot = BatterySnapshot(
            snapshotId = snapshotId,
            device = device,
            sessionGeneration = sessionGeneration,
            observedAtMillis = observedAtMillis,
            components = emptyMap(),
            overallFreshness = BatteryFreshness.UNKNOWN,
        )
    }
}
