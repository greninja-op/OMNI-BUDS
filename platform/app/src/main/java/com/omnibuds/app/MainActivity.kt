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
import com.omnibuds.android.presentation.devices.DevicesViewModel
import com.omnibuds.android.presentation.integration.AndroidSurfaceCoordinator
import com.omnibuds.android.presentation.settings.SettingsViewModel
import com.omnibuds.android.presentation.shell.AndroidApplicationShell
import com.omnibuds.android.presentation.workspace.DeviceWorkspaceViewModel
import com.omnibuds.android.ui.compose.shell.OmniBudsAppShell
import com.omnibuds.android.ui.compose.theme.OmniBudsTheme
import com.omnibuds.core.globalstate.GlobalDeviceStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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
            if (intent.action != ACTION_BATTERY_LEVEL_CHANGED) return
            val device: BluetoothDevice? = intent.deviceExtra()
            val level = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -100)
            val address = try {
                device?.address
            } catch (_: Exception) {
                null
            }
            if (device != null && address != null && level in 0..100 &&
                AudioWearableFilter.isAudioWearable(device)
            ) {
                bridge.onBatteryLevel(address, level)
                // A battery report implies a live connection right now —
                // a good moment to refresh the connection state too.
                syncDescribed(device)
            }
        }
    }

    /** Re-describe one device from the system and sync it into the state engine. */
    private fun syncDescribed(device: BluetoothDevice) {
        appScope.launch {
            try {
                discoveryProvider.describe(device)?.let { described ->
                    bridge.syncDevice(described, described.connectionState)
                }
            } catch (_: Exception) {
                // A missed live update is recovered by the next inventory refresh.
            }
        }
    }

    private val aclReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED &&
                intent.action != BluetoothDevice.ACTION_ACL_DISCONNECTED
            ) {
                return
            }
            val device: BluetoothDevice? = intent.deviceExtra()
            if (device != null) syncDescribed(device)
        }
    }

    /**
     * A2DP / headset profile connection changes — the authoritative signal
     * that an audio device actually connected or dropped.
     */
    private val profileReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device: BluetoothDevice? = intent.deviceExtra()
            if (device != null) syncDescribed(device)
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
        val surfaceCoordinator = AndroidSurfaceCoordinator(repository = repository)
        devicesViewModel = DevicesViewModel(
            scope = appScope,
            discoveryProvider = discoveryProvider,
            globalStateRepository = repository,
        )
        val settingsViewModel = SettingsViewModel(scope = appScope)
        val shell = AndroidApplicationShell(
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

        try {
            registerReceiver(batteryReceiver, IntentFilter(ACTION_BATTERY_LEVEL_CHANGED))
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
                }
            } catch (_: Exception) {
                // Inventory is best-effort; discovery remains available.
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

    override fun onResume() {
        super.onResume()
        if (::devicesViewModel.isInitialized) refreshPlatformState()
        refreshInventory()
    }

    override fun onDestroy() {
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
