package com.omnibuds.core.verification

/**
 * Deterministic verification state machine.
 *
 * Phase 18 (OB-P18-REQ-010, OB-P18-REQ-028): pure transitions. Terminal
 * records never transition back. Invalid transitions are rejected.
 */
object VerificationStateMachine {

    /**
     * Apply an event to a record, producing the next record.
     * @throws IllegalStateException for invalid transitions.
     */
    fun transition(
        record: VerificationRecord,
        event: VerificationEvent,
        nowMillis: Long,
    ): VerificationRecord {
        if (record.isTerminal) {
            throw IllegalStateException("terminal record ${record.id} cannot transition")
        }
        return when (event) {
            is VerificationEvent.EligibilityDetermined ->
                if (event.eligible) {
                    record.copy(
                        stage = nextStage(record, VerificationStage.ELIGIBILITY_CHECK),
                        updatedAtMillis = nowMillis,
                    )
                } else {
                    complete(record, VerificationOutcome.UNSUPPORTED, event.reason, nowMillis)
                }
            is VerificationEvent.BaselineCaptured ->
                record.copy(
                    stage = nextStage(record, VerificationStage.BASELINE_CAPTURE),
                    baseline = event.baseline,
                    updatedAtMillis = nowMillis,
                )
            is VerificationEvent.ApplyRequested ->
                record.copy(
                    stage = VerificationStage.APPLY_REQUESTED,
                    applicationStatus = ApplicationStatus.REQUESTED,
                    updatedAtMillis = nowMillis,
                )
            is VerificationEvent.Acknowledged ->
                record.copy(
                    stage = VerificationStage.APPLY_ACKNOWLEDGED,
                    applicationStatus = ApplicationStatus.ACKNOWLEDGED,
                    updatedAtMillis = nowMillis,
                )
            is VerificationEvent.Rejected ->
                complete(
                    record.copy(applicationStatus = ApplicationStatus.REJECTED),
                    VerificationOutcome.FAILED,
                    event.reason,
                    nowMillis,
                )
            is VerificationEvent.ReadBackReceived ->
                handleReadBack(record, event, nowMillis)
            is VerificationEvent.LifecycleBoundaryObserved ->
                handleBoundary(record, event.boundary, nowMillis)
            is VerificationEvent.PostBoundaryReadBack ->
                handlePostBoundaryReadBack(record, event, nowMillis)
            is VerificationEvent.TimedOut ->
                // Timeout is ambiguous — never success, never failure.
                complete(record, VerificationOutcome.INCONCLUSIVE, "timed out waiting for ${event.waitingFor}", nowMillis)
            is VerificationEvent.Cancelled ->
                complete(record, VerificationOutcome.CANCELLED, "cancelled", nowMillis)
            is VerificationEvent.DeviceDisconnected ->
                // Disconnect during operation: ambiguous unless already confirmed.
                if (record.applicationStatus == ApplicationStatus.READ_BACK_CONFIRMED) {
                    // Already confirmed; disconnect doesn't un-confirm.
                    record.copy(updatedAtMillis = nowMillis)
                } else {
                    complete(record, VerificationOutcome.INCONCLUSIVE, "device disconnected during verification", nowMillis)
                }
        }
    }

    private fun nextStage(record: VerificationRecord, current: VerificationStage): VerificationStage {
        val plan = record.plan.stages
        val idx = plan.indexOf(current)
        if (idx == -1 || idx + 1 >= plan.size) return VerificationStage.EVALUATING_EVIDENCE
        return plan[idx + 1]
    }

    private fun handleReadBack(
        record: VerificationRecord,
        event: VerificationEvent.ReadBackReceived,
        nowMillis: Long,
    ): VerificationRecord {
        if (event.malformed || event.observed == null) {
            return complete(
                record,
                VerificationOutcome.INCONCLUSIVE,
                "read-back unavailable or malformed",
                nowMillis,
            )
        }
        val match = ReadBackComparison.compare(record.plan.expectedValue, event.observed)
        return when (match) {
            is ComparisonResult.Match -> record.copy(
                stage = VerificationStage.INITIAL_READ_BACK,
                applicationStatus = ApplicationStatus.READ_BACK_CONFIRMED,
                provenScope = maxScope(record.provenScope, PersistenceScope.SESSION_ONLY),
                updatedAtMillis = nowMillis,
            )
            is ComparisonResult.Mismatch -> complete(
                record,
                VerificationOutcome.NOT_VERIFIED,
                "read-back mismatch: ${match.reason}",
                nowMillis,
            )
        }
    }

    private fun handleBoundary(
        record: VerificationRecord,
        boundary: LifecycleBoundary,
        nowMillis: Long,
    ): VerificationRecord {
        val stage = when (boundary) {
            LifecycleBoundary.CONTROL_SESSION_END -> VerificationStage.SESSION_BOUNDARY_CHECK
            LifecycleBoundary.DISCONNECT_RECONNECT -> VerificationStage.RECONNECT_CHECK
            LifecycleBoundary.APPLICATION_RESTART -> VerificationStage.APPLICATION_RESTART_CHECK
            LifecycleBoundary.DEVICE_POWER_CYCLE -> VerificationStage.DEVICE_POWER_CYCLE_CHECK
        }
        return record.copy(stage = stage, updatedAtMillis = nowMillis)
    }

    private fun handlePostBoundaryReadBack(
        record: VerificationRecord,
        event: VerificationEvent.PostBoundaryReadBack,
        nowMillis: Long,
    ): VerificationRecord {
        if (event.observed == null) {
            return record.copy(updatedAtMillis = nowMillis) // No evidence; scope unchanged.
        }
        val match = ReadBackComparison.compare(record.plan.expectedValue, event.observed)
        if (match !is ComparisonResult.Match) {
            return complete(record, VerificationOutcome.NOT_VERIFIED, "post-boundary mismatch", nowMillis)
        }
        val scope = when (event.boundary) {
            LifecycleBoundary.CONTROL_SESSION_END -> PersistenceScope.CONTROL_SESSION_PERSISTENT
            LifecycleBoundary.DISCONNECT_RECONNECT -> PersistenceScope.CONNECTION_PERSISTENT
            LifecycleBoundary.APPLICATION_RESTART -> PersistenceScope.APPLICATION_RESTART_PERSISTENT
            LifecycleBoundary.DEVICE_POWER_CYCLE -> PersistenceScope.DEVICE_REBOOT_PERSISTENT
        }
        return record.copy(
            provenScope = maxScope(record.provenScope, scope),
            updatedAtMillis = nowMillis,
        )
    }

    private fun complete(
        record: VerificationRecord,
        outcome: VerificationOutcome,
        reason: String?,
        nowMillis: Long,
    ): VerificationRecord = record.copy(
        stage = VerificationStage.COMPLETED,
        outcome = outcome,
        failureReason = reason,
        updatedAtMillis = nowMillis,
        completedAtMillis = nowMillis,
    )

    private fun maxScope(a: PersistenceScope, b: PersistenceScope): PersistenceScope {
        if (a == PersistenceScope.UNKNOWN) return b
        if (b == PersistenceScope.UNKNOWN) return a
        // Lower ordinal = stronger (SESSION_ONLY=0 is weakest... wait, no).
        // Ordinal order: SESSION_ONLY(0) < CONTROL(1) < CONNECTION(2) < RESTART(3) < REBOOT(4) < FIRMWARE(5).
        // Stronger = higher ordinal. UNKNOWN(6) handled above.
        return if (a.ordinal >= b.ordinal) a else b
    }
}

/** Result of comparing expected vs observed values. */
sealed interface ComparisonResult {
    data object Match : ComparisonResult
    data class Mismatch(val reason: String) : ComparisonResult
}

/**
 * Feature-specific read-back comparison.
 *
 * Phase 18 (OB-P18-REQ-011): exact match by default. Normalization only
 * when feature semantics define it (not implemented here — extension point).
 */
object ReadBackComparison {
    fun compare(
        expected: com.omnibuds.core.config.ConfigurationValue,
        observed: com.omnibuds.core.config.ConfigurationValue,
    ): ComparisonResult {
        // Exact structural equality. Different types never match.
        return if (expected == observed) {
            ComparisonResult.Match
        } else {
            ComparisonResult.Mismatch("expected $expected, observed $observed")
        }
    }
}
