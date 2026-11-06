package com.omnibuds.android.hardware.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FastPairAdvParserTest {

    @Test
    fun `parses 3-byte direct triplet advertisement`() {
        // Left: 80% (0x50), Right: 90% (0x5A), Case: 100% charging (0x80 | 0x64 = 0xE4)
        val payload = byteArrayOf(0x50, 0x5A, 0xE4.toByte())
        val report = FastPairAdvParser.parse(payload)

        assertNotNull(report)
        assertEquals(80, report?.left)
        assertEquals(90, report?.right)
        assertEquals(100, report?.case)
        assertFalse(report?.leftCharging == true)
        assertFalse(report?.rightCharging == true)
        assertTrue(report?.caseCharging == true)
    }

    @Test
    fun `parses type-prefixed Fast Pair battery notification`() {
        // Type 0b0011 (0x03), Left: 75% charging (0xCB), Right: 70% (0x46), Case: 50% charging (0xB2)
        val payload = byteArrayOf(0x03, 0xCB.toByte(), 0x46, 0xB2.toByte())
        val report = FastPairAdvParser.parse(payload)

        assertNotNull(report)
        assertEquals(75, report?.left)
        assertEquals(70, report?.right)
        assertEquals(50, report?.case)
        assertTrue(report?.leftCharging == true)
        assertFalse(report?.rightCharging == true)
        assertTrue(report?.caseCharging == true)
    }

    @Test
    fun `parses length-prefixed 0x33 Fast Pair battery notification`() {
        // Length 3 + Type 0b0011 -> 0x33, Left: 90%, Right: 85%, Case: 60%
        val payload = byteArrayOf(0x33, 0x5A, 0x55, 0x3C)
        val report = FastPairAdvParser.parse(payload)

        assertNotNull(report)
        assertEquals(90, report?.left)
        assertEquals(85, report?.right)
        assertEquals(60, report?.case)
    }

    @Test
    fun `parses battery notification in extended payload with salt`() {
        // Salt bytes (8 bytes) followed by Fast Pair battery header 0x33 and triplet
        val payload = byteArrayOf(
            0x10, 0x20, 0x30, 0x40, 0x50, 0x60, 0x70, 0x80.toByte(),
            0x33, 0x46, 0x46, 0x64,
        )
        val report = FastPairAdvParser.parse(payload)

        assertNotNull(report)
        assertEquals(70, report?.left)
        assertEquals(70, report?.right)
        assertEquals(100, report?.case)
    }

    @Test
    fun `handles unavailable component with 0x7F`() {
        // Left: 85%, Right: 85%, Case: unavailable (0x7F)
        val payload = byteArrayOf(0x04, 0x55, 0x55, 0x7F)
        val report = FastPairAdvParser.parse(payload)

        assertNotNull(report)
        assertEquals(85, report?.left)
        assertEquals(85, report?.right)
        assertNull(report?.case)
    }

    @Test
    fun `returns null for invalid or empty service data`() {
        assertNull(FastPairAdvParser.parse(null))
        assertNull(FastPairAdvParser.parse(byteArrayOf(0x01, 0x02)))
        assertNull(FastPairAdvParser.parse(byteArrayOf(0x7F, 0x7F, 0x7F)))
    }
}
