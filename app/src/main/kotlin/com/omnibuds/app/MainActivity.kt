package com.omnibuds.app

import android.Manifest
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
        appScope.launch {
            try {
                val described = discoveryProvider.describe(device)?.copy(
                    connectionState = ConnectionState.Disconnected,
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
            if (state == android.bluetooth.BluetoothProfile.STATE_DISCONNECTED) {
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
            val stored = getSharedPreferences(PREFS_PRODUCT_OVERRIDES, MODE_PRIVATE).all
            for ((key, value) in stored) {
                if (value !is String) continue
                if (key.startsWith(PHOTO_KEY_PREFIX)) {
                    productPhotos[key.removePrefix(PHOTO_KEY_PREFIX)] = value
                } else if (key.startsWith(ALIAS_KEY_PREFIX)) {
                    deviceAliases[key.removePrefix(ALIAS_KEY_PREFIX)] = value
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
        val surfaceCoordinator = AndroidSurfaceCoordinator(repository = repository)
        devicesViewModel = DevicesViewModel(
            scope = appScope,
            discoveryProvider = discoveryProvider,
            globalStateRepository = repository,
        )
        val settingsViewModel = SettingsViewModel(scope = appScope)
        shell = AndroidApplicationShell(
            devicesViewModel = devicesViewModel,
            settingsViewModel = settingsViewModel,
            surfaceCoordinator = surfaceCoordinator,
        )

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
                    components.add(
                        com.omnibuds.core.globalstate.BatteryState.ComponentLevel(
                            com.omnibuds.core.globalstate.BatteryState.Component.LEFT_EARBUD,
                            it,
                            report.leftCharging,
                        ),
                    )
                }
                report.right?.let {
                    components.add(
                        com.omnibuds.core.globalstate.BatteryState.ComponentLevel(
                            com.omnibuds.core.globalstate.BatteryState.Component.RIGHT_EARBUD,
                            it,
                            report.rightCharging,
                        ),
                    )
                }
                report.case?.let {
                    if (it > 0 || report.caseCharging) {
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
                    deviceAliasFor = { address -> deviceAliases[address] },
                    onDeviceAliasChange = { address, alias ->
                        val prefs = getSharedPreferences(PREFS_PRODUCT_OVERRIDES, MODE_PRIVATE)
                        val trimmed = alias?.trim()?.takeIf { it.isNotEmpty() }
                        if (trimmed == null) {
                            deviceAliases.remove(address)
                            prefs.edit().remove(ALIAS_KEY_PREFIX + address).apply()
                        } else {
                            deviceAliases[address] = trimmed
                            prefs.edit().putString(ALIAS_KEY_PREFIX + address, trimmed).apply()
                        }
                    },
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

    private fun handleFeatureControlDispatched(address: String, featureId: String, value: String) {
        val device = discoveryProvider.bondedDevice(address)
        when (featureId) {
            "anc_mode" -> {
                if (device != null) {
                    appScope.launch {
                        com.omnibuds.android.hardware.engine.UniversalTwsEngine.setNoiseControl(device, value)
                    }
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
                        com.omnibuds.android.hardware.protocol.EarbudProtocolManager.sendTouchControl(device, value)
                    }
                }
                toast("Touch gesture updated")
            }
            "low_latency" -> {
                val enabled = value.equals("ON", ignoreCase = true)
                if (device != null) {
                    appScope.launch {
                        com.omnibuds.android.hardware.engine.UniversalTwsEngine.setGameMode(device, enabled)
                    }
                }
                toast("Game Mode: $value")
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
        }
    }

    private var connectionMonitorJob: kotlinx.coroutines.Job? = null

    private fun startConnectionMonitor() {
        connectionMonitorJob?.cancel()
        connectionMonitorJob = appScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(2000)
                try {
                    discoveryProvider.snapshotBonded().forEach { described ->
                        val actualState = discoveryProvider.connectionStateFor(described.address)
                        bridge.syncDevice(described, actualState)
                        if (actualState is ConnectionState.Connected) {
                            discoveryProvider.bondedDevice(described.address)?.let { dev ->
                                updateBatteryFromDevice(dev)
                                com.omnibuds.android.hardware.engine.UniversalTwsEngine.connect(applicationContext, dev)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    override fun onResume() {
        super.onResume()
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
        appScope.cancel()
        super.onDestroy()
    }

    // -- Permissions & platform state ---------------------------------------

    private fun requiredPermissions(): List<String> = when {
        Build.VERSION.SDK_INT >= 31 -> listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
        Build.VERSION.SDK_INT >= 23 -> listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        else -> emptyList()
    }

    private fun ensurePermissions() {
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
            permissionStateFor(Manifest.permission.BLUETOOTH_SCAN)
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
