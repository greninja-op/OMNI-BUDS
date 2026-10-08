package com.omnibuds.core.audio

import kotlinx.coroutines.flow.Flow

/**
 * The platform-facing seams the audio transport engine observes through.
 *
 * These are *ports* in the hexagonal sense: the core defines the questions it
 * needs answered, and the platform module answers them with Android APIs. The
 * questions are deliberately narrow and read-only (OB-P10-REQ-011,
 * OB-P10-REQ-012):
 *
 * - [AudioProfileSource]: what do the Bluetooth profile proxies report.
 * - [AudioDeviceSource]: which audio devices exist and which are active.
 *
 * Two ports rather than one, because the two answers come from different
 * platform subsystems (Bluetooth profiles vs. AudioManager) that fail
 * independently — and because the reconciler's entire job is to notice when
 * they disagree. Merging them into one "give me the audio state" call would
 * hide exactly the contradictions Phase 10 exists to surface.
 *
 * Implementations must never capture, decode, re-encode or route audio, and
 * must never request microphone permission merely to answer these questions
 * (OB-P10-REQ-020, OB-P10-REQ-021).
 */
interface AudioProfileSource {
    /**
     * Reads the current per-profile state once. Returns one entry per profile
     * the platform was asked about; profiles the platform cannot answer for
     * are absent or [ProfileAvailability.UNKNOWN], never fabricated.
     */
    suspend fun readProfileStates(): Map<AudioTransportKind, AudioProfileState>

    /**
     * The transport the platform currently names as active, if it names one.
     * Null is unreported, not "none active".
     */
    suspend fun readActiveTransportHint(): AudioTransportKind?
}

/**
 * Discrete audio-device changes, as translated from platform callbacks.
 *
 * The platform's `AudioDeviceCallback` speaks in added/removed device lists;
 * this sealed hierarchy is the domain translation, carrying
 * [ObservedAudioDevice] records instead of framework objects so the core never
 * sees `AudioDeviceInfo` (OB-P10-REQ-012).
 */
sealed interface AudioDeviceEvent {
    /** Devices the platform reported as newly present. */
    data class Added(val devices: List<ObservedAudioDevice>) : AudioDeviceEvent

    /** Platform ids the platform reported as gone. Ids, not records: the record is gone. */
    data class Removed(val platformDeviceIds: List<Int>) : AudioDeviceEvent

    /** A full re-read, e.g. after (re)starting observation. Replaces all device state. */
    data class Resynchronized(val devices: List<ObservedAudioDevice>) : AudioDeviceEvent
}

interface AudioDeviceSource {
    /** Reads the current audio-device list once. */
    suspend fun readDevices(): List<ObservedAudioDevice>

    /**
     * Emits [AudioDeviceEvent]s for platform audio-device changes. The flow is
     * cold: collecting starts platform observation, cancelling the collection
     * stops it. The source is responsible for deterministic unregistration —
     * the engine cancels, the source cleans up (OB-P10-REQ-017).
     */
    fun observeDeviceEvents(): Flow<AudioDeviceEvent>
}
