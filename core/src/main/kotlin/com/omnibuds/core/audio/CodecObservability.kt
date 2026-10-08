package com.omnibuds.core.audio

/**
 * Whether OmniBuds can currently observe a codec state at all.
 *
 * This separates platform limitations from device limitations (Phase 11 §20):
 * `SUPPORTED = UNKNOWN` with observability [NOT_OBSERVABLE] means "the platform
 * does not expose this", never "the codec is unsupported".
 *
 * Example: the active A2DP codec has no public observation API on Android, so a
 * record about it carries [NOT_OBSERVABLE] — and any consumer that rendered that
 * as "SBC" would be inventing a fact.
 */
enum class CodecObservability {
    /** The platform exposes this state through a public API. */
    OBSERVABLE,

    /**
     * Partially exposed: some dimensions observable (e.g. local support list),
     * others not (e.g. per-device negotiated codec).
     */
    PARTIALLY_OBSERVABLE,

    /**
     * No public platform API exposes this state. Claims about it stay UNKNOWN
     * with this observability, never degrading into negative facts.
     */
    NOT_OBSERVABLE,

    /** Observability itself has not been determined. */
    UNKNOWN,
}
