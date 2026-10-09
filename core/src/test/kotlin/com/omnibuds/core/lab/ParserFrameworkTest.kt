package com.omnibuds.core.lab

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase 20: parser framework tests.
 */
class ParserFrameworkTest {

    private val parser = LengthPrefixedParser()

    @Test
    fun `valid length-prefixed message parses`() {
        // Length 3, payload 01 02 03.
        val input = byteArrayOf(0x00, 0x03, 0x01, 0x02, 0x03)
        val outcome = parser.parse(input)
        assertTrue(outcome is ParseOutcome.Parsed)
        val parsed = outcome as ParseOutcome.Parsed
        assertEquals(5, parsed.bytesConsumed)
        // Semantic meaning stays unknown — framing only.
        assertEquals(null, parsed.message.semanticMeaning)
    }

    @Test
    fun `incomplete input returns Incomplete`() {
        val input = byteArrayOf(0x00, 0x05, 0x01) // Declares 5, has 1.
        val outcome = parser.parse(input)
        assertTrue(outcome is ParseOutcome.Incomplete)
    }

    @Test
    fun `oversized declaration is malformed`() {
        val input = byteArrayOf(0x7F, 0xFF.toByte()) // 32767 > limit.
        val outcome = parser.parse(input)
        assertTrue(outcome is ParseOutcome.Malformed)
    }

    @Test
    fun `multiple messages in one buffer`() {
        val input = byteArrayOf(
            0x00, 0x01, 0x0A,
            0x00, 0x01, 0x0B,
        )
        val first = parser.parse(input)
        assertTrue(first is ParseOutcome.Parsed)
        val consumed = (first as ParseOutcome.Parsed).bytesConsumed
        assertEquals(3, consumed)
        val second = parser.parse(input.copyOfRange(consumed, input.size))
        assertTrue(second is ParseOutcome.Parsed)
    }

    @Test
    fun `empty payload parses`() {
        val input = byteArrayOf(0x00, 0x00)
        val outcome = parser.parse(input)
        assertTrue(outcome is ParseOutcome.Parsed)
        assertEquals(2, (outcome as ParseOutcome.Parsed).bytesConsumed)
    }

    @Test
    fun `limit exceeded on huge input`() {
        val input = ByteArray(parser.maxInputBytes + 1)
        val outcome = parser.parse(input)
        assertEquals(ParseOutcome.LimitExceeded, outcome)
    }
}
