package com.omnibuds.android.bluetooth.permission

import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.platform.BluetoothOperation
import com.omnibuds.core.platform.BluetoothPermission
import com.omnibuds.core.platform.FrozenPermissionRequirementResolver
import com.omnibuds.core.platform.PermissionContext
import com.omnibuds.core.platform.PermissionRequirementResolver
import com.omnibuds.core.platform.PermissionState

/**
 * Reports permission standing on Android, and only permission standing.
 *
 * Two questions are answerable and they are kept apart because they have different subjects:
 *
 *  - [standingOf] asks what the platform currently holds for the app. It answers from
 *    [PermissionStandingReader] plus the session's own request history, and it can never say
 *    "not required", because requirement is a property of an operation, not of a permission.
 *  - [stateFor] asks whether a specific operation needs this permission on this app's target SDK.
 *    That judgement belongs to [PermissionRequirementResolver] (ADR-P2-009), and only when the plan
 *    actually requires the permission is the platform's standing reported.
 *
 * Collapsing the two is how an app ends up showing a permission prompt for an operation that needed
 * none, which Phase 2 prompt section 5.3 forbids outright.
 *
 * **This class never returns [PermissionState.DENIED_PERMANENTLY]** (ADR-P2-012). Research Q4 found no
 * app-visible signal that establishes it on current Android: `shouldShowRequestPermissionRationale`
 * returns false both before a first request and after a "don't ask again" refusal, so the two are
 * indistinguishable and any inference would be a guess. [PermissionState.REQUIRES_USER_ACTION] is
 * likewise not produced here - it describes an action outside the app, which Phase 2 has no authorised
 * way to detect. Both states stay in the model for the platform signal that may one day justify them.
 * The behaviour is asserted by `AndroidPermissionStateProviderTest`, so a later edit that starts
 * inferring permanent denial fails a build rather than passing review.
 */
class AndroidPermissionStateProvider(
    private val reader: PermissionStandingReader,
    private val ledger: PermissionRequestLedger,
    private val resolver: PermissionRequirementResolver = FrozenPermissionRequirementResolver(),
) {

    /** What the platform holds for [permission] right now, without asking the user anything. */
    fun standingOf(permission: BluetoothPermission): PermissionState =
        when (reader.isGranted(permission)) {
            // No answer is not a refusal. Reporting UNKNOWN keeps "we could not look" separate from
            // "the user said no", which is the distinction a later prompt decision depends on.
            null -> PermissionState.UNKNOWN

            true -> PermissionState.GRANTED

            false -> when {
                // An install-time permission is not grantable by a prompt at all: a denial here means
                // the manifest does not declare it, which is a developer decision (ADR-P2-011 keeps
                // this module's manifest empty), not a user refusal to act on.
                !permission.isRuntimePermission -> PermissionState.DENIED

                // The platform cannot tell never-asked from refused, so the session's own record
                // decides, and the absence of a record is reported honestly as NOT_REQUESTED.
                ledger.wasRequested(permission) -> PermissionState.DENIED

                else -> PermissionState.NOT_REQUESTED
            }
        }

    /** The standing of every permission in the model. Used by the capability report. */
    fun standings(): Map<BluetoothPermission, PermissionState> =
        BluetoothPermission.entries.associateWith { permission -> standingOf(permission) }

    /**
     * Whether [permission] stands between [operation] and this app, on this target SDK.
     *
     * [PermissionState.NOT_REQUIRED] is the plan's answer, not the platform's, and is returned even
     * where the permission itself is held - holding a permission you were never asked for says
     * nothing about the operation.
     */
    fun stateFor(
        operation: BluetoothOperation,
        permission: BluetoothPermission,
        context: PermissionContext,
    ): PermissionState =
        when (val plan = resolver.planFor(operation, context)) {
            is OperationOutcome.Success ->
                if (plan.value.requirements.any { requirement ->
                        requirement.required && requirement.permission == permission
                    }
                ) {
                    standingOf(permission)
                } else {
                    PermissionState.NOT_REQUIRED
                }

            // An undeterminable plan is not a plan that requires nothing: reporting NOT_REQUIRED here
            // would let an unknown target SDK silently become "no permission needed".
            is OperationOutcome.Failure -> PermissionState.UNKNOWN

            OperationOutcome.Cancelled -> PermissionState.UNKNOWN
        }
}
