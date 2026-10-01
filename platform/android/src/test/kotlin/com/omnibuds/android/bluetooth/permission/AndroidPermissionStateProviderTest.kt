package com.omnibuds.android.bluetooth.permission

import com.omnibuds.core.platform.BluetoothOperation
import com.omnibuds.core.platform.BluetoothPermission
import com.omnibuds.core.platform.PermissionContext
import com.omnibuds.core.platform.PermissionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The permission standing rules, tested as a table rather than as examples.
 *
 * Phase 2 prompt section 5.3 is mostly a list of things that must not be claimed, and the only way to
 * hold a line like "never say permanently denied" is to enumerate the whole input space and assert the
 * forbidden outputs appear nowhere in it. That is what [noInputProducesAnUnsupportedClaim] does; the
 * other tests pin the individual rows a caller depends on.
 */
class AndroidPermissionStateProviderTest {

    @Test
    fun aGrantedPermissionIsReportedAsGranted() {
        val provider = provider(granted = setOf(BluetoothPermission.BLUETOOTH_CONNECT.manifestName))

        assertEquals(PermissionState.GRANTED, provider.standingOf(BluetoothPermission.BLUETOOTH_CONNECT))
    }

    @Test
    fun anUnrequestedRuntimePermissionIsNotReportedAsADenial() {
        val provider = provider(granted = emptySet())

        assertEquals(
            PermissionState.NOT_REQUESTED,
            provider.standingOf(BluetoothPermission.BLUETOOTH_SCAN),
            "checkSelfPermission cannot tell never-asked from refused, so the session record decides",
        )
    }

    @Test
    fun aRuntimePermissionRefusedAfterARequestIsReportedAsDenied() {
        val ledger = InMemoryPermissionRequestLedger()
        val provider = AndroidPermissionStateProvider(reader = { false }, ledger = ledger)

        ledger.record(BluetoothPermission.BLUETOOTH_SCAN)

        assertEquals(
            PermissionState.DENIED,
            provider.standingOf(BluetoothPermission.BLUETOOTH_SCAN),
            "a permission this process asked for and did not receive is a refusal, not an un-asked question",
        )
    }

    @Test
    fun anUndeclaredInstallTimePermissionIsDeniedRatherThanUnrequested() {
        val provider = provider(granted = emptySet())

        // A prompt cannot produce this permission, so "the user has not been asked" would describe an
        // action that does not exist. The truthful row is a plain denial: the manifest does not hold it.
        assertEquals(PermissionState.DENIED, provider.standingOf(BluetoothPermission.BLUETOOTH))
        assertEquals(PermissionState.DENIED, provider.standingOf(BluetoothPermission.BLUETOOTH_ADMIN))
    }

    @Test
    fun anUnansweredQuestionIsUnknownRatherThanDenied() {
        val provider = AndroidPermissionStateProvider(reader = { null }, ledger = PermissionRequestLedger { false })

        assertEquals(PermissionState.UNKNOWN, provider.standingOf(BluetoothPermission.BLUETOOTH_CONNECT))
    }

    @Test
    fun noInputProducesAnUnsupportedClaim() {
        val forbidden = setOf(PermissionState.DENIED_PERMANENTLY, PermissionState.REQUIRES_USER_ACTION)

        for (permission in BluetoothPermission.entries) {
            for (granted in listOf(true, false, null)) {
                for (requested in listOf(true, false)) {
                    val provider = AndroidPermissionStateProvider(
                        reader = { granted },
                        ledger = PermissionRequestLedger { requested },
                    )
                    val state = provider.standingOf(permission)
                    assertFalse(state in forbidden, "produced $state for $permission/$granted/$requested")
                }
            }
        }
    }

    @Test
    fun anOperationThatNeedsNoPermissionReportsNotRequiredEvenWhenNothingIsGranted() {
        val provider = provider(granted = emptySet())

        // The authorised Phase 2 inspections need nothing on a target-31-and-above app (ADR-P2-011),
        // and a denial-shaped answer here would be the silent-misreport the resolver exists to stop.
        assertEquals(
            PermissionState.NOT_REQUIRED,
            provider.stateFor(
                operation = BluetoothOperation.ADAPTER_STATE_OBSERVATION,
                permission = BluetoothPermission.BLUETOOTH_CONNECT,
                context = PermissionContext(targetSdk = 35, deviceSdk = 35),
            ),
        )
    }

    @Test
    fun aRequiredPermissionReportsThePlatformsStanding() {
        val provider = provider(granted = emptySet())
        val ledger = InMemoryPermissionRequestLedger()
        val recordingProvider = AndroidPermissionStateProvider(reader = { false }, ledger = ledger)
        ledger.record(BluetoothPermission.BLUETOOTH_SCAN)

        val modernScan = recordingProvider.stateFor(
            operation = BluetoothOperation.DEVICE_DISCOVERY_SCAN,
            permission = BluetoothPermission.BLUETOOTH_SCAN,
            context = PermissionContext(targetSdk = 35, deviceSdk = 35),
        )
        assertEquals(PermissionState.DENIED, modernScan)

        val unasked = provider.stateFor(
            operation = BluetoothOperation.DEVICE_DISCOVERY_SCAN,
            permission = BluetoothPermission.ACCESS_FINE_LOCATION,
            context = PermissionContext(targetSdk = 35, deviceSdk = 35),
        )
        assertEquals(PermissionState.NOT_REQUESTED, unasked, "location is required without neverForLocation")
    }

    @Test
    fun theLegacyBandIsJudgedFromTheTargetSdkNotThePhone() {
        val holdingLegacyBluetooth = provider(granted = setOf(BluetoothPermission.BLUETOOTH.manifestName))
        val holdingNothing = provider(granted = emptySet())

        val legacy = holdingLegacyBluetooth.stateFor(
            operation = BluetoothOperation.ADAPTER_STATE_INSPECTION,
            permission = BluetoothPermission.BLUETOOTH,
            context = PermissionContext(targetSdk = 30, deviceSdk = 35),
        )
        val modern = holdingNothing.stateFor(
            operation = BluetoothOperation.ADAPTER_STATE_INSPECTION,
            permission = BluetoothPermission.BLUETOOTH,
            context = PermissionContext(targetSdk = 35, deviceSdk = 30),
        )

        // Requirements follow targetSdk; the phone's own level does not move them (ADR-P2-009).
        assertEquals(PermissionState.GRANTED, legacy, "the legacy band requires install-time BLUETOOTH")
        assertEquals(
            PermissionState.NOT_REQUIRED,
            modern,
            "a 31+ target needs no permission to read the adapter, even on an older phone",
        )
    }

    @Test
    fun anUnknownTargetSdkIsNotSilentlyTreatedAsRequiringNothing() {
        val provider = provider(granted = emptySet())

        assertEquals(
            PermissionState.UNKNOWN,
            provider.stateFor(
                operation = BluetoothOperation.CONNECTED_DEVICE_INSPECTION,
                permission = BluetoothPermission.BLUETOOTH_CONNECT,
                context = PermissionContext(targetSdk = null, deviceSdk = 34),
            ),
            "an undeterminable plan is not a plan that requires nothing",
        )
    }

    @Test
    fun theStandingReportCoversEveryPermissionInTheModel() {
        val provider = provider(granted = emptySet())

        assertEquals(BluetoothPermission.entries.toSet(), provider.standings().keys)
    }

    private fun provider(granted: Set<String>): AndroidPermissionStateProvider =
        AndroidPermissionStateProvider(
            reader = { manifestName -> manifestName in granted },
            ledger = PermissionRequestLedger { false },
        )
}
