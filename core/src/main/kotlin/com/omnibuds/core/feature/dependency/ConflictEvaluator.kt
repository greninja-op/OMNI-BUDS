package com.omnibuds.core.feature.dependency

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.feature.FeatureRelation

/**
 * What the evaluator knows about one feature on the target device.
 *
 * Phase 29 (OB-P29-REQ-005): unknown/stale are never treated as satisfied.
 */
enum class FeatureAvailability {
    UNKNOWN,
    UNSUPPORTED,
    UNAVAILABLE,
    AVAILABLE,
    ENABLED,
    PENDING,
    STALE,
}

/** True when the feature may be treated as established. */
val FeatureAvailability.isEstablished: Boolean
    get() = this == FeatureAvailability.AVAILABLE || this == FeatureAvailability.ENABLED

/**
 * A proposed configuration: features to enable.
 */
data class ProposedConfiguration(
    val deviceId: String,
    /** Features the caller wants enabled. */
    val enable: Set<FeatureId>,
    /** Current availability snapshot per feature. */
    val availability: Map<FeatureId, FeatureAvailability>,
)

/**
 * Conflict evaluation outcomes.
 */
sealed interface ConflictFinding {
    /** Rule responsible. */
    val ruleId: String

    /** A verified conflict: execution must be blocked. */
    data class HardConflict(
        override val ruleId: String,
        val message: String,
        val involved: Set<FeatureId>,
    ) : ConflictFinding

    /** Uncertainty: execution may proceed only if the caller accepts it. */
    data class Advisory(
        override val ruleId: String,
        val message: String,
        val involved: Set<FeatureId>,
    ) : ConflictFinding

    /** Cannot be resolved with current information. */
    data class Unresolved(
        override val ruleId: String,
        val message: String,
        val involved: Set<FeatureId>,
    ) : ConflictFinding
}

/**
 * The evaluation result for a proposed configuration.
 */
data class ConflictEvaluation(
    val valid: Boolean,
    val findings: List<ConflictFinding>,
) {
    /** True when no hard conflict blocks execution. */
    val mayExecute: Boolean
        get() = findings.none { it is ConflictFinding.HardConflict }
}

/**
 * Deterministic conflict evaluator.
 *
 * Phase 29: pure, no hardware side effects. Inferred relationships never
 * produce hard conflicts (OB-P29-REQ-002).
 */
object ConflictEvaluator {

    /**
     * Evaluate a proposed configuration against active rules.
     */
    fun evaluate(
        graph: RelationshipGraph,
        config: ProposedConfiguration,
        manufacturer: String?,
        model: String?,
        firmware: String?,
    ): ConflictEvaluation {
        val findings = mutableListOf<ConflictFinding>()
        val active = graph.activeFor(manufacturer, model, firmware)

        for (rule in active) {
            when (val relation = rule.relation) {
                is FeatureRelation.Requires ->
                    checkRequires(rule, relation, config, findings)
                is FeatureRelation.RequiresOneOf ->
                    checkRequiresOneOf(rule, relation, config, findings)
                is FeatureRelation.ConflictsWith ->
                    checkConflictsWith(rule, relation, config, findings)
                is FeatureRelation.MutuallyExclusive ->
                    checkMutuallyExclusive(rule, relation, config, findings)
                is FeatureRelation.Implies -> {
                    // Implies is informational here: the engine never
                    // silently enables the implied feature (OB-P29-REQ-010).
                }
                is FeatureRelation.VendorException -> {
                    // Vendor exceptions relax a rule; handled by the caller
                    // when assembling the rule set.
                }
            }
        }

        val sorted = findings.sortedBy { it.ruleId }
        return ConflictEvaluation(
            valid = sorted.none { it is ConflictFinding.HardConflict },
            findings = sorted,
        )
    }

    private fun availabilityOf(
        config: ProposedConfiguration,
        feature: FeatureId,
    ): FeatureAvailability =
        config.availability[feature] ?: FeatureAvailability.UNKNOWN

    private fun emit(
        rule: RelationshipRule,
        message: String,
        involved: Set<FeatureId>,
        findings: MutableList<ConflictFinding>,
    ) {
        val finding = if (rule.provenance.verification.mayBlock) {
            ConflictFinding.HardConflict(rule.provenance.ruleId, message, involved)
        } else {
            ConflictFinding.Advisory(rule.provenance.ruleId, message, involved)
        }
        findings.add(finding)
    }

    private fun checkRequires(
        rule: RelationshipRule,
        relation: FeatureRelation.Requires,
        config: ProposedConfiguration,
        findings: MutableList<ConflictFinding>,
    ) {
        if (relation.feature !in config.enable) return
        when (availabilityOf(config, relation.prerequisite)) {
            FeatureAvailability.UNKNOWN, FeatureAvailability.STALE -> findings.add(
                ConflictFinding.Unresolved(
                    rule.provenance.ruleId,
                    "prerequisite ${relation.prerequisite.qualifiedName} state unknown; " +
                        "cannot verify requirement for ${relation.feature.qualifiedName}",
                    setOf(relation.feature, relation.prerequisite),
                ),
            )
            FeatureAvailability.UNSUPPORTED, FeatureAvailability.UNAVAILABLE ->
                emit(
                    rule,
                    "prerequisite ${relation.prerequisite.qualifiedName} not supported; " +
                        "${relation.feature.qualifiedName} cannot be enabled",
                    setOf(relation.feature, relation.prerequisite),
                    findings,
                )
            else -> Unit // AVAILABLE, ENABLED, PENDING: satisfied or in progress.
        }
    }

    private fun checkRequiresOneOf(
        rule: RelationshipRule,
        relation: FeatureRelation.RequiresOneOf,
        config: ProposedConfiguration,
        findings: MutableList<ConflictFinding>,
    ) {
        if (relation.feature !in config.enable) return
        val states = relation.options.map { availabilityOf(config, it) }
        if (states.any { it.isEstablished }) return
        if (states.any { it == FeatureAvailability.UNKNOWN || it == FeatureAvailability.STALE }) {
            findings.add(
                ConflictFinding.Unresolved(
                    rule.provenance.ruleId,
                    "none of the alternatives for ${relation.feature.qualifiedName} " +
                        "is confirmed established; some states unknown",
                    setOf(relation.feature) + relation.options,
                ),
            )
        } else {
            emit(
                rule,
                "none of the required alternatives for ${relation.feature.qualifiedName} " +
                    "is available",
                setOf(relation.feature) + relation.options,
                findings,
            )
        }
    }

    private fun checkConflictsWith(
        rule: RelationshipRule,
        relation: FeatureRelation.ConflictsWith,
        config: ProposedConfiguration,
        findings: MutableList<ConflictFinding>,
    ) {
        if (relation.feature !in config.enable) return
        val otherAvail = availabilityOf(config, relation.other)
        if (otherAvail == FeatureAvailability.ENABLED || relation.other in config.enable) {
            emit(
                rule,
                "${relation.feature.qualifiedName} conflicts with " +
                    "${relation.other.qualifiedName}",
                setOf(relation.feature, relation.other),
                findings,
            )
        }
    }

    private fun checkMutuallyExclusive(
        rule: RelationshipRule,
        relation: FeatureRelation.MutuallyExclusive,
        config: ProposedConfiguration,
        findings: MutableList<ConflictFinding>,
    ) {
        val members = setOf(relation.feature) + relation.others
        val active = members.filter { feature ->
            feature in config.enable ||
                availabilityOf(config, feature) == FeatureAvailability.ENABLED
        }
        if (active.size > 1) {
            emit(
                rule,
                "mutually exclusive features active: " +
                    active.joinToString { it.qualifiedName },
                active.toSet(),
                findings,
            )
        }
    }
}
