package com.omnibuds.desktop.presentation.battery

import com.omnibuds.core.globalstate.BatteryState

/**
 * Single battery component reading (e.g. left bud, right bud, or case).
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
}

/**
 * Honest, evidence-backed battery and power presentation model.
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
        fun unavailable(reason: String = "Battery telemetry is not exposed by this device or OS adapter."): BatteryPresentationModel =
            BatteryPresentationModel(
                isAvailable = false,
                components = emptyList(),
                overallPercent = null,
                isCharging = null,
                lastUpdatedMillis = null,
                isStale = false,
                unavailableReason = reason,
            )

        fun fromCoreState(
            batteryState: BatteryState,
            currentEpochMillis: Long = System.currentTimeMillis(),
            staleThresholdMillis: Long = 60_000L,
        ): BatteryPresentationModel = when (batteryState) {
            is BatteryState.Unknown -> unavailable()
            is BatteryState.Known -> {
                val observedAt = batteryState.observation?.provenance?.observedAtMillis
                val isStale = if (observedAt != null) {
                    (currentEpochMillis - observedAt) > staleThresholdMillis
                } else {
                    false
                }

                val components = mutableListOf<BatteryComponentReading>()
                if (batteryState.levelPercent != null) {
                    components.add(
                        BatteryComponentReading(
                            componentName = "Device",
                            levelPercent = batteryState.levelPercent,
                            isCharging = batteryState.charging,
                        ),
                    )
                }

                BatteryPresentationModel(
                    isAvailable = batteryState.levelPercent != null,
                    components = components,
                    overallPercent = batteryState.levelPercent,
                    isCharging = batteryState.charging,
                    lastUpdatedMillis = observedAt,
                    isStale = isStale,
                    unavailableReason = if (batteryState.levelPercent == null) "Battery level was not reported." else null,
                )
            }
        }
    }
}
