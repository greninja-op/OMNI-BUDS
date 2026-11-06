package com.omnibuds.android.hardware.engine

import com.omnibuds.android.hardware.protocol.HexUtils
import com.omnibuds.android.hardware.protocol.TwsBatteryReport

/**
 * Parser for Google Fast Pair BLE Advertisement telemetry (Service UUID 0xFE2C).
 *
 * Implements Layer 1 Google Fast Pair advertisement parsing:
 * Extracts Left, Right, and Case battery levels from Fast Pair BLE advertisements without
 * requiring proprietary pairing.
 *
 * Format:
 * Type 0b0011 (0x03) or 0b0100 (0x04) (often high nibble length = 3, i.e., 0x33 or 0x34)
 * followed by 3 battery bytes:
 * - Bit 7: charging flag (0x80)
 * - Bits 0-6: battery percentage (0-100%, 0x7F = unavailable / not reported)
 */
object FastPairAdvParser {

    fun parse(serviceData: ByteArray?): TwsBatteryReport? {
        if (serviceData == null || serviceData.size < 3) return null

        // 1. Type-prefixed format at byte 0 (Length 4: [Type, Left, Right, Case])
        if (serviceData.size >= 4) {
            val typeByte = serviceData[0].toInt() and 0xFF
            if (isBatteryType(typeByte)) {
                parseTriplet(serviceData, 1)?.let { return it }
            }
        }

        // 2. Sliding window search for Fast Pair battery payload in extended service data
        // Fast Pair battery notification header: low nibble is 3 or 4, optional high nibble is length 3
        for (i in 0..serviceData.size - 4) {
            val header = serviceData[i].toInt() and 0xFF
            if (isBatteryType(header)) {
                val report = parseTriplet(serviceData, i + 1)
                if (report != null && report.hasComponentData) {
                    return report
                }
            }
        }

        // 3. Direct 3-byte triplet [Left, Right, Case] fallback only if all 3 bytes are valid battery values
        // (Must NOT match 3-byte Fast Pair Model ID)
        if (serviceData.size == 3) {
            val b0 = serviceData[0].toInt() and 0xFF
            val b1 = serviceData[1].toInt() and 0xFF
            val b2 = serviceData[2].toInt() and 0xFF
            val l = b0 and 0x7F
            val r = b1 and 0x7F
            val c = b2 and 0x7F
            // Battery percentages must be in 0..100 or 0x7F, and at least one must have bit 7 (charging)
            // or all three must be within realistic battery range with at least two != 0x7F
            val validLevels = (l in 0..100 || l == 0x7F) &&
                (r in 0..100 || r == 0x7F) &&
                (c in 0..100 || c == 0x7F)
            val hasChargingBit = (b0 and 0x80) != 0 || (b1 and 0x80) != 0 || (b2 and 0x80) != 0
            val validCount = listOf(l, r, c).count { it in 0..100 }

            if (validLevels && (hasChargingBit || validCount >= 2)) {
                return parseTriplet(serviceData, 0)
            }
        }

        return null
    }

    private fun isBatteryType(b: Int): Boolean {
        // Fast Pair battery notification type indicators:
        // Type 0b0011 (3: show notification) or 0b0100 (4: silent/update only)
        // High nibble can be 0 (no length specified) or 3 (length = 3 bytes of battery data)
        val lowNibble = b and 0x0F
        val highNibble = (b shr 4) and 0x0F

        val isTypeMatch = lowNibble == 0b0011 || lowNibble == 0b0100
        val isValidLen = highNibble == 0 || highNibble == 3

        return isTypeMatch && isValidLen
    }

    private fun parseTriplet(data: ByteArray, offset: Int): TwsBatteryReport? {
        if (offset + 3 > data.size) return null

        val b0 = data[offset].toInt() and 0xFF
        val b1 = data[offset + 1].toInt() and 0xFF
        val b2 = data[offset + 2].toInt() and 0xFF

        fun parseLevel(b: Int): Int? {
            val level = b and 0x7F
            return if (level in 0..100) level else null
        }

        val left = parseLevel(b0)
        val right = parseLevel(b1)
        val case = parseLevel(b2)

        if (left == null && right == null && case == null) return null

        val leftCharging = (b0 and 0x80) != 0
        val rightCharging = (b1 and 0x80) != 0
        val caseCharging = (b2 and 0x80) != 0

        return TwsBatteryReport(
            left = left,
            right = right,
            case = case,
            leftCharging = leftCharging,
            rightCharging = rightCharging,
            caseCharging = caseCharging,
            rawHex = HexUtils.formatHex(data.copyOfRange(offset, offset + 3)),
        )
    }
}
