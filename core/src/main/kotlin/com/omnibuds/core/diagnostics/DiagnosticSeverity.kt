package com.omnibuds.core.diagnostics

/**
 * How loud a diagnostic event is, and whether hearing it costs the user privacy.
 *
 * The split matters more than the ordering. `TRACE` and `PACKET` are reserved levels
 * that "must be opt-in and privacy-aware" (Phase 1 execution prompt section 41): they
 * exist so that verbose and traffic-level detail can be named, gated and refused, not
 * so that a caller can emit it because it looked like the right verbosity.
 * [requiresOptIn] is how that gate is expressed in the type, and
 * `com.omnibuds.core.config.DiagnosticMode` is where the user's decision is recorded.
 *
 * The four always-available levels match the minimal logging foundation the prompt
 * asks for: `DEBUG`, `INFO`, `WARN`, `ERROR`.
 */
enum class DiagnosticSeverity(
    /** Whether emitting at this level requires a recorded user opt-in first. */
    val requiresOptIn: Boolean,
) {
    /** Very fine program flow. Opt-in: volume alone makes it a privacy risk. */
    TRACE(requiresOptIn = true),

    /** Developer-facing detail; allowed, but still bounded by what it may contain. */
    DEBUG(requiresOptIn = false),

    /** Normal operation worth recording: a session started, a state transitioned. */
    INFO(requiresOptIn = false),

    /** Something degraded but handled: a read that came back partial, a stale cache. */
    WARN(requiresOptIn = false),

    /** An operation failed. Usually paired with a structured `OmniBudsError`. */
    ERROR(requiresOptIn = false),

    /**
     * Raw traffic.
     *
     * Opt-in per session only, and prohibited in release builds at any level
     * (`SEC-LOG-004`). Packet content can carry identifiers, manufacturer data and
     * pairing or identity material, so this level is a user decision
     * (`SEC-LOG-005`), not a logging convenience.
     */
    PACKET(requiresOptIn = true),
}
