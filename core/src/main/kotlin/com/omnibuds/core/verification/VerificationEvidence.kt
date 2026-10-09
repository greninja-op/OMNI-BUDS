package com.omnibuds.core.verification

import com.omnibuds.core.config.ConfigurationValue

/**
 * One piece of verification evidence.
 *
 * Phase 18 (OB-P18-REQ-005, §6): structured, auditable, provenance-bearing.
 * No raw protocol payloads by default.
 */
data class VerificationEvidence(
    val evidenceId: String,
    val verificationId: VerificationId,
    /** Fingerprint-derived device key, never a raw Bluetooth address. */
    val deviceKey: String,
    /** Feature or configuration key. */
    val targetKey: String,
    /** What was expected, when applicable. */
    val expectedValue: ConfigurationValue?,
    /** What was observed, when applicable. */
    val observedValue: ConfigurationValue?,
    val evidenceType: EvidenceType,
    /** Source interface or protocol adapter name. */
    val source: String,
    val protocolVersion: String?,
    val timestampMillis: Long,
    /** Device session at observation time. */
    val sessionId: String?,
    /** Connection generation, when available. */
    val connectionGeneration: Long?,
    /** True when this evidence is too old to establish current state. */
    val isStale: Boolean,
    val correlationId: String?,
    val limitations: List<String> = emptyList(),
) {
    init {
        require(evidenceId.isNotBlank()) { "evidenceId must not be blank" }
        require(deviceKey.isNotBlank()) { "deviceKey must not be blank" }
        require(timestampMillis >= 0) { "timestampMillis must be non-negative" }
    }
}

/** What kind of evidence this is. */
enum class EvidenceType {
    /** A command was acknowledged (meaning per protocol). */
    COMMAND_ACKNOWLEDGEMENT,

    /** Value read directly from the device. */
    DEVICE_READ_BACK,

    /** Value observed after reconnect. */
    RECONNECT_READ_BACK,

    /** Value observed after app restart. */
    RESTART_READ_BACK,

    /** Value observed after device power cycle. */
    POWER_CYCLE_READ_BACK,

    /** Baseline captured before applying. */
    BASELINE_OBSERVATION,

    /** Local preference was stored (NOT hardware evidence). */
    LOCAL_PREFERENCE_STORED,

    /** Lifecycle boundary observed (disconnect, session end, etc.). */
    LIFECYCLE_OBSERVATION,

    /** Operation was rejected. */
    REJECTION,

    /** Operation timed out (ambiguous, not failure). */
    TIMEOUT_MARKER,
}
