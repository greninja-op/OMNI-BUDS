package com.omnibuds.core.feature

import com.omnibuds.core.capability.CapabilityDependency
import com.omnibuds.core.capability.DependencyStatus
import com.omnibuds.core.capability.DependencyValidator
import com.omnibuds.core.capability.DeviceCapabilities
import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.config.ConfigurationValue

/**
 * One relationship verdict: what was checked, why it matters, and whether it
 * stops an operation.
 *
 * [blocksRead] and [blocksWrite] are separate because reads change nothing: an
 * unknown prerequisite stops a write (the engine cannot prove the operation is
 * meaningful) but not a read, while a positively missing prerequisite or a
 * dependency cycle stops both. Nothing here ever enables a prerequisite or
 * disables a conflicting feature — a blocked operation is refused with its
 * explanation, and what happens next is the caller's decision (Phase 9 prompt
 * section 19).
 */
data class FeatureRelationIssue(
    val relation: FeatureRelation,
    val code: FeatureErrorCode,
    val detail: String,
    val blocksRead: Boolean,
    val blocksWrite: Boolean,
)

/**
 * The verdict over one definition's relations: every issue found, the cycles
 * detected, and the vendor notes recorded.
 *
 * Deterministic: relations are evaluated in declaration order and cycles arrive
 * in [DependencyValidator]'s stable order, so the same definition and snapshot
 * always yield the same report.
 */
data class FeatureRelationReport(
    val feature: FeatureId,
    val issues: List<FeatureRelationIssue>,
    val cycles: List<List<FeatureId>>,
    val vendorNotes: List<FeatureRelation.VendorException>,
) {
    /** No issue blocks a read. */
    val mayRead: Boolean get() = issues.none { it.blocksRead }

    /** No issue blocks a write. */
    val mayWrite: Boolean get() = issues.none { it.blocksWrite }

    companion object {
        /** A definition with no relations has nothing to evaluate. */
        fun empty(feature: FeatureId): FeatureRelationReport =
            FeatureRelationReport(feature, emptyList(), emptyList(), emptyList())
    }
}

/**
 * Evaluates a feature definition's relations against discovered capability truth
 * and current control state.
 *
 * Requires-edges are evaluated through [DependencyValidator] — the same
 * validator discovery uses — so "requires" means one thing everywhere: a
 * missing prerequisite is explicit, an *unknown* prerequisite keeps the verdict
 * unknown rather than blocked-unsupported, and a cycle is reported loudly
 * instead of being broken by dropping an edge. The richer relations
 * (requires-one-of, conflicts, mutual exclusion, implications) are evaluated
 * here, against the same capability snapshot plus the live [FeatureState] map
 * for conflict activity.
 *
 * This evaluator reads state; it changes nothing. It enables no prerequisite,
 * disables no conflicting feature, and infers no support.
 */
object FeatureDependencyEvaluator {

    /**
     * Evaluates [feature]'s own relations against discovered capability truth and
     * live control state.
     *
     * [allRelations] carries every known relation — across all definitions — so
     * cycle detection sees the whole graph, not just one definition's edges: a
     * cycle needs an edge *into* the feature, which lives on another definition.
     * The verdicts (blocking issues) are computed from [ownRelations] only; the
     * shared validation statuses come from the whole graph.
     */
    fun evaluate(
        feature: FeatureId,
        ownRelations: List<FeatureRelation>,
        allRelations: List<FeatureRelation>,
        capabilities: DeviceCapabilities,
        currentStates: Map<FeatureId, FeatureState>,
    ): FeatureRelationReport {
        if (ownRelations.isEmpty() && allRelations.isEmpty()) {
            return FeatureRelationReport.empty(feature)
        }

        // Requires and Implies edges share the discovery validator: one semantics for
        // "needs", one cycle detector. RequiresOneOf options ride along as synthetic
        // edges so their per-option status is available without a second traversal.
        // Cycle detection runs over ALL relations; verdicts only over the feature's own.
        val edgeSources = mutableMapOf<CapabilityDependency, FeatureRelation>()
        val oneOfOptions = mutableMapOf<FeatureRelation.RequiresOneOf, List<CapabilityDependency>>()
        for (relation in allRelations) {
            when (relation) {
                is FeatureRelation.Requires -> {
                    val edge = CapabilityDependency(relation.feature, relation.prerequisite)
                    edgeSources[edge] = relation
                }

                is FeatureRelation.Implies -> {
                    val edge = CapabilityDependency(relation.feature, relation.implied)
                    edgeSources[edge] = relation
                }

                is FeatureRelation.RequiresOneOf -> {
                    val edges = relation.options.map { option ->
                        CapabilityDependency(relation.feature, option)
                    }
                    // Only the feature's own one-of relations need per-option lookup.
                    if (relation in ownRelations) {
                        oneOfOptions[relation] = edges
                    }
                    edges.forEach { edge -> edgeSources[edge] = relation }
                }

                is FeatureRelation.ConflictsWith,
                is FeatureRelation.MutuallyExclusive,
                is FeatureRelation.VendorException,
                -> Unit // evaluated below against live state, not capability edges
            }
        }

        val validation = DependencyValidator.validate(edgeSources.keys.toList(), capabilities)
        val inCycle = validation.cycles.flatten().toSet()

        val issues = mutableListOf<FeatureRelationIssue>()
        val vendorNotes = mutableListOf<FeatureRelation.VendorException>()

        for (relation in ownRelations) {
            when (relation) {
                is FeatureRelation.Requires -> {
                    val edge = CapabilityDependency(relation.feature, relation.prerequisite)
                    val status = validation.statuses[edge]
                    issues += requiresIssue(relation, edge, status, inCycle)
                }

                is FeatureRelation.RequiresOneOf -> {
                    issues += requiresOneOfIssue(relation, oneOfOptions.getValue(relation), validation, inCycle)
                }

                is FeatureRelation.ConflictsWith -> {
                    val active = activeValue(relation.other, currentStates)
                    if (active != null) {
                        issues += FeatureRelationIssue(
                            relation = relation,
                            code = FeatureErrorCode.FEATURE_CONFLICT,
                            detail = "refusing: ${relation.other.qualifiedName} holds an active confirmed " +
                                "value ($active) and ${relation.feature.qualifiedName} declares a " +
                                "conflict with it: ${relation.reason}",
                            blocksRead = false,
                            blocksWrite = true,
                        )
                    }
                }

                is FeatureRelation.MutuallyExclusive -> {
                    val group = listOf(relation.feature) + relation.others
                    val activeMember = relation.others.firstNotNullOfOrNull { member ->
                        activeValue(member, currentStates)?.let { member to it }
                    }
                    if (activeMember != null) {
                        issues += FeatureRelationIssue(
                            relation = relation,
                            code = FeatureErrorCode.FEATURE_CONFLICT,
                            detail = "refusing: ${activeMember.first.qualifiedName} holds an active " +
                                "confirmed value (${activeMember.second}) and the exclusion group " +
                                "${group.map { it.qualifiedName }} permits only one active member: " +
                                relation.reason,
                            blocksRead = false,
                            blocksWrite = true,
                        )
                    }
                }

                is FeatureRelation.Implies -> {
                    val edge = CapabilityDependency(relation.feature, relation.implied)
                    if (edge.feature in inCycle || edge.requires in inCycle) {
                        issues += FeatureRelationIssue(
                            relation = relation,
                            code = FeatureErrorCode.DEPENDENCY_NOT_SATISFIED,
                            detail = "${relation.feature.qualifiedName} implies " +
                                "${relation.implied.qualifiedName}, which sits in a dependency cycle " +
                                "${validation.cycles}: a cycle is a modelling error, not a resolvable graph",
                            blocksRead = true,
                            blocksWrite = true,
                        )
                    } else if (validation.statuses[edge] == DependencyStatus.MISSING_PREREQUISITE) {
                        // Informational: the definition claims an implication the device
                        // contradicts. Surfaced, never silently repaired.
                        issues += FeatureRelationIssue(
                            relation = relation,
                            code = FeatureErrorCode.DEPENDENCY_NOT_SATISFIED,
                            detail = "modelling warning: ${relation.feature.qualifiedName} implies " +
                                "${relation.implied.qualifiedName}, which discovery positively " +
                                "established as unsupported on this device",
                            blocksRead = false,
                            blocksWrite = false,
                        )
                    }
                }

                is FeatureRelation.VendorException -> vendorNotes += relation
            }
        }

        return FeatureRelationReport(
            feature = feature,
            issues = issues,
            cycles = validation.cycles,
            vendorNotes = vendorNotes,
        )
    }

    /**
     * Convenience overload for a single definition evaluated against its own
     * relations only. Cross-definition cycles are invisible to this overload —
     * the engine uses the full overload — so it is for tests and simple
     * callers, not for operation gating.
     */
    fun evaluate(
        definition: FeatureDefinition,
        capabilities: DeviceCapabilities,
        currentStates: Map<FeatureId, FeatureState>,
    ): FeatureRelationReport = evaluate(
        feature = definition.feature,
        ownRelations = definition.relations,
        allRelations = definition.relations,
        capabilities = capabilities,
        currentStates = currentStates,
    )

    private fun requiresIssue(
        relation: FeatureRelation.Requires,
        edge: CapabilityDependency,
        status: DependencyStatus?,
        inCycle: Set<FeatureId>,
    ): FeatureRelationIssue {
        val names = "${relation.feature.qualifiedName} requires ${relation.prerequisite.qualifiedName}"
        return when {
            edge.feature in inCycle || edge.requires in inCycle ->
                FeatureRelationIssue(
                    relation = relation,
                    code = FeatureErrorCode.DEPENDENCY_NOT_SATISFIED,
                    detail = "$names, but the prerequisite sits in a dependency cycle: " +
                        "a cycle is a modelling error, not a resolvable graph",
                    blocksRead = true,
                    blocksWrite = true,
                )

            status == DependencyStatus.MISSING_PREREQUISITE ->
                FeatureRelationIssue(
                    relation = relation,
                    code = FeatureErrorCode.DEPENDENCY_NOT_SATISFIED,
                    detail = "$names, but discovery positively established " +
                        "${relation.prerequisite.qualifiedName} as unsupported on this device",
                    blocksRead = true,
                    blocksWrite = true,
                )

            status == DependencyStatus.UNKNOWN_PREREQUISITE ->
                FeatureRelationIssue(
                    relation = relation,
                    code = FeatureErrorCode.DEPENDENCY_NOT_SATISFIED,
                    detail = "$names, but nothing is established about " +
                        "${relation.prerequisite.qualifiedName}: unknown is not satisfied, so the " +
                        "write is refused rather than attempted blind",
                    blocksRead = false,
                    blocksWrite = true,
                )

            else -> FeatureRelationIssue(
                relation = relation,
                code = FeatureErrorCode.DEPENDENCY_NOT_SATISFIED,
                detail = "$names: satisfied",
                blocksRead = false,
                blocksWrite = false,
            )
        }
    }

    private fun requiresOneOfIssue(
        relation: FeatureRelation.RequiresOneOf,
        edges: List<CapabilityDependency>,
        validation: com.omnibuds.core.capability.DependencyReport,
        inCycle: Set<FeatureId>,
    ): FeatureRelationIssue {
        val statuses = edges.map { validation.statuses[it] }
        val names = relation.options.map { it.qualifiedName }
        val anyCycled = edges.any { it.feature in inCycle || it.requires in inCycle }
        val anySatisfied = statuses.any { it == DependencyStatus.SATISFIED }
        val anyNotMissing = statuses.any { it != DependencyStatus.MISSING_PREREQUISITE }
        return when {
            anyCycled -> FeatureRelationIssue(
                relation = relation,
                code = FeatureErrorCode.DEPENDENCY_NOT_SATISFIED,
                detail = "${relation.feature.qualifiedName} requires one of $names, but a " +
                    "prerequisite option sits in a dependency cycle: a cycle is a modelling " +
                    "error, not a resolvable graph",
                blocksRead = true,
                blocksWrite = true,
            )

            !anySatisfied -> FeatureRelationIssue(
                relation = relation,
                code = FeatureErrorCode.DEPENDENCY_NOT_SATISFIED,
                detail = "${relation.feature.qualifiedName} requires one of $names, but none " +
                    "is established as supported on this device",
                blocksRead = !anyNotMissing,
                blocksWrite = true,
            )

            else -> FeatureRelationIssue(
                relation = relation,
                code = FeatureErrorCode.DEPENDENCY_NOT_SATISFIED,
                detail = "${relation.feature.qualifiedName} requires one of $names: satisfied",
                blocksRead = false,
                blocksWrite = false,
            )
        }
    }

    /**
     * The active value another feature holds, or null when it holds none.
     *
     * A [FeatureState.Confirmed] value that [isActiveValue] calls engaged counts,
     * and so does a [FeatureState.Pending] request for an engaged value: a write
     * already in flight for the other feature would race this one. Anything else —
     * unknown, available, failed, unavailable, or a confirmed inert value — is not
     * a conflict.
     */
    private fun activeValue(
        other: FeatureId,
        currentStates: Map<FeatureId, FeatureState>,
    ): ConfigurationValue? {
        val state = currentStates[other] ?: return null
        val value: ConfigurationValue? = when (state) {
            is FeatureState.Confirmed -> state.value
            is FeatureState.Pending -> state.requested
            else -> null
        }
        return if (value != null && isActiveValue(value)) value else null
    }
}
