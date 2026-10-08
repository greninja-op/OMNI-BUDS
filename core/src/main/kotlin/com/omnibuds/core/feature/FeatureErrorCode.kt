package com.omnibuds.core.feature

import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory

/**
 * The named failure modes of feature operations.
 *
 * This is vocabulary, not a second error taxonomy: every code maps onto an
 * existing [OmniBudsErrorCategory], following the ADR-P8-004 precedent of refusing
 * to fork the category ladder. The mapping decides two things the engine must get
 * right without thinking per call site — whether the operation may be retried
 * ([OmniBudsErrorCategory.retryClass], derived so a side-effecting command can
 * never be retried by accident) and whether the device's true state is now
 * uncertain ([OmniBudsErrorCategory.invalidatesSession]).
 *
 * Cancellation is deliberately absent: it is an [com.omnibuds.core.common.OperationOutcome]
 * case, not an error (ADR-P1-004). A cancelled operation reports `Cancelled` and
 * restores the previous state; it never fabricates a failure.
 */
enum class FeatureErrorCode(val category: OmniBudsErrorCategory) {

    /** The feature has no definition or no capability record: nothing is known. */
    FEATURE_UNKNOWN(OmniBudsErrorCategory.INVALID_STATE),

    /** Discovery positively established that the device does not implement the feature. */
    FEATURE_UNSUPPORTED(OmniBudsErrorCategory.UNSUPPORTED_FEATURE),

    /** The feature is supported but cannot be used right now. */
    FEATURE_UNAVAILABLE(OmniBudsErrorCategory.INVALID_STATE),

    /** A write was attempted on a read-only feature. */
    FEATURE_READ_ONLY(OmniBudsErrorCategory.UNSUPPORTED_OPERATION),

    /** A read was attempted where no read path is established. */
    FEATURE_WRITE_UNSUPPORTED(OmniBudsErrorCategory.UNSUPPORTED_OPERATION),

    /**
     * The requested value failed definition validation: wrong shape, out of range,
     * disallowed mode or flag, over-long text or payload. Values are refused, never
     * silently clamped (Phase 9 prompt section 13).
     */
    INVALID_VALUE(OmniBudsErrorCategory.INVALID_STATE),

    /** A required prerequisite is missing, unknown or caught in a dependency cycle. */
    DEPENDENCY_NOT_SATISFIED(OmniBudsErrorCategory.INVALID_STATE),

    /** The operation would drive two declared-conflicting features at once. */
    FEATURE_CONFLICT(OmniBudsErrorCategory.INVALID_STATE),

    /** The protocol layer does not offer the attempted operation. */
    PROTOCOL_UNAVAILABLE(OmniBudsErrorCategory.UNSUPPORTED_OPERATION),

    /** The operation type is declared vocabulary but not implemented in Phase 9. */
    OPERATION_NOT_IMPLEMENTED(OmniBudsErrorCategory.UNSUPPORTED_OPERATION),

    /** No transport is established for the feature. */
    TRANSPORT_UNAVAILABLE(OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE),

    /** The operation exceeded its bound; a side-effecting write is never re-sent. */
    OPERATION_TIMEOUT(OmniBudsErrorCategory.TIMEOUT),

    /** The device rejected the command. */
    DEVICE_REJECTED(OmniBudsErrorCategory.WRITE_REJECTED),

    /**
     * An operation completed but the resulting device state could not be
     * established — e.g. a write was accepted yet the verification read failed.
     * The state moves to [FeatureState.Unknown], never to a guess.
     */
    DEVICE_STATE_UNKNOWN(OmniBudsErrorCategory.READ_FAILED),

    /** The device reported a value, but it was not the value requested. */
    STATE_VERIFICATION_FAILED(OmniBudsErrorCategory.VERIFICATION_FAILED),

    /** A device response could not be interpreted as the feature's value. */
    MALFORMED_RESPONSE(OmniBudsErrorCategory.INVALID_STATE),

    /** Anything else, with the session conservatively invalidated. */
    UNKNOWN_ERROR(OmniBudsErrorCategory.UNKNOWN_FAILURE),
}

/** Builds the structured error this code names, carrying no device identifiers. */
fun FeatureErrorCode.toError(operationId: String, detail: String? = null): OmniBudsError =
    OmniBudsError.of(category, operationId, detail)
