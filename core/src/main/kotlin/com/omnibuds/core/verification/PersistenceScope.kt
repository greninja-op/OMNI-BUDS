package com.omnibuds.core.verification

/**
 * How long a setting is proven to persist.
 *
 * Phase 18 (OB-P18-REQ-003, §5): ordered by strength. Each scope has an
 * explicit, testable meaning. UNKNOWN is valid — not an error.
 */
enum class PersistenceScope {
    /** Proven only within the current control session. */
    SESSION_ONLY,

    /** Survives control-session boundary (new control session, same connection). */
    CONTROL_SESSION_PERSISTENT,

    /** Survives genuine disconnect/reconnect. */
    CONNECTION_PERSISTENT,

    /** Survives app process restart + hardware read-back. */
    APPLICATION_RESTART_PERSISTENT,

    /** Survives device power off/on + read-back. */
    DEVICE_REBOOT_PERSISTENT,

    /** Explicitly established as firmware-retained. Strongest claim. */
    FIRMWARE_PERSISTENT,

    /** Scope not established. Valid result. */
    UNKNOWN,
}

/**
 * Application status dimension, independent of persistence scope.
 *
 * Phase 18 (OB-P18-REQ-004): whether the value was applied is separate
 * from how long it persists.
 */
enum class ApplicationStatus {
    NOT_ATTEMPTED,
    REQUESTED,
    ACKNOWLEDGED,
    READ_BACK_CONFIRMED,
    REJECTED,
    UNKNOWN,
}

/** Ordered comparison: stronger scopes are greater. */
fun PersistenceScope.strongerThan(other: PersistenceScope): Boolean {
    if (other == PersistenceScope.UNKNOWN) return this != PersistenceScope.UNKNOWN
    if (this == PersistenceScope.UNKNOWN) return false
    // Ordinal order: SESSION_ONLY(0) weakest → FIRMWARE_PERSISTENT(5) strongest.
    return this.ordinal > other.ordinal
}
