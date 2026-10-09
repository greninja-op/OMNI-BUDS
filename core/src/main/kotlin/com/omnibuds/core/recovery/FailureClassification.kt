package com.omnibuds.core.recovery

import com.omnibuds.core.common.RetryClass

/**
 * Centralized failure categories.
 *
 * Phase 34: every category has distinct handling. Cancellation is
 * cancellation, not failure. Timeouts never imply unsupported hardware;
 * permission errors never imply disconnection.
 */
enum class FailureCategory {
    PERMISSION_DENIED,
    BLUETOOTH_DISABLED,
    BLUETOOTH_UNAVAILABLE,
    DEVICE_DISCONNECTED,
    CONNECTION_TIMEOUT,
    TRANSPORT_UNAVAILABLE,
    TRANSPORT_FAILURE,
    PROTOCOL_TIMEOUT,
    PROTOCOL_MALFORMED_RESPONSE,
    PROTOCOL_UNSUPPORTED,
    DEVICE_BUSY,
    OPERATION_REJECTED,
    OPERATION_OUTCOME_UNKNOWN,
    CAPABILITY_UNAVAILABLE,
    STALE_STATE,
    PERSISTENCE_FAILURE,
    RESOURCE_EXHAUSTED,
    CANCELLED,
    BACKGROUND_RESTRICTED,
    INTERNAL_ERROR,
}

/**
 * Classified failure with minimal useful metadata.
 *
 * Never carries raw Bluetooth payloads, secrets, or credentials.
 */
data class ClassifiedFailure(
    /** Stable failure code, e.g. "TRANSPORT_FAILURE". */
    val code: String,
    val category: FailureCategory,
    val deviceId: String?,
    val sessionId: String?,
    val operationId: String?,
    val retryClass: RetryClass,
    /** True when the operation may already have taken effect. */
    val mayHaveExecuted: Boolean,
    /** True when state reconciliation is required before retry. */
    val reconciliationRequired: Boolean,
    /** Safe-to-display message; never raw payloads. */
    val displayMessage: String,
    /** Correlation ID for diagnostics. */
    val correlationId: String,
) {
    init {
        require(code.isNotBlank()) { "code must not be blank" }
        require(correlationId.isNotBlank()) { "correlationId must not be blank" }
    }
}

/**
 * Maps a category to its default retry class.
 *
 * Phase 34: retry eligibility is never inferred from "an exception
 * occurred". Side-effecting operations whose outcome is unknown require
 * re-read before any retry; non-idempotent writes never retry.
 */
object FailureClassifier {

    fun retryClassFor(category: FailureCategory): RetryClass = when (category) {
        FailureCategory.TRANSPORT_FAILURE,
        FailureCategory.CONNECTION_TIMEOUT,
        FailureCategory.PROTOCOL_TIMEOUT,
        FailureCategory.DEVICE_BUSY,
        FailureCategory.RESOURCE_EXHAUSTED,
        -> RetryClass.SAFE_TO_RETRY

        FailureCategory.OPERATION_OUTCOME_UNKNOWN,
        FailureCategory.STALE_STATE,
        -> RetryClass.RETRY_AFTER_REREAD

        FailureCategory.PERMISSION_DENIED,
        FailureCategory.BLUETOOTH_DISABLED,
        FailureCategory.BLUETOOTH_UNAVAILABLE,
        FailureCategory.DEVICE_DISCONNECTED,
        FailureCategory.TRANSPORT_UNAVAILABLE,
        FailureCategory.PROTOCOL_MALFORMED_RESPONSE,
        FailureCategory.PROTOCOL_UNSUPPORTED,
        FailureCategory.OPERATION_REJECTED,
        FailureCategory.CAPABILITY_UNAVAILABLE,
        FailureCategory.PERSISTENCE_FAILURE,
        FailureCategory.CANCELLED,
        FailureCategory.BACKGROUND_RESTRICTED,
        FailureCategory.INTERNAL_ERROR,
        -> RetryClass.NEVER_RETRY
    }

    fun classify(
        category: FailureCategory,
        deviceId: String? = null,
        sessionId: String? = null,
        operationId: String? = null,
        mayHaveExecuted: Boolean = false,
        displayMessage: String,
        correlationId: String,
    ): ClassifiedFailure {
        val retryClass = retryClassFor(category)
        return ClassifiedFailure(
            code = category.name,
            category = category,
            deviceId = deviceId,
            sessionId = sessionId,
            operationId = operationId,
            retryClass = retryClass,
            mayHaveExecuted = mayHaveExecuted,
            reconciliationRequired =
                retryClass == RetryClass.RETRY_AFTER_REREAD || mayHaveExecuted,
            displayMessage = displayMessage,
            correlationId = correlationId,
        )
    }
}
