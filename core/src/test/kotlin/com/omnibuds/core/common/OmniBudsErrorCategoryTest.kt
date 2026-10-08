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
    fun theCategorySetIsExactlyTheDocumentedOne() {
        val expected = setOf(
            // ADR-P0-012, the thirteen canonical categories
            "BLUETOOTH_DISABLED", "PERMISSION_DENIED", "DEVICE_DISCONNECTED",
            "TRANSPORT_UNAVAILABLE", "GATT_FAILURE", "RFCOMM_FAILURE", "PROTOCOL_MISMATCH",
            "UNSUPPORTED_FEATURE", "WRITE_REJECTED", "VERIFICATION_FAILED", "TIMEOUT",
            "FIRMWARE_MISMATCH", "CODEC_UNAVAILABLE",
            // ADR-P1-006, the three the Phase 1 prompt adds
            "READ_FAILED", "UNKNOWN_DEVICE", "INVALID_STATE",
            // ADR-P2-004, the platform-failure categories Phase 2 needs
            "ADAPTER_UNAVAILABLE", "UNSUPPORTED_OPERATION", "PLATFORM_API_UNAVAILABLE",
            "CONNECTION_UNAVAILABLE", "RESOURCE_UNAVAILABLE", "PLATFORM_EXCEPTION",
            "UNKNOWN_FAILURE",
            // ADR-P10-007, the audio-observation categories Phase 10 needs
            "AUDIO_OBSERVATION_FAILED", "LE_AUDIO_UNAVAILABLE", "AUDIO_STATE_CONFLICT",
        )

        assertEquals(expected, OmniBudsErrorCategory.entries.map { it.name }.toSet())
    }

    @Test
    fun everyCategoryDeclaresItsRetryClassAndNoneIsUnspecified() {
        val expected = mapOf(
            OmniBudsErrorCategory.READ_FAILED to RetryClass.SAFE_TO_RETRY,
            // Phase 10: observation is read-only, so re-reading after a failed
            // observation has no side effects to compound (ADR-P10-007).
            OmniBudsErrorCategory.AUDIO_OBSERVATION_FAILED to RetryClass.SAFE_TO_RETRY,

            OmniBudsErrorCategory.BLUETOOTH_DISABLED to RetryClass.RETRY_AFTER_REREAD,
            OmniBudsErrorCategory.DEVICE_DISCONNECTED to RetryClass.RETRY_AFTER_REREAD,
            OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE to RetryClass.RETRY_AFTER_REREAD,
            OmniBudsErrorCategory.GATT_FAILURE to RetryClass.RETRY_AFTER_REREAD,
            OmniBudsErrorCategory.RFCOMM_FAILURE to RetryClass.RETRY_AFTER_REREAD,
            OmniBudsErrorCategory.TIMEOUT to RetryClass.RETRY_AFTER_REREAD,
            OmniBudsErrorCategory.ADAPTER_UNAVAILABLE to RetryClass.RETRY_AFTER_REREAD,
            OmniBudsErrorCategory.CONNECTION_UNAVAILABLE to RetryClass.RETRY_AFTER_REREAD,
            OmniBudsErrorCategory.RESOURCE_UNAVAILABLE to RetryClass.RETRY_AFTER_REREAD,
            OmniBudsErrorCategory.PLATFORM_EXCEPTION to RetryClass.RETRY_AFTER_REREAD,

            OmniBudsErrorCategory.PERMISSION_DENIED to RetryClass.NEVER_RETRY,
            OmniBudsErrorCategory.PROTOCOL_MISMATCH to RetryClass.NEVER_RETRY,
            OmniBudsErrorCategory.UNSUPPORTED_FEATURE to RetryClass.NEVER_RETRY,
            OmniBudsErrorCategory.WRITE_REJECTED to RetryClass.NEVER_RETRY,
            OmniBudsErrorCategory.VERIFICATION_FAILED to RetryClass.NEVER_RETRY,
            OmniBudsErrorCategory.FIRMWARE_MISMATCH to RetryClass.NEVER_RETRY,
            OmniBudsErrorCategory.CODEC_UNAVAILABLE to RetryClass.NEVER_RETRY,
            OmniBudsErrorCategory.UNKNOWN_DEVICE to RetryClass.NEVER_RETRY,
            OmniBudsErrorCategory.INVALID_STATE to RetryClass.NEVER_RETRY,
            OmniBudsErrorCategory.UNSUPPORTED_OPERATION to RetryClass.NEVER_RETRY,
            OmniBudsErrorCategory.PLATFORM_API_UNAVAILABLE to RetryClass.NEVER_RETRY,
            OmniBudsErrorCategory.UNKNOWN_FAILURE to RetryClass.NEVER_RETRY,
            // Phase 10: LE Audio absence will not change without an OS upgrade,
            // and a state conflict is a data condition, not a failure.
            OmniBudsErrorCategory.LE_AUDIO_UNAVAILABLE to RetryClass.NEVER_RETRY,
            OmniBudsErrorCategory.AUDIO_STATE_CONFLICT to RetryClass.NEVER_RETRY,
        )

        assertFullCoverage(expected)
        expected.forEach { (category, retry) ->
            assertEquals(retry, category.retryClass, category.name)
        }
    }

    @Test
    fun aWriteOrUnknownEffectIsNeverBlindlyRetried() {
        val blindRetry = OmniBudsErrorCategory.entries.filter { it.retryClass == RetryClass.SAFE_TO_RETRY }

        // Exactly two categories may be repeated without first checking what the device did:
        // a failed read, and a failed audio observation. Both are side-effect-free reads;
        // widening this set to anything side-effecting is how a command eventually gets re-sent.
        assertEquals(
            setOf(OmniBudsErrorCategory.READ_FAILED, OmniBudsErrorCategory.AUDIO_OBSERVATION_FAILED),
            blindRetry.toSet(),
        )
    }

    @Test
    fun everyCategoryDeclaresWhetherTheDeviceStateIsNowUncertain() {
        val expected = mapOf(
            // State on the device may have changed, or is no longer knowable from here.
            OmniBudsErrorCategory.DEVICE_DISCONNECTED to true,
            OmniBudsErrorCategory.GATT_FAILURE to true,
            OmniBudsErrorCategory.RFCOMM_FAILURE to true,
            OmniBudsErrorCategory.PROTOCOL_MISMATCH to true,
            OmniBudsErrorCategory.WRITE_REJECTED to true,
            OmniBudsErrorCategory.VERIFICATION_FAILED to true,
            OmniBudsErrorCategory.TIMEOUT to true,
            OmniBudsErrorCategory.CONNECTION_UNAVAILABLE to true,
            OmniBudsErrorCategory.PLATFORM_EXCEPTION to true,
            OmniBudsErrorCategory.UNKNOWN_FAILURE to true,

            OmniBudsErrorCategory.BLUETOOTH_DISABLED to false,
            OmniBudsErrorCategory.PERMISSION_DENIED to false,
            OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE to false,
            OmniBudsErrorCategory.UNSUPPORTED_FEATURE to false,
            OmniBudsErrorCategory.READ_FAILED to false,
            OmniBudsErrorCategory.FIRMWARE_MISMATCH to false,
            OmniBudsErrorCategory.CODEC_UNAVAILABLE to false,
            OmniBudsErrorCategory.UNKNOWN_DEVICE to false,
            OmniBudsErrorCategory.INVALID_STATE to false,
            OmniBudsErrorCategory.ADAPTER_UNAVAILABLE to false,
            OmniBudsErrorCategory.UNSUPPORTED_OPERATION to false,
            OmniBudsErrorCategory.PLATFORM_API_UNAVAILABLE to false,
            OmniBudsErrorCategory.RESOURCE_UNAVAILABLE to false,
            // Phase 10: observation is read-only, so it can never leave the
            // device state uncertain (ADR-P10-007).
            OmniBudsErrorCategory.AUDIO_OBSERVATION_FAILED to false,
            OmniBudsErrorCategory.LE_AUDIO_UNAVAILABLE to false,
            OmniBudsErrorCategory.AUDIO_STATE_CONFLICT to false,
        )

        assertFullCoverage(expected)
        expected.forEach { (category, invalidates) ->
            assertEquals(invalidates, category.invalidatesSession, category.name)
        }
    }

    /**
     * Guards against the failure mode where a new category is added and silently joins neither
     * branch of an assertion list. Both tables above must name every entry of the enum exactly once.
     */
    private fun <V> assertFullCoverage(expected: Map<OmniBudsErrorCategory, V>) {
        val all = OmniBudsErrorCategory.entries.toSet()
        assertEquals(all, expected.keys, "every category must appear in this table")
        assertEquals(all.size, expected.size, "a category is listed more than once")
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
