package com.omnibuds.android.presentation.devices

import kotlinx.coroutines.flow.Flow

/**
 * Handle to an active device discovery session on Android.
 */
interface AndroidDiscoverySession {
    /** Stream of discovered devices updated as new observations arrive. */
    val results: Flow<List<AndroidDiscoveredDevice>>

    /** Gracefully stop the discovery session. */
    suspend fun stop()

    /** Cancel discovery immediately. */
    suspend fun cancel()
}

/**
 * Android discovery provider interface.
 */
interface AndroidDiscoveryProvider {
    suspend fun startDiscovery(): AndroidDiscoverySession
}
