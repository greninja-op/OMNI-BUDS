package com.omnibuds.android.hardware.engine.drivers

import com.omnibuds.android.hardware.protocol.HexUtils
import com.omnibuds.android.hardware.protocol.TwsBatteryReport

/**
 * Driver for Xiaomi, Redmi, EarFun, and SoundPEATS (Airoha RACE Protocol).
 *
 * RACE (Relayer and Communication Protocol) framing:
 * 0x05 0x5A | Length (2B, LE) | Opcode / Type (2B) | Payload...
 */
class XiaomiAirohaRaceDriver : IVendorDriver {

    override val driverName: String = "Xiaomi / Redmi / EarFun / SoundPEATS (Airoha RACE)"

    override val preferredTransport: TransportPreference = TransportPreference.BLE_FIRST

    companion object {
        const val RACE_MAGIC_0: Byte = 0x05
        const val RACE_MAGIC_1: Byte = 0x5A

        const val RACE_TYPE_ANC = 0x0D0A
        const val RACE_TYPE_BATTERY_QUERY = 0x020A
        const val RACE_TYPE_BATTERY_RESP = 0x028A
        const val RACE_TYPE_EQ = 0x080A
    }

    private val knownOuiPrefixes = listOf(
        "78:02:F8", // Xiaomi / Redmi
        "84:F3:EB",
        "50:EC:50",
        "34:80:0D",
        "00:1E:C0", // EarFun / Airoha
        "4C:24:98", // SoundPEATS
    )

    private val knownGattUuids = listOf(
        "00000500-0000-1000-8000-00805f9b34fb",
        "00004558-0000-1000-8000-00805f9b34fb",
        "0500",
        "4558",
    )

    override fun matches(
        deviceName: String?,
        macAddress: String,
        serviceUuids: List<String>,
    ): Boolean {
        val name = deviceName?.lowercase() ?: ""
        if (name.contains("redmi") || name.contains("xiaomi") || name.contains("poco") ||
            name.contains("earfun") || name.contains("soundpeats") || name.contains("airoha") ||
            name.contains("edifier") || name.contains("1more") || name.contains("qcy") ||
            name.contains("baseus") || name.contains("ugreen") || name.contains("tribit")
        ) {
            return true
        }

        val macUpper = macAddress.uppercase()
        if (knownOuiPrefixes.any { macUpper.startsWith(it) }) {
            return true
        }

        val servicesLower = serviceUuids.map { it.lowercase() }
        if (knownGattUuids.any { known -> servicesLower.any { it.contains(known) } }) {
            return true
        }

        return false
    }

    /**
     * Builds a RACE framed packet:
     * 05 5A [len_lo] [len_hi] [type_lo] [type_hi] [payload...]
     */
    fun buildRaceFrame(opcode: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        val totalPayloadLen = 2 + payload.size
        val lenLo = (totalPayloadLen and 0xFF).toByte()
        val lenHi = ((totalPayloadLen shr 8) and 0xFF).toByte()
        val opLo = (opcode and 0xFF).toByte()
        val opHi = ((opcode shr 8) and 0xFF).toByte()

        val header = byteArrayOf(
            RACE_MAGIC_0,
            RACE_MAGIC_1,
            lenLo,
            lenHi,
            opLo,
            opHi,
        )
        return header + payload
    }

    override fun buildAncCommand(mode: String): ByteArray {
        val modeVal: Byte = when (mode.uppercase()) {
            "ANC", "ANC_ON", "NOISE_CANCELLATION" -> 0x01
            "TRANSPARENCY", "AMBIENT" -> 0x02
            "OFF", "NORMAL" -> 0x00
            else -> 0x00
        }
        return buildRaceFrame(RACE_TYPE_ANC, byteArrayOf(modeVal, 0x00))
    }

    override fun buildEqualizerCommand(preset: String): ByteArray {
        val eqVal: Byte = when (preset.uppercase()) {
            "FLAT", "BALANCED" -> 0x00
            "BASS_BOOST", "BASS" -> 0x01
            "VOCAL", "CLEAR_VOCAL" -> 0x02
            "TREBLE_BOOST", "BRIGHT" -> 0x03
            "HARMAN_TARGET" -> 0x04
            "WARMTH" -> 0x05
            else -> 0x00
        }
        return buildRaceFrame(RACE_TYPE_EQ, byteArrayOf(eqVal))
    }

    override fun buildBatteryQueryCommand(): ByteArray {
        return buildRaceFrame(RACE_TYPE_BATTERY_QUERY, byteArrayOf(0x00))
    }

    override fun parseBatteryReport(bytes: ByteArray): TwsBatteryReport? {
        if (bytes.size < 6) return null

        for (i in 0..bytes.size - 6) {
            if (bytes[i] == RACE_MAGIC_0 && bytes[i + 1] == RACE_MAGIC_1) {
                val len = (bytes[i + 2].toInt() and 0xFF) or ((bytes[i + 3].toInt() and 0xFF) shl 8)
                val opLo = bytes[i + 4].toInt() and 0xFF
                val opHi = bytes[i + 5].toInt() and 0xFF
                val opcode = (opHi shl 8) or opLo

                if (opcode == RACE_TYPE_BATTERY_RESP || opcode == RACE_TYPE_BATTERY_QUERY) {
                    val pStart = i + 6
                    val pLen = minOf(len - 2, bytes.size - pStart)
                    if (pLen >= 3) {
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
                                rawHex = HexUtils.formatHex(bytes.copyOfRange(i, minOf(bytes.size, i + 6 + pLen))),
                            )
                        }
                    }
                }
            }
        }
        return null
    }
}
