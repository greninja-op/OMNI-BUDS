package com.omnibuds.core.config

/**
 * Configuration owned by the application itself.
 *
 * Phase 1 execution prompt section 39 requires three configuration concepts that do
 * not mix, and this is the first: how *OmniBuds* behaves. It contains logging and
 * diagnostic levels plus gated app behaviour.
 *
 * It deliberately cannot express anything about a device:
 *  - no [com.omnibuds.core.common.FeatureId] keyed values (that is
 *    [DeviceConfiguration], which describes settings *on* the earbud),
 *  - no protocol id, transport or timeout/retry budget (that is
 *    [ProtocolConfiguration], which describes how a control channel is addressed).
 *
 * The three types share no supertype and no fields, so passing an
 * `ApplicationConfiguration` where a `DeviceConfiguration` is expected is a type
 * error, not a code-review finding.
 *
 * The defaults are conservative on purpose: logging quiet, diagnostics off. Nothing
 * in this type turns on a capability or a capture by accident.
 */
data class ApplicationConfiguration(
    /** Whether verbose debug logging is emitted. Off unless the user says so. */
    val debugLoggingEnabled: Boolean,
    /** How much diagnostic work the user has agreed to; see [DiagnosticMode]. */
    val diagnosticMode: DiagnosticMode,
    /** Gated app behaviour. A flag switches UI/adapter/diagnostics, never support. */
    val featureFlags: Set<FeatureFlag>,
) {
    init {
        // A diagnostic mode that was never opted into cannot be the silent default.
        require(diagnosticMode == DiagnosticMode.OFF || diagnosticMode.requiresOptIn) {
            "diagnostic mode ${diagnosticMode.name} is above OFF but does not declare an opt-in requirement"
        }
    }

    /** The flag with this id, or null when the id is not configured at all. */
    fun flagFor(id: String): FeatureFlag? = featureFlags.firstOrNull { it.id == id }

    /**
     * Whether a gated behaviour is switched on.
     *
     * Reads a flag; never implies that a device supports the gated thing. Callers
     * still have to consult the capability model for support.
     */
    fun isGateOpen(id: String): Boolean = flagFor(id)?.enabled == true

    companion object {
        /**
         * The shipping default: debug logging off, diagnostics off, no gates open.
         *
         * Silence is the default because the cheapest default to reverse is a loud
         * one, and identifier leakage is not recoverable after the fact.
         */
        fun defaults(): ApplicationConfiguration = ApplicationConfiguration(
            debugLoggingEnabled = false,
            diagnosticMode = DiagnosticMode.OFF,
            featureFlags = emptySet(),
        )
    }
}
