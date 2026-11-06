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

    val diagnosticLogs: StateFlow<List<DiagnosticLogEntry>>
        get() = UniversalTwsEngine.diagnosticLogs

    val discoveredServices: StateFlow<Map<String, List<GattServiceDiagnostic>>>
        get() = UniversalTwsEngine.discoveredServices

    val lastRawPackets: StateFlow<Map<String, String>>
        get() = UniversalTwsEngine.lastRawPackets

    val connectionStatus: StateFlow<Map<String, String>>
        get() = UniversalTwsEngine.connectionStatus

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
     * Sends Touch Gesture Configuration: "bud:gesture:action"
     */
    @SuppressLint("MissingPermission")
    suspend fun sendTouchControl(device: BluetoothDevice, config: String): Boolean {
        val parts = config.split(":")
        if (parts.size != 3) return false
        val (bud, gesture, action) = parts
        val packet = RealmeProtocolParser.buildTouchGestureCommand(bud, gesture, action)
        return UniversalTwsEngine.sendRawHex(device, HexUtils.formatHex(packet))
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
}
