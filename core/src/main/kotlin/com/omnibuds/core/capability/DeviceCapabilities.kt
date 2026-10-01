package com.omnibuds.core.capability

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.state.CapabilityState
import com.omnibuds.core.state.isControllable

/**
 * What has been discovered about one device, feature by feature.
 *
 * An immutable snapshot, not a mutable registry: a new discovery result, a re-read after
 * a failed write, or a database import all produce a *different* container
 * ([with], [mergedWith]) so that the capability engine stays the single authoritative
 * source for capability state and every mutation site is greppable (Phase 1 prompt
 * section 24, specs section 5.7).
 *
 * The one rule this container exists to protect is stated below in [stateOf]:
 * **a feature that is not present is `UNKNOWN`, not `UNSUPPORTED`, and not an error.**
 * A lookup miss is the most common event in this system — most devices do not expose
 * most features, and most sessions have not looked yet — so the shape of that answer
 * decides whether the UI ever renders "not supported" for something nobody checked
 * (PROTO-CAP-004, master section 53).
 *
 * Nothing here negotiates, caches, times out or talks to hardware. [empty] is a valid
 * answer for a device that has been discovered but not examined.
 */
class DeviceCapabilities(entries: Map<FeatureId, FeatureCapability>) {

    /**
     * Copied on construction, so a caller who handed in a mutable map cannot edit this
     * snapshot afterwards. The copy is taken explicitly rather than through `toMap()`,
     * which is permitted to hand back the very map it was given — and a defensive copy
     * that is sometimes not a copy is worse than one that is plainly never made.
     */
    private val entries: Map<FeatureId, FeatureCapability> = entries.toMutableMap()

    /**
     * The honest answer for "what does this device do with [feature]".
     *
     * Absence reads as [CapabilityState.UNKNOWN]. It never reads as
     * [CapabilityState.UNSUPPORTED], and it never throws — this is the line that keeps
     * "not discovered" from becoming "unsupported" (master section 53, REQ-P0-002).
     */
    fun stateOf(feature: FeatureId): CapabilityState =
        entries[feature]?.state ?: CapabilityState.UNKNOWN

    /**
     * The record for [feature], or `null` when nothing has been established about it.
     *
     * `null` here means "no record", which the container expresses as [stateOf]
     * returning [CapabilityState.UNKNOWN]; it is not a claim about the device.
     */
    operator fun get(feature: FeatureId): FeatureCapability? = entries[feature]

    /** Every feature this container holds a record for, including records that say `UNKNOWN`. */
    val features: Set<FeatureId>
        get() = entries.keys

    /**
     * Features that may be offered as controls, derived from state rather than from any
     * caller's opinion about writability (PROTO-CAP-005).
     */
    val controllable: Set<FeatureId>
        get() = featuresMatching { it.state.isControllable() }

    /** Features whose recorded affordance actually permits a reading. */
    val readable: Set<FeatureId>
        get() = featuresMatching { it.readable }

    /**
     * Features this container holds an explicit `UNKNOWN` record for.
     *
     * Not every feature that is unknown — an absent feature is equally unknown and is
     * simply not listed here, because there is nothing to list.
     */
    val unknown: Set<FeatureId>
        get() = featuresMatching { it.state == CapabilityState.UNKNOWN }

    /**
     * Features positively established as absent on this device.
     *
     * Deliberately narrow: only a conclusive negative earns membership, so this set is
     * empty for a device that has merely not been asked.
     */
    val unsupported: Set<FeatureId>
        get() = featuresMatching { it.state == CapabilityState.UNSUPPORTED }

    /**
     * This snapshot plus one record, as a new instance.
     *
     * The key and the record's own identity must agree: filing a capability about one
     * feature under a different feature's name would let [stateOf] answer a question
     * with evidence for something else, so it is refused rather than documented away.
     */
    fun with(feature: FeatureId, capability: FeatureCapability): DeviceCapabilities {
        require(capability.feature == feature) {
            "refusing to file ${capability.feature.qualifiedName} under the unrelated key ${feature.qualifiedName}"
        }
        val updated = entries.toMutableMap()
        updated[feature] = capability
        return DeviceCapabilities(updated)
    }

    /**
     * Combine two snapshots of the same device without losing evidence.
     *
     * **The rule.** For a feature present in both, the record on the higher
     * [CapabilityState] rung wins, because the rungs are an evidence ladder
     * (`UNKNOWN < UNSUPPORTED < READ_ONLY < SUPPORTED_VOLATILE < SUPPORTED_PERSISTENT <
     * PERSISTENCE_VERIFIED`) and a merge is not evidence. A tie keeps the *receiver's*
     * record wholesale, which makes the result deterministic and keeps the merge from
     * inventing a hybrid record out of two sources. Features only one side knows about
     * are carried through untouched.
     *
     * Consequences worth naming: a `PERSISTENCE_VERIFIED` record can never be quietly
     * replaced by a `SUPPORTED_VOLATILE` one from a later, weaker pass, and an explicit
     * `UNKNOWN` record in the receiver is still out-ranked by any established state in
     * [other] — re-discovery that has not concluded yet says nothing about what was
     * already proven. Moving a capability *down* the ladder is a withdrawal of evidence
     * with a recorded reason (PROTO-CAP-003), which is what [with] is for, not a merge.
     */
    fun mergedWith(other: DeviceCapabilities): DeviceCapabilities {
        if (other === this) return this
        val merged = entries.toMutableMap()
        for ((feature, capability) in other.entries) {
            val established = merged[feature]
            if (established == null || evidenceOf(capability.state) > evidenceOf(established.state)) {
                merged[feature] = capability
            }
        }
        return DeviceCapabilities(merged)
    }

    private fun featuresMatching(predicate: (FeatureCapability) -> Boolean): Set<FeatureId> =
        entries.values.filter(predicate).map { it.feature }.toSet()

    override fun equals(other: Any?): Boolean =
        other is DeviceCapabilities && other.entries == entries

    override fun hashCode(): Int = entries.hashCode()

    override fun toString(): String = if (entries.isEmpty()) {
        "DeviceCapabilities(nothing discovered)"
    } else {
        "DeviceCapabilities(" + entries.values.joinToString { "${it.feature.qualifiedName}=${it.state}" } + ")"
    }

    companion object {
        /**
         * A snapshot with no records: every feature is unknown, no feature is
         * unsupported, and no control may be offered.
         */
        fun empty(): DeviceCapabilities = EMPTY

        /**
         * The evidence ladder spelled out instead of read off `ordinal`.
         *
         * Declaration order of [CapabilityState] *is* the ladder, but writing the order
         * as numbers here means a future reorder of the kernel enum surfaces as a
         * non-exhaustive `when` at compile time rather than as a silent merge rule
         * change.
         */
        private fun evidenceOf(state: CapabilityState): Int = when (state) {
            CapabilityState.UNKNOWN -> 0
            CapabilityState.UNSUPPORTED -> 1
            CapabilityState.READ_ONLY -> 2
            CapabilityState.SUPPORTED_VOLATILE -> 3
            CapabilityState.SUPPORTED_PERSISTENT -> 4
            CapabilityState.PERSISTENCE_VERIFIED -> 5
        }

        private val EMPTY: DeviceCapabilities = DeviceCapabilities(emptyMap())
    }
}
