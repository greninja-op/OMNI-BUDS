package com.omnibuds.core.verification

import com.omnibuds.core.config.ConfigurationValue

/**
 * The verification record — one attempt's complete state.
 *
 * Phase 18 (OB-P18-REQ-001): immutable snapshots; the state machine
 * produces new records on each transition.
 */
data class VerificationRecord(
    val id: VerificationId,
    val deviceKey: String,
    val plan: VerificationPlan,
    val stage: VerificationStage,
    val outcome: VerificationOutcome?,
    val applicationStatus: ApplicationStatus,
    /** Strongest scope proven so far. */
    val provenScope: PersistenceScope,
    val evidence: List<VerificationEvidence>,
    val baseline: ConfigurationValue?,
    val failureReason: String?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val completedAtMillis: Long?,
) {
    init {
        require(deviceKey.isNotBlank()) { "deviceKey must not be blank" }
        // Terminal outcome requires COMPLETED stage.
        if (outcome != null) {
            require(stage == VerificationStage.COMPLETED) {
                "outcome set but stage is $stage, not COMPLETED"
            }
            require(completedAtMillis != null) {
                "completed record must have completedAtMillis"
            }
        }
    }

    /** True when this record is terminal. */
    val isTerminal: Boolean get() = outcome != null

    companion object {
        fun create(
            deviceKey: String,
            plan: VerificationPlan,
            nowMillis: Long,
        ): VerificationRecord = VerificationRecord(
            id = VerificationId.new(),
            deviceKey = deviceKey,
            plan = plan,
            stage = VerificationStage.CREATED,
            outcome = null,
            applicationStatus = ApplicationStatus.NOT_ATTEMPTED,
            provenScope = PersistenceScope.UNKNOWN,
            evidence = emptyList(),
            baseline = null,
            failureReason = null,
            createdAtMillis = nowMillis,
            updatedAtMillis = nowMillis,
            completedAtMillis = null,
        )
    }
}
