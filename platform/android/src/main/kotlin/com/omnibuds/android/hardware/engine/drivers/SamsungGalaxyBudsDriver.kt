package com.omnibuds.android.hardware.engine.drivers

import com.omnibuds.android.hardware.protocol.HexUtils
import com.omnibuds.android.hardware.protocol.TwsBatteryReport

/**
 * Driver for Samsung Galaxy Buds ecosystem (Buds, Buds+, Live, Pro, Buds2, Buds2 Pro, FE).
 *
 * Framing:
 * 0xFD | Length (2B, LE) | MsgID (1B) | Payload... | CRC16 (2B, LE) | 0xDD
 */
class SamsungGalaxyBudsDriver : IVendorDriver {

    override val driverName: String = "Samsung Galaxy Buds"

    override val preferredTransport: TransportPreference = TransportPreference.RFCOMM_FIRST

    companion object {
        const val START_BYTE: Byte = 0xFD.toByte()
        const val END_BYTE: Byte = 0xDD.toByte()

        const val MSG_SET_NOISE_CONTROL: Byte = 0x78.toByte()
        const val MSG_SET_EQ: Byte = 0x65.toByte()
        const val MSG_EXTENDED_STATUS_REQ: Byte = 0x61.toByte()
        const val MSG_EXTENDED_STATUS_RESP: Byte = 0x61.toByte()
    }

    private val knownOuiPrefixes = listOf(
        "24:4B:03", // Samsung Electronics
        "50:32:37",
        "68:EB:AE",
        "78:B8:D6",
        "94:65:2D",
        "CC:07:AB",
        "F4:7B:5E",
    )

    override fun matches(
        deviceName: String?,
        macAddress: String,
        serviceUuids: List<String>,
    ): Boolean {
        val name = deviceName?.lowercase() ?: ""
        if (name.contains("galaxy buds") || name.contains("buds pro") || name.contains("buds live") ||
            name.contains("buds2") || name.contains("buds fe") || name.contains("gear iconx") ||
            name.contains("akg")
        ) {
            return true
        }

        val macUpper = macAddress.uppercase()
        if (knownOuiPrefixes.any { macUpper.startsWith(it) }) {
            return true
        }

        return false
    }

    /**
     * Builds a Galaxy Buds packet:
     * FD [len_lo] [len_hi] [msg_id] [payload] [crc_lo] [crc_hi] DD
     */
    fun buildBudsFrame(msgId: Byte, payload: ByteArray = ByteArray(0)): ByteArray {
        val bodyLen = 1 + payload.size
        val lenLo = (bodyLen and 0xFF).toByte()
        val lenHi = ((bodyLen shr 8) and 0xFF).toByte()

        val body = ByteArray(bodyLen)
        body[0] = msgId
        System.arraycopy(payload, 0, body, 1, payload.size)

        val crc = calculateCrc16(body)
        val crcLo = (crc and 0xFF).toByte()
        val crcHi = ((crc shr 8) and 0xFF).toByte()

        val packet = ByteArray(bodyLen + 6)
        packet[0] = START_BYTE
        packet[1] = lenLo
        packet[2] = lenHi
        System.arraycopy(body, 0, packet, 3, bodyLen)
        packet[packet.size - 3] = crcLo
        packet[packet.size - 2] = crcHi
        packet[packet.size - 1] = END_BYTE

        return packet
    }

    fun calculateCrc16(data: ByteArray): Int {
        var crc = 0x0000
        for (b in data) {
            var byteVal = (b.toInt() and 0xFF) shl 8
            for (i in 0 until 8) {
                if (((crc xor byteVal) and 0x8000) != 0) {
                    crc = (crc shl 1) xor 0x1021
                } else {
                    crc = crc shl 1
                }
                byteVal = byteVal shl 1
                crc = crc and 0xFFFF
            }
        }
        return crc
    }

    override fun buildAncCommand(mode: String): ByteArray {
        val modeByte: Byte = when (mode.uppercase()) {
            "ANC", "ANC_ON", "NOISE_CANCELLATION" -> 0x01
            "TRANSPARENCY", "AMBIENT" -> 0x02
            "OFF", "NORMAL" -> 0x00
            else -> 0x00
        }
        return buildBudsFrame(MSG_SET_NOISE_CONTROL, byteArrayOf(modeByte))
    }

    override fun buildEqualizerCommand(preset: String): ByteArray {
        val eqVal: Byte = when (preset.uppercase()) {
            "BASS_BOOST", "BASS" -> 0x00
            "FLAT", "BALANCED", "NORMAL" -> 0x05
            "CLEAR", "VOCAL" -> 0x03
            "TREBLE_BOOST", "BRIGHT" -> 0x04
            else -> 0x05
        }
        return buildBudsFrame(MSG_SET_EQ, byteArrayOf(eqVal))
    }

    override fun buildBatteryQueryCommand(): ByteArray {
        return buildBudsFrame(MSG_EXTENDED_STATUS_REQ)
    }

    override fun parseBatteryReport(bytes: ByteArray): TwsBatteryReport? {
        if (bytes.size < 8) return null

        for (i in 0..bytes.size - 8) {
            if (bytes[i] == START_BYTE) {
                val len = (bytes[i + 1].toInt() and 0xFF) or ((bytes[i + 2].toInt() and 0xFF) shl 8)
                val msgId = bytes[i + 3]

                val packetTotalLen = len + 6
                val hasEndByte = i + packetTotalLen <= bytes.size && bytes[i + packetTotalLen - 1] == END_BYTE

                if (msgId == MSG_EXTENDED_STATUS_RESP && i + 3 + len <= bytes.size && hasEndByte) {
                    val pStart = i + 4
                    val payload = bytes.copyOfRange(pStart, i + 3 + len)

                    // Samsung Extended Status layout:
                    // payload[0] = batteryLeft (0-100)
                    // payload[1] = batteryRight (0-100)
                    // payload[2] = batteryCase (0-100)
                    // payload[3] = charging status flags (bit 0 = left, bit 1 = right, bit 2 = case)
                    if (payload.size >= 3) {
                        val rawL = payload[0].toInt() and 0xFF
                        val rawR = payload[1].toInt() and 0xFF
                        val rawC = if (payload.size > 2) payload[2].toInt() and 0xFF else 0xFF

                        val left = (rawL and 0x7F).takeIf { it in 0..100 }
                        val right = (rawR and 0x7F).takeIf { it in 0..100 }
                        val case = (rawC and 0x7F).takeIf { it in 0..100 }

                        val chgFlags = if (payload.size > 3) payload[3].toInt() and 0xFF else 0
                        val leftChg = (chgFlags and 0x01) != 0 || (rawL and 0x80) != 0
                        val rightChg = (chgFlags and 0x02) != 0 || (rawR and 0x80) != 0
                        val caseChg = (chgFlags and 0x04) != 0 || (rawC and 0x80) != 0

                        if (left != null || right != null || case != null) {
                            return TwsBatteryReport(
                                left = left,
                                right = right,
                                case = case,
                                leftCharging = leftChg,
                                rightCharging = rightChg,
                                caseCharging = caseChg,
                                rawHex = HexUtils.formatHex(bytes.copyOfRange(i, minOf(bytes.size, i + len + 6))),
                            )
                        }
                    }
                }
            }
        }
        return null
    }
}
