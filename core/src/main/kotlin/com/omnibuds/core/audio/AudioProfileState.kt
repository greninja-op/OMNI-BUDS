package com.omnibuds.core.audio

/**
 * The observed state of one Bluetooth audio profile (A2DP, HFP, HSP, LE Audio).
 *
 * A profile is a different axis from a transport connection and from an audio
 * device, and this type keeps the three apart (OB-P10-REQ-009, OB-P10-REQ-010):
 *
 * - [availability]: can this platform offer the profile at all.
 * - [connectionState]: what the profile proxy reports right now.
 * - [deviceAddress]: which Bluetooth device the profile state belongs to, where
 *   the platform exposes it. Null means unassociated, not "no device".
 *
 * For HFP the profile carries an extra distinction the platform exposes
 * separately: the *connection* state (is the headset linked for calls) versus
 * the *audio* state (is SCO audio actually up). [audioState] holds the latter
 * where observed; it stays null for profiles that have no such split (A2DP,
 * LE Audio), and null is never read as "audio down".
 *
 * Nothing here configures anything. In particular there is no codec field: the
 * negotiated codec is Phase 11's subject, and this phase refuses to let a
 * profile observation smuggle codec claims in through the side door
 * (OB-P10-REQ-003).
 */
data class AudioProfileState(
    /** Which profile this record describes. */
    val profile: AudioTransportKind,
    /** Whether the platform can offer this profile. */
    val availability: ProfileAvailability,
    /** The connection state the profile proxy reported. */
    val connectionState: AudioConnectionState,
    /**
     * HFP/HSP audio (SCO) state where the platform exposes it separately from
     * the profile connection; null for profiles without that split and null
     * when unreported. Never inferred from [connectionState].
     */
    val audioState: AudioConnectionState?,
    /** Bluetooth address the state belongs to, where exposed; null when unassociated. */
    val deviceAddress: String?,
    /** Which platform API produced this record, for diagnostics. */
    val source: String,
) {
    init {
        require(
            profile == AudioTransportKind.CLASSIC_A2DP ||
                profile == AudioTransportKind.HFP ||
                profile == AudioTransportKind.HSP ||
                profile == AudioTransportKind.LE_AUDIO ||
                profile == AudioTransportKind.UNKNOWN,
        ) { "AudioProfileState.profile must be an audio profile kind, was $profile" }
        require(source.isNotBlank()) { "source must name the platform API that produced this record" }
    }
}
