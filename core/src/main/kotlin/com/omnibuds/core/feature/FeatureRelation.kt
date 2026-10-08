package com.omnibuds.core.feature

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.config.ConfigurationValue

/**
 * A declared relationship between features, as the *definition* states it.
 *
 * Relations are the mechanism behind Phase 9 prompt section 19. They are
 * deliberately definition-level facts — "this control contract says X needs Y" —
 * and not universal hardware truths: the same pair of features may relate
 * differently on another device, and a protocol-specific edge discovered at
 * runtime belongs in [com.omnibuds.core.capability.CapabilityDependency], not
 * here. Declaring a relation in a [FeatureDefinition] *is* the explicit
 * establishment the prompt requires before any relationship may be treated as
 * real.
 *
 * The evaluator ([FeatureDependencyEvaluator]) never enables a prerequisite and
 * never disables a conflicting feature. An unsatisfied relation refuses the
 * operation with an explanation; what happens next is the caller's decision.
 */
sealed interface FeatureRelation {

    /** The feature this relation is declared on. */
    val feature: FeatureId

    /**
     * `feature` needs `prerequisite` established before it may be operated on.
     *
     * Evaluated through [com.omnibuds.core.capability.DependencyValidator], so
     * the requires-edge semantics — including cycle detection and the
     * unknown-is-not-missing rule — are shared with discovery rather than
     * reimplemented.
     */
    data class Requires(
        override val feature: FeatureId,
        val prerequisite: FeatureId,
    ) : FeatureRelation {
        init {
            require(feature != prerequisite) {
                "$feature cannot require itself; that is a cycle of length one and a modelling error"
            }
        }
    }

    /**
     * `feature` needs at least one of [options] established. For devices where
     * the same underlying capability surfaces under different identities.
     */
    data class RequiresOneOf(
        override val feature: FeatureId,
        val options: List<FeatureId>,
    ) : FeatureRelation {
        init {
            require(options.isNotEmpty()) { "requires-one-of with no options can never be satisfied" }
            require(feature !in options) {
                "$feature cannot be its own prerequisite option; that is a cycle of length one"
            }
            require(options.size == options.toSet().size) { "duplicate prerequisite options: $options" }
        }
    }

    /**
     * `feature` and [other] must not be driven at once: enabling one while the
     * other holds an *active* confirmed value would put the hardware in a
     * contradictory state.
     *
     * "Active" is decided by [FeatureDependencyEvaluator.isActiveValue], which
     * encodes the documented convention that a boolean `true`, a non-`off` mode
     * and a non-zero level mean "engaged". A conflict blocks the *write*; it never
     * silently turns the other feature off.
     */
    data class ConflictsWith(
        override val feature: FeatureId,
        val other: FeatureId,
        val reason: String,
    ) : FeatureRelation {
        init {
            require(feature != other) { "$feature cannot conflict with itself" }
            require(reason.isNotBlank()) { "a conflict must explain itself; a blank reason helps nobody" }
        }
    }

    /**
     * `feature` belongs to a group of which at most one member may hold an active
     * confirmed value — e.g. alternative write paths to the same DSP block.
     * [others] are the fellow members; [feature] itself is implied.
     */
    data class MutuallyExclusive(
        override val feature: FeatureId,
        val others: List<FeatureId>,
        val reason: String,
    ) : FeatureRelation {
        init {
            require(others.isNotEmpty()) { "a mutual-exclusion group needs at least one other member" }
            require(feature !in others) { "$feature is implied in its own exclusion group; do not list it" }
            require(others.size == others.toSet().size) { "duplicate group members: $others" }
            require(reason.isNotBlank()) { "an exclusion must explain itself; a blank reason helps nobody" }
        }
    }

    /**
     * `feature` being confirmed active suggests [implied] is meaningful on this
     * device — a hint for presentation and for later phases, never a block. If
     * [implied] is positively unsupported while `feature` is established, the
     * evaluator surfaces that as a modelling warning in the report rather than
     * refusing the operation.
     */
    data class Implies(
        override val feature: FeatureId,
        val implied: FeatureId,
    ) : FeatureRelation {
        init {
            require(feature != implied) { "$feature cannot imply itself" }
        }
    }

    /**
     * Documents that a relation which holds in general does *not* hold for
     * [vendor]'s devices. Informational only: the evaluator records it in the
     * report and never blocks on it. Vendor variance is data, not an excuse to
     * branch shared code on a brand (ADR-P0-007).
     */
    data class VendorException(
        override val feature: FeatureId,
        val vendor: String,
        val note: String,
    ) : FeatureRelation {
        init {
            require(vendor.isNotBlank()) { "a vendor exception must name its vendor" }
            require(note.isNotBlank()) { "a vendor exception must say what differs" }
        }
    }
}

/**
 * Whether a confirmed [ConfigurationValue] counts as "engaged" for conflict
 * evaluation.
 *
 * The convention, documented so it can be argued with: a boolean `true` is
 * engaged; a mode is engaged unless its technical name is `"off"`; a numeric
 * level is engaged unless it is zero; anything else confirmed is conservatively
 * treated as engaged, because the engine cannot know which value of an opaque
 * shape is the inert one. Definitions should therefore use the technical name
 * `"off"` for the inactive mode — the one universal claim this convention makes.
 */
fun isActiveValue(value: ConfigurationValue): Boolean = when (value) {
    is ConfigurationValue.BooleanValue -> value.value
    is ConfigurationValue.ModeValue -> value.technicalName != INACTIVE_MODE_NAME
    is ConfigurationValue.IntValue -> value.value != 0
    is ConfigurationValue.FloatValue -> value.value != 0.0
    is ConfigurationValue.RangeValue -> value.min != 0.0 || value.max != 0.0
    is ConfigurationValue.StringValue -> value.value.isNotEmpty()
    is ConfigurationValue.StructuredValue -> true
    is ConfigurationValue.BitmaskValue -> true
    is ConfigurationValue.CustomValue -> true
}

/** The technical mode name the conflict evaluator treats as "not engaged". */
const val INACTIVE_MODE_NAME: String = "off"
