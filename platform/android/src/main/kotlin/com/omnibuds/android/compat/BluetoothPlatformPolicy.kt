package com.omnibuds.android.compat

/**
 * What the platform reports about Bluetooth availability.
 *
 * Phase 32: a permission grant never proves an operation will succeed;
 * an unavailable adapter is reported explicitly, never converted into
 * a false device capability or a false CONNECTED state.
 */
data class BluetoothPlatformState(
    /** True when the device has a Bluetooth adapter at all. */
    val adapterPresent: Boolean,
    /** True when the adapter is currently enabled. */
    val adapterEnabled: Boolean,
    val connectPermission: PermissionState,
    val scanPermission: PermissionState,
)

/**
 * Bluetooth platform decisions.
 */
sealed interface BluetoothDecision {
    data object MayOperate : BluetoothDecision
    data class CannotOperate(val reason: String) : BluetoothDecision
    data class Degraded(val reason: String, val allowed: String) : BluetoothDecision
}

/**
 * Pure Bluetooth platform-compatibility decisions.
 */
object BluetoothPlatformPolicy {

    /**
     * Decide what Bluetooth work is possible.
     */
    fun decide(state: BluetoothPlatformState): BluetoothDecision {
        if (!state.adapterPresent) {
            return BluetoothDecision.CannotOperate("no Bluetooth adapter on this device")
        }
        if (!state.adapterEnabled) {
            return BluetoothDecision.Degraded(
                "Bluetooth adapter disabled",
                "observe adapter state; no device operations",
            )
        }
        return when (
            PermissionPolicy.decide(state.connectPermission, "Bluetooth operation")
        ) {
            is PermissionDecision.Allow -> BluetoothDecision.MayOperate
            is PermissionDecision.Refuse ->
                BluetoothDecision.CannotOperate("BLUETOOTH_CONNECT not granted")
            is PermissionDecision.Defer ->
                BluetoothDecision.Degraded(
                    "permission state unknown",
                    "recheck permission before operating",
                )
        }
    }
}
