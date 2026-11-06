package com.omnibuds.android.hardware.engine.drivers

import com.omnibuds.android.hardware.protocol.HexUtils
import com.omnibuds.android.hardware.protocol.TwsBatteryReport

/**
 * Generic / Heuristic BLE Auto-Prober for Unknown Earbuds.
 *
 * Automatically matches any connected earphone that does not belong to a known brand profile,
 * enables notification subscription across custom GATT characteristics, and sends
 * heuristic probe frames to auto-detect hardware responses.
 */
class GenericBleProberDriver : IVendorDriver {

    override val driverName: String = "Generic / Heuristic Auto-Prober"

    override val preferredTransport: TransportPreference = TransportPreference.BLE_FIRST

    override fun matches(
        deviceName: String?,
        macAddress: String,
        serviceUuids: List<String>,
    ): Boolean {
        // Universal catch-all fallback driver
        return true
    }

    override fun buildAncCommand(mode: String): ByteArray {
        // Heuristic multi-profile frame: standard HeyMelody/BES framing as primary heuristic
        val modeVal: Byte = when (mode.uppercase()) {
            "ANC", "ANC_ON", "NOISE_CANCELLATION" -> 0x02
            "TRANSPARENCY", "AMBIENT" -> 0x04
            "OFF", "NORMAL" -> 0x01
            else -> 0x01
        }
        val header = byteArrayOf(0xAA.toByte(), 0x08, 0x00, 0x00, 0x04, 0x04, 0x01, 0x01, 0x00, modeVal)
        return header
    }

    override fun buildBatteryQueryCommand(): ByteArray {
        // Standard query battery opcode
        return byteArrayOf(0xAA.toByte(), 0x07, 0x00, 0x00, 0x01, 0x06, 0x01, 0x00, 0x00)
    }

    /**
     * Exposes Auto-Detect / Learn Mode probe frames for unknown earbuds.
     */
    fun generateLearnProbeFrames(): List<ByteArray> {
        return listOf(
            // 1. HeyMelody / BES battery query
            byteArrayOf(0xAA.toByte(), 0x07, 0x00, 0x00, 0x01, 0x06, 0x01, 0x00, 0x00),
            // 2. Airoha RACE battery query
            byteArrayOf(0x05, 0x5A, 0x03, 0x00, 0x0A, 0x02, 0x00),
            // 3. Nothing / CMF query
            byteArrayOf(0x55, 0x60, 0x01, 0x07, 0xC0.toByte(), 0x00),
            // 4. Samsung Buds extended status
            byteArrayOf(0xFD.toByte(), 0x01, 0x00, 0x61, 0x79, 0x10, 0xDD.toByte()),
            // 5. AT command query (HFP AT+IPHONEACCEV)
            "AT+IPHONEACCEV?\r\n".toByteArray(Charsets.US_ASCII),
        )
    }

    override fun parseBatteryReport(bytes: ByteArray): TwsBatteryReport? {
        if (bytes.isEmpty()) return null

        // 1. Single byte standard battery level (0-100)
        if (bytes.size == 1) {
            val level = bytes[0].toInt() and 0xFF
            if (level in 0..100) {
                return TwsBatteryReport(
                    left = level,
                    right = level,
                    case = null,
                    rawHex = HexUtils.formatHex(bytes),
                )
            }
        }

        // 2. Direct 3-byte triplet [Left, Right, Case]
        if (bytes.size == 3) {
            val rawL = bytes[0].toInt() and 0xFF
            val rawR = bytes[1].toInt() and 0xFF
            val rawC = bytes[2].toInt() and 0xFF
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

        return null
    }
}
