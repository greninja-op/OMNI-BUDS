package com.omnibuds.core.capability

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.state.CapabilityState
import com.omnibuds.core.state.isEstablished

/**
 * One "this feature needs that feature" edge, as a *protocol/firmware* fact, never a catalogue fact.
 *
 * `HEAD_TRACKING` may require `SPATIAL_AUDIO` on one device and not on another, so the edge lives in the
 * discovery layer keyed to the protocol that declared it — not on [CapabilityDefinition], which is
 * deliberately device-agnostic (a catalogue entry saying "exists" is never a device fact). A
 * [firmwareConstraint] narrows when the edge applies; null means "no firmware condition stated".
 */
data class CapabilityDependency(
    val feature: FeatureId,
    val requires: FeatureId,
    val protocolId: String? = null,
    val firmwareConstraint: String? = null,
) {
    init {
        require(feature != requires) {
            "$feature cannot depend on itself; that is a cycle of length one and a modelling error"
        }
    }
}

/** How a dependency edge resolved against what has actually been established. */
enum class DependencyStatus {
    /** The prerequisite is established for this device. */
    SATISFIED,

    /** The prerequisite is positively unsupported, so the dependent cannot be offered. */
    MISSING_PREREQUISITE,

    /** Nothing is known about the prerequisite — the dependent is unknown, *not* blocked-unsupported. */
    UNKNOWN_PREREQUISITE,

    /** The edge sits inside a dependency cycle; no feature in the cycle is treated as satisfiable. */
    CYCLE,
}

/** The outcome of validating a dependency graph: per-edge statuses plus the cycles found. */
data class DependencyReport(
    val statuses: Map<CapabilityDependency, DependencyStatus>,
    val cycles: List<List<FeatureId>>,
) {
    /** A feature is offerable only if every one of its edges is satisfied (no missing/unknown/cycle). */
    fun isFullySatisfied(feature: FeatureId): Boolean =
        statuses.filterKeys { it.feature == feature }.all { (_, status) -> status == DependencyStatus.SATISFIED }

    /** Features with at least one unresolved edge — blocked or merely unknown, distinguished per edge. */
    val blockedFeatures: Set<FeatureId>
        get() = statuses.filterValues { it == DependencyStatus.MISSING_PREREQUISITE || it == DependencyStatus.CYCLE }
            .keys.map { it.feature }.toSet()

    companion object {
        val EMPTY = DependencyReport(emptyMap(), emptyList())
    }
}

/**
 * Resolves dependency edges against established capability state and finds cycles — without ever inferring
 * or enabling anything.
 *
 * Three rules (§11): a missing prerequisite is explicit (the dependent is `MISSING_PREREQUISITE`), an
 * *unknown* prerequisite keeps the dependent `UNKNOWN_PREREQUISITE` and **not** blocked-unsupported
 * (unknown ≠ negative), and a cycle is reported loudly rather than broken by dropping an edge. It reads the
 * supplied [DeviceCapabilities]; it does not mutate it, does not enable a prerequisite, and does not infer
 * a prerequisite's support merely because a dependent was reported (a dependent claim that contradicts a
 * missing prerequisite is a conflict for the evaluator, not something the validator silently repairs).
 */
object DependencyValidator {

    fun validate(dependencies: List<CapabilityDependency>, capabilities: DeviceCapabilities): DependencyReport {
        if (dependencies.isEmpty()) return DependencyReport.EMPTY

        val cycles = findCycles(dependencies)
        val inCycle = cycles.flatten().toSet()

        val statuses = dependencies.associateWith { edge ->
            val prerequisite = capabilities.stateOf(edge.requires)
            when {
                edge.feature in inCycle || edge.requires in inCycle -> DependencyStatus.CYCLE
                !prerequisite.isEstablished() -> DependencyStatus.UNKNOWN_PREREQUISITE
                prerequisite == CapabilityState.UNSUPPORTED -> DependencyStatus.MISSING_PREREQUISITE
                else -> DependencyStatus.SATISFIED
            }
        }
        return DependencyReport(statuses, cycles)
    }

    /**
     * Depth-first search over the feature graph, reporting each back-edge's cycle path once.
     *
     * Recursive rather than explicit-stack because a dependency graph is bounded by the small number of
     * features one protocol declares; the recursion depth cannot approach a stack limit here. Deterministic:
     * neighbours are visited in a stable (sorted-by-name) order so the same graph always yields the same
     * cycle lists. Self-edges are already refused at construction.
     */
    private fun findCycles(dependencies: List<CapabilityDependency>): List<List<FeatureId>> {
        val adjacency: Map<FeatureId, List<FeatureId>> = dependencies
            .groupBy { it.feature }
            .mapValues { (_, edges) -> edges.map { it.requires }.sortedBy { feature -> feature.qualifiedName } }

        val cycles = mutableListOf<List<FeatureId>>()
        val visited = mutableSetOf<FeatureId>()
        val stack = mutableListOf<FeatureId>()
        val onStack = mutableSetOf<FeatureId>()

        fun dfs(node: FeatureId) {
            visited += node
            stack += node
            onStack += node
            for (next in adjacency[node].orEmpty()) {
                when {
                    next in onStack -> {
                        val start = stack.indexOf(next)
                        if (start >= 0) cycles += stack.subList(start, stack.size).toList() + next
                    }
                    next !in visited -> dfs(next)
                }
            }
            stack.removeAt(stack.lastIndex)
            onStack -= node
        }

        for (node in adjacency.keys.sortedBy { feature -> feature.qualifiedName }) {
            if (node !in visited) dfs(node)
        }
        return cycles
    }
}
