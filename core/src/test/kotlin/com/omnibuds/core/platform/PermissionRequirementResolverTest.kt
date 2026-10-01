package com.omnibuds.core.platform

import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The permission matrix, asserted rather than remembered.
 *
 * These cases exist because the verified platform research contradicted what this project had
 * previously written down: requirements key off the app's `targetSdkVersion`, and adapter
 * inspection needs no permission at all for a 31+ target. A correction that only lives in a document
 * will be re-forgotten; a failing test will not.
 */
class PermissionRequirementResolverTest {

    private val resolver = FrozenPermissionRequirementResolver()

    private val modern = PermissionContext(targetSdk = 35, deviceSdk = 34)
    private val legacy = PermissionContext(targetSdk = 30, deviceSdk = 30)

    private fun plan(operation: BluetoothOperation, context: PermissionContext): PermissionPlan =
        assertIs<OperationOutcome.Success<PermissionPlan>>(resolver.planFor(operation, context)).value

    @Test
    fun phaseTwoOperationsRequireNoPermissionForAModernTarget() {
        listOf(
            BluetoothOperation.ADAPTER_AVAILABILITY_INSPECTION,
            BluetoothOperation.ADAPTER_STATE_INSPECTION,
            BluetoothOperation.ADAPTER_STATE_OBSERVATION,
            BluetoothOperation.PLATFORM_CAPABILITY_INSPECTION,
            BluetoothOperation.PERMISSION_STATUS_INSPECTION,
        ).forEach { operation ->
            val result = plan(operation, modern)
            assertTrue(result.needsNoPermission, "$operation should require nothing, asked for ${result.requirements}")
            assertTrue(result.runtimePermissions.isEmpty(), "$operation must not produce a runtime prompt")
        }
    }

    @Test
    fun theLegacyBandStillRequiresTheInstallTimePermissionForAdapterReads() {
        val state = plan(BluetoothOperation.ADAPTER_STATE_INSPECTION, legacy)

        assertEquals(listOf(BluetoothPermission.BLUETOOTH), state.installTimePermissions)
        assertTrue(state.runtimePermissions.isEmpty(), "an install-time permission is never a prompt")
        assertEquals(
            plan(BluetoothOperation.ADAPTER_AVAILABILITY_INSPECTION, legacy).needsNoPermission,
            true,
            "obtaining the adapter object itself is documented as permission-free on both bands",
        )
    }

    @Test
    fun anUnknownTargetSdkProducesNoPlanRatherThanAGuessedBand() {
        val outcome = resolver.planFor(
            BluetoothOperation.DEVICE_DISCOVERY_SCAN,
            PermissionContext(targetSdk = null, deviceSdk = 34),
        )

        val failure = assertIs<OperationOutcome.Failure>(outcome)
        assertEquals(OmniBudsErrorCategory.INVALID_STATE, failure.error.category)
    }

    @Test
    fun scanningNeedsLocationUntilNeverForLocationIsAsserted() {
        val withoutAssertion = plan(BluetoothOperation.DEVICE_DISCOVERY_SCAN, modern)
        val withAssertion = plan(
            BluetoothOperation.DEVICE_DISCOVERY_SCAN,
            PermissionContext(targetSdk = 35, deviceSdk = 34, assertsNeverForLocation = true),
        )

        assertContains(withoutAssertion.runtimePermissions, BluetoothPermission.ACCESS_FINE_LOCATION)
        assertFalse(BluetoothPermission.ACCESS_FINE_LOCATION in withAssertion.runtimePermissions)
        assertEquals(listOf(BluetoothPermission.BLUETOOTH_SCAN), withAssertion.runtimePermissions)
        assertNotNull(withoutAssertion.caveat, "the cost of not asserting the flag must be stated")
    }

    @Test
    fun theLegacyScanBandCarriesAllFourPermissionsAndSaysWhyPerItem() {
        val scan = plan(BluetoothOperation.DEVICE_DISCOVERY_SCAN, legacy)

        assertEquals(
            setOf(
                BluetoothPermission.BLUETOOTH,
                BluetoothPermission.BLUETOOTH_ADMIN,
                BluetoothPermission.ACCESS_COARSE_LOCATION,
                BluetoothPermission.ACCESS_FINE_LOCATION,
            ),
            scan.requirements.map { it.permission }.toSet(),
        )
        scan.requirements.forEach { requirement ->
            assertTrue(requirement.reason.isNotBlank(), "${requirement.permission} arrived without a reason")
            assertTrue(requirement.appliesAt(30), "${requirement.permission} band must cover the legacy range")
            assertFalse(requirement.appliesAt(31), "${requirement.permission} band must not spill into the modern range")
        }
    }

    @Test
    fun connectClassOperationsFollowTheGuideWhereTheMethodDocsAreSilent() {
        listOf(
            BluetoothOperation.TRANSPORT_GATT_OPEN,
            BluetoothOperation.TRANSPORT_RFCOMM_OPEN,
        ).forEach { operation ->
            val legacyPlan = plan(operation, legacy)

            assertEquals(listOf(BluetoothPermission.BLUETOOTH), legacyPlan.installTimePermissions)
            // The asymmetry between the compatibility guide and the silent per-method pages has to
            // travel with the requirement, or a later reader "corrects" it back to the docs gap.
            assertContains(legacyPlan.requirements.first().reason, "guide")
            assertEquals(
                listOf(BluetoothPermission.BLUETOOTH_CONNECT),
                plan(operation, modern).runtimePermissions,
            )
        }
    }

    @Test
    fun leAudioOnALegacyTargetIsAnApiLimitNotAPermissionQuestion() {
        val legacyPlan = plan(BluetoothOperation.LE_AUDIO_SESSION_INSPECTION, legacy)

        assertTrue(legacyPlan.requirements.isEmpty())
        assertNotNull(legacyPlan.caveat)
        assertContains(legacyPlan.caveat.orEmpty(), "does not exist below API 31")
        assertNull(plan(BluetoothOperation.ADAPTER_STATE_INSPECTION, modern).caveat)
    }

    @Test
    fun profileStateCarriesTheDeviceLevelEnforcementCaveat() {
        val onModernDevice = plan(
            BluetoothOperation.PROFILE_CONNECTION_STATE_INSPECTION,
            PermissionContext(targetSdk = 35, deviceSdk = 34),
        )
        val onOlderDevice = plan(
            BluetoothOperation.PROFILE_CONNECTION_STATE_INSPECTION,
            PermissionContext(targetSdk = 35, deviceSdk = 30),
        )

        assertNotNull(onModernDevice.caveat)
        assertNull(onOlderDevice.caveat)
    }

    @Test
    fun noPhaseTwoOperationEverAsksForLocation() {
        // Location for a Phase 2 app would be an unjustified request; the privacy rule is
        // SEC-PERM-004, and this test is what keeps a future matrix edit from leaking it backwards.
        listOf(
            BluetoothOperation.ADAPTER_AVAILABILITY_INSPECTION,
            BluetoothOperation.ADAPTER_STATE_INSPECTION,
            BluetoothOperation.ADAPTER_STATE_OBSERVATION,
            BluetoothOperation.PLATFORM_CAPABILITY_INSPECTION,
            BluetoothOperation.PERMISSION_STATUS_INSPECTION,
        ).forEach { operation ->
            listOf(modern, legacy).forEach { context ->
                val permissions = plan(operation, context).requirements.map { it.permission }
                assertFalse(BluetoothPermission.ACCESS_FINE_LOCATION in permissions, "$operation on $context")
                assertFalse(BluetoothPermission.ACCESS_COARSE_LOCATION in permissions, "$operation on $context")
            }
        }
    }

    @Test
    fun everyRequirementNamesTheOperationItBelongsTo() {
        val scan = plan(BluetoothOperation.DEVICE_DISCOVERY_SCAN, legacy)

        scan.requirements.forEach { requirement ->
            assertEquals(BluetoothOperation.DEVICE_DISCOVERY_SCAN, requirement.operation)
        }
    }
}
