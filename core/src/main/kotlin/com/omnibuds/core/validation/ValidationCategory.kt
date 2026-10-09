package com.omnibuds.core.validation

/**
 * The independently testable validation categories.
 *
 * Phase 14 (OB-P14-REQ-004 … OB-P14-REQ-010): each category owns a set of
 * rules that can be tested in isolation.
 */
enum class ValidationCategory {
    /** Audio observations bound to the correct device/session. */
    DEVICE_ASSOCIATION,

    /** Transport/profile/device-type/route consistency. */
    TRANSPORT,

    /** Codec identity vs transport, snapshot, runtime, negotiation. */
    CODEC,

    /** Available devices, selected route, media vs communication. */
    ROUTE,

    /** Sample rate, bit depth, bitrate, channels, quality/adaptive modes. */
    PARAMETERS,

    /** Current vs stale vs unknown observations. */
    FRESHNESS,

    /** Connection → route → codec → disconnect → reconnect ordering. */
    LIFECYCLE,
}
