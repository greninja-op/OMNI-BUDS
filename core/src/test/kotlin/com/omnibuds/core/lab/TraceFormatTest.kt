package com.omnibuds.core.lab

import com.omnibuds.core.common.TransportKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase 20: trace format and validation tests.
 */
class TraceFormatTest {

    private fun event(id: String, seq: Long) = TraceEvent(
        eventId = id,
        sequence = seq,
        relativeMillis = seq * 10,
        direction = TraceDirection.HOST_TO_DEVICE,
        category = "test",
        payload = byteArrayOf(0x01, 0x02),
        declaredLength = 2,
        correlationId = null,
        redacted = false,
    )

    @Test
    fun `valid trace passes validation`() {
        val trace = ProtocolTrace.create(
            traceId = "t1",
            sourceType = TraceSourceType.SYNTHETIC_FIXTURE,
            transport = TransportKind.RFCOMM,
            events = listOf(event("e1", 0), event("e2", 1)),
            provenance = "test",
            nowMillis = 1000L,
        )
        assertTrue(TraceValidator.isValid(trace))
    }

    @Test
    fun `duplicate event ids rejected`() {
        val trace = ProtocolTrace.create(
            traceId = "t1",
            sourceType = TraceSourceType.SYNTHETIC_FIXTURE,
            transport = TransportKind.RFCOMM,
            events = listOf(event("e1", 0), event("e1", 1)),
            provenance = "test",
            nowMillis = 1000L,
        )
        val errors = TraceValidator.validate(trace)
        assertTrue(errors.any { it.message.contains("duplicate") })
    }

    @Test
    fun `out-of-order sequences rejected`() {
        val trace = ProtocolTrace.create(
            traceId = "t1",
            sourceType = TraceSourceType.SYNTHETIC_FIXTURE,
            transport = TransportKind.RFCOMM,
            events = listOf(event("e1", 1), event("e2", 0)),
            provenance = "test",
            nowMillis = 1000L,
        )
        assertFalse(TraceValidator.isValid(trace))
    }

    @Test
    fun `declared length mismatch refused at construction`() {
        try {
            TraceEvent(
                eventId = "e1",
                sequence = 0,
                relativeMillis = null,
                direction = TraceDirection.HOST_TO_DEVICE,
                category = "test",
                payload = byteArrayOf(0x01),
                declaredLength = 5, // Wrong.
                correlationId = null,
                redacted = false,
            )
            assertTrue(false, "should throw")
        } catch (e: IllegalArgumentException) {
            assertTrue(true)
        }
    }

    @Test
    fun `oversized payload refused`() {
        try {
            TraceEvent(
                eventId = "e1",
                sequence = 0,
                relativeMillis = null,
                direction = TraceDirection.HOST_TO_DEVICE,
                category = "test",
                payload = ByteArray(LabLimits.MAX_PAYLOAD_BYTES + 1),
                declaredLength = null,
                correlationId = null,
                redacted = false,
            )
            assertTrue(false, "should throw")
        } catch (e: IllegalArgumentException) {
            assertTrue(true)
        }
    }

    @Test
    fun `source types are distinct`() {
        // Synthetic must never equal captured.
        assertTrue(TraceSourceType.SYNTHETIC_FIXTURE != TraceSourceType.SANITIZED_CAPTURE)
        assertEquals(5, TraceSourceType.entries.size)
    }

    @Test
    fun `redaction inconsistency flagged`() {
        val trace = ProtocolTrace.create(
            traceId = "t1",
            sourceType = TraceSourceType.SYNTHETIC_FIXTURE,
            transport = TransportKind.RFCOMM,
            events = listOf(event("e1", 0)),
            provenance = "test",
            nowMillis = 1000L,
        ).copy(
            redaction = RedactionMetadata(emptyList(), structureAltered = true),
        )
        val errors = TraceValidator.validate(trace)
        assertTrue(errors.any { it.fieldPath == "redaction" })
    }
}
