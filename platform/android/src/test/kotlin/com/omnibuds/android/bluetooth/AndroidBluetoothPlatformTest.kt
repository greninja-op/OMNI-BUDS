package com.omnibuds.android.bluetooth

import android.bluetooth.BluetoothAdapter
import com.omnibuds.android.bluetooth.adapter.BluetoothAdapterHandle
import com.omnibuds.android.bluetooth.capability.AndroidPlatformCapabilityProvider
import com.omnibuds.android.bluetooth.capability.ApiLevelProvider
import com.omnibuds.android.bluetooth.capability.PlatformFeatureProbe
import com.omnibuds.android.bluetooth.capability.TargetSdkProvider
import com.omnibuds.android.bluetooth.permission.AndroidPermissionStateProvider
import com.omnibuds.android.bluetooth.permission.PermissionRequestLedger
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.platform.AdapterStateObservation
import com.omnibuds.core.platform.ApiAvailability
import com.omnibuds.core.platform.BluetoothAdapterState
import com.omnibuds.core.platform.BluetoothPermission
import com.omnibuds.core.platform.BluetoothPlatformCapabilities
import com.omnibuds.core.platform.ObservationKind
import com.omnibuds.core.platform.PermissionState
import com.omnibuds.core.platform.PlatformFeature
import com.omnibuds.core.platform.PlatformRegistration
import com.omnibuds.core.platform.TimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The composed platform, checked as a composition.
 *
 * Each piece has its own suite, so the questions here are about the wiring: does the adapter answer
 * come through the shared core observer rather than a second mechanism invented in the platform layer,
 * is the clock injected instead of read inline, does the single-slot rule survive being reached through
 * this class, and does a capability question ever prompt anyone (Phase 2 prompt sections 5.4, 5.8;
 * ADR-P2-007, ADR-P2-011).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidBluetoothPlatformTest {

    @Test
    fun aSingleReadCarriesThePlatformAnswerAndTheInjectedTime() = runTest {
        val platform = platform(
            handle = ScriptedHandle(raw = BluetoothAdapter.STATE_OFF),
            time = TimeProvider { EPOCH },
        )

        val outcome = platform.readAdapterState()

        assertIs<OperationOutcome.Success<AdapterStateObservation>>(outcome)
        assertEquals(BluetoothAdapterState.DISABLED, outcome.value.state)
        assertEquals(ObservationKind.PLATFORM_READ, outcome.value.kind)
        assertEquals(EPOCH, outcome.value.observedAtEpochMillis)
    }

    @Test
    fun anObservationReportsTheCurrentStateBeforeItReportsChanges() = runTest {
        val handle = ScriptedHandle(raw = BluetoothAdapter.STATE_ON)
        val platform = platform(handle = handle)
        val observed = mutableListOf<BluetoothAdapterState>()

        val collector = launch { platform.observeAdapterState().states().toList(observed) }
        advanceUntilIdle()
        handle.emit?.invoke(BluetoothAdapter.STATE_OFF)
        advanceUntilIdle()
        collector.cancelAndJoin()

        assertEquals(listOf(BluetoothAdapterState.ENABLED, BluetoothAdapterState.DISABLED), observed)
    }

    @Test
    fun aSecondConcurrentObservationIsRefusedThroughThePlatformWiring() = runTest {
        val platform = platform(handle = ScriptedHandle())
        val holder = launch { platform.observeAdapterState().states().toList() }
        advanceUntilIdle()

        val refusal = platform.observeAdapterState().first()

        assertIs<OperationOutcome.Failure>(refusal)
        assertEquals(OmniBudsErrorCategory.RESOURCE_UNAVAILABLE, refusal.error.category)
        holder.cancelAndJoin()
    }

    @Test
    fun permissionStandingComesFromTheProviderWithoutPromptingAnyone() = runTest {
        val platform = platform(
            handle = ScriptedHandle(),
            granted = setOf(BluetoothPermission.BLUETOOTH_CONNECT.manifestName),
        )

        assertEquals(
            OperationOutcome.Success(PermissionState.GRANTED),
            platform.permissionState(BluetoothPermission.BLUETOOTH_CONNECT),
        )
        assertEquals(
            OperationOutcome.Success(PermissionState.NOT_REQUESTED),
            platform.permissionState(BluetoothPermission.BLUETOOTH_SCAN),
        )
    }

    @Test
    fun capabilitiesAreDelegatedAndCarryNoClaimThePhoneDidNotMake() = runTest {
        val platform = platform(handle = ScriptedHandle())

        val outcome = platform.capabilities()

        assertIs<OperationOutcome.Success<BluetoothPlatformCapabilities>>(outcome)
        val report = outcome.value
        assertEquals(API_LEVEL, report.apiLevel)
        assertEquals(ApiAvailability.AVAILABLE, report.adapterPresent)
        assertEquals(ApiAvailability.AVAILABLE, report.supportFor(PlatformFeature.CLASSIC_BLUETOOTH).apiAvailability)
        assertEquals(
            ApiAvailability.UNKNOWN,
            report.supportFor(PlatformFeature.A2DP_CONNECTION_STATE).apiAvailability,
        )
    }

    private fun platform(
        handle: BluetoothAdapterHandle,
        time: TimeProvider = TimeProvider { null },
        granted: Set<String> = emptySet(),
    ): AndroidBluetoothPlatform {
        val permissionProvider = AndroidPermissionStateProvider(
            reader = { manifestName -> manifestName in granted },
            ledger = PermissionRequestLedger { false },
        )
        return AndroidBluetoothPlatform(
            handle = handle,
            capabilityProvider = AndroidPlatformCapabilityProvider(
                apiLevel = ApiLevelProvider { API_LEVEL },
                featureProbe = PlatformFeatureProbe { name -> name == CLASSIC_FEATURE },
                permissionProvider = permissionProvider,
                targetSdk = TargetSdkProvider { API_LEVEL },
                adapterPresent = { handle.adapterPresent },
            ),
            permissionProvider = permissionProvider,
            time = time,
            dispatcher = Dispatchers.Unconfined,
        )
    }

    /** A handle that answers what it is told and remembers the emission callback. */
    private class ScriptedHandle(
        private val present: Boolean = true,
        private val raw: Int? = BluetoothAdapter.STATE_ON,
    ) : BluetoothAdapterHandle {
        var emit: ((Int) -> Unit)? = null

        override val adapterPresent: Boolean
            get() = present

        override fun readRawState(): Int? = raw

        override fun openStateChanges(emit: (Int) -> Unit): PlatformRegistration {
            this.emit = emit
            return object : PlatformRegistration {
                override val isActive: Boolean
                    get() = true

                override suspend fun dispose() = Unit
            }
        }
    }

    private companion object {
        const val API_LEVEL = 31
        const val EPOCH = 1_700_000_000_000L
        const val CLASSIC_FEATURE = "android.hardware.bluetooth"
    }
}

/** The successful states out of an observation stream, dropping the structured failures. */
private fun Flow<OperationOutcome<AdapterStateObservation>>.states(): Flow<BluetoothAdapterState> =
    mapNotNull { outcome -> (outcome as? OperationOutcome.Success)?.value?.state }
