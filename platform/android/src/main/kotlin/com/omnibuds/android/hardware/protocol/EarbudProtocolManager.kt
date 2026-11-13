@file:Suppress("DEPRECATION")

package com.omnibuds.android.hardware.protocol

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import com.omnibuds.android.hardware.engine.UniversalTwsEngine
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Universal TWS Protocol & Hardware Communication Manager.
 *
 * Facade that delegates to [UniversalTwsEngine] for unified 3-layer architecture:
 * 1. Universal Split-Battery Discovery (Google Fast Pair, Apple Continuity, Vendor Telemetry)
 * 2. Multi-Vendor Hardware Protocol Router (HeyMelody, Airoha RACE, Nothing, Samsung, Generic Prober)
 * 3. Universal OS-Level DSP Engine (10-Band EQ, 3D Spatial Audio & Headstage Crossfeed)
 */
object EarbudProtocolManager {

    val batteryReports: SharedFlow<Pair<String, TwsBatteryReport>>
        get() = UniversalTwsEngine.batteryReports

    val latestBatteryReports: StateFlow<Map<String, TwsBatteryReport>>
        get() = UniversalTwsEngine.latestBatteryReports

    val diagnosticLogs: StateFlow<List<DiagnosticLogEntry>>
        get() = UniversalTwsEngine.diagnosticLogs

    val discoveredServices: StateFlow<Map<String, List<GattServiceDiagnostic>>>
        get() = UniversalTwsEngine.discoveredServices

    val lastRawPackets: StateFlow<Map<String, String>>
        get() = UniversalTwsEngine.lastRawPackets

    val lastSentPackets: StateFlow<Map<String, String>>
        get() = UniversalTwsEngine.lastSentPackets

    val confirmedAncMode: StateFlow<Map<String, String>>
        get() = UniversalTwsEngine.confirmedAncMode

    val confirmedGameMode: StateFlow<Map<String, Boolean>>
        get() = UniversalTwsEngine.confirmedGameMode

    val configSaveState: StateFlow<Map<String, String>>
        get() = UniversalTwsEngine.configSaveState

    val pendingAncMode: StateFlow<Map<String, String?>>
        get() = UniversalTwsEngine.pendingAncMode

    val connectionStatus: StateFlow<Map<String, String>>
        get() = UniversalTwsEngine.connectionStatus

    val noiseControlDiagnosticStates: StateFlow<Map<String, com.omnibuds.android.hardware.engine.NoiseControlDiagnosticState>>
        get() = UniversalTwsEngine.bleSequencer.diagnosticStates

    fun getAncMapping(address: String): AncByteMapping = UniversalTwsEngine.getAncMapping(address)

    fun cycleAncProfile(address: String): AncByteMapping = UniversalTwsEngine.cycleAncProfile(address)

    fun setCustomAncMapping(address: String, off: Byte, anc: Byte, trans: Byte): AncByteMapping =
        UniversalTwsEngine.setCustomAncMapping(address, off, anc, trans)

    @SuppressLint("MissingPermission")
    suspend fun testOffByteVariant(device: BluetoothDevice, candidateOffByte: Byte): Boolean =
        UniversalTwsEngine.testOffByteVariant(device, candidateOffByte)

    fun lockInOffByteVariant(address: String, candidateOffByte: Byte): AncByteMapping =
        UniversalTwsEngine.lockInOffByteVariant(address, candidateOffByte)

    /**
     * Connects to a device via BLE GATT and Classic Bluetooth RFCOMM.
     */
    @SuppressLint("MissingPermission")
    fun connect(context: Context, device: BluetoothDevice) {
        UniversalTwsEngine.init(context)
        UniversalTwsEngine.connect(context, device)
    }

    /**
     * Disconnects and cleans up all GATT and RFCOMM handles for a device.
     */
    @SuppressLint("MissingPermission")
    fun disconnect(address: String) {
        UniversalTwsEngine.disconnect(address)
    }

    /**
     * Sends an Active Noise Cancellation mode command: "ANC", "OFF", or "TRANSPARENCY".
     */
    @SuppressLint("MissingPermission")
    suspend fun sendAncMode(device: BluetoothDevice, mode: String): Boolean {
        return UniversalTwsEngine.setNoiseControl(device, mode)
    }

    /**
     * Sends an Equalizer preset command: "FLAT", "BASS_BOOST", "VOCAL", "TREBLE_BOOST", "HARMAN_TARGET", "WARMTH".
     */
    @SuppressLint("MissingPermission")
    suspend fun sendEqualizer(device: BluetoothDevice, preset: String): Boolean {
        return UniversalTwsEngine.setEqualizer(device, preset)
    }

    /**
     * Sends a Spatial Audio command: true (On) / false (Off).
     */
    @SuppressLint("MissingPermission")
    suspend fun sendSpatialAudio(device: BluetoothDevice, enabled: Boolean): Boolean {
        return UniversalTwsEngine.setSpatialAudio(device, enabled)
    }

    /**
     * Sends a Game Mode (Low Latency) toggle command.
     */
    @SuppressLint("MissingPermission")
    suspend fun sendGameMode(device: BluetoothDevice, enabled: Boolean): Boolean {
        return UniversalTwsEngine.setGameMode(device, enabled)
    }

    /**
     * Sends Dynamic Bass command.
     */
    @SuppressLint("MissingPermission")
    suspend fun sendDynamicBass(device: BluetoothDevice, enabled: Boolean, level: Int = 3): Boolean {
        return UniversalTwsEngine.setDynamicBass(device, enabled, level)
    }

    /**
     * Sends Volume Enhancer command.
     */
    @SuppressLint("MissingPermission")
    suspend fun sendVolumeEnhancer(device: BluetoothDevice, enabled: Boolean): Boolean {
        return UniversalTwsEngine.setVolumeEnhancer(device, enabled)
    }

    /**
     * Sends Touch Gesture Configuration: "bud:gesture:action" or full struct.
     */
    @SuppressLint("MissingPermission")
    suspend fun sendTouchControl(device: BluetoothDevice, config: String): Boolean {
        val parts = config.split(":")
        if (parts.size == 3) {
            val (bud, gesture, action) = parts
            // Master enable wake-up + single write
            val enablePkts = RealmeProtocolParser.buildTouchMasterEnableCommands(true)
            for (p in enablePkts) UniversalTwsEngine.sendRawHex(device, HexUtils.formatHex(p))
            val packet = RealmeProtocolParser.buildTouchGestureCommand(bud, gesture, action)
            return UniversalTwsEngine.sendRawHex(device, HexUtils.formatHex(packet))
        }
        return false
    }

    /**
     * Sends the complete synchronized Left + Right touch gesture matrix.
     */
    @SuppressLint("MissingPermission")
    suspend fun sendFullTouchMatrix(
        device: BluetoothDevice,
        leftDouble: String,
        rightDouble: String,
        leftTriple: String,
        rightTriple: String,
        leftHold: String,
        rightHold: String,
        ancCycleBitmask: Byte = 0x06,
    ): Boolean {
        return UniversalTwsEngine.setTouchControls(
            device = device,
            leftDouble = leftDouble,
            rightDouble = rightDouble,
            leftTriple = leftTriple,
            rightTriple = rightTriple,
            leftHold = leftHold,
            rightHold = rightHold,
            ancCycleBitmask = ancCycleBitmask,
        )
    }

    /**
     * Restores default touch controls on earbuds.
     */
    @SuppressLint("MissingPermission")
    suspend fun restoreDefaultTouchControls(device: BluetoothDevice): Boolean {
        return UniversalTwsEngine.restoreDefaultTouchControls(device)
    }

    /**
     * Rings earbud: bud = "Left", "Right", or "Both".
     */
    @SuppressLint("MissingPermission")
    fun ringEarbud(device: BluetoothDevice?, bud: String, ring: Boolean) {
        UniversalTwsEngine.ringEarbud(device, bud, ring)
    }

    /**
     * Directly writes a custom raw hex string (e.g. from an HCI snoop log) to the connected device.
     */
    @SuppressLint("MissingPermission")
    suspend fun sendRawHex(device: BluetoothDevice, hexString: String): Boolean {
        return UniversalTwsEngine.sendRawHex(device, hexString)
    }

    /**
     * Triggers a query for battery telemetry.
     */
    @SuppressLint("MissingPermission")
    suspend fun queryBattery(device: BluetoothDevice) {
        UniversalTwsEngine.queryBattery(device)
    }

    @SuppressLint("MissingPermission")
    fun getCapabilityManifest(device: BluetoothDevice) = UniversalTwsEngine.getCapabilityManifest(device)

    @SuppressLint("MissingPermission")
    suspend fun sendProAncLevel(device: BluetoothDevice, level: String) = UniversalTwsEngine.setProAncLevel(device, level)

    @SuppressLint("MissingPermission")
    suspend fun sendWindNoiseReduction(device: BluetoothDevice, enabled: Boolean) = UniversalTwsEngine.setWindNoiseReduction(device, enabled)

    @SuppressLint("MissingPermission")
    suspend fun sendTransparencyLevel(device: BluetoothDevice, level: Int, voiceBoost: Boolean) = UniversalTwsEngine.setTransparencyLevel(device, level, voiceBoost)

    @SuppressLint("MissingPermission")
    suspend fun sendInEarDetection(device: BluetoothDevice, enabled: Boolean) = UniversalTwsEngine.setInEarDetection(device, enabled)

    @SuppressLint("MissingPermission")
    suspend fun sendMultipoint(device: BluetoothDevice, enabled: Boolean) = UniversalTwsEngine.setMultipoint(device, enabled)

    @SuppressLint("MissingPermission")
    suspend fun sendConversationalAwareness(device: BluetoothDevice, enabled: Boolean) = UniversalTwsEngine.setConversationalAwareness(device, enabled)

    @SuppressLint("MissingPermission")
    suspend fun sendPinchSpeed(device: BluetoothDevice, speed: String) = UniversalTwsEngine.setPinchSpeed(device, speed)

    @SuppressLint("MissingPermission")
    suspend fun sendStemSlide(device: BluetoothDevice, enabled: Boolean) = UniversalTwsEngine.setStemSlide(device, enabled)

    @SuppressLint("MissingPermission")
    suspend fun runPersonalizedAnc(device: BluetoothDevice) = UniversalTwsEngine.runPersonalizedAnc(device)

    @SuppressLint("MissingPermission")
    suspend fun runEarbudFitTest(device: BluetoothDevice) = UniversalTwsEngine.runEarbudFitTest(device)

    fun sendDolbyAtmos(enabled: Boolean, scenario: String = "SMART", environment: String = "INDOOR") = UniversalTwsEngine.setDolbyAtmos(enabled, scenario, environment)

    fun sendMonoAudio(enabled: Boolean) = UniversalTwsEngine.setMonoAudio(enabled)

    fun sendChannelBalance(balance: Float) = UniversalTwsEngine.setChannelBalance(balance)

    fun getEstimatedSplDb(): Float = UniversalTwsEngine.getEstimatedSplDb()

    @SuppressLint("MissingPermission")
    suspend fun sendHiResMode(device: BluetoothDevice, enabled: Boolean) = UniversalTwsEngine.setHiResMode(device, enabled)

    @SuppressLint("MissingPermission")
    fun sendForceHighBitrateAac(device: BluetoothDevice, enabled: Boolean) = UniversalTwsEngine.forceHighBitrateAac(device, enabled)

    suspend fun playHearingTestTone(freqHz: Int, ear: String, volumeDb: Float) = UniversalTwsEngine.playHearingTestTone(freqHz, ear, volumeDb)

    fun stopHearingTestTone() = UniversalTwsEngine.stopHearingTestTone()

    fun recordHearingResult(ear: String, freqHz: Int, heardDb: Float) = UniversalTwsEngine.recordHearingResult(ear, freqHz, heardDb)

    fun applyHearingAccommodations(preset: String, intensity: String) = UniversalTwsEngine.applyHearingAccommodations(preset, intensity)

    fun playMindRelax(soundscape: String, durationMinutes: Int = 0) = UniversalTwsEngine.playMindRelax(soundscape, durationMinutes)

    fun stopMindRelax() = UniversalTwsEngine.stopMindRelax()
}
