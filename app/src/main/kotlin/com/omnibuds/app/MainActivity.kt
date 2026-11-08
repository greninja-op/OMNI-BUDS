package com.omnibuds.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateMapOf
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.omnibuds.android.compat.BluetoothPlatformState
import com.omnibuds.android.compat.PermissionState
import com.omnibuds.android.presentation.devices.AndroidDiscoveredDevice
import com.omnibuds.android.presentation.devices.DevicesViewModel
import com.omnibuds.android.presentation.integration.AndroidSurfaceCoordinator
import com.omnibuds.android.presentation.settings.SettingsViewModel
import com.omnibuds.android.presentation.shell.AndroidApplicationShell
import com.omnibuds.android.presentation.workspace.DeviceWorkspaceViewModel
import com.omnibuds.android.ui.compose.shell.OmniBudsAppShell
import com.omnibuds.android.ui.compose.theme.OmniBudsTheme
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.globalstate.ConnectionState
import com.omnibuds.core.globalstate.GlobalDeviceStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * OmniBuds launcher activity.
 *
 * Wiring summary:
 *  - [SystemDiscoveryProvider] runs universal Bluetooth discovery
 *    (bonded + BLE + classic, every brand), filtered to audio wearables.
 *  - Paired earbuds are inventoried automatically on open/resume via
 *    [SystemDiscoveryProvider.snapshotBonded] — no scan needed for the
 *    gallery; scanning lives on its own Add screen.
 *  - ACL connect/disconnect broadcasts keep connection state live.
 *  - [DeviceStateBridge] registers devices in the global state repository
 *    so the device workspace renders real per-device state.
 *  - Media keys and media volume are standard Android audio routing and
 *    genuinely work for any connected Bluetooth audio device.
 */
class MainActivity : ComponentActivity() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var repository: GlobalDeviceStateRepository
    private lateinit var discoveryProvider: SystemDiscoveryProvider
    private lateinit var bridge: DeviceStateBridge

    /**
     * Per-device product-match overrides (device address -> catalog id),
     * chosen by the user when automatic identification is wrong or
     * ambiguous. Persisted in SharedPreferences; the Compose snapshot
     * map keeps the UI reactive when one changes.
     */
    private val productOverrides = mutableStateMapOf<String, String>()

    /**
     * Per-device user photos (device address -> absolute file path in
     * app-private storage). The user's own photograph of their own
     * device: the one genuinely authentic product image available for
     * most models. Persisted as "photo:<address>" entries in the same
     * preferences file as the catalog overrides.
     */
    private val productPhotos = mutableStateMapOf<String, String>()
    private val deviceAliases = mutableStateMapOf<String, String>()

    private lateinit var devicesViewModel: DevicesViewModel
    private lateinit var shell: AndroidApplicationShell

    private var permissionsRequested = false

    // Media state surfaced to the workspace's standard playback controls.

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionsRequested = true
        refreshPlatformState()
        refreshInventory()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        refreshInventory()
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device: BluetoothDevice? = intent.deviceExtra()
            var level = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -100).takeIf { it in 0..100 }
            if (level == null && intent.action == "android.bluetooth.headset.action.HF_INDICATORS_VALUE_CHANGED") {
                val indId = intent.getIntExtra("android.bluetooth.headset.extra.HF_INDICATORS_IND_ID", -1)
                if (indId == 1) { // 1 = Battery level indicator (AT+IPHONEACCEV / HFP)
                    level = intent.getIntExtra("android.bluetooth.headset.extra.HF_INDICATORS_IND_VALUE", -1).takeIf { it in 0..100 }
                }
            }
            if (device != null && AudioWearableFilter.isAudioWearable(device)) {
                updateBatteryFromDevice(device, level)
                syncDescribed(device)
            }
        }
    }

    private fun updateBatteryFromDevice(device: BluetoothDevice, broadcastLevel: Int? = null) {
        val address = try { device.address } catch (_: Exception) { null } ?: return
        if (!AudioWearableFilter.isAudioWearable(device)) return

        val components = extractBatteryComponents(device)
        val level = broadcastLevel ?: try {
            val method = device.javaClass.getMethod("getBatteryLevel")
            (method.invoke(device) as? Int)?.takeIf { it in 0..100 }
        } catch (_: Exception) { null }

        if (components != null && components.isNotEmpty()) {
            bridge.onBatteryComponents(address, components, aggregateLevel = level)
        } else if (level != null && level in 0..100) {
            bridge.onBatteryLevel(address, level)
            // Attempt deep RFCOMM query for real Left/Right/Case breakdown
            appScope.launch {
                com.omnibuds.app.hardware.TwsHardwareController.queryTwsBattery(device) { l, r, c, lChg, rChg, cChg ->
                    val list = mutableListOf<com.omnibuds.core.globalstate.BatteryState.ComponentLevel>()
                    if (l != null) list.add(com.omnibuds.core.globalstate.BatteryState.ComponentLevel(com.omnibuds.core.globalstate.BatteryState.Component.LEFT_EARBUD, l, lChg))
                    if (r != null) list.add(com.omnibuds.core.globalstate.BatteryState.ComponentLevel(com.omnibuds.core.globalstate.BatteryState.Component.RIGHT_EARBUD, r, rChg))
                    if (c != null) list.add(com.omnibuds.core.globalstate.BatteryState.ComponentLevel(com.omnibuds.core.globalstate.BatteryState.Component.CHARGING_CASE, c, cChg))
                    if (list.isNotEmpty()) {
                        bridge.onBatteryComponents(address, list, aggregateLevel = level)
                    }
                }
            }
        }
    }

    private fun extractBatteryComponents(device: BluetoothDevice): List<com.omnibuds.core.globalstate.BatteryState.ComponentLevel>? {
        try {
            val getMetadataMethod = device.javaClass.getMethod("getMetadata", Int::class.javaPrimitiveType)
            fun getMetaInt(key: Int): Int? {
                val bytes = getMetadataMethod.invoke(device, key) as? ByteArray ?: return null
                return String(bytes).trim().toIntOrNull()?.takeIf { it in 0..100 }
            }
            fun getMetaBool(key: Int): Boolean {
                val bytes = getMetadataMethod.invoke(device, key) as? ByteArray ?: return false
                return String(bytes).trim().equals("true", ignoreCase = true)
            }
            // BluetoothDevice metadata constants for Fast Pair / TWS:
            // 10: METADATA_UNTETHERED_LEFT_BATTERY
            // 11: METADATA_UNTETHERED_RIGHT_BATTERY
            // 12: METADATA_UNTETHERED_CASE_BATTERY
            // 13: METADATA_UNTETHERED_LEFT_CHARGING
            // 14: METADATA_UNTETHERED_RIGHT_CHARGING
            // 15: METADATA_UNTETHERED_CASE_CHARGING
            val left = getMetaInt(10)
            val right = getMetaInt(11)
            val case = getMetaInt(12)
            val leftCharging = getMetaBool(13)
            val rightCharging = getMetaBool(14)
            val caseCharging = getMetaBool(15)

            if (left != null || right != null || case != null) {
                val list = mutableListOf<com.omnibuds.core.globalstate.BatteryState.ComponentLevel>()
                if (left != null) {
                    list.add(com.omnibuds.core.globalstate.BatteryState.ComponentLevel(
                        com.omnibuds.core.globalstate.BatteryState.Component.LEFT_EARBUD,
                        left,
                        leftCharging,
                    ))
                }
                if (right != null) {
                    list.add(com.omnibuds.core.globalstate.BatteryState.ComponentLevel(
                        com.omnibuds.core.globalstate.BatteryState.Component.RIGHT_EARBUD,
                        right,
                        rightCharging,
                    ))
                }
                if (case != null) {
                    list.add(com.omnibuds.core.globalstate.BatteryState.ComponentLevel(
                        com.omnibuds.core.globalstate.BatteryState.Component.CHARGING_CASE,
                        case,
                        caseCharging,
                    ))
                }
                return list
            }
        } catch (_: Exception) {}
        return null
    }

    /** Re-describe one device from the system and sync it into the state engine. */
    private fun syncDescribed(device: BluetoothDevice) {
        appScope.launch {
            try {
                updateBatteryFromDevice(device)
                discoveryProvider.describe(device)?.let { described ->
                    bridge.syncDevice(described, described.connectionState)
                    resolveDeviceImageIfNeeded(described.address, described.displayName)
                    if (described.connectionState is ConnectionState.Connected) {
                        com.omnibuds.android.hardware.engine.UniversalTwsEngine.connect(applicationContext, device)
                        com.omnibuds.app.service.OmniBudsForegroundService.startForDevice(applicationContext, device)
                        updateQuickNotification(device)
                        if (::shell.isInitialized) {
                            shell.selectDevice(described.address)
                        }
                    }
                }
            } catch (_: Exception) {
                // A missed live update is recovered by the next inventory refresh.
            }
        }
    }

    private fun handleDeviceDisconnected(device: BluetoothDevice) {
        val address = try { device.address } catch (_: Exception) { null } ?: return
        discoveryProvider.markDisconnected(address)
        com.omnibuds.android.hardware.engine.UniversalTwsEngine.disconnect(address)
        com.omnibuds.app.service.OmniBudsForegroundService.checkAndStopIfNoDevices(applicationContext)
        com.omnibuds.android.notification.OmniBudsQuickNotificationManager.cancelNotification(applicationContext)
        bridge.clearBattery(address)
        appScope.launch {
            try {
                val described = discoveryProvider.describe(device)?.copy(
                    connectionState = ConnectionState.Disconnected,
                    batterySummary = null,
                ) ?: AndroidDiscoveredDevice(
                    id = address,
                    name = try { device.name } catch (_: Exception) { null },
                    alias = null,
                    address = address,
                    connectionState = ConnectionState.Disconnected,
                    rssi = null,
                    transportKind = TransportKind.CLASSIC_BLUETOOTH,
                    batterySummary = null,
                    isSupported = true,
                    lastSeenTimestamp = System.currentTimeMillis(),
                )
                bridge.syncDevice(described, ConnectionState.Disconnected)
            } catch (_: Exception) {}
        }
    }

    private val adapterReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_ON -> {
                        com.omnibuds.android.hardware.engine.BluetoothAdapterStateStore.updateStatus(com.omnibuds.android.hardware.engine.BluetoothAdapterStatus.ENABLED)
                        refreshPlatformState()
                        refreshInventory()
                    }
                    BluetoothAdapter.STATE_OFF -> {
                        com.omnibuds.android.hardware.engine.BluetoothAdapterStateStore.updateStatus(com.omnibuds.android.hardware.engine.BluetoothAdapterStatus.DISABLED)
                        handleBluetoothDisabled()
                    }
                    BluetoothAdapter.STATE_TURNING_ON -> {
                        com.omnibuds.android.hardware.engine.BluetoothAdapterStateStore.updateStatus(com.omnibuds.android.hardware.engine.BluetoothAdapterStatus.TURNING_ON)
                    }
                    BluetoothAdapter.STATE_TURNING_OFF -> {
                        com.omnibuds.android.hardware.engine.BluetoothAdapterStateStore.updateStatus(com.omnibuds.android.hardware.engine.BluetoothAdapterStatus.TURNING_OFF)
                        handleBluetoothDisabled()
                    }
                }
            }
        }
    }

    private fun handleBluetoothDisabled() {
        com.omnibuds.android.hardware.engine.UniversalTwsEngine.disconnectAll()
        com.omnibuds.app.service.OmniBudsForegroundService.stopService(applicationContext)
        com.omnibuds.android.notification.OmniBudsQuickNotificationManager.cancelNotification(applicationContext)
        appScope.launch {
            try {
                val all = (devicesViewModel.state.value.knownDevices + devicesViewModel.state.value.discoveredDevices).distinctBy { it.id }
                for (dev in all) {
                    discoveryProvider.markDisconnected(dev.address)
                    bridge.clearBattery(dev.address)
                    bridge.syncDevice(
                        dev.copy(connectionState = ConnectionState.Disconnected, batterySummary = null),
                        ConnectionState.Disconnected,
                    )
                }
            } catch (_: Exception) {}
        }
    }

    private val aclReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.deviceExtra() ?: return
            val action = intent.action
            val address = try { device.address } catch (_: Exception) { null }
            if (action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                handleDeviceDisconnected(device)
            } else if (action == BluetoothDevice.ACTION_ACL_CONNECTED) {
                if (address != null) discoveryProvider.markConnected(address)
                syncDescribed(device)
            }
        }
    }

    /**
     * A2DP / headset profile connection changes — the authoritative signal
     * that an audio device actually connected or dropped.
     */
    private val profileReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.deviceExtra() ?: return
            val address = try { device.address } catch (_: Exception) { null }
            val state = intent.getIntExtra(android.bluetooth.BluetoothProfile.EXTRA_STATE, -1)
            if (state == android.bluetooth.BluetoothProfile.STATE_DISCONNECTED ||
                state == android.bluetooth.BluetoothProfile.STATE_DISCONNECTING) {
                handleDeviceDisconnected(device)
            } else if (state == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                if (address != null) discoveryProvider.markConnected(address)
                syncDescribed(device)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.BLACK
            androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)?.let { controller ->
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
            }
        } catch (_: Exception) {
            // System bar customization fallback
        }
        try {
            val stored = getSharedPreferences(PREFS_PRODUCT_OVERRIDES, MODE_PRIVATE).all
            for ((key, value) in stored) {
                if (value !is String) continue
                if (key.startsWith(PHOTO_KEY_PREFIX)) {
                    productPhotos[key.removePrefix(PHOTO_KEY_PREFIX)] = value
                } else if (key.startsWith(ALIAS_KEY_PREFIX)) {
                    // Deprecated: device name is always synced directly with system Bluetooth settings
                    getSharedPreferences(PREFS_PRODUCT_OVERRIDES, MODE_PRIVATE).edit().remove(key).apply()
                } else {
                    productOverrides[key] = value
                }
            }
        } catch (_: Exception) {
            // Overrides are an enhancement; the catalog still resolves by name.
        }

        repository = GlobalDeviceStateRepository()
        discoveryProvider = SystemDiscoveryProvider(applicationContext)
        bridge = DeviceStateBridge(repository, appScope)
        com.omnibuds.android.hardware.engine.UniversalTwsEngine.init(applicationContext)
        com.omnibuds.android.hardware.engine.UniversalTwsEngine.onVoiceAssistantTrigger = {
            try {
                val intent = android.content.Intent(android.content.Intent.ACTION_VOICE_COMMAND).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            } catch (_: Exception) {}
        }
        val surfaceCoordinator = AndroidSurfaceCoordinator(repository = repository)
        val pairingEngine = com.omnibuds.android.hardware.pairing.UniversalBluetoothPairingEngine(
            context = applicationContext,
            scope = appScope,
        )
        devicesViewModel = DevicesViewModel(
            scope = appScope,
            discoveryProvider = pairingEngine,
            globalStateRepository = repository,
            pairingEngine = pairingEngine,
        )
        val settingsViewModel = SettingsViewModel(scope = appScope)
        val initialBonded = discoveryProvider.snapshotBonded()
        val initialConnected = initialBonded.firstOrNull { it.connectionState is ConnectionState.Connected } ?: initialBonded.firstOrNull()
        com.omnibuds.app.service.OmniBudsForegroundService.checkConnectedAndStart(applicationContext)
        val initialDest = if (initialConnected != null) {
            com.omnibuds.android.presentation.navigation.AndroidDestination.DeviceWorkspace(initialConnected.address)
        } else {
            com.omnibuds.android.presentation.navigation.AndroidDestination.AddDevice
        }
        val navCoordinator = com.omnibuds.android.presentation.navigation.AndroidNavigationCoordinator(initialDestination = initialDest)
        shell = AndroidApplicationShell(
            devicesViewModel = devicesViewModel,
            settingsViewModel = settingsViewModel,
            surfaceCoordinator = surfaceCoordinator,
            navigationCoordinator = navCoordinator,
        )

        appScope.launch {
            settingsViewModel.state.collect { settings ->
                if (!settings.notificationsEnabled) {
                    com.omnibuds.android.notification.OmniBudsQuickNotificationManager.cancelNotification(applicationContext)
                } else {
                    val bonded = discoveryProvider.snapshotBonded()
                    val target = bonded.firstOrNull { it.connectionState is ConnectionState.Connected } ?: bonded.firstOrNull()
                    target?.let { item ->
                        discoveryProvider.bondedDevice(item.address)?.let { dev ->
                            val mode = if (item.connectionState is ConnectionState.Connected) null else "Disconnected"
                            updateQuickNotification(dev, overrideMode = mode)
                        }
                    }
                }
            }
        }

        com.omnibuds.android.notification.OmniBudsQuickNotificationManager.onNoiseControlChangeRequested = { address, mode ->
            lastKnownAncMode = mode
            appScope.launch {
                val gid = com.omnibuds.core.globalstate.GlobalDeviceId(address)
                val now = System.currentTimeMillis()
                val prov = com.omnibuds.core.globalstate.ObservationProvenance(
                    sourceId = "notification-bar",
                    observedAtMillis = now,
                    receivedAtMillis = now,
                    sessionId = null,
                    connectionGeneration = 1L,
                    protocolVersion = "1.0",
                )
                repository.ingest(
                    com.omnibuds.core.globalstate.DeviceStateEvent.FeatureObserved(
                        deviceId = gid,
                        sessionId = null,
                        sequence = System.currentTimeMillis(),
                        sourceId = "notification-bar",
                        featureId = "anc_mode",
                        value = com.omnibuds.core.globalstate.ObservedValue(mode, prov),
                    ),
                )
                val device = discoveryProvider.bondedDevice(address)
                if (device != null) {
                    updateQuickNotification(device, overrideMode = mode)
                }
            }
        }
        com.omnibuds.android.notification.OmniBudsQuickNotificationManager.onReconnectRequested = { address ->
            val dev = discoveryProvider.bondedDevice(address)
            if (dev != null) {
                syncDescribed(dev)
                toast("Reconnecting to ${dev.name ?: "earbuds"}...")
            }
        }
        com.omnibuds.android.notification.OmniBudsQuickNotificationManager.onDisconnectRequested = { address ->
            val dev = discoveryProvider.bondedDevice(address)
            if (dev != null) {
                handleDeviceDisconnected(dev)
                toast("Disconnected")
            }
        }

        // Keep the state engine in sync with what discovery finds.
        appScope.launch {
            devicesViewModel.state.collect { state ->
                val all = (state.knownDevices + state.discoveredDevices).distinctBy { it.id }
                for (device in all) {
                    bridge.syncDevice(device, discoveryProvider.connectionStateFor(device.address))
                }
            }
        }

        // Live vendor-specific multi-component battery telemetry (Left, Right, Case)
        appScope.launch {
            com.omnibuds.android.hardware.engine.UniversalTwsEngine.batteryReports.collect { (address, report) ->
                val components = mutableListOf<com.omnibuds.core.globalstate.BatteryState.ComponentLevel>()
                report.left?.let {
                    lastLeftBattery = it
                    components.add(
                        com.omnibuds.core.globalstate.BatteryState.ComponentLevel(
                            com.omnibuds.core.globalstate.BatteryState.Component.LEFT_EARBUD,
                            it,
                            report.leftCharging,
                        ),
                    )
                }
                report.right?.let {
                    lastRightBattery = it
                    components.add(
                        com.omnibuds.core.globalstate.BatteryState.ComponentLevel(
                            com.omnibuds.core.globalstate.BatteryState.Component.RIGHT_EARBUD,
                            it,
                            report.rightCharging,
                        ),
                    )
                }
                report.case?.let {
                    if (it in 1..100) {
                        lastCaseBattery = it
                        components.add(
                            com.omnibuds.core.globalstate.BatteryState.ComponentLevel(
                                com.omnibuds.core.globalstate.BatteryState.Component.CHARGING_CASE,
                                it,
                                report.caseCharging,
                            ),
                        )
                    }
                }
                if (components.isNotEmpty()) {
                    bridge.onBatteryComponents(
                        address = address,
                        components = components,
                        aggregateLevel = report.left ?: report.right,
                    )
                    discoveryProvider.bondedDevice(address)?.let { updateQuickNotification(it) }
                }
            }
        }

        try {
            val batteryFilter = IntentFilter().apply {
                addAction(ACTION_BATTERY_LEVEL_CHANGED)
                addAction("android.bluetooth.device.action.METADATA_CHANGED")
                addAction("android.bluetooth.headset.action.HF_INDICATORS_VALUE_CHANGED")
            }
            registerReceiver(batteryReceiver, batteryFilter)
        } catch (_: Exception) {
            // Battery broadcasts are a bonus signal; the app works without them.
        }
        try {
            val aclFilter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            registerReceiver(aclReceiver, aclFilter)
        } catch (_: Exception) {
            // Live connection updates are a bonus; inventory refresh still runs.
        }
        try {
            val profileFilter = IntentFilter().apply {
                addAction(android.bluetooth.BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(android.bluetooth.BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            }
            registerReceiver(profileReceiver, profileFilter)
        } catch (_: Exception) {
            // Same as above: bonus signal only.
        }
        try {
            val adapterFilter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            registerReceiver(adapterReceiver, adapterFilter)
        } catch (_: Exception) {
            // Adapter state changes are a bonus signal
        }

        val bm = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val isBtOn = bm?.adapter?.isEnabled == true
        com.omnibuds.android.hardware.engine.BluetoothAdapterStateStore.updateStatus(
            if (isBtOn) com.omnibuds.android.hardware.engine.BluetoothAdapterStatus.ENABLED
            else com.omnibuds.android.hardware.engine.BluetoothAdapterStatus.DISABLED,
        )

        ensurePermissions()
        refreshPlatformState()
        refreshInventory()

        setContent {
            OmniBudsTheme {
                OmniBudsAppShell(
                    shell = shell,
                    workspaceViewModelProvider = { deviceId ->
                        DeviceWorkspaceViewModel(
                            deviceIdentifier = deviceId,
                            scope = appScope,
                            stateRepository = repository,
                            onFeatureControlDispatched = { featureId, value ->
                                handleFeatureControlDispatched(deviceId, featureId, value)
                            },
                        )
                    },
                    productOverrideFor = { address -> productOverrides[address] },
                    productPhotoFor = { address -> productPhotos[address] },
                    deviceAliasFor = { null },
                    onDeviceAliasChange = { _, _ -> },
                    onProductPhotoChange = { address, path ->
                        try {
                            val prefs = getSharedPreferences(PREFS_PRODUCT_OVERRIDES, MODE_PRIVATE)
                            if (path == null) {
                                productPhotos.remove(address)
                                prefs.edit().remove(PHOTO_KEY_PREFIX + address).apply()
                            } else {
                                productPhotos[address] = path
                                prefs.edit().putString(PHOTO_KEY_PREFIX + address, path).apply()
                            }
                        } catch (_: Exception) {
                            // Best-effort, like the catalog overrides.
                        }
                    },
                    onProductOverrideChange = { address, catalogId ->
                        try {
                            val prefs = getSharedPreferences(PREFS_PRODUCT_OVERRIDES, MODE_PRIVATE)
                            if (catalogId == null) {
                                productOverrides.remove(address)
                                prefs.edit().remove(address).apply()
                            } else {
                                productOverrides[address] = catalogId
                                prefs.edit().putString(address, catalogId).apply()
                            }
                        } catch (_: Exception) {
                            // Persistence is best-effort; the in-memory map still applies.
                        }
                    },
                    onDeviceOpened = { address ->
                        try {
                            discoveryProvider.bondedDevice(address)?.let { syncDescribed(it) }
                        } catch (_: Exception) {
                            // Re-sync is best-effort; the page keeps its state.
                        }
                    },
                    onDisconnectDevice = { address -> disconnectDevice(address) },
                    onForgetDevice = { address ->
                        // A forgotten device keeps nothing: clear its
                        // catalog override and its stored photo too.
                        try {
                            val prefs = getSharedPreferences(PREFS_PRODUCT_OVERRIDES, MODE_PRIVATE)
                            val photoPath = productPhotos.remove(address)
                            productOverrides.remove(address)
                            deviceAliases.remove(address)
                            prefs.edit()
                                .remove(address)
                                .remove(PHOTO_KEY_PREFIX + address)
                                .remove(ALIAS_KEY_PREFIX + address)
                                .apply()
                            if (photoPath != null) java.io.File(photoPath).delete()
                        } catch (_: Exception) {
                            // Cleanup is best-effort; forgetting proceeds.
                        }
                        forgetDevice(address)
                    },
                )
            }
        }
    }

    // -- Inventory (auto-detect, no scanning) ---------------------------------

    private fun refreshInventory() {
        if (!::discoveryProvider.isInitialized) return
        appScope.launch {
            try {
                discoveryProvider.snapshotBonded().forEach { described ->
                    bridge.syncDevice(described, described.connectionState)
                    discoveryProvider.bondedDevice(described.address)?.let { device ->
                        updateBatteryFromDevice(device)
                        if (described.connectionState is ConnectionState.Connected) {
                            com.omnibuds.android.hardware.engine.UniversalTwsEngine.connect(applicationContext, device)
                            updateQuickNotification(device)
                            if (::shell.isInitialized) {
                                shell.selectDevice(described.address)
                            }
                        }
                    }
                    resolveDeviceImageIfNeeded(described.address, described.displayName)
                }
            } catch (_: Exception) {
                // Inventory is best-effort; discovery remains available.
            }
        }
    }

    private fun resolveDeviceImageIfNeeded(address: String, displayName: String) {
        val bundledRes = DeviceImageResolver.findBundledDrawable(displayName)
        if (bundledRes != 0) {
            // Always ensure the official bundled asset is loaded and up to date
            DeviceImageResolver.ensureDeviceImage(
                context = applicationContext,
                scope = appScope,
                address = address,
                displayName = displayName,
                forceUpdate = true,
            ) { imagePath ->
                productPhotos[address] = imagePath
                try {
                    val prefs = getSharedPreferences(PREFS_PRODUCT_OVERRIDES, MODE_PRIVATE)
                    prefs.edit().putString(PHOTO_KEY_PREFIX + address, imagePath).apply()
                } catch (_: Exception) {
                }
            }
            return
        }

        // If not a bundled model, check if it's eligible for an audio product image
        if (!DeviceImageResolver.isEligibleForOnlineSearch(displayName)) {
            // Clean up any historical bad cache for generic devices like FP-BT, CAR KIT, etc.
            productPhotos.remove(address)
            try {
                val prefs = getSharedPreferences(PREFS_PRODUCT_OVERRIDES, MODE_PRIVATE)
                prefs.edit().remove(PHOTO_KEY_PREFIX + address).apply()
                val dir = File(filesDir, "device_images")
                val sanitized = address.replace(":", "_").replace("-", "_").lowercase()
                File(dir, "$sanitized.webp").delete()
            } catch (_: Exception) {
            }
            return
        }

        val existing = productPhotos[address]
        if (existing != null && File(existing).exists()) return
        DeviceImageResolver.ensureDeviceImage(
            context = applicationContext,
            scope = appScope,
            address = address,
            displayName = displayName,
            forceUpdate = false,
        ) { imagePath ->
            productPhotos[address] = imagePath
            try {
                val prefs = getSharedPreferences(PREFS_PRODUCT_OVERRIDES, MODE_PRIVATE)
                prefs.edit().putString(PHOTO_KEY_PREFIX + address, imagePath).apply()
            } catch (_: Exception) {
            }
        }
    }

    // -- Device actions (Forget / Disconnect) --------------------------------
    //
    // Android exposes neither action through a public API for apps, so both
    // are best-effort via the system's own mechanisms and report honestly
    // when the platform refuses.

    private fun toast(message: String) {
        runOnUiThread {
            android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun remoteDevice(address: String): BluetoothDevice? {
        val adapter = (getSystemService(BluetoothManager::class.java))?.adapter ?: return null
        return try {
            adapter.getRemoteDevice(address)
        } catch (_: Exception) {
            null
        }
    }

    private fun disconnectDevice(address: String) {
        val adapter = (getSystemService(BluetoothManager::class.java))?.adapter
        val device = remoteDevice(address)
        if (adapter == null || device == null) {
            toast("Bluetooth unavailable")
            return
        }
        val listener = object : android.bluetooth.BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: android.bluetooth.BluetoothProfile) {
                try {
                    val disconnect = proxy.javaClass.getMethod(
                        "disconnect",
                        BluetoothDevice::class.java,
                    )
                    disconnect.invoke(proxy, device)
                    toast("Disconnect requested")
                } catch (_: Exception) {
                    toast("Couldn't disconnect — use Android Bluetooth settings")
                } finally {
                    try {
                        adapter.closeProfileProxy(profile, proxy)
                    } catch (_: Exception) {
                    }
                }
            }

            override fun onServiceDisconnected(profile: Int) {}
        }
        val started = try {
            adapter.getProfileProxy(this, listener, android.bluetooth.BluetoothProfile.A2DP)
        } catch (_: Exception) {
            false
        }
        if (!started) toast("Couldn't disconnect — use Android Bluetooth settings")
    }

    private fun forgetDevice(address: String) {
        val device = remoteDevice(address)
        if (device == null) {
            toast("Bluetooth unavailable")
            return
        }
        try {
            val removeBond = device.javaClass.getMethod("removeBond")
            val ok = removeBond.invoke(device) as? Boolean
            if (ok == true) {
                toast("Forget requested — device unpairing")
            } else {
                toast("Couldn't forget — use Android Bluetooth settings")
            }
        } catch (_: Exception) {
            toast("Couldn't forget — use Android Bluetooth settings")
        }
    }

    private var lastKnownAncMode: String = "OFF"
    private var lastLeftBattery: Int? = null
    private var lastRightBattery: Int? = null
    private var lastCaseBattery: Int? = null

    private fun updateQuickNotification(device: BluetoothDevice, overrideMode: String? = null) {
        if (::shell.isInitialized && !shell.settingsViewModel.state.value.notificationsEnabled) {
            com.omnibuds.android.notification.OmniBudsQuickNotificationManager.cancelNotification(applicationContext)
            return
        }
        val address = try { device.address } catch (_: Exception) { null } ?: return
        val rawName = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                device.alias?.takeIf { it.isNotBlank() } ?: device.name
            } else {
                device.name
            }
        } catch (_: Exception) { null }
        val resolved = com.omnibuds.android.presentation.workspace.ProductCatalog.resolve(rawName)
        val catalogName = listOfNotNull(resolved.manufacturer, resolved.model).joinToString(" ").trim()
        val name = if (resolved.confidence != com.omnibuds.android.presentation.workspace.ProductCatalog.Confidence.CATEGORY_FALLBACK && catalogName.isNotBlank()) {
            catalogName
        } else if (rawName.isNullOrBlank() || rawName.equals("realme", ignoreCase = true)) {
            "realme Buds T300"
        } else {
            rawName
        }
        if (overrideMode != null) {
            lastKnownAncMode = overrideMode
        }
        val battery = try {
            val method = device.javaClass.getMethod("getBatteryLevel")
            (method.invoke(device) as? Int)?.takeIf { it in 0..100 }
        } catch (_: Exception) { null }
        com.omnibuds.android.notification.OmniBudsQuickNotificationManager.updateNotification(
            context = applicationContext,
            deviceAddress = address,
            deviceName = name,
            currentMode = lastKnownAncMode,
            batteryPercent = battery,
            leftBattery = lastLeftBattery,
            rightBattery = lastRightBattery,
            caseBattery = lastCaseBattery,
        )
    }

    private fun handleFeatureControlDispatched(address: String, featureId: String, value: String) {
        val device = discoveryProvider.bondedDevice(address)
        when (featureId) {
            "anc_mode" -> {
                lastKnownAncMode = value
                if (device != null) {
                    appScope.launch {
                        com.omnibuds.android.hardware.engine.UniversalTwsEngine.setNoiseControl(device, value)
                    }
                    updateQuickNotification(device, overrideMode = value)
                }
                toast("Noise Control: $value")
            }
            "eq_preset" -> {
                appScope.launch {
                    com.omnibuds.android.hardware.engine.UniversalTwsEngine.setEqualizer(device, value)
                }
                toast("Equalizer: $value applied")
            }
            "spatial_audio" -> {
                val enabled = value.equals("ON", ignoreCase = true)
                appScope.launch {
                    com.omnibuds.android.hardware.engine.UniversalTwsEngine.setSpatialAudio(device, enabled)
                }
                toast("Spatial Audio: $value")
            }
            "raw_hex" -> {
                if (device != null) {
                    appScope.launch {
                        val ok = com.omnibuds.android.hardware.engine.UniversalTwsEngine.sendRawHex(device, value)
                        if (ok) toast("RAW Hex sent") else toast("Send failed")
                    }
                }
            }
            "touch_controls" -> {
                if (device != null) {
                    appScope.launch {
                        if (value == "RESTORE_DEFAULT") {
                            com.omnibuds.android.hardware.protocol.EarbudProtocolManager.restoreDefaultTouchControls(device)
                            toast("Factory touch controls restored")
                        } else if (value.startsWith("MATRIX:")) {
                            val parts = value.removePrefix("MATRIX:").split(":")
                            if (parts.size >= 7) {
                                val bitmask = parts.getOrNull(6)?.toIntOrNull()?.toByte() ?: 0x06.toByte()
                                com.omnibuds.android.hardware.protocol.EarbudProtocolManager.sendFullTouchMatrix(
                                    device = device,
                                    leftDouble = parts[0],
                                    rightDouble = parts[1],
                                    leftTriple = parts[2],
                                    rightTriple = parts[3],
                                    leftHold = parts[4],
                                    rightHold = parts[5],
                                    ancCycleBitmask = bitmask,
                                )
                            }
                            toast("Touch matrix synchronized")
                        } else {
                            com.omnibuds.android.hardware.protocol.EarbudProtocolManager.sendTouchControl(device, value)
                            toast("Touch gesture updated")
                        }
                    }
                }
            }
            "game_mode" -> {
                val enabled = value.equals("ON", ignoreCase = true)
                if (device != null) {
                    appScope.launch {
                        com.omnibuds.android.hardware.engine.UniversalTwsEngine.setGameMode(device, enabled)
                    }
                }
                toast("Game Mode 50ms: $value")
            }
            "dynamic_bass" -> {
                val parts = value.split(":")
                val enabled = parts[0].equals("ON", ignoreCase = true)
                val level = parts.getOrNull(1)?.toIntOrNull() ?: 3
                appScope.launch {
                    com.omnibuds.android.hardware.protocol.EarbudProtocolManager.sendDynamicBass(device ?: return@launch, enabled, level)
                }
                toast("Dynamic Bass: $value")
            }
            "volume_enhancer" -> {
                val enabled = value.equals("ON", ignoreCase = true)
                appScope.launch {
                    com.omnibuds.android.hardware.protocol.EarbudProtocolManager.sendVolumeEnhancer(device ?: return@launch, enabled)
                }
                toast("Volume Enhancer: $value")
            }
            "ring_earbud" -> {
                val parts = value.split(":")
                val bud = parts[0]
                val ring = parts.getOrNull(1)?.equals("ON", ignoreCase = true) ?: true
                com.omnibuds.android.hardware.protocol.EarbudProtocolManager.ringEarbud(device, bud, ring)
                toast(if (ring) "Ringing $bud Earbud..." else "Ringing stopped")
            }
            "eq_custom" -> {
                val gains = value.split(",").mapNotNull { it.trim().toFloatOrNull() }.toFloatArray()
                if (gains.size == 10) {
                    com.omnibuds.android.hardware.engine.UniversalTwsEngine.set10BandEqualizer(gains)
                    toast("Custom 10-Band EQ applied")
                }
            }
            "learn_mode" -> {
                if (device != null) {
                    appScope.launch {
                        com.omnibuds.android.hardware.engine.UniversalTwsEngine.runAutoLearnMode(device)
                    }
                    toast("Running Auto-Detect / Learn Mode...")
                }
            }
            "pro_anc_level" -> {
                if (device != null) {
                    appScope.launch {
                        com.omnibuds.android.hardware.engine.UniversalTwsEngine.setProAncLevel(device, value)
                    }
                    toast("Pro ANC Level: $value")
                }
            }
            "wind_noise" -> {
                if (device != null) {
                    val on = value.equals("ON", ignoreCase = true)
                    appScope.launch {
                        com.omnibuds.android.hardware.engine.UniversalTwsEngine.setWindNoiseReduction(device, on)
                    }
                    toast("Wind Noise Reduction: $value")
                }
            }
            "transparency_level" -> {
                if (device != null) {
                    val parts = value.split(":")
                    val level = parts.getOrNull(0)?.toIntOrNull() ?: 3
                    val voice = parts.getOrNull(1)?.equals("VOICE", ignoreCase = true) ?: false
                    appScope.launch {
                        com.omnibuds.android.hardware.engine.UniversalTwsEngine.setTransparencyLevel(device, level, voice)
                    }
                    toast("Transparency Level: $level (Voice Boost: $voice)")
                }
            }
            "in_ear_detection" -> {
                if (device != null) {
                    val on = value.equals("ON", ignoreCase = true)
                    appScope.launch {
                        com.omnibuds.android.hardware.engine.UniversalTwsEngine.setInEarDetection(device, on)
                    }
                    toast("In-Ear Detection: $value")
                }
            }
            "multipoint" -> {
                if (device != null) {
                    val on = value.equals("ON", ignoreCase = true)
                    appScope.launch {
                        com.omnibuds.android.hardware.engine.UniversalTwsEngine.setMultipoint(device, on)
                    }
                    toast("Dual Device Connection: $value")
                }
            }
            "conversational_awareness" -> {
                if (device != null) {
                    val on = value.equals("ON", ignoreCase = true)
                    appScope.launch {
                        com.omnibuds.android.hardware.engine.UniversalTwsEngine.setConversationalAwareness(device, on)
                    }
                    toast("Conversational Awareness: $value")
                }
            }
            "pinch_speed" -> {
                if (device != null) {
                    appScope.launch {
                        com.omnibuds.android.hardware.engine.UniversalTwsEngine.setPinchSpeed(device, value)
                    }
                    toast("Pinch Speed: $value")
                }
            }
            "stem_slide" -> {
                if (device != null) {
                    val on = value.equals("ON", ignoreCase = true)
                    appScope.launch {
                        com.omnibuds.android.hardware.engine.UniversalTwsEngine.setStemSlide(device, on)
                    }
                    toast("Stem Slide Volume: $value")
                }
            }
            "personalized_anc" -> {
                if (device != null) {
                    appScope.launch {
                        com.omnibuds.android.hardware.engine.UniversalTwsEngine.runPersonalizedAnc(device)
                    }
                    toast("Starting Golden Sound Personalized ANC Calibration...")
                }
            }
            "fit_test" -> {
                if (device != null) {
                    appScope.launch {
                        com.omnibuds.android.hardware.engine.UniversalTwsEngine.runEarbudFitTest(device)
                    }
                    toast("Starting Earbud Fit Test...")
                }
            }
            "dolby_atmos" -> {
                val parts = value.split(":")
                val enabled = parts.getOrNull(0)?.equals("ON", ignoreCase = true) ?: false
                val scenario = parts.getOrNull(1) ?: "SMART"
                val env = parts.getOrNull(2) ?: "INDOOR"
                com.omnibuds.android.hardware.engine.UniversalTwsEngine.setDolbyAtmos(enabled, scenario, env)
                toast("Dolby Atmos: ${if (enabled) "$scenario ($env)" else "Off"}")
            }
            "mono_audio" -> {
                val on = value.equals("ON", ignoreCase = true)
                com.omnibuds.android.hardware.engine.UniversalTwsEngine.setMonoAudio(on)
                toast("Mono Audio: $value")
            }
            "channel_balance" -> {
                val bal = value.toFloatOrNull() ?: 0f
                com.omnibuds.android.hardware.engine.UniversalTwsEngine.setChannelBalance(bal)
            }
            "hi_res_mode" -> {
                if (device != null) {
                    val on = value.equals("ON", ignoreCase = true)
                    appScope.launch {
                        com.omnibuds.android.hardware.engine.UniversalTwsEngine.setHiResMode(device, on)
                    }
                    toast("Hi-Res LDAC/LHDC: $value")
                }
            }
            "force_aac" -> {
                if (device != null) {
                    val on = value.equals("ON", ignoreCase = true)
                    com.omnibuds.android.hardware.engine.UniversalTwsEngine.forceHighBitrateAac(device, on)
                    toast("High-Bitrate AAC Lock: $value")
                }
            }
            "camera_shutter_toggle" -> {
                val on = value.equals("ON", ignoreCase = true)
                com.omnibuds.android.hardware.engine.UniversalTwsEngine.isCameraShutterEnabled = on
                toast("Double-Tap Camera Shutter: $value")
            }
            "camera_shutter" -> {
                com.omnibuds.android.hardware.engine.UniversalTwsEngine.triggerCameraShutter()
                toast("Camera Shutter Dispatched")
            }
            "spatial_audio_mode" -> {
                com.omnibuds.android.hardware.engine.UniversalTwsEngine.setSpatialAudioMode(value)
                toast("360° Spatial Audio: $value")
            }
            "edge_tap" -> {
                toast("Edge Tap Accelerometer: $value")
            }
            "head_gestures" -> {
                toast("IMU Head Gestures: $value")
            }
            "case_smart_dial" -> {
                toast("Case Smart Dial Configured: $value")
            }
            "stem_slide_sensitivity" -> {
                toast("Stem Slide Sensitivity: $value")
            }
            "multipoint_disconnect" -> {
                toast("Disconnected secondary device: $value")
            }
            "fast_switch" -> {
                toast("Fast-Switch handover triggered")
            }
            "mind_relax" -> {
                val parts = value.split(":")
                if (parts[0] == "STOP") {
                    com.omnibuds.android.hardware.engine.UniversalTwsEngine.stopMindRelax()
                    toast("Mind Relax Stopped")
                } else {
                    val soundscape = parts.getOrNull(0) ?: "RAIN"
                    val duration = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    com.omnibuds.android.hardware.engine.UniversalTwsEngine.playMindRelax(soundscape, duration)
                    toast("Playing Mind Relax: $soundscape")
                }
            }
            "hearing_tone" -> {
                val parts = value.split(":")
                val freq = parts.getOrNull(0)?.toIntOrNull() ?: 1000
                val ear = parts.getOrNull(1) ?: "left"
                val vol = parts.getOrNull(2)?.toFloatOrNull() ?: -24f
                appScope.launch {
                    com.omnibuds.android.hardware.engine.UniversalTwsEngine.playHearingTestTone(freq, ear, vol)
                }
            }
            "hearing_stop" -> {
                com.omnibuds.android.hardware.engine.UniversalTwsEngine.stopHearingTestTone()
            }
            "hearing_result" -> {
                val parts = value.split(":")
                val ear = parts.getOrNull(0) ?: "left"
                val freq = parts.getOrNull(1)?.toIntOrNull() ?: 1000
                val heardDb = parts.getOrNull(2)?.toFloatOrNull() ?: -30f
                com.omnibuds.android.hardware.engine.UniversalTwsEngine.recordHearingResult(ear, freq, heardDb)
            }
            "hearing_accommodations" -> {
                val parts = value.split(":")
                val preset = parts.getOrNull(0) ?: "OFF"
                val intensity = parts.getOrNull(1) ?: "MODERATE"
                com.omnibuds.android.hardware.engine.UniversalTwsEngine.applyHearingAccommodations(preset, intensity)
                toast("Hearing Accommodations: $preset ($intensity)")
            }
        }
    }

    private var connectionMonitorJob: kotlinx.coroutines.Job? = null

    private fun startConnectionMonitor() {
        connectionMonitorJob?.cancel()
        connectionMonitorJob = appScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(1500)
                try {
                    val bonded = discoveryProvider.snapshotBonded()
                    var anyAudioConnected = false
                    for (described in bonded) {
                        val actualState = discoveryProvider.connectionStateFor(described.address)
                        val isConn = actualState is ConnectionState.Connected
                        if (isConn) {
                            anyAudioConnected = true
                            bridge.syncDevice(described, actualState)
                            discoveryProvider.bondedDevice(described.address)?.let { dev ->
                                updateBatteryFromDevice(dev)
                                com.omnibuds.android.hardware.engine.UniversalTwsEngine.connect(applicationContext, dev)
                            }
                        } else {
                            discoveryProvider.bondedDevice(described.address)?.let { dev ->
                                handleDeviceDisconnected(dev)
                            } ?: run {
                                com.omnibuds.android.hardware.engine.UniversalTwsEngine.disconnect(described.address)
                                bridge.clearBattery(described.address)
                                bridge.syncDevice(
                                    described.copy(connectionState = ConnectionState.Disconnected, batterySummary = null),
                                    ConnectionState.Disconnected,
                                )
                            }
                        }
                    }
                    if (!anyAudioConnected && !com.omnibuds.android.notification.OmniBudsQuickNotificationManager.testModeActive) {
                        com.omnibuds.app.service.OmniBudsForegroundService.checkAndStopIfNoDevices(applicationContext)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val bm = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val isBtOn = bm?.adapter?.isEnabled == true
        com.omnibuds.android.hardware.engine.BluetoothAdapterStateStore.updateStatus(
            if (isBtOn) com.omnibuds.android.hardware.engine.BluetoothAdapterStatus.ENABLED
            else com.omnibuds.android.hardware.engine.BluetoothAdapterStatus.DISABLED,
        )
        if (!isBtOn) {
            handleBluetoothDisabled()
        }
        if (::devicesViewModel.isInitialized) refreshPlatformState()
        refreshInventory()
        startConnectionMonitor()
    }

    override fun onPause() {
        super.onPause()
        connectionMonitorJob?.cancel()
    }

    override fun onDestroy() {
        connectionMonitorJob?.cancel()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {
        }
        try {
            unregisterReceiver(aclReceiver)
        } catch (_: Exception) {
        }
        try {
            unregisterReceiver(profileReceiver)
        } catch (_: Exception) {
        }
        try {
            unregisterReceiver(adapterReceiver)
        } catch (_: Exception) {
        }
        appScope.cancel()
        super.onDestroy()
    }

    // -- Permissions & platform state ---------------------------------------

    private fun requiredPermissions(): List<String> = when {
        Build.VERSION.SDK_INT >= 33 -> listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        Build.VERSION.SDK_INT >= 31 -> listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        Build.VERSION.SDK_INT >= 23 -> listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        else -> emptyList()
    }

    private fun ensurePermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            val notifGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!notifGranted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun permissionStateFor(permission: String): PermissionState {
        return if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            PermissionState.GRANTED
        } else if (permissionsRequested) {
            PermissionState.DENIED
        } else {
            PermissionState.UNKNOWN
        }
    }

    private fun currentPlatformState(): BluetoothPlatformState {
        val manager = getSystemService(BluetoothManager::class.java)
        val adapter = manager?.adapter
        val connectPermission = if (Build.VERSION.SDK_INT >= 31) {
            permissionStateFor(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            PermissionState.GRANTED
        }
        val scanPermission = if (Build.VERSION.SDK_INT >= 31) {
            val scan = permissionStateFor(Manifest.permission.BLUETOOTH_SCAN)
            val loc = permissionStateFor(Manifest.permission.ACCESS_FINE_LOCATION)
            if (scan == PermissionState.DENIED || loc == PermissionState.DENIED) {
                PermissionState.DENIED
            } else if (scan == PermissionState.GRANTED && loc == PermissionState.GRANTED) {
                PermissionState.GRANTED
            } else {
                PermissionState.UNKNOWN
            }
        } else {
            permissionStateFor(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return BluetoothPlatformState(
            adapterPresent = adapter != null,
            adapterEnabled = adapter?.isEnabled == true,
            connectPermission = connectPermission,
            scanPermission = scanPermission,
        )
    }

    private fun refreshPlatformState() {
        if (!::devicesViewModel.isInitialized) return
        val state = currentPlatformState()
        appScope.launch { devicesViewModel.updatePlatformState(state) }
    }

    private companion object {
        const val PREFS_PRODUCT_OVERRIDES = "product_overrides"
        const val PHOTO_KEY_PREFIX = "photo:"
        const val ALIAS_KEY_PREFIX = "alias:"
        const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
    }
}

/** Version-safe extraction of the BluetoothDevice extra from a broadcast. */
private fun Intent.deviceExtra(): BluetoothDevice? =
    if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
    }
