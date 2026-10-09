package com.omnibuds.core.extension

/**
 * Execution contracts for vendor feature operations.
 *
 * Phase 23 (OB-P23-REQ-013/014/016/017): typed outcomes for reads, writes,
 * and ambiguous results. The framework defines the contract; actual
 * transport execution happens through verified protocol adapters —
 * this package holds no transport handles.
 */

/**
 * Declared operation capabilities for a feature.
 */
data class OperationContract(
    /** Whether reading is supported. */
    val readable: Boolean,
    /** Whether writing is supported. */
    val writable: Boolean,
    /** Whether a response acknowledgement is expected. */
    val acknowledgementExpected: Boolean,
    /** Whether read-back is supported after a write. */
    val readBackSupported: Boolean,
    /** Whether the operation is idempotent (safe to retry). */
    val idempotent: Boolean,
    /** Whether retries are safe for this operation. */
    val retrySafe: Boolean,
    /** Whether the setting may be volatile (lost on disconnect). */
    val mayBeVolatile: Boolean,
    /** Persistence scopes that can be verified. */
    val verifiableScopes: Set<String> = emptySet(),
    /** Required verification evidence status for execution. */
    val requiredEvidence: String = "none",
    /** Timeout in milliseconds. */
    val timeoutMillis: Long = 5000L,
) {
    init {
        require(timeoutMillis > 0) { "timeout must be positive" }
    }
}

/**
 * A read request for a vendor feature.
 */
data class VendorFeatureReadRequest(
    val featureId: VendorFeatureId,
    /** Address-free device identity reference. */
    val deviceKey: String,
    /** Session correlation ID. */
    val sessionId: String?,
)

/**
 * The result of a vendor feature read.
 */
sealed interface VendorFeatureReadResult {
    /** Read succeeded; [value] is the observed value. */
    data class Success(val value: VendorFeatureValue) : VendorFeatureReadResult

    /** The feature does not support reading. */
    data class NotReadable(val reason: String) : VendorFeatureReadResult

    /** The operation is not supported on this device. */
    data class Unsupported(val reason: String) : VendorFeatureReadResult

    /** The outcome is unknown (disconnect/timeout after submission). */
    data class Unknown(val reason: String) : VendorFeatureReadResult

    /** The read was denied by the access policy. */
    data class Denied(val reason: String) : VendorFeatureReadResult

    /** The read was cancelled. */
    data object Cancelled : VendorFeatureReadResult
}

/**
 * A write request for a vendor feature.
 */
data class VendorFeatureWriteRequest(
    val featureId: VendorFeatureId,
    val value: VendorFeatureValue,
    /** Address-free device identity reference. */
    val deviceKey: String,
    /** Session correlation ID. */
    val sessionId: String?,
)

/**
 * The result of a vendor feature write.
 *
 * Phase 23: ambiguous outcomes are explicit. Never assume success or
 * failure after a disconnect/timeout.
 */
sealed interface VendorFeatureWriteResult {
    /** Write succeeded and was verified (read-back matched). */
    data class Success(val readBackValue: VendorFeatureValue?) : VendorFeatureWriteResult

    /** Write was accepted but read-back is unsupported or unavailable. */
    data class Accepted(val reason: String) : VendorFeatureWriteResult

    /** The requested value failed validation. */
    data class InvalidValue(val reason: String) : VendorFeatureWriteResult

    /** A prerequisite is missing or a conflict exists. */
    data class DependencyFailed(val reason: String) : VendorFeatureWriteResult

    /** The feature does not support writing. */
    data class NotWritable(val reason: String) : VendorFeatureWriteResult

    /** The operation is not supported on this device. */
    data class Unsupported(val reason: String) : VendorFeatureWriteResult

    /** The outcome is unknown — do not assume success or failure. */
    data class Unknown(val reason: String) : VendorFeatureWriteResult

    /** The write was denied by the access policy. */
    data class Denied(val reason: String) : VendorFeatureWriteResult

    /** The write was cancelled; no retry was attempted. */
    data object Cancelled : VendorFeatureWriteResult

    /** The device rejected the command. */
    data class Rejected(val reason: String) : VendorFeatureWriteResult

    /** Read-back did not match the requested value. */
    data class ReadBackMismatch(
        val requested: VendorFeatureValue,
        val observed: VendorFeatureValue,
    ) : VendorFeatureWriteResult
}

/**
 * The extension contract.
 *
 * Phase 23 (OB-P23-REQ-001): capability-specific optional interfaces are
 * preferred — an extension implements only what it supports.
 */
interface VendorFeatureExtension {
    val descriptor: VendorExtensionDescriptor

    /**
     * Resolve the features this extension provides for the context.
     * Returns the definitions; compatibility is evaluated separately.
     */
    suspend fun resolveFeatures(
        context: VendorFeatureContext,
    ): VendorFeatureResolution
}

/** Optional: extensions that support observation. */
interface VendorFeatureReader {
    suspend fun observeFeature(
        request: VendorFeatureReadRequest,
    ): VendorFeatureReadResult
}

/** Optional: extensions that support control. */
interface VendorFeatureWriter {
    suspend fun executeFeature(
        request: VendorFeatureWriteRequest,
    ): VendorFeatureWriteResult
}

/**
 * Context for feature resolution.
 */
data class VendorFeatureContext(
    /** Address-free device identity reference. */
    val deviceKey: String,
    val manufacturerId: String,
    val modelId: String?,
    val firmwareVersion: String?,
    val firmwareUnknown: Boolean,
    val protocolId: String?,
    val transport: String?,
    val identityAmbiguous: Boolean,
)

/**
 * The result of resolving features for a context.
 */
sealed interface VendorFeatureResolution {
    /** Resolved to a definite set of features. */
    data class Resolved(val features: List<VendorFeatureDefinition>) : VendorFeatureResolution

    /** Multiple candidates remain; restricted operations denied. */
    data class Ambiguous(val candidates: List<VendorExtensionId>, val reason: String) :
        VendorFeatureResolution

    /** No extension matches this context. */
    data class NoMatch(val reason: String) : VendorFeatureResolution
}
