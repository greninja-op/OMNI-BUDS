package com.omnibuds.core.config

/**
 * One on/off switch for something the application is allowed to gate.
 *
 * A flag is a *statement about the app's own behaviour*, never about the device. It
 * carries a [kind] drawn from the closed [FeatureFlagKind.permitted] set, and the
 * construction below makes the guard invariant explicit rather than leaving it to
 * reviewer discipline.
 *
 * Note what is deliberately absent: there is no [com.omnibuds.core.common.FeatureId]
 * here and no `CapabilityState`. That is structural, not lazy — a flag keyed by a
 * feature identity and able to carry a state would be one field away from turning
 * "unsupported ANC" into "supported ANC" by editing configuration. Feature identity
 * lives in device configuration and in the capability model; gating lives here.
 */
data class FeatureFlag(
    /** Stable, namespaced flag identity, e.g. `ui.gesture-editor-v2`. */
    val id: String,
    /** What class of thing this flag gates; see [FeatureFlagKind]. */
    val kind: FeatureFlagKind,
    /** Whether the gated, non-capability behaviour is currently switched on. */
    val enabled: Boolean,
) {
    init {
        require(id.isNotBlank()) { "feature flag id cannot be blank" }
        require(kind in FeatureFlagKind.permitted) {
            "feature flag kind $kind is outside the permitted set; a new kind needs an ADR first"
        }
        require(!kind.grantsCapabilitySupport) {
            "feature flag kind $kind would grant hardware capability support, which is prohibited " +
                "(Phase 1 execution prompt section 40)"
        }
    }
}
