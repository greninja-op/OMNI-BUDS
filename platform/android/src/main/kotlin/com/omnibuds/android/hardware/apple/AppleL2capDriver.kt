@file:Suppress("DEPRECATION")

package com.omnibuds.android.hardware.apple

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Build
import android.util.Log
import com.omnibuds.android.hardware.capability.PinchSpeed
import com.omnibuds.android.hardware.protocol.HexUtils
import com.omnibuds.android.hardware.protocol.TwsBatteryReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Direct Apple L2CAP Socket Driver (PSM 0x1001).
 *
 * Unlocks native Apple AirPods and Beats Pro controls on Android:
 * - PSM 0x1001 L2CAP direct connection channel
 * - ANC, Transparency, Adaptive Audio, and Normal/Off switching
 * - Conversational Awareness toggle
 * - Stem Pinch Speed / hold duration adjustment (Default / Slow / Slowest)
 * - True 1% Left / Right / Case battery telemetry
 */
class AppleL2capDriver(private val scope: CoroutineScope) {

    companion object {
        private const val TAG = "AppleL2capDriver"
        const val AIRPODS_L2CAP_PSM = 0x1001

        // Apple Accessory Protocol (AAP) Packet Identifiers over L2CAP
        val AAP_MAGIC = byteArrayOf(0x04, 0x00, 0x04, 0x00)
    }

    private val sockets = ConcurrentHashMap<String, BluetoothSocket>()
    private val workerJobs = ConcurrentHashMap<String, Job>()

    private val _batteryState = MutableStateFlow<Map<String, TwsBatteryReport>>(emptyMap())
    val batteryState: StateFlow<Map<String, TwsBatteryReport>> = _batteryState.asStateFlow()

    private val _ancState = MutableStateFlow<Map<String, String>>(emptyMap())
    val ancState: StateFlow<Map<String, String>> = _ancState.asStateFlow()

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        val address = device.address
        if (sockets.containsKey(address) && sockets[address]?.isConnected == true) return

        workerJobs[address]?.cancel()
        workerJobs[address] = scope.launch(Dispatchers.IO) {
            var socket: BluetoothSocket? = null
            try {
                Log.i(TAG, "Opening L2CAP channel PSM 0x1001 to Apple device: $address")
                socket = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        device.createInsecureL2capChannel(AIRPODS_L2CAP_PSM)
                    } catch (_: Exception) {
                        device.createL2capChannel(AIRPODS_L2CAP_PSM)
                    }
                } else {
                    val method = device.javaClass.getMethod("createL2capSocket", Int::class.javaPrimitiveType)
                    method.invoke(device, AIRPODS_L2CAP_PSM) as BluetoothSocket
                }

                socket.connect()
                sockets[address] = socket
                Log.i(TAG, "L2CAP socket connected to $address (PSM 0x1001)")

                val inStream = socket.inputStream
                val outStream = socket.outputStream

                // Send initial AAP Handshake
                sendHandshake(outStream)

                val buffer = ByteArray(512)
                while (isActive && socket.isConnected) {
                    if (inStream.available() > 0) {
                        val read = inStream.read(buffer)
                        if (read > 0) {
                            val packet = buffer.copyOf(read)
                            parseIncomingPacket(address, packet)
                        }
                    }
                    delay(50)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Apple L2CAP connection to $address closed/unavailable: ${e.message}")
            } finally {
                try { socket?.close() } catch (_: Exception) {}
                sockets.remove(address)
            }
        }
    }

    fun disconnect(address: String) {
        workerJobs[address]?.cancel()
        workerJobs.remove(address)
        try { sockets[address]?.close() } catch (_: Exception) {}
        sockets.remove(address)
    }

    private fun sendHandshake(out: OutputStream) {
        try {
            // AAP Handshake frame: 0x04 0x00 0x04 0x00 [0x01, 0x00, 0x02, 0x00]
            val handshake = byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x01, 0x00, 0x02, 0x00)
            out.write(handshake)
            out.flush()
        } catch (_: Exception) {}
    }

    /**
     * Sets Noise Control on AirPods:
     * mode: "ANC", "TRANSPARENCY", "ADAPTIVE", "OFF"
     */
    fun setNoiseControl(address: String, mode: String): Boolean {
        val socket = sockets[address] ?: return false
        if (!socket.isConnected) return false
        return try {
            val modeByte: Byte = when (mode.uppercase()) {
                "OFF", "NORMAL" -> 0x01
                "ANC", "NOISE_CANCELLATION" -> 0x02
                "TRANSPARENCY" -> 0x03
                "ADAPTIVE" -> 0x04
                else -> 0x01
            }
            // Apple Accessory Protocol ANC Frame: Magic(4B) + Opcode(0x0D, 0x00) + Length(0x01) + ModeByte
            val packet = byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x0D, 0x00, 0x01, modeByte)
            socket.outputStream.write(packet)
            socket.outputStream.flush()
            _ancState.value = _ancState.value + (address to mode)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send ANC to Apple L2CAP: ${e.message}")
            false
        }
    }

    /**
     * Sets Conversational Awareness on AirPods Pro 2 / Pro 3:
     */
    fun setConversationalAwareness(address: String, enabled: Boolean): Boolean {
        val socket = sockets[address] ?: return false
        return try {
            val flag: Byte = if (enabled) 0x01 else 0x00
            val packet = byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x1E, 0x00, 0x01, flag)
            socket.outputStream.write(packet)
            socket.outputStream.flush()
            true
        } catch (_: Exception) { false }
    }

    /**
     * Sets Stem Pinch Speed / Hold Duration:
     */
    fun setPinchSpeed(address: String, speed: PinchSpeed): Boolean {
        val socket = sockets[address] ?: return false
        return try {
            val speedByte: Byte = when (speed) {
                PinchSpeed.DEFAULT -> 0x00
                PinchSpeed.SLOW -> 0x01
                PinchSpeed.SLOWEST -> 0x02
            }
            val packet = byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x22, 0x00, 0x01, speedByte)
            socket.outputStream.write(packet)
            socket.outputStream.flush()
            true
        } catch (_: Exception) { false }
    }

    private fun parseIncomingPacket(address: String, packet: ByteArray) {
        if (packet.size < 6) return
        val opcode = (packet[4].toInt() and 0xFF) or ((packet[5].toInt() and 0xFF) shl 8)

        when (opcode) {
            // Battery Status Opcode
            0x0004 -> {
                if (packet.size >= 10) {
                    val left = packet[6].toInt() and 0x7F
                    val leftChg = (packet[6].toInt() and 0x80) != 0
                    val right = packet[7].toInt() and 0x7F
                    val rightChg = (packet[7].toInt() and 0x80) != 0
                    val case = packet[8].toInt() and 0x7F
                    val caseChg = (packet[8].toInt() and 0x80) != 0

                    val report = TwsBatteryReport(
                        left = left.takeIf { it in 0..100 },
                        right = right.takeIf { it in 0..100 },
                        case = case.takeIf { it in 0..100 },
                        leftCharging = leftChg,
                        rightCharging = rightChg,
                        caseCharging = caseChg,
                        rawHex = HexUtils.formatHex(packet),
                        sourceProtocol = "Apple L2CAP (0x1001)",
                    )
                    _batteryState.value = _batteryState.value + (address to report)
                }
            }
            // ANC Mode Status Opcode
            0x000D -> {
                if (packet.size >= 7) {
                    val modeByte = packet[6].toInt() and 0xFF
                    val mode = when (modeByte) {
                        0x01 -> "OFF"
                        0x02 -> "ANC"
                        0x03 -> "TRANSPARENCY"
                        0x04 -> "ADAPTIVE"
                        else -> null
                    }
                    if (mode != null) {
                        _ancState.value = _ancState.value + (address to mode)
                    }
                }
            }
        }
    }
}
