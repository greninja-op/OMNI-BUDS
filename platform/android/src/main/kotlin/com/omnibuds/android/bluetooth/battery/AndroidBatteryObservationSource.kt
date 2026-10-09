package com.omnibuds.android.bluetooth.battery

import android.os.Build
import com.omnibuds.core.battery.BatteryObservationSource
import com.omnibuds.core.battery.BatteryUpdate
import com.omnibuds.core.battery.RefreshResult
import com.omnibuds.core.device.DeviceIdentity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Android platform battery observations for Bluetooth audio devices.
 *
 * Phase 16 (OB-P16-REQ-007): honesty about platform limits.
 *
 * As of API 35, the Android SDK exposes NO public API for reading a
 * connected Bluetooth audio device's battery level:
 * - `BluetoothDevice.getBatteryLevel()` is a hidden (`@hide`) API.
 * - No public broadcast delivers per-device battery levels.
 * - GATT Battery Service (0x180F) is not universally present on headsets,
 *   and scanning arbitrary services is out of scope (OB-P16-REQ-010).
 *
 * This source therefore reports itself as unsupported with the reason
 * recorded. It exists so the limitation is explicit and testable — not
 * hidden. If a future API level adds a public mechanism, the API-level
 * guard below is the single place to enable it.
 *
 * No permissions are requested. No polling. No fabrication.
 */
class AndroidBatteryObservationSource : BatteryObservationSource {

    override val sourceName: String = "android-platform"

    /**
     * True when the running API level exposes a public Bluetooth battery
     * API. Checked against the SDK, not assumed.
     */
    fun isSupported(): Boolean {
        // No public API exists as of API 35. When one lands, gate it here.
        return false
    }

    /** The API level this assessment was made against. */
    fun assessedApiLevel(): Int = Build.VERSION.SDK_INT

    override fun observe(device: DeviceIdentity): Flow<BatteryUpdate> {
        // No legitimate observation mechanism: emit nothing.
        return emptyFlow()
    }

    override suspend fun refresh(device: DeviceIdentity): RefreshResult =
        RefreshResult.Unsupported(
            reason = "No public Android API exposes Bluetooth device battery " +
                "as of API ${Build.VERSION.SDK_INT}; BluetoothDevice.getBatteryLevel() is hidden.",
        )
}
