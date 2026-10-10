package com.omnibuds.android.presentation.battery

import com.omnibuds.core.globalstate.BatteryState

/**
 * Single battery component reading (e.g. left bud, right bud, charging case, or overall device).
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
}

/**
 * Pure presentation model for hardware battery state on Android.
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
        const val STALE_THRESHOLD_MS: Long = 60_000L

        fun unavailable(
            reason: String = "No public Android API exposes Bluetooth device battery telemetry as of API 35.",
        ): BatteryPresentationModel = BatteryPresentationModel(
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
            staleThresholdMillis: Long = STALE_THRESHOLD_MS,
        ): BatteryPresentationModel = when (batteryState) {
            is BatteryState.Unknown -> unavailable()
            is BatteryState.Known -> {
                val observedAt = batteryState.observation?.provenance?.observedAtMillis
                val isStale = if (observedAt != null) {
                    (currentEpochMillis - observedAt) > staleThresholdMillis
                } else {
                    false
                }

                BatteryPresentationModel(
                    isAvailable = true,
                    components = listOfNotNull(
                        batteryState.levelPercent?.let {
                            BatteryComponentReading(
                                componentName = "Headset",
                                levelPercent = it,
                                isCharging = batteryState.charging,
                            )
                        },
                    ),
                    overallPercent = batteryState.levelPercent,
                    isCharging = batteryState.charging,
                    lastUpdatedMillis = observedAt,
                    isStale = isStale,
                    unavailableReason = null,
                )
            }
        }

        fun fromComponents(
            left: Int?,
            right: Int?,
            case: Int?,
            leftCharging: Boolean? = null,
            rightCharging: Boolean? = null,
            caseCharging: Boolean? = null,
            observedAt: Long? = null,
            currentEpochMillis: Long = System.currentTimeMillis(),
        ): BatteryPresentationModel {
            val isAvailable = left != null || right != null || case != null
            val isStale = if (observedAt != null && observedAt > 0) {
                (currentEpochMillis - observedAt) > STALE_THRESHOLD_MS
            } else {
                false
            }
            val comps = mutableListOf<BatteryComponentReading>()
            if (left != null || leftCharging != null) {
                comps.add(BatteryComponentReading("Left", left, leftCharging))
            }
            if (right != null || rightCharging != null) {
                comps.add(BatteryComponentReading("Right", right, rightCharging))
            }
            if (case != null || caseCharging != null) {
                comps.add(BatteryComponentReading("Case", case, caseCharging))
            }

            return BatteryPresentationModel(
                isAvailable = isAvailable,
                components = comps,
                overallPercent = comps.mapNotNull { it.levelPercent }.average().takeIf { !it.isNaN() }?.toInt(),
                isCharging = leftCharging == true || rightCharging == true || caseCharging == true,
                lastUpdatedMillis = observedAt,
                isStale = isStale,
                unavailableReason = if (!isAvailable) "Battery telemetry not reported" else null,
            )
        }
    }
}
