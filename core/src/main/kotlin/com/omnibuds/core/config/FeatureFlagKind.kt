package com.omnibuds.core.config

/**
 * The only kinds of thing a feature flag may gate.
 *
 * Quoting the rule this enum exists to enforce (Phase 1 execution prompt section 40):
 *
 * ```text
 * If feature flags are introduced:
 *
 * They must never be used to fake hardware capabilities.
 *
 * A flag may control:
 *   experimental UI
 *   experimental protocol adapter
 *   debug diagnostics
 *
 * It must not turn:
 *   unsupported ANC
 * into:
 *   supported ANC
 * ```
 *
 * So a flag gates *presentation*, *adapter selection* and *diagnostics*. Support
 * itself is a discovery result owned by `com.omnibuds.core.state.CapabilityState`,
 * established per device by protocol reads and hardware verification — a fact no
 * configuration value can overwrite ([grantsCapabilitySupport] is `false` for every
 * kind, and [FeatureFlag] refuses to construct against anything else).
 *
 * Membership here is exhaustive and closed: [permitted] is exactly this set, and a
 * test pins it, so a fourth kind cannot be added quietly. Anything that needs a kind
 * beyond these three is not a flag — it is a capability claim, and it needs evidence.
 */
enum class FeatureFlagKind(
    /**
     * Whether turning a flag of this kind on would make OmniBuds claim that a device
     * supports something.
     *
     * `false` for all three kinds, permanently. A `true` here would mean the flag is
     * permitted to manufacture capability support, which is exactly the thing the
     * project forbids.
     */
    val grantsCapabilitySupport: Boolean,
) {
    /** Shows or hides unfinished interface. Changes nothing about the device. */
    EXPERIMENTAL_UI(grantsCapabilitySupport = false),

    /**
     * Selects an unproven protocol adapter.
     *
     * Gating *which* code path attempts a discovery or write is a flag decision.
     * Whether the device supports the feature remains whatever the adapter reads
     * back, and an unproven adapter may only produce `UNKNOWN` or a verified state —
     * never a supported state borrowed from a similar device.
     */
    EXPERIMENTAL_PROTOCOL_ADAPTER(grantsCapabilitySupport = false),

    /** Raises local diagnostic verbosity. Bounded by `DiagnosticMode`, never by itself. */
    DEBUG_DIAGNOSTIC(grantsCapabilitySupport = false),
    ;

    companion object {
        /** The complete, closed set of flag kinds. Adding a kind means amending this. */
        val permitted: Set<FeatureFlagKind> = setOf(
            FeatureFlagKind.EXPERIMENTAL_UI,
            FeatureFlagKind.EXPERIMENTAL_PROTOCOL_ADAPTER,
            FeatureFlagKind.DEBUG_DIAGNOSTIC,
        )
    }
}
