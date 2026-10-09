package com.omnibuds.core.vendor.apple

import com.omnibuds.core.device.BatteryState

/**
 * Capability scope for the AirPods family adapter (Phase 42).
 *
 * Two groups:
 * - Read-only observations backed by standard Android Bluetooth APIs.
 * - Apple-specific controls that are explicitly UNSUPPORTED.
 *
 * No feature here is controllable; there are no write operations.
 */
object AirpodsCapabilityScope {

    /** Observations OmniBuds may report when Android exposes them. */
    enum class ReadOnlyObservation(val description: String) {
        CONNECTION_STATE("Bluetooth connection state via Android"),
        DEVICE_NAME("Bluetooth display name (not used for model identity)"),
        BOND_STATE("Pairing/bond state via Android"),
        AUDIO_PROFILES("Connected A2DP/HFP profiles via Android"),
        BATTERY_LEVEL("Android-reported battery; null when unreported"),
        CODEC_INFO("Android-exposed codec info; UNKNOWN when absent"),
    }

    /** Apple-specific features that remain unsupported on Android. */
    enum class UnsupportedFeature(val reason: String) {
        NOISE_CONTROL_SET(
            "No legitimate Android-accessible control mechanism; " +
                "on-device stem control works independently",
        ),
        ADAPTIVE_AUDIO("iOS-only; no third-party mechanism"),
        SPATIAL_AUDIO("Apple ecosystem feature; no third-party mechanism"),
        HEAD_TRACKING("Apple ecosystem feature; no third-party mechanism"),
        EAR_DETECTION_CONTROL("No legitimate Android observation/control path"),
        GESTURE_CUSTOMIZATION("Requires Apple device settings"),
        FIRMWARE_UPDATE("Requires iPhone/iPad/Mac"),
        FIND_MY("Apple network; not available on Android"),
        SIRI("iOS-exclusive"),
        AUTO_SWITCHING("Apple ecosystem feature"),
        PER_BUD_BATTERY(
            "Android exposes at most one aggregate battery value; " +
                "per-bud and case values are unavailable via supported APIs",
        ),
        CASE_BATTERY(
            "Not exposed via supported Android APIs; never inferred " +
                "from earbud values",
        ),
        MULTIPOINT("Apple ecosystem behavior; no third-party mechanism"),
        CONVERSATION_AWARENESS("iOS-only; no third-party mechanism"),
    }

    /**
     * Maps an Android-reported battery value to [BatteryState].
     *
     * Android exposes at most one aggregate battery value for AirPods.
     * It is recorded as the left level is NOT done — recording it
     * against a single earbud would fabricate per-bud data. The value
     * is kept in a dedicated aggregate slot: here we record it as
     * unknown-per-source and keep every per-source field null, because
     * the source does not distinguish buds from case.
     *
     * In practice: a single Android value has no provenance, so all
     * fields stay null unless a source explicitly distinguishes them.
     */
    fun batteryFromAndroidAggregate(
        aggregatePercent: Int?,
        sourceDistinguishesBuds: Boolean,
    ): BatteryState {
        if (aggregatePercent == null) return BatteryState()
        // Without per-source provenance we refuse to attribute the value.
        if (!sourceDistinguishesBuds) return BatteryState()
        return BatteryState(
            leftLevel = aggregatePercent,
            rightLevel = aggregatePercent,
        )
    }
}
