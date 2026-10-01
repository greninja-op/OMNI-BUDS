package com.omnibuds.core.platform

import com.omnibuds.core.common.OperationOutcome
import kotlinx.coroutines.flow.Flow

/**
 * The seam through which the app asks the phone about its own Bluetooth, never about a headset.
 *
 * Phase 2 authorises exactly four questions: is there an adapter, what state is it in, what can
 * this platform expose, and how do my permissions stand (Phase 2 prompt section 5). Anything beyond
 * that - discovering devices, opening a transport, reading battery or codec state - belongs to
 * later phases, so it is absent from this interface rather than present and throwing. An interface
 * with `TODO()` methods would advertise a capability that does not exist.
 *
 * Implementations live in a platform module. Nothing in `:core` may implement this, and no
 * implementation may fabricate a positive answer: an unavailable adapter is reported as
 * [BluetoothAdapterState.UNAVAILABLE] or [BluetoothAdapterState.UNKNOWN], never as enabled
 * (ADR-P0-001, ADR-P0-016).
 */
interface BluetoothPlatform {
    /** The adapter state as of now, without holding a registration open. */
    suspend fun readAdapterState(): OperationOutcome<AdapterStateObservation>

    /**
     * A live stream of adapter-state observations.
     *
     * Cold: collecting registers with the platform, and losing the collector must unregister.
     * At most one concurrent observation may be held; a second attempt is refused with a structured
     * failure rather than silently double-registering (Phase 2 prompt sections 5.4, 5.8).
     */
    fun observeAdapterState(): Flow<OperationOutcome<AdapterStateObservation>>

    /** What this phone and Android version can expose. */
    suspend fun capabilities(): OperationOutcome<BluetoothPlatformCapabilities>

    /** The standing of [permission] for the app as it runs now. */
    suspend fun permissionState(permission: BluetoothPermission): OperationOutcome<PermissionState>
}
