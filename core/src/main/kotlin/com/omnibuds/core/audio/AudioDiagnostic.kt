package com.omnibuds.core.audio

/**
 * A recorded contradiction between audio observation sources.
 *
 * The reconciler refuses to guess when sources disagree (OB-P10-REQ-016): it
 * preserves [AudioConnectionState.UNKNOWN] and records *why* here instead of
 * hiding the conflict. A diagnostic names the two sources and what each
 * claimed, so a later investigation — human or automated — can tell a stale
 * callback from a platform bug from a mapping error.
 *
 * Diagnostics are bounded: the engine keeps only the most recent few per
 * snapshot, because an unbounded contradiction log is a memory leak wearing a
 * debugger's clothes (OB-P10-REQ-022).
 */
data class AudioDiagnostic(
    /** Stable code for the contradiction kind, e.g. "PROFILE_DEVICE_MISMATCH". */
    val code: String,
    /** Human-readable account naming both sources and their claims. */
    val message: String,
    /** Wall-clock millis when the contradiction was recorded. */
    val timestampMillis: Long,
) {
    init {
        require(code.isNotBlank()) { "diagnostic code must be non-blank" }
        require(message.isNotBlank()) { "diagnostic message must be non-blank" }
        require(timestampMillis >= 0) { "timestampMillis must be non-negative" }
    }
}
