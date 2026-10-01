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
import kotlin.test.fail

/**
 * The boundary of what Phase 3 is allowed to reach, expressed as a check.
 *
 * Modelled on [PhaseTwoScopeTest], which it complements and never edits: Phase 2's file asserts what
 * Phase 2 was authorised to do, and `architecture-audit.md` section 3 recorded that Phase 3 needed its own
 * rather than a widening of that one. This is the deliverable that was asked for and never written.
 *
 * **This is test metadata, not runtime gating (ADR-P2-014).** `BluetoothOperation.authorizedInPhase` is
 * data, and nothing in a shipped binary consults it, because doing so would need an ambient "which phase
 * are we in" value that `docs/phases/phase-0/architecture-governance.md` forbids as hidden global state
 * (audit finding R-10). What enforces the boundary is this file: the authorised set is named, anything
 * outside it is shown to be unreachable, and the permission posture the phase claims is read from the
 * frozen resolver rather than asserted from memory. A shipped binary still does not know which phase
 * produced it, and this test is the honest substitute for pretending that it does.
 *
 * Why the scan row is worth watching. `DEVICE_DISCOVERY_SCAN` carried `authorizedInPhase = 3` from Phase
 * 2's pre-computation until the contradiction was corrected: ADR-P3-012 refuses to declare the scan
 * permission because this phase does not scan, prompt section 13 forbids collecting unrelated
 * nearby-device information, and prompt section 10.C forbids retaining what a scan would find. The tag now
 * sits outside Phase 3, and [noScanTransportOrSessionOperationIsAuthorisedAtPhaseThree] is the check that
 * keeps it there - a scope test is the only place a metadata correction like that can be made durable.
 */
class PhaseThreeScopeTest {

    /** Phase 2's five host-side inspections, authorised here by inheritance rather than by argument. */
    private val phaseTwoInspections = setOf(
        BluetoothOperation.ADAPTER_AVAILABILITY_INSPECTION,
        BluetoothOperation.ADAPTER_STATE_INSPECTION,
        BluetoothOperation.ADAPTER_STATE_OBSERVATION,
        BluetoothOperation.PLATFORM_CAPABILITY_INSPECTION,
        BluetoothOperation.PERMISSION_STATUS_INSPECTION,
    )

    /** The three device-facing reads Phase 3 was authorised to perform, and the only ones it performs. */
    private val phaseThreeAdditions = setOf(
        BluetoothOperation.CONNECTED_DEVICE_INSPECTION,
        BluetoothOperation.BONDED_DEVICE_LIST_INSPECTION,
        BluetoothOperation.PROFILE_CONNECTION_STATE_INSPECTION,
    )

    @Test
    fun theAuthorisedSetIsExactlyTheFiveInspectionsPlusThreeDeviceReads() {
        val derived = BluetoothOperation.entries.filter { it.isAuthorizedIn(PHASE_THREE) }.toSet()

        assertEquals(phaseTwoInspections + phaseThreeAdditions, derived)
    }

    @Test
    fun noScanTransportOrSessionOperationIsAuthorisedAtPhaseThree() {
        // Every one of these is modelled - the permission matrix pre-computes them so a later phase does
        // not re-guess them from memory - and none of them is authorised here. Prompt section 16's list and
        // section 17's audio-path isolation are the reason; this is the shape that makes them checkable.
        val refused = listOf(
            BluetoothOperation.DEVICE_DISCOVERY_SCAN,
            BluetoothOperation.TRANSPORT_GATT_OPEN,
            BluetoothOperation.TRANSPORT_RFCOMM_OPEN,
            BluetoothOperation.LE_AUDIO_SESSION_INSPECTION,
        )
        refused.forEach { operation ->
            assertFalse(operation.isAuthorizedIn(PHASE_THREE), "$operation must not be authorised at 3")
        }

        // Nothing that scans, opens or sessions is reachable at all, however it is named.
        val byPrefix = BluetoothOperation.entries.filter { it.isAuthorizedIn(PHASE_THREE) }
            .filter { it.technicalName.startsWith("transport.") || it.technicalName.startsWith("leaudio.") }
        assertEquals(emptyList(), byPrefix, "a transport or LE Audio session operation is authorised at 3")

        // And no authorised operation asks for the scan permission, which is the manifest-level form of the
        // same refusal (ADR-P3-012: exactly one earned entry, BLUETOOTH_CONNECT).
        val modern = PermissionContext(targetSdk = MODERN_TARGET_SDK, deviceSdk = MODERN_DEVICE_SDK)
        val requests = BluetoothOperation.entries
            .filter { it.isAuthorizedIn(PHASE_THREE) }
            .flatMap { operation -> with(plan(operation, modern)) { runtimePermissions + installTimePermissions } }
            .toSet()
        assertFalse(
            BluetoothPermission.BLUETOOTH_SCAN in requests,
            "a phase that may not scan may not plan a scan permission either",
        )
        assertFalse(BluetoothPermission.BLUETOOTH_ADMIN in requests)
        assertFalse(BluetoothPermission.ACCESS_FINE_LOCATION in requests)
        assertFalse(BluetoothPermission.ACCESS_COARSE_LOCATION in requests)
        assertEquals(
            setOf(BluetoothPermission.BLUETOOTH_CONNECT),
            requests,
            "the whole permission surface Phase 3 stands on is one runtime permission, named",
        )
    }

    @Test
    fun theProfileReadCarriesItsEnforcementCaveatAndTheOtherTwoCarryNone() {
        // Caveats are device-level facts that change behaviour without changing the requirement list, and
        // Phase 2 kept them out of the requirements precisely so one could never be read as a permission the
        // app may ask for. The split is still worth pinning for the rows Phase 3 uses: the profile-state
        // enforcement note belongs to that operation alone.
        val context = PermissionContext(targetSdk = MODERN_TARGET_SDK, deviceSdk = MODERN_DEVICE_SDK)

        assertNotNull(plan(BluetoothOperation.PROFILE_CONNECTION_STATE_INSPECTION, context).caveat)
        assertNull(plan(BluetoothOperation.CONNECTED_DEVICE_INSPECTION, context).caveat)
        assertNull(plan(BluetoothOperation.BONDED_DEVICE_LIST_INSPECTION, context).caveat)
    }

    @Test
    fun theThreeNewReadsWereNotAuthorisedAtPhaseTwoSoThePhaseTwoGuardStillMeansSomething() {
        // PhaseTwoScopeTest is untouched, and this is the half of the argument that keeps it honest: the
        // operations Phase 3 adds were outside Phase 2's set when Phase 2 wrote its check, and stay outside
        // it. A widening here would mean Phase 2's file had been edited to accommodate it.
        phaseThreeAdditions.forEach { operation ->
            assertFalse(operation.isAuthorizedIn(PHASE_TWO), "$operation must stay unauthorised at 2")
            assertTrue(operation.isAuthorizedIn(PHASE_THREE), "and authorised at 3")
            assertContains(BluetoothOperation.entries, operation)
        }
    }

    @Test
    fun everyNewlyAuthorisedOperationNeedsBluetoothConnectOnAModernTargetAndPromptsForIt() {
        // Prompt section 13's "respect the Phase 2 permission resolver" read as data: the three device reads
        // resolve to the runtime permission the platform's own member docs name, and to nothing else. The
        // runtime character matters - a plan whose only requirement is install-time produces no prompt, and
        // a background observer that expects a prompt it will never get is a silent failure.
        val context = PermissionContext(targetSdk = MODERN_TARGET_SDK, deviceSdk = MODERN_DEVICE_SDK)

        phaseThreeAdditions.forEach { operation ->
            val plan = plan(operation, context)

            assertEquals(listOf(BluetoothPermission.BLUETOOTH_CONNECT), plan.runtimePermissions, operation.name)
            assertEquals(emptyList(), plan.installTimePermissions, operation.name)
            assertEquals(1, plan.requirements.size, "one operation, one stated reason, no over-declaration")
            assertFalse(plan.needsNoPermission, operation.name)
            assertFalse(plan.indeterminate, operation.name)
            assertTrue(BluetoothPermission.BLUETOOTH_CONNECT.isRuntimePermission, operation.name)
            assertTrue(
                plan.requirements.all { requirement -> requirement.reason.isNotBlank() },
                "a permission this phase may need must be explainable to the user (SEC-PERM-004)",
            )
        }
    }

    @Test
    fun theLegacyBandAnswersInstallTimeBluetoothForTheSameReadsWithNoPrompt() {
        // The pre-computed rows Phase 2 wrote and Phase 3 consumes rather than re-derives. Below target 31
        // the same three operations need the install-time permission, so there is no runtime grant to
        // refuse and nothing to prompt for - and the reason string carries the guide-versus-method asymmetry
        // the resolver recorded as research Note A rather than hiding it.
        val context = PermissionContext(targetSdk = LEGACY_TARGET_SDK, deviceSdk = LEGACY_TARGET_SDK)

        phaseThreeAdditions.forEach { operation ->
            val plan = plan(operation, context)

            assertEquals(listOf(BluetoothPermission.BLUETOOTH), plan.installTimePermissions, operation.name)
            assertEquals(emptyList(), plan.runtimePermissions, "a legacy target produces no runtime prompt")
            assertFalse(plan.indeterminate, operation.name)
            assertTrue(plan.requirements.single().reason.contains("guide"), operation.name)
        }
    }

    @Test
    fun anUndeterminedTargetSdkRefusesRatherThanChoosingABand() {
        // The same answer Phase 2 pinned, demanded of the rows Phase 3 actually uses: guessing the band would
        // turn an unknown target into either a silent NOT_REQUIRED or a prompt the app cannot justify. The
        // Android source reaches this Failure through the provider as a refusal, never as an empty list.
        val indeterminate = PermissionContext(targetSdk = null, deviceSdk = null)

        (phaseTwoInspections + phaseThreeAdditions).forEach { operation ->
            val outcome = FrozenPermissionRequirementResolver().planFor(operation, indeterminate)

            val failure = assertIs<OperationOutcome.Failure>(outcome, operation.name)
            assertEquals(OmniBudsErrorCategory.INVALID_STATE, failure.error.category, operation.name)
        }
    }

    @Test
    fun thePhaseThatFollowsIsNotAuthorisedToReachAnythingThisOneInvented() {
        // A guard on the guard: the authorised-at-3 set is exactly the operations whose permission rows exist
        // *and* whose names the observation seam can speak. If a later phase adds a BluetoothOperation with a
        // tag of 3 it has widened Phase 3 after the fact, and this fails rather than the prose noticing.
        val authorisedAtThree = BluetoothOperation.entries.filter { it.isAuthorizedIn(PHASE_THREE) }.toSet()

        assertEquals(8, authorisedAtThree.size, "Phase 3's authorised set is eight named reads and no more")
        assertEquals(
            setOf(
                "adapter.availability-inspection",
                "adapter.state-inspection",
                "adapter.state-observation",
                "platform.capability-inspection",
                "platform.permission-status-inspection",
                "device.connected-inspection",
                "device.bonded-list-inspection",
                "profile.connection-state-inspection",
            ),
            authorisedAtThree.map { it.technicalName }.toSet(),
        )
    }

    private fun plan(operation: BluetoothOperation, context: PermissionContext): PermissionPlan =
        when (val outcome = FrozenPermissionRequirementResolver().planFor(operation, context)) {
            is OperationOutcome.Success -> outcome.value
            is OperationOutcome.Failure -> fail("the resolver refused a plan for $operation: ${outcome.error}")
            OperationOutcome.Cancelled -> fail("the resolver cancelled a plan for $operation")
        }

    private companion object {
        const val PHASE_TWO = 2
        const val PHASE_THREE = 3
        const val MODERN_TARGET_SDK = 31
        const val MODERN_DEVICE_SDK = 34
        const val LEGACY_TARGET_SDK = 30
    }
}
