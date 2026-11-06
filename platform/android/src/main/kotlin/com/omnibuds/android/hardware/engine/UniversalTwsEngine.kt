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
import com.omnibuds.android.hardware.protocol.DiagnosticLogEntry
import com.omnibuds.android.hardware.protocol.FastPairProtocolParser
import com.omnibuds.android.hardware.protocol.GattCharDiagnostic
import com.omnibuds.android.hardware.protocol.GattServiceDiagnostic
import com.omnibuds.android.hardware.protocol.HexUtils
import com.omnibuds.android.hardware.protocol.KnownUuids
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Subsystems
    val protocolRouter = VendorProtocolRouter()
    val dspController = UniversalDspController()
    private var _batteryScanner: FastPairAndBleBatteryScanner? = null
    val batteryScanner: FastPairAndBleBatteryScanner?
        get() = _batteryScanner

    // Transports: address -> BluetoothGatt / BluetoothSocket
    private val activeGatts = ConcurrentHashMap<String, BluetoothGatt>()
    private val activeWriteCharacteristics = ConcurrentHashMap<String, BluetoothGattCharacteristic>()
    private val activeRfcommSockets = ConcurrentHashMap<String, BluetoothSocket>()
    private val rfcommJobs = ConcurrentHashMap<String, Job>()

    // Live Telemetry Flows
    private val _batteryReports = MutableSharedFlow<Pair<String, TwsBatteryReport>>(extraBufferCapacity = 128)
    val batteryReports: SharedFlow<Pair<String, TwsBatteryReport>> = _batteryReports.asSharedFlow()

    private val _diagnosticLogs = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
    val diagnosticLogs: StateFlow<List<DiagnosticLogEntry>> = _diagnosticLogs.asStateFlow()

    private val _discoveredServices = MutableStateFlow<Map<String, List<GattServiceDiagnostic>>>(emptyMap())
    val discoveredServices: StateFlow<Map<String, List<GattServiceDiagnostic>>> = _discoveredServices.asStateFlow()

    private val _lastRawPackets = MutableStateFlow<Map<String, String>>(emptyMap())
    val lastRawPackets: StateFlow<Map<String, String>> = _lastRawPackets.asStateFlow()

    private val _connectionStatus = MutableStateFlow<Map<String, String>>(emptyMap())
    val connectionStatus: StateFlow<Map<String, String>> = _connectionStatus.asStateFlow()

    private val _activeDrivers = MutableStateFlow<Map<String, String>>(emptyMap())
    val activeDrivers: StateFlow<Map<String, String>> = _activeDrivers.asStateFlow()

    private var appContext: Context? = null
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        appContext = context.applicationContext
        isInitialized = true

        dspController.attach()

        val scanner = FastPairAndBleBatteryScanner(context.applicationContext, scope)
        _batteryScanner = scanner

        // Forward BLE advertisement battery reports to master flow
        scope.launch {
            scanner.batteryReports.collect { report ->
                _batteryReports.emit(report)
            }
        }

        scanner.startScan()
        Log.i(TAG, "UniversalTwsEngine initialized successfully with all 3 layers active")
    }

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

    @SuppressLint("MissingPermission")
    fun connect(context: Context, device: BluetoothDevice) {
        val address = device.address
        if (activeGatts.containsKey(address) || activeRfcommSockets.containsKey(address) || rfcommJobs[address]?.isActive == true) {
            return
        }

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

        startRfcommWorker(device)
    }

    @SuppressLint("MissingPermission")
    fun disconnect(address: String) {
        log(address, "Disconnecting GATT and RFCOMM sessions")
        _connectionStatus.value = _connectionStatus.value - address
        _activeDrivers.value = _activeDrivers.value - address

        activeGatts.remove(address)?.let { gatt ->
            try {
                gatt.disconnect()
                gatt.close()
            } catch (_: Exception) {}
        }
        activeWriteCharacteristics.remove(address)

        rfcommJobs.remove(address)?.cancel()
        activeRfcommSockets.remove(address)?.let { sock ->
            try { sock.close() } catch (_: Exception) {}
        }
        protocolRouter.clear(address)
    }

    // -- Controls & Routing -------------------------------------------------------------

    /**
     * Sets Noise Control (ANC, Transparency, Off/Normal) via the detected vendor driver.
     */
    @SuppressLint("MissingPermission")
    suspend fun setNoiseControl(device: BluetoothDevice, mode: String): Boolean {
        val address = device.address
        val packet = protocolRouter.buildAncCommand(device, mode)
        val driver = protocolRouter.resolveDriver(device)
        log(address, "Routing ANC mode '$mode' via ${driver.driverName}", HexUtils.formatHex(packet))
        return sendPacket(device, packet)
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
        // 1. Universal OS-Level DSP Spatial Audio (100% universal across all earphones)
        dspController.setSpatialAudioEnabled(enabled)

        // 2. Route hardware packet if connected device is provided
        if (device != null) {
            val packet = protocolRouter.buildSpatialAudioCommand(device, enabled)
            if (packet != null && packet.isNotEmpty()) {
                val driver = protocolRouter.resolveDriver(device)
                log(device.address, "Sending hardware Spatial Audio ($enabled) via ${driver.driverName}", HexUtils.formatHex(packet))
                sendPacket(device, packet)
            }
        }
        return true
    }

    /**
     * Toggles Game Mode (Low Latency) via hardware driver.
     */
    @SuppressLint("MissingPermission")
    suspend fun setGameMode(device: BluetoothDevice, enabled: Boolean): Boolean {
        val packet = protocolRouter.buildGameModeCommand(device, enabled) ?: return false
        val driver = protocolRouter.resolveDriver(device)
        log(device.address, "Sending Game Mode ($enabled) via ${driver.driverName}", HexUtils.formatHex(packet))
        return sendPacket(device, packet)
    }

    /**
     * Queries battery telemetry from the connected device.
     */
    @SuppressLint("MissingPermission")
    suspend fun queryBattery(device: BluetoothDevice) {
        val packet = protocolRouter.buildBatteryQueryCommand(device) ?: return
        val driver = protocolRouter.resolveDriver(device)
        log(device.address, "Querying battery telemetry via ${driver.driverName}", HexUtils.formatHex(packet))
        sendPacket(device, packet)
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

    // -- Packet Transport ---------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private suspend fun sendPacket(device: BluetoothDevice, packet: ByteArray): Boolean {
        val address = device.address
        val driver = protocolRouter.resolveDriver(device)

        if (driver.preferredTransport == TransportPreference.RFCOMM_FIRST) {
            if (activeRfcommSockets.containsKey(address) && writeRfcomm(device, packet)) {
                return true
            }
            if (writeGatt(device, packet)) return true
        } else {
            if (writeGatt(device, packet)) return true
            if (writeRfcomm(device, packet)) return true
        }

        // Final fallback: try opening RFCOMM if neither was connected
        if (writeRfcomm(device, packet)) return true

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
                gatt.writeCharacteristic(writeChar, packet, writeType) == BluetoothGatt.GATT_SUCCESS
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
        activeRfcommSockets[address] = freshSocket
        return try {
            freshSocket.outputStream.write(packet)
            freshSocket.outputStream.flush()
            true
        } catch (e: Exception) {
            log(address, "RFCOMM write error on fresh socket: ${e.message}")
            try { freshSocket.close() } catch (_: Exception) {}
            activeRfcommSockets.remove(address)
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
                    log(address, "GATT Disconnected")
                    activeGatts.remove(address)
                    activeWriteCharacteristics.remove(address)
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

                        // Subscribe to all NOTIFY / INDICATE characteristics (Auto-prober)
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
                                    if (Build.VERSION.SDK_INT >= 33) {
                                        gatt.writeDescriptor(cccd, descVal)
                                    } else {
                                        cccd.value = descVal
                                        gatt.writeDescriptor(cccd)
                                    }
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
                    queryBattery(device)
                }
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

    private fun processIncomingBytes(address: String, charUuid: UUID, bytes: ByteArray) {
        val hex = HexUtils.formatHex(bytes)
        _lastRawPackets.value = _lastRawPackets.value + (address to hex)
        log(address, "RX [${charUuid.toString().take(8)}]: $hex", hex)

        // 1. Try vendor router drivers
        val report = protocolRouter.parseBatteryReport(address, bytes)
        if (report != null && report.hasComponentData) {
            log(address, "Parsed Multi-Component Battery -> L:${report.left}% R:${report.right}% Case:${report.case}%")
            scope.launch { _batteryReports.emit(address to report) }
            return
        }

        // 2. Try Fast Pair BLE parser
        val fpReport = FastPairProtocolParser.parseBattery(bytes)
        if (fpReport != null && fpReport.hasComponentData) {
            log(address, "Parsed FastPair Battery -> L:${fpReport.left}% R:${fpReport.right}% Case:${fpReport.case}%")
            scope.launch { _batteryReports.emit(address to fpReport) }
            return
        }

        // 3. Standard BLE Battery Service
        if (charUuid == KnownUuids.BATTERY_LEVEL_CHAR && bytes.isNotEmpty()) {
            val level = bytes[0].toInt() and 0xFF
            if (level in 0..100) {
                val singleReport = TwsBatteryReport(left = level, right = level, case = null, rawHex = hex)
                scope.launch { _batteryReports.emit(address to singleReport) }
            }
        }
    }

    private fun isKnownVendorChar(uuid: UUID): Boolean {
        return uuid == KnownUuids.REALME_BLE_CHAR_TX_1 ||
            uuid == KnownUuids.REALME_BLE_CHAR_TX_2 ||
            uuid == KnownUuids.BES_CHAR_TX ||
            uuid == KnownUuids.NORDIC_UART_TX
    }

    // -- RFCOMM Worker -----------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun startRfcommWorker(device: BluetoothDevice) {
        val address = device.address
        if (activeRfcommSockets.containsKey(address) || rfcommJobs[address]?.isActive == true) return

        val job = scope.launch {
            var socket: BluetoothSocket? = null
            try {
                socket = connectRfcommSocket(device) ?: return@launch
                activeRfcommSockets[address] = socket
                log(address, "RFCOMM Socket connected successfully")

                val input: InputStream = socket.inputStream
                val out: OutputStream = socket.outputStream

                // Initial handshake and battery query
                val queryPkt = protocolRouter.buildBatteryQueryCommand(device)
                if (queryPkt != null) {
                    out.write(queryPkt)
                    out.flush()
                }

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
                            _lastRawPackets.value = _lastRawPackets.value + (address to hex)

                            protocolRouter.parseBatteryReport(address, packet)?.let { rpt ->
                                log(address, "RFCOMM Parsed Battery -> L:${rpt.left}% R:${rpt.right}% Case:${rpt.case}%")
                                _batteryReports.emit(address to rpt)
                            }
                        }
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastBatteryPoll > 6000) {
                        lastBatteryPoll = now
                        queryPkt?.let {
                            try {
                                out.write(it)
                                out.flush()
                            } catch (_: Exception) {}
                        }
                    }
                    delay(200)
                }
            } catch (e: Exception) {
                log(address, "RFCOMM worker terminated: ${e.message}")
            } finally {
                try { socket?.close() } catch (_: Exception) {}
                activeRfcommSockets.remove(address)
            }
        }
        rfcommJobs[address] = job
    }

    @SuppressLint("MissingPermission")
    private fun connectRfcommSocket(device: BluetoothDevice): BluetoothSocket? {
        try {
            val sock = device.createInsecureRfcommSocketToServiceRecord(KnownUuids.REALME_RFCOMM_UUID)
            sock.connect()
            return sock
        } catch (_: Exception) {}

        try {
            val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            val sock = m.invoke(device, 12) as? BluetoothSocket
            sock?.connect()
            return sock
        } catch (_: Exception) {}

        try {
            val sock = device.createInsecureRfcommSocketToServiceRecord(KnownUuids.STANDARD_SPP_UUID)
            sock.connect()
            return sock
        } catch (_: Exception) {}

        return null
    }
}
