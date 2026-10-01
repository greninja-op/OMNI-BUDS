package com.omnibuds.core.config

/**
 * How much diagnostic work the user has agreed to.
 *
 * This is *application* configuration: it governs what OmniBuds records about its own
 * behaviour, never what a device is capable of (execution prompt section 39).
 *
 * Every level above [OFF] is a deliberate user decision. Logging governance binds
 * this: redaction is owed at the point of emission, not on export
 * (`SEC-LOG-002`), and packet capture is opt-in per session with the opt-in stated
 * plainly (`SEC-LOG-005`). A mode of [OFF] therefore means nothing is recorded, and
 * no code path may treat "not asked" as "assumed on".
 */
enum class DiagnosticMode(
    /** Whether this level may only be reached by an explicit user decision. */
    val requiresOptIn: Boolean,
) {
    /** Nothing beyond ordinary program flow. The default, and the safe state. */
    OFF(requiresOptIn = false),

    /** Verbose logging kept on the local machine for a developer session. */
    LOCAL_DEBUG(requiresOptIn = true),

    /**
     * Capturing the traffic of one session.
     *
     * This level may persist protocol traffic, and captured traffic can contain
     * Bluetooth addresses, device names bound to an address and manufacturer data —
     * that is personal data, not a debug curiosity (`SEC-LOG-005`). It therefore
     * requires an explicit user decision, an expiry/retention limit and a visible
     * wipe before any capture starts (`SEC-LOG-006`). Phase 1 defines the mode only;
     * it implements no capture at all.
     */
    SESSION_CAPTURE(requiresOptIn = true),
}
