package com.omnibuds.core.platform

/**
 * A platform-level Bluetooth feature whose availability OmniBuds may eventually need to report.
 *
 * These are phone capabilities, not headset capabilities. Keeping them separate from
 * [com.omnibuds.core.common.FeatureId] - which names something a *device* can do - is what stops
 * "this phone has LE Audio APIs" from being rendered as "your earbuds support LE Audio".
 */
enum class PlatformFeature(
    /** Stable identifier for reports and diagnostics. */
    val technicalName: String,
) {
    CLASSIC_BLUETOOTH("platform.classic-bluetooth"),
    BLE_CENTRAL("platform.ble-central"),
    GATT_CLIENT("platform.gatt-client"),
    RFCOMM_CLIENT("platform.rfcomm-client"),
    LE_AUDIO("platform.le-audio"),
    A2DP_CONNECTION_STATE("platform.a2dp-connection-state"),
    HEADSET_CONNECTION_STATE("platform.headset-connection-state"),
    ADAPTER_STATE_OBSERVATION("platform.adapter-state-observation"),
}
