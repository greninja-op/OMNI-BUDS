package com.omnibuds.android.hardware.protocol

/**
 * Encapsulates packet framing, opcode definitions, and payload parsing for
 * Realme, OPPO, OnePlus, and HeyMelody-compatible TWS earphones.
 */
object RealmeProtocolParser {

    const val MAGIC_HEADER: Byte = 0xAA.toByte()

    // Opcodes (Little Endian in packets)
    const val CMD_HANDSHAKE = 0x0100
    const val CMD_QUERY_BATTERY = 0x0106
    const val CMD_BATTERY_RESP = 0x8106
    const val CMD_BATTERY_ALT_RESP = 0x0204
    const val CMD_SET_ANC = 0x0404
    const val CMD_SET_EQ = 0x0405
    const val CMD_SET_SPATIAL_AUDIO = 0x0422
    const val CMD_SET_SPATIAL_AUDIO_ALT = 0x0410
    const val CMD_SET_GAME_MODE = 0x0403
    const val CMD_SET_TOUCH_GESTURE = 0x0408

    /**
     * Builds an OPOv1 framed packet:
     * 0xAA | TotalLen (1B) | 0x00 0x00 | Cmd_Lo | Cmd_Hi | Seq | PLen_Lo | PLen_Hi | Payload...
     */
    fun buildFrame(cmd: Int, payload: ByteArray = ByteArray(0), seq: Byte = 1): ByteArray {
        val cmdLo = (cmd and 0xFF).toByte()
        val cmdHi = ((cmd shr 8) and 0xFF).toByte()
        val pLenLo = (payload.size and 0xFF).toByte()
        val pLenHi = ((payload.size shr 8) and 0xFF).toByte()
        val len = 7 + payload.size
        val header = byteArrayOf(
            MAGIC_HEADER,
            len.toByte(),
            0x00, 0x00,
            cmdLo, cmdHi,
            seq,
            pLenLo, pLenHi,
        )
        return header + payload
    }

    /**
     * Builds the payload to change ANC modes:
     * Mode: "ANC" -> 0x02, "OFF" -> 0x01, "TRANSPARENCY" -> 0x04
     * HeyMelody payload format: [0x01, 0x01, modeVal]
     */
    fun buildAncCommand(mode: String): ByteArray {
        val modeVal: Byte = when (mode.uppercase()) {
            "ANC", "ANC_ON", "NOISE_CANCELLATION" -> 0x02
            "TRANSPARENCY", "AMBIENT" -> 0x04
            "OFF", "NORMAL" -> 0x01
            else -> 0x01
        }
        return buildFrame(CMD_SET_ANC, byteArrayOf(0x01, 0x01, modeVal))
    }

    /**
     * Builds the payload to set Equalizer preset:
     * "FLAT" -> 0x00, "BASS_BOOST" -> 0x01, "VOCAL" -> 0x02, "TREBLE_BOOST" -> 0x03
     */
    fun buildEqualizerCommand(preset: String): ByteArray {
        val eqVal: Byte = when (preset.uppercase()) {
            "FLAT", "BALANCED" -> 0x00
            "BASS_BOOST", "BASS" -> 0x01
            "VOCAL", "CLEAR_VOCAL" -> 0x02
            "TREBLE_BOOST", "BRIGHT" -> 0x03
            else -> 0x00
        }
        return buildFrame(CMD_SET_EQ, byteArrayOf(0x01, 0x01, eqVal))
    }

    /**
     * Builds the payload for Spatial Audio:
     * true -> 0x01, false -> 0x00
     */
    fun buildSpatialAudioCommand(enabled: Boolean): ByteArray {
        val flag: Byte = if (enabled) 0x01 else 0x00
        return buildFrame(CMD_SET_SPATIAL_AUDIO, byteArrayOf(0x01, 0x01, flag))
    }

    /**
     * Builds the payload for Game Mode (Low Latency):
     */
    fun buildGameModeCommand(enabled: Boolean): ByteArray {
        val flag: Byte = if (enabled) 0x01 else 0x00
        return buildFrame(CMD_SET_GAME_MODE, byteArrayOf(0x28, flag))
    }

    /**
     * Builds the payload for Touch Gesture Configuration:
     * bud: "Left" / "Right"
     * gesture: "double_tap", "triple_tap", "long_press"
     * action: "PLAY_PAUSE", "NEXT_TRACK", "PREVIOUS_TRACK", "SWITCH_NOISE_CONTROL", "GAME_MODE", "VOICE_ASSISTANT", "VOLUME_UP", "VOLUME_DOWN", "NONE"
     */
    fun buildTouchGestureCommand(bud: String, gesture: String, action: String): ByteArray {
        val budId: Byte = if (bud.equals("Right", ignoreCase = true)) 0x02 else 0x01
        val gestureId: Byte = when (gesture.lowercase()) {
            "double_tap" -> 0x01
            "triple_tap" -> 0x02
            "long_press" -> 0x03
            else -> 0x01
        }
        val actionId: Byte = when (action.uppercase()) {
            "PLAY_PAUSE" -> 0x01
            "NEXT_TRACK" -> 0x02
            "PREVIOUS_TRACK" -> 0x03
            "SWITCH_NOISE_CONTROL", "ANC" -> 0x04
            "VOICE_ASSISTANT" -> 0x05
            "GAME_MODE" -> 0x06
            "VOLUME_UP" -> 0x07
            "VOLUME_DOWN" -> 0x08
            "NONE" -> 0x00
            else -> 0x01
        }
        return buildFrame(CMD_SET_TOUCH_GESTURE, byteArrayOf(0x01, budId, gestureId, actionId))
    }

    /**
     * Builds the battery query packet.
     */
    fun buildBatteryQuery(): ByteArray {
        return buildFrame(CMD_QUERY_BATTERY)
    }

    /**
     * Attempts to parse a multi-component battery response from raw incoming bytes.
     * Real packet structure:
     * AA 0F 00 00 06 81 01 08 00 [00] [03] [01 64] [02 28] [03 32]
     */
    fun parseBatteryReport(bytes: ByteArray): TwsBatteryReport? {
        if (bytes.size < 10) return null

        var payloadStart = -1
        var payloadLen = 0

        for (i in 0 until bytes.size - 8) {
            if (bytes[i] == MAGIC_HEADER) {
                val cmdLo = bytes[i + 4].toInt() and 0xFF
                val cmdHi = bytes[i + 5].toInt() and 0xFF
                val cmd = (cmdHi shl 8) or cmdLo

                val pLen = (bytes[i + 7].toInt() and 0xFF) or ((bytes[i + 8].toInt() and 0xFF) shl 8)
                if (cmd == CMD_BATTERY_RESP || cmd == CMD_BATTERY_ALT_RESP) {
                    payloadStart = i + 9
                    payloadLen = minOf(pLen, bytes.size - payloadStart)
                    break
                }
            }
        }

        // If not a battery response packet, do not parse!
        if (payloadStart == -1 || payloadLen < 2) {
            return null
        }

        // Check if index 0 of payload is status code (0x00 = success)
        var cur = payloadStart
        if (cur < bytes.size && bytes[cur] == 0.toByte() && cur + 1 < bytes.size) {
            cur++ // skip status code
        }

        // Format 1: Count-based [Count] [ID, Val] [ID, Val]...
        if (cur < bytes.size) {
            val count = bytes[cur].toInt() and 0xFF
            if (count in 1..4 && cur + 1 + count * 2 <= bytes.size) {
                var left: Int? = null
                var right: Int? = null
                var case: Int? = null
                var leftChg = false
                var rightChg = false
                var caseChg = false

                var itemPtr = cur + 1
                for (c in 0 until count) {
                    val compId = bytes[itemPtr].toInt() and 0xFF
                    val rawVal = bytes[itemPtr + 1].toInt() and 0xFF
                    val level = (rawVal and 0x7F).takeIf { it in 0..100 }
                    val charging = (rawVal and 0x80) != 0

                    when (compId) {
                        1 -> { left = level; leftChg = charging }
                        2 -> { right = level; rightChg = charging }
                        3 -> { case = level; caseChg = charging }
                    }
                    itemPtr += 2
                }

                if (left != null || right != null || case != null) {
                    return TwsBatteryReport(
                        left = left,
                        right = right,
                        case = case,
                        leftCharging = leftChg,
                        rightCharging = rightChg,
                        caseCharging = caseChg,
                        rawHex = HexUtils.formatHex(bytes),
                    )
                }
            }
        }

        // Format 2: Direct 3-byte triplet [Left, Right, Case]
        if (cur + 3 <= bytes.size) {
            val rawL = bytes[cur].toInt() and 0xFF
            val rawR = bytes[cur + 1].toInt() and 0xFF
            val rawC = bytes[cur + 2].toInt() and 0xFF

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
