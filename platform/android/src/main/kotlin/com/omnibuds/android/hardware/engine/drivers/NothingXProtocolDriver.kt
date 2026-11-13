package com.omnibuds.android.hardware.engine.drivers

import com.omnibuds.android.hardware.protocol.AncByteMapping
import com.omnibuds.android.hardware.protocol.HexUtils
import com.omnibuds.android.hardware.protocol.TwsBatteryReport

/**
 * Hardware-accurate, dedicated 1:1 Nothing X protocol driver.
 *
 * Implements official Nothing OS & CMF firmware framing across:
 * - CMF Buds 1 (Model B168)
 * - CMF Buds Pro / Pro 2
 * - CMF Neckband Pro
 * - Nothing Ear / Ear (a) / Ear (2) / Ear (1)
 *
 * Frame Format:
 * [Header: 0x55, 0x60] [Version: 0x01] [Cmd: 1B] [SubCmd: 1B] [Len: 1B] [Payload...] [CRC16: 2B]
 */
class NothingXProtocolDriver : IVendorDriver {

    override val driverName: String = "Nothing X Protocol Driver"

    override val preferredTransport: TransportPreference = TransportPreference.BLE_FIRST

    companion object {
        val NOTHING_HEADER = byteArrayOf(0x55.toByte(), 0x60.toByte(), 0x01.toByte())

        // Command & Subcommand opcodes
        const val CMD_BATTERY = 0x07
        const val SUB_BATTERY = 0xC0

        const val CMD_NOISE_CONTROL = 0x0F
        const val SUB_NOISE_CONTROL = 0xF0

        const val CMD_EQ = 0x0D
        const val SUB_EQ_PRESET = 0xE0
        const val SUB_EQ_3BAND = 0xE1

        const val CMD_ULTRA_BASS = 0x19
        const val SUB_ULTRA_BASS = 0xA0

        const val CMD_LOW_LAG = 0x11
        const val SUB_LOW_LAG = 0xB0

        const val CMD_IN_EAR = 0x15
        const val SUB_IN_EAR = 0xA0

        const val CMD_DUAL_CONN = 0x17
        const val SUB_DUAL_CONN = 0xB0

        const val CMD_FIND_MY_BUDS = 0x1A
        const val SUB_FIND_MY_BUDS = 0xC0

        const val CMD_TOUCH_GESTURES = 0x18
        const val SUB_TOUCH_GESTURES = 0xD0

        // Noise Control modes (real Nothing X values)
        const val MODE_ANC_HIGH = 0x01
        const val MODE_ANC_MID = 0x02
        const val MODE_ANC_LOW = 0x03
        const val MODE_ANC_ADAPTIVE = 0x04
        const val MODE_OFF = 0x05
        const val MODE_TRANSPARENCY = 0x07

        private val seqCounter = java.util.concurrent.atomic.AtomicInteger(0xCD)

        fun nextSeq(): Int = seqCounter.getAndIncrement() and 0xFF

        // CRC-16/MODBUS: init 0xFFFF, reflected poly 0xA001.
        fun calculateCrc16(data: ByteArray): Int {
            var crc = 0xFFFF
            for (b in data) {
                crc = crc xor (b.toInt() and 0xFF)
                for (i in 0 until 8) {
                    crc = if ((crc and 0x0001) != 0) {
                        (crc shr 1) xor 0xA001
                    } else {
                        crc shr 1
                    }
                }
            }
            return crc and 0xFFFF
        }
    }

    override fun matches(
        deviceName: String?,
        macAddress: String,
        serviceUuids: List<String>,
    ): Boolean {
        val name = deviceName?.lowercase()?.trim() ?: ""
        if (name.contains("cmf") || name.contains("nothing") || name.contains("b168") ||
            name.contains("ear (1)") || name.contains("ear (2)") || name.contains("ear (a)") ||
            name.contains("ear open")
        ) {
            return true
        }

        val servicesLower = serviceUuids.map { it.lowercase() }
        if (servicesLower.any { it.contains("fee0") || it.contains("fee1") || it.contains("5560") }) {
            return true
        }

        return false
    }

    /**
     * Builds standard Nothing X frame with length prefix and CRC16 checksum.
     */
    fun buildNothingFrame(
        cmd: Int,
        subCmd: Int,
        payload: ByteArray = ByteArray(0),
        seq: Int = nextSeq(),
    ): ByteArray {
        val body = byteArrayOf(
            0x55.toByte(),
            0x60.toByte(),
            0x01.toByte(),
            (cmd and 0xFF).toByte(),
            (subCmd and 0xFF).toByte(),
            (payload.size and 0xFF).toByte(),
            ((payload.size shr 8) and 0xFF).toByte(),
            (seq and 0xFF).toByte(),
        ) + payload
        val crc = calculateCrc16(body)
        val crcBytes = byteArrayOf(
            (crc and 0xFF).toByte(),
            ((crc shr 8) and 0xFF).toByte(),
        )
        return body + crcBytes
    }

    // --- Battery Read Command ---
    override fun buildBatteryQueryCommand(): ByteArray {
        return buildNothingFrame(CMD_BATTERY, SUB_BATTERY, ByteArray(0))
    }

    override fun buildStateQueryCommands(): List<ByteArray> = listOf(
        buildNothingFrame(CMD_BATTERY, 0xC0, ByteArray(0)),
        buildNothingFrame(CMD_NOISE_CONTROL, 0xC0, ByteArray(0)),
        buildNothingFrame(0x03, 0xC0, ByteArray(0)),
        buildNothingFrame(0x04, 0xC0, ByteArray(0)),
        buildNothingFrame(0x09, 0xC0, ByteArray(0)),
        buildNothingFrame(0x40, 0xC0, ByteArray(0)),
        buildNothingFrame(0x1E, 0xC0, ByteArray(0)),
        buildNothingFrame(0x1F, 0xC0, ByteArray(0)),
    )

    // --- Noise Control Command ---
    override fun buildAncCommand(mode: String): ByteArray {
        val upper = mode.uppercase().trim()
        val modeByte = when {
            upper.contains("HIGH") || upper.contains("42DB") || upper.contains("50DB") -> MODE_ANC_HIGH
            upper.contains("MID") -> MODE_ANC_MID
            upper.contains("LOW") && !upper.contains("LOW LAG") -> MODE_ANC_LOW
            upper.contains("ADAPTIVE") -> MODE_ANC_ADAPTIVE
            upper == "ANC" || upper == "ANC_ON" || upper == "ACTIVE NOISE CANCELLATION" || upper == "NOISE_CANCELLATION" ->
                MODE_ANC_HIGH
            upper.contains("TRANSPARENCY") || upper.contains("AMBIENT") -> MODE_TRANSPARENCY
            upper == "OFF" || upper == "NORMAL" -> MODE_OFF
            else -> MODE_OFF
        }
        return buildNothingFrame(
            CMD_NOISE_CONTROL,
            SUB_NOISE_CONTROL,
            byteArrayOf(0x01, modeByte.toByte(), 0x00),
        )
    }

    // --- Equalizer Command ---
    override fun buildEqualizerCommand(preset: String): ByteArray {
        val presetByte: Byte = when (preset.uppercase().trim()) {
            "DIRAC OPTEO", "DIRAC", "OPTEO" -> 0x00
            "BALANCED", "FLAT" -> 0x01
            "MORE BASS", "BASS_BOOST", "BASS" -> 0x02
            "MORE TREBLE", "TREBLE_BOOST", "TREBLE" -> 0x03
            "VOICE", "VOCAL", "CLEAR_VOCAL" -> 0x04
            "CUSTOM" -> 0x05
            else -> 0x00
        }
        return buildNothingFrame(CMD_EQ, SUB_EQ_PRESET, byteArrayOf(presetByte))
    }

    /**
     * Builds interactive 3-band Bass / Mid / Treble tuning frame (-6dB to +6dB).
     */
    fun buildCustom3BandEqCommand(bass: Int, mid: Int, treble: Int): ByteArray {
        val b = bass.coerceIn(-6, 6).toByte()
        val m = mid.coerceIn(-6, 6).toByte()
        val t = treble.coerceIn(-6, 6).toByte()
        return buildNothingFrame(CMD_EQ, SUB_EQ_3BAND, byteArrayOf(b, m, t))
    }

    // --- Ultra Bass Technology 2.0 Command ---
    fun buildUltraBassCommand(enabled: Boolean, level: Int): ByteArray {
        val toggleByte: Byte = if (enabled) 0x01 else 0x00
        val levelByte: Byte = level.coerceIn(1, 5).toByte()
        return buildNothingFrame(CMD_ULTRA_BASS, SUB_ULTRA_BASS, byteArrayOf(toggleByte, levelByte))
    }

    // --- Low Lag Mode (Game Mode) Command ---
    override fun buildGameModeCommand(enabled: Boolean): ByteArray {
        val flag: Byte = if (enabled) 0x01 else 0x00
        return buildNothingFrame(CMD_LOW_LAG, SUB_LOW_LAG, byteArrayOf(flag))
    }

    // --- In-Ear Detection Command ---
    fun buildInEarDetectionCommand(enabled: Boolean): ByteArray {
        val flag: Byte = if (enabled) 0x01 else 0x00
        return buildNothingFrame(CMD_IN_EAR, SUB_IN_EAR, byteArrayOf(flag))
    }

    // --- Dual Connection (Multipoint) Command ---
    fun buildDualConnectionCommand(enabled: Boolean): ByteArray {
        val flag: Byte = if (enabled) 0x01 else 0x00
        return buildNothingFrame(CMD_DUAL_CONN, SUB_DUAL_CONN, byteArrayOf(flag))
    }

    // --- Find My Earbuds Command ---
    fun buildFindMyBudsCommand(bud: String, ring: Boolean): ByteArray {
        val budByte: Byte = when (bud.uppercase().trim()) {
            "LEFT", "L" -> 0x01
            "RIGHT", "R" -> 0x02
            else -> 0x03 // Both
        }
        val ringByte: Byte = if (ring) 0x01 else 0x00
        return buildNothingFrame(CMD_FIND_MY_BUDS, SUB_FIND_MY_BUDS, byteArrayOf(budByte, ringByte))
    }

    // --- Touch Controls Command ---
    fun buildTouchControlCommand(bud: String, gesture: String, action: String): ByteArray {
        val budByte: Byte = if (bud.equals("Right", ignoreCase = true)) 0x02 else 0x01
        val gestureByte: Byte = when (gesture.lowercase().trim()) {
            "double_tap" -> 0x01
            "triple_tap" -> 0x02
            "tap_hold", "long_press" -> 0x03
            "double_tap_hold" -> 0x04
            else -> 0x01
        }
        val actionByte: Byte = when (action.uppercase().trim()) {
            "PLAY_PAUSE" -> 0x01
            "SKIP_FORWARD", "NEXT_TRACK" -> 0x02
            "SKIP_BACK", "PREVIOUS_TRACK" -> 0x03
            "VOICE_ASSISTANT" -> 0x04
            "SWITCH_ANC_TRANSPARENCY", "SWITCH_NOISE_CONTROL" -> 0x05
            "DECLINE_CALL", "REJECT_CALL" -> 0x06
            "VOLUME_UP" -> 0x07
            "VOLUME_DOWN" -> 0x08
            "LOW_LAG_MODE", "GAME_MODE" -> 0x09
            "NONE", "NO_ACTION" -> 0x00
            else -> 0x00
        }
        return buildNothingFrame(CMD_TOUCH_GESTURES, SUB_TOUCH_GESTURES, byteArrayOf(budByte, gestureByte, actionByte))
    }

    /**
     * Parses incoming Nothing X byte payload into split Left, Right, and Case battery report.
     *
     * Rules:
     * - Left and Right: bits 0-6 = 0..100%, bit 7 = isCharging
     * - Case: bits 0-6 = 1..100%, bit 7 = isCharging.
     *   IF case byte is 0, 0x7F, 0xFF, or not in 1..100 -> case is strictly NULL (Case Ring must be hidden)!
     */
    override fun parseBatteryReport(bytes: ByteArray): TwsBatteryReport? {
        if (bytes.size < 11) return null

        for (i in 0..bytes.size - 11) {
            if (bytes[i] == 0x55.toByte() && bytes[i + 1] == 0x60.toByte() && bytes[i + 2] == 0x01.toByte()) {
                val cmd = bytes[i + 3].toInt() and 0xFF
                val subCmd = bytes[i + 4].toInt() and 0xFF

                val isBattery = cmd == CMD_BATTERY || cmd == 0x87 || cmd == 0x08 || cmd == 0x88 ||
                    (cmd == 0x07 && subCmd == SUB_BATTERY)
                if (!isBattery) continue

                val payloadStart = i + 8
                // Tolerate an optional status/count prefix before the raw triplet.
                for (offset in 0..2) {
                    val p = payloadStart + offset
                    if (p + 3 > bytes.size) break

                    val rawL = bytes[p].toInt() and 0xFF
                    val rawR = bytes[p + 1].toInt() and 0xFF
                    val rawC = bytes[p + 2].toInt() and 0xFF

                    val left = (rawL and 0x7F).takeIf { it in 0..100 }
                    val right = (rawR and 0x7F).takeIf { it in 0..100 }
                    val case = (rawC and 0x7F).takeIf { it in 1..100 }

                    if (left != null || right != null || case != null) {
                        return TwsBatteryReport(
                            left = left,
                            right = right,
                            case = case,
                            leftCharging = left != null && (rawL and 0x80) != 0,
                            rightCharging = right != null && (rawR and 0x80) != 0,
                            caseCharging = case != null && (rawC and 0x80) != 0,
                            caseIsLive = case != null,
                            rawHex = HexUtils.formatHex(bytes),
                            sourceProtocol = "Nothing X (0x07 C0)",
                        )
                    }
                }
            }
        }
        return null
    }

    override fun parseAncModeNotification(bytes: ByteArray, mapping: AncByteMapping): String? {
        if (bytes.size < 11) return null
        for (i in 0..bytes.size - 11) {
            if (bytes[i] == 0x55.toByte() && bytes[i + 1] == 0x60.toByte() && bytes[i + 2] == 0x01.toByte()) {
                val cmd = bytes[i + 3].toInt() and 0xFF
                val sub = bytes[i + 4].toInt() and 0xFF
                if (cmd == CMD_NOISE_CONTROL && (sub == SUB_NOISE_CONTROL || sub == 0xC0)) {
                    val payloadStart = i + 8
                    if (payloadStart + 2 < bytes.size) {
                        return when (bytes[payloadStart + 1].toInt() and 0xFF) {
                            MODE_ANC_HIGH, MODE_ANC_MID, MODE_ANC_LOW, MODE_ANC_ADAPTIVE -> "ANC"
                            MODE_TRANSPARENCY -> "TRANSPARENCY"
                            MODE_OFF -> "OFF"
                            else -> null
                        }
                    }
                }
            }
        }
        return null
    }

    override fun isAncAck(bytes: ByteArray): Boolean {
        if (bytes.size < 6) return false
        for (i in 0..bytes.size - 6) {
            if (bytes[i] == 0x55.toByte() && bytes[i + 1] == 0x60.toByte() && bytes[i + 2] == 0x01.toByte()) {
                val cmd = bytes[i + 3].toInt() and 0xFF
                if (cmd == CMD_NOISE_CONTROL || cmd == 0x8F) {
                    return true
                }
            }
        }
        return false
    }
}
