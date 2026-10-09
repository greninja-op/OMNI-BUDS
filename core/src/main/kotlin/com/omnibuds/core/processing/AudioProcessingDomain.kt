package com.omnibuds.core.processing

/**
 * Where audio processing happens.
 *
 * Phase 15 (OB-P15-REQ-001): three real domains plus UNKNOWN. Hardware DSP
 * and firmware are separate values, but the model preserves uncertainty
 * where their boundary cannot be established — a capability may be
 * DEVICE_HARDWARE_DSP or DEVICE_FIRMWARE, and when the distinction is
 * unprovable the resolver reports the ambiguity rather than guessing.
 */
enum class AudioProcessingDomain {
    /** Processing inside the earbuds/headphones hardware DSP. */
    DEVICE_HARDWARE_DSP,

    /** Processing in the device firmware (boundary with DSP may be unclear). */
    DEVICE_FIRMWARE,

    /** Processing by the Android audio framework. */
    ANDROID_PLATFORM,

    /** OmniBuds' own logic: discovery, state, orchestration, diagnostics. */
    APPLICATION_LOGIC,

    /** The processing owner is unknown. The default. */
    UNKNOWN,
}
