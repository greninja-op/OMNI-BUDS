package com.omnibuds.android.hardware.engine.drivers

import com.omnibuds.android.hardware.protocol.HexUtils
import com.omnibuds.android.hardware.protocol.TwsBatteryReport

/**
 * Driver for Nothing and CMF Earbuds (Nothing OS / CMF SPP & BLE Protocol).
 *
 * Framing:
 * 0x55 0x60 0x01 | Command (1B) | Subcommand (1B) | Payload...
 */
class NothingEarbudsDriver : IVendorDriver {

    override val driverName: String = "Nothing / CMF Earbuds"

    override val preferredTransport: TransportPreference = TransportPreference.BLE_FIRST

    companion object {
        val NOTHING_HEADER = byteArrayOf(0x55.toByte(), 0x60.toByte(), 0x01.toByte())

        const val CMD_NOISE_CONTROL = 0x0F
        const val SUB_NOISE_CONTROL = 0xF0

        const val CMD_BATTERY_QUERY = 0x07
        const val SUB_BATTERY_QUERY = 0xC0

        const val CMD_EQ = 0x0D
        const val SUB_EQ = 0xE0

        const val CMD_LOW_LATENCY = 0x11
        const val SUB_LOW_LATENCY = 0xB0
    }

    override fun matches(
        deviceName: String?,
        macAddress: String,
        serviceUuids: List<String>,
    ): Boolean {
        val name = deviceName?.lowercase() ?: ""
        if (name.contains("nothing") || name.contains("cmf") || name.contains("ear (1)") ||
            name.contains("ear (2)") || name.contains("ear (a)")
        ) {
            return true
        }

        val servicesLower = serviceUuids.map { it.lowercase() }
        if (servicesLower.any { it.contains("fee0") || it.contains("fee1") || it.contains("5560") }) {
            return true
        }

        return false
    }

    fun buildNothingFrame(cmd: Int, subCmd: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        val header = byteArrayOf(
            0x55.toByte(),
            0x60.toByte(),
            0x01.toByte(),
            (cmd and 0xFF).toByte(),
            (subCmd and 0xFF).toByte(),
        )
        return header + payload
    }

    override fun buildAncCommand(mode: String): ByteArray {
        val (levelByte, modeByte) = when (mode.uppercase()) {
            "ANC", "ANC_ON", "NOISE_CANCELLATION" -> Pair(0x03.toByte(), 0x01.toByte()) // High ANC
            "TRANSPARENCY", "AMBIENT" -> Pair(0x00.toByte(), 0x02.toByte())
            "OFF", "NORMAL" -> Pair(0x00.toByte(), 0x00.toByte())
            else -> Pair(0x00.toByte(), 0x00.toByte())
        }
        return buildNothingFrame(CMD_NOISE_CONTROL, SUB_NOISE_CONTROL, byteArrayOf(levelByte, modeByte))
    }

    override fun buildEqualizerCommand(preset: String): ByteArray {
        val presetByte: Byte = when (preset.uppercase()) {
            "FLAT", "BALANCED" -> 0x00
            "BASS_BOOST", "BASS" -> 0x01
            "VOCAL", "CLEAR_VOCAL" -> 0x02
            "TREBLE_BOOST", "BRIGHT" -> 0x03
            else -> 0x00
        }
        return buildNothingFrame(CMD_EQ, SUB_EQ, byteArrayOf(presetByte))
    }

    override fun buildGameModeCommand(enabled: Boolean): ByteArray {
        val flag: Byte = if (enabled) 0x01 else 0x00
        return buildNothingFrame(CMD_LOW_LATENCY, SUB_LOW_LATENCY, byteArrayOf(flag))
    }

    override fun buildBatteryQueryCommand(): ByteArray {
        return buildNothingFrame(CMD_BATTERY_QUERY, SUB_BATTERY_QUERY, byteArrayOf(0x00.toByte()))
    }

    override fun parseBatteryReport(bytes: ByteArray): TwsBatteryReport? {
        if (bytes.size < 6) return null

        for (i in 0..bytes.size - 6) {
            if (bytes[i] == 0x55.toByte() && bytes[i + 1] == 0x60.toByte() && bytes[i + 2] == 0x01.toByte()) {
                val cmd = bytes[i + 3].toInt() and 0xFF
                if (cmd == CMD_BATTERY_QUERY || cmd == 0x08) {
                    val pStart = i + 5
                    if (pStart + 3 <= bytes.size) {
                        val rawL = bytes[pStart].toInt() and 0xFF
                        val rawR = bytes[pStart + 1].toInt() and 0xFF
                        val rawC = bytes[pStart + 2].toInt() and 0xFF

                        val left = (rawL and 0x7F).takeIf { it in 0..100 }
                        val right = (rawR and 0x7F).takeIf { it in 0..100 }
                        val case = (rawC and 0x7F).takeIf { it in 0..100 }

                        if (left != null || right != null || case != null) {
                            return TwsBatteryReport(
                                left = left,
                                right = right,
                                case = case,
                                leftCharging = (rawL and 0x80) != 0,
                                rightCharging = (rawR and 0x80) != 0,
                                caseCharging = (rawC and 0x80) != 0,
                                rawHex = HexUtils.formatHex(bytes),
                            )
                        }
                    }
                }
            }
        }
        return null
    }
}
