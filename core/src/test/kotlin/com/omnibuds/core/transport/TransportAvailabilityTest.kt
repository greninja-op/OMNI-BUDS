package com.omnibuds.core.transport

import com.omnibuds.core.common.TransportKind

import com.omnibuds.core.common.OmniBudsErrorCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The coupling between availability and reason in [TransportAvailability], which is the
 * structural form of PROTO-XPORT-006 and PROTO-XPORT-007: a refused channel is recorded
 * with the category that refused it, and never becomes a silent fallback.
 *
 * Tier T1. No channel is opened, probed or assumed anywhere here.
 */
class TransportAvailabilityTest {

    @Test
    fun anAvailableTransportCannotCarryAReason() {
        assertFailsWith<IllegalArgumentException> {
            TransportAvailability(
                kind = TransportKind.RFCOMM,
                available = true,
                reason = OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE,
            )
        }
        assertNull(TransportAvailability.available(TransportKind.RFCOMM).reason)
    }

    @Test
    fun anUnavailableTransportMustSayWhy() {
        assertFailsWith<IllegalArgumentException> {
            TransportAvailability(kind = TransportKind.GATT, available = false, reason = null)
        }
        // A refusal with no category is the swallowed failure this rule exists to prevent.
        assertEquals(
            OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE,
            TransportAvailability.unavailable(
                TransportKind.GATT,
                OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE,
            ).reason,
        )
    }

    @Test
    fun theRefusalCategoriesTransportRulesAllowAreAllExpressible() {
        val refusals = listOf(
            OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE,
            OmniBudsErrorCategory.GATT_FAILURE,
            OmniBudsErrorCategory.RFCOMM_FAILURE,
            OmniBudsErrorCategory.TIMEOUT,
            OmniBudsErrorCategory.PERMISSION_DENIED,
        )

        // PROTO-XPORT-006 maps a platform presentation result onto one of these, with the
        // transport named in the record rather than inferred from a default.
        refusals.forEach { category ->
            val record = TransportAvailability.unavailable(TransportKind.CLASSIC_BLUETOOTH, category)
            assertFalse(record.available)
            assertTrue(record.isRefused)
            assertEquals(category, record.reason)
            assertEquals(TransportKind.CLASSIC_BLUETOOTH, record.kind)
        }
    }

    @Test
    fun anUndeterminedChannelKindCannotBeReportedAsUsable() {
        assertFailsWith<IllegalArgumentException> {
            TransportAvailability(kind = TransportKind.UNKNOWN, available = true, reason = null)
        }
        // Recording that no channel could be established is honest and is permitted.
        val noneEstablished = TransportAvailability.unavailable(
            TransportKind.UNKNOWN,
            OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE,
        )
        assertTrue(noneEstablished.isRefused)
    }

    @Test
    fun eachCandidateIsRecordedSeparatelyInsteadOfOneChannelStandingInForAnother() {
        val gatt = TransportAvailability.unavailable(
            TransportKind.GATT,
            OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE,
        )
        val rfcomm = TransportAvailability.available(TransportKind.RFCOMM)

        // Two records for one session (PROTO-XPORT-005): the GATT absence is not repaired
        // by rewriting this row, and it is not reported as UNSUPPORTED_FEATURE either.
        assertNotEquals(gatt, rfcomm)
        assertEquals(OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE, gatt.reason)
        assertTrue(rfcomm.available)
    }
}
