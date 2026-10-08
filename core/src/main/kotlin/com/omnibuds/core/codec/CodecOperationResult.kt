package com.omnibuds.core.codec

import com.omnibuds.core.audio.Codec
import com.omnibuds.core.common.OmniBudsError

/**
 * The structured outcome of a [CodecOperation].
 *
 * Phase 12 (OB-P12-REQ-007): failures are never collapsed into `Exception` or
 * `false`. The caller can always determine what actually happened.
 *
 * Success is a ladder, not a boolean:
 *
 * - [Accepted]: the operation passed preconditions and was dispatched, but no
 *   platform/vendor attempt has completed yet.
 * - [Applied]: the platform/vendor mechanism reported the change was applied,
 *   but it has not been verified by re-observation.
 * - [Verified]: re-observation confirmed the requested state. This is the only
 *   result that advances confirmed state.
 * - [AppliedUnverified]: the change was applied but no verification mechanism
 *   exists ([CodecVerificationStrategy.NONE]). The engine records the request;
 *   it does NOT mark the codec as confirmed.
 *
 * Every failure carries the [operationId] for correlation and, where useful, a
 * structured [OmniBudsError].
 */
sealed interface CodecOperationResult {

    val operationId: String

    /** Preconditions passed; the operation was dispatched. */
    data class Accepted(override val operationId: String) : CodecOperationResult

    /** The mechanism reported success, but verification has not run. */
    data class Applied(
        override val operationId: String,
        val codec: Codec,
        val configuration: CodecConfiguration?,
    ) : CodecOperationResult

    /** Re-observation confirmed the requested state. Advances confirmed state. */
    data class Verified(
        override val operationId: String,
        val codec: Codec,
        val configuration: CodecConfiguration?,
    ) : CodecOperationResult

    /**
     * Applied, but no verification mechanism exists. Recorded, never confirmed.
     * (OB-P12-REQ-027: NONE strategy caps at APPLIED_UNVERIFIED.)
     */
    data class AppliedUnverified(
        override val operationId: String,
        val codec: Codec,
        val configuration: CodecConfiguration?,
    ) : CodecOperationResult

    /** Preconditions failed before any platform/vendor attempt. */
    data class Rejected(
        override val operationId: String,
        val reason: String,
        val error: OmniBudsError? = null,
    ) : CodecOperationResult

    /** The codec is not supported by this device/platform. */
    data class Unsupported(
        override val operationId: String,
        val codec: Codec,
    ) : CodecOperationResult

    /** No legitimate mechanism exists to select this codec. */
    data class NotSelectable(
        override val operationId: String,
        val codec: Codec,
    ) : CodecOperationResult

    /** No legitimate mechanism exists to configure this codec. */
    data class NotConfigurable(
        override val operationId: String,
        val codec: Codec,
    ) : CodecOperationResult

    /** The codec state cannot be observed, so the operation cannot proceed. */
    data class NotObservable(
        override val operationId: String,
        val codec: Codec,
    ) : CodecOperationResult

    /** The device disconnected during the operation. Never a success. */
    data class DeviceDisconnected(
        override val operationId: String,
    ) : CodecOperationResult

    /** The platform mechanism is unavailable (API level, permission, profile). */
    data class PlatformUnavailable(
        override val operationId: String,
        val reason: String,
    ) : CodecOperationResult

    /**
     * The mechanism reported success but re-observation disagreed (or the
     * observation is still the old state). Confirmed state does NOT advance.
     */
    data class VerificationFailed(
        override val operationId: String,
        val requestedCodec: Codec,
        val observedCodec: Codec,
    ) : CodecOperationResult

    /** The operation exceeded its bounded timeout. Never a success. */
    data class TimedOut(
        override val operationId: String,
    ) : CodecOperationResult

    /** A catch-all for unexpected failures; carries the structured error. */
    data class Failed(
        override val operationId: String,
        val error: OmniBudsError,
    ) : CodecOperationResult
}

/** True only for the result that advances confirmed state. */
val CodecOperationResult.isConfirmed: Boolean
    get() = this is CodecOperationResult.Verified

/** True for results that indicate the operation cannot be performed at all. */
val CodecOperationResult.isTerminalRefusal: Boolean
    get() = this is CodecOperationResult.Unsupported ||
        this is CodecOperationResult.NotSelectable ||
        this is CodecOperationResult.NotConfigurable ||
        this is CodecOperationResult.NotObservable ||
        this is CodecOperationResult.PlatformUnavailable
