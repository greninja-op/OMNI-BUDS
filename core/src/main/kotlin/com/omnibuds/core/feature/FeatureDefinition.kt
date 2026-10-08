package com.omnibuds.core.feature

import com.omnibuds.core.capability.FeatureCategory
import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.config.ConfigurationValue

/**
 * The control contract for one hardware feature.
 *
 * Where [com.omnibuds.core.capability.CapabilityDefinition] is the capability
 * layer's vocabulary — "this is a thing discovery can ask about" — this is the
 * feature layer's contract: "this is how the thing is *driven*". The two are
 * separate on purpose (ARCH-LAYER-003): capability truth stays owned by
 * discovery, while value shapes, constraints and inter-feature relations live
 * here, where the engine that issues commands can use them.
 *
 * A definition is still not a support claim. Listing `noise-control.anc` here
 * says "ANC, where it exists, is driven as a boolean"; whether the connected
 * device implements it is decided by discovery and recorded in
 * [com.omnibuds.core.capability.FeatureCapability] (Phase 1 prompt sections 14
 * and 47: "Do not assume all are supported").
 *
 * Two namespace rules, enforced in `init`:
 *
 *  - A core definition (no [vendor]) may not carry a `vendor.<vendor>.<feature>`
 *    identity — vendor-only behaviour is addressed through the vendor namespace,
 *    where it cannot overwrite or masquerade as a universal feature.
 *  - A vendor definition must carry a `vendor.<vendor>.<feature>` identity whose
 *    vendor segment matches [vendor], segment by segment, mirroring
 *    [com.omnibuds.core.capability.VendorExtension]'s guard.
 */
data class FeatureDefinition(
    /** Which feature this contract drives; identity, never a label. */
    val feature: FeatureId,
    /** Human-facing label; never used as a lookup key. */
    val displayName: String,
    /** The functional area this feature belongs to; a brand never appears here. */
    val category: FeatureCategory,
    /** The shape of value this feature takes. */
    val valueType: FeatureValueType,
    /** Bounds on acceptable values; null means no definition-level bound is stated. */
    val constraints: FeatureConstraints? = null,
    /** Inter-feature relationships this definition explicitly establishes. */
    val relations: List<FeatureRelation> = emptyList(),
    /**
     * The vendor namespace segment for a vendor-only feature, or null for a core
     * feature. Present exactly when [feature] is vendor-namespaced.
     */
    val vendor: String? = null,
) {

    init {
        require(displayName.isNotBlank()) {
            "feature definition for $feature needs a display name; a blank label is not a value"
        }
        if (vendor != null) {
            require(feature.isVendorExtension) {
                "${feature.qualifiedName} is not vendor-namespaced, so it cannot be a vendor definition: " +
                    "vendor-only behaviour belongs under vendor.<vendor>.<feature>"
            }
            require(feature.vendorName == vendor) {
                "${feature.qualifiedName} names vendor '${feature.vendorName}', not '$vendor'"
            }
        } else {
            require(!feature.isVendorExtension) {
                "${feature.qualifiedName} is vendor-namespaced but declares no vendor; " +
                    "core definitions cannot carry vendor identities"
            }
        }
        require(relations.all { it.feature == feature }) {
            "a definition for $feature cannot carry relations declared on another feature"
        }
    }

    /**
     * Validates a candidate value against this contract's shape and constraints.
     *
     * Returns `null` when the value is acceptable, or the failure code when it is
     * not. Shape is checked before constraints: a value of the wrong kind is
     * [FeatureErrorCode.INVALID_VALUE] with a shape explanation, never passed to
     * constraints that do not apply to it. Untrusted device values go through the
     * same check as requested ones — the engine validates both directions.
     */
    fun validateValue(value: ConfigurationValue): FeatureErrorCode? {
        if (!valueType.accepts(value)) {
            return FeatureErrorCode.INVALID_VALUE
        }
        val violation = constraints?.violationOf(value)
        return if (violation == null) null else FeatureErrorCode.INVALID_VALUE
    }

    /**
     * The human-readable reason a value failed validation, or null when it is
     * acceptable. Kept beside [validateValue] so error details can name the actual
     * problem (wrong shape vs. which bound) without re-running the checks.
     */
    fun validationDetail(value: ConfigurationValue): String? {
        if (!valueType.accepts(value)) {
            return "expected a ${valueType.name.lowercase()} value, got ${value::class.simpleName}"
        }
        return constraints?.violationOf(value)
    }
}
