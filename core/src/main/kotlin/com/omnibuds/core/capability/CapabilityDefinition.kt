package com.omnibuds.core.capability

import com.omnibuds.core.common.FeatureId

/**
 * What a feature identity *means*, for humans.
 *
 * The catalogue of definitions describes the vocabulary OmniBuds can reason about. It is
 * not a support list: it says nothing about any device, and a definition existing in a
 * catalogue is never evidence that anything implements it (Phase 1 prompt sections 14 and
 * 47: "Do not assume all are supported"). Device truth lives in [FeatureCapability]
 * records held by a [DeviceCapabilities] snapshot, and a feature with no record at all is
 * `UNKNOWN` rather than absent-and-therefore-unsupported.
 *
 * [supportedValueHint] is the field most likely to be misread, so it is named carefully and
 * documented harder: it is a note about the *shape* such a feature takes in the wild — the
 * kinds of values vendors commonly expose, the range a later phase should expect to
 * negotiate. It is documentation for a reader, never a claim of support, and it must not be
 * rendered as though the connected device offers what it lists. It may be `null`, which
 * simply means nobody has written the note yet.
 *
 * [displayName] is a label, not an identity: [feature] stays the key, and the label may be
 * reworded without breaking a contract (specs section 1.3).
 */
data class CapabilityDefinition(
    /** The stable identity this definition describes. */
    val feature: FeatureId,
    /** Human-facing label; never used as a lookup key or compared against by callers. */
    val displayName: String,
    /** The functional area this feature belongs to; a brand never appears here. */
    val category: FeatureCategory,
    /**
     * Documentation of the shape such a feature commonly takes. A note about devices in
     * general, *not* a statement that the device in hand supports any of it.
     */
    val supportedValueHint: String?,
) {

    init {
        require(displayName.isNotBlank()) {
            "capability definition for $feature needs a display name; a blank label is not a value"
        }
        require(supportedValueHint == null || supportedValueHint.isNotBlank()) {
            "supportedValueHint for $feature is blank; use null when the note has not been written"
        }
    }
}
