package com.omnibuds.app.service

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.omnibuds.app.AudioWearableFilter

/**
 * Static system-level BroadcastReceiver that wakes up OmniBuds immediately when
 * any Bluetooth audio device connects (even if the app is killed or device just rebooted).
 */
class BluetoothConnectionAutoReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "BluetoothConnectionAutoReceiver received: $action")

        when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val device: BluetoothDevice? = intent.getDeviceExtra()
                if (device != null && AudioWearableFilter.isAudioWearable(device)) {
                    Log.i(TAG, "ACL_CONNECTED for audio wearable: ${device.address}. Starting foreground notification.")
                    OmniBudsForegroundService.startForDevice(context, device)
                }
            }

            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                val device: BluetoothDevice? = intent.getDeviceExtra()
                if (state == BluetoothProfile.STATE_CONNECTED && device != null && AudioWearableFilter.isAudioWearable(device)) {
                    Log.i(TAG, "A2DP CONNECTED for ${device.address}. Starting foreground service.")
                    OmniBudsForegroundService.startForDevice(context, device)
                } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i(TAG, "A2DP DISCONNECTED.")
                    OmniBudsForegroundService.checkAndStopIfNoDevices(context)
                }
            }

            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                val device: BluetoothDevice? = intent.getDeviceExtra()
                if (state == BluetoothProfile.STATE_CONNECTED && device != null && AudioWearableFilter.isAudioWearable(device)) {
                    Log.i(TAG, "Headset profile CONNECTED for ${device.address}. Starting foreground service.")
                    OmniBudsForegroundService.startForDevice(context, device)
                } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.i(TAG, "Headset profile DISCONNECTED.")
                    OmniBudsForegroundService.checkAndStopIfNoDevices(context)
                }
            }

            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                Log.i(TAG, "ACL_DISCONNECTED received. Checking remaining connected devices.")
                OmniBudsForegroundService.checkAndStopIfNoDevices(context)
            }

            Intent.ACTION_BOOT_COMPLETED -> {
                Log.i(TAG, "BOOT_COMPLETED received. Checking if an earbud is already connected.")
                OmniBudsForegroundService.checkConnectedAndStart(context)
            }

            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (btState == BluetoothAdapter.STATE_ON) {
                    Log.i(TAG, "Bluetooth turned ON. Checking connected devices.")
                    OmniBudsForegroundService.checkConnectedAndStart(context)
                } else if (btState == BluetoothAdapter.STATE_OFF || btState == BluetoothAdapter.STATE_TURNING_OFF) {
                    Log.i(TAG, "Bluetooth turned OFF. Stopping notification service.")
                    OmniBudsForegroundService.stopService(context)
                }
            }
        }
    }

    private fun Intent.getDeviceExtra(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    companion object {
        private const val TAG = "OmniBudsAutoReceiver"
    }
}

