package com.omnibuds.core.audio

/**
 * The authoritative, immutable picture of Bluetooth audio transport right now.
 *
 * There is exactly one snapshot shape and one interpreter (OB-P10-REQ-013):
 * the engine publishes these, consumers read these, and no second store keeps
 * a rival version of "what the audio state is". The snapshot is immutable and
 * fully self-describing so it can be logged, compared and tested without
 * hidden context.
 *
 * Field semantics, all chosen to make overreach impossible:
 *
 * - [devices]: every audio device the platform reported, including
 *   non-Bluetooth ones. Multiple Bluetooth audio devices are normal
 *   (OB-P10-REQ-015); the list is never collapsed by name similarity.
 * - [profileStates]: one entry per audio profile kind, keyed by
 *   [AudioTransportKind]. Absent key and [ProfileAvailability.UNKNOWN] both
 *   mean "not determined" — neither means "unsupported".
 * - [activeTransport]: the transport the platform currently routes audio
 *   through, or null when the platform did not identify one. Null is not
 *   "nothing is playing"; it is "the platform did not say".
 * - [activeOutputDevice]/[activeInputDevice]: the active devices where the
 *   platform exposed them, by platform id. Null is unreported, not absent.
 * - [capabilities]: what the platform can do, independent of any headset.
 * - [diagnostics]: contradictions the reconciler refused to resolve by
 *   guessing, newest first, bounded by [MAX_DIAGNOSTICS].
 * - [schemaVersion]: bumped whenever a field's meaning changes, so persisted
 *   or logged snapshots from an older engine cannot be misread.
 *
 * No history is kept: each snapshot replaces the last. No UI fields, no
 * vendor fields, no codec-configuration fields — codec state beyond the
 * transport kind is Phase 11's subject (OB-P10-REQ-003).
 */
data class AudioTransportSnapshot(
    val schemaVersion: Int = SCHEMA_VERSION,
    val timestampMillis: Long,
    val devices: List<ObservedAudioDevice>,
    val profileStates: Map<AudioTransportKind, AudioProfileState>,
    val activeTransport: AudioTransportKind?,
    val activeOutputDevice: ObservedAudioDevice?,
    val activeInputDevice: ObservedAudioDevice?,
    val capabilities: AudioPlatformCapabilities,
    val diagnostics: List<AudioDiagnostic>,
) {
    init {
        require(schemaVersion == SCHEMA_VERSION) {
            "snapshot schemaVersion $schemaVersion does not match engine schema $SCHEMA_VERSION"
        }
        require(timestampMillis >= 0) { "timestampMillis must be non-negative" }
        require(diagnostics.size <= MAX_DIAGNOSTICS) {
            "diagnostics bounded at $MAX_DIAGNOSTICS, got ${diagnostics.size}"
        }
    }

    companion object {
        const val SCHEMA_VERSION: Int = 1

        /** Diagnostics are evidence, not history: keep the recent few. */
        const val MAX_DIAGNOSTICS: Int = 8

        /** The snapshot before the first observation: everything unknown, nothing claimed. */
        fun initial(timestampMillis: Long): AudioTransportSnapshot = AudioTransportSnapshot(
            timestampMillis = timestampMillis,
            devices = emptyList(),
            profileStates = emptyMap(),
            activeTransport = null,
            activeOutputDevice = null,
            activeInputDevice = null,
            capabilities = AudioPlatformCapabilities.UNKNOWN,
            diagnostics = emptyList(),
        )
    }
}
