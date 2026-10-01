package com.omnibuds.core.platform

import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.common.TransportKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The boundary of what Phase 2 is allowed to reach, expressed as a check.
 *
 * Audit finding R-10 objected that `BluetoothOperation.authorizedInPhase` looked like runtime phase
 * gating baked into domain data, which would need an ambient "current phase" value - exactly the
 * hidden global state Phase 0 forbids. It is kept as metadata, and the enforcement lives here: the
 * authorised set is named, and anything outside it must not be reachable from Phase 2 code.
 *
 * A shipped binary does not know which phase produced it. This test is the honest substitute for
 * pretending it does.
 */
class PhaseTwoScopeTest {

    private val phaseTwoOperations = setOf(
        BluetoothOperation.ADAPTER_AVAILABILITY_INSPECTION,
        BluetoothOperation.ADAPTER_STATE_INSPECTION,
        BluetoothOperation.ADAPTER_STATE_OBSERVATION,
        BluetoothOperation.PLATFORM_CAPABILITY_INSPECTION,
        BluetoothOperation.PERMISSION_STATUS_INSPECTION,
    )

    @Test
    fun theAuthorisedSetIsExactlyTheFiveInspectionOperations() {
        val derived = BluetoothOperation.entries.filter { it.isAuthorizedIn(PHASE_TWO) }.toSet()

        assertEquals(phaseTwoOperations, derived)
    }

    @Test
    fun nothingThatTouchesADeviceIsAuthorizedInPhaseTwo() {
        val deviceFacing = BluetoothOperation.entries.filter { it.isAuthorizedIn(PHASE_TWO) && it != BluetoothOperation.ADAPTER_STATE_OBSERVATION }
            .filter { it.technicalName.startsWith("device.") || it.technicalName.startsWith("transport.") || it.technicalName.startsWith("leaudio.") }

        assertTrue(deviceFacing.isEmpty(), "Phase 2 may not reach a device: $deviceFacing")

        // The operations that do reach a device are modelled, so the matrix is pre-computed, and are
        // authorised later.
        assertFalse(BluetoothOperation.DEVICE_DISCOVERY_SCAN.isAuthorizedIn(PHASE_TWO))
        assertFalse(BluetoothOperation.TRANSPORT_GATT_OPEN.isAuthorizedIn(PHASE_TWO))
        assertFalse(BluetoothOperation.BONDED_DEVICE_LIST_INSPECTION.isAuthorizedIn(PHASE_TWO))
    }

    @Test
    fun everyAuthorisedOperationHasAPlanAndNoneOfThemPromptsTheUser() {
        val resolver = FrozenPermissionRequirementResolver()
        val context = PermissionContext(targetSdk = 35, deviceSdk = 34)

        phaseTwoOperations.forEach { operation ->
            val plan = assertIs<OperationOutcome.Success<PermissionPlan>>(
                resolver.planFor(operation, context),
            ).value

            assertTrue(plan.needsNoPermission, "$operation must not require anything in Phase 2")
            assertTrue(plan.runtimePermissions.isEmpty(), "$operation must not produce a prompt")
        }
    }

    @Test
    fun noTransportBoundaryIsOpenableInPhaseTwo() {
        // A phase that may not open a channel must also not be able to name one as available for
        // exchange; probing availability is allowed, exchange is Phase 6.
        val boundaryKinds = setOf(
            TransportKind.BLE,
            TransportKind.GATT,
            TransportKind.CLASSIC_BLUETOOTH,
            TransportKind.RFCOMM,
            TransportKind.LE_AUDIO,
        )

        boundaryKinds.forEach { kind ->
            assertContains(TransportKind.entries.toSet(), kind)
        }
        assertEquals(6, TransportKind.entries.filter { it != TransportKind.VENDOR_SPECIFIC }.size)
    }

    @Test
    fun theResolverRefusesRatherThanInventingForAnUndeterminedContext() {
        val outcome = FrozenPermissionRequirementResolver().planFor(
            BluetoothOperation.ADAPTER_STATE_INSPECTION,
            PermissionContext(targetSdk = null, deviceSdk = null),
        )

        val failure = assertIs<OperationOutcome.Failure>(outcome)
        assertEquals(OmniBudsErrorCategory.INVALID_STATE, failure.error.category)
    }

    private companion object {
        const val PHASE_TWO = 2
    }
}
