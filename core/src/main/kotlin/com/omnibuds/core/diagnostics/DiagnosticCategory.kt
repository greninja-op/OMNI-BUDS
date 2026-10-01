package com.omnibuds.core.diagnostics

/**
 * Which part of the system a diagnostic event belongs to.
 *
 * A category is a routing and filtering key, not a severity and not an error: it says
 * where the observation came from so that a report can be read section by section and a
 * logger can decide what to keep. Permitted log content is enumerated by
 * `SEC-LOG-001` — timestamps, error categories, operations, transports, protocol
 * families, capability and codec transitions, verification-level changes — and these
 * categories are the axes that content sorts along.
 *
 * [PERSISTENCE] is the local storage concern; it is not permission to write, and the
 * storage rule is local-first with no background upload (`SEC-PRIV-001`, `SEC-LOG-008`).
 */
enum class DiagnosticCategory {
    /** Adapter, discovery, pairing-adjacent observation. */
    BLUETOOTH,

    /** GATT / RFCOMM / LE Audio channel behaviour. */
    TRANSPORT,

    /** Command construction, response parsing, protocol selection. */
    PROTOCOL,

    /** Capability discovery and capability state movement. */
    CAPABILITY,

    /** Audio route, codec and quality observation. */
    AUDIO,

    /** Device session lifecycle: identify, classify, save, forget. */
    SESSION,

    /** Reads and writes of local stored state. */
    PERSISTENCE,

    /** Runtime permission acquisition and refusal. */
    PERMISSION,

    /** Application, device or protocol configuration handling. */
    CONFIGURATION,

    /** Anything with no better home; a fallback, not a dumping ground. */
    GENERAL,
}
