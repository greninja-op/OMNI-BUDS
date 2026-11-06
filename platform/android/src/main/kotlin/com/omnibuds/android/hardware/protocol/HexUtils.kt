package com.omnibuds.android.hardware.protocol

/**
 * Utility functions for hex formatting, parsing, and bitwise manipulation.
 */
object HexUtils {

    fun formatHex(bytes: ByteArray?, separator: String = " "): String {
        if (bytes == null || bytes.isEmpty()) return ""
        return bytes.joinToString(separator) { String.format("%02X", it) }
    }

    fun parseHex(hex: String): ByteArray {
        val clean = hex.replace(" ", "")
            .replace("0x", "", ignoreCase = true)
            .replace(",", "")
            .replace(":", "")
            .trim()
        if (clean.length % 2 != 0) {
            throw IllegalArgumentException("Hex string must have an even number of characters: '$clean'")
        }
        val result = ByteArray(clean.length / 2)
        for (i in 0 until clean.length step 2) {
            result[i / 2] = clean.substring(i, i + 2).toInt(16).toByte()
        }
        return result
    }

    fun safeParseHex(hex: String): ByteArray? {
        return try {
            parseHex(hex)
        } catch (_: Exception) {
            null
        }
    }
}

