package com.omnibuds.android.hardware.capability

import com.omnibuds.android.ui.compose.model.AudioFormFactor

/**
 * The five battery presentation topologies. Realme TWS stays on
 * POGO_PIN_CACHED_CASE and keeps its existing pogo-pin/cache behavior.
 */
enum class BatteryTopologyMode {
    POGO_PIN_CACHED_CASE,
    ALWAYS_LIVE_3_WAY,
    SPLIT_LR_NO_CASE,
    DYNAMIC_COMBINED_FALLBACK,
    SINGLE_UNIT_HEADSET,
}

object BatteryTopologyResolver {

    private val alwaysLiveTokens = listOf(
        "airpods pro 2", "airpods pro 3", "airpods 4 with", "airpods 5",
        "jbl tour pro 3", "jbl live buds 3", "jbl live beam 3", "jbl live flex 3",
        "freebuds pro 5",
    )

    private val noCaseTokens = listOf(
        "cmf buds neo", "nothing ear (open)", "bose ultra open", "openfit air",
    )

    private val pogoTokens = listOf(
        "realme", "oneplus", "oppo", "nothing ear", "cmf buds pro",
        "cmf buds 2", "galaxy buds", "sony wf", "redmi buds", "xiaomi buds",
        "soundcore liberty", "soundcore space", "buds air", "buds t",
    )

    fun resolve(
        manufacturer: String,
        model: String,
        formFactor: AudioFormFactor,
    ): BatteryTopologyMode {
        val text = "$manufacturer $model".lowercase()

        if (formFactor == AudioFormFactor.NECKBAND ||
            formFactor == AudioFormFactor.HEADSET_OVER_ON_EAR ||
            formFactor == AudioFormFactor.HEADSET_OPEN_BACK ||
            formFactor == AudioFormFactor.BONE_CONDUCTION
        ) {
            return BatteryTopologyMode.SINGLE_UNIT_HEADSET
        }

        // CMF Buds 1 (B168) has no case telemetry. It must stay split L/R only.
        if (text.contains("cmf buds") && !text.contains("pro") && !text.contains("2")) {
            return BatteryTopologyMode.SPLIT_LR_NO_CASE
        }

        if (noCaseTokens.any { text.contains(it) }) {
            return BatteryTopologyMode.SPLIT_LR_NO_CASE
        }

        if (alwaysLiveTokens.any { text.contains(it) }) {
            return BatteryTopologyMode.ALWAYS_LIVE_3_WAY
        }

        if (pogoTokens.any { text.contains(it) }) {
            return BatteryTopologyMode.POGO_PIN_CACHED_CASE
        }

        return BatteryTopologyMode.DYNAMIC_COMBINED_FALLBACK
    }
}
