package com.omnibuds.core.sdk.api

/**
 * Typed execution outcome for community SDK operations.
 *
 * Prevents ambiguous exception bubbling and enforces structured error classification.
 */
sealed interface SdkOperationResult<out T> {

    /** Successful execution with typed output payload. */
    data class Success<out T>(val value: T) : SdkOperationResult<T>

    /** The target device or firmware does not support this operation. */
    data class Unsupported(val reason: String) : SdkOperationResult<Nothing>

    /** The device identity or connection is incompatible. */
    data class IncompatibleDevice(val reason: String) : SdkOperationResult<Nothing>

    /** Operation refused due to insufficient or unverified evidence. */
    data class InsufficientEvidence(val reason: String) : SdkOperationResult<Nothing>

    /** Centralized host authorization denied the operation. */
    data class AuthorizationDenied(val reason: String) : SdkOperationResult<Nothing>

    /** The incoming request payload or device response was malformed. */
    data class MalformedData(val detail: String) : SdkOperationResult<Nothing>

    /** Operation timed out before completion. */
    data class Timeout(val timeoutMillis: Long) : SdkOperationResult<Nothing>

    /** Operation was cancelled by caller or host lifecycle. */
    data class Cancelled(val reason: String) : SdkOperationResult<Nothing>

    /** Transport or link failure during execution. */
    data class TransportFailure(val detail: String) : SdkOperationResult<Nothing>
}
