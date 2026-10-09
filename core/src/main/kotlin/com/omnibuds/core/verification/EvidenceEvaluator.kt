package com.omnibuds.core.verification

/**
 * Deterministic evidence evaluation.
 *
 * Phase 18 (OB-P18-REQ-007): computes the strongest proven scope from
 * evidence. Pure function. No numeric confidence scores.
 *
 * Rules:
 * - Local preference storage is NOT hardware evidence (ignored for scope).
 * - Acknowledgement alone proves SESSION_ONLY at best.
 * - Device read-back proves SESSION_ONLY.
 * - Reconnect/restart/power-cycle read-backs prove their respective scopes.
 * - Stale or cross-session evidence cannot establish current state.
 * - Conflicting evidence caps the outcome at INCONCLUSIVE.
 */
object EvidenceEvaluator {

    /**
     * Evaluate evidence for a verification.
     * @return the strongest proven scope and whether conflicts exist.
     */
    fun evaluate(
        evidence: List<VerificationEvidence>,
        currentSessionId: String?,
    ): Evaluation {
        if (evidence.isEmpty()) {
            return Evaluation(PersistenceScope.UNKNOWN, false, "no evidence")
        }

        // Filter to current-session evidence for state claims.
        // Cross-session evidence is preserved but cannot establish current state.
        val current = evidence.filter { e ->
            !e.isStale && (e.sessionId == null || e.sessionId == currentSessionId)
        }

        // Check for conflicts among read-backs.
        val readBacks = current.filter { it.evidenceType in READ_BACK_TYPES }
        val conflicts = hasConflicts(readBacks)

        var scope = PersistenceScope.UNKNOWN
        for (e in current) {
            scope = maxScope(scope, scopeFor(e))
        }

        return Evaluation(
            strongestScope = scope,
            hasConflicts = conflicts,
            reason = if (conflicts) "conflicting evidence present" else "evaluated ${current.size} evidence items",
        )
    }

    private fun scopeFor(e: VerificationEvidence): PersistenceScope = when (e.evidenceType) {
        EvidenceType.COMMAND_ACKNOWLEDGEMENT -> PersistenceScope.SESSION_ONLY
        EvidenceType.DEVICE_READ_BACK -> PersistenceScope.SESSION_ONLY
        EvidenceType.RECONNECT_READ_BACK -> PersistenceScope.CONNECTION_PERSISTENT
        EvidenceType.RESTART_READ_BACK -> PersistenceScope.APPLICATION_RESTART_PERSISTENT
        EvidenceType.POWER_CYCLE_READ_BACK -> PersistenceScope.DEVICE_REBOOT_PERSISTENT
        // These prove nothing about persistence:
        EvidenceType.BASELINE_OBSERVATION,
        EvidenceType.LOCAL_PREFERENCE_STORED,
        EvidenceType.LIFECYCLE_OBSERVATION,
        EvidenceType.REJECTION,
        EvidenceType.TIMEOUT_MARKER,
        -> PersistenceScope.UNKNOWN
    }

    private fun hasConflicts(readBacks: List<VerificationEvidence>): Boolean {
        val values = readBacks.mapNotNull { it.observedValue }.toSet()
        return values.size > 1
    }

    private fun maxScope(a: PersistenceScope, b: PersistenceScope): PersistenceScope {
        if (a == PersistenceScope.UNKNOWN) return b
        if (b == PersistenceScope.UNKNOWN) return a
        return if (a.ordinal >= b.ordinal) a else b
    }

    private val READ_BACK_TYPES = setOf(
        EvidenceType.DEVICE_READ_BACK,
        EvidenceType.RECONNECT_READ_BACK,
        EvidenceType.RESTART_READ_BACK,
        EvidenceType.POWER_CYCLE_READ_BACK,
    )
}

/** The result of evidence evaluation. */
data class Evaluation(
    val strongestScope: PersistenceScope,
    val hasConflicts: Boolean,
    val reason: String,
)
