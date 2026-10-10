package com.omnibuds.core.platform.desktop

import com.omnibuds.core.common.OperationOutcome
import kotlinx.coroutines.flow.Flow

/**
 * Portable abstraction for desktop Bluetooth discovery.
 *
 * Implements deterministic session control, cancellation, observation streaming,
 * and deterministic resource cleanup.
 */
interface DesktopDeviceDiscoverySession {
    /** True while discovery is active; false once stopped or cancelled. */
    val isActive: Boolean

    /** Stream of device observations from this discovery session. */
    val observations: Flow<DesktopDiscoveredDevice>

    /** Stops discovery and releases underlying OS scan/discovery resources cleanly. */
    suspend fun stop(): OperationOutcome<Unit>
}

/**
 * Entry point for initiating Bluetooth discovery sessions on desktop platforms.
 */
interface DesktopDiscoveryProvider {
    /**
     * Initiates a new discovery session.
     *
     * Fails with a structured error if discovery is already in progress,
     * if the adapter is unavailable or disabled, or if permissions are missing.
     */
    suspend fun startDiscovery(): OperationOutcome<DesktopDeviceDiscoverySession>

    /**
     * Checks if discovery is currently active.
     */
    val isDiscovering: Boolean
}
