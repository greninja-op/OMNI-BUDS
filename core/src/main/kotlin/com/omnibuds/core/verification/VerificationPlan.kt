package com.omnibuds.core.verification

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.config.ConfigurationValue

/**
 * Declarative verification plan.
 *
 * Phase 18 (OB-P18-REQ-008, §9): derived from actual device/protocol
 * capabilities. Different protocols expose different observability —
 * the plan reflects that.
 */
data class VerificationPlan(
    val targetFeature: FeatureId,
    val expectedValue: ConfigurationValue,
    val requestedScope: PersistenceScope,
    /** Which stages this plan will attempt, in order. */
    val stages: List<VerificationStage>,
    /** Whether the protocol supports read-back. */
    val supportsReadBack: Boolean,
    /** Whether reconnect observation is possible. */
    val supportsReconnectCheck: Boolean,
    /** Whether restart observation is possible. */
    val supportsRestartCheck: Boolean,
    /** Whether power-cycle observation is possible. */
    val supportsPowerCycleCheck: Boolean,
    /** Max time to wait for acknowledgement. */
    val ackTimeoutMillis: Long,
    /** Max time to wait for read-back. */
    val readBackTimeoutMillis: Long,
    /** Max retries for idempotent operations. */
    val maxRetries: Int,
    /** True when the operation is safe to retry. */
    val retrySafe: Boolean,
) {
    init {
        require(stages.isNotEmpty()) { "plan must have at least one stage" }
        require(ackTimeoutMillis > 0) { "ackTimeoutMillis must be positive" }
        require(readBackTimeoutMillis > 0) { "readBackTimeoutMillis must be positive" }
        require(maxRetries >= 0) { "maxRetries must be non-negative" }
        // Read-back stages require read-back support.
        if (!supportsReadBack) {
            require(VerificationStage.INITIAL_READ_BACK !in stages) {
                "plan includes read-back without protocol support"
            }
        }
    }

    companion object {
        /**
         * Minimal plan: apply + acknowledge only (write-only protocol).
         * Proves nothing beyond acknowledgement.
         */
        fun writeOnly(
            feature: FeatureId,
            value: ConfigurationValue,
        ): VerificationPlan = VerificationPlan(
            targetFeature = feature,
            expectedValue = value,
            requestedScope = PersistenceScope.SESSION_ONLY,
            stages = listOf(
                VerificationStage.ELIGIBILITY_CHECK,
                VerificationStage.APPLY_REQUESTED,
                VerificationStage.APPLY_ACKNOWLEDGED,
                VerificationStage.EVALUATING_EVIDENCE,
                VerificationStage.COMPLETED,
            ),
            supportsReadBack = false,
            supportsReconnectCheck = false,
            supportsRestartCheck = false,
            supportsPowerCycleCheck = false,
            ackTimeoutMillis = 5000L,
            readBackTimeoutMillis = 5000L,
            maxRetries = 0,
            retrySafe = false,
        )
    }
}
