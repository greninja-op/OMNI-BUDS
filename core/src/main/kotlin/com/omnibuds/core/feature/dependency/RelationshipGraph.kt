package com.omnibuds.core.feature.dependency

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.feature.FeatureRelation

/**
 * A relationship rule: a [FeatureRelation] with provenance and scope.
 *
 * Phase 29: relationships are only as trustworthy as their provenance.
 * Superseded rules are inert — never silently reinterpreted.
 */
data class RelationshipRule(
    val relation: FeatureRelation,
    val provenance: RelationshipProvenance,
) {
    /** True when this rule may be evaluated for the given device. */
    fun isActiveFor(
        manufacturer: String?,
        model: String?,
        firmware: String?,
    ): Boolean =
        !provenance.superseded &&
            provenance.scope.matches(manufacturer, model, firmware)

    /** All feature ids this rule references. */
    fun endpoints(): Set<FeatureId> = when (val r = relation) {
        is FeatureRelation.Requires -> setOf(r.feature, r.prerequisite)
        is FeatureRelation.RequiresOneOf -> setOf(r.feature) + r.options
        is FeatureRelation.ConflictsWith -> setOf(r.feature, r.other)
        is FeatureRelation.MutuallyExclusive -> setOf(r.feature) + r.others.toSet()
        is FeatureRelation.Implies -> setOf(r.feature, r.implied)
        is FeatureRelation.VendorException -> setOf(r.feature)
    }
}

/**
 * A validated, device-scoped relationship graph.
 *
 * Phase 29 (OB-P29-REQ-007): deterministic validation; pure.
 */
class RelationshipGraph private constructor(
    val rules: List<RelationshipRule>,
) {
    companion object {
        /**
         * Build and validate a graph. Returns failures instead of throwing.
         */
        fun build(rules: List<RelationshipRule>): GraphBuildResult {
            val failures = mutableListOf<GraphFailure>()

            // Duplicate rule ids.
            val seenIds = mutableSetOf<String>()
            for (rule in rules) {
                if (!seenIds.add(rule.provenance.ruleId)) {
                    failures.add(GraphFailure.DuplicateRuleId(rule.provenance.ruleId))
                }
            }

            // Cycle detection over Requires edges (deterministic order).
            val requiresEdges = rules.flatMap { rule ->
                when (val r = rule.relation) {
                    is FeatureRelation.Requires -> listOf(r.feature to r.prerequisite)
                    is FeatureRelation.RequiresOneOf ->
                        r.options.map { r.feature to it }
                    else -> emptyList()
                }
            }
            val cycle = findCycle(requiresEdges)
            if (cycle != null) {
                failures.add(GraphFailure.DependencyCycle(cycle))
            }

            // Contradictory rules: same pair both Requires and ConflictsWith.
            val requiresPairs = requiresEdges.map { it.first to it.second }.toSet()
            for (rule in rules) {
                val r = rule.relation
                if (r is FeatureRelation.ConflictsWith) {
                    if ((r.feature to r.other) in requiresPairs ||
                        (r.other to r.feature) in requiresPairs
                    ) {
                        failures.add(
                            GraphFailure.ContradictoryRules(rule.provenance.ruleId),
                        )
                    }
                }
            }

            return if (failures.isEmpty()) {
                GraphBuildResult.Valid(RelationshipGraph(rules.sortedBy { it.provenance.ruleId }))
            } else {
                GraphBuildResult.Invalid(failures)
            }
        }

        /** Deterministic DFS cycle detection. */
        private fun findCycle(edges: List<Pair<FeatureId, FeatureId>>): List<FeatureId>? {
            val adjacency = edges.groupBy({ it.first }, { it.second })
                .mapValues { (_, v) -> v.sortedBy { it.qualifiedName } }
            val visited = mutableSetOf<FeatureId>()
            val stack = mutableListOf<FeatureId>()

            fun visit(node: FeatureId): List<FeatureId>? {
                if (node in stack) return stack.drop(stack.indexOf(node)) + node
                if (node in visited) return null
                visited.add(node)
                stack.add(node)
                for (next in adjacency[node].orEmpty()) {
                    val cycle = visit(next)
                    if (cycle != null) return cycle
                }
                stack.removeAt(stack.lastIndex)
                return null
            }

            for (node in adjacency.keys.sortedBy { it.qualifiedName }) {
                val cycle = visit(node)
                if (cycle != null) return cycle
            }
            return null
        }
    }

    /** Rules active for a device, in deterministic order. */
    fun activeFor(
        manufacturer: String?,
        model: String?,
        firmware: String?,
    ): List<RelationshipRule> =
        rules.filter { it.isActiveFor(manufacturer, model, firmware) }
}

/**
 * Graph build outcomes.
 */
sealed interface GraphBuildResult {
    data class Valid(val graph: RelationshipGraph) : GraphBuildResult
    data class Invalid(val failures: List<GraphFailure>) : GraphBuildResult
}

/**
 * Graph validation failures.
 */
sealed interface GraphFailure {
    data class DuplicateRuleId(val ruleId: String) : GraphFailure
    data class DependencyCycle(val cycle: List<FeatureId>) : GraphFailure
    data class ContradictoryRules(val ruleId: String) : GraphFailure
}
