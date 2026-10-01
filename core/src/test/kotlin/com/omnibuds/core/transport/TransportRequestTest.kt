package com.omnibuds.core.transport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The value discipline of [TransportRequest] (Phase 1 prompt sections 25 and 49,
 * `docs/phases/phase-0/specs.md` section 2.2, PROTO-NOMAGIC-002).
 *
 * Tier T1. The command ids are fictional names and the payloads are inert fixture bytes:
 * they encode no protocol and stand for no real device's wire format.
 */
class TransportRequestTest {

    @Test
    fun twoRequestsWithIdenticalPayloadContentAreEqual() {
        val first = request(bytesOf(1, 2, 3))
        val second = request(byteArrayOf(1, 2, 3))

        // The default data-class implementation would compare array references here and
        // report these two as unequal.
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun differentPayloadContentUnderOneCommandIdIsNotEqual() {
        val wanted = request(bytesOf(1, 2, 3))
        val other = request(bytesOf(1, 2, 4))
        val longer = request(bytesOf(1, 2, 3, 0))

        assertNotEquals(wanted, other)
        assertNotEquals(wanted, longer)
        assertNotEquals(other.hashCode(), longer.hashCode())
    }

    @Test
    fun anEmptyPayloadIsARealRequestAndIsStillComparedByContent() {
        val empty = request(byteArrayOf())
        val alsoEmpty = request(byteArrayOf())

        assertEquals(empty, alsoEmpty)
        assertEquals(empty.hashCode(), alsoEmpty.hashCode())
        assertNotEquals(empty, request(bytesOf(0)))
    }

    @Test
    fun commandIdAndTimeoutBoundsArePartOfTheIdentity() {
        val base = request(bytesOf(7), timeoutMillis = 400)

        assertNotEquals(base, base.copy(commandId = "example-vendor.other-command"))
        assertNotEquals(base, base.copy(timeoutMillis = 500))
        assertNotEquals(base, base.copy(timeoutMillis = null))
        assertNotEquals(base.copy(timeoutMillis = null), base.copy(timeoutMillis = 1))
    }

    @Test
    fun aTimeoutOfZeroOrBelowIsRefusedRatherThanTreatedAsNoBound() {
        assertFailsWith<IllegalArgumentException> { request(bytesOf(1), timeoutMillis = 0) }
        assertFailsWith<IllegalArgumentException> { request(bytesOf(1), timeoutMillis = -1) }
        // null is the honest "no bound stated here", and is not the same claim as zero.
        assertEquals(null, request(bytesOf(1), timeoutMillis = null).timeoutMillis)
    }

    @Test
    fun aBlankCommandIdCannotCarryNoIdentity() {
        assertFailsWith<IllegalArgumentException> { TransportRequest("   ", byteArrayOf(), null) }
    }

    @Test
    fun theTextFormReportsTheLengthAndNotTheBytes() {
        val rendered = request(bytesOf(0xAA, 0xBB, 0xCC)).toString()

        // SEC-LOG-004: raw packet content must not be reachable through a string form.
        // 0xAA arrives as the signed byte -86, which would be visible if bytes were printed.
        assertTrue(rendered.contains("payloadBytes=3"), rendered)
        assertTrue(!rendered.contains("-86"), "payload byte leaked into: $rendered")
        assertTrue(!rendered.contains("170"), "payload byte leaked into: $rendered")
    }

    private fun request(payload: ByteArray, timeoutMillis: Long? = DEFAULT_TIMEOUT): TransportRequest =
        TransportRequest(
            commandId = "example-vendor.read-status",
            payload = payload,
            timeoutMillis = timeoutMillis,
        )

    private fun bytesOf(vararg values: Int): ByteArray = ByteArray(values.size) { index -> values[index].toByte() }

    private companion object {
        const val DEFAULT_TIMEOUT = 250L
    }
}
