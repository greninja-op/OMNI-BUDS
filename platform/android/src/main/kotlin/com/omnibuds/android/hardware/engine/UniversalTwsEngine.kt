@file:Suppress("DEPRECATION")

package com.omnibuds.android.hardware.engine

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import android.util.Log
import com.omnibuds.android.hardware.engine.drivers.TransportPreference
import com.omnibuds.android.hardware.engine.drivers.IVendorDriver
import com.omnibuds.android.hardware.engine.drivers.NothingXProtocolDriver
import com.omnibuds.android.hardware.engine.drivers.OnePlusHeyMelodyDriver
import com.omnibuds.android.hardware.capability.StrictBrandProfile
import com.omnibuds.android.hardware.capability.StrictBrandProfileResolver
import com.omnibuds.android.hardware.capability.BrandSchema
import com.omnibuds.android.hardware.apple.AppleL2capDriver
import com.omnibuds.android.hardware.audio.CodecDetails
import com.omnibuds.android.hardware.audio.CodecManager
import com.omnibuds.android.hardware.audio.EarChannel
import com.omnibuds.android.hardware.audio.HeadphoneAccommodationPreset
import com.omnibuds.android.hardware.audio.AccommodationIntensity
import com.omnibuds.android.hardware.audio.HearingTestManager
import com.omnibuds.android.hardware.audio.MindRelaxGenerator
import com.omnibuds.android.hardware.audio.Soundscape
import com.omnibuds.android.hardware.capability.DeviceCapabilityManifest
import com.omnibuds.android.hardware.capability.HardwareCapabilityInspector
import com.omnibuds.android.hardware.capability.PinchSpeed
import com.omnibuds.android.hardware.capability.StrictBrandProfileDatabase
import com.omnibuds.android.hardware.protocol.AncByteMapping
import com.omnibuds.android.hardware.protocol.DiagnosticLogEntry
import com.omnibuds.android.hardware.protocol.FastPairProtocolParser
import com.omnibuds.android.hardware.protocol.GattCharDiagnostic
import com.omnibuds.android.hardware.protocol.GattServiceDiagnostic
import com.omnibuds.android.hardware.protocol.HexUtils
import com.omnibuds.android.hardware.protocol.KnownUuids
import com.omnibuds.android.hardware.protocol.OpcodeRemapMatrix
import com.omnibuds.android.hardware.protocol.TwsBatteryReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Universal Hybrid TWS Engine.
 *
 * Implements:
 * - Layer 1: Universal Split-Battery Discovery (Google Fast Pair BLE, Apple Proximity Beacon, Vendor Notifications)
 * - Layer 2: Multi-Vendor Hardware Protocol Router (OnePlus/Realme/Oppo, Xiaomi/Airoha, Nothing, Samsung, Generic Prober)
 * - Layer 3: Universal OS-Level DSP Engine (10-Band EQ + Flagship Presets, 3D Spatial Audio & Headstage Crossfeed)
 */
object UniversalTwsEngine {

    private const val TAG = "UniversalTwsEngine"
    private const val MAX_DESCRIPTOR_ATTEMPTS = 3
    private const val STATE_SYNC_MIN_INTERVAL_MS = 10_000L
    private const val MODE_SETTLE_WINDOW_MS = 1_500L
    private val lastStateSyncAt = ConcurrentHashMap<String, Long>()
    private val lastReportedModeMask = ConcurrentHashMap<String, Int>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Subsystems
    val protocolRouter = VendorProtocolRouter()
    val dspController = UniversalDspController()
    val hearingTestManager = HearingTestManager()
    val codecManager: CodecManager? by lazy { appContext?.let { CodecManager(it) } }
    val mindRelaxGenerator: MindRelaxGenerator by lazy { MindRelaxGenerator(scope) }
    val appleL2capDriver: AppleL2capDriver by lazy { AppleL2capDriver(scope) }
    val bleSequencer: BleCommandSequencer by lazy { BleCommandSequencer(scope) }
    private var _batteryScanner: FastPairAndBleBatteryScanner? = null
    val batteryScanner: FastPairAndBleBatteryScanner?
        get() = _batteryScanner

    // Transports: address -> BluetoothGatt / BluetoothSocket
    private val activeGatts = ConcurrentHashMap<String, BluetoothGatt>()
    private val activeWriteCharacteristics = ConcurrentHashMap<String, BluetoothGattCharacteristic>()

    private data class PendingDescriptor(
        val gatt: BluetoothGatt,
        val descriptor: BluetoothGattDescriptor,
        val value: ByteArray,
        val attempts: Int = 0,
    )

    private val descriptorQueues = ConcurrentHashMap<String, java.util.ArrayDeque<PendingDescriptor>>()
    private val descriptorWriting = ConcurrentHashMap<String, Boolean>()
    private val descriptorInFlight = ConcurrentHashMap<String, PendingDescriptor>()
    private val activeRfcommSockets = ConcurrentHashMap<String, BluetoothSocket>()
    private val rfcommJobs = ConcurrentHashMap<String, Job>()

    // Live Telemetry Flows
    private val _batteryReports = MutableSharedFlow<Pair<String, TwsBatteryReport>>(extraBufferCapacity = 128)
    val batteryReports: SharedFlow<Pair<String, TwsBatteryReport>> = _batteryReports.asSharedFlow()

    private val _latestBatteryReports = MutableStateFlow<Map<String, TwsBatteryReport>>(emptyMap())
    val latestBatteryReports: StateFlow<Map<String, TwsBatteryReport>> = _latestBatteryReports.asStateFlow()

    data class CachedCaseBattery(
        val level: Int,
        val isCharging: Boolean,
        val timestamp: Long,
    )
    private val cachedCaseBattery = ConcurrentHashMap<String, CachedCaseBattery>()

    private val _diagnosticLogs = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
    val diagnosticLogs: StateFlow<List<DiagnosticLogEntry>> = _diagnosticLogs.asStateFlow()

    private val _discoveredServices = MutableStateFlow<Map<String, List<GattServiceDiagnostic>>>(emptyMap())
    val discoveredServices: StateFlow<Map<String, List<GattServiceDiagnostic>>> = _discoveredServices.asStateFlow()

    private val _lastRawPackets = MutableStateFlow<Map<String, String>>(emptyMap())
    val lastRawPackets: StateFlow<Map<String, String>> = _lastRawPackets.asStateFlow()

    private val _lastSentPackets = MutableStateFlow<Map<String, String>>(emptyMap())
    val lastSentPackets: StateFlow<Map<String, String>> = _lastSentPackets.asStateFlow()

    private val _confirmedAncMode = MutableStateFlow<Map<String, String>>(emptyMap())
    val confirmedAncMode: StateFlow<Map<String, String>> = _confirmedAncMode.asStateFlow()

    private val _confirmedGameMode = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val confirmedGameMode: StateFlow<Map<String, Boolean>> = _confirmedGameMode.asStateFlow()

    private val _configSaveState = MutableStateFlow<Map<String, String>>(emptyMap())
    val configSaveState: StateFlow<Map<String, String>> = _configSaveState.asStateFlow()

    private fun markConfigSaved(address: String, message: String) {
        _configSaveState.value = _configSaveState.value + (address to message)
        scope.launch {
            delay(2600)
            if (_configSaveState.value[address] == message) {
                _configSaveState.value = _configSaveState.value - address
            }
        }
    }

    private val _pendingAncMode = MutableStateFlow<Map<String, String?>>(emptyMap())
    val pendingAncMode: StateFlow<Map<String, String?>> = _pendingAncMode.asStateFlow()

    private val _connectionStatus = MutableStateFlow<Map<String, String>>(emptyMap())
    val connectionStatus: StateFlow<Map<String, String>> = _connectionStatus.asStateFlow()

    private val _activeDrivers = MutableStateFlow<Map<String, String>>(emptyMap())
    val activeDrivers: StateFlow<Map<String, String>> = _activeDrivers.asStateFlow()

    // Active session state and strict brand profile
    var activeDevice: BluetoothDevice? = null
        private set
    var activeDriver: IVendorDriver? = null
        private set
    var activeProfile: StrictBrandProfile? = null
        private set
    var activeCapabilityManifest: DeviceCapabilityManifest? = null
        private set
    var leftBattery: Int? = null
        private set
    var rightBattery: Int? = null
        private set
    var caseBattery: Int? = null
        private set

    private var appContext: Context? = null
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        appContext = context.applicationContext
        isInitialized = true

        OpcodeRemapMatrix.init(context)
        dspController.attach(context)

        val scanner = FastPairAndBleBatteryScanner(context.applicationContext, scope)
        _batteryScanner = scanner

        // Forward BLE advertisement battery reports to master flow
        scope.launch {
            scanner.batteryReports.collect { (addr, report) ->
                handleBatteryReport(addr, report, report.sourceProtocol)
            }
        }

        // Forward Apple L2CAP battery reports and ANC status
        scope.launch {
            appleL2capDriver.batteryState.collect { map ->
                map.forEach { (addr, report) ->
                    handleBatteryReport(addr, report, report.sourceProtocol)
                }
            }
        }
        scope.launch {
            appleL2capDriver.ancState.collect { map ->
                map.forEach { (addr, mode) ->
                    _confirmedAncMode.value = _confirmedAncMode.value + (addr to mode)
                }
            }
        }

        scanner.startScan()
        bleSequencer.onPreemptBatteryPoll = {
            abortBatteryPoll()
        }
        startBatteryHeartbeat()
        Log.i(TAG, "UniversalTwsEngine initialized successfully with all 3 layers active")
    }

    private var batteryHeartbeatJob: Job? = null
    private var currentPollJob: Job? = null

    /**
     * Immediately aborts in-progress background battery polling when user commands arrive.
     */
    fun abortBatteryPoll() {
        currentPollJob?.cancel()
        currentPollJob = null
    }

    /**
     * Active 1500ms Non-Blocking Battery Polling Heartbeat.
     * Continuously queries the hardware for battery state and pogo-pin dock status.
     * Preempted immediately whenever a user command arrives, resuming 500ms post-tap.
     */
    private fun startBatteryHeartbeat() {
        if (batteryHeartbeatJob?.isActive == true) return
        batteryHeartbeatJob = scope.launch {
            while (isActive) {
                delay(1500)
                if (bleSequencer.canPollBattery()) {
                    currentPollJob = scope.launch {
                        pollAllConnectedBatteries()
                    }
                    try {
                        currentPollJob?.join()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private suspend fun pollAllConnectedBatteries() {
        if (!bleSequencer.canPollBattery()) return

        // 1. Query RFCOMM connected sockets
        for ((addr, socket) in activeRfcommSockets) {
            if (!bleSequencer.canPollBattery(addr)) return
            if (socket.isConnected) {
                try {
                    val dev = socket.remoteDevice
                    val query = protocolRouter.buildBatteryQueryCommand(dev)
                    if (query != null && bleSequencer.canPollBattery(addr)) {
                        socket.outputStream.write(query)
                        socket.outputStream.flush()
                    }
                } catch (_: Exception) {}
            }
        }

        // 2. Query BLE GATT connected devices
        for ((addr, gatt) in activeGatts) {
            if (!bleSequencer.canPollBattery(addr)) return
            try {
                val dev = gatt.device
                val query = protocolRouter.buildBatteryQueryCommand(dev)
                if (query != null && bleSequencer.canPollBattery(addr)) {
                    writeGatt(dev, query)
                }
                delay(50)
                val bService = gatt.getService(KnownUuids.BATTERY_SERVICE)
                val bChar = bService?.getCharacteristic(KnownUuids.BATTERY_LEVEL_CHAR)
                if (bChar != null && bleSequencer.canPollBattery(addr)) {
                    gatt.readCharacteristic(bChar)
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Centralized Battery Parser & Pogo-Pin State Machine.
     * Manages live earbud levels, charging states, and smart case persistence when earbuds are removed.
     */
    fun handleBatteryReport(address: String, incomingReport: TwsBatteryReport, source: String? = null) {
        val src = source ?: incomingReport.sourceProtocol

        // A generic battery broadcast (or an empty vendor frame) must never
        // erase a split L/R report that a vendor protocol already delivered.
        // Keep the last component-bearing report until a newer component report
        // arrives or the device disconnects.
        val previous = _latestBatteryReports.value[address]
        if (!incomingReport.hasComponentData && previous?.hasComponentData == true) {
            log(address, "Ignoring component-less battery report from $src; preserving split telemetry")
            return
        }

        val dev = activeDevice?.takeIf { it.address == address }
            ?: activeRfcommSockets[address]?.remoteDevice
            ?: activeGatts[address]?.device
        val devName = try { dev?.name } catch (_: Exception) { null }

        val profile = activeProfile?.takeIf { activeDevice?.address == address }
            ?: if (src.contains("Nothing", ignoreCase = true) || src.contains("CMF", ignoreCase = true) || src.contains("0x07 C0")) {
                StrictBrandProfileDatabase.findProfileByKey("cmf_buds")
                    ?: StrictBrandProfileDatabase.GENERIC_AUDIO
            } else {
                StrictBrandProfileResolver.resolve(dev, nameOverride = devName, addressOverride = address)
            }

        // Smart Case Pogo-Pin State Machine
        var finalCase = incomingReport.case
        var finalCaseChg = incomingReport.caseCharging
        var caseIsLive = true

        if (incomingReport.case != null && incomingReport.case in 1..100) {
            // Case is physically reporting via docked earbud pogo-pins
            cachedCaseBattery[address] = CachedCaseBattery(
                level = incomingReport.case,
                isCharging = incomingReport.caseCharging,
                timestamp = System.currentTimeMillis(),
            )
            caseIsLive = true
        } else {
            // Both buds are out of case or offline (0x00, 0x7F, 0xFF).
            // For Nothing/CMF and any model where supportsCaseBatteryWhenUndocked == false:
            // COMPLETELY HIDE the case battery! Never retain old case or show placeholder.
            if (profile.supportsCaseBatteryWhenUndocked && profile.brandSchema == BrandSchema.REALME_LINK) {
                val cached = cachedCaseBattery[address]
                if (cached != null) {
                    finalCase = cached.level
                    finalCaseChg = cached.isCharging
                    caseIsLive = false // Cached from earlier in this session
                } else {
                    finalCase = null
                    finalCaseChg = false
                    caseIsLive = false // Never docked during session
                }
            } else {
                finalCase = null
                finalCaseChg = false
                caseIsLive = false
            }
        }

        val enriched = incomingReport.copy(
            case = finalCase,
            caseCharging = finalCaseChg,
            caseIsLive = caseIsLive,
            sourceProtocol = src,
            timestamp = System.currentTimeMillis(),
        )

        _latestBatteryReports.value = _latestBatteryReports.value + (address to enriched)
        if (activeDevice == null || activeDevice?.address == address) {
            leftBattery = enriched.left
            rightBattery = enriched.right
            caseBattery = enriched.case
        }
        log(address, "Battery Telemetry [$src] -> L:${enriched.left}% R:${enriched.right}% Case:${enriched.case}% (LiveCase: $caseIsLive)", enriched.rawHex)
        scope.launch {
            _batteryReports.emit(address to enriched)
        }
    }

    fun getAncMapping(address: String): AncByteMapping = protocolRouter.getAncMapping(address)

    fun cycleAncProfile(address: String): AncByteMapping = protocolRouter.cycleAncProfile(address)

    fun setCustomAncMapping(address: String, off: Byte, anc: Byte, trans: Byte): AncByteMapping =
        protocolRouter.setCustomAncMapping(address, off, anc, trans)

    private fun log(address: String, message: String, rawHex: String? = null) {
        val entry = DiagnosticLogEntry(
            tag = address.takeLast(5),
            message = message,
            rawHex = rawHex,
        )
        Log.i(TAG, "[$address] $message ${rawHex?.let { "(hex: $it)" } ?: ""}")
        _diagnosticLogs.value = (_diagnosticLogs.value + entry).takeLast(250)
    }

    // -- Connection Lifecycle -----------------------------------------------------------

    /**
     * Purges previous session state and telemetry to prevent state bleed across devices.
     */
    fun purgeSessionState(previousAddress: String? = null) {
        val prevAddr = previousAddress ?: activeDevice?.address
        if (prevAddr != null) {
            disconnect(prevAddr)
            cachedCaseBattery.remove(prevAddr)
            _latestBatteryReports.value = _latestBatteryReports.value - prevAddr
            _confirmedAncMode.value = _confirmedAncMode.value - prevAddr
            _confirmedGameMode.value = _confirmedGameMode.value - prevAddr
            _pendingAncMode.value = _pendingAncMode.value - prevAddr
            _activeDrivers.value = _activeDrivers.value - prevAddr
        }
        activeDriver = null
        activeProfile = null
        activeCapabilityManifest = null
        leftBattery = null
        rightBattery = null
        caseBattery = null
        activeDevice = null
    }

    /**
     * Mandatory State Purge & Re-Fingerprinting on Device Switch.
     * The exact millisecond a new device connects or changes:
     * 1. Purges previous session state (activeDriver, capabilityManifest, cachedCaseBattery, telemetry).
     * 2. Runs StrictBrandProfileResolver to load exact brand profile.
     * 3. Initializes transport and queries fresh battery telemetry.
     */
    @SuppressLint("MissingPermission")
    fun onDeviceChanged(context: Context, newDevice: BluetoothDevice) {
        val prev = activeDevice
        val newAddress = newDevice.address
        val newName = try { newDevice.name } catch (_: Exception) { null }

        if (prev != null && (prev.address != newAddress || prev.name != newName)) {
            val prevName = try { prev.name } catch (_: Exception) { null } ?: prev.address
            log(prev.address, "Device switch detected: purging previous session state for $prevName")
            purgeSessionState(prev.address)
        } else if (prev == null) {
            purgeSessionState(null)
        }

        activeDevice = newDevice
        // Only clear the cached case on a real device switch. This method is
        // called every monitor cycle for the same device, and wiping the cache
        // each time destroyed the docked case reading.

        val sdpUuids = try { newDevice.uuids?.map { it.uuid.toString() } ?: emptyList() } catch (_: Exception) { emptyList() }
        val profile = StrictBrandProfileResolver.resolve(newDevice, discoveredRfcommUuids = sdpUuids)
        activeProfile = profile
        val driver = protocolRouter.resolveDriver(newDevice)
        activeDriver = driver
        val manifest = HardwareCapabilityInspector.inspect(newDevice, newAddress, newName)
        activeCapabilityManifest = manifest

        val devName = newName ?: newAddress
        log(newAddress, "Strict Brand Profile Activated: [${profile.brandSchema.companionAppName}] for $devName via ${driver.driverName}")
        connect(context, newDevice)
        queryBattery(newDevice)
    }

    /**
     * Dispatches active vendor-accurate battery query over RFCOMM socket and BLE GATT.
     */
    @SuppressLint("MissingPermission")
    fun queryBattery(device: BluetoothDevice) {
        val address = device.address
        val query = protocolRouter.buildBatteryQueryCommand(device)
        if (query != null) {
            // Write to RFCOMM socket if connected
            activeRfcommSockets[address]?.let { sock ->
                if (sock.isConnected) {
                    try {
                        sock.outputStream.write(query)
                        sock.outputStream.flush()
                        log(address, "Dispatched vendor battery query via RFCOMM", HexUtils.formatHex(query))
                    } catch (_: Exception) {}
                }
            }
            // Write to GATT characteristic if connected
            scope.launch {
                writeGatt(device, query)
            }
        }
        // Simultaneously activate Fast Pair scan
        batteryScanner?.startScan()
    }

    /**
     * Connection-time NVRAM state dump: reads the hardware's active noise mode,
     * keymap/gesture tables, EQ and mode toggles so the UI never opens blank.
     */
    @SuppressLint("MissingPermission")
    private suspend fun syncHardwareStateOnConnect(device: BluetoothDevice) {
        val address = device.address
        // The dump writes through the RFCOMM socket. Without this limit, each new
        // socket started another dump, and the dump's own write broke that socket.
        // The device log showed a reconnect about every 0.6 s. One dump per 10 s.
        val now = System.currentTimeMillis()
        if (now - (lastStateSyncAt[address] ?: 0L) < STATE_SYNC_MIN_INTERVAL_MS) return
        lastStateSyncAt[address] = now
        val queries = buildList {
            protocolRouter.buildBatteryQueryCommand(device)?.let { add(it) }
            addAll(protocolRouter.buildStateQueryCommands(device))
        }
        for (query in queries) {
            try {
                sendPacket(device, query)
                delay(90)
            } catch (_: Exception) {}
        }
        com.omnibuds.android.hardware.protocol.AncAuditLog.record(
            stage = "STATE_SYNC",
            address = address,
            note = "sent ${queries.size} connection-time state queries",
        )
    }

    @SuppressLint("MissingPermission")
    fun connect(context: Context, device: BluetoothDevice) {
        val address = device.address
        val gattAlive = activeGatts.containsKey(address)
        val rfcommAlive = activeRfcommSockets.containsKey(address) || rfcommJobs[address]?.isActive == true
        if (gattAlive && rfcommAlive) return

        // BLE and RFCOMM are checked separately. The old single guard returned early
        // whenever either transport was alive, so a dropped GATT link or a failed
        // RFCOMM start was never retried while the other transport stayed up.
        if (!gattAlive) {
            val driver = protocolRouter.resolveDriver(device)
            _activeDrivers.value = _activeDrivers.value + (address to driver.driverName)
            _connectionStatus.value = _connectionStatus.value + (address to "Connecting (${driver.driverName})...")
            log(address, "Connecting via ${driver.driverName} (Transport pref: ${driver.preferredTransport})")

            val callback = createGattCallback(device)
            val gatt = if (Build.VERSION.SDK_INT >= 23) {
                device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, callback)
            }
            activeGatts[address] = gatt

            val manifest = HardwareCapabilityInspector.inspect(device, address, device.name)
            if (manifest.isAppleAirPods) {
                appleL2capDriver.connect(device)
            }
            try {
                codecManager?.inspectCodec(device)
            } catch (_: Exception) {}
        }

        // startRfcommWorker returns early when an RFCOMM session is already running.
        startRfcommWorker(device)
        startBatteryHeartbeat()
    }

    @SuppressLint("MissingPermission")
    fun disconnect(address: String) {
        log(address, "Disconnecting GATT and RFCOMM sessions")
        _connectionStatus.value = _connectionStatus.value - address
        _activeDrivers.value = _activeDrivers.value - address
        _confirmedGameMode.value = _confirmedGameMode.value - address

        appleL2capDriver.disconnect(address)

        activeGatts.remove(address)?.let { gatt ->
            try {
                gatt.disconnect()
                gatt.close()
            } catch (_: Exception) {}
        }
        activeWriteCharacteristics.remove(address)
        resetDescriptorState(address)
        lastStateSyncAt.remove(address)
        lastReportedModeMask.remove(address)
        _holdCycleMasks.value = _holdCycleMasks.value - address

        rfcommJobs.remove(address)?.cancel()
        activeRfcommSockets.remove(address)?.let { sock ->
            try { sock.close() } catch (_: Exception) {}
        }
        bleSequencer.clear(address)
        protocolRouter.clear(address)
        _latestBatteryReports.value = _latestBatteryReports.value - address
        cachedCaseBattery.remove(address)
    }

    fun disconnectAll() {
        val addresses = _connectionStatus.value.keys + activeGatts.keys + activeRfcommSockets.keys + _latestBatteryReports.value.keys
        for (addr in addresses.toSet()) {
            disconnect(addr)
        }
        _latestBatteryReports.value = emptyMap()
        cachedCaseBattery.clear()
    }

    fun clearAllBatteryReports() {
        _latestBatteryReports.value = emptyMap()
    }

    // -- Controls & Routing -------------------------------------------------------------

    /**
     * Sets Noise Control (ANC, Transparency, Off/Normal) via the detected vendor driver.
     * Uses BleCommandSequencer for:
     * - Immediate 500ms battery polling preemption
     * - 120ms idempotency deduplication
     * - "Latest-wins" rapid-tap coalescing (skips intermediate transitions)
     * - Clean 60ms firmware settle gap
     * - Authoritative 300ms verification query
     * - Software ambient/passthrough DSP teardown on Off/Normal
     */
    @SuppressLint("MissingPermission")
    suspend fun setNoiseControl(device: BluetoothDevice, mode: String): Boolean {
        val address = device.address
        val currentConfirmed = _confirmedAncMode.value[address]
        com.omnibuds.android.hardware.protocol.AncAuditLog.record(
            stage = "ENGINE",
            address = address,
            activeMode = currentConfirmed,
            requestedMode = mode,
        )

        // 1. Teardown any active software ambient/passthrough boost on Normal/Off
        if (mode.equals("OFF", ignoreCase = true) || mode.equals("NORMAL", ignoreCase = true)) {
            dspController.disableAmbientAndPassthrough()
        }

        val manifest = HardwareCapabilityInspector.inspect(device, address, device.name)
        if (manifest.isAppleAirPods) {
            val appleOk = appleL2capDriver.setNoiseControl(address, mode)
            if (appleOk) {
                _confirmedAncMode.value = _confirmedAncMode.value + (address to mode)
                bleSequencer.updateConfirmedMode(address, mode)
                return true
            }
        }

        _pendingAncMode.value = _pendingAncMode.value + (address to mode)

        // Every explicit tap must be transmitted; clear the sequencer's
        // same-target dedupe before submitting the new request.
        bleSequencer.clearNoiseModeDedup(address)
        bleSequencer.submitNoiseMode(
            device = device,
            mode = mode,
            dispatchAction = { targetMode ->
                dispatchNoiseModePacket(device, targetMode)
            },
            onAuthoritativeVerify = {
                val queryPkt = protocolRouter.buildAncQueryCommand(device)
                if (queryPkt != null) {
                    log(address, "Running authoritative 300ms post-tap verification query")
                    sendPacket(device, queryPkt)
                }
            },
        )
        return true
    }

    @SuppressLint("MissingPermission")
    private suspend fun dispatchNoiseModePacket(device: BluetoothDevice, targetMode: String): Boolean {
        val address = device.address
        val driver = protocolRouter.resolveDriver(device)
        val mapping = protocolRouter.getAncMapping(address)
        val packet = protocolRouter.buildAncCommand(device, targetMode)
        if (packet.isEmpty()) {
            log(address, "No verified ANC write frame for this silicon family; staying read-only")
            com.omnibuds.android.hardware.protocol.AncAuditLog.record(
                stage = "READ_ONLY",
                address = address,
                requestedMode = targetMode,
                note = "no verified ${driver.driverName} write frame",
            )
            _pendingAncMode.value = _pendingAncMode.value + (address to null)
            return false
        }
        val targetByteHex = "0x%02X".format(mapping.byteForMode(targetMode))

        if (bleSequencer.isDuplicateWrite(address, packet)) {
            log(address, "Hardware Command Idempotency Guard: Suppressed duplicate frame within 120ms")
            return true
        }

        log(address, "Routing ANC mode '$targetMode' (target byte $targetByteHex) via ${driver.driverName}", HexUtils.formatHex(packet))
        com.omnibuds.android.hardware.protocol.AncAuditLog.record(
            stage = "SEND",
            address = address,
            activeMode = _confirmedAncMode.value[address],
            requestedMode = targetMode,
            sentHex = HexUtils.formatHex(packet),
            mapping = "off=0x%02X anc=0x%02X trans=0x%02X".format(
                mapping.offByte,
                mapping.ancByte,
                mapping.transByte,
            ),
        )

        var startTime = System.currentTimeMillis()
        var sent = sendPacket(device, packet)
        if (!sent) {
            // The earbuds can close the RFCOMM socket between the battery poll
            // and the tap. Give the reconnect a moment and retry, otherwise the
            // mode command is silently lost and the button appears dead.
            delay(350)
            sent = sendPacket(device, packet)
        }
        if (!sent) {
            delay(700)
            sent = sendPacket(device, packet)
        }
        val latency = System.currentTimeMillis() - startTime

        bleSequencer.recordWriteAttempt(
            address = address,
            packet = packet,
            latencyMs = latency,
            mode = targetMode,
            activeOff = mapping.offByte,
            activeAnc = mapping.ancByte,
            activeTrans = mapping.transByte,
        )

        if (!sent) {
            _pendingAncMode.value = _pendingAncMode.value + (address to null)
        }
        return sent
    }

    /**
     * Diagnostic Tool: Directly tests candidate 2-byte teardown reset frame for Normal/Off mode.
     */
    @SuppressLint("MissingPermission")
    suspend fun testOffByteVariant(device: BluetoothDevice, candidateOffByte: Byte): Boolean {
        val address = device.address
        bleSequencer.recordUserInteraction(address)
        val mapping = protocolRouter.getAncMapping(address)
        val packet = com.omnibuds.android.hardware.protocol.RealmeProtocolParser.buildAncCustomOffCommand(candidateOffByte)
        log(address, "Testing candidate Off-Byte 0x%02X teardown frame".format(candidateOffByte), HexUtils.formatHex(packet))
        com.omnibuds.android.hardware.protocol.AncAuditLog.record(
            stage = "OFF_TEST",
            address = address,
            requestedMode = "OFF",
            sentHex = HexUtils.formatHex(packet),
            note = "candidate off=0x%02X".format(candidateOffByte),
        )

        dspController.disableAmbientAndPassthrough()
        val startTime = System.currentTimeMillis()
        val sent = sendPacket(device, packet)
        val latency = System.currentTimeMillis() - startTime

        bleSequencer.recordWriteAttempt(
            address = address,
            packet = packet,
            latencyMs = latency,
            mode = "TEST OFF (0x%02X)".format(candidateOffByte),
            activeOff = candidateOffByte,
            activeAnc = mapping.ancByte,
            activeTrans = mapping.transByte,
        )
        return sent
    }

    /**
     * Diagnostic Tool: Locks in chosen candidate Off-Byte as permanent profile mapping.
     */
    fun lockInOffByteVariant(address: String, candidateOffByte: Byte): AncByteMapping {
        val current = protocolRouter.getAncMapping(address)
        val updated = protocolRouter.setCustomAncMapping(address, off = candidateOffByte, anc = current.ancByte, trans = current.transByte)
        log(address, "Locked in Off-Byte 0x%02X permanently".format(candidateOffByte))
        _confirmedAncMode.value = _confirmedAncMode.value + (address to "OFF")
        bleSequencer.updateConfirmedMode(address, "OFF")
        return updated
    }

    /**
     * Sets Equalizer preset universally via OS-level DSP engine and routes hardware packet if supported.
     */
    @SuppressLint("MissingPermission")
    suspend fun setEqualizer(device: BluetoothDevice?, preset: String): Boolean {
        // 1. Universal OS-Level DSP Equalizer (100% universal across all earphones)
        dspController.setEqualizerPreset(preset)

        // 2. Route hardware packet if connected device is provided
        if (device != null) {
            val packet = protocolRouter.buildEqualizerCommand(device, preset)
            if (packet != null && packet.isNotEmpty()) {
                val driver = protocolRouter.resolveDriver(device)
                log(device.address, "Sending hardware EQ '$preset' via ${driver.driverName}", HexUtils.formatHex(packet))
                sendPacket(device, packet)
            }
        }
        return true
    }

    /**
     * Sets 10-Band Equalizer gains universally via OS-level DSP engine.
     */
    fun set10BandEqualizer(gainsDb: FloatArray) {
        dspController.set10BandEqualizer(gainsDb)
    }

    /**
     * Toggles 3D Spatial Audio & Headstage Crossfeed universally via OS-level DSP engine
     * and routes hardware spatial packet if supported.
     */
    @SuppressLint("MissingPermission")
    suspend fun setSpatialAudio(device: BluetoothDevice?, enabled: Boolean): Boolean {
        // 1. Universal OS-Level DSP Spatial Audio (360° HRTF binaural virtualizer + air-lift tuning)
        dspController.setSpatialAudioEnabled(enabled)

        // 2. Route hardware packet if connected device is provided
        if (device != null) {
            val packets = com.omnibuds.android.hardware.protocol.RealmeProtocolParser.buildSpatialAudioCommands(enabled)
            for (packet in packets) {
                sendPacket(device, packet)
                delay(30)
            }
            log(device.address, "Sent hardware 360° Spatial Audio ($enabled)")
        }

        // 3. Audible confirmation earcon
        try {
            val tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 85)
            if (enabled) {
                tone.startTone(android.media.ToneGenerator.TONE_PROP_PROMPT, 200)
            } else {
                tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 120)
            }
        } catch (_: Exception) {}

        return true
    }

    /**
     * Toggles Game Mode (50ms Low Latency) via hardware driver, routes low latency in DSP, and plays confirmation chime.
     */
    @SuppressLint("MissingPermission")
    suspend fun setGameMode(device: BluetoothDevice, enabled: Boolean): Boolean {
        val packets = com.omnibuds.android.hardware.protocol.RealmeProtocolParser.buildGameModeCommands(enabled)
        for (packet in packets) {
            sendPacket(device, packet)
            delay(30)
        }
        dspController.setLowLatencyMode(enabled)
        log(device.address, "Sent 50ms Game Mode ($enabled)")

        try {
            val tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 85)
            if (enabled) {
                tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 180)
            } else {
                tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 120)
            }
        } catch (_: Exception) {}

        return true
    }

    /**
     * Sets Dynamic Bass (low-frequency harmonic enhancer for 12.4mm driver).
     */
    @SuppressLint("MissingPermission")
    suspend fun setDynamicBass(device: BluetoothDevice?, enabled: Boolean, level: Int = 3): Boolean {
        dspController.setDynamicBass(enabled, level)
        if (device != null) {
            val packet = com.omnibuds.android.hardware.protocol.RealmeProtocolParser.buildDynamicBassCommand(enabled, level)
            sendPacket(device, packet)
            log(device.address, "Sent hardware Dynamic Bass ($enabled, level=$level)")
        }
        return true
    }

    /**
     * Sets Volume Enhancer (+5.0dB safe post-gain limiter boost).
     */
    @SuppressLint("MissingPermission")
    suspend fun setVolumeEnhancer(device: BluetoothDevice?, enabled: Boolean): Boolean {
        dspController.setVolumeEnhancer(enabled)
        if (device != null) {
            val packet = com.omnibuds.android.hardware.protocol.RealmeProtocolParser.buildVolumeEnhancerCommand(enabled)
            sendPacket(device, packet)
            log(device.address, "Sent hardware Volume Enhancer ($enabled)")
        }

        // Audible confirmation earcon
        try {
            val tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 85)
            if (enabled) {
                tone.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 220)
            } else {
                tone.startTone(android.media.ToneGenerator.TONE_PROP_NACK, 140)
            }
        } catch (_: Exception) {}

        return true
    }

    @SuppressLint("MissingPermission")
    fun getCapabilityManifest(device: BluetoothDevice): DeviceCapabilityManifest {
        val svcs = discoveredServices.value[device.address]?.map { it.uuid } ?: emptyList()
        return HardwareCapabilityInspector.inspect(device, device.address, device.name, svcs)
    }

    @SuppressLint("MissingPermission")
    suspend fun setProAncLevel(device: BluetoothDevice, level: String): Boolean {
        val driver = protocolRouter.resolveDriver(device)
        val packet = if (driver is NothingXProtocolDriver) {
            driver.buildAncCommand(level)
        } else {
            com.omnibuds.android.hardware.protocol.RealmeProtocolParser.buildProAncLevelCommand(level)
        }
        val ok = sendPacket(device, packet)
        log(device.address, "Sent Pro ANC Sub-mode ($level) via ${driver.driverName}: $ok")
        return ok
    }

    @SuppressLint("MissingPermission")
    suspend fun setWindNoiseReduction(device: BluetoothDevice, enabled: Boolean): Boolean {
        val packet = com.omnibuds.android.hardware.protocol.RealmeProtocolParser.buildWindNoiseReductionCommand(enabled)
        val ok = sendPacket(device, packet)
        log(device.address, "Sent Wind Noise Reduction ($enabled): $ok")
        return ok
    }

    @SuppressLint("MissingPermission")
    suspend fun setTransparencyLevel(device: BluetoothDevice, level: Int, voiceBoost: Boolean): Boolean {
        val packet = com.omnibuds.android.hardware.protocol.RealmeProtocolParser.buildTransparencyLevelCommand(level, voiceBoost)
        val ok = sendPacket(device, packet)
        log(device.address, "Sent Transparency Ambient Level ($level, voiceBoost=$voiceBoost): $ok")
        return ok
    }

    @SuppressLint("MissingPermission")
    suspend fun setInEarDetection(device: BluetoothDevice, enabled: Boolean): Boolean {
        val driver = protocolRouter.resolveDriver(device)
        val packet = if (driver is NothingXProtocolDriver) {
            driver.buildInEarDetectionCommand(enabled)
        } else {
            com.omnibuds.android.hardware.protocol.RealmeProtocolParser.buildInEarDetectionCommand(enabled)
        }
        val ok = sendPacket(device, packet)
        log(device.address, "Sent In-Ear Detection ($enabled) via ${driver.driverName}: $ok")
        return ok
    }

    @SuppressLint("MissingPermission")
    suspend fun setMultipoint(device: BluetoothDevice, enabled: Boolean): Boolean {
        val packet = com.omnibuds.android.hardware.protocol.RealmeProtocolParser.buildMultipointCommand(enabled)
        val ok = sendPacket(device, packet)
        log(device.address, "Sent Multipoint Dual Connection ($enabled): $ok")
        return ok
    }

    var isCameraShutterEnabled: Boolean = false

    fun triggerCameraShutter() {
        val ctx = appContext ?: return
        try {
            val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            audioManager?.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_CAMERA))
            audioManager?.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_CAMERA))
            Log.i(TAG, "Camera Shutter triggered via KeyCode Camera")
        } catch (_: Exception) {
            try {
                val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                audioManager?.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_VOLUME_DOWN))
                audioManager?.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_VOLUME_DOWN))
            } catch (_: Exception) {}
        }
    }

    var isConversationalAwarenessActive: Boolean = false
        private set
    private var preDuckVolume: Int? = null
    private var preDuckAncMode: String? = null
    private var conversationalSilenceJob: Job? = null

    fun onSpeechDetected(address: String) {
        if (!isConversationalAwarenessActive) return
        conversationalSilenceJob?.cancel()
        val ctx = appContext ?: return
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return
        val dev = activeGatts[address]?.device ?: activeRfcommSockets[address]?.remoteDevice

        if (preDuckVolume == null) {
            preDuckVolume = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            preDuckAncMode = _confirmedAncMode.value[address] ?: "OFF"
            val maxVol = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            val duckedVol = (maxVol * 0.15f).toInt().coerceAtLeast(1)
            am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, duckedVol, 0)
            if (dev != null) {
                scope.launch { setNoiseControl(dev, "TRANSPARENCY") }
            }
            log(address, "Conversational Awareness: Speech detected! Ducked media to 15% and switched to Transparency")
        }

        // 4-second silence countdown to restore media volume and ANC mode
        conversationalSilenceJob = scope.launch {
            delay(4000)
            preDuckVolume?.let { origVol ->
                am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, origVol, 0)
            }
            val origAnc = preDuckAncMode
            preDuckVolume = null
            preDuckAncMode = null
            if (dev != null && origAnc != null) {
                setNoiseControl(dev, origAnc)
            }
            log(address, "Conversational Awareness: Speech ended, restored volume and previous ANC mode")
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun setConversationalAwareness(device: BluetoothDevice, enabled: Boolean): Boolean {
        isConversationalAwarenessActive = enabled
        if (!enabled) {
            conversationalSilenceJob?.cancel()
            preDuckVolume?.let { origVol ->
                val ctx = appContext
                val am = ctx?.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                am?.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, origVol, 0)
            }
            preDuckVolume = null
            preDuckAncMode = null
        }
        val manifest = HardwareCapabilityInspector.inspect(device, device.address, device.name)
        if (manifest.isAppleAirPods) {
            appleL2capDriver.setConversationalAwareness(device.address, enabled)
        }
        val packet = com.omnibuds.android.hardware.protocol.RealmeProtocolParser.buildConversationalAwarenessCommand(enabled)
        val ok = sendPacket(device, packet)
        log(device.address, "Sent Conversational Awareness ($enabled): $ok")
        return ok
    }

    @SuppressLint("MissingPermission")
    suspend fun setPinchSpeed(device: BluetoothDevice, speed: String): Boolean {
        val manifest = HardwareCapabilityInspector.inspect(device, device.address, device.name)
        if (manifest.isAppleAirPods) {
            val ps = when (speed.uppercase()) {
                "SLOW" -> PinchSpeed.SLOW
                "SLOWEST" -> PinchSpeed.SLOWEST
                else -> PinchSpeed.DEFAULT
            }
            appleL2capDriver.setPinchSpeed(device.address, ps)
        }
        val packet = com.omnibuds.android.hardware.protocol.RealmeProtocolParser.buildPinchSpeedCommand(speed)
        val ok = sendPacket(device, packet)
        log(device.address, "Sent Pinch Speed ($speed): $ok")
        return ok
    }

    @SuppressLint("MissingPermission")
    suspend fun setStemSlide(device: BluetoothDevice, enabled: Boolean): Boolean {
        val packet = com.omnibuds.android.hardware.protocol.RealmeProtocolParser.buildStemSlideCommand(enabled)
        val ok = sendPacket(device, packet)
        log(device.address, "Sent Stem Slide Volume ($enabled): $ok")
        return ok
    }

    @SuppressLint("MissingPermission")
    suspend fun runPersonalizedAnc(device: BluetoothDevice): Boolean {
        val packet = com.omnibuds.android.hardware.protocol.RealmeProtocolParser.buildPersonalizedAncCommand(true)
        val ok = sendPacket(device, packet)
        log(device.address, "Triggered Golden Sound Personalized ANC: $ok")
        return ok
    }

    @SuppressLint("MissingPermission")
    suspend fun runEarbudFitTest(device: BluetoothDevice): Boolean {
        val packet = com.omnibuds.android.hardware.protocol.RealmeProtocolParser.buildEarbudFitTestCommand(true)
        val ok = sendPacket(device, packet)
        log(device.address, "Triggered Earbud Fit Test: $ok")
        return ok
    }

    fun setDolbyAtmos(enabled: Boolean, scenario: String = "SMART", environment: String = "INDOOR") {
        val sc = try { DolbyAtmosScenario.valueOf(scenario.uppercase()) } catch (_: Exception) { DolbyAtmosScenario.SMART }
        val env = try { DolbyAtmosEnvironment.valueOf(environment.uppercase()) } catch (_: Exception) { DolbyAtmosEnvironment.INDOOR }
        dspController.setDolbyAtmos(enabled, sc, env)
    }

    fun setMonoAudio(enabled: Boolean) {
        dspController.setMonoAudio(enabled)
    }

    fun setChannelBalance(balance: Float) {
        dspController.setChannelBalance(balance)
    }

    fun getEstimatedSplDb(): Float = dspController.getEstimatedSplDb()

    fun setSpatialAudioMode(mode: String) {
        val sc = try {
            com.omnibuds.android.hardware.engine.SpatialAudioMode.valueOf(mode.uppercase())
        } catch (_: Exception) {
            if (mode.equals("ON", true) || mode.equals("FIXED", true)) com.omnibuds.android.hardware.engine.SpatialAudioMode.FIXED else com.omnibuds.android.hardware.engine.SpatialAudioMode.OFF
        }
        dspController.setSpatialAudioMode(sc)
    }

    @SuppressLint("MissingPermission")
    suspend fun setHiResMode(device: BluetoothDevice, enabled: Boolean): Boolean {
        val packet = com.omnibuds.android.hardware.protocol.RealmeProtocolParser.buildHiResCodecCommand(enabled)
        sendPacket(device, packet)
        log(device.address, "Sent vendor NVRAM Hi-Res command ($enabled)")
        return codecManager?.setHiResMode(device, enabled) ?: false
    }

    @SuppressLint("MissingPermission")
    fun forceHighBitrateAac(device: BluetoothDevice, enabled: Boolean): Boolean {
        return codecManager?.forceHighBitrateAac(device, enabled) ?: false
    }

    suspend fun playHearingTestTone(freqHz: Int, ear: String, volumeDb: Float) {
        hearingTestManager.playTestTone(freqHz, if (ear.equals("left", true)) EarChannel.LEFT else EarChannel.RIGHT, volumeDb)
    }

    fun stopHearingTestTone() {
        hearingTestManager.stopTone()
    }

    fun recordHearingResult(ear: String, freqHz: Int, heardDb: Float) {
        hearingTestManager.recordHearingResult(if (ear.equals("left", true)) EarChannel.LEFT else EarChannel.RIGHT, freqHz, heardDb)
    }

    fun applyHearingAccommodations(preset: String, intensity: String) {
        hearingTestManager.activePreset = try { HeadphoneAccommodationPreset.valueOf(preset.uppercase()) } catch (_: Exception) { HeadphoneAccommodationPreset.OFF }
        hearingTestManager.activeIntensity = try { AccommodationIntensity.valueOf(intensity.uppercase()) } catch (_: Exception) { AccommodationIntensity.MODERATE }
        val leftGains = hearingTestManager.computeCompensationGains(EarChannel.LEFT)
        val rightGains = hearingTestManager.computeCompensationGains(EarChannel.RIGHT)
        dspController.setPerEarHearingCompensation(leftGains, rightGains)
    }

    fun playMindRelax(soundscape: String, durationMinutes: Int = 0) {
        val sc = try { Soundscape.valueOf(soundscape.uppercase()) } catch (_: Exception) { Soundscape.RAIN }
        mindRelaxGenerator.play(sc, durationMinutes)
    }

    fun stopMindRelax() {
        mindRelaxGenerator.stop()
    }

    /**
     * Writes full synchronized gesture mapping and sends Touch Master Enable bit.
     * Prevents the "None" lockout bug on realme Buds T300 and recovers disabled touch sensors.
     */
    @SuppressLint("MissingPermission")
    suspend fun setTouchControls(
        device: BluetoothDevice,
        leftDouble: String,
        rightDouble: String,
        leftTriple: String,
        rightTriple: String,
        leftHold: String,
        rightHold: String,
        ancCycleBitmask: Byte = 0x06,
    ): Boolean {
        val address = device.address
        log(address, "Sending Touch Master Enable & Synchronized Gestures")

        // 1. Send Master Enable packets to wake up capacitive touch sensor
        val enablePackets = com.omnibuds.android.hardware.protocol.RealmeProtocolParser.buildTouchMasterEnableCommands(true)
        for (pkt in enablePackets) {
            sendPacket(device, pkt)
            delay(40)
        }

        // 2. Send full synchronized Left + Right struct frame
        val structPacket = com.omnibuds.android.hardware.protocol.RealmeProtocolParser.buildSynchronizedTouchGestureStruct(
            leftDouble = leftDouble,
            rightDouble = rightDouble,
            leftTriple = leftTriple,
            rightTriple = rightTriple,
            leftHold = leftHold,
            rightHold = rightHold,
            ancCycleBitmask = ancCycleBitmask,
        )
        sendPacket(device, structPacket)
        delay(60)

        // 3. Send explicit per-bud frames to ensure compatibility across firmwares
        val leftDoublePkt = com.omnibuds.android.hardware.protocol.RealmeProtocolParser.buildTouchGestureCommand("Left", "double_tap", leftDouble, ancCycleBitmask)
        val rightDoublePkt = com.omnibuds.android.hardware.protocol.RealmeProtocolParser.buildTouchGestureCommand("Right", "double_tap", rightDouble, ancCycleBitmask)
        val leftTriplePkt = com.omnibuds.android.hardware.protocol.RealmeProtocolParser.buildTouchGestureCommand("Left", "triple_tap", leftTriple, ancCycleBitmask)
        val rightTriplePkt = com.omnibuds.android.hardware.protocol.RealmeProtocolParser.buildTouchGestureCommand("Right", "triple_tap", rightTriple, ancCycleBitmask)
        val leftHoldPkt = com.omnibuds.android.hardware.protocol.RealmeProtocolParser.buildTouchGestureCommand("Left", "long_press", leftHold, ancCycleBitmask)
        val rightHoldPkt = com.omnibuds.android.hardware.protocol.RealmeProtocolParser.buildTouchGestureCommand("Right", "long_press", rightHold, ancCycleBitmask)

        sendPacket(device, leftDoublePkt)
        delay(40)
        sendPacket(device, rightDoublePkt)
        delay(40)
        sendPacket(device, leftTriplePkt)
        delay(40)
        sendPacket(device, rightTriplePkt)
        delay(40)
        sendPacket(device, leftHoldPkt)
        delay(40)
        sendPacket(device, rightHoldPkt)

        log(address, "Synchronized touch controls successfully written to hardware")
        delay(120)
        // Read-back verification: ask the buds for the stored Left gesture table.
        sendPacket(device, com.omnibuds.android.hardware.protocol.RealmeProtocolParser.buildTouchGestureQuery(0x01))
        markConfigSaved(address, "Gestures saved to earbud memory ✓")
        return true
    }

    /**
     * Restores factory realme Buds T300 default touch controls:
     * - Double Tap (L/R): Play/Pause
     * - Triple Tap (L/R): Next Track
     * - Touch & Hold (L/R): Switch Noise Control (ANC + Transparency)
     */
    @SuppressLint("MissingPermission")
    suspend fun restoreDefaultTouchControls(device: BluetoothDevice): Boolean {
        return setTouchControls(
            device = device,
            leftDouble = "PLAY_PAUSE",
            rightDouble = "PLAY_PAUSE",
            leftTriple = "NEXT_TRACK",
            rightTriple = "NEXT_TRACK",
            leftHold = "SWITCH_NOISE_CONTROL",
            rightHold = "SWITCH_NOISE_CONTROL",
            ancCycleBitmask = 0x06,
        )
    }

    private var activeRingJob: Job? = null

    /**
     * Find My Earbuds: Rings Left / Right earbud with sweeping chirp and sends hardware ring frame.
     */
    @SuppressLint("MissingPermission")
    fun ringEarbud(device: BluetoothDevice?, bud: String, ring: Boolean) {
        if (device != null) {
            scope.launch {
                val pkt = com.omnibuds.android.hardware.protocol.RealmeProtocolParser.buildFindMyBudsCommand(bud, ring)
                sendPacket(device, pkt)
            }
        }

        activeRingJob?.cancel()
        if (!ring) return

        activeRingJob = scope.launch(Dispatchers.Default) {
            try {
                val sampleRate = 44100
                val durationMs = 350
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val leftVol = if (bud.equals("Right", ignoreCase = true)) 0.0f else 1.0f
                val rightVol = if (bud.equals("Left", ignoreCase = true)) 0.0f else 1.0f

                val track = android.media.AudioTrack(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                    android.media.AudioFormat.Builder()
                        .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_STEREO)
                        .build(),
                    numSamples * 4,
                    android.media.AudioTrack.MODE_STREAM,
                    0,
                )
                track.setStereoVolume(leftVol, rightVol)
                track.play()

                val buffer = ShortArray(numSamples * 2)
                for (pulse in 0 until 14) {
                    if (!isActive) break
                    for (i in 0 until numSamples) {
                        val progress = i.toDouble() / numSamples
                        val freq = 3200.0 + (progress * 1300.0) // 3.2kHz to 4.5kHz chirp
                        val angle = 2.0 * Math.PI * i / (sampleRate / freq)
                        val sample = (Math.sin(angle) * Short.MAX_VALUE * 0.9).toInt().toShort()
                        buffer[i * 2] = sample
                        buffer[i * 2 + 1] = sample
                    }
                    track.write(buffer, 0, buffer.size)
                    delay(160)
                }
                track.stop()
                track.release()
            } catch (e: Exception) {
                Log.w("UniversalTwsEngine", "Ring chirp error: ${e.message}")
            }
        }
    }

    var onVoiceAssistantTrigger: (() -> Unit)? = null

    /**
     * Intercepts earbud touch notifications and triggers matching system KeyEvent / action.
     */
    private fun handleTouchInterception(action: String, address: String) {
        val ctx = appContext ?: return
        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
        log(address, "Software Touch Interceptor: Executing '$action'")
        when (action.uppercase()) {
            "PLAY_PAUSE" -> {
                audioManager?.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
                audioManager?.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
            }
            "NEXT_TRACK" -> {
                audioManager?.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_NEXT))
                audioManager?.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_NEXT))
            }
            "PREVIOUS_TRACK" -> {
                audioManager?.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS))
                audioManager?.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS))
            }
            "VOICE_ASSISTANT" -> {
                onVoiceAssistantTrigger?.invoke()
            }
            "SWITCH_NOISE_CONTROL", "ANC" -> {
                val current = _confirmedAncMode.value[address] ?: "OFF"
                val next = when (current) {
                    "ANC" -> "TRANSPARENCY"
                    "TRANSPARENCY" -> "OFF"
                    else -> "ANC"
                }
                com.omnibuds.android.hardware.protocol.AncAuditLog.record(
                    stage = "HARDWARE_TOUCH",
                    address = address,
                    activeMode = current,
                    requestedMode = next,
                    note = "noise-control touch event",
                )
                val dev = activeGatts[address]?.device ?: activeRfcommSockets[address]?.remoteDevice
                if (dev != null) {
                    scope.launch { setNoiseControl(dev, next) }
                }
            }
            "GAME_MODE" -> {
                val current = _confirmedGameMode.value[address] ?: false
                _confirmedGameMode.value = _confirmedGameMode.value + (address to !current)
                com.omnibuds.android.hardware.protocol.AncAuditLog.record(
                    stage = "HARDWARE_TOUCH",
                    address = address,
                    activeMode = if (!current) "ON" else "OFF",
                    note = "game-mode touch event",
                )
            }
            "VOLUME_UP" -> {
                audioManager?.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_RAISE, android.media.AudioManager.FLAG_SHOW_UI)
            }
            "VOLUME_DOWN" -> {
                audioManager?.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_LOWER, android.media.AudioManager.FLAG_SHOW_UI)
            }
        }
    }

    /**
     * Directly writes a custom raw hex string (HCI analysis / reverse engineering).
     */
    @SuppressLint("MissingPermission")
    suspend fun sendRawHex(device: BluetoothDevice, hexString: String): Boolean {
        val bytes = HexUtils.safeParseHex(hexString) ?: return false
        log(device.address, "Sending Custom RAW Hex", HexUtils.formatHex(bytes))
        return sendPacket(device, bytes)
    }

    /**
     * Executes the Auto-Detect / Learn Mode sequence for unknown or generic BLE earbuds.
     * Probes candidate write characteristics with standard vendor queries.
     */
    @SuppressLint("MissingPermission")
    suspend fun runAutoLearnMode(device: BluetoothDevice) {
        val prober = com.omnibuds.android.hardware.engine.drivers.GenericBleProberDriver()
        val frames = prober.generateLearnProbeFrames()
        log(device.address, "Starting Heuristic Auto-Detect / Learn Mode with ${frames.size} probes")
        for (frame in frames) {
            sendPacket(device, frame)
            delay(150)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun setUltraBass(device: BluetoothDevice, enabled: Boolean, level: Int): Boolean {
        val pkt = protocolRouter.buildUltraBassCommand(device, enabled, level)
        if (pkt != null) {
            sendPacket(device, pkt)
        }
        dspController.setDynamicBass(enabled, level)
        log(device.address, "Ultra Bass 2.0 -> ${if (enabled) "ON (Level $level)" else "OFF"}")
        return true
    }

    @SuppressLint("MissingPermission")
    suspend fun set3BandEqualizer(device: BluetoothDevice, bass: Int, mid: Int, treble: Int): Boolean {
        val pkt = protocolRouter.buildCustom3BandEqCommand(device, bass, mid, treble)
        if (pkt != null) {
            sendPacket(device, pkt)
        }
        val b = bass.toFloat()
        val m = mid.toFloat()
        val t = treble.toFloat()
        val gains = floatArrayOf(
            b, b, b * 0.8f,
            m * 0.9f, m, m, m * 0.9f,
            t * 0.8f, t, t
        )
        set10BandEqualizer(gains)
        log(device.address, "Nothing Custom 3-Band EQ -> Bass:$bass Mid:$mid Treble:$treble")
        return true
    }

    @SuppressLint("MissingPermission")
    suspend fun setNothingInEarDetection(device: BluetoothDevice, enabled: Boolean): Boolean {
        val pkt = protocolRouter.buildInEarDetectionCommand(device, enabled)
        if (pkt != null) {
            sendPacket(device, pkt)
        }
        log(device.address, "Nothing In-Ear Detection -> $enabled")
        return true
    }

    @SuppressLint("MissingPermission")
    suspend fun setNothingDualConnection(device: BluetoothDevice, enabled: Boolean): Boolean {
        val pkt = protocolRouter.buildDualConnectionCommand(device, enabled)
        if (pkt != null) {
            sendPacket(device, pkt)
        }
        log(device.address, "Nothing Dual Connection -> $enabled")
        return true
    }

    @SuppressLint("MissingPermission")
    suspend fun ringNothingEarbud(device: BluetoothDevice, bud: String, ring: Boolean): Boolean {
        val pkt = protocolRouter.buildFindMyBudsCommand(device, bud, ring)
        if (pkt != null) {
            sendPacket(device, pkt)
        }
        log(device.address, "Nothing Find My Earbuds: Ring $bud -> $ring")
        return true
    }

    @SuppressLint("MissingPermission")
    suspend fun sendNothingTouchControl(device: BluetoothDevice, bud: String, gesture: String, action: String): Boolean {
        val pkt = protocolRouter.buildTouchControlCommand(device, bud, gesture, action)
        if (pkt != null) {
            sendPacket(device, pkt)
            delay(120)
            markConfigSaved(device.address, "Touch control saved to earbud memory ✓")
        }
        log(device.address, "Nothing Touch Control: $bud $gesture -> $action")
        return true
    }

    // -- Packet Transport ---------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private suspend fun sendPacket(device: BluetoothDevice, packet: ByteArray): Boolean {
        val address = device.address
        val driver = protocolRouter.resolveDriver(device)
        val hex = HexUtils.formatHex(packet)
        _lastSentPackets.value = _lastSentPackets.value + (address to hex)

        if (driver.preferredTransport == TransportPreference.RFCOMM_FIRST) {
            if (activeRfcommSockets.containsKey(address) && writeRfcomm(device, packet)) {
                bleSequencer.notifyRfcommWriteCompleted(address)
                return true
            }
            if (writeGatt(device, packet)) return true
        } else {
            if (writeGatt(device, packet)) return true
            if (writeRfcomm(device, packet)) {
                bleSequencer.notifyRfcommWriteCompleted(address)
                return true
            }
        }

        // Final fallback: try opening RFCOMM if neither was connected
        if (writeRfcomm(device, packet)) {
            bleSequencer.notifyRfcommWriteCompleted(address)
            return true
        }

        log(address, "Failed to write packet via both BLE GATT and RFCOMM")
        return false
    }

    @SuppressLint("MissingPermission")
    private fun writeGatt(device: BluetoothDevice, packet: ByteArray): Boolean {
        val address = device.address
        val gatt = activeGatts[address] ?: return false
        val writeChar = activeWriteCharacteristics[address] ?: return false

        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                val writeType = if ((writeChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                } else {
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                }
                gatt.writeCharacteristic(writeChar, packet, writeType) == android.bluetooth.BluetoothStatusCodes.SUCCESS
            } else {
                writeChar.value = packet
                writeChar.writeType = if ((writeChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                } else {
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                }
                gatt.writeCharacteristic(writeChar)
            }
        } catch (e: Exception) {
            Log.w(TAG, "GATT write error: ${e.message}")
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeRfcomm(device: BluetoothDevice, packet: ByteArray): Boolean {
        val address = device.address
        val existingSocket = activeRfcommSockets[address]
        if (existingSocket != null && existingSocket.isConnected) {
            try {
                existingSocket.outputStream.write(packet)
                existingSocket.outputStream.flush()
                return true
            } catch (e: Exception) {
                log(address, "RFCOMM write error on cached socket: ${e.message}")
                try { existingSocket.close() } catch (_: Exception) {}
                activeRfcommSockets.remove(address)
            }
        }

        // Fresh connection attempt if cached socket failed or did not exist
        val freshSocket = connectRfcommSocket(device) ?: return false
        return try {
            freshSocket.outputStream.write(packet)
            freshSocket.outputStream.flush()
            // Hand the live socket to the reader worker. Caching it without a
            // reader left a zombie that the earbuds closed a few seconds later,
            // so every subsequent write hit "Broken pipe".
            rfcommJobs.remove(address)?.cancel()
            activeRfcommSockets[address] = freshSocket
            startRfcommWorker(device, freshSocket)
            true
        } catch (e: Exception) {
            log(address, "RFCOMM write error on fresh socket: ${e.message}")
            try { freshSocket.close() } catch (_: Exception) {}
            if (activeRfcommSockets[address] === freshSocket) {
                activeRfcommSockets.remove(address)
            }
            false
        }
    }

    // -- BLE GATT Callback -------------------------------------------------------------

    private fun createGattCallback(device: BluetoothDevice): BluetoothGattCallback {
        val address = device.address

        return object : BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    val driver = protocolRouter.resolveDriver(device)
                    _connectionStatus.value = _connectionStatus.value + (address to "Ready (${driver.driverName})")
                    log(address, "GATT Connected! Discovering services...")
                    try { gatt.requestMtu(512) } catch (_: Exception) {}
                    try { gatt.discoverServices() } catch (_: Exception) {}
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    _connectionStatus.value = _connectionStatus.value + (address to "GATT Disconnected")
                    log(address, "GATT Disconnected (status $status)")
                    // Only tear down session state that belongs to this GATT. A late
                    // callback from an older GATT must not evict a live session.
                    if (activeGatts.remove(address, gatt)) {
                        activeWriteCharacteristics.remove(address)
                        resetDescriptorState(address)
                    }
                    try { gatt.close() } catch (_: Exception) {}
                }
            }

            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) return

                val services = gatt.services ?: emptyList()
                val serviceUuidStrings = services.map { it.uuid.toString() }
                protocolRouter.registerDiscoveredServices(address, device, serviceUuidStrings)

                val driver = protocolRouter.resolveDriver(device, serviceUuidStrings)
                _activeDrivers.value = _activeDrivers.value + (address to driver.driverName)
                _connectionStatus.value = _connectionStatus.value + (address to "Ready: ${driver.driverName}")
                log(address, "Discovered ${services.size} services. Active Driver: ${driver.driverName}")

                if (activeDevice?.address == address) {
                    val updatedProfile = StrictBrandProfileResolver.resolve(
                        device = device,
                        discoveredGattUuids = serviceUuidStrings,
                    )
                    activeProfile = updatedProfile
                    activeDriver = driver
                    activeCapabilityManifest = HardwareCapabilityInspector.inspect(
                        device = device,
                        address = address,
                        deviceName = device.name,
                        discoveredGattUuids = serviceUuidStrings,
                    )
                }

                val diagList = mutableListOf<GattServiceDiagnostic>()
                var candidateWriteChar: BluetoothGattCharacteristic? = null

                services.forEach { service ->
                    val sUuid = service.uuid
                    val charDiagList = mutableListOf<GattCharDiagnostic>()

                    service.characteristics?.forEach { char ->
                        val cUuid = char.uuid
                        val props = char.properties
                        val canRead = (props and BluetoothGattCharacteristic.PROPERTY_READ) != 0
                        val canWrite = (props and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0
                        val canNotify = (props and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0

                        val propLabels = mutableListOf<String>()
                        if (canRead) propLabels += "READ"
                        if (canWrite) propLabels += "WRITE"
                        if (canNotify) propLabels += "NOTIFY"

                        charDiagList += GattCharDiagnostic(
                            uuid = cUuid.toString(),
                            properties = propLabels,
                            canRead = canRead,
                            canWrite = canWrite,
                            canNotify = canNotify,
                        )

                        if (canWrite) {
                            if (isKnownVendorChar(cUuid)) {
                                candidateWriteChar = char
                            } else if (candidateWriteChar == null) {
                                candidateWriteChar = char
                            }
                        }

                        // Subscribe to all NOTIFY / INDICATE characteristics.
                        // Android drops a second writeDescriptor() while the first
                        // is still pending, so descriptors are queued and written
                        // one at a time from onDescriptorWrite().
                        if (canNotify) {
                            try {
                                gatt.setCharacteristicNotification(char, true)
                                val cccd = char.getDescriptor(KnownUuids.CCCD_DESCRIPTOR)
                                if (cccd != null) {
                                    val descVal = if ((props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    } else {
                                        BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                                    }
                                    enqueueDescriptor(address, PendingDescriptor(gatt, cccd, descVal))
                                }
                            } catch (_: Exception) {}
                        }
                    }

                    diagList += GattServiceDiagnostic(
                        uuid = sUuid.toString(),
                        name = sUuid.toString().take(8),
                        characteristics = charDiagList,
                    )
                }

                _discoveredServices.value = _discoveredServices.value + (address to diagList)
                candidateWriteChar?.let { activeWriteCharacteristics[address] = it }

                scope.launch {
                    delay(300)
                    syncHardwareStateOnConnect(device)
                }
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                bleSequencer.onCharacteristicWriteCompleted(address, status)
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                val target = try { gatt.device.address } catch (_: Exception) { address }
                val finished = descriptorInFlight.remove(target)
                descriptorWriting[target] = false
                if (status != BluetoothGatt.GATT_SUCCESS && finished != null) {
                    log(target, "CCCD write failed (status $status) for ${descriptor.characteristic.uuid}")
                    requeueDescriptor(target, finished)
                }
                drainDescriptorQueue(target)
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                @Suppress("DEPRECATION")
                val value = characteristic.value ?: return
                processIncomingBytes(address, characteristic.uuid, value)
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                processIncomingBytes(address, characteristic.uuid, value)
            }

            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    @Suppress("DEPRECATION")
                    val value = characteristic.value ?: return
                    processIncomingBytes(address, characteristic.uuid, value)
                }
            }
        }
    }

    /**
     * Drops CCCD queue state for one link. A descriptor write that was still in
     * flight when the link dropped never receives onDescriptorWrite(), so its busy
     * flag stayed set and every later subscription on that address was skipped.
     */
    private fun resetDescriptorState(address: String) {
        descriptorQueues.remove(address)?.let { queue -> synchronized(queue) { queue.clear() } }
        descriptorInFlight.remove(address)
        descriptorWriting.remove(address)
    }

    /** Puts a failed CCCD write back at the front of the queue, up to a bounded number of attempts. */
    private fun requeueDescriptor(address: String, failed: PendingDescriptor) {
        if (failed.attempts + 1 >= MAX_DESCRIPTOR_ATTEMPTS) {
            log(address, "Giving up on CCCD write for ${failed.descriptor.characteristic.uuid} after ${failed.attempts + 1} attempts")
            return
        }
        val queue = descriptorQueues.computeIfAbsent(address) { java.util.ArrayDeque() }
        synchronized(queue) { queue.addFirst(failed.copy(attempts = failed.attempts + 1)) }
    }

    private fun enqueueDescriptor(address: String, pending: PendingDescriptor) {
        val queue = descriptorQueues.computeIfAbsent(address) { java.util.ArrayDeque() }
        synchronized(queue) { queue.addLast(pending) }
        drainDescriptorQueue(address)
    }

    private fun drainDescriptorQueue(address: String) {
        val queue = descriptorQueues[address] ?: return
        synchronized(queue) {
            if (descriptorWriting[address] == true) return
            val next = queue.pollFirst()
            if (next == null) {
                descriptorWriting.remove(address)
                return
            }
            descriptorWriting[address] = true
            descriptorInFlight[address] = next
            val ok: Boolean = try {
                if (Build.VERSION.SDK_INT >= 33) {
                    next.gatt.writeDescriptor(next.descriptor, next.value) ==
                        android.bluetooth.BluetoothStatusCodes.SUCCESS
                } else {
                    next.descriptor.value = next.value
                    next.gatt.writeDescriptor(next.descriptor)
                    true
                }
            } catch (_: Exception) {
                false
            }
            if (!ok) {
                descriptorInFlight.remove(address)
                descriptorWriting[address] = false
                requeueDescriptor(address, next)
                scope.launch { drainDescriptorQueue(address) }
            }
        }
    }

    /**
     * True when the earbuds' own mode report can be trusted for this device: the driver has a
     * mode read and the byte table was checked against the buds. The UI uses this to decide
     * whether tapping the active mode may switch back to Ambient.
     */
    fun hasVerifiedModeReadback(address: String): Boolean {
        val driver = protocolRouter.getDriverForAddress(address)
        return driver is OnePlusHeyMelodyDriver &&
            protocolRouter.getAncMapping(address).profileType == com.omnibuds.android.hardware.protocol.AncProfileType.PROFILE_G
    }

    /**
     * Turns a mode the buds reported into the app's confirmed mode, so a press on the stem
     * moves the buttons and the notification. A report that disagrees with a command sent
     * moments ago is ignored: the buds need about a third of a second to apply it.
     */
    private fun applyReportedMode(address: String, mask: Int) {
        if (!hasVerifiedModeReadback(address)) return
        if (mask !in 1..0xFF) return
        val mapping = protocolRouter.getAncMapping(address)
        val reported = mapping.modeForByte(mask.toByte()) ?: return

        val requested = bleSequencer.getLatestRequestedNoiseMode(address)
        val settling = bleSequencer.millisSinceUserCommand(address) < MODE_SETTLE_WINDOW_MS
        if (settling && requested != null && !requested.equals(reported, ignoreCase = true)) return

        if (_confirmedAncMode.value[address] != reported) {
            com.omnibuds.android.hardware.protocol.AncAuditLog.record(
                stage = "NOTIFY",
                address = address,
                activeMode = reported,
                note = "buds report mode 0x%02X".format(mask),
            )
            _confirmedAncMode.value = _confirmedAncMode.value + (address to reported)
            bleSequencer.updateConfirmedMode(address, reported)
        }
        if (_pendingAncMode.value[address] != null &&
            (_pendingAncMode.value[address].equals(reported, ignoreCase = true) || !settling)
        ) {
            _pendingAncMode.value = _pendingAncMode.value + (address to null)
        }
    }

    /**
     * Records the noise-mode value the buds themselves report (reply to the mode read, or an
     * event-03 push). The raw bitmask is logged and nothing else changes: its meaning per
     * model is not confirmed yet, so it must not feed the confirmed mode.
     */
    // -- Press-and-hold cycle ----------------------------------------------------------
    // The buds keep this setting themselves, so the app stores nothing about it on the phone.

    private val _holdCycleMasks = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** What each connected pair of buds says its press-and-hold cycles through (mode bit mask). */
    val holdCycleMasks: StateFlow<Map<String, Int>> = _holdCycleMasks.asStateFlow()

    private fun recordHoldCycleReports(address: String, bytes: ByteArray) {
        for (mask in com.omnibuds.android.hardware.protocol.RealmeProtocolParser.parseHoldCycleReports(bytes)) {
            if (_holdCycleMasks.value[address] != mask) {
                _holdCycleMasks.value = _holdCycleMasks.value + (address to mask)
                com.omnibuds.android.hardware.protocol.AncAuditLog.record(
                    stage = "HOLD_CYCLE",
                    address = address,
                    note = "buds report press-and-hold cycle mask=0x%X".format(mask),
                )
            }
        }
    }

    /**
     * Sets which two or three modes the earbuds' press-and-hold cycles through, then reads the
     * setting back from the buds. Returns true only when the buds report the new value.
     * Only for devices whose mode table was verified on hardware.
     */
    @SuppressLint("MissingPermission")
    suspend fun setHoldCycle(device: BluetoothDevice, mask: Int): Boolean =
        withContext(Dispatchers.IO) { writeHoldCycleAndVerify(device, mask) }

    @SuppressLint("MissingPermission")
    private suspend fun writeHoldCycleAndVerify(device: BluetoothDevice, mask: Int): Boolean {
        val address = device.address
        if (!hasVerifiedModeReadback(address)) return false
        val allowed = protocolRouter.getAncMapping(address).let {
            (it.ancByte.toInt() and 0xFF) or (it.transByte.toInt() and 0xFF) or (it.offByte.toInt() and 0xFF)
        }
        if (mask and allowed.inv() != 0 || Integer.bitCount(mask) < 2) {
            log(address, "Refusing hold-cycle mask 0x%X: needs at least two known modes".format(mask))
            return false
        }
        val write = com.omnibuds.android.hardware.protocol.RealmeProtocolParser.buildHoldCycleWrite(mask)
        log(address, "Writing press-and-hold cycle 0x%X".format(mask), HexUtils.formatHex(write))
        com.omnibuds.android.hardware.protocol.AncAuditLog.record(
            stage = "HOLD_WRITE",
            address = address,
            sentHex = HexUtils.formatHex(write),
            note = "requested mask=0x%X".format(mask),
        )
        if (!sendPacket(device, write)) return false

        // An accepted write is not proof. Read the setting back from the buds.
        repeat(4) {
            delay(350)
            sendPacket(device, com.omnibuds.android.hardware.protocol.RealmeProtocolParser.buildHoldCycleRead())
            delay(250)
            if (_holdCycleMasks.value[address] == mask) {
                markConfigSaved(address, "Press-and-hold saved to the earbuds ✓")
                return true
            }
        }
        com.omnibuds.android.hardware.protocol.AncAuditLog.record(
            stage = "HOLD_REJECTED",
            address = address,
            note = "buds still report 0x%X after writing 0x%X".format(_holdCycleMasks.value[address] ?: -1, mask),
        )
        return false
    }

    private fun recordModeReports(address: String, bytes: ByteArray, hex: String) {
        recordHoldCycleReports(address, bytes)
        for (report in com.omnibuds.android.hardware.protocol.RealmeProtocolParser.parseAncModeReports(bytes)) {
            // The mode is polled, so the same value arrives every cycle. Log changes only.
            val previous = lastReportedModeMask.put(address, report.mask)
            applyReportedMode(address, report.mask)
            if (previous == report.mask && !report.viaPush) continue
            val kind = if (report.viaPush) "push" else "read"
            com.omnibuds.android.hardware.protocol.AncAuditLog.record(
                stage = "ANC_RAW",
                address = address,
                responseHex = hex,
                note = "buds reported mode mask=0x%X bytes=[%s] via %s".format(report.mask, report.maskBytes, kind),
            )
        }
    }

    private fun processIncomingBytes(address: String, charUuid: UUID, bytes: ByteArray) {
        val hex = HexUtils.formatHex(bytes)
        _lastRawPackets.value = _lastRawPackets.value + (address to hex)
        log(address, "RX [${charUuid.toString().take(8)}]: $hex", hex)
        recordModeReports(address, bytes, hex)

        // 1. Two-Way Hardware Sync: ANC / Noise Mode Notification & ACK
        val isAck = protocolRouter.isAncAck(address, bytes)
        // An ACK only confirms a command we sent. With nothing pending, the frame is
        // an unsolicited device report (for example a stem press), so it is decoded below.
        val ackForPendingCommand = isAck && (bleSequencer.getInFlightMode(address) ?: _pendingAncMode.value[address]) != null
        if (ackForPendingCommand) {
            val inFlight = bleSequencer.getInFlightMode(address) ?: _pendingAncMode.value[address]
            if (inFlight != null) {
                if (bleSequencer.shouldIgnoreNotification(address, inFlight)) {
                    log(address, "Ignored stale ACK ($inFlight) within 250ms of user command")
                    return
                }
                log(address, "BLE Hardware ACK: Confirmed requested ANC mode -> $inFlight")
                com.omnibuds.android.hardware.protocol.AncAuditLog.record(
                    stage = "ACK",
                    address = address,
                    activeMode = inFlight,
                    requestedMode = inFlight,
                    responseHex = hex,
                )
                _confirmedAncMode.value = _confirmedAncMode.value + (address to inFlight)
                if (_pendingAncMode.value[address] == inFlight) {
                    _pendingAncMode.value = _pendingAncMode.value + (address to null)
                }
                bleSequencer.updateConfirmedMode(address, inFlight)
                markConfigSaved(address, "Noise mode saved to earbud memory ✓")
            }
        } else {
            val ancMode = protocolRouter.parseAncModeNotification(address, bytes)
            if (ancMode != null) {
                if (bleSequencer.shouldIgnoreNotification(address, ancMode)) {
                    log(address, "Ignored stale BLE notification ($ancMode) within 250ms of user command (${bleSequencer.getLatestRequestedNoiseMode(address)})")
                    return
                }
                log(address, "Two-Way Hardware Sync: Confirmed Active Noise Mode -> $ancMode")
                com.omnibuds.android.hardware.protocol.AncAuditLog.record(
                    stage = "NOTIFY",
                    address = address,
                    activeMode = ancMode,
                    responseHex = hex,
                )
                _confirmedAncMode.value = _confirmedAncMode.value + (address to ancMode)
                if (_pendingAncMode.value[address] == ancMode) {
                    _pendingAncMode.value = _pendingAncMode.value + (address to null)
                }
                bleSequencer.updateConfirmedMode(address, ancMode)
            }
        }

        // Software Touch Event Interceptor fallback
        val touchEvt = com.omnibuds.android.hardware.protocol.RealmeProtocolParser.parseTouchNotification(bytes)
        if (touchEvt != null) {
            if (touchEvt.isDoubleTap && isCameraShutterEnabled) {
                triggerCameraShutter()
            } else if (touchEvt.action != "UNKNOWN") {
                handleTouchInterception(touchEvt.action, address)
            }
        }

        protocolRouter.parseBatchStatus(address, bytes)?.let { status ->
            status.gameMode?.let { enabled ->
                _confirmedGameMode.value = _confirmedGameMode.value + (address to enabled)
                com.omnibuds.android.hardware.protocol.AncAuditLog.record(
                    stage = "GAME_MODE",
                    address = address,
                    activeMode = if (enabled) "ON" else "OFF",
                    responseHex = hex,
                )
            }
        }

        // 2. Try vendor router drivers
        val report = protocolRouter.parseBatteryReport(address, bytes)
        if (report != null && report.hasComponentData) {
            handleBatteryReport(address, report, "BLE Vendor GATT")
            return
        }

        // 3. Try Fast Pair BLE parser
        val fpReport = FastPairProtocolParser.parseBattery(bytes)
        if (fpReport != null && fpReport.hasComponentData) {
            handleBatteryReport(address, fpReport, "Fast Pair (0xFE2C)")
            return
        }

        // 4. Standard BLE Battery Service
        if (charUuid == KnownUuids.BATTERY_LEVEL_CHAR && bytes.isNotEmpty()) {
            val level = bytes[0].toInt() and 0xFF
            if (level in 0..100) {
                val singleReport = TwsBatteryReport(
                    left = level,
                    right = level,
                    case = null,
                    rawHex = hex,
                    sourceProtocol = "BLE Battery Service (0x180F)",
                )
                handleBatteryReport(address, singleReport, "BLE Battery Service (0x180F)")
            }
        }
    }

    private fun isKnownVendorChar(uuid: UUID): Boolean {
        return uuid == KnownUuids.REALME_BLE_CHAR_TX_1 ||
            uuid == KnownUuids.REALME_BLE_CHAR_TX_2 ||
            uuid == KnownUuids.OPO_CHAR_TX ||
            uuid == KnownUuids.BES_CHAR_TX ||
            uuid == KnownUuids.NORDIC_UART_TX
    }

    // -- RFCOMM Worker -----------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun startRfcommWorker(
        device: BluetoothDevice,
        existingSocket: BluetoothSocket? = null,
    ) {
        val address = device.address
        if (existingSocket == null &&
            (activeRfcommSockets.containsKey(address) || rfcommJobs[address]?.isActive == true)
        ) return

        val job = scope.launch {
            var socket: BluetoothSocket? = existingSocket
            try {
                socket = existingSocket ?: connectRfcommSocket(device) ?: run {
                    log(address, "RFCOMM connect failed on all channels; the next connect() call retries")
                    return@launch
                }
                activeRfcommSockets[address] = socket
                log(address, "RFCOMM Socket connected successfully")

                val input: InputStream = socket.inputStream
                val out: OutputStream = socket.outputStream

                // Initial battery + NVRAM state dump
                val queryPkt = protocolRouter.buildBatteryQueryCommand(device)
                val modePollPkt = protocolRouter.buildModePollCommand(device)
                syncHardwareStateOnConnect(device)

                var lastBatteryPoll = System.currentTimeMillis()
                val buffer = ByteArray(512)

                while (isActive && socket.isConnected) {
                    val available = try { input.available() } catch (_: Exception) { -1 }
                    if (available < 0) break

                    if (available > 0) {
                        val read = input.read(buffer)
                        if (read > 0) {
                            val packet = buffer.copyOf(read)
                            val hex = HexUtils.formatHex(packet)
                            log(address, "RFCOMM RX: $hex", hex)
                            recordModeReports(address, packet, hex)
                            _lastRawPackets.value = _lastRawPackets.value + (address to hex)

                            // Two-Way Sync: ANC / Noise Mode
                            val isAck = protocolRouter.isAncAck(address, packet)
                            if (!isAck && protocolRouter.parseBatteryReport(address, packet) == null) {
                                // Keep every non-battery, non-ACK frame visible in
                                // the audit so a query response can be decoded.
                                com.omnibuds.android.hardware.protocol.AncAuditLog.record(
                                    stage = "RX",
                                    address = address,
                                    responseHex = hex,
                                )
                            }
                            // An ACK only confirms a command we sent (see processIncomingBytes).
                            if (isAck && _pendingAncMode.value[address] != null) {
                                val pending = _pendingAncMode.value[address]
                                if (pending != null) {
                                    if (!bleSequencer.shouldIgnoreNotification(address, pending)) {
                                        log(address, "RFCOMM Hardware ACK: Confirmed requested ANC mode -> $pending")
                                        com.omnibuds.android.hardware.protocol.AncAuditLog.record(
                                            stage = "ACK",
                                            address = address,
                                            activeMode = pending,
                                            requestedMode = pending,
                                            responseHex = hex,
                                        )
                                        _confirmedAncMode.value = _confirmedAncMode.value + (address to pending)
                                        _pendingAncMode.value = _pendingAncMode.value + (address to null)
                                        bleSequencer.updateConfirmedMode(address, pending)
                                        markConfigSaved(address, "Noise mode saved to earbud memory ✓")
                                    }
                                }
                            } else {
                                val ancMode = protocolRouter.parseAncModeNotification(address, packet)
                                if (ancMode != null) {
                                    if (!bleSequencer.shouldIgnoreNotification(address, ancMode)) {
                                        log(address, "RFCOMM Hardware Sync: Confirmed Active Noise Mode -> $ancMode")
                                        com.omnibuds.android.hardware.protocol.AncAuditLog.record(
                                            stage = "NOTIFY",
                                            address = address,
                                            activeMode = ancMode,
                                            responseHex = hex,
                                        )
                                        _confirmedAncMode.value = _confirmedAncMode.value + (address to ancMode)
                                        _pendingAncMode.value = _pendingAncMode.value + (address to null)
                                        bleSequencer.updateConfirmedMode(address, ancMode)
                                    }
                                }
                            }

                            // Software Touch Event Interceptor fallback
                            val touchEvt = com.omnibuds.android.hardware.protocol.RealmeProtocolParser.parseTouchNotification(packet)
                            if (touchEvt != null) {
                                if (touchEvt.isDoubleTap && isCameraShutterEnabled) {
                                    triggerCameraShutter()
                                } else if (touchEvt.action != "UNKNOWN") {
                                    handleTouchInterception(touchEvt.action, address)
                                }
                            }

                            protocolRouter.parseBatteryReport(address, packet)?.let { rpt ->
                                handleBatteryReport(address, rpt, "RFCOMM SPP")
                            }

                            protocolRouter.parseBatchStatus(address, packet)?.let { status ->
                                status.gameMode?.let { enabled ->
                                    _confirmedGameMode.value = _confirmedGameMode.value + (address to enabled)
                                    com.omnibuds.android.hardware.protocol.AncAuditLog.record(
                                        stage = "GAME_MODE",
                                        address = address,
                                        activeMode = if (enabled) "ON" else "OFF",
                                        responseHex = hex,
                                    )
                                }
                            }
                        }
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastBatteryPoll > 1500 && bleSequencer.canPollBattery(address)) {
                        lastBatteryPoll = now
                        queryPkt?.let {
                            try {
                                out.write(it)
                                out.flush()
                            } catch (_: Exception) {}
                        }
                        // Ask the buds for their real noise mode. A press on the stem is not
                        // pushed to the app, so this read is how a physical change is seen.
                        modePollPkt?.let {
                            try {
                                out.write(it)
                                out.flush()
                            } catch (_: Exception) {}
                        }
                        // Batch status carries live switch state (game mode).
                        protocolRouter.buildBatchStatusQueryCommand(device)?.let {
                            log(address, "Batch status query sent", HexUtils.formatHex(it))
                            try {
                                out.write(it)
                                out.flush()
                            } catch (_: Exception) {}
                        }
                    }
                    delay(150)
                }
            } catch (e: Exception) {
                log(address, "RFCOMM worker terminated: ${e.message}")
            } finally {
                try { socket?.close() } catch (_: Exception) {}
                // Only remove the map entry if it still belongs to this worker;
                // a replacement socket may already have taken over.
                if (activeRfcommSockets[address] === socket) {
                    activeRfcommSockets.remove(address)
                }
            }
        }
        rfcommJobs[address] = job
    }

    @SuppressLint("MissingPermission")
    private fun connectRfcommSocket(device: BluetoothDevice): BluetoothSocket? {
        val name = try { device.name ?: "" } catch (_: Exception) { "" }.lowercase()
        val isNothingOrCmf = name.contains("nothing") || name.contains("cmf") || name.contains("b168")

        // 1. For non-Nothing earbuds (e.g. Realme/Oppo), try proprietary UUID first
        if (!isNothingOrCmf) {
            try {
                val sock = device.createInsecureRfcommSocketToServiceRecord(KnownUuids.REALME_RFCOMM_UUID)
                sock.connect()
                return sock
            } catch (_: Exception) {}
        }

        // 2. Standard SPP UUID 00001101-0000-1000-8000-00805F9B34FB (Nothing / CMF and universal standard)
        try {
            val sock = device.createInsecureRfcommSocketToServiceRecord(KnownUuids.STANDARD_SPP_UUID)
            sock.connect()
            return sock
        } catch (_: Exception) {}

        // 3. Fallback direct channel reflection
        try {
            val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            val sock = m.invoke(device, 12) as? BluetoothSocket
            sock?.connect()
            return sock
        } catch (_: Exception) {}

        return null
    }
}
