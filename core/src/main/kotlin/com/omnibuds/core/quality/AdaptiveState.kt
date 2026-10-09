package com.omnibuds.core.quality

/**
 * Whether the audio configuration adapts at runtime.
 *
 * Phase 13 (OB-P13-REQ-012): ADAPTIVE requires genuine evidence of adaptive
 * behavior — never inferred merely because a codec *supports* adaptive
 * operation.
 */
enum class AdaptiveState {
    /** The configuration is fixed. */
    FIXED,

    /** The configuration demonstrably adapts at runtime. */
    ADAPTIVE,

    /** Unknown — the platform did not report behavior. */
    UNKNOWN,
}
