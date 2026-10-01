package com.omnibuds.android.bluetooth

import com.omnibuds.android.bluetooth.adapter.AndroidAdapterStateSource
import com.omnibuds.android.bluetooth.adapter.BluetoothAdapterHandle
import com.omnibuds.android.bluetooth.capability.AndroidPlatformCapabilityProvider
import com.omnibuds.android.bluetooth.permission.AndroidPermissionStateProvider
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.platform.AdapterStateObserver
import com.omnibuds.core.platform.AdapterStateSource
import com.omnibuds.core.platform.AdapterStateObservation
import com.omnibuds.core.platform.BluetoothPermission
import com.omnibuds.core.platform.BluetoothPlatform
import com.omnibuds.core.platform.BluetoothPlatformCapabilities
import com.omnibuds.core.platform.PermissionState
import com.omnibuds.core.platform.TimeProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * The Android answer to the four questions Phase 2 authorises.
 *
 * It is a composition of the pieces rather than a place where logic lives: adapter facts come through
 * [BluetoothAdapterHandle], capability facts through [AndroidPlatformCapabilityProvider], permission
 * standing through [AndroidPermissionStateProvider], and the single-slot, duplicate-suppressing,
 * cleanup-safe behaviour stays in `:core`'s [AdapterStateObserver] so it is the same machine the core
 * tests exercise. Wiring it out here would duplicate that behaviour in the one place no unit test can
 * reach.
 *
 * Each entry point moves its platform calls onto [dispatcher]. `getState()`, `hasSystemFeature()` and
 * `checkSelfPermission()` are all binder or package-manager work, and Phase 2 prompt section 5.8 makes
 * main-thread blocking a defect rather than a performance note.
 */
class AndroidBluetoothPlatform(
    private val handle: BluetoothAdapterHandle,
    private val capabilityProvider: AndroidPlatformCapabilityProvider,
    private val permissionProvider: AndroidPermissionStateProvider,
    private val time: TimeProvider = SystemTimeProvider,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BluetoothPlatform {

    private val source: AdapterStateSource = AndroidAdapterStateSource(handle, dispatcher)
    private val observer = AdapterStateObserver(source, time)

    override suspend fun readAdapterState(): OperationOutcome<AdapterStateObservation> = observer.readOnce()

    override fun observeAdapterState(): Flow<OperationOutcome<AdapterStateObservation>> = observer.observe()

    override suspend fun capabilities(): OperationOutcome<BluetoothPlatformCapabilities> =
        withContext(dispatcher) { capabilityProvider.capabilities() }

    /**
     * The standing of [permission] as the platform reports it.
     *
     * Success carries the state, including [PermissionState.UNKNOWN]: the call answered, and what it
     * answered was that it could not say. Those are different facts and conflating them would hide the
     * read failure (ADR-P0-016).
     *
     * This is the operation-free question - `BluetoothPlatform` deliberately carries no operation
     * parameter, because Phase 2 has no operation to name. Whether a permission is *required* is a
     * per-operation judgement from [com.omnibuds.core.platform.PermissionRequirementResolver], and
     * [AndroidPermissionStateProvider.stateFor] is the method that combines the two.
     */
    override suspend fun permissionState(permission: BluetoothPermission): OperationOutcome<PermissionState> =
        withContext(dispatcher) { OperationOutcome.Success(permissionProvider.standingOf(permission)) }
}
