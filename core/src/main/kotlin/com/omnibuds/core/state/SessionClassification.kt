package com.omnibuds.core.state

/**
 * Whether a device is present because the user saved it, or only because it is
 * currently connected.
 *
 * Saving is an explicit user action. OmniBuds must not quietly promote every device
 * it momentarily saw into a permanent record (ADR-P0-004, master section 5).
 */
enum class SessionClassification {
    /** Detected through the active session only; forgets when it goes away unless saved. */
    TEMPORARY,

    /** The user chose "Add to My Devices"; identity and discovered facts persist. */
    SAVED,
}
