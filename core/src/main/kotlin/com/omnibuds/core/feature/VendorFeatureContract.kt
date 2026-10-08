package com.omnibuds.core.feature

import com.omnibuds.core.capability.FeatureCategory
import com.omnibuds.core.common.FeatureId

/**
 * The contract for vendor-specific features at the feature-control layer.
 *
 * "Universal" does not mean the lowest common denominator: a manufacturer's
 * unique capability must stay addressable instead of being discarded. The rules
 * below are the feature layer's half of the vendor strategy whose capability
 * half lives in [com.omnibuds.core.capability.VendorExtension]
 * (PROTO-VENDOR-001..004, Phase 1 prompt section 21):
 *
 * 1. **Namespace.** A vendor feature is addressed as
 *    `vendor.<vendor>.<feature>` and nothing else. The factory below refuses
 *    anything else, so a vendor-only behaviour can never be registered as, or
 *    consumed as, a universal core feature — and a universal feature can never
 *    be quietly redefined by one manufacturer's version of it.
 * 2. **No brand branches.** Shared engine code never conditions on the vendor
 *    segment. A vendor feature is driven through the same [FeatureEngine],
 *    [FeatureValidator] and [FeatureState] machinery as a core feature; the
 *    only vendor-aware code is the vendor's own protocol adapter in a later
 *    phase, behind the [FeatureProtocolPort] seam (ADR-P0-007).
 * 3. **Type safety.** Vendor values use the same [FeatureValueType] shapes as
 *    core values. Truly opaque manufacturer payloads ride as
 *    [FeatureValueType.CUSTOM] ([com.omnibuds.core.config.ConfigurationValue.CustomValue]),
 *    length-bounded and never parsed by the generic engine — interpretation
 *    belongs to the vendor's adapter, not to shared code.
 * 4. **Support is still discovered.** A vendor definition is a contract for
 *    *how* the feature would be driven, not a claim that any device implements
 *    it. The capability gate applies to vendor features exactly as to core
 *    ones: no record, no control.
 * 5. **No vendor commands in Phase 9.** Definitions describe; they do not
 *    encode. Opcodes, UUIDs, packet layouts and command bytes are forbidden
 *    here as everywhere in `:core` (master section 52, Phase 9 prompt
 *    section 27).
 */
object VendorFeatureContract {

    /**
     * Declares a vendor-specific feature contract.
     *
     * [vendor] is the namespace segment (lower-case kebab, matching
     * [FeatureId]'s grammar); [feature] is the feature name within it. The
     * resulting definition is filed under `vendor.<vendor>.<feature>` in the
     * [FeatureCategory.VENDOR] category.
     */
    fun define(
        vendor: String,
        feature: String,
        displayName: String,
        valueType: FeatureValueType,
        constraints: FeatureConstraints? = null,
        relations: List<FeatureRelation> = emptyList(),
    ): FeatureDefinition {
        val id = FeatureId.ofVendor(vendor, feature)
        return FeatureDefinition(
            feature = id,
            displayName = displayName,
            category = FeatureCategory.VENDOR,
            valueType = valueType,
            constraints = constraints,
            relations = relations,
            vendor = vendor,
        )
    }

    /**
     * Whether [feature] is a vendor-namespaced identity the engine may treat as
     * a vendor extension. Core identities are never vendor extensions, however
     * manufacturer-specific their behaviour.
     */
    fun isVendorFeature(feature: FeatureId): Boolean =
        feature.isVendorExtension
}
