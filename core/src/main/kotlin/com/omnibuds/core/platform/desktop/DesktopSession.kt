package com.omnibuds.core.platform.desktop

import com.omnibuds.core.common.TransportKind

/**
 * Handle to an active transport session on desktop.
 */
interface DesktopTransportSession {
    val transportKind: TransportKind
    val deviceIdentifier: String
    val isConnected: Boolean

    /** Deterministically close session and release native OS handles/descriptors. */
    suspend fun close()
}

/**
 * High-level desktop connection session state.
 *
 * Explicitly separates OS-level connection (e.g. system A2DP) from application transport
 * session and vendor control protocol availability.
 */
data class DesktopConnectionSessionState(
    /** Platform-specific identifier of the remote target. */
    val platformIdentifier: String,

    /** Whether the OS reports an established link to the device. */
    val isConnectedAtOsLevel: Boolean,

    /** The active transport kind if OmniBuds opened a transport session, or null. */
    val activeTransportKind: TransportKind? = null,

    /** Whether OmniBuds holds an active transport session handle. */
    val hasActiveTransportSession: Boolean = false,

    /** Whether a verified vendor protocol session is established (never guessed). */
    val hasVendorProtocolSession: Boolean = false,

    /** Session creation epoch timestamp. */
    val sessionStartedEpochMillis: Long? = null,
) {
    /** True if ready for vendor-level control commands. */
    val isVendorControllable: Boolean
        get() = hasActiveTransportSession && hasVendorProtocolSession
}
