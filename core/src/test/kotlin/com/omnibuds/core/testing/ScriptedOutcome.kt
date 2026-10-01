package com.omnibuds.core.testing

import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OperationOutcome
import kotlinx.coroutines.yield

/**
 * A queue of verdicts a test scripts for one operation: succeed, fail, or be cancelled.
 *
 * TEST DOUBLE SUPPORT ONLY, and the reason it exists is the rule in Phase 1 prompt section
 * 53: a double may never invent a happy path silently. The default shape of every fake is
 * "return what the in-memory state actually says", and the moment a test needs an operation
 * to *not* work, that need is expressed here as a queued step rather than by editing a fake
 * to return `Success` on a path that should have failed. Draining the queue is also how a
 * test notices it scripted fewer steps than it used.
 *
 * Nothing is queued by default, and [next] on an empty queue raises rather than returning a
 * plausible-looking outcome: an invented `Success` is the failure mode this helper exists to
 * make impossible, so the helper refuses to be the place it happens.
 *
 * [next] is suspending and yields first, so a caller whose coroutine was cancelled
 * propagates `CancellationException` instead of consuming a step - the same contract a real
 * transport owes (specs.md section 5.3: a cancelled operation reports cancelled, never
 * success).
 */
class ScriptedOutcome<T> {

    private val steps: ArrayDeque<OperationOutcome<T>> = ArrayDeque()

    /** Steps still queued, for a test that wants to assert the script was fully consumed. */
    val remaining: Int
        get() = steps.size

    /** Whether [next] would return a scripted step rather than raise. */
    val hasNext: Boolean
        get() = steps.isNotEmpty()

    /** The queued steps, oldest first, as a snapshot. */
    fun scripted(): List<OperationOutcome<T>> = steps.toList()

    /** Queue a step where the operation completed with [value]. */
    fun succeed(value: T): ScriptedOutcome<T> = apply { steps.addLast(OperationOutcome.Success(value)) }

    /** Queue a step where the operation completed with [error]. */
    fun fail(error: OmniBudsError): ScriptedOutcome<T> =
        apply { steps.addLast(OperationOutcome.Failure(error)) }

    /** Queue a step where the operation was cancelled - not a failure, and not a success. */
    fun cancel(): ScriptedOutcome<T> = apply { steps.addLast(OperationOutcome.Cancelled) }

    /**
     * Take the next scripted step, oldest first.
     *
     * @throws IllegalStateException when nothing is scripted, by design: the alternative is
     * a default `Success` handed to a test that asked for nothing.
     */
    suspend fun next(): OperationOutcome<T> {
        yield()
        return steps.removeFirstOrNull() ?: throw IllegalStateException(
            "ScriptedOutcome is empty: nothing was scripted for this call, and a test double " +
                "invents no happy path (Phase 1 prompt section 53).",
        )
    }
}
