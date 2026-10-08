package com.omnibuds.core.audio

/**
 * The direction of audio flow on a transport or device, as observed.
 *
 * Direction is reported per transport because the same physical headset can
 * carry different directions on different transports at the same time: A2DP
 * commonly represents media output while HFP involves input and output, and LE
 * Audio can support multiple use cases (OB-P10-REQ-007). Nothing here hardcodes
 * "A2DP is output-only" as an axiom — the platform is the authority on what a
 * transport is actually doing, and where the platform exposes more accurate
 * information that information wins.
 *
 * Like every observation in this engine, [UNKNOWN] is a first-class value: a
 * transport whose direction the platform did not disclose is unknown, not
 * assumed output.
 */
enum class AudioDirection {
    /** Audio flows toward the device: media playback, call downlink, prompts. */
    OUTPUT,

    /** Audio flows from the device: microphone capture, call uplink. */
    INPUT,

    /**
     * Both directions are in play on this transport — the normal HFP call
     * shape, and a possible LE Audio shape. Bidirectional does not imply the
     * two directions are active at the same instant.
     */
    BIDIRECTIONAL,

    /** The platform did not disclose a direction. Not evidence of no audio. */
    UNKNOWN,
}
