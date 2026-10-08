package com.omnibuds.core.codec

import com.omnibuds.core.audio.Codec
import com.omnibuds.core.device.DeviceIdentity

/**
 * The platform/vendor seam for actually performing a codec operation.
 *
 * Phase 12 (OB-P12-REQ-016): the codec engine never touches raw Bluetooth
 * packets, never bypasses the transport or protocol engines. A control
 * adapter is the *only* path from a validated [CodecOperation] to a real
 * mechanism — and it may legitimately report that no mechanism exists.
 *
 * An adapter that has no legitimate mechanism for an operation returns
 * [CodecApplyOutcome.NotAvailable], never a fake success. The engine turns
 * that into the appropriate structured [CodecOperationResult].
 */
interface CodecControlAdapter {

    /**
     * Attempt the platform/vendor side of [operation].
     *
     * The operation has already passed validation and capability prechecks;
     * this is the actual mechanism call, bounded by the operation's timeout by
     * the engine. Implementations must be cancellation-safe and must never
     * claim success they did not perform.
     */
    suspend fun apply(operation: CodecOperation): CodecApplyOutcome

    /**
     * Re-observe the codec state after an apply, for verification.
     * Returns null when the state cannot be observed.
     */
    suspend fun observeAfterApply(
        device: DeviceIdentity,
        codec: Codec,
    ): CodecConfiguration?
}

/**
 * What the platform/vendor mechanism actually did.
 *
 * Distinct from [CodecOperationResult]: this is the raw mechanism outcome;
 * the engine adds verification, state tracking, and the requested-vs-confirmed
 * distinction on top.
 */
sealed interface CodecApplyOutcome {
    /** The mechanism performed the requested change. */
    data object Performed : CodecApplyOutcome

    /**
     * No legitimate mechanism exists for this operation on this platform.
     * The [reason] names the missing capability (e.g. "no public API exposes
     * codec selection").
     */
    data class NotAvailable(val reason: String) : CodecApplyOutcome

    /** The device was not connected when the attempt ran. */
    data object DeviceDisconnected : CodecApplyOutcome

    /** The mechanism attempt failed. */
    data class Failed(val reason: String) : CodecApplyOutcome
}
