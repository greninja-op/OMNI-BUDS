package com.omnibuds.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the retry and session-invalidation table.
 *
 * If a category's retry class changes, this test fails rather than letting the change
 * ride along quietly: the prohibition on blindly re-sending a write is a product safety
 * rule, not an implementation detail (ADR-P1-006, docs/phases/phase-0/specs.md section 4).
 */
class OmniBudsErrorCategoryTest {

    @Test
    fun theCanonicalCategoriesAreAllPresent() {
        val expected = setOf(
            "BLUETOOTH_DISABLED", "PERMISSION_DENIED", "DEVICE_DISCONNECTED",
            "TRANSPORT_UNAVAILABLE", "GATT_FAILURE", "RFCOMM_FAILURE", "PROTOCOL_MISMATCH",
            "UNSUPPORTED_FEATURE", "READ_FAILED", "WRITE_REJECTED", "VERIFICATION_FAILED",
            "TIMEOUT", "FIRMWARE_MISMATCH", "CODEC_UNAVAILABLE", "UNKNOWN_DEVICE",
            "INVALID_STATE",
        )

        assertEquals(expected, OmniBudsErrorCategory.entries.map { it.name }.toSet())
    }

    @Test
    fun everyCategoryDeclaresItsRetryClassAndNoneIsUnspecified() {
        val expected = mapOf(
            OmniBudsErrorCategory.READ_FAILED to RetryClass.SAFE_TO_RETRY,
            OmniBudsErrorCategory.BLUETOOTH_DISABLED to RetryClass.RETRY_AFTER_REREAD,
            OmniBudsErrorCategory.DEVICE_DISCONNECTED to RetryClass.RETRY_AFTER_REREAD,
            OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE to RetryClass.RETRY_AFTER_REREAD,
            OmniBudsErrorCategory.GATT_FAILURE to RetryClass.RETRY_AFTER_REREAD,
            OmniBudsErrorCategory.RFCOMM_FAILURE to RetryClass.RETRY_AFTER_REREAD,
            OmniBudsErrorCategory.TIMEOUT to RetryClass.RETRY_AFTER_REREAD,
            OmniBudsErrorCategory.PERMISSION_DENIED to RetryClass.NEVER_RETRY,
            OmniBudsErrorCategory.PROTOCOL_MISMATCH to RetryClass.NEVER_RETRY,
            OmniBudsErrorCategory.UNSUPPORTED_FEATURE to RetryClass.NEVER_RETRY,
            OmniBudsErrorCategory.WRITE_REJECTED to RetryClass.NEVER_RETRY,
            OmniBudsErrorCategory.VERIFICATION_FAILED to RetryClass.NEVER_RETRY,
            OmniBudsErrorCategory.FIRMWARE_MISMATCH to RetryClass.NEVER_RETRY,
            OmniBudsErrorCategory.CODEC_UNAVAILABLE to RetryClass.NEVER_RETRY,
            OmniBudsErrorCategory.UNKNOWN_DEVICE to RetryClass.NEVER_RETRY,
            OmniBudsErrorCategory.INVALID_STATE to RetryClass.NEVER_RETRY,
        )

        // A category missing from this table would carry an unchecked retry class, which is
        // how a side-effecting write starts being re-sent on a timeout.
        assertEquals(OmniBudsErrorCategory.entries.toSet(), expected.keys, "every category must be pinned")
        expected.forEach { (category, retry) ->
            assertEquals(retry, category.retryClass, category.name)
        }
    }

    @Test
    fun onlyIdempotentReadsMayBeRetriedWithoutCheckingStateFirst() {
        val blindRetry = OmniBudsErrorCategory.entries.filter { it.retryClass == RetryClass.SAFE_TO_RETRY }

        assertEquals(setOf(OmniBudsErrorCategory.READ_FAILED), blindRetry.toSet())
    }

    @Test
    fun aTimedOutWriteIsResolvedByReReadingRatherThanByResending() {
        assertEquals(RetryClass.RETRY_AFTER_REREAD, OmniBudsErrorCategory.TIMEOUT.retryClass)
        assertTrue(OmniBudsErrorCategory.TIMEOUT.invalidatesSession)
    }

    @Test
    fun categoriesThatLeaveTheDeviceStateUncertainSaySo() {
        listOf(
            OmniBudsErrorCategory.DEVICE_DISCONNECTED,
            OmniBudsErrorCategory.GATT_FAILURE,
            OmniBudsErrorCategory.RFCOMM_FAILURE,
            OmniBudsErrorCategory.PROTOCOL_MISMATCH,
            OmniBudsErrorCategory.WRITE_REJECTED,
            OmniBudsErrorCategory.VERIFICATION_FAILED,
            OmniBudsErrorCategory.TIMEOUT,
        ).forEach { category ->
            assertTrue(category.invalidatesSession, "$category leaves device state uncertain")
        }

        listOf(
            OmniBudsErrorCategory.BLUETOOTH_DISABLED,
            OmniBudsErrorCategory.PERMISSION_DENIED,
            OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE,
            OmniBudsErrorCategory.UNSUPPORTED_FEATURE,
            OmniBudsErrorCategory.READ_FAILED,
            OmniBudsErrorCategory.FIRMWARE_MISMATCH,
            OmniBudsErrorCategory.CODEC_UNAVAILABLE,
            OmniBudsErrorCategory.UNKNOWN_DEVICE,
            OmniBudsErrorCategory.INVALID_STATE,
        ).forEach { category ->
            assertFalse(category.invalidatesSession, "$category changes nothing on the device")
        }
    }

    @Test
    fun anErrorCarriesItsOperationAndTransportAndNeverInventsAValue() {
        val error = OmniBudsError(
            category = OmniBudsErrorCategory.TIMEOUT,
            operationId = "example-protocol.read-battery",
            detail = "no response within the declared window",
            transport = TransportKind.RFCOMM,
        )

        assertEquals("example-protocol.read-battery", error.operationId)
        assertEquals(TransportKind.RFCOMM, error.transport)
        assertEquals(1, error.attempts)
        assertTrue(error.invalidatesSession)
    }

    @Test
    fun anErrorWithoutARecordedAttemptCountIsNotZeroAttempts() {
        assertFailsWith<IllegalArgumentException> {
            OmniBudsError(
                category = OmniBudsErrorCategory.READ_FAILED,
                operationId = "example-protocol.read-state",
                attempts = 0,
            )
        }
    }

    @Test
    fun theFactoryLeavesOptionalDetailUnknownRatherThanEmpty() {
        val error = OmniBudsError.of(
            category = OmniBudsErrorCategory.PERMISSION_DENIED,
            operationId = "example-protocol.connect",
        )

        assertNull(error.detail)
        assertEquals(TransportKind.UNKNOWN, error.transport)
        assertNotNull(error.category)
    }
}
