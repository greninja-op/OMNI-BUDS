package com.omnibuds.core.common

/**
 * Whether an operation may change device state.
 *
 * The class is data, not derived per call site, so a side-effecting command can
 * never be retried by accident: the engine consults this together with the error
 * category's [RetryClass] before any repeat (Phase 9 prompt section 21,
 * mirroring [RetryClass]'s contract).
 *
 * Moved from `com.omnibuds.core.feature` in Phase 12: this is a generic
 * operation concept, not a feature-engine concept. The codec control engine
 * (layer 3) needs it, and layer 3 may not depend on the layer-5 feature
 * package (DependencyDirectionTest).
 */
enum class SideEffectClass {

    /** Idempotent reads; may be retried under a bounded policy. */
    READ_ONLY_SAFE,

    /**
     * May change device state; never re-sent blindly. A timed-out write is
     * followed by a read, never by a second write (PROTO-ERR-002).
     */
    SIDE_EFFECTING,
}
