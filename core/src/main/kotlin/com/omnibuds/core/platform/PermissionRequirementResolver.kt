package com.omnibuds.core.platform

import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome

/**
 * The answer to "what does this operation need, on this app's target SDK, on this device".
 *
 * [indeterminate] is a first-class outcome, not a failure: when the target SDK is unknown the
 * resolver says so instead of picking the safer-looking band. Guessing here is worse than a plain
 * failure, because a wrong `NOT_REQUIRED` silently breaks a feature later while a wrong requirement
 * asks the user for a permission the app may never legitimately need.
 */
data class PermissionPlan(
    val operation: BluetoothOperation,
    val requirements: List<PermissionRequirement>,
    val bandLabel: String,
    val indeterminate: Boolean,
    val caveat: String? = null,
) {
    /** Runtime permissions the user must be asked for, in declaration order. */
    val runtimePermissions: List<BluetoothPermission>
        get() = requirements.filter { it.required && it.permission.isRuntimePermission }.map { it.permission }

    /** Install-time permissions, which are never the subject of a runtime request. */
    val installTimePermissions: List<BluetoothPermission>
        get() = requirements.filter { it.required && !it.permission.isRuntimePermission }.map { it.permission }

    val needsNoPermission: Boolean
        get() = !indeterminate && requirements.none { it.required }
}

/** Contract for the centralised resolver Phase 2 prompt section 5.3 asks for. */
interface PermissionRequirementResolver {
    fun planFor(operation: BluetoothOperation, context: PermissionContext): OperationOutcome<PermissionPlan>
}

/**
 * The frozen permission table, transcribed from the verified platform research rather than from
 * recollection.
 *
 * Every reason string below exists so a permission request can be explained to the user in one
 * sentence, which is what `SEC-PERM-004` demands; a requirement with no reason is not constructible
 * ([PermissionRequirement]).
 *
 * **Phase 2's own answer is that it needs nothing.** Adapter availability, adapter state, state
 * observation, capability inspection and permission-status inspection require no permission at all
 * for an app targeting API 31 or above (`bluetooth-api-research.md` section 3 rows 1-4, Q7). The
 * rows for scanning, bonding, profiles and socket opens are pre-computed so those phases do not
 * re-guess them from memory, and each still owes its own re-verification (`SEC-PERM-003`) before it
 * is acted on. Note also the asymmetry recorded as "Note A": for connect-class calls the per-method
 * docs state only the 31+ requirement while the guide says `BLUETOOTH` is necessary for any classic
 * or BLE communication below that - the legacy rows below follow the guide, and the reason strings
 * say so.
 */
class FrozenPermissionRequirementResolver : PermissionRequirementResolver {

    override fun planFor(
        operation: BluetoothOperation,
        context: PermissionContext,
    ): OperationOutcome<PermissionPlan> {
        val targetSdk = context.targetSdk
            ?: return OperationOutcome.Failure(
                OmniBudsError(
                    category = OmniBudsErrorCategory.INVALID_STATE,
                    operationId = "permission-resolver.plan",
                    detail = "target SDK is unknown; the permission band cannot be chosen without guessing",
                ),
            )

        val modern = targetSdk >= MODERN_TARGET
        val band = if (modern) "targetSdk>=$MODERN_TARGET (modern model)" else "targetSdk<=$LEGACY_MAX (legacy model)"

        val requirements = when (operation) {
            // ---- Phase 2 authorised: nothing is required, on either band, for these.
            BluetoothOperation.ADAPTER_AVAILABILITY_INSPECTION ->
                emptyList()

            BluetoothOperation.ADAPTER_STATE_INSPECTION,
            BluetoothOperation.ADAPTER_STATE_OBSERVATION,
            ->
                if (modern) {
                    emptyList()
                } else {
                    listOf(
                        requirement(
                            operation,
                            BluetoothPermission.BLUETOOTH,
                            ApiRange(MIN_MATRIX_SDK, LEGACY_MAX),
                            REASON_LEGACY_ADAPTER_READ,
                        ),
                    )
                }

            BluetoothOperation.PLATFORM_CAPABILITY_INSPECTION,
            BluetoothOperation.PERMISSION_STATUS_INSPECTION,
            ->
                emptyList()

            // ---- Phase 3 and later, pre-computed and not executable here.
            BluetoothOperation.CONNECTED_DEVICE_INSPECTION,
            BluetoothOperation.BONDED_DEVICE_LIST_INSPECTION,
            BluetoothOperation.PROFILE_CONNECTION_STATE_INSPECTION,
            ->
                if (modern) {
                    listOf(
                        requirement(
                            operation,
                            BluetoothPermission.BLUETOOTH_CONNECT,
                            ApiRange(MODERN_TARGET, MAX_MATRIX_SDK),
                            REASON_CONNECT_DEVICE_METADATA,
                        ),
                    )
                } else {
                    listOf(
                        requirement(
                            operation,
                            BluetoothPermission.BLUETOOTH,
                            ApiRange(MIN_MATRIX_SDK, LEGACY_MAX),
                            REASON_GUIDE_LEVEL_NOT_METHOD_LEVEL,
                        ),
                    )
                }

            BluetoothOperation.DEVICE_DISCOVERY_SCAN ->
                if (modern) {
                    val scan = listOf(
                        requirement(
                            operation,
                            BluetoothPermission.BLUETOOTH_SCAN,
                            ApiRange(MODERN_TARGET, MAX_MATRIX_SDK),
                            REASON_SCAN_RESULTS_LOCATION,
                        ),
                    )
                    if (context.assertsNeverForLocation) {
                        scan
                    } else {
                        scan + requirement(
                            operation,
                            BluetoothPermission.ACCESS_FINE_LOCATION,
                            ApiRange(MODERN_TARGET, MAX_MATRIX_SDK),
                            REASON_LOCATION_UNTIL_NEVER_FOR_LOCATION,
                        )
                    }
                } else {
                    listOf(
                        requirement(
                            operation,
                            BluetoothPermission.BLUETOOTH,
                            ApiRange(MIN_MATRIX_SDK, LEGACY_MAX),
                            REASON_LEGACY_ADAPTER_READ,
                        ),
                        requirement(
                            operation,
                            BluetoothPermission.BLUETOOTH_ADMIN,
                            ApiRange(MIN_MATRIX_SDK, LEGACY_MAX),
                            REASON_LEGACY_DISCOVERY_START,
                        ),
                        requirement(
                            operation,
                            BluetoothPermission.ACCESS_COARSE_LOCATION,
                            ApiRange(MIN_MATRIX_SDK, LEGACY_MAX),
                            REASON_LEGACY_SCAN_RESULTS,
                        ),
                        requirement(
                            operation,
                            BluetoothPermission.ACCESS_FINE_LOCATION,
                            ApiRange(MIN_MATRIX_SDK, LEGACY_MAX),
                            REASON_LEGACY_SCAN_RESULTS,
                        ),
                    )
                }

            BluetoothOperation.TRANSPORT_GATT_OPEN,
            BluetoothOperation.TRANSPORT_RFCOMM_OPEN,
            ->
                if (modern) {
                    listOf(
                        requirement(
                            operation,
                            BluetoothPermission.BLUETOOTH_CONNECT,
                            ApiRange(MODERN_TARGET, MAX_MATRIX_SDK),
                            REASON_CONNECT_SOCKET_OPEN,
                        ),
                    )
                } else {
                    listOf(
                        requirement(
                            operation,
                            BluetoothPermission.BLUETOOTH,
                            ApiRange(MIN_MATRIX_SDK, LEGACY_MAX),
                            REASON_GUIDE_LEVEL_NOT_METHOD_LEVEL,
                        ),
                    )
                }

            BluetoothOperation.LE_AUDIO_SESSION_INSPECTION ->
                // The class itself does not exist below API 31, so a legacy target has nothing to
                // ask permission for: this is an API-availability answer, not a permission answer.
                if (!modern) {
                    emptyList()
                } else {
                    listOf(
                        requirement(
                            operation,
                            BluetoothPermission.BLUETOOTH_CONNECT,
                            ApiRange(MODERN_TARGET, MAX_MATRIX_SDK),
                            REASON_LE_AUDIO_PROFILE_STATE,
                        ),
                    )
                }
        }

        return OperationOutcome.Success(
            PermissionPlan(
                operation = operation,
                requirements = requirements,
                bandLabel = band,
                indeterminate = false,
                caveat = caveatFor(operation, context, modern),
            ),
        )
    }

    /**
     * Device-level facts that change behaviour but not the requirement list.
     *
     * Kept separate from requirements so a caveat can never be read as a permission the app may
     * request.
     */
    private fun caveatFor(
        operation: BluetoothOperation,
        context: PermissionContext,
        modern: Boolean,
    ): String? = when {
        operation == BluetoothOperation.LE_AUDIO_SESSION_INSPECTION && !modern ->
            "the LE Audio platform API does not exist below API 31; this is an availability limit, not a permission grant"

        operation == BluetoothOperation.PROFILE_CONNECTION_STATE_INSPECTION &&
            modern && (context.deviceSdk ?: 0) >= MODERN_TARGET ->
            "profile-state enforcement is actually active on devices running API 31 or above; a 31+ target on an older device is documented but not enforced"

        operation == BluetoothOperation.DEVICE_DISCOVERY_SCAN && modern && !context.assertsNeverForLocation ->
            "without neverForLocation, scan results are location-derived; asserting the flag filters some BLE beacons and must be decided with the scan design"

        else ->
            null
    }

    private fun requirement(
        operation: BluetoothOperation,
        permission: BluetoothPermission,
        appliesTo: ApiRange,
        reason: String,
    ): PermissionRequirement = PermissionRequirement(
        operation = operation,
        permission = permission,
        appliesTo = appliesTo,
        required = true,
        reason = reason,
    )

    private companion object {
        const val MODERN_TARGET = 31
        const val LEGACY_MAX = 30
        const val MIN_MATRIX_SDK = ApiRange.MIN_MATRIX_SDK
        const val MAX_MATRIX_SDK = ApiRange.MAX_MATRIX_SDK

        const val REASON_LEGACY_ADAPTER_READ =
            "below target 31 the platform documents adapter state reads as requiring the install-time BLUETOOTH permission"
        const val REASON_LEGACY_DISCOVERY_START =
            "starting classic discovery below target 31 is an administrator action requiring BLUETOOTH_ADMIN"
        const val REASON_LEGACY_SCAN_RESULTS =
            "below target 31 scan results are treated as location data, so the location permissions are required to receive them"
        const val REASON_GUIDE_LEVEL_NOT_METHOD_LEVEL =
            "the compatibility guide requires BLUETOOTH for any classic or BLE communication at target 30 and below, while the per-method pages state only the 31+ requirement; the guide is followed and the gap recorded (research Note A)"
        const val REASON_CONNECT_DEVICE_METADATA =
            "reading another device's identifying or connection state requires BLUETOOTH_CONNECT from target 31"
        const val REASON_CONNECT_SOCKET_OPEN =
            "opening a transport socket to a device requires BLUETOOTH_CONNECT from target 31"
        const val REASON_SCAN_RESULTS_LOCATION =
            "discovery or BLE scanning requires BLUETOOTH_SCAN from target 31"
        const val REASON_LOCATION_UNTIL_NEVER_FOR_LOCATION =
            "without a neverForLocation assertion, scan results remain location-derived and fine location is required"
        const val REASON_LE_AUDIO_PROFILE_STATE =
            "LE Audio group and connection state is device profile metadata and requires BLUETOOTH_CONNECT"
    }
}
