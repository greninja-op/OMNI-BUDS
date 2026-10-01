package com.omnibuds.core.diagnostics

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.state.CapabilityState

/**
 * Accumulates the facts a [DiagnosticSnapshot] reports.
 *
 * The builder records; it does not conclude. Every entry in the resulting snapshot
 * came from a caller who already had it — a discovered capability state, a returned
 * error, a piece of platform text. In particular:
 *
 *  - [record] stores a state someone established. The builder never fills gaps, never
 *    defaults a missing feature to `UNKNOWN` by writing an entry for it, and never
 *    moves a state up or down on its own. Absent stays absent, and
 *    [DiagnosticSnapshot.stateOf] reads that absence as `UNKNOWN`.
 *  - Timestamps are supplied by the caller, per event. There is no clock in this
 *    module: a core contract that reads the wall clock itself cannot be tested
 *    deterministically and cannot be ported, so the platform layer owns time
 *    (execution prompt section 8).
 *  - Ordering is deterministic. Capability states are sorted by feature identity so
 *    that two identical runs produce an equal snapshot; errors, warnings and notes keep
 *    the order they happened, because sequence is the information they carry.
 *
 * One owner at a time, like the rest of the mutable state in core (specs.md section 5,
 * rule 7): this is not thread-safe and is meant to be built and read by whoever created
 * it.
 */
class SnapshotBuilder {

    private val capabilityStates = mutableMapOf<FeatureId, CapabilityState>()
    private val errors = mutableListOf<OmniBudsError>()
    private val warnings = mutableListOf<DiagnosticEvent>()
    private val platformNotes = mutableListOf<String>()
    private var audioTransportSummary: String? = null

    /**
     * Records what is known about one capability of this device.
     *
     * Re-recording the same feature replaces the earlier state, so the snapshot reports
     * the latest established fact and holds one entry per feature. A fresh discovery
     * result that is weaker than the recorded one is still authoritative: support moves
     * down on failed verification, and this map is where that lands.
     */
    fun record(feature: FeatureId, state: CapabilityState) {
        capabilityStates[feature] = state
    }

    /**
     * Records a warning-level observation.
     *
     * [message] is pre-redaction text with the same obligation as
     * [DiagnosticEvent.message]: no unredacted Bluetooth address, address-bound device
     * name or manufacturer data (`SEC-LOG-002`).
     */
    fun warn(
        category: DiagnosticCategory,
        message: String,
        atEpochMillis: Long,
        operationId: String? = null,
    ) {
        warnings += DiagnosticEvent(
            timestampEpochMillis = atEpochMillis,
            severity = DiagnosticSeverity.WARN,
            category = category,
            message = message,
            operationId = operationId,
            error = null,
        )
    }

    /** Records a structured failure. Errors are not flattened into warning text. */
    fun fail(error: OmniBudsError) {
        errors += error
    }

    /** Adds one line of caller-supplied platform context. Nothing here is inferred. */
    fun note(platformNote: String) {
        platformNotes += platformNote
    }

    /**
     * Sets the human-readable audio summary, or clears it with null.
     *
     * Owned by whoever can read the audio state; this type has no audio dependencies,
     * so the text arrives already decided and is not re-derived here.
     */
    fun describeAudioTransport(summary: String?) {
        audioTransportSummary = summary
    }

    /**
     * Produces the snapshot as of [capturedAtEpochMillis].
     *
     * The result holds copies, so continuing to use this builder cannot rewrite a
     * snapshot someone is already reading or about to show the user (`SEC-LOG-007`).
     */
    fun build(capturedAtEpochMillis: Long): DiagnosticSnapshot = DiagnosticSnapshot(
        capturedAtEpochMillis = capturedAtEpochMillis,
        capabilityStates = capabilityStates.entries
            .sortedBy { entry -> entry.key.qualifiedName }
            .associate { entry -> entry.key to entry.value },
        audioTransportSummary = audioTransportSummary,
        errors = errors.toList(),
        warnings = warnings.toList(),
        platformNotes = platformNotes.toList(),
    )
}
