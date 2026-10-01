package com.omnibuds.core.protocol

import com.omnibuds.core.audio.AudioTransportState
import com.omnibuds.core.common.OperationOutcome

/**
 * A protocol that can report what the audio session is actually doing.
 *
 * **What implementing this says, and what it does not.** It says this protocol family can
 * read audio transport state. It does not mean the protocol can *change* it: nothing here
 * negotiates, switches or reconfigures a codec, and no OmniBuds code captures, processes,
 * re-encodes or retransmits media audio (ADR-P0-002, master section 3, Phase 1 prompt
 * sections 2 and 51 — codec control and codec switching are explicitly forbidden work).
 *
 * The returned snapshot must be a report of observations with each field unknown
 * independently: a codec that was not read stays null, an unread sample rate stays null, and
 * `AudioTransportState.unobserved()` is the honest answer for a session nothing was read
 * from (AUD-QUAL-002, ADR-P0-016). Defaults such as SBC at 44.1 kHz, 16 bit, stereo would be
 * a fabrication dressed as an observation.
 *
 * Reporting is also not verification. Whether a codec claim may be shown as `ACTIVE` is
 * gated by the audio governance rules and the [com.omnibuds.core.state.VerificationLevel]
 * of the protocol that produced the reading (PROTO-VERIFY-002), not by the existence of this
 * interface.
 *
 * No feature semantics live here: ANC, transparency, equalizer and gesture behaviour is not
 * modelled by any reporting interface in this package, because inventing those shapes in
 * Phase 1 would fabricate hardware behaviour. Those features arrive as values addressed
 * through [FeatureReadSupport] and [FeatureWriteSupport], defined by the phases that own
 * them (Phase 1 prompt sections 2, 26 and 51).
 *
 * Phase 1 defines the contract only: no audio state reading, no polling, no implementation
 * in `:core`.
 */
interface AudioStateReportingSupport {

    /** Read the audio transport state as the device currently reports it. */
    suspend fun readAudioTransportState(): OperationOutcome<AudioTransportState>
}
