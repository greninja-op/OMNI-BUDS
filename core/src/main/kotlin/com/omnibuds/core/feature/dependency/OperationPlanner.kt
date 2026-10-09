package com.omnibuds.core.feature.dependency

import com.omnibuds.core.common.FeatureId

/**
 * A pure operation plan.
 *
 * Phase 29 (OB-P29-REQ-008): planning never sends commands. The plan
 * carries everything execution needs to revalidate before running.
 */
data class OperationPlan(
    /** Stable plan identifier. */
    val planId: String,
    val deviceId: String,
    val sessionId: String,
    /** Requested feature operations, in execution order. */
    val steps: List<PlannedStep>,
    /** Monotonic version of the state the plan was built from. */
    val stateVersion: Long,
    /** Rule-set version used for evaluation. */
    val ruleSetVersion: Int,
    /** When the plan was created (epoch millis). */
    val createdAtMillis: Long,
)

/**
 * One planned step.
 */
data class PlannedStep(
    val feature: FeatureId,
    /** Human-readable description; no raw protocol payloads. */
    val description: String,
    /** Features that must already be established before this step. */
    val requiresEstablished: Set<FeatureId> = emptySet(),
    /** True when a verified compensation exists for this step. */
    val compensationAvailable: Boolean = false,
)

/**
 * Plan outcomes.
 */
sealed interface PlanResult {
    /** A plan is ready, pending execution-time revalidation. */
    data class Planned(val plan: OperationPlan) : PlanResult

    /** No executable plan; [reasons] explain why. */
    data class Rejected(val reasons: List<String>) : PlanResult

    /** An existing plan was invalidated by newer state. */
    data class Invalidated(val planId: String, val reason: String) : PlanResult
}

/**
 * Pure operation planner.
 *
 * Phase 29: builds plans from conflict evaluation + dependency ordering.
 * Never issues hardware commands. Never silently changes prerequisites
 * (OB-P29-REQ-010): unsatisfied relations reject the plan with an
 * explanation.
 */
object OperationPlanner {

    /**
     * Build a plan for enabling [requested] features.
     */
    fun plan(
        planId: String,
        deviceId: String,
        sessionId: String,
        requested: Set<FeatureId>,
        evaluation: ConflictEvaluation,
        graph: RelationshipGraph,
        availability: Map<FeatureId, FeatureAvailability>,
        stateVersion: Long,
        ruleSetVersion: Int,
        nowMillis: Long,
    ): PlanResult {
        if (!evaluation.mayExecute) {
            val reasons = evaluation.findings
                .filterIsInstance<ConflictFinding.HardConflict>()
                .map { "${it.ruleId}: ${it.message}" }
            return PlanResult.Rejected(reasons)
        }

        val unresolved = evaluation.findings.filterIsInstance<ConflictFinding.Unresolved>()
        if (unresolved.isNotEmpty()) {
            return PlanResult.Rejected(
                unresolved.map { "${it.ruleId}: ${it.message}" },
            )
        }

        // Dependency ordering: prerequisites before dependents
        // (deterministic topological order over Requires edges).
        val order = topologicalOrder(requested, graph)
            ?: return PlanResult.Rejected(
                listOf("dependency cycle among requested features; no executable plan"),
            )

        val steps = order.map { feature ->
            val requires = graph.rules.flatMap { rule ->
                when (val r = rule.relation) {
                    is com.omnibuds.core.feature.FeatureRelation.Requires ->
                        if (r.feature == feature) listOf(r.prerequisite) else emptyList()
                    else -> emptyList()
                }
            }.toSet()
            PlannedStep(
                feature = feature,
                description = "enable ${feature.qualifiedName}",
                requiresEstablished = requires,
                compensationAvailable = false, // Only set when verified.
            )
        }

        return PlanResult.Planned(
            OperationPlan(
                planId = planId,
                deviceId = deviceId,
                sessionId = sessionId,
                steps = steps,
                stateVersion = stateVersion,
                ruleSetVersion = ruleSetVersion,
                createdAtMillis = nowMillis,
            ),
        )
    }

    /**
     * Check whether a plan is still fresh.
     */
    fun checkFreshness(
        plan: OperationPlan,
        currentStateVersion: Long,
        currentRuleSetVersion: Int,
        sessionValid: Boolean,
    ): PlanResult {
        if (!sessionValid) {
            return PlanResult.Invalidated(plan.planId, "session invalidated")
        }
        if (currentStateVersion != plan.stateVersion) {
            return PlanResult.Invalidated(plan.planId, "state changed since planning")
        }
        if (currentRuleSetVersion != plan.ruleSetVersion) {
            return PlanResult.Invalidated(plan.planId, "rule set changed since planning")
        }
        return PlanResult.Planned(plan)
    }

    /** Deterministic topological order; null on cycle. */
    private fun topologicalOrder(
        requested: Set<FeatureId>,
        graph: RelationshipGraph,
    ): List<FeatureId>? {
        val edges = graph.rules.flatMap { rule ->
            when (val r = rule.relation) {
                is com.omnibuds.core.feature.FeatureRelation.Requires ->
                    if (r.feature in requested) listOf(r.feature to r.prerequisite)
                    else emptyList()
                else -> emptyList()
            }
        }
        val nodes = (requested + edges.flatMap { listOf(it.first, it.second) })
            .sortedBy { it.qualifiedName }
        val adjacency = edges.groupBy({ it.first }, { it.second })
        val visited = mutableSetOf<FeatureId>()
        val temp = mutableSetOf<FeatureId>()
        val result = mutableListOf<FeatureId>()

        fun visit(node: FeatureId): Boolean {
            if (node in temp) return false // Cycle.
            if (node in visited) return true
            temp.add(node)
            for (next in adjacency[node].orEmpty().sortedBy { it.qualifiedName }) {
                if (!visit(next)) return false
            }
            temp.remove(node)
            visited.add(node)
            result.add(node)
            return true
        }

        for (node in nodes) {
            if (!visit(node)) return null
        }
        // Post-order already places prerequisites before dependents.
        return result.filter { it in requested }
    }
}
