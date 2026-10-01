package com.omnibuds.android.bluetooth.mapping

import android.bluetooth.BluetoothAdapter
import com.omnibuds.core.platform.BluetoothAdapterState

/**
 * Translates Android's adapter numbers into the domain's adapter state.
 *
 * This is the only place in the codebase permitted to know what `STATE_ON` means, which is what makes
 * the rest of the platform layer testable without a radio: everything above it deals in
 * [BluetoothAdapterState] values, and everything below it deals in raw integers.
 *
 * Unrecognised input becomes [BluetoothAdapterState.UNKNOWN] rather than the friendlier-looking
 * [BluetoothAdapterState.DISABLED]. A state we did not read is not a state we may report, and
 * "your Bluetooth is off" is a claim with a user-visible consequence - it would send someone to
 * Settings to fix a phone that was never queried (ADR-P0-016, Phase 2 prompt section 5.2).
 *
 * An absent adapter is reported before the raw value is considered, because `getState()` on a null
 * adapter yields nothing and a missing adapter is a different fact from an adapter that is off.
 */
fun bluetoothAdapterStateOf(adapterPresent: Boolean, rawState: Int?): BluetoothAdapterState = when {
    !adapterPresent -> BluetoothAdapterState.UNAVAILABLE
    rawState == null -> BluetoothAdapterState.UNKNOWN
    rawState == BluetoothAdapter.STATE_ON -> BluetoothAdapterState.ENABLED
    rawState == BluetoothAdapter.STATE_TURNING_ON -> BluetoothAdapterState.ENABLING
    rawState == BluetoothAdapter.STATE_OFF -> BluetoothAdapterState.DISABLED
    rawState == BluetoothAdapter.STATE_TURNING_OFF -> BluetoothAdapterState.DISABLING
    else -> BluetoothAdapterState.UNKNOWN
}

/**
 * The value handed back when a broadcast carries no adapter-state extra.
 *
 * `-1` is chosen because it is outside the platform's own adapter-state range, so
 * [bluetoothAdapterStateOf] resolves it to [BluetoothAdapterState.UNKNOWN] instead of reading it as
 * one of the four real states. It is a missing-value marker, not a state.
 */
const val RAW_STATE_UNREADABLE: Int = -1
