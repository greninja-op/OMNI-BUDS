package com.omnibuds.core.capability

import com.omnibuds.core.common.FeatureId

/**
 * One manufacturer-specific feature as OmniBuds holds it: provenance, capability state
 * and an opaque payload (Phase 1 prompt section 21).
 *
 * The `init` block is the guard the whole vendor strategy depends on. A vendor-only
 * feature may only be carried under a `vendor.<vendor>.<feature>` identity that names the
 * same vendor its [metadata] does, which blocks both directions of the mistake it is easy
 * to make:
 *
 *  - a vendor-only behaviour cannot be registered as, or consumed as, a universal core
 *    feature, because a [CoreFeature] identity is rejected here outright;
 *  - and a universal feature cannot be quietly redefined by one manufacturer's version of
 *    it, because the vendor's own behaviour has to be filed under its own namespace, where
 *    it cannot overwrite `noise-control.anc` or whatever the shared model calls the thing.
 *
 * Nothing is renamed, folded, or matched on a substring: identity is checked segment by
 * segment, so a vendor cannot smuggle a redefinition through a similar name.
 *
 * Presence of a vendor extension is still not support. The honest state for "this vendor
 * advertises it, OmniBuds has not modelled it" is a [FeatureCapability] in
 * [com.omnibuds.core.state.CapabilityState.UNKNOWN] with unmapped payload — recorded, not
 * dropped, and never rendered as a control (PROTO-VENDOR-002..003).
 *
 * [payload] is an opaque string carried alongside the record for whatever the owning layer
 * puts there. It is **not** protocol data: no command bytes, no framing, no encoded
 * packets, and nothing in Phase 1 defines or decodes it (prompt sections 21 and 51).
 *
 * ### Why the vendor segment is checked here as well as in [FeatureId]
 *
 * [FeatureId.isVendorExtension] answers whether the identity is shaped like
 * `vendor.<vendor>.<feature>`. That is necessary but not sufficient: an extension record
 * also has to agree with its own metadata about *which* vendor it belongs to, so `init`
 * compares the vendor segment against [VendorFeatureMetadata.vendor]. A record naming one
 * vendor in its id and another in its metadata would misattribute knowledge between
 * manufacturers, which no single type on its own can detect.
 */
data class VendorExtension(
    /** Where the feature came from and how far that source is trusted. */
    val metadata: VendorFeatureMetadata,
    /**
     * The device-specific standing of the feature, which may well be `UNKNOWN`; a vendor
     * extension carries no more authority than that.
     */
    val capability: FeatureCapability,
    /** Opaque carrier for vendor-side detail. Never protocol bytes, never parsed here. */
    val payload: String? = null,
) {

    /** The identity this extension is filed under; always vendor-namespaced. */
    val feature: FeatureId
        get() = capability.feature

    init {
        val id = capability.feature
        require(isVendorFeature(id)) {
            "${id.qualifiedName} is not a vendor-namespaced feature identity, so it cannot be carried as a " +
                "vendor extension: universal features belong in CoreFeature, and vendor-only ones are " +
                "addressed as vendor.<vendor>.<feature>"
        }
        require(vendorSegmentOf(id) == metadata.vendor) {
            "${id.qualifiedName} names vendor '${vendorSegmentOf(id) ?: "nobody"}', not '${metadata.vendor}', " +
                "which is the vendor its metadata is registered under"
        }
    }

    companion object {
        /** The reserved namespace root every vendor extension identity starts with. */
        const val VENDOR_ROOT: String = "vendor"

        /** Segments in the shortest valid `vendor.<vendor>.<feature>` identity. */
        private const val VENDOR_ID_SEGMENTS: Int = 3

        /**
         * Whether [feature] is a vendor-only identity that may be carried by a
         * [VendorExtension]: the documented `vendor.<vendor>.<feature>` shape, which is
         * also the only shape [FeatureId.ofVendor] produces.
         */
        fun isVendorFeature(feature: FeatureId): Boolean = feature.isVendorExtension

        /**
         * The vendor segment of a `vendor.<vendor>.<feature>` identity, or `null` when the
         * id is not vendor-namespaced deeply enough to name a vendor.
         */
        internal fun vendorSegmentOf(feature: FeatureId): String? {
            val segments = feature.qualifiedName.split('.')
            return if (segments.size >= VENDOR_ID_SEGMENTS && segments.first() == VENDOR_ROOT) {
                segments[1]
            } else {
                null
            }
        }
    }
}
