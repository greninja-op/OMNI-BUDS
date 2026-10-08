package com.omnibuds.core.audio

import com.omnibuds.core.common.TransportKind

/**
 * The mandatory separation between the audio path and the control path.
 *
 * A connected headset lives on two independent planes at once (OB-P10-REQ-018):
 *
 * ```
 * Audio plane:    Android -> A2DP / HFP / LE Audio -> earbuds
 * Control plane:  OmniBuds -> vendor protocol -> GATT / RFCOMM -> earbuds
 * ```
 *
 * This type pins the two planes side by side so they can be reasoned about
 * together without ever being conflated. Its invariants are the ones Phase 10
 * exists to enforce:
 *
 * - The audio transport is never inferred from the control transport. A GATT
 *   control channel says nothing about whether audio runs over A2DP or LE
 *   Audio, and an A2DP audio link says nothing about which control channel the
 *   vendor protocol uses.
 * - The control transport is never inferred from the audio transport. LE Audio
 *   as an *audio* transport ([AudioTransportKind.LE_AUDIO]) and LE Audio as a
 *   *control* channel ([TransportKind.LE_AUDIO]) are different claims about
 *   different planes; one must never license the other.
 * - [verify] fails when either side is missing: a topology with an unknown
 *   audio transport, or an unknown control transport, is not a verified
 *   topology. Unknown is honest; a half-known topology presented as complete
 *   is not.
 *
 * The companion [unverified] constructor exists for the observation phase,
 * when one plane is known and the other is still being discovered — the two
 * halves arrive at different times and must not block each other.
 */
data class AudioControlTopology(
    /** The observed audio transport, or UNKNOWN while undiscovered. */
    val audioTransport: AudioTransportKind,
    /** The established control channel, or UNKNOWN while undiscovered. */
    val controlTransport: TransportKind,
) {
    /**
     * Returns true only when both planes are known and independently
     * established. A false here is not an error — it is the normal state
     * while discovery is still running on one plane.
     */
    fun verify(): Boolean =
        audioTransport != AudioTransportKind.UNKNOWN &&
            controlTransport != TransportKind.UNKNOWN

    companion object {
        /** A topology with nothing established yet on either plane. */
        val UNVERIFIED = AudioControlTopology(
            audioTransport = AudioTransportKind.UNKNOWN,
            controlTransport = TransportKind.UNKNOWN,
        )
    }
}
