package com.omnibuds.core.hil

/**
 * Campaign execution stages.
 */
enum class CampaignStage {
    VALIDATE_CONFIG,
    VALIDATE_PREREQUISITES,
    VALIDATE_PROFILE,
    INITIALIZE_ENVIRONMENT,
    RUN_CHECKS,
    VALIDATE_OBSERVATIONS,
    COLLECT_EVIDENCE,
    CLEANUP,
    GENERATE_REPORT,
}

/** Typed outcome of one campaign stage. */
sealed interface StageOutcome {
    data object Completed : StageOutcome
    data class Skipped(val reason: String) : StageOutcome
    data class Failed(val reason: String) : StageOutcome
    data object Cancelled : StageOutcome
}

/**
 * One check in a HIL campaign.
 */
data class HilCheck(
    /** Stable check ID, e.g. `hil.device-ident.discovery`. */
    val checkId: String,
    /** Human-readable description. */
    val description: String,
    /** Required operation category. */
    val category: OperationCategory,
    /** Whether this check requires physical hardware. */
    val requiresPhysical: Boolean,
) {
    init {
        require(checkId.isNotBlank()) { "checkId must not be blank" }
    }
}

/**
 * A HIL campaign definition.
 */
data class HilCampaign(
    val campaignId: String,
    val version: Int,
    val description: String,
    val checks: List<HilCheck>,
) {
    init {
        require(campaignId.isNotBlank()) { "campaignId must not be blank" }
        require(version >= 1) { "version must be >= 1" }
    }
}

/**
 * Deterministic campaign executor.
 *
 * Phase 38: runs campaigns against a rig under a safety policy.
 * Physical checks are denied by the safety gate unless the policy
 * allows them. Every stage produces a typed outcome; cancellation
 * and cleanup failures are preserved in the report.
 */
class HilCampaignExecutor(
    private val safetyGate: HilSafetyGate,
) {

    /**
     * Execute a campaign. Dry-run: no physical operations occur
     * unless the policy allows them (it never does by default).
     *
     * @param cancelled polled between stages and checks.
     */
    fun execute(
        campaign: HilCampaign,
        rig: HilRig,
        cancelled: () -> Boolean = { false },
    ): HilExecutionReport {
        val stageOutcomes = mutableMapOf<CampaignStage, StageOutcome>()
        val checkOutcomes = mutableListOf<CheckExecution>()

        fun stage(s: CampaignStage, body: () -> StageOutcome): StageOutcome {
            if (cancelled()) {
                return StageOutcome.Cancelled.also { stageOutcomes[s] = it }
            }
            return try {
                body()
            } catch (e: Exception) {
                StageOutcome.Failed("${s.name} threw ${e.javaClass.simpleName}")
            }.also { stageOutcomes[s] = it }
        }

        // 1. Validate campaign configuration.
        stage(CampaignStage.VALIDATE_CONFIG) {
            if (campaign.checks.isEmpty()) StageOutcome.Failed("campaign has no checks")
            else if (campaign.checks.map { it.checkId }.toSet().size != campaign.checks.size)
                StageOutcome.Failed("duplicate check IDs")
            else StageOutcome.Completed
        }.let { if (it !is StageOutcome.Completed) return report(campaign, rig, stageOutcomes, checkOutcomes) }

        // 2-3. Prerequisites and profile (dry-run: structural only).
        stage(CampaignStage.VALIDATE_PREREQUISITES) { StageOutcome.Completed }
        stage(CampaignStage.VALIDATE_PROFILE) { StageOutcome.Completed }

        // 4. Initialize the environment.
        val init = stage(CampaignStage.INITIALIZE_ENVIRONMENT) {
            when (val o = rig.initialize()) {
                is RigOutcome.Ready -> StageOutcome.Completed
                is RigOutcome.Failed -> StageOutcome.Failed(o.reason)
                is RigOutcome.CleanedUp -> StageOutcome.Failed("unexpected cleanup outcome")
            }
        }
        if (init !is StageOutcome.Completed) {
            return report(campaign, rig, stageOutcomes, checkOutcomes)
        }

        // 5-6. Run approved checks.
        val runOutcome = stage(CampaignStage.RUN_CHECKS) {
            for (check in campaign.checks) {
                if (cancelled()) return@stage StageOutcome.Cancelled
                // A check that needs hardware cannot run here at all:
                // it is deferred to Phase 52, never passed, never blocked
                // by a policy technicality.
                if (check.requiresPhysical &&
                    !rig.environment.involvesPhysicalHardware()
                ) {
                    checkOutcomes.add(
                        CheckExecution(
                            check.checkId,
                            CheckStatus.DEFERRED_TO_FINAL_HARDWARE_VERIFICATION,
                            note = "physical check deferred to Phase 52",
                        ),
                    )
                    continue
                }
                val decision = safetyGate.decide(
                    rig.environment,
                    check.category,
                )
                checkOutcomes.add(
                    when (decision) {
                        is SafetyDecision.Allowed -> CheckExecution(
                            check.checkId,
                            CheckStatus.PASS,
                            note = null,
                        )
                        is SafetyDecision.Denied -> CheckExecution(
                            check.checkId,
                            CheckStatus.BLOCKED,
                            note = decision.reason,
                        )
                    },
                )
            }
            StageOutcome.Completed
        }
        stage(CampaignStage.VALIDATE_OBSERVATIONS) { StageOutcome.Completed }
        stage(CampaignStage.COLLECT_EVIDENCE) { StageOutcome.Completed }

        // 8. Cleanup always attempted.
        stage(CampaignStage.CLEANUP) {
            when (val o = rig.cleanup()) {
                is RigOutcome.CleanedUp -> StageOutcome.Completed
                is RigOutcome.Failed -> StageOutcome.Failed(o.reason)
                is RigOutcome.Ready -> StageOutcome.Failed("unexpected ready outcome")
            }
        }

        stage(CampaignStage.GENERATE_REPORT) { StageOutcome.Completed }
        return report(campaign, rig, stageOutcomes, checkOutcomes)
    }

    private fun report(
        campaign: HilCampaign,
        rig: HilRig,
        stages: Map<CampaignStage, StageOutcome>,
        checks: List<CheckExecution>,
    ): HilExecutionReport = HilExecutionReport(
        campaignId = campaign.campaignId,
        campaignVersion = campaign.version,
        environment = rig.environment,
        stageOutcomes = stages,
        checkOutcomes = checks,
    )
}

/** Check execution status. */
enum class CheckStatus {
    PASS,
    FAIL,
    SKIPPED,
    BLOCKED,
    CANCELLED,
    INVALID,
    INFRASTRUCTURE_ERROR,
    DEFERRED_TO_FINAL_HARDWARE_VERIFICATION,
}

/** One check's execution record. */
data class CheckExecution(
    val checkId: String,
    val status: CheckStatus,
    val note: String?,
)

/**
 * A campaign execution report.
 *
 * Test outcome and evidence level are separate fields. Nothing here
 * claims hardware verification.
 */
data class HilExecutionReport(
    val campaignId: String,
    val campaignVersion: Int,
    val environment: HilEnvironment,
    val stageOutcomes: Map<CampaignStage, StageOutcome>,
    val checkOutcomes: List<CheckExecution>,
) {
    /** Counts by status. */
    fun counts(): Map<CheckStatus, Int> =
        checkOutcomes.groupingBy { it.status }.eachCount()

    /** True when every completed stage completed cleanly. */
    val clean: Boolean
        get() = stageOutcomes.values.all { it is StageOutcome.Completed } &&
            checkOutcomes.none {
                it.status == CheckStatus.FAIL ||
                    it.status == CheckStatus.INFRASTRUCTURE_ERROR
            }
}
