package com.omnibuds.app

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.ParcelUuid
import com.omnibuds.android.presentation.devices.AndroidDiscoveredDevice
import com.omnibuds.android.presentation.devices.AndroidDiscoveryProvider
import com.omnibuds.android.presentation.devices.AndroidDiscoverySession
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.globalstate.ConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Real universal Bluetooth discovery for the OmniBuds app.
 *
 * Combines three standard, brand-agnostic sources:
 *  - already-bonded (paired) devices from the system,
 *  - Bluetooth Low Energy scan results,
 *  - classic Bluetooth discovery results.
 *
 * Nothing here is vendor-specific: every earbud, headphone or speaker that
 * advertises over standard Bluetooth shows up. Values the system does not
 * expose (name, battery, capabilities) stay null/unknown rather than being
 * invented.
 */
class SystemDiscoveryProvider(
    private val appContext: Context,
) : AndroidDiscoveryProvider {

    private val bluetoothManager: BluetoothManager? =
        appContext.getSystemService(BluetoothManager::class.java)

    /**
     * Live profile-service proxies. The profile services (A2DP, headset)
     * keep their own connection bookkeeping, reached over a different path
     * than the adapter queries — on stacks where the adapter-level probes
     * misreport (observed on HyperOS), the proxies stay truthful.
     */
    @Volatile
    private var a2dpProxy: BluetoothA2dp? = null

    @Volatile
    private var headsetProxy: BluetoothHeadset? = null

    private val recentlyDisconnected = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun markDisconnected(address: String) {
        recentlyDisconnected[address] = System.currentTimeMillis()
    }

    fun markConnected(address: String) {
        recentlyDisconnected.remove(address)
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dpProxy = proxy as? BluetoothA2dp
                BluetoothProfile.HEADSET -> headsetProxy = proxy as? BluetoothHeadset
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dpProxy = null
                BluetoothProfile.HEADSET -> headsetProxy = null
            }
        }
    }

    init {
        try {
            adapter?.getProfileProxy(appContext, profileListener, BluetoothProfile.A2DP)
            adapter?.getProfileProxy(appContext, profileListener, BluetoothProfile.HEADSET)
        } catch (_: Exception) {
            // Proxies are one signal among several; their absence is not fatal.
        }
    }

    val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    override suspend fun startDiscovery(): AndroidDiscoverySession {
        val adapter = adapter ?: throw IllegalStateException("No Bluetooth adapter on this device")
        if (!adapter.isEnabled) throw IllegalStateException("Bluetooth is turned off")
        val session = Session(appContext, adapter, bluetoothManager)
        session.start()
        return session
    }

    /**
     * Live connection state for one device address, straight from the system
     * profile proxies' bookkeeping (no connection attempt is made).
     *
     * Never throws: some OEM Bluetooth stacks raise a RuntimeException
     * ("Profile not supported: N") from getConnectionState when a profile
     * service is not bound, instead of returning STATE_DISCONNECTED. A
     * failed probe means "not known to be connected", nothing more.
     */
    @SuppressLint("MissingPermission")
    fun connectionStateFor(address: String): ConnectionState {
        val lastDisc = recentlyDisconnected[address]
        if (lastDisc != null) {
            if (System.currentTimeMillis() - lastDisc < 6000) {
                return ConnectionState.Disconnected
            } else {
                recentlyDisconnected.remove(address)
            }
        }

        val manager = bluetoothManager ?: return ConnectionState.Disconnected
        val adapter = manager.adapter ?: return ConnectionState.Disconnected
        val device = try {
            adapter.getRemoteDevice(address)
        } catch (_: Exception) {
            return ConnectionState.Disconnected
        }
        val btConnected = PROFILES.any { profileConnected(manager, device, it) } ||
            proxyConnected(device)
        return if (btConnected) {
            ConnectionState.Connected(
                sessionId = "android-system",
                generation = 1L,
                transport = "Bluetooth",
            )
        } else {
            ConnectionState.Disconnected
        }
    }

    /**
     * One filtered, described bonded device, or null when the device is not
     * an audio wearable (speaker, car kit, set-top box, ...).
     */
    @SuppressLint("MissingPermission")
    fun describe(device: BluetoothDevice): AndroidDiscoveredDevice? {
        if (!AudioWearableFilter.isAudioWearable(device)) return null
        val address = try {
            device.address
        } catch (_: Exception) {
            return null
        } ?: return null
        val name = try {
            device.name
        } catch (_: Exception) {
            null
        }
        return AndroidDiscoveredDevice(
            id = address,
            name = name,
            alias = deviceAlias(device),
            address = address,
            connectionState = connectionStateFor(address),
            rssi = null,
            transportKind = TransportKind.CLASSIC_BLUETOOTH,
            batterySummary = null,
            isSupported = true,
            lastSeenTimestamp = System.currentTimeMillis(),
        )
    }

    /** True when a bound profile service counts this device as connected. Never throws. */
    @SuppressLint("MissingPermission")
    private fun proxyConnected(device: BluetoothDevice): Boolean {
        for (proxy in listOfNotNull(a2dpProxy, headsetProxy)) {
            try {
                if (proxy.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED) {
                    return true
                }
            } catch (_: Exception) {
                // Try the list form below.
            }
            try {
                if (proxy.connectedDevices.any { it.address == device.address }) {
                    return true
                }
            } catch (_: Exception) {
                // Next proxy.
            }
        }
        return false
    }

    /**
     * True when the audio system currently lists this device as an output —
     * the media stack's own view, independent of Bluetooth adapter probes.
     * Matched by address where the system exposes one, else by name.
     */
    @SuppressLint("MissingPermission")
    private fun audioOutputConnected(device: BluetoothDevice): Boolean {
        val audioManager = appContext.getSystemService(AudioManager::class.java) ?: return false
        val targetAddress = try {
            device.address
        } catch (_: Exception) {
            null
        } ?: return false
        val targetName = try {
            device.name
        } catch (_: Exception) {
            null
        }
        val outputs = try {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        } catch (_: Exception) {
            return false
        }
        return outputs.any { info ->
            val typeOk = info.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                info.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                info.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
            if (!typeOk) {
                false
            } else {
                val address = if (Build.VERSION.SDK_INT >= 28) {
                    try {
                        info.address
                    } catch (_: Exception) {
                        null
                    }
                } else {
                    null
                }
                when {
                    !address.isNullOrBlank() -> address.equals(targetAddress, ignoreCase = true)
                    else -> targetName != null &&
                        info.productName?.toString()?.equals(targetName, ignoreCase = true) == true
                }
            }
        }
    }

    /** The bonded BluetoothDevice for an address, or null. Never throws. */
    fun bondedDevice(address: String): BluetoothDevice? {
        val bonded = try {
            adapter?.bondedDevices
        } catch (_: Exception) {
            null
        } ?: return null
        return bonded.firstOrNull { device ->
            try {
                device.address == address
            } catch (_: Exception) {
                false
            }
        }
    }

    /** All paired audio wearables, described — no scanning involved. */
    fun snapshotBonded(): List<AndroidDiscoveredDevice> {
        val adapter = adapter ?: return emptyList()
        val bonded = try {
            adapter.bondedDevices
        } catch (_: Exception) {
            null
        } ?: return emptyList()
        return bonded.mapNotNull { device ->
            try {
                describe(device)
            } catch (_: Exception) {
                null
            }
        }
    }

    companion object {
        // Audio wearables are only connected when A2DP (media) or HEADSET (calls) profile is connected.
        // GATT is intentionally excluded: our own app connects a GATT client for telemetry/control,
        // so checking GATT creates a ghost connection that keeps the device reported as connected
        // even after the user disconnects or docks the earbuds!
        private val PROFILES = listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)

        /**
         * True only when the system positively reports this profile connected.
         * Never throws. Two sources, in order:
         *  1. the per-device connection-state query;
         *  2. the per-profile connected-devices list — some OEM stacks throw
         *     or misreport on (1) for live A2DP links (observed on HyperOS:
         *     "Profile not supported: 2", and connected buds reporting
         *     STATE_DISCONNECTED), while (2) stays truthful.
         */
        @SuppressLint("MissingPermission")
        fun profileConnected(manager: BluetoothManager, device: BluetoothDevice, profile: Int): Boolean {
            try {
                if (manager.getConnectionState(device, profile) == BluetoothProfile.STATE_CONNECTED) {
                    return true
                }
            } catch (_: Exception) {
                // Fall through to the connected-devices list.
            }
            return try {
                manager.getConnectedDevices(profile).any { it.address == device.address }
            } catch (_: Exception) {
                false
            }
        }
    }

    private class Session(
        private val appContext: Context,
        private val adapter: BluetoothAdapter,
        private val bluetoothManager: BluetoothManager?,
    ) : AndroidDiscoverySession {

        private val devices = LinkedHashMap<String, AndroidDiscoveredDevice>()
        private val flow = MutableStateFlow<List<AndroidDiscoveredDevice>>(emptyList())
        override val results: Flow<List<AndroidDiscoveredDevice>> = flow

        private var receiver: BroadcastReceiver? = null
        private var scanning = false

        @SuppressLint("MissingPermission")
        fun start() {
            // 1) Bonded devices first: these are the user's own earbuds in most cases.
            // One unreadable device must never abort the rest of the list.
            try {
                adapter.bondedDevices?.forEach { device -> upsert(device, null, TransportKind.CLASSIC_BLUETOOTH) }
            } catch (_: Exception) {
                // Names/addresses stay hidden until permission is granted; scan may still work.
            }

            // 2) Classic discovery for nearby classic/dual devices.
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            val rcv = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    when (intent.action) {
                        BluetoothDevice.ACTION_FOUND -> {
                            val device: BluetoothDevice? =
                                if (Build.VERSION.SDK_INT >= 33) {
                                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                                } else {
                                    @Suppress("DEPRECATION")
                                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                                }
                            val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                                .takeIf { it != Short.MIN_VALUE }?.toInt()
                            if (device != null) upsert(device, rssi, TransportKind.CLASSIC_BLUETOOTH)
                        }
                    }
                }
            }
            receiver = rcv
            try {
                appContext.registerReceiver(rcv, filter)
                adapter.startDiscovery()
            } catch (_: SecurityException) {
                // BLE scan below may still deliver results.
            } catch (_: Exception) {
                // Discovery unsupported in this state; BLE scan may still deliver results.
            }

            // 3) BLE scan for LE advertisers.
            try {
                val scanner = adapter.bluetoothLeScanner
                if (scanner != null) {
                    val settings = ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build()
                    scanner.startScan(null, settings, leCallback)
                    scanning = true
                }
            } catch (_: SecurityException) {
                // No scan permission; whatever arrived above still stands.
            } catch (_: Exception) {
                // Scanner unavailable (e.g. adapter state changed mid-start).
            }
        }

        private val leCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                upsert(result.device, result.rssi, TransportKind.BLE, result.scanRecord?.deviceName, result.scanRecord?.serviceUuids)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onScanResult(0, it) }
            }
        }

        @SuppressLint("MissingPermission")
        private fun upsert(
            device: BluetoothDevice,
            rssi: Int?,
            transport: TransportKind,
            fallbackName: String? = null,
            services: Collection<ParcelUuid>? = null,
        ) {
            // Bulletproof by design: a single device the stack chokes on is
            // skipped, never allowed to abort discovery for everything else.
            try {
                upsertInternal(device, rssi, transport, fallbackName, services)
            } catch (_: Exception) {
                // Skip this device; discovery continues.
            }
        }

        @SuppressLint("MissingPermission")
        private fun upsertInternal(
            device: BluetoothDevice,
            rssi: Int?,
            transport: TransportKind,
            fallbackName: String? = null,
            services: Collection<ParcelUuid>? = null,
        ) {
            // OmniBuds is for earbuds/headphones only: speakers, car kits,
            // set-top boxes and other Bluetooth gear never enter the list.
            if (!AudioWearableFilter.isAudioWearable(device, services)) return
            val address = try {
                device.address
            } catch (_: Exception) {
                return
            } ?: return
            val name = try {
                device.name
            } catch (_: Exception) {
                null
            } ?: fallbackName
            val connected = isConnected(device)
            val existing = devices[address]
            devices[address] = AndroidDiscoveredDevice(
                id = address,
                name = name ?: existing?.name,
                alias = deviceAlias(device) ?: existing?.alias,
                address = address,
                connectionState = if (connected) {
                    ConnectionState.Connected("android-system", 1L, "Bluetooth")
                } else {
                    existing?.connectionState ?: ConnectionState.Disconnected
                },
                rssi = rssi ?: existing?.rssi,
                transportKind = transport,
                batterySummary = existing?.batterySummary,
                isSupported = true,
                lastSeenTimestamp = System.currentTimeMillis(),
            )
            flow.value = devices.values.toList()
        }

        @SuppressLint("MissingPermission")
        private fun isConnected(device: BluetoothDevice): Boolean {
            val manager = bluetoothManager ?: return false
            return PROFILES.any { profileConnected(manager, device, it) }
        }

        @SuppressLint("MissingPermission")
        override suspend fun stop() {
            teardown()
        }

        @SuppressLint("MissingPermission")
        override suspend fun cancel() {
            teardown()
        }

        private fun teardown() {
            if (scanning) {
                try {
                    adapter.bluetoothLeScanner?.stopScan(leCallback)
                } catch (_: Exception) {
                }
                scanning = false
            }
            try {
                if (adapter.isDiscovering) adapter.cancelDiscovery()
            } catch (_: Exception) {
            }
            receiver?.let {
                try {
                    appContext.unregisterReceiver(it)
                } catch (_: Exception) {
                }
                receiver = null
            }
        }
    }
}

/**
 * The alias Android displays for this device (the name the user sees in
 * system Bluetooth settings), API 30+. Null when unavailable.
 * Never throws. Top-level so both the provider and its scan session
 * (a non-inner nested class) can use it.
 */
@SuppressLint("MissingPermission")
private fun deviceAlias(device: BluetoothDevice): String? {
    if (Build.VERSION.SDK_INT < 30) return null
    return try {
        device.alias?.trim()?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }
}
