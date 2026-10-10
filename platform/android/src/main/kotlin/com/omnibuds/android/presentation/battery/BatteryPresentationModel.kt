package com.omnibuds.android.presentation.battery

import com.omnibuds.core.globalstate.BatteryState
import com.omnibuds.core.presentation.battery.UnifiedBatteryComponent
import com.omnibuds.core.presentation.battery.UnifiedBatteryModel

/**
 * Single battery component reading (e.g. left bud, right bud, charging case, or overall device).
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
            levelPercent != null -> "$levelPercent%${if (isCharging == true) " ⚡" else ""}"
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
 * Pure presentation model for hardware battery state on Android.
 * Adapts unified core model (com.omnibuds.core.presentation.battery.UnifiedBatteryModel).
 * Preserves nullability strictly: never substitutes 0% for unknown readings.
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
    val hasAnyData: Boolean
        get() = overallPercent != null || components.any { it.levelPercent != null }

    val summaryText: String
        get() {
            if (!isAvailable || !hasAnyData) {
                return unavailableReason ?: "Battery telemetry unavailable"
            }
            if (components.isNotEmpty()) {
                val parts = components.filter { it.levelPercent != null }.map { "${it.componentName}: ${it.displayString}" }
                if (parts.isNotEmpty()) {
                    val suffix = if (isStale) " (stale)" else ""
                    return parts.joinToString(" • ") + suffix
                }
            }
            val base = overallPercent?.let { "$it%${if (isCharging == true) " ⚡" else ""}" } ?: "Unknown"
            val suffix = if (isStale) " (stale)" else ""
            return base + suffix
        }

    val talkBackDescription: String
        get() {
            if (!isAvailable || !hasAnyData) {
                return unavailableReason ?: "Battery level not reported by device"
            }
            val parts = mutableListOf<String>()
            if (components.isNotEmpty()) {
                for (comp in components) {
                    comp.levelPercent?.let {
                        parts.add("${comp.componentName} $it percent${if (comp.isCharging == true) ", charging" else ""}")
                    }
                }
            } else overallPercent?.let {
                parts.add("Battery $it percent${if (isCharging == true) ", charging" else ""}")
            }
            if (isStale) parts.add("reading may be outdated")
            return parts.joinToString(", ")
        }

    companion object {
        const val STALE_THRESHOLD_MS: Long = UnifiedBatteryModel.STALE_THRESHOLD_MS

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

        fun unavailable(
            reason: String = "No public Android API exposes Bluetooth device battery telemetry as of API 35.",
        ): BatteryPresentationModel = fromUnified(UnifiedBatteryModel.unavailable(reason))

        fun fromCoreState(
            batteryState: BatteryState,
            currentEpochMillis: Long = System.currentTimeMillis(),
            staleThresholdMillis: Long = STALE_THRESHOLD_MS,
        ): BatteryPresentationModel = fromUnified(
            UnifiedBatteryModel.fromCoreState(
                batteryState = batteryState,
                currentEpochMillis = currentEpochMillis,
                staleThresholdMillis = staleThresholdMillis,
            ),
        )

        fun fromComponents(
            left: Int?,
            right: Int?,
            case: Int?,
            leftCharging: Boolean? = null,
            rightCharging: Boolean? = null,
            caseCharging: Boolean? = null,
            observedAt: Long? = null,
            currentEpochMillis: Long = System.currentTimeMillis(),
            staleThresholdMillis: Long = STALE_THRESHOLD_MS,
        ): BatteryPresentationModel = fromUnified(
            UnifiedBatteryModel.fromComponents(
                left = left,
                right = right,
                case = case,
                leftCharging = leftCharging,
                rightCharging = rightCharging,
                caseCharging = caseCharging,
                observedAt = observedAt,
                currentEpochMillis = currentEpochMillis,
                staleThresholdMillis = staleThresholdMillis,
            ),
        )
    }
}
