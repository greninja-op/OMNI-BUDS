package com.omnibuds.android.hardware.protocol

/**
 * Parser for Google Fast Pair specification battery characteristics and notifications.
 * Fast Pair battery telemetry is standardized across Realme, Xiaomi, Nothing, JBL, Sony, and Google.
 */
object FastPairProtocolParser {

    /**
     * Parses standard Fast Pair battery triplets:
     * Byte 0: Left Earbud (bit 7 = charging, bits 0-6 = level 0..100, 0x7F = unavailable)
     * Byte 1: Right Earbud (bit 7 = charging, bits 0-6 = level 0..100, 0x7F = unavailable)
     * Byte 2: Charging Case (bit 7 = charging, bits 0-6 = level 0..100, 0x7F = unavailable)
     */
    fun parseBattery(bytes: ByteArray): TwsBatteryReport? {
        if (bytes.size < 3) return null

        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        val b2 = bytes[2].toInt() and 0xFF

        fun parseLevel(b: Int): Int? {
            val level = b and 0x7F
            return if (level in 0..100) level else null
        }

        val left = parseLevel(b0)
        val right = parseLevel(b1)
        // Case is only valid when in 1..100 (0x00, 0x7F, 0xFF represent pogo pins offline / both buds out)
        val case = (b2 and 0x7F).takeIf { it in 1..100 }

        val leftChg = (b0 and 0x80) != 0
        val rightChg = (b1 and 0x80) != 0
        val caseChg = (b2 and 0x80) != 0

        if (left == null && right == null && case == null) return null

        return TwsBatteryReport(
            left = left,
            right = right,
            case = case,
            leftCharging = leftChg,
            rightCharging = rightChg,
            caseCharging = caseChg,
            rawHex = HexUtils.formatHex(bytes),
            sourceProtocol = "Fast Pair (0xFE2C)",
        )
    }
}

