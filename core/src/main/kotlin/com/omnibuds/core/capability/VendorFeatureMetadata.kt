package com.omnibuds.core.capability

import com.omnibuds.core.state.VerificationLevel

/**
 * Where a vendor-only feature came from, and how far that source can be trusted.
 *
 * This is provenance for a functionality OmniBuds has *heard of*, not evidence that it
 * works. Phase 0 governs vendor features by name and by citation precisely so that an
 * advertised capability, a manual page or another app's toggle cannot become a support
 * claim by the time it reaches a record (PROTO-VENDOR-001..004, protocol-governance
 * section 7).
 *
 * [vendor] is a namespace segment, not a label: it is the value compared against the
 * vendor segment of a `vendor.<vendor>.<feature>` identity by [VendorExtension], so a
 * mismatch between the two is a construction failure rather than a silent miss. That is
 * why it is validated against the same grammar [com.omnibuds.core.common.FeatureId]
 * accepts, in lower case: `Sony` would never equal a real id segment, and an
 * unmatchable-but-accepted value is exactly the kind of quiet no-op this model refuses.
 *
 * [confidence] describes the strength of *this* provenance. Documentation of a feature
 * earns `INFERRED` at best — nothing in a manual has ever been read back from a device —
 * and it is not the same field as a capability's verification level, which is earned per
 * device (PROTO-VERIFY-005).
 */
data class VendorFeatureMetadata(
    /** The vendor namespace segment this feature belongs to, e.g. `sony`. */
    val vendor: String,
    /** The manufacturer's own name for it, kept verbatim so the citation survives translation. */
    val vendorFeatureName: String?,
    /** A citation — document, release note, observed control — not a protocol description. */
    val documentation: String?,
    /** How strongly this provenance supports the claim that the feature exists at all. */
    val confidence: VerificationLevel,
) {

    init {
        require(vendor.matches(vendorSegmentPattern)) {
            "vendor '$vendor' is not a usable namespace segment: lower-case kebab, like the " +
                "segments of FeatureId.of, so it can be compared against a feature identity"
        }
        require(vendorFeatureName == null || vendorFeatureName.isNotBlank()) {
            "vendorFeatureName for vendor '$vendor' is blank; use null when the manufacturer's own " +
                "wording is not recorded"
        }
        require(documentation == null || documentation.isNotBlank()) {
            "documentation for vendor '$vendor' is blank; an empty citation is not a citation"
        }
    }

    companion object {
        /**
         * The segment grammar [com.omnibuds.core.common.FeatureId] enforces. It is
         * restated here because the kernel keeps its own copy private, and the
         * [VendorExtension] vendor comparison is only meaningful if both sides accept the
         * same shape.
         */
        private val vendorSegmentPattern: Regex = Regex("[a-z][a-z0-9]*(-[a-z0-9]+)*")
    }
}
