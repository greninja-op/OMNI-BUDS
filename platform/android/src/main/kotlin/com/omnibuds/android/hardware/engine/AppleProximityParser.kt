package com.omnibuds.android.hardware.engine

import com.omnibuds.android.hardware.protocol.HexUtils
import com.omnibuds.android.hardware.protocol.TwsBatteryReport

/**
 * Parser for Apple Continuity & Proximity Beacons (Manufacturer ID 0x004C).
 *
 * Implements Layer 1 Apple Proximity Beacon parsing:
 * Parses 27-byte Continuity/Proximity payload (0x0719) nibble offsets for Left, Right,
 * and Case battery levels on AirPods, Beats, and clones.
 */
object AppleProximityParser {

    const val APPLE_MANUFACTURER_ID = 0x004C
    const val CONTINUITY_PROXIMITY_TYPE: Byte = 0x07
    const val CONTINUITY_PROXIMITY_LEN: Byte = 0x19 // 25 bytes payload + 2 header = 27 bytes

    fun parse(manufacturerData: ByteArray?): TwsBatteryReport? {
        if (manufacturerData == null || manufacturerData.size < 3) return null

        // 1. Locate the Continuity Proximity header (0x07, 0x19)
        var payloadOffset = -1
        for (i in 0..manufacturerData.size - 2) {
            if (manufacturerData[i] == CONTINUITY_PROXIMITY_TYPE &&
                manufacturerData[i + 1] == CONTINUITY_PROXIMITY_LEN
            ) {
                payloadOffset = i + 2
                break
            }
        }

        // If not found, check if manufacturerData is already the 25-byte proximity payload without the 2-byte type/len header
        val payload = when {
            payloadOffset >= 0 -> manufacturerData.copyOfRange(payloadOffset, manufacturerData.size)
            manufacturerData.size == 25 -> manufacturerData
            else -> null
        }

        if (payload != null && payload.size >= 6) {
            // Standard Apple AirPods Continuity layout:
            // payload[4]: [Right: high nibble (0-10, 0xF), Left: low nibble (0-10, 0xF)]
            // payload[5]: [Charging: high nibble, Case: low nibble (0-10, 0xF)]
            // Nibble values: 0..10 represent 0%..100% (value * 10), 0xF = not available
            val bA = payload[4].toInt() and 0xFF
            val bB = payload[5].toInt() and 0xFF

            val leftNibble = bA and 0x0F
            val rightNibble = (bA shr 4) and 0x0F
            val caseNibble = bB and 0x0F
            val chargeNibble = (bB shr 4) and 0x0F

            fun nibbleToPercent(n: Int): Int? = when {
                n in 0..10 -> n * 10
                else -> null
            }

            val leftLevel = nibbleToPercent(leftNibble)
            val rightLevel = nibbleToPercent(rightNibble)
            val caseLevel = nibbleToPercent(caseNibble)

            // Must have at least one valid level
            if (leftLevel != null || rightLevel != null || caseLevel != null) {
                val leftChg = (chargeNibble and 0x01) != 0
                val rightChg = (chargeNibble and 0x02) != 0
                val caseChg = (chargeNibble and 0x04) != 0

                return TwsBatteryReport(
                    left = leftLevel,
                    right = rightLevel,
                    case = caseLevel,
                    leftCharging = leftChg,
                    rightCharging = rightChg,
                    caseCharging = caseChg,
                    rawHex = HexUtils.formatHex(manufacturerData),
                )
            }
        }

        // 2. Fallback for third-party clones: 3 consecutive percentage bytes [L, R, C]
        // Only if size matches a short beacon (e.g. 3 to 5 bytes)
        if (manufacturerData.size in 3..5) {
            for (i in 0..manufacturerData.size - 3) {
                val rawL = manufacturerData[i].toInt() and 0xFF
                val rawR = manufacturerData[i + 1].toInt() and 0xFF
                val rawC = manufacturerData[i + 2].toInt() and 0xFF

                val left = (rawL and 0x7F).takeIf { it in 0..100 }
                val right = (rawR and 0x7F).takeIf { it in 0..100 }
                val case = (rawC and 0x7F).takeIf { it in 0..100 }

                if ((left != null && right != null) || (left != null && case != null)) {
                    return TwsBatteryReport(
                        left = left,
                        right = right,
                        case = case,
                        leftCharging = (rawL and 0x80) != 0,
                        rightCharging = (rawR and 0x80) != 0,
                        caseCharging = (rawC and 0x80) != 0,
                        rawHex = HexUtils.formatHex(manufacturerData),
                    )
                }
            }
        }

        return null
    }
}
