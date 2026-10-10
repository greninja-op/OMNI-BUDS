package com.omnibuds.desktop.presentation.battery

import com.omnibuds.core.globalstate.BatteryState
import com.omnibuds.core.presentation.battery.UnifiedBatteryComponent
import com.omnibuds.core.presentation.battery.UnifiedBatteryModel

/**
 * Single battery component reading (e.g. left bud, right bud, or case).
 * Backed by unified core model.
 */
data class BatteryComponentReading(
    val componentName: String,
    val levelPercent: Int?,
    val isCharging: Boolean?,
    val isPresent: Boolean = true,
) {
    init {
        levelPercent?.let {
            require(it in 0..100) { "levelPercent must be between 0 and 100 or null, was $it" }
        }
    }

    val displayString: String
        get() = when {
            levelPercent != null -> "$levelPercent%"
            else -> "Unknown"
        }

    fun toUnified(): UnifiedBatteryComponent = UnifiedBatteryComponent(
        componentName = componentName,
        levelPercent = levelPercent,
        isCharging = isCharging,
        isPresent = isPresent,
    )

    companion object {
        fun fromUnified(core: UnifiedBatteryComponent): BatteryComponentReading =
            BatteryComponentReading(
                componentName = core.componentName,
                levelPercent = core.levelPercent,
                isCharging = core.isCharging,
                isPresent = core.isPresent,
            )
    }
}

/**
 * Honest, evidence-backed battery and power presentation model for desktop.
 * Adapts unified core model (com.omnibuds.core.presentation.battery.UnifiedBatteryModel).
 */
data class BatteryPresentationModel(
    val isAvailable: Boolean = false,
    val components: List<BatteryComponentReading> = emptyList(),
    val overallPercent: Int? = null,
    val isCharging: Boolean? = null,
    val lastUpdatedMillis: Long? = null,
    val isStale: Boolean = false,
    val unavailableReason: String? = null,
) {
    companion object {
        fun fromUnified(core: UnifiedBatteryModel): BatteryPresentationModel =
            BatteryPresentationModel(
                isAvailable = core.isAvailable,
                components = core.components.map { BatteryComponentReading.fromUnified(it) },
                overallPercent = core.overallPercent,
                isCharging = core.isCharging,
                lastUpdatedMillis = core.lastUpdatedMillis,
                isStale = core.isStale,
                unavailableReason = core.unavailableReason,
            )

        fun unavailable(reason: String = "Battery telemetry is not exposed by this device or OS adapter."): BatteryPresentationModel =
            fromUnified(UnifiedBatteryModel.unavailable(reason))

        fun fromCoreState(
            batteryState: BatteryState,
            currentEpochMillis: Long = System.currentTimeMillis(),
            staleThresholdMillis: Long = 60_000L,
        ): BatteryPresentationModel =
            fromUnified(
                UnifiedBatteryModel.fromCoreState(
                    batteryState = batteryState,
                    currentEpochMillis = currentEpochMillis,
                    staleThresholdMillis = staleThresholdMillis,
                ),
            )
    }
}
