package com.omnibuds.core.diagnostics

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.state.CapabilityState

/**
 * The Phase 1 seed of the diagnostic report described by master context section 31.
 *
 * It is a **reporting structure, not a reasoning engine**: every field reports what
 * something else established, and nothing in this type concludes anything. It cannot
 * promote a capability, cannot turn a missing read into `UNSUPPORTED`, and cannot
 * imply support from a device family (master section 53, `SEC-UNK-009`: not discovered
 * is not `UNSUPPORTED`, not verified is not `SUPPORTED`). A report that inferred
 * support would be indistinguishable from a claim, and this project's whole error
 * history is claims that started as convenient inferences.
 *
 * **An absent capability entry means [CapabilityState.UNKNOWN].** That is the contract
 * of [capabilityStates]: keys are recorded only where discovery produced a state. An
 * unrecorded feature is "not discovered or not provable from here", which is a
 * different statement from `UNSUPPORTED` and must never be rendered as one
 * (specs.md section 2.3, rule 2). Use [stateOf], which encodes that reading.
 *
 * Audio is a human-readable [audioTransportSummary] string, not structured audio types,
 * because the audio domain is owned by another package in this phase. Importing
 * `com.omnibuds.core.audio` from diagnostics would create cross-package coupling that
 * Phase 1 has not authorised (execution prompt section 32), so the summary travels as
 * text until the audio contracts are settled — with the understanding that a summary is
 * presentation, not a typed reading.
 *
 * Report content is user-facing and reviewable before sharing, and its default shape
 * omits raw identifiers (`SEC-LOG-007`). The lists here are built by [SnapshotBuilder]
 * from caller-supplied facts, so a snapshot is a value with no live reference into
 * session state.
 */
data class DiagnosticSnapshot(
    /** When the snapshot was taken, in milliseconds since the Unix epoch. */
    val capturedAtEpochMillis: Long,
    /**
     * Capability states recorded by discovery. Absent key means UNKNOWN, never
     * `UNSUPPORTED` and never an assumed supported state.
     */
    val capabilityStates: Map<FeatureId, CapabilityState>,
    /** Human-readable audio transport/route description, or null when unknown. */
    val audioTransportSummary: String?,
    /** Structured failures recorded during the session, in the order they happened. */
    val errors: List<OmniBudsError>,
    /** Warning-level events recorded during the session, in the order they happened. */
    val warnings: List<DiagnosticEvent>,
    /** Platform observations worth showing a device owner, supplied as text by the caller. */
    val platformNotes: List<String>,
) {
    init {
        require(capturedAtEpochMillis >= 0L) { "epoch millis cannot be negative, was $capturedAtEpochMillis" }
    }

    /**
     * The recorded state for [feature].
     *
     * Returns [CapabilityState.UNKNOWN] when nothing was recorded, which is the honest
     * reading of an absent entry: not discovered. It never returns `UNSUPPORTED` for a
     * feature it has no evidence about, and it never upgrades a recorded state.
     */
    fun stateOf(feature: FeatureId): CapabilityState = capabilityStates[feature] ?: CapabilityState.UNKNOWN
}
