package com.omnibuds.app.service

import android.companion.CompanionDeviceService
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * CompanionDeviceService allowing Android 12–15 to grant instant background foreground-service
 * launch privileges the moment associated earbuds appear or connect.
 */
@RequiresApi(Build.VERSION_CODES.S)
@Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
class OmniBudsCompanionDeviceService : CompanionDeviceService() {

    override fun onDeviceAppeared(address: String) {
        Log.i(TAG, "Companion device appeared: $address")
        OmniBudsForegroundService.startForAddress(this, address)
    }

    override fun onDeviceDisappeared(address: String) {
        Log.i(TAG, "Companion device disappeared: $address")
        OmniBudsForegroundService.checkAndStopIfNoDevices(this)
    }

    companion object {
        private const val TAG = "OmniBudsCompanion"
    }
}

