package com.omnibuds.core.audio

/**
 * How fresh a codec observation is.
 *
 * Codec information is time-sensitive (Phase 11 §29). When a device
 * disconnects, its runtime codec state must not keep rendering as current:
 * it becomes [STALE], and a reconnect starts from a fresh observation, never
 * from the stale record.
 */
enum class CodecFreshness {
    /** Observed recently enough to trust for decisions. */
    CURRENT,

    /**
     * Superseded: the device disconnected, the observation aged out, or a newer
     * read contradicted it. Never rendered as current.
     */
    STALE,

    /** Freshness has not been determined. */
    UNKNOWN,
}
