package com.omnibuds.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The three-case outcome model (ADR-P1-004).
 *
 * The point of these tests is that nothing here can silently turn a failure into a
 * value: a failure that maps to a default, or a cancellation that reads as success, is
 * how fabricated device state begins.
 */
class OperationOutcomeTest {

    private val failure: OperationOutcome<Int> = OperationOutcome.Failure(
        OmniBudsError(
            category = OmniBudsErrorCategory.READ_FAILED,
            operationId = "example-protocol.read-state",
        ),
    )

    @Test
    fun successCarriesTheValueAndNothingElse() {
        val outcome: OperationOutcome<Int> = OperationOutcome.Success(7)

        assertIs<OperationOutcome.Success<Int>>(outcome)
        assertEquals(7, outcome.value)
        assertEquals(7, outcome.valueOrNull)
        assertEquals(7, outcome.getOrNull())
        assertNull(outcome.errorOrNull)
        assertTrue(outcome.isSuccess)
    }

    @Test
    fun failureExposesItsStructuredErrorAndNoValue() {
        val outcome: OperationOutcome<Int> = failure

        assertIs<OperationOutcome.Failure>(outcome)
        assertNull(outcome.valueOrNull)
        assertNull(outcome.getOrNull())
        assertFalse(outcome.isSuccess)
        assertEquals(OmniBudsErrorCategory.READ_FAILED, outcome.errorOrNull?.category)
    }

    @Test
    fun cancellationIsNotAFailureAndNotASuccess() {
        val outcome: OperationOutcome<Int> = OperationOutcome.Cancelled

        assertIs<OperationOutcome.Cancelled>(outcome)
        assertNull(outcome.errorOrNull)
        assertNull(outcome.valueOrNull)
        assertFalse(outcome.isSuccess)
    }

    @Test
    fun mappingOnlyTouchesSuccess() {
        assertEquals(16, (OperationOutcome.Success(8) as OperationOutcome<Int>).map { it * 2 }.valueOrNull)
        assertEquals(null, failure.map { it * 2 }.valueOrNull)
        assertEquals(OmniBudsErrorCategory.READ_FAILED, failure.map { it * 2 }.errorOrNull?.category)
        assertIs<OperationOutcome.Cancelled>((OperationOutcome.Cancelled as OperationOutcome<Int>).map { it * 2 })
    }

    @Test
    fun thereIsNoFourthCase() {
        // Exhaustiveness is what forces a caller to consider cancellation; if a fourth
        // case is ever added, this when-expression stops compiling until it is handled.
        val described = when (failure) {
            is OperationOutcome.Success<*> -> "success"
            is OperationOutcome.Failure -> "failure"
            OperationOutcome.Cancelled -> "cancelled"
        }

        assertEquals("failure", described)
    }
}
