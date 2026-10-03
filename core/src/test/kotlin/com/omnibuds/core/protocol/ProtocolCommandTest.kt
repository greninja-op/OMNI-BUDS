package com.omnibuds.core.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Runtime command/response contracts under prompt §11 and §15: correlation is required, payloads are bounded,
 * byte-content governs equality, and no bytes reach a `toString`. Tier T1.
 */
class ProtocolCommandTest {

    @Test
    fun byteIdenticalCommandsAreEqualRegardlessOfArrayIdentity() {
        val a = ProtocolCommand("read.x", byteArrayOf(1, 2, 3), "corr-1", null)
        val b = ProtocolCommand("read.x", byteArrayOf(1, 2, 3), "corr-1", null)

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun aDifferentCorrelationIdMakesADifferentCommand() {
        val a = ProtocolCommand("read.x", byteArrayOf(1), "corr-1", null)
        val b = ProtocolCommand("read.x", byteArrayOf(1), "corr-2", null)

        assertFalse(a == b, "correlation id participates in identity (prompt §11)")
    }

    @Test
    fun aBlankCorrelationIdIsRefused() {
        assertFailsWith<IllegalArgumentException> {
            ProtocolCommand("read.x", ByteArray(0), "", null)
        }
    }

    @Test
    fun anOverLargePayloadIsRefusedAtConstruction() {
        val oversized = ByteArray(ProtocolCommand.MAX_PAYLOAD_BYTES + 1)
        assertFailsWith<IllegalArgumentException> {
            ProtocolCommand("write.x", oversized, "corr", 1_000L)
        }
    }

    @Test
    fun aZeroTimeoutIsRefusedButNullMeansUnbound() {
        assertFailsWith<IllegalArgumentException> { ProtocolCommand("c", ByteArray(0), "k", 0L) }
        assertTrue(ProtocolCommand("c", ByteArray(0), "k", null).timeoutMillis == null)
    }

    @Test
    fun toStringReportsLengthNeverPayloadBytes() {
        val secret = byteArrayOf(0x53, 0x45, 0x43) // "SEC"
        val rendered = ProtocolCommand("write.x", secret, "corr-9", 500L).toString()

        assertFalse(rendered.contains("83"), "a raw payload byte reached the string (SEC-LOG-004)")
        assertTrue(rendered.contains("payloadBytes=3"))
    }

    @Test
    fun responseDistinguishesAbsentFromEmptyPayload() {
        val absent = ProtocolResponse("c", "k", null, acknowledged = true)
        val empty = ProtocolResponse("c", "k", ByteArray(0), acknowledged = true)

        assertFalse(absent.hasPayload)
        assertTrue(empty.hasPayload)
        assertEquals(0, empty.payloadByteCount)
        assertFalse(absent == empty, "a missing answer and an empty answer are distinct states (ADR-P0-016)")
    }
}
