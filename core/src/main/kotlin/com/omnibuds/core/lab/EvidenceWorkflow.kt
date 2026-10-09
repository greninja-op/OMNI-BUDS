package com.omnibuds.core.lab

import com.omnibuds.core.state.VerificationLevel

/**
 * Evidence workflow: status promotion rules.
 *
 * Phase 20 (OB-P20-REQ-019): explicit evidence requirements per transition.
 * Synthetic fixtures can never produce HARDWARE_VERIFIED or
 * PERSISTENCE_VERIFIED. All transitions auditable.
 */
object EvidenceWorkflow {

    /**
     * Requirements to advance from one status to the next.
     * Returns the evidence description required, or null when the
     * transition is not permitted.
     */
    fun requirementFor(
        from: VerificationLevel,
        to: VerificationLevel,
    ): String? {
        if (to.ordinal <= from.ordinal) return null // No downgrade or no-op via promotion.
        return when (to) {
            VerificationLevel.IMPLEMENTED ->
                if (from == VerificationLevel.INFERRED)
                    "implementation exists with documented inputs, outputs, limitations, versions"
                else null
            VerificationLevel.LAB_TESTED ->
                if (from == VerificationLevel.IMPLEMENTED)
                    "automated or controlled offline tests passed; fixtures and versions recorded"
                else null
            VerificationLevel.HARDWARE_VERIFIED ->
                if (from == VerificationLevel.LAB_TESTED)
                    "real-device evidence via authorized verification process; synthetic fixtures insufficient"
                else null
            VerificationLevel.PERSISTENCE_VERIFIED ->
                if (from == VerificationLevel.HARDWARE_VERIFIED)
                    "setting observed from device after the relevant lifecycle boundary"
                else null
            VerificationLevel.INFERRED -> null
        }
    }

    /**
     * Validate a proposed transition.
     * @param hasRealDeviceEvidence true only when authorized hardware
     * verification actually occurred.
     */
    fun canPromote(
        from: VerificationLevel,
        to: VerificationLevel,
        hasRealDeviceEvidence: Boolean,
    ): Boolean {
        if (requirementFor(from, to) == null) return false
        // Hardware-level statuses require real device evidence.
        if (to == VerificationLevel.HARDWARE_VERIFIED ||
            to == VerificationLevel.PERSISTENCE_VERIFIED
        ) {
            return hasRealDeviceEvidence
        }
        return true
    }
}

/** One auditable status transition. */
data class EvidenceTransition(
    val schemaKey: String,
    val from: VerificationLevel,
    val to: VerificationLevel,
    val evidenceRef: String,
    val timestampMillis: Long,
    val approved: Boolean,
)
