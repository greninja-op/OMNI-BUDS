package com.omnibuds.core.transport

import com.omnibuds.core.common.TransportKind

import com.omnibuds.core.common.OmniBudsErrorCategory

/**
 * Whether one candidate control channel could be used, and why not when it could not.
 *
 * This is the record a session keeps per transport and per purpose: one device may need
 * BLE for bookkeeping, RFCOMM for configuration and A2DP for media, and all of those
 * availability statements belong to the same session at once (PROTO-XPORT-005,
 * ADR-P0-003). Recording them separately is what stops "the GATT service was absent"
 * from being read as "this device has no control channel" (PROTO-XPORT-001).
 *
 * **An unavailable transport produces [OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE] and
 * a recorded failed-candidate entry; it never produces a silent fallback to a different
 * transport.** PROTO-XPORT-007 names silent skip, "assume GATT" and reporting an absent
 * transport as `UNSUPPORTED_FEATURE` as violations, and ADR-P0-007/ADR-P0-003 make the
 * fallback a correctness bug rather than a convenience: a command written to a channel
 * chosen by retry policy is a command written to a channel nobody established.
 *
 * [available] and [reason] are locked together by construction, because
 * "available, for reason X" and "unavailable, and here is why" are the only two honest
 * statements. A third shape — unavailable with no recorded reason — would be the
 * swallowed failure specs.md section 2.2 forbids, so it cannot be built.
 */
data class TransportAvailability(
    /** The channel this statement is about; a fallback candidate is a separate record. */
    val kind: TransportKind,

    /** Whether this channel was found usable. */
    val available: Boolean,

    /**
     * Why the channel is unavailable, or null while it is available.
     *
     * Non-null exactly when [available] is false.
     */
    val reason: OmniBudsErrorCategory?,
) {

    init {
        if (available) {
            require(reason == null) {
                "an available $kind transport cannot carry a failure reason, was $reason; " +
                    "record the refusal as unavailable instead"
            }
            require(kind != TransportKind.UNKNOWN) {
                "an available transport must be a determined channel kind; TransportKind.UNKNOWN " +
                    "means none has been established, which cannot be reported as usable"
            }
        } else {
            requireNotNull(reason) {
                "an unavailable $kind transport must carry the category that refused it; " +
                    "a refusal without a recorded reason is a swallowed failure"
            }
        }
    }

    /** Whether this record names a refusal, so callers can branch without reading [reason]. */
    val isRefused: Boolean
        get() = !available

    companion object {
        /** The channel was established as usable for this purpose. */
        fun available(kind: TransportKind): TransportAvailability =
            TransportAvailability(kind = kind, available = true, reason = null)

        /**
         * The channel was missing or refused, with the category that says which.
         *
         * [OmniBudsErrorCategory.TRANSPORT_UNAVAILABLE] is the ordinary answer; the
         * GATT/RFCOMM/timeout/permission categories are used when the platform reported
         * one of those specifically (PROTO-XPORT-006).
         */
        fun unavailable(kind: TransportKind, reason: OmniBudsErrorCategory): TransportAvailability =
            TransportAvailability(kind = kind, available = false, reason = reason)
    }
}
