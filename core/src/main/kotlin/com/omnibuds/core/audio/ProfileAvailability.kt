package com.omnibuds.core.audio

/**
 * Whether a Bluetooth audio profile is usable on this platform at all.
 *
 * Availability is about the platform and the profile — not about any headset.
 * A profile can be [AVAILABLE] with zero devices connected; a profile can be
 * [UNAVAILABLE] because the platform lacks the API (LE Audio below API 33) or
 * because the Bluetooth stack does not implement it. [UNKNOWN] is the state
 * before the platform has been asked, or when the answer could not be read.
 *
 * Supported, available, enabled, negotiated and active are separate rungs
 * (master Phase 10 rule 3). This enum is the "available" rung only: it must
 * never be read as "a device is using this profile right now".
 */
enum class ProfileAvailability {
    /** The platform implements this profile and it may be used. */
    AVAILABLE,

    /** The platform cannot offer this profile (missing API, missing stack support). */
    UNAVAILABLE,

    /** Not yet determined, or the determination failed without disproof. */
    UNKNOWN,
}
