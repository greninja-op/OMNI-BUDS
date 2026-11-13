package com.omnibuds.app.service

import android.app.Notification
import android.app.Service
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.omnibuds.android.hardware.engine.UniversalTwsEngine
import com.omnibuds.android.notification.OmniBudsQuickNotificationManager
import com.omnibuds.android.presentation.workspace.ProductCatalog
import com.omnibuds.app.AudioWearableFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Persistent Foreground Service maintaining the OmniBuds system notification panel,
 * active Bluetooth GATT/RFCOMM connection, and real-time battery & ANC telemetry
 * across the entire device lifecycle, surviving app closing and task removal.
 */
class OmniBudsForegroundService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var telemetryJob: Job? = null

    private var activeDeviceAddress: String? = null
    private var activeDeviceName: String? = null
    private var activeMode: String = "Ambient"
    private var currentLeftBattery: Int? = null
    private var currentRightBattery: Int? = null
    private var currentCaseBattery: Int? = null
    private var currentAggregateBattery: Int? = null

    inner class LocalBinder : Binder() {
        fun getService(): OmniBudsForegroundService = this@OmniBudsForegroundService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "OmniBudsForegroundService created")
        UniversalTwsEngine.init(applicationContext)
        startObservingTelemetry()

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        try {
            registerReceiver(serviceDisconnectReceiver, filter)
            serviceReceiverRegistered = true
        } catch (_: Exception) {}
    }

    private var serviceReceiverRegistered = false
    private val serviceDisconnectReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            when (action) {
                BluetoothDevice.ACTION_ACL_DISCONNECTED,
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val dev = findConnectedAudioDevice(this@OmniBudsForegroundService)
                    if (dev == null) {
                        Log.i(TAG, "Audio device disconnected in background. Stopping notification, keeping vendor link for docked-case telemetry.")
                        activeDeviceAddress = null
                        currentLeftBattery = null
                        currentRightBattery = null
                        currentCaseBattery = null
                        currentAggregateBattery = null
                        stopForegroundAndSelf()
                    } else if (dev.address != activeDeviceAddress) {
                        handleDeviceConnected(dev)
                    }
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                        Log.i(TAG, "Bluetooth disabled in background. Stopping foreground service.")
                        UniversalTwsEngine.disconnectAll()
                        activeDeviceAddress = null
                        currentLeftBattery = null
                        currentRightBattery = null
                        currentCaseBattery = null
                        currentAggregateBattery = null
                        stopForegroundAndSelf()
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            Log.i(TAG, "Service restarted by system (START_STICKY)")
            restoreOrDiscoverConnectedDevice()
            return START_STICKY
        }

        val action = intent.action
        val address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
            ?: intent.getStringExtra(OmniBudsQuickNotificationManager.EXTRA_ADDRESS)
            ?: activeDeviceAddress

        val device: BluetoothDevice? = intent.getParcelableExtraCompat(EXTRA_DEVICE)
            ?: address?.let { getBluetoothDevice(it) }

        if (address != null) {
            activeDeviceAddress = address
        }

        when (action) {
            ACTION_START, ACTION_DEVICE_CONNECTED -> {
                if (device != null) {
                    handleDeviceConnected(device)
                } else if (address != null) {
                    val resolvedDev = getBluetoothDevice(address)
                    if (resolvedDev != null) {
                        handleDeviceConnected(resolvedDev)
                    } else {
                        promoteToForeground(address, activeDeviceName ?: "OmniBuds")
                    }
                } else {
                    restoreOrDiscoverConnectedDevice()
                }
            }

            OmniBudsQuickNotificationManager.ACTION_NOISE_CONTROL -> {
                val mode = intent.getStringExtra(OmniBudsQuickNotificationManager.EXTRA_MODE) ?: return START_STICKY
                activeMode = mode
                updateNotification()
            }

            OmniBudsQuickNotificationManager.ACTION_RECONNECT -> {
                if (address != null) {
                    serviceScope.launch(Dispatchers.IO) {
                        val targetDev = device ?: getBluetoothDevice(address)
                        if (targetDev != null) {
                            UniversalTwsEngine.connect(applicationContext, targetDev)
                        }
                    }
                }
            }

            OmniBudsQuickNotificationManager.ACTION_DISCONNECT -> {
                if (address != null) {
                    serviceScope.launch(Dispatchers.IO) {
                        UniversalTwsEngine.disconnect(address)
                    }
                }
                stopForegroundAndSelf()
            }

            ACTION_DEVICE_DISCONNECTED -> {
                val stillConnected = findConnectedAudioDevice(this)
                if (stillConnected == null) {
                    Log.i(TAG, "All audio devices disconnected. Stopping foreground service, keeping vendor link.")
                    activeDeviceAddress = null
                    currentLeftBattery = null
                    currentRightBattery = null
                    currentCaseBattery = null
                    currentAggregateBattery = null
                    stopForegroundAndSelf()
                } else {
                    handleDeviceConnected(stillConnected)
                }
            }

            ACTION_STOP -> {
                stopForegroundAndSelf()
            }
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val connectedDevice = findConnectedAudioDevice(this)
        if (connectedDevice != null || activeDeviceAddress != null) {
            Log.i(TAG, "App task removed from Recents. Audio device connected; persisting Foreground Service & Notification.")
            val notif = buildCurrentNotification()
            promoteWithNotification(notif)
        } else {
            Log.i(TAG, "App task removed and no device connected. Stopping service.")
            stopForegroundAndSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "OmniBudsForegroundService destroyed")
        if (serviceReceiverRegistered) {
            try { unregisterReceiver(serviceDisconnectReceiver) } catch (_: Exception) {}
            serviceReceiverRegistered = false
        }
        serviceScope.cancel()
    }

    private fun handleDeviceConnected(device: BluetoothDevice) {
        val address = try { device.address } catch (_: Exception) { null } ?: return
        activeDeviceAddress = address

        val rawName = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                device.alias?.takeIf { it.isNotBlank() } ?: device.name
            } else {
                device.name
            }
        } catch (_: Exception) { null }

        val resolved = ProductCatalog.resolve(rawName)
        val catalogName = listOfNotNull(resolved.manufacturer, resolved.model).joinToString(" ").trim()
        val displayName = when {
            resolved.confidence != ProductCatalog.Confidence.CATEGORY_FALLBACK && catalogName.isNotBlank() -> catalogName
            !rawName.isNullOrBlank() -> rawName
            else -> "OmniBuds"
        }
        activeDeviceName = displayName

        // Promote to foreground immediately
        promoteToForeground(address, displayName)

        // Connect hardware engine
        serviceScope.launch(Dispatchers.IO) {
            UniversalTwsEngine.connect(applicationContext, device)
        }
    }

    private fun promoteToForeground(address: String, name: String) {
        val notif = OmniBudsQuickNotificationManager.buildNotification(
            context = this,
            deviceAddress = address,
            deviceName = name,
            currentMode = activeMode,
            batteryPercent = currentAggregateBattery,
            leftBattery = currentLeftBattery,
            rightBattery = currentRightBattery,
            caseBattery = currentCaseBattery,
        )
        promoteWithNotification(notif)
    }

    private fun promoteWithNotification(notif: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    OmniBudsQuickNotificationManager.NOTIFICATION_ID,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
                )
            } else {
                startForeground(OmniBudsQuickNotificationManager.NOTIFICATION_ID, notif)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error promoting to foreground: ${e.message}")
        }
    }

    private fun updateNotification() {
        val address = activeDeviceAddress ?: "00:00:00:00:00:00"
        OmniBudsQuickNotificationManager.updateNotification(
            context = this,
            deviceAddress = address,
            deviceName = activeDeviceName,
            currentMode = activeMode,
            batteryPercent = currentAggregateBattery,
            leftBattery = currentLeftBattery,
            rightBattery = currentRightBattery,
            caseBattery = currentCaseBattery,
        )
    }

    private fun buildCurrentNotification(): Notification {
        val address = activeDeviceAddress ?: "00:00:00:00:00:00"
        return OmniBudsQuickNotificationManager.buildNotification(
            context = this,
            deviceAddress = address,
            deviceName = activeDeviceName,
            currentMode = activeMode,
            batteryPercent = currentAggregateBattery,
            leftBattery = currentLeftBattery,
            rightBattery = currentRightBattery,
            caseBattery = currentCaseBattery,
        )
    }

    private fun startObservingTelemetry() {
        telemetryJob?.cancel()
        telemetryJob = serviceScope.launch {
            // 1. Observe battery reports
            launch {
                UniversalTwsEngine.latestBatteryReports.collect { reports ->
                    val address = activeDeviceAddress ?: return@collect
                    val report = reports[address] ?: return@collect
                    var changed = false
                    if (report.left != null && report.left != currentLeftBattery) {
                        currentLeftBattery = report.left
                        changed = true
                    }
                    if (report.right != null && report.right != currentRightBattery) {
                        currentRightBattery = report.right
                        changed = true
                    }
                    if (report.case != null && report.case != currentCaseBattery) {
                        currentCaseBattery = report.case
                        changed = true
                    }
                    val aggregate = report.left ?: report.right ?: report.case
                    if (aggregate != null && aggregate != currentAggregateBattery) {
                        currentAggregateBattery = aggregate
                        changed = true
                    }
                    if (changed) {
                        updateNotification()
                    }
                }
            }

            // 2. Observe hardware-confirmed ANC mode changes (e.g. from physical earbud stem touches)
            launch {
                UniversalTwsEngine.confirmedAncMode.collect { modes ->
                    val address = activeDeviceAddress ?: return@collect
                    val mode = modes[address] ?: return@collect
                    if (mode.isNotBlank() && !mode.equals(activeMode, ignoreCase = true)) {
                        activeMode = mode
                        updateNotification()
                    }
                }
            }
        }
    }

    private fun restoreOrDiscoverConnectedDevice() {
        val connectedDevice = findConnectedAudioDevice(this)
        if (connectedDevice != null) {
            handleDeviceConnected(connectedDevice)
        } else {
            Log.i(TAG, "No connected audio device found on restore.")
            stopForegroundAndSelf()
        }
    }

    private fun stopForegroundAndSelf() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {}
        OmniBudsQuickNotificationManager.cancelNotification(this)
        stopSelf()
    }

    private fun getBluetoothDevice(address: String): BluetoothDevice? {
        return try {
            val bm = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bm?.adapter?.getRemoteDevice(address)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "OmniBudsForeground"

        const val ACTION_START = "com.omnibuds.action.FOREGROUND_START"
        const val ACTION_STOP = "com.omnibuds.action.FOREGROUND_STOP"
        const val ACTION_DEVICE_CONNECTED = "com.omnibuds.action.DEVICE_CONNECTED"
        const val ACTION_DEVICE_DISCONNECTED = "com.omnibuds.action.DEVICE_DISCONNECTED"

        const val EXTRA_DEVICE = "extra_bluetooth_device"
        const val EXTRA_DEVICE_ADDRESS = "extra_device_address"

        fun startForDevice(context: Context, device: BluetoothDevice) {
            val intent = Intent(context, OmniBudsForegroundService::class.java).apply {
                action = ACTION_DEVICE_CONNECTED
                putExtra(EXTRA_DEVICE, device)
                putExtra(EXTRA_DEVICE_ADDRESS, try { device.address } catch (_: Exception) { null })
            }
            tryStartForeground(context, intent)
        }

        fun startForAddress(context: Context, address: String) {
            val intent = Intent(context, OmniBudsForegroundService::class.java).apply {
                action = ACTION_DEVICE_CONNECTED
                putExtra(EXTRA_DEVICE_ADDRESS, address)
            }
            tryStartForeground(context, intent)
        }

        fun checkConnectedAndStart(context: Context) {
            val device = findConnectedAudioDevice(context)
            if (device != null) {
                startForDevice(context, device)
            }
        }

        fun checkAndStopIfNoDevices(context: Context) {
            val device = findConnectedAudioDevice(context)
            if (device == null) {
                val intent = Intent(context, OmniBudsForegroundService::class.java).apply {
                    action = ACTION_DEVICE_DISCONNECTED
                }
                try {
                    context.startService(intent)
                } catch (_: Exception) {}
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, OmniBudsForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }

        private fun tryStartForeground(context: Context, intent: Intent) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start foreground service: ${e.message}")
            }
        }

        fun findConnectedAudioDevice(context: Context): BluetoothDevice? {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = try {
                bm?.adapter
            } catch (_: Exception) { null } ?: return null

            if (!adapter.isEnabled) return null

            // 1. Query A2DP audio profile
            val a2dpDevices = try {
                bm?.getConnectedDevices(BluetoothProfile.A2DP).orEmpty()
            } catch (_: Exception) { emptyList() }

            for (d in a2dpDevices) {
                if (AudioWearableFilter.isAudioWearable(d)) return d
            }

            // 2. Query Headset audio profile
            val headsetDevices = try {
                bm?.getConnectedDevices(BluetoothProfile.HEADSET).orEmpty()
            } catch (_: Exception) { emptyList() }

            for (d in headsetDevices) {
                if (AudioWearableFilter.isAudioWearable(d)) return d
            }

            // 3. Fallback: reflection check on bonded devices
            val bonded = try {
                adapter.bondedDevices.orEmpty()
            } catch (_: Exception) { emptySet() }

            for (d in bonded) {
                if (!AudioWearableFilter.isAudioWearable(d)) continue
                try {
                    val isConnectedMethod = d.javaClass.getMethod("isConnected")
                    if (isConnectedMethod.invoke(d) == true) {
                        return d
                    }
                } catch (_: Exception) {}
            }
            return null
        }
    }
}

private fun Intent.getParcelableExtraCompat(key: String): BluetoothDevice? =
    if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(key, BluetoothDevice::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key)
    }

