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
    const val CMD_QUERY_ANC = 0x0104
    const val CMD_QUERY_ANC_OPO = 0x8204
    const val CMD_SET_ANC = 0x0404
    const val CMD_BATCH_STATUS_QUERY = 0x010D
    const val CMD_BATCH_STATUS_RESP = 0x810D
    const val CMD_ANC_RESP = 0x8404
    const val CMD_SET_EQ = 0x0405
    const val CMD_SET_SPATIAL_AUDIO = 0x0422
    const val CMD_SET_SPATIAL_AUDIO_ALT = 0x0410
    const val CMD_SET_GAME_MODE = 0x0403
    const val CMD_SET_GAME_MODE_ALT = 0x0411
    const val CMD_QUERY_TOUCH_GESTURE = 0x0108
    const val CMD_SET_TOUCH_GESTURE = 0x0408
    const val CMD_SET_TOUCH_ENABLE = 0x0409
    const val CMD_TOUCH_GESTURE_RESP = 0x8408
    const val CMD_FIND_BUDS = 0x040A
    const val CMD_DYNAMIC_BASS = 0x0424
    const val CMD_VOLUME_ENHANCER = 0x0425
    const val CMD_SET_ANC_LEVEL = 0x040D
    const val CMD_SET_WIND_NOISE = 0x040E
    const val CMD_SET_TRANSPARENCY_LEVEL = 0x040F
    const val CMD_SET_IN_EAR_DETECTION = 0x0406
    const val CMD_SET_MULTIPOINT = 0x041E
    const val CMD_SET_CONVERSATIONAL_AWARENESS = 0x0426
    const val CMD_PERSONALIZED_ANC = 0x0427
    const val CMD_EARBUD_FIT_TEST = 0x040B
    const val CMD_SET_PINCH_SPEED = 0x0418
    const val CMD_SET_STEM_SLIDE = 0x0419
    const val CMD_SET_HI_RES_CODEC = 0x041A

    private val ancSeqCounter = java.util.concurrent.atomic.AtomicInteger(1)

    /** Monotonic sequence for ANC SET frames; wraps 1..255. */
    fun nextAncSeq(): Byte {
        val value = ancSeqCounter.getAndIncrement() and 0xFF
        return (if (value == 0) 1 else value).toByte()
    }

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
     * Builds the payload to change ANC modes (legacy 3-byte payload for backward compatibility):
     * Mode: "ANC", "OFF", "TRANSPARENCY"
     * Mapped via the device's active [AncByteMapping] to prevent scrambled or inverted states.
     * HeyMelody payload format: [0x01, 0x01, modeVal]
     */
    fun buildAncCommand(
        mode: String,
        mapping: AncByteMapping = AncByteMapping.PROFILE_C,
    ): ByteArray {
        val modeVal = mapping.byteForMode(mode)
        return buildFrame(CMD_SET_ANC, byteArrayOf(0x01, 0x01, modeVal), seq = nextAncSeq())
    }

    /**
     * Builds the OPOv1/BES ANC SET frame.
     *
     * Hardware captures for realme/OPPO/OnePlus show a fixed 3-byte payload
     * `[0x01, 0x01, mode]`; longer payloads are not part of the SET command and
     * can be misread as sub-modes by the buds.
     */
    fun buildFullHardwareAncCommand(
        mode: String,
        mapping: AncByteMapping = AncByteMapping.PROFILE_D,
    ): ByteArray {
        val modeVal = mapping.byteForMode(mode)
        return buildFrame(CMD_SET_ANC, byteArrayOf(0x01, 0x01, modeVal), seq = nextAncSeq())
    }

    /**
     * Builds candidate reset payload for the 3-Variant Off-Byte Tester.
     */
    fun buildAncCustomOffCommand(offByte: Byte, subByte: Byte = 0x00): ByteArray {
        return buildFrame(CMD_SET_ANC, byteArrayOf(0x01, 0x01, offByte), seq = nextAncSeq())
    }

    /**
     * Builds the OPOv1 ANC query: CAT 0x04 / SUB 0x82.
     * The older 0x0104 frame is a firmware query on realme hardware and never
     * names the current noise mode.
     */
    fun buildAncQueryCommand(): ByteArray = buildAncModeRead()

    // -- Mode read and event subscription ---------------------------------------------
    // The current noise mode is read with 0x010C 01 01, and the buds answer with the value as
    // a little-endian bitmask. The read was confirmed on a realme Buds T300 (reply
    // `00 01 01 <mask>`). These frames are reads and subscriptions only. They never change a mode.

    const val CMD_QUERY_ANC_MODE = 0x010C
    const val CMD_ANC_MODE_RESP = 0x810C
    const val CMD_QUERY_BROADCAST_CODES = 0x0200
    const val CMD_SUBSCRIBE_EVENTS = 0x0205

    fun buildHandshake(): ByteArray = buildFrame(CMD_HANDSHAKE, seq = 0x40)

    fun buildBroadcastCodesQuery(): ByteArray = buildFrame(CMD_QUERY_BROADCAST_CODES, seq = 0x41)

    /** Payload is the count first, then the event codes. */
    fun buildEventSubscribe(codes: ByteArray): ByteArray =
        buildFrame(CMD_SUBSCRIBE_EVENTS, byteArrayOf(codes.size.toByte()) + codes, seq = 0x42)

    fun buildAncModeRead(): ByteArray = buildFrame(CMD_QUERY_ANC_MODE, byteArrayOf(0x01, 0x01), seq = 0x43)

    /**
     * Reads which modes the buds' own press-and-hold cycles through. The reply is
     * `00 02 01 <mask LE>` and uses the same bit values as a mode write.
     */
    fun buildHoldCycleRead(): ByteArray = buildFrame(CMD_QUERY_ANC_MODE, byteArrayOf(0x02, 0x01), seq = 0x45)

    /**
     * Sets which modes the buds' press-and-hold cycles through. Payload is `02 01 <mask LE>`,
     * using the fewest bytes that hold the mask. The buds keep this setting themselves, so it
     * follows them to any phone. The mask uses the same bit values as a mode write.
     */
    fun buildHoldCycleWrite(mask: Int): ByteArray {
        val payload = mutableListOf<Byte>(0x02, 0x01)
        var rest = mask
        do {
            payload.add((rest and 0xFF).toByte())
            rest = rest ushr 8
        } while (rest != 0 && payload.size < 6)
        return buildFrame(CMD_SET_ANC, payload.toByteArray(), seq = 0x46)
    }

    /** True for a push shaped `03 02 <kind> <mask LE>`: the hold-cycle setting, not a mode. */
    fun isHoldCyclePush(bytes: ByteArray, payloadStart: Int, avail: Int): Boolean =
        avail >= 4 &&
            bytes[payloadStart] == 0x03.toByte() &&
            bytes[payloadStart + 1] == 0x02.toByte()

    /**
     * Reads the hold-cycle masks the buds report: the reply to `0x010C 02 01`
     * (payload `00 02 01 <mask>`) and the matching event push (`03 02 01 <mask>`).
     */
    fun parseHoldCycleReports(bytes: ByteArray): List<Int> {
        val masks = mutableListOf<Int>()
        var i = 0
        while (i + 9 <= bytes.size) {
            if (bytes[i] != MAGIC_HEADER) {
                i++
                continue
            }
            val total = (bytes[i + 1].toInt() and 0xFF) + 2
            if (total < 9 || i + total > bytes.size) {
                i++
                continue
            }
            val cmd = ((bytes[i + 5].toInt() and 0xFF) shl 8) or (bytes[i + 4].toInt() and 0xFF)
            val payLen = (bytes[i + 7].toInt() and 0xFF) or ((bytes[i + 8].toInt() and 0xFF) shl 8)
            val start = i + 9
            val avail = minOf(payLen, i + total - start)
            val isRead = cmd == CMD_ANC_MODE_RESP &&
                avail >= 4 && bytes[start] == 0x00.toByte() &&
                bytes[start + 1] == 0x02.toByte() && bytes[start + 2] == 0x01.toByte()
            val isPush = cmd == CMD_BATTERY_ALT_RESP &&
                isHoldCyclePush(bytes, start, avail) && bytes[start + 2] == 0x01.toByte()
            if (isRead || isPush) {
                val count = minOf(avail - 3, 4)
                var mask = 0
                for (k in 0 until count) {
                    mask = mask or ((bytes[start + 3 + k].toInt() and 0xFF) shl (8 * k))
                }
                masks += mask
            }
            i += total
        }
        return masks
    }

    /**
     * A noise-mode value the buds reported, kept as the raw little-endian bitmask.
     * The mask is NOT a write byte: the report table and the write table differ.
     */
    data class AncModeReport(
        val mask: Int,
        val maskBytes: String,
        val viaPush: Boolean,
    )

    /** True for a push shaped `03 01 01 <mask LE>`. The old tagged-list decoder must skip it. */
    fun isModeMaskPush(bytes: ByteArray, payloadStart: Int, avail: Int): Boolean =
        avail >= 4 &&
            bytes[payloadStart] == 0x03.toByte() &&
            bytes[payloadStart + 1] == 0x01.toByte() &&
            bytes[payloadStart + 2] == 0x01.toByte()

    /**
     * Reads the buds' own noise-mode reports from a buffer that may hold several frames:
     * the reply to 0x010C 01 01 (payload `00 01 01 <mask>`) and the event-03 push
     * (payload `03 01 01 <mask>`, cmd 0x0204).
     */
    fun parseAncModeReports(bytes: ByteArray): List<AncModeReport> {
        val reports = mutableListOf<AncModeReport>()
        var i = 0
        while (i + 9 <= bytes.size) {
            if (bytes[i] != MAGIC_HEADER) {
                i++
                continue
            }
            val total = (bytes[i + 1].toInt() and 0xFF) + 2
            if (total < 9 || i + total > bytes.size) {
                i++
                continue
            }
            val cmd = ((bytes[i + 5].toInt() and 0xFF) shl 8) or (bytes[i + 4].toInt() and 0xFF)
            val payLen = (bytes[i + 7].toInt() and 0xFF) or ((bytes[i + 8].toInt() and 0xFF) shl 8)
            val start = i + 9
            val avail = minOf(payLen, i + total - start)
            val isRead = cmd == CMD_ANC_MODE_RESP &&
                avail >= 4 && bytes[start] == 0x00.toByte() &&
                bytes[start + 1] == 0x01.toByte() && bytes[start + 2] == 0x01.toByte()
            val isPush = cmd == CMD_BATTERY_ALT_RESP && isModeMaskPush(bytes, start, avail)
            if (isRead || isPush) {
                val count = minOf(avail - 3, 4)
                var mask = 0
                val shown = StringBuilder()
                for (k in 0 until count) {
                    val b = bytes[start + 3 + k].toInt() and 0xFF
                    mask = mask or (b shl (8 * k))
                    if (k > 0) shown.append(' ')
                    shown.append("%02X".format(b))
                }
                reports += AncModeReport(mask = mask, maskBytes = shown.toString(), viaPush = isPush)
            }
            i += total
        }
        return reports
    }

    /**
     * Fixed OPOv1 batch-status query. The reply carries a key/value stream;
     * key 0x28 names the game-mode state (next byte 0x01 on / 0x00 off).
     */
    fun buildBatchStatusQuery(): ByteArray = buildFrame(
        CMD_BATCH_STATUS_QUERY,
        byteArrayOf(
            0x0B, 0x05, 0x04, 0x0B, 0x11, 0x13, 0x18, 0x06, 0x1B, 0x1C, 0x27, 0x28,
        ),
    )

    data class BatchStatus(
        val gameMode: Boolean?,
    )

    /**
     * Parses the 0x810D batch-status reply. Payload is:
     * status, count, then `count` (key, value) pairs.
     * Key 0x28 is game mode. Key 0x27 is deliberately NOT used as the ANC
     * mode: it is a battery-poll field that stays at 0x01 and must never
     * overwrite the confirmed noise-control state.
     */
    fun parseBatchStatus(bytes: ByteArray): BatchStatus? {
        var payloadStart = -1
        var payloadLen = 0
        for (i in 0 until bytes.size - 8) {
            if (bytes[i] != MAGIC_HEADER) continue
            val cmdLo = bytes[i + 4].toInt() and 0xFF
            val cmdHi = bytes[i + 5].toInt() and 0xFF
            val cmd = (cmdHi shl 8) or cmdLo
            if (cmd == CMD_BATCH_STATUS_RESP) {
                payloadStart = i + 9
                payloadLen = minOf(
                    (bytes[i + 7].toInt() and 0xFF) or ((bytes[i + 8].toInt() and 0xFF) shl 8),
                    bytes.size - payloadStart,
                )
                break
            }
        }
        if (payloadStart < 0 || payloadLen < 3) return null

        val end = payloadStart + payloadLen
        var p = payloadStart
        if ((bytes[p].toInt() and 0xFF) == 0x00) p++ // status
        if (p >= end) return null
        val count = bytes[p].toInt() and 0xFF
        p++
        var gameMode: Boolean? = null
        repeat(count.coerceAtMost(32)) {
            if (p + 1 >= end) return@repeat
            val key = bytes[p].toInt() and 0xFF
            val value = bytes[p + 1].toInt() and 0xFF
            if (key == 0x28) {
                gameMode = when (value) {
                    0x01 -> true
                    0x00 -> false
                    else -> null
                }
            }
            p += 2
        }
        return BatchStatus(gameMode = gameMode)
    }

    /** Reads the game-mode key from a 0x810D batch-status reply. */
    fun parseGameModeFromBatchStatus(bytes: ByteArray): Boolean? =
        parseBatchStatus(bytes)?.gameMode

    /**
     * Checks if the packet is an acknowledgment (ACK) response for CMD_SET_ANC.
     * In HeyMelody/BES protocol, CMD_SET_ANC (0x0404) responds with CMD_ANC_RESP (0x8404),
     * where a 1-byte payload of 0x00 indicates command execution SUCCESS.
     */
    fun isAncCommandAck(bytes: ByteArray): Boolean {
        if (bytes.size < 10) return false
        for (i in 0 until bytes.size - 9) {
            if (bytes[i] == MAGIC_HEADER) {
                val cmdLo = bytes[i + 4].toInt() and 0xFF
                val cmdHi = bytes[i + 5].toInt() and 0xFF
                val cmd = (cmdHi shl 8) or cmdLo
                if (cmd == CMD_ANC_RESP) {
                    val pLen = (bytes[i + 7].toInt() and 0xFF) or ((bytes[i + 8].toInt() and 0xFF) shl 8)
                    if (pLen == 1 && i + 9 < bytes.size && bytes[i + 9] == 0x00.toByte()) {
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * Parses incoming ANC mode from ACK, notification, or earbud pinch/tap telemetry packet.
     * Checks the authoritative mode position (offset 2 when framed with 0x01, 0x01)
     * rather than scanning backwards, preventing trailing 0x00 sub-bytes from being falsely
     * identified as OFF.
     */
    fun parseAncModeResponse(
        bytes: ByteArray,
        mapping: AncByteMapping = AncByteMapping.PROFILE_A,
    ): String? {
        if (bytes.size < 4) return null

        for (i in 0 until bytes.size - 4) {
            if (bytes[i] == MAGIC_HEADER) {
                val cmdLo = bytes[i + 4].toInt() and 0xFF
                val cmdHi = if (i + 5 < bytes.size) bytes[i + 5].toInt() and 0xFF else 0
                val cmd = (cmdHi shl 8) or cmdLo

                if (cmd == CMD_SET_ANC || cmd == CMD_ANC_RESP || cmd == CMD_QUERY_ANC || cmd == CMD_QUERY_ANC_OPO || cmd == 0x0204 || cmd == 0x0401) {
                    val payloadStart = i + 9
                    if (payloadStart < bytes.size) {
                        val pLen = if (i + 8 < bytes.size) {
                            (bytes[i + 7].toInt() and 0xFF) or ((bytes[i + 8].toInt() and 0xFF) shl 8)
                        } else {
                            bytes.size - payloadStart
                        }
                        val avail = minOf(pLen, bytes.size - payloadStart)

                        // A 1-byte 0x8404 payload is a command status (0x00 accepted,
                        // 0x01 rejected), never a mode byte.
                        if (cmd == CMD_ANC_RESP && avail == 1) {
                            return null
                        }

                        // Unsolicited status notification (CAT 0x04 / SUB 0x02):
                        // tagged list <type> <count> (id, value)...
                        // Type 0x03 is the real noise mode; type 0x02 is not.
                        // A `03 01 01 <mask>` push carries a report bitmask, not a write byte.
                        // The tagged-list decoder below would look one mask byte up in the
                        // write table and invent a mode, so these frames are left alone.
                        if (cmd == CMD_BATTERY_ALT_RESP &&
                            (isModeMaskPush(bytes, payloadStart, avail) || isHoldCyclePush(bytes, payloadStart, avail))
                        ) {
                            continue
                        }

                        if (cmd == CMD_BATTERY_ALT_RESP) {
                            var p = payloadStart
                            val end = payloadStart + avail
                            while (p + 1 < end) {
                                val type = bytes[p].toInt() and 0xFF
                                val count = bytes[p + 1].toInt() and 0xFF
                                if (count <= 0 || p + 2 + count * 2 > end) break
                                if (type == 0x03) {
                                    var q = p + 2
                                    repeat(count) {
                                        val id = bytes[q].toInt() and 0xFF
                                        val raw = bytes[q + 1]
                                        if (id == 1) {
                                            val detected = mapping.modeForByte(raw)
                                            if (detected != null) return detected
                                        }
                                        q += 2
                                    }
                                }
                                p += 2 + count * 2
                            }
                        }

                        // In HeyMelody/BES framing, payload starts with [0x01, 0x01, modeByte, ...]
                        if (avail >= 3 && bytes[payloadStart] == 0x01.toByte() && bytes[payloadStart + 1] == 0x01.toByte()) {
                            val candidate = bytes[payloadStart + 2]
                            val detected = mapping.modeForByte(candidate)
                            if (detected != null) {
                                return detected
                            }
                        } else if (avail >= 1) {
                            // Check first byte of payload directly
                            val candidate = bytes[payloadStart]
                            val detected = mapping.modeForByte(candidate)
                            if (detected != null) {
                                return detected
                            }
                        }
                    }
                }
            }
        }

        return null
    }

    /**
     * Builds the payload to set Equalizer preset for realme Buds T300:
     * - "PURE_BASS": 0x01 (Default dynamic 12.4mm driver tuning)
     * - "CLEAR_VOCALS" / "VOCAL": 0x02 (Midrange presence boost at 1kHz-4kHz)
     * - "NATURE_BALANCE" / "FLAT" / "BALANCED": 0x00 (Flat studio reference response)
     * - "BASS_BOOST" / "BASS_BOOST_PLUS": 0x04 (Deep sub-bass shelf +5dB at 31Hz-125Hz)
     * - "CUSTOM": 0x03 (User multi-band custom EQ)
     */
    fun buildEqualizerCommand(preset: String): ByteArray {
        val eqVal: Byte = when (preset.uppercase()) {
            "NATURE_BALANCE", "FLAT", "BALANCED" -> 0x00
            "PURE_BASS", "DEFAULT" -> 0x01
            "CLEAR_VOCALS", "VOCAL" -> 0x02
            "CUSTOM" -> 0x03
            "BASS_BOOST_PLUS", "BASS_BOOST", "BASS" -> 0x04
            "TREBLE_BOOST", "BRIGHT" -> 0x05
            else -> 0x01
        }
        return buildFrame(CMD_SET_EQ, byteArrayOf(0x01, 0x01, eqVal))
    }

    /**
     * Builds Spatial Audio commands for realme Buds T300:
     * Dispatches both CMD_SET_SPATIAL_AUDIO (0x0422) and alternate (0x0410).
     */
    fun buildSpatialAudioCommands(enabled: Boolean): List<ByteArray> {
        val flag: Byte = if (enabled) 0x01 else 0x00
        return listOf(
            buildFrame(CMD_SET_SPATIAL_AUDIO, byteArrayOf(0x01, 0x01, flag)),
            buildFrame(CMD_SET_SPATIAL_AUDIO_ALT, byteArrayOf(0x01, 0x01, flag)),
        )
    }

    fun buildSpatialAudioCommand(enabled: Boolean): ByteArray {
        return buildSpatialAudioCommands(enabled).first()
    }

    /**
     * Builds Game Mode (50ms Ultra-Low Latency) commands:
     */
    fun buildGameModeCommands(enabled: Boolean): List<ByteArray> {
        val flag: Byte = if (enabled) 0x01 else 0x00
        return listOf(
            buildFrame(CMD_SET_GAME_MODE, byteArrayOf(0x01, 0x01, flag)),
            buildFrame(CMD_SET_GAME_MODE, byteArrayOf(0x28, flag)),
            buildFrame(CMD_SET_GAME_MODE_ALT, byteArrayOf(0x01, 0x01, flag)),
        )
    }

    fun buildGameModeCommand(enabled: Boolean): ByteArray {
        return buildGameModeCommands(enabled).first()
    }

    /**
     * Dynamic Bass Enhancer (Level -5 to +5):
     * Maps -5..+5 to 0..10 or legacy 1..5.
     */
    fun buildDynamicBassCommand(enabled: Boolean, level: Int = 3): ByteArray {
        val flag: Byte = if (enabled) 0x01 else 0x00
        val clampedLevel = (level + 5).coerceIn(0, 10).toByte()
        return buildFrame(CMD_DYNAMIC_BASS, byteArrayOf(0x01, flag, clampedLevel))
    }

    /**
     * Builds the vendor NVRAM command to toggle LHDC / LDAC high-res mode on Realme/OnePlus/HeyMelody earbuds.
     */
    fun buildHiResCodecCommand(enabled: Boolean): ByteArray {
        val flag: Byte = if (enabled) 0x01 else 0x00
        return buildFrame(CMD_SET_HI_RES_CODEC, byteArrayOf(0x01, 0x01, flag))
    }

    /**
     * Volume Enhancer (+3.5dB safe limiter boost):
     */
    fun buildVolumeEnhancerCommand(enabled: Boolean): ByteArray {
        val flag: Byte = if (enabled) 0x01 else 0x00
        return buildFrame(CMD_VOLUME_ENHANCER, byteArrayOf(0x01, 0x01, flag))
    }

    /**
     * Find My Earbuds: Ring Left / Right bud:
     * bud: "Left" (0x01), "Right" (0x02), "Both" (0x03)
     */
    fun buildFindMyBudsCommand(bud: String, ring: Boolean): ByteArray {
        val budId: Byte = when (bud.lowercase()) {
            "left" -> 0x01
            "right" -> 0x02
            else -> 0x03
        }
        val ringFlag: Byte = if (ring) 0x01 else 0x00
        return buildFrame(CMD_FIND_BUDS, byteArrayOf(0x01, budId, ringFlag))
    }

    /**
     * Pro Multi-Level ANC Sub-Mode (Max 50dB, Moderate 40dB, Mild 25dB, Smart Adaptive):
     */
    fun buildProAncLevelCommand(level: String): ByteArray {
        val lvlByte: Byte = when (level.uppercase()) {
            "MAX", "MAX_50DB", "DEEP" -> 0x03
            "MODERATE", "MODERATE_40DB" -> 0x02
            "MILD", "MILD_25DB" -> 0x01
            "ADAPTIVE", "SMART" -> 0x00
            else -> 0x03
        }
        return buildFrame(CMD_SET_ANC_LEVEL, byteArrayOf(0x01, 0x01, lvlByte))
    }

    /**
     * Wind Noise Reduction toggle:
     */
    fun buildWindNoiseReductionCommand(enabled: Boolean): ByteArray {
        val flag: Byte = if (enabled) 0x01 else 0x00
        return buildFrame(CMD_SET_WIND_NOISE, byteArrayOf(0x01, 0x01, flag))
    }

    /**
     * Pro Transparency Mode: 5-level ambient slider (1 to 5) + Vocal Enhancement / Voice Boost:
     */
    fun buildTransparencyLevelCommand(level: Int, voiceBoost: Boolean): ByteArray {
        val clamped = level.coerceIn(1, 5).toByte()
        val boostFlag: Byte = if (voiceBoost) 0x01 else 0x00
        return buildFrame(CMD_SET_TRANSPARENCY_LEVEL, byteArrayOf(0x01, clamped, boostFlag))
    }

    /**
     * In-Ear Detection (Auto-pause / resume wear sensor):
     */
    fun buildInEarDetectionCommand(enabled: Boolean): ByteArray {
        val flag: Byte = if (enabled) 0x01 else 0x00
        return buildFrame(CMD_SET_IN_EAR_DETECTION, byteArrayOf(0x01, 0x01, flag))
    }

    /**
     * Dual Device Connection (Multipoint) toggle:
     */
    fun buildMultipointCommand(enabled: Boolean): ByteArray {
        val flag: Byte = if (enabled) 0x01 else 0x00
        return buildFrame(CMD_SET_MULTIPOINT, byteArrayOf(0x01, 0x01, flag))
    }

    /**
     * Conversational Awareness / Auto-duck media volume:
     */
    fun buildConversationalAwarenessCommand(enabled: Boolean): ByteArray {
        val flag: Byte = if (enabled) 0x01 else 0x00
        return buildFrame(CMD_SET_CONVERSATIONAL_AWARENESS, byteArrayOf(0x01, 0x01, flag))
    }

    /**
     * Golden Sound Personalized ANC Ear Canal Calibration trigger:
     */
    fun buildPersonalizedAncCommand(start: Boolean): ByteArray {
        val flag: Byte = if (start) 0x01 else 0x00
        return buildFrame(CMD_PERSONALIZED_ANC, byteArrayOf(0x01, 0x01, flag))
    }

    /**
     * Earbud Fit Test trigger:
     */
    fun buildEarbudFitTestCommand(start: Boolean): ByteArray {
        val flag: Byte = if (start) 0x01 else 0x00
        return buildFrame(CMD_EARBUD_FIT_TEST, byteArrayOf(0x01, 0x01, flag))
    }

    /**
     * Stem Slide Volume Control toggle:
     */
    fun buildStemSlideCommand(enabled: Boolean): ByteArray {
        val flag: Byte = if (enabled) 0x01 else 0x00
        return buildFrame(CMD_SET_STEM_SLIDE, byteArrayOf(0x01, 0x01, flag))
    }

    /**
     * Pinch Speed / Hold Duration:
     */
    fun buildPinchSpeedCommand(speed: String): ByteArray {
        val speedByte: Byte = when (speed.uppercase()) {
            "DEFAULT" -> 0x00
            "SLOW" -> 0x01
            "SLOWEST" -> 0x02
            else -> 0x00
        }
        return buildFrame(CMD_SET_PINCH_SPEED, byteArrayOf(0x01, 0x01, speedByte))
    }

    /**
     * Touch Sensor Master Enable packets:
     * Re-enables touch controller on realme Buds T300 after being disabled/locked by "None".
     */
    fun buildTouchMasterEnableCommands(enable: Boolean = true): List<ByteArray> {
        val flag: Byte = if (enable) 0x01 else 0x00
        return listOf(
            buildFrame(CMD_SET_TOUCH_ENABLE, byteArrayOf(0x01, 0x01, flag)),
            buildFrame(CMD_SET_TOUCH_GESTURE, byteArrayOf(0x00, 0x01, flag)),
        )
    }

    /**
     * Builds the synchronized full Left + Right gesture struct for realme Buds T300:
     * [0x02, leftDouble, rightDouble, leftTriple, rightTriple, leftHold, rightHold, ancCycleBitmask]
     */
    fun buildSynchronizedTouchGestureStruct(
        leftDouble: String,
        rightDouble: String,
        leftTriple: String,
        rightTriple: String,
        leftHold: String,
        rightHold: String,
        ancCycleBitmask: Byte = 0x06, // Default ANC + Transparency
    ): ByteArray {
        return buildFrame(
            CMD_SET_TOUCH_GESTURE,
            byteArrayOf(
                0x02,
                actionByteForGesture(leftDouble),
                actionByteForGesture(rightDouble),
                actionByteForGesture(leftTriple),
                actionByteForGesture(rightTriple),
                actionByteForGesture(leftHold),
                actionByteForGesture(rightHold),
                ancCycleBitmask,
            ),
        )
    }

    /**
     * Translates action string to official realme Buds T300 gesture opcode.
     */
    fun actionByteForGesture(action: String): Byte = when (action.uppercase()) {
        "NONE" -> 0x00
        "PLAY_PAUSE" -> 0x01
        "NEXT_TRACK" -> 0x02
        "PREVIOUS_TRACK" -> 0x03
        "VOICE_ASSISTANT" -> 0x04
        "SWITCH_NOISE_CONTROL", "ANC" -> 0x05
        "GAME_MODE" -> 0x06
        "VOLUME_UP" -> 0x07
        "VOLUME_DOWN" -> 0x08
        else -> 0x01
    }

    /**
     * Builds individual gesture configuration frame for a bud with ANC cycle bitmask:
     */
    fun buildTouchGestureCommand(
        bud: String,
        gesture: String,
        action: String,
        ancCycleBitmask: Byte = 0x06,
    ): ByteArray {
        val budId: Byte = if (bud.equals("Right", ignoreCase = true)) 0x02 else 0x01
        val gestureId: Byte = when (gesture.lowercase()) {
            "single_tap" -> 0x00
            "double_tap" -> 0x01
            "triple_tap" -> 0x02
            "long_press", "touch_and_hold" -> 0x03
            else -> 0x01
        }
        val actByte = actionByteForGesture(action)
        return buildFrame(CMD_SET_TOUCH_GESTURE, byteArrayOf(0x01, budId, gestureId, actByte, ancCycleBitmask))
    }

    data class ParsedTouchEvent(
        val budId: Int,
        val gestureId: Int,
        val action: String,
    ) {
        val isDoubleTap: Boolean get() = gestureId == 0x01
        val isSingleTap: Boolean get() = gestureId == 0x00
        val isTripleTap: Boolean get() = gestureId == 0x02
        val isLongPress: Boolean get() = gestureId == 0x03
    }

    /**
     * Parses incoming touch notification / telemetry from the earbuds.
     */
    fun parseTouchNotification(bytes: ByteArray): ParsedTouchEvent? {
        if (bytes.size < 10) return null
        for (i in 0 until bytes.size - 9) {
            if (bytes[i] == MAGIC_HEADER) {
                val cmdLo = bytes[i + 4].toInt() and 0xFF
                val cmdHi = bytes[i + 5].toInt() and 0xFF
                val cmd = (cmdHi shl 8) or cmdLo
                if (cmd == CMD_TOUCH_GESTURE_RESP || cmd == 0x0408 || cmd == 0x0208 || cmd == 0x0108) {
                    val pLen = (bytes[i + 7].toInt() and 0xFF) or ((bytes[i + 8].toInt() and 0xFF) shl 8)
                    if (pLen >= 3 && i + 9 + pLen <= bytes.size) {
                        val bud = bytes[i + 10].toInt() and 0xFF
                        val gest = bytes[i + 11].toInt() and 0xFF
                        val actCode = if (pLen >= 4) bytes[i + 12].toInt() and 0xFF else 0
                        val actString = when (actCode) {
                            0x01 -> "PLAY_PAUSE"
                            0x02 -> "NEXT_TRACK"
                            0x03 -> "PREVIOUS_TRACK"
                            0x04 -> "VOICE_ASSISTANT"
                            0x05 -> "SWITCH_NOISE_CONTROL"
                            0x06 -> "GAME_MODE"
                            0x07 -> "VOLUME_UP"
                            0x08 -> "VOLUME_DOWN"
                            else -> "UNKNOWN"
                        }
                        return ParsedTouchEvent(bud, gest, actString)
                    }
                }
            }
        }
        return null
    }

    /**
     * Queries touch gesture configuration from the earbuds.
     */
    fun buildTouchGestureQuery(budId: Byte = 0x01): ByteArray {
        return buildFrame(CMD_QUERY_TOUCH_GESTURE, byteArrayOf(budId))
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
        var batteryCmd = 0

        for (i in 0 until bytes.size - 8) {
            if (bytes[i] == MAGIC_HEADER) {
                val cmdLo = bytes[i + 4].toInt() and 0xFF
                val cmdHi = bytes[i + 5].toInt() and 0xFF
                val cmd = (cmdHi shl 8) or cmdLo

                val pLen = (bytes[i + 7].toInt() and 0xFF) or ((bytes[i + 8].toInt() and 0xFF) shl 8)
                if (cmd == CMD_BATTERY_RESP || cmd == CMD_BATTERY_ALT_RESP) {
                    payloadStart = i + 9
                    payloadLen = minOf(pLen, bytes.size - payloadStart)
                    batteryCmd = cmd
                    break
                }
            }
        }

        // If not a battery response packet, do not parse!
        if (payloadStart == -1 || payloadLen < 2) {
            return null
        }

        // 0x0204 is also the unsolicited status channel. Its tagged list can
        // carry the noise mode (type 0x03) or another setting (type 0x02);
        // those are not battery frames. ANC parsing handles them.
        if (batteryCmd == CMD_BATTERY_ALT_RESP &&
            (bytes[payloadStart] == 0x02.toByte() || bytes[payloadStart] == 0x03.toByte())
        ) {
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
                    val level = rawVal and 0x7F
                    val charging = (rawVal and 0x80) != 0

                    when (compId) {
                        1 -> {
                            // On realme Buds T300, a docked or powered-down single bud sends 0x00, 0x7F (127), or 0xFF (255).
                            // Only report valid level if 1..100 or 0 while charging; otherwise null (offline / in case).
                            left = if (level in 1..100 || (level == 0 && charging)) level else null
                            leftChg = charging
                        }
                        2 -> {
                            right = if (level in 1..100 || (level == 0 && charging)) level else null
                            rightChg = charging
                        }
                        3 -> {
                            // Case index. 0x00 / 0x7F / 0xFF mean no case
                            // reading (both buds are out); 1..100 is live.
                            case = if (level in 1..100) level else null
                            caseChg = charging
                        }
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
                        sourceProtocol = "RFCOMM / Vendor SPP",
                    )
                }
            }
        }

        // Format 2: Direct 3-byte triplet [Left, Right, Case]
        if (cur + 3 <= bytes.size) {
            val rawL = bytes[cur].toInt() and 0xFF
            val rawR = bytes[cur + 1].toInt() and 0xFF
            val rawC = bytes[cur + 2].toInt() and 0xFF

            val leftChg = (rawL and 0x80) != 0
            val rightChg = (rawR and 0x80) != 0
            val caseChg = (rawC and 0x80) != 0

            val leftLvl = rawL and 0x7F
            val rightLvl = rawR and 0x7F
            val caseLvl = rawC and 0x7F

            val left = if (leftLvl in 1..100 || (leftLvl == 0 && leftChg)) leftLvl else null
            val right = if (rightLvl in 1..100 || (rightLvl == 0 && rightChg)) rightLvl else null
            val case = caseLvl.takeIf { it in 1..100 }

            if (left != null || right != null || case != null) {
                return TwsBatteryReport(
                    left = left,
                    right = right,
                    case = case,
                    leftCharging = leftChg,
                    rightCharging = rightChg,
                    caseCharging = caseChg,
                    rawHex = HexUtils.formatHex(bytes),
                    sourceProtocol = "RFCOMM / Vendor SPP",
                )
            }
        }

        return null
    }
}
