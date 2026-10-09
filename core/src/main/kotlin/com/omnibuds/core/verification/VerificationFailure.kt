package com.omnibuds.core.verification

import com.omnibuds.core.common.OmniBudsErrorCategory

/**
 * Failure classification for verification operations.
 *
 * Phase 18 (§12): conceptual categories mapped to existing
 * [OmniBudsErrorCategory] values. No redundant error types.
 */
enum class VerificationFailure(
    val category: OmniBudsErrorCategory,
    val description: String,
) {
    DEVICE_UNKNOWN(OmniBudsErrorCategory.UNKNOWN_DEVICE, "device identity not established"),
    DEVICE_IDENTITY_AMBIGUOUS(OmniBudsErrorCategory.UNKNOWN_DEVICE, "identity ambiguous; refusing auto-apply"),
    PROTOCOL_UNAVAILABLE(OmniBudsErrorCategory.UNSUPPORTED_FEATURE, "no verified protocol for this operation"),
    PROTOCOL_VERSION_INCOMPATIBLE(OmniBudsErrorCategory.UNSUPPORTED_FEATURE, "protocol version incompatible"),
    CAPABILITY_UNKNOWN(OmniBudsErrorCategory.UNSUPPORTED_FEATURE, "capability support unknown"),
    CAPABILITY_UNSUPPORTED(OmniBudsErrorCategory.UNSUPPORTED_FEATURE, "capability not supported"),
    CAPABILITY_READ_ONLY(OmniBudsErrorCategory.UNSUPPORTED_FEATURE, "capability is read-only"),
    READ_BACK_UNAVAILABLE(OmniBudsErrorCategory.UNSUPPORTED_FEATURE, "protocol has no read-back"),
    PERSISTENCE_SCOPE_UNOBSERVABLE(OmniBudsErrorCategory.UNSUPPORTED_FEATURE, "scope cannot be observed"),
    DEVICE_DISCONNECTED(OmniBudsErrorCategory.DEVICE_DISCONNECTED, "device disconnected"),
    SESSION_CHANGED(OmniBudsErrorCategory.INVALID_STATE, "session changed during verification"),
    COMMAND_REJECTED(OmniBudsErrorCategory.INVALID_STATE, "command rejected by device"),
    COMMAND_OUTCOME_UNKNOWN(OmniBudsErrorCategory.TIMEOUT, "command outcome unknown (ambiguous)"),
    READ_BACK_MISMATCH(OmniBudsErrorCategory.READ_FAILED, "read-back did not match expected"),
    OBSERVATION_STALE(OmniBudsErrorCategory.READ_FAILED, "observation is stale"),
    CONFLICTING_EVIDENCE(OmniBudsErrorCategory.READ_FAILED, "conflicting evidence"),
    INVALID_CONFIGURATION(OmniBudsErrorCategory.INVALID_STATE, "configuration invalid"),
    OPERATION_CONFLICT(OmniBudsErrorCategory.INVALID_STATE, "conflicting operation in progress"),
    TIMEOUT(OmniBudsErrorCategory.TIMEOUT, "operation timed out (ambiguous)"),
    CANCELLED(OmniBudsErrorCategory.INVALID_STATE, "operation cancelled"),
    STORAGE_FAILURE(OmniBudsErrorCategory.INVALID_STATE, "verification record storage failed"),
    SCHEMA_INCOMPATIBLE(OmniBudsErrorCategory.INVALID_STATE, "stored schema incompatible"),
    INTERNAL_ERROR(OmniBudsErrorCategory.INVALID_STATE, "internal error"),
}
