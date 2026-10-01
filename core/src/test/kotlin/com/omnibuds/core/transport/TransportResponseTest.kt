package com.omnibuds.core.transport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * [TransportResponse] equality, and the difference between "no body arrived" and "an empty
 * body arrived" (Phase 1 prompt section 49, `docs/phases/phase-0/specs.md` section 2.2,
 * PROTO-PERSIST-001 for what [TransportResponse.acknowledged] may and may not mean).
 *
 * Tier T1. Fictional command ids, inert fixture bytes; no protocol is encoded.
 */
class TransportResponseTest {

    @Test
    fun identicalBodyContentIsEqualAcrossSeparateArrays() {
        val first = response(byteArrayOf(9, 8, 7))
        val second = response(byteArrayOf(9, 8, 7))

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun differentBodyContentIsNotEqual() {
        val wanted = response(byteArrayOf(9, 8, 7))

        assertNotEquals(wanted, response(byteArrayOf(9, 8, 8)))
        assertNotEquals(wanted, response(byteArrayOf(9, 8)))
        assertNotEquals(wanted, response(byteArrayOf(9, 8, 7, 7)))
    }

    @Test
    fun anAbsentBodyAndAnEmptyBodyAreDifferentStatements() {
        val nothingReported = response(null)
        val emptyBodyReported = response(byteArrayOf())

        // Collapsing these two would let "the device said nothing" be read as though a
        // real zero-length answer had arrived.
        assertNotEquals(nothingReported, emptyBodyReported)
        assertNotEquals(nothingReported.hashCode(), emptyBodyReported.hashCode())
        assertFalse(nothingReported.hasPayload)
        assertTrue(emptyBodyReported.hasPayload)
        assertEquals(0, nothingReported.payloadByteCount)
        assertEquals(0, emptyBodyReported.payloadByteCount)
    }

    @Test
    fun acknowledgementParticipatesInIdentityBecauseItIsAClaim() {
        val acknowledged = response(byteArrayOf(1))
        val unacknowledged = response(byteArrayOf(1)).copy(acknowledged = false)

        assertNotEquals(acknowledged, unacknowledged)
        assertTrue(acknowledged.acknowledged)
    }

    @Test
    fun anAcknowledgementReportsDeliveryWithoutReportingAppliedState() {
        val acknowledged = response(byteArrayOf(1))

        // Ring 2 of the persistence ladder at most: "command accepted" is not "value
        // applied" and never "survives a reconnect" (PROTO-PERSIST-001). This type offers
        // no field that could say more, which is the guarantee being asserted.
        assertEquals(1, acknowledged.payloadByteCount)
        assertTrue(acknowledged.acknowledged)
        assertNotEquals(acknowledged, response(null))
    }

    @Test
    fun aBlankCorrelationKeyIsRefused() {
        assertFailsWith<IllegalArgumentException> {
            TransportResponse(commandId = "", payload = byteArrayOf(1), acknowledged = true)
        }
    }

    @Test
    fun theTextFormReportsLengthsAndNotBodyBytes() {
        val rendered = response(byteArrayOf(0x55, 0x7F)).toString()

        assertTrue(rendered.contains("payloadBytes=2"), rendered)
        assertTrue(rendered.contains("payloadReported=true"), rendered)
        // 0x55 is 85 and 0x7F is 127 as signed-or-unsigned text; neither may be printed.
        assertFalse(rendered.contains("85"), rendered)
        assertFalse(rendered.contains("127"), rendered)
    }

    private fun response(body: ByteArray?): TransportResponse = TransportResponse(
        commandId = "example-vendor.read-status",
        payload = body,
        acknowledged = true,
    )
}
