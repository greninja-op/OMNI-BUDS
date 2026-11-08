package com.omnibuds.android.hardware.pairing

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
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
import android.os.Build
import android.os.ParcelUuid
import com.omnibuds.android.hardware.capability.HardwareCapabilityInspector
import com.omnibuds.android.hardware.engine.FastPairAdvParser
import com.omnibuds.android.hardware.engine.UniversalTwsEngine
import com.omnibuds.android.presentation.devices.AndroidDiscoveredDevice
import com.omnibuds.android.presentation.devices.AndroidDiscoveryProvider
import com.omnibuds.android.presentation.devices.AndroidDiscoverySession
import com.omnibuds.android.ui.compose.model.AudioFormFactorResolver
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.device.DisplayIdentityResolver
import com.omnibuds.core.globalstate.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Stages of the OS-level Bluetooth bonding and audio routing state machine.
 */
sealed interface BondingStage {
    data object Idle : BondingStage
    data class Bonding(val address: String, val name: String, val message: String) : BondingStage
    data class Bonded(val address: String, val name: String, val message: String) : BondingStage
    data class AudioConnecting(val address: String, val name: String, val message: String) : BondingStage
    data class Connected(val address: String, val name: String) : BondingStage
    data class Failed(val address: String, val name: String, val error: String, val canRetry: Boolean = true) : BondingStage
}

/**
 * Universal Bluetooth Pairing and Discovery Engine.
 *
 * Implements:
 * 1. Dual-Mode Hybrid Scanner: Classic inquiry + Low-Latency BLE Scanner.
 * 2. Smart Audio Filter: Audio-only wearable verification and non-wearable blacklist.
 * 3. RPA / Classic Deduplication: Merges BLE Resolvable Private Addresses with Classic BR/EDR
 *    bondable addresses for the same physical device.
 * 4. Bonded/Disconnected Device Inclusion: Surfaces previously paired earbuds for one-tap reconnect.
 * 5. Full Unpaired Bonding State Machine: Manages `createBond()`, pairing dialogs, and timeouts.
 * 6. Automatic Audio Profile Routing: Connects `A2DP` and `HFP` via system profile proxies.
 * 7. Seamless hand-off to [UniversalTwsEngine] and [RememberedDevicesStore].
 */
class UniversalBluetoothPairingEngine(
    private val context: Context,
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val rememberedStore: RememberedDevicesStore = RememberedDevicesStore.get(context),
) : AndroidDiscoveryProvider {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(BluetoothManager::class.java)
    val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private val _bondingStage = MutableStateFlow<BondingStage>(BondingStage.Idle)
    val bondingStage: StateFlow<BondingStage> = _bondingStage.asStateFlow()

    @Volatile
    private var a2dpProxy: BluetoothA2dp? = null

    @Volatile
    private var headsetProxy: BluetoothHeadset? = null

    private var activeSession: HybridDiscoverySession? = null

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
            adapter?.getProfileProxy(context, profileListener, BluetoothProfile.A2DP)
            adapter?.getProfileProxy(context, profileListener, BluetoothProfile.HEADSET)
        } catch (_: Exception) {
            // Profile proxies are best-effort enhancements.
        }
    }

    override suspend fun startDiscovery(): AndroidDiscoverySession {
        val currentAdapter = adapter ?: throw IllegalStateException("Bluetooth adapter is not available")
        if (!currentAdapter.isEnabled) throw IllegalStateException("Bluetooth is turned off")

        activeSession?.stop()
        val session = HybridDiscoverySession(
            context = context,
            adapter = currentAdapter,
            bluetoothManager = bluetoothManager,
            engine = this,
        )
        activeSession = session
        session.start()
        return session
    }

    /**
     * Stop active inquiry and scan across the engine.
     */
    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        val session = activeSession
        activeSession = null
        if (session != null) {
            scope.launch {
                session.stop()
            }
        } else {
            try {
                if (adapter?.isDiscovering == true) adapter?.cancelDiscovery()
            } catch (_: Exception) {}
        }
    }

    fun resetBondingStage() {
        _bondingStage.value = BondingStage.Idle
    }

    /**
     * Stop discovery and release BluetoothProfile proxies.
     */
    fun close() {
        stopDiscovery()
        try {
            a2dpProxy?.let { adapter?.closeProfileProxy(BluetoothProfile.A2DP, it) }
            a2dpProxy = null
            headsetProxy?.let { adapter?.closeProfileProxy(BluetoothProfile.HEADSET, it) }
            headsetProxy = null
        } catch (_: Exception) {}
    }

    /**
     * Initiates OS-level bonding and audio routing for an unpaired or disconnected device.
     */
    @SuppressLint("MissingPermission")
    fun pairAndConnect(
        device: AndroidDiscoveredDevice,
        onStageChanged: ((BondingStage) -> Unit)? = null,
    ) {
        scope.launch {
            pairAndConnectInternal(device, onStageChanged)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun pairAndConnectInternal(
        device: AndroidDiscoveredDevice,
        onStageChanged: ((BondingStage) -> Unit)?,
    ) {
        val currentAdapter = adapter ?: run {
            emitStage(
                BondingStage.Failed(device.address, device.displayName, "Bluetooth adapter unavailable", false),
                onStageChanged,
            )
            return
        }

        val remoteDevice = try {
            currentAdapter.getRemoteDevice(device.address)
        } catch (e: Exception) {
            emitStage(
                BondingStage.Failed(device.address, device.displayName, "Invalid device address: ${e.message}", false),
                onStageChanged,
            )
            return
        }

        // 1. Cancel active discovery to prevent RF contention and pairing timeouts
        stopDiscovery()

        val initialBondState = try {
            remoteDevice.bondState
        } catch (_: Exception) {
            BluetoothDevice.BOND_NONE
        }

        if (initialBondState == BluetoothDevice.BOND_BONDED) {
            // Already bonded: proceed directly to audio profile routing
            emitStage(
                BondingStage.Bonded(device.address, device.displayName, "Device already paired. Routing audio..."),
                onStageChanged,
            )
            routeAudioAndHandOff(remoteDevice, device, onStageChanged)
            return
        }

        // 2. Initiate Bonding State Machine
        emitStage(
            BondingStage.Bonding(
                device.address,
                device.displayName,
                "Pairing with ${device.displayName}... Confirm on screen if prompted",
            ),
            onStageChanged,
        )

        val bondSuccess = performBonding(remoteDevice, device, onStageChanged)
        if (!bondSuccess) {
            return
        }

        // 3. Audio profile connection and handoff
        emitStage(
            BondingStage.Bonded(device.address, device.displayName, "Connecting Audio & Features..."),
            onStageChanged,
        )
        routeAudioAndHandOff(remoteDevice, device, onStageChanged)
    }

    @SuppressLint("MissingPermission")
    private suspend fun performBonding(
        remoteDevice: BluetoothDevice,
        device: AndroidDiscoveredDevice,
        onStageChanged: ((BondingStage) -> Unit)?,
    ): Boolean = withContext(Dispatchers.Default) {
        var isBonded = false
        var bondFailedReason: String? = null
        val bondLock = Any()
        var receiverRegistered = false

        val bondReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action
                val targetDevice: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }

                if (targetDevice?.address != remoteDevice.address) return

                when (action) {
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                        val prevBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)

                        if (bondState == BluetoothDevice.BOND_BONDED) {
                            synchronized(bondLock) {
                                isBonded = true
                            }
                        } else if (bondState == BluetoothDevice.BOND_NONE && prevBondState == BluetoothDevice.BOND_BONDING) {
                            synchronized(bondLock) {
                                bondFailedReason = "Pairing rejected or cancelled."
                            }
                        }
                    }
                    BluetoothDevice.ACTION_PAIRING_REQUEST -> {
                        emitStage(
                            BondingStage.Bonding(
                                device.address,
                                device.displayName,
                                "Pairing request received. Please confirm on screen.",
                            ),
                            onStageChanged,
                        )
                    }
                }
            }
        }

        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(bondReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(bondReceiver, filter)
            }
            receiverRegistered = true

            val bondTriggered = try {
                remoteDevice.createBond()
            } catch (e: Exception) {
                false
            }

            if (!bondTriggered && remoteDevice.bondState != BluetoothDevice.BOND_BONDING && remoteDevice.bondState != BluetoothDevice.BOND_BONDED) {
                emitStage(
                    BondingStage.Failed(
                        device.address,
                        device.displayName,
                        "Could not initiate pairing. Ensure device is in pairing mode.",
                        canRetry = true,
                    ),
                    onStageChanged,
                )
                return@withContext false
            }

            // 15-second bonding timeout window
            val timeoutDeadline = System.currentTimeMillis() + 15_000L
            while (System.currentTimeMillis() < timeoutDeadline) {
                if (remoteDevice.bondState == BluetoothDevice.BOND_BONDED) {
                    synchronized(bondLock) { isBonded = true }
                    break
                }
                synchronized(bondLock) {
                    if (isBonded || bondFailedReason != null) return@synchronized
                }
                if (bondFailedReason != null) break
                delay(250L)
            }

            val finalBonded = synchronized(bondLock) { isBonded } || remoteDevice.bondState == BluetoothDevice.BOND_BONDED
            if (finalBonded) {
                return@withContext true
            } else {
                val reason = bondFailedReason ?: "Pairing timed out (15s). Ensure earbuds are in pairing mode."
                emitStage(
                    BondingStage.Failed(device.address, device.displayName, reason, canRetry = true),
                    onStageChanged,
                )
                return@withContext false
            }
        } finally {
            if (receiverRegistered) {
                try {
                    context.unregisterReceiver(bondReceiver)
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Connects A2DP and Headset profiles, launches telemetry engines, and persists the device.
     */
    @SuppressLint("MissingPermission")
    private suspend fun routeAudioAndHandOff(
        remoteDevice: BluetoothDevice,
        device: AndroidDiscoveredDevice,
        onStageChanged: ((BondingStage) -> Unit)?,
    ) {
        emitStage(
            BondingStage.AudioConnecting(device.address, device.displayName, "Connecting Audio & Features..."),
            onStageChanged,
        )

        val audioLock = Any()
        var audioConnected = isAudioProfileConnected(remoteDevice)
        var audioReceiverRegistered = false

        val audioProfileReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val action = intent.action
                if (action == BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED ||
                    action == BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED) {
                    val targetDevice: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (targetDevice?.address == remoteDevice.address) {
                        val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED)
                        if (state == BluetoothProfile.STATE_CONNECTED) {
                            synchronized(audioLock) {
                                audioConnected = true
                            }
                        }
                    }
                }
            }
        }

        try {
            val audioFilter = IntentFilter().apply {
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(audioProfileReceiver, audioFilter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(audioProfileReceiver, audioFilter)
            }
            audioReceiverRegistered = true

            // Attempt reflection-based connect on A2DP and HFP proxies
            connectAudioProfiles(remoteDevice)

            // Allow up to 4.0s grace period for profile links to announce STATE_CONNECTED or stabilize
            var attempts = 0
            while (attempts < 16) {
                val isConnected = synchronized(audioLock) { audioConnected } || isAudioProfileConnected(remoteDevice)
                if (isConnected) {
                    break
                }
                delay(250L)
                attempts++
            }
        } finally {
            if (audioReceiverRegistered) {
                try {
                    context.unregisterReceiver(audioProfileReceiver)
                } catch (_: Exception) {}
            }
        }

        // Seamless hand-off to UniversalTwsEngine
        try {
            UniversalTwsEngine.connect(context, remoteDevice)
        } catch (_: Exception) {}

        // Inspect capabilities and resolve form factor
        val formFactor = AudioFormFactorResolver.resolve(
            context = context,
            address = device.address,
            deviceName = device.displayName,
        )
        HardwareCapabilityInspector.inspect(remoteDevice, device.address, device.displayName)

        // Persist to RememberedDevicesStore
        rememberedStore.saveDevice(
            RememberedDevice(
                address = device.address,
                name = device.displayName,
                formFactor = formFactor.name,
                firstPairedTimestamp = System.currentTimeMillis(),
                lastConnectedTimestamp = System.currentTimeMillis(),
                autoReconnect = true,
            ),
        )

        emitStage(
            BondingStage.Connected(device.address, device.displayName),
            onStageChanged,
        )
    }

    @SuppressLint("MissingPermission")
    private fun connectAudioProfiles(device: BluetoothDevice) {
        val a2dp = a2dpProxy
        val hfp = headsetProxy

        if (a2dp != null) {
            connectProfileViaReflection(a2dp, device)
        }
        if (hfp != null) {
            connectProfileViaReflection(hfp, device)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectProfileViaReflection(proxy: BluetoothProfile, device: BluetoothDevice): Boolean {
        return try {
            val method = proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
            method.isAccessible = true
            val res = method.invoke(proxy, device)
            (res as? Boolean) ?: true
        } catch (_: Exception) {
            // Some OEM stacks restrict hidden connect() reflection; fallback to standard system handling.
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun isAudioProfileConnected(device: BluetoothDevice): Boolean {
        a2dpProxy?.let { proxy ->
            try {
                if (proxy.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED) return true
                if (proxy.connectedDevices.any { it.address == device.address }) return true
            } catch (_: Exception) {}
        }
        headsetProxy?.let { proxy ->
            try {
                if (proxy.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED) return true
                if (proxy.connectedDevices.any { it.address == device.address }) return true
            } catch (_: Exception) {}
        }
        return false
    }

    private fun emitStage(stage: BondingStage, callback: ((BondingStage) -> Unit)?) {
        _bondingStage.value = stage
        callback?.invoke(stage)
    }

    companion object {
        // Fast Pair service UUID (0xFE2C)
        val FAST_PAIR_SERVICE_UUID: UUID = UUID.fromString("0000fe2c-0000-1000-8000-00805f9b34fb")

        // Audio Service UUIDs
        val A2DP_SINK_UUID: UUID = UUID.fromString("0000110b-0000-1000-8000-00805f9b34fb")
        val HEADSET_UUID: UUID = UUID.fromString("00001108-0000-1000-8000-00805f9b34fb")
        val HANDSFREE_UUID: UUID = UUID.fromString("0000111e-0000-1000-8000-00805f9b34fb")
        val HANDSFREE_AG_UUID: UUID = UUID.fromString("0000111f-0000-1000-8000-00805f9b34fb")
        val AVRCP_UUID: UUID = UUID.fromString("0000110e-0000-1000-8000-00805f9b34fb")
        val LE_AUDIO_ASCS_UUID: UUID = UUID.fromString("0000184e-0000-1000-8000-00805f9b34fb")
        val LE_AUDIO_PACS_UUID: UUID = UUID.fromString("00001850-0000-1000-8000-00805f9b34fb")
        val LE_AUDIO_BAP_UUID: UUID = UUID.fromString("00001853-0000-1000-8000-00805f9b34fb")

        val AUDIO_SERVICE_UUIDS = setOf(
            FAST_PAIR_SERVICE_UUID,
            A2DP_SINK_UUID,
            HEADSET_UUID,
            HANDSFREE_UUID,
            HANDSFREE_AG_UUID,
            AVRCP_UUID,
            LE_AUDIO_ASCS_UUID,
            LE_AUDIO_PACS_UUID,
            LE_AUDIO_BAP_UUID,
        )

        private val CAR_NAME_TOKENS = setOf(
            "car", "carkit", "innova", "fortuner", "creta", "nexon", "baleno",
            "ertiga", "scorpio", "toyota", "honda", "hyundai", "kia", "ford",
            "nissan", "renault", "skoda", "volkswagen", "mahindra", "maruti",
            "suzuki", "bmw", "audi", "jeep", "chevrolet", "fiat", "volvo",
        )

        private val NON_AUDIO_NAME_TOKENS = setOf(
            "tv", "smarttv", "bravia", "firestick", "roku",
            "watch", "applewatch",
            "laptop", "desktop", "macbook", "ipad", "iphone", "printer",
        )

        private val AUDIO_BRAND_KEYWORDS = listOf(
            "realme", "buds", "airpods", "oneplus", "sony", "wh-", "wf-", "wi-",
            "galaxy buds", "nothing", "cmf", "bose", "sennheiser", "jbl", "boat",
            "rockerz", "bullets", "soundcore", "earfun", "shokz", "oppo",
            "beats", "edifier", "anker", "pixel buds", "freebuds", "headphone",
            "headset", "earbud", "earphone", "tws", "openrun", "linkbuds",
            "redmi", "mi ", "soundpeats", "marshall",
        )

        /**
         * Comprehensive smart audio filter:
         * Validates BluetoothClass, advertised audio UUIDs, manufacturer proximity packets,
         * and audio brand dictionary while strictly excluding cars, TVs, and smartwatches.
         */
        fun isAudioWearableDevice(
            name: String?,
            bluetoothClass: BluetoothClass?,
            serviceUuids: Collection<ParcelUuid>? = null,
            rawUuids: Collection<UUID>? = null,
            manufacturerData: Map<Int, ByteArray>? = null,
        ): Boolean {
            val lowerName = name?.lowercase()?.trim()

            // 1. Strict Car Audio and Non-Wearable Exclusion
            if (lowerName != null) {
                if (lowerName.contains("car kit") || lowerName.contains("car-kit")) return false
                val tokens = lowerName.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
                if (tokens.any { it in CAR_NAME_TOKENS }) return false
                if (tokens.any { it in NON_AUDIO_NAME_TOKENS }) return false
            }

            // 2. Hardware Class of Device (COD) validation
            if (bluetoothClass != null) {
                when (bluetoothClass.majorDeviceClass) {
                    BluetoothClass.Device.Major.AUDIO_VIDEO -> {
                        when (bluetoothClass.deviceClass) {
                            BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
                            BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
                            BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE,
                            BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO,
                            -> return true
                            BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
                            BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO,
                            BluetoothClass.Device.AUDIO_VIDEO_SET_TOP_BOX,
                            -> return false
                        }
                    }
                    BluetoothClass.Device.Major.PHONE,
                    BluetoothClass.Device.Major.COMPUTER,
                    BluetoothClass.Device.Major.IMAGING,
                    BluetoothClass.Device.Major.PERIPHERAL,
                    BluetoothClass.Device.Major.NETWORKING,
                    BluetoothClass.Device.Major.TOY,
                    -> return false
                    BluetoothClass.Device.Major.WEARABLE -> {
                        if (bluetoothClass.deviceClass == BluetoothClass.Device.WEARABLE_WRIST_WATCH) {
                            return false
                        }
                    }
                }
            }

            // 3. Advertised Audio Service UUIDs (Fast Pair, A2DP, HFP, LE Audio)
            if (rawUuids != null) {
                for (u in rawUuids) {
                    if (AUDIO_SERVICE_UUIDS.contains(u)) {
                        return true
                    }
                }
            }
            if (serviceUuids != null) {
                for (parcelUuid in serviceUuids) {
                    val u = try { parcelUuid.uuid } catch (_: Exception) { null }
                    if (u != null && AUDIO_SERVICE_UUIDS.contains(u)) {
                        return true
                    }
                }
            }

            // 4. Vendor Proximity Advertisements (Apple Continuity 0x004C)
            if (manufacturerData != null) {
                val appleData = manufacturerData[0x004C]
                if (appleData != null && appleData.isNotEmpty()) {
                    val type = appleData[0].toInt() and 0xFF
                    // 0x07 = AirPods proximity, 0x10 = Nearby Action
                    if (type == 0x07 || type == 0x10) return true
                }
            }

            // 5. Audio Keyword Dictionary fallback
            if (lowerName != null) {
                if (AUDIO_BRAND_KEYWORDS.any { lowerName.contains(it) }) {
                    return true
                }
            }

            return false
        }

        /**
         * Normalizes a device name for cross-transport deduplication.
         */
        fun normalizeDeviceName(name: String?): String {
            if (name.isNullOrBlank()) return ""
            return name.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
        }
    }
}

/**
 * Active dual-mode discovery session tracking and deduplicating live devices.
 */
/**
 * Cross-Transport Smart Deduplicator:
 * When BLE RPA and Classic BR/EDR addresses represent the same physical device name or Fast Pair ID,
 * prioritizes the Classic BR/EDR bondable address.
 * Preserves distinct physical devices sharing the same commercial model name.
 */
internal class HybridDeviceDeduplicator {
    private val deviceMap = LinkedHashMap<String, AndroidDiscoveredDevice>()
    private val nameToAddressMap = HashMap<String, String>()
    private val fastPairIdToAddressMap = HashMap<String, String>()

    private val flow = MutableStateFlow<List<AndroidDiscoveredDevice>>(emptyList())
    val results: Flow<List<AndroidDiscoveredDevice>> = flow
    val currentDevices: List<AndroidDiscoveredDevice> get() = flow.value

    fun upsert(device: AndroidDiscoveredDevice, fastPairModelId: String? = null) {
        synchronized(deviceMap) {
            val rawName = device.name?.trim()
            val hasRealName = !rawName.isNullOrEmpty() &&
                !rawName.equals(DisplayIdentityResolver.FALLBACK_DISPLAY_NAME, ignoreCase = true)
            val normName = if (hasRealName) UniversalBluetoothPairingEngine.normalizeDeviceName(rawName) else ""

            val existingByAddress = deviceMap[device.address]

            if (existingByAddress != null) {
                // Address already known: update signal & details
                val merged = existingByAddress.copy(
                    name = device.name ?: existingByAddress.name,
                    alias = device.alias ?: existingByAddress.alias,
                    rssi = device.rssi ?: existingByAddress.rssi,
                    connectionState = if (device.connectionState is ConnectionState.Connected) device.connectionState else existingByAddress.connectionState,
                    lastSeenTimestamp = System.currentTimeMillis(),
                    isBonded = device.isBonded || existingByAddress.isBonded,
                )
                deviceMap[device.address] = merged
            } else {
                // Check if a previously discovered BLE RPA shares the same physical device name or Fast Pair ID
                val correlatedAddress = when {
                    fastPairModelId != null && fastPairIdToAddressMap.containsKey(fastPairModelId) ->
                        fastPairIdToAddressMap[fastPairModelId]
                    normName.isNotEmpty() && nameToAddressMap.containsKey(normName) ->
                        nameToAddressMap[normName]
                    else -> null
                }

                val correlatedExisting = correlatedAddress?.let { deviceMap[it] }

                if (correlatedExisting != null) {
                    if (correlatedExisting.transportKind == TransportKind.BLE && device.transportKind == TransportKind.CLASSIC_BLUETOOTH) {
                        // Upgrade from BLE RPA to Classic BR/EDR bondable address!
                        deviceMap.remove(correlatedAddress)
                        if (normName.isNotEmpty()) nameToAddressMap[normName] = device.address
                        if (fastPairModelId != null) fastPairIdToAddressMap[fastPairModelId] = device.address
                        val upgraded = device.copy(
                            rssi = device.rssi ?: correlatedExisting.rssi,
                            lastSeenTimestamp = System.currentTimeMillis(),
                            isBonded = device.isBonded || correlatedExisting.isBonded,
                        )
                        deviceMap[device.address] = upgraded
                    } else if (correlatedExisting.transportKind == TransportKind.CLASSIC_BLUETOOTH && device.transportKind == TransportKind.BLE) {
                        // Classic is already the primary bondable address; refresh signal on Classic entry
                        val refreshed = correlatedExisting.copy(
                            rssi = device.rssi ?: correlatedExisting.rssi,
                            lastSeenTimestamp = System.currentTimeMillis(),
                            isBonded = device.isBonded || correlatedExisting.isBonded,
                        )
                        deviceMap[correlatedAddress] = refreshed
                    } else if (correlatedExisting.transportKind == TransportKind.CLASSIC_BLUETOOTH && device.transportKind == TransportKind.CLASSIC_BLUETOOTH) {
                        // Both are Classic BR/EDR with different MACs: TWO SEPARATE PHYSICAL DEVICES!
                        deviceMap[device.address] = device
                    } else {
                        // Both are BLE with different addresses
                        deviceMap[device.address] = device
                        if (normName.isNotEmpty() && !nameToAddressMap.containsKey(normName)) {
                            nameToAddressMap[normName] = device.address
                        }
                    }
                } else {
                    // New device
                    deviceMap[device.address] = device
                    if (normName.isNotEmpty() && !nameToAddressMap.containsKey(normName)) {
                        nameToAddressMap[normName] = device.address
                    }
                    if (fastPairModelId != null && !fastPairIdToAddressMap.containsKey(fastPairModelId)) {
                        fastPairIdToAddressMap[fastPairModelId] = device.address
                    }
                }
            }

            flow.value = deviceMap.values.toList()
        }
    }

    fun updateDeviceName(address: String, newName: String) {
        synchronized(deviceMap) {
            val existing = deviceMap[address]
            if (existing != null) {
                deviceMap[address] = existing.copy(name = newName)
                flow.value = deviceMap.values.toList()
            }
        }
    }

    fun clear() {
        synchronized(deviceMap) {
            deviceMap.clear()
            nameToAddressMap.clear()
            fastPairIdToAddressMap.clear()
            flow.value = emptyList()
        }
    }
}

/**
 * Active dual-mode discovery session tracking and deduplicating live devices.
 */
private class HybridDiscoverySession(
    private val context: Context,
    private val adapter: BluetoothAdapter,
    private val bluetoothManager: BluetoothManager?,
    private val engine: UniversalBluetoothPairingEngine,
) : AndroidDiscoverySession {

    private val deduplicator = HybridDeviceDeduplicator()
    override val results: Flow<List<AndroidDiscoveredDevice>> = deduplicator.results

    private var classicReceiver: BroadcastReceiver? = null
    private var isBleScanning = false
    private var isClassicInquiring = false
    @Volatile
    private var isRunning = false

    @SuppressLint("MissingPermission")
    fun start() {
        isRunning = true

        // 1. Bonded devices: surface paired but disconnected audio wearables on outer orbit
        try {
            val bonded = adapter.bondedDevices ?: emptySet()
            for (device in bonded) {
                if (!UniversalBluetoothPairingEngine.isAudioWearableDevice(device.name, device.bluetoothClass)) {
                    continue
                }
                val address = device.address ?: continue
                val name = device.name
                val connected = isDeviceConnected(device)

                val discovered = AndroidDiscoveredDevice(
                    id = address,
                    name = name,
                    alias = getDeviceAlias(device),
                    address = address,
                    connectionState = if (connected) ConnectionState.Connected("system", 1L, "Bluetooth") else ConnectionState.Disconnected,
                    rssi = null,
                    transportKind = TransportKind.CLASSIC_BLUETOOTH,
                    batterySummary = null,
                    isSupported = true,
                    lastSeenTimestamp = System.currentTimeMillis(),
                    isBonded = true,
                )
                deduplicator.upsert(discovered)
            }
        } catch (_: Exception) {}

        // 2. Classic Bluetooth Discovery Receiver
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice = (if (Build.VERSION.SDK_INT >= 33) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }) ?: return

                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                            .takeIf { it != Short.MIN_VALUE }?.toInt()
                        val btClass: BluetoothClass? = if (Build.VERSION.SDK_INT >= 33) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_CLASS, BluetoothClass::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_CLASS)
                        }

                        if (!UniversalBluetoothPairingEngine.isAudioWearableDevice(device.name, btClass ?: device.bluetoothClass)) {
                            return
                        }

                        val address = device.address ?: return
                        val name = device.name
                        val isBonded = device.bondState == BluetoothDevice.BOND_BONDED
                        val isConnected = isDeviceConnected(device)

                        val discovered = AndroidDiscoveredDevice(
                            id = address,
                            name = name,
                            alias = getDeviceAlias(device),
                            address = address,
                            connectionState = if (isConnected) ConnectionState.Connected("system", 1L, "Bluetooth") else ConnectionState.Disconnected,
                            rssi = rssi,
                            transportKind = TransportKind.CLASSIC_BLUETOOTH,
                            batterySummary = null,
                            isSupported = true,
                            lastSeenTimestamp = System.currentTimeMillis(),
                            isBonded = isBonded,
                        )
                        deduplicator.upsert(discovered)
                    }
                    BluetoothDevice.ACTION_NAME_CHANGED -> {
                        val device: BluetoothDevice = (if (Build.VERSION.SDK_INT >= 33) {
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        }) ?: return

                        val address = device.address ?: return
                        deduplicator.updateDeviceName(address, device.name ?: "")
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                        isClassicInquiring = true
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        isClassicInquiring = false
                        if (isRunning && adapter.isEnabled) {
                            engine.scope.launch {
                                delay(1000L)
                                if (isRunning && adapter.isEnabled && !adapter.isDiscovering) {
                                    try {
                                        if (adapter.startDiscovery()) {
                                            isClassicInquiring = true
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    }
                }
            }
        }
        classicReceiver = receiver

        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothDevice.ACTION_NAME_CHANGED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            if (adapter.startDiscovery()) {
                isClassicInquiring = true
            }
        } catch (_: Exception) {}

        // 3. Low-Latency BLE Scanner
        try {
            val scanner = adapter.bluetoothLeScanner
            if (scanner != null) {
                val settings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build()
                scanner.startScan(null, settings, bleCallback)
                isBleScanning = true
            }
        } catch (_: Exception) {}
    }

    private val bleCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val record = result.scanRecord
            val name = record?.deviceName ?: device.name

            val mfgData = HashMap<Int, ByteArray>()
            record?.manufacturerSpecificData?.let { sparse ->
                for (i in 0 until sparse.size()) {
                    mfgData[sparse.keyAt(i)] = sparse.valueAt(i)
                }
            }

            if (!UniversalBluetoothPairingEngine.isAudioWearableDevice(
                    name = name,
                    bluetoothClass = device.bluetoothClass,
                    serviceUuids = record?.serviceUuids,
                    manufacturerData = mfgData,
                )
            ) {
                return
            }

            val fpData = record?.getServiceData(ParcelUuid(UniversalBluetoothPairingEngine.FAST_PAIR_SERVICE_UUID))
            val fastPairModelId = if (fpData != null && fpData.size == 3) {
                val isBattery = FastPairAdvParser.parse(fpData)?.hasComponentData == true
                if (!isBattery) {
                    String.format("%02X%02X%02X", fpData[0], fpData[1], fpData[2])
                } else null
            } else null

            val address = device.address ?: return
            val isBonded = device.bondState == BluetoothDevice.BOND_BONDED
            val isConnected = isDeviceConnected(device)

            val discovered = AndroidDiscoveredDevice(
                id = address,
                name = name,
                alias = getDeviceAlias(device),
                address = address,
                connectionState = if (isConnected) ConnectionState.Connected("system", 1L, "Bluetooth") else ConnectionState.Disconnected,
                rssi = result.rssi,
                transportKind = TransportKind.BLE,
                batterySummary = null,
                isSupported = true,
                lastSeenTimestamp = System.currentTimeMillis(),
                isBonded = isBonded,
            )
            deduplicator.upsert(discovered, fastPairModelId)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { onScanResult(0, it) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getDeviceAlias(device: BluetoothDevice): String? {
        return if (Build.VERSION.SDK_INT >= 30) {
            try {
                device.alias
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    @SuppressLint("MissingPermission")
    private fun isDeviceConnected(device: BluetoothDevice): Boolean {
        val manager = bluetoothManager ?: return false
        return try {
            manager.getConnectionState(device, BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED ||
                manager.getConnectionState(device, BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED
        } catch (_: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun stop() {
        teardown()
    }

    @SuppressLint("MissingPermission")
    override suspend fun cancel() {
        teardown()
    }

    @SuppressLint("MissingPermission")
    private fun teardown() {
        isRunning = false
        if (isBleScanning) {
            try {
                adapter.bluetoothLeScanner?.stopScan(bleCallback)
            } catch (_: Exception) {}
            isBleScanning = false
        }
        if (isClassicInquiring || adapter.isDiscovering) {
            try {
                adapter.cancelDiscovery()
            } catch (_: Exception) {}
            isClassicInquiring = false
        }
        classicReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) {}
            classicReceiver = null
        }
    }
}
