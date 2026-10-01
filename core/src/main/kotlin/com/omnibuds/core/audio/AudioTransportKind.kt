package com.omnibuds.core.audio

/**
 * The media or call transport an audio observation belongs to.
 *
 * This is a domain representation only: nothing here opens, selects or tears down a
 * transport (Phase 1 prompt sections 18 and 51). Transport selection stays owned by
 * the platform, and OmniBuds must not implement or imply a silent fallback between
 * classic and LE Audio (AUD-XPORT-005, AUD-PATH-002).
 *
 * It is a distinct concept from [com.omnibuds.core.common.TransportKind], which
 * describes how a *control channel* reaches a device; the two must not be conflated,
 * because a device can be controlled over GATT while audio runs over A2DP.
 */
enum class AudioTransportKind {
    /** Classic Bluetooth A2DP media transport: SBC, AAC, aptX, LDAC and their kin. */
    CLASSIC_A2DP,

    /** Hands-free profile call audio, whose codec rules differ from media audio. */
    HFP,

    /** LE Audio transport, carrying LC3. A separate family, not an A2DP codec (master section 20). */
    LE_AUDIO,

    /** Not read yet, or not determinable from here — not evidence that no transport is in use. */
    UNKNOWN,
}
