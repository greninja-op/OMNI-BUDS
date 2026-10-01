package com.omnibuds.core.common

/**
 * The result of an asynchronous hardware operation.
 *
 * Deliberately narrow: everything else that can go wrong - timeout, unsupported
 * feature, disconnected device, unavailable codec - is a typed
 * [OmniBudsErrorCategory] inside [Failure] rather than a separate outcome shape, so
 * that callers have exactly three cases to handle instead of thirteen
 * (ADR-P1-004).
 *
 * "Unknown" is likewise not an outcome. Unknown is a property of the *value*: a
 * read that succeeded but could not determine a measurement returns Success with
 * that field left unknown, never a special outcome.
 */
sealed interface OperationOutcome<out T> {

    /** The operation completed and [value] is what the device actually reported. */
    data class Success<out T>(val value: T) : OperationOutcome<T>

    /** The operation did not complete, or completed with a rejected result. */
    data class Failure(val error: OmniBudsError) : OperationOutcome<Nothing>

    /**
     * The operation was cancelled by the caller or by lifecycle teardown. Not a
     * failure: the device state is untouched by definition, so no re-read is owed.
     */
    data object Cancelled : OperationOutcome<Nothing>

    fun <R> map(transform: (T) -> R): OperationOutcome<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
        Cancelled -> Cancelled
    }

    val valueOrNull: T?
        get() = (this as? Success)?.value

    val errorOrNull: OmniBudsError?
        get() = (this as? Failure)?.error

    val isSuccess: Boolean
        get() = this is Success
}

/** Recover a value or null, for call sites that only ever read state. */
fun <T> OperationOutcome<T>.getOrNull(): T? = valueOrNull
