package com.omnibuds.core.platform

import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.common.TransportKind

/**
 * Physical connection session handle created by a platform transport implementation.
 */
interface PlatformConnectionSession : PlatformRegistration {
    val transportKind: TransportKind
    val isConnected: Boolean
}

/**
 * Seam for creating platform-specific transport sessions without referencing Android GATT or
 * desktop socket APIs in core logic.
 */
interface PlatformTransportFactory {
    /** Supported transport kinds on this platform. */
    val supportedTransports: Set<TransportKind>

    /**
     * Attempts to create a connection session for [deviceAddress] using [kind].
     */
    suspend fun openSession(
        deviceAddress: String,
        kind: TransportKind,
    ): OperationOutcome<PlatformConnectionSession>
}
