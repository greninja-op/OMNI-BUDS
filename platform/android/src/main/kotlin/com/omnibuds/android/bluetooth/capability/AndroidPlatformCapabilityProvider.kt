package com.omnibuds.android.bluetooth.capability

import android.content.Context
import android.content.pm.PackageManager
import com.omnibuds.android.bluetooth.permission.AndroidPermissionStateProvider
import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.platform.ApiAvailability
import com.omnibuds.core.platform.BluetoothOperation
import com.omnibuds.core.platform.BluetoothPermission
import com.omnibuds.core.platform.BluetoothPlatformCapabilities
import com.omnibuds.core.platform.PermissionContext
import com.omnibuds.core.platform.PlatformFeature
import com.omnibuds.core.platform.PlatformFeatureSupport
import com.omnibuds.core.platform.apiLevelSupports
import com.omnibuds.core.state.VerificationLevel

/**
 * Reads the app's declared target SDK version from the running platform.
 *
 * This is the number that selects the modern-versus-legacy Bluetooth permission model, and it is not
 * `Build.VERSION.SDK_INT`: an app targeting API 31 running on an API 29 phone is in the modern band
 * (ADR-P2-009). `ApplicationInfo.targetSdkVersion` is where the platform records the value (API 4 and
 * up), so it is read rather than assumed from a build configuration the runtime cannot see.
 *
 * A null answer means the platform did not supply one, and a permission plan then refuses to guess.
 */
fun interface TargetSdkProvider {
    fun targetSdk(): Int?
}

/** Reads the target SDK the package manager recorded for this app. */
class SystemTargetSdkProvider(private val context: Context) : TargetSdkProvider {
    override fun targetSdk(): Int? = runCatching { context.applicationInfo.targetSdkVersion }.getOrNull()
}

/**
 * Builds the platform capability report from facts the phone states about itself.
 *
 * Nothing here involves a headset: adapter presence, feature declarations, API level and permission
 * standings are all local facts, which is what keeps this inside Phase 2's authorised scope (prompt
 * section 5.5) while device-facing capability discovery stays in Phase 3.
 *
 * The kinds of fact are kept separate because they fail separately. `apiAvailability` is what this
 * Android can expose; `hardwareEvidence` is what has been shown about this phone's hardware;
 * `permissionState` is what the user has allowed; and connected-device support is not modelled here at
 * all, because a phone advertising `FEATURE_BLUETOOTH_LE` says nothing about a particular pair of
 * earbuds.
 *
 * Every entry reports `hardwareEvidence = INFERRED`, because Phase 2 opened no radio and ran no lab
 * procedure. `isUsable` is therefore false for every feature, with a reason that names the missing
 * evidence - the honest answer rather than a confident guess, and the state a later phase changes by
 * testing, not by editing a constant (ADR-P2-008).
 *
 * Each row's `permissionState` is the standing of the permission that **the operation paired with it
 * in `OPERATIONS_BY_FEATURE`** would need, judged against this app's `targetSdk` - not a general
 * statement about the feature. The two rows paired with `PLATFORM_CAPABILITY_INSPECTION` therefore
 * answer `NOT_REQUIRED` on any target-31-and-above app, because assembling this report genuinely asks
 * the user for nothing. That is not a claim that classic or BLE use in general is permission-free;
 * those judgements belong to the transport and profile operations, which are Phase 3 and Phase 6, and
 * their rows are pre-computed here rather than authorised by it.
 */
class AndroidPlatformCapabilityProvider(
    private val apiLevel: ApiLevelProvider,
    private val featureProbe: PlatformFeatureProbe,
    private val permissionProvider: AndroidPermissionStateProvider,
    private val targetSdk: TargetSdkProvider,
    private val adapterPresent: () -> Boolean,
) {

    /**
     * The capability report, or the structured reason it could not be assembled.
     *
     * A platform call that throws is reported with the exception class named and nothing else: the
     * stack stays in diagnostics, and no device identifier reaches a user-facing message (SEC-LOG-001).
     * Only the adapter-presence question is fatal to the report; a failing feature probe degrades that
     * one entry to [ApiAvailability.UNKNOWN] instead of losing facts the platform did answer.
     */
    fun capabilities(): OperationOutcome<BluetoothPlatformCapabilities> {
        val presence = runCatching { adapterPresent() }
            .getOrElse { problem ->
                return OperationOutcome.Failure(
                    OmniBudsError(
                        category = OmniBudsErrorCategory.PLATFORM_EXCEPTION,
                        operationId = OPERATION_ID,
                        detail = "the adapter presence check reported ${problem::class.simpleName}",
                    ),
                )
            }

        val level = runCatching { apiLevel.apiLevel() }.getOrNull()
        val context = PermissionContext(
            targetSdk = targetSdk.targetSdk(),
            deviceSdk = level,
        )

        val classicAdvertised = probe(PackageManager.FEATURE_BLUETOOTH)
        val bleAdvertised = probe(PackageManager.FEATURE_BLUETOOTH_LE)

        var report = BluetoothPlatformCapabilities(
            apiLevel = level,
            adapterPresent = if (presence) ApiAvailability.AVAILABLE else ApiAvailability.UNAVAILABLE,
            features = emptyMap(),
            permissionStatus = permissionProvider.standings(),
            candidateTransports = emptySet(),
        )

        for (feature in PlatformFeature.entries) {
            report = report.withFeature(
                feature,
                PlatformFeatureSupport(
                    apiAvailability = availabilityOf(feature, level, classicAdvertised, bleAdvertised),
                    hardwareEvidence = VerificationLevel.INFERRED,
                    permissionState = permissionProvider.stateFor(
                        operation = OPERATIONS_BY_FEATURE.getValue(feature),
                        permission = PERMISSIONS_BY_FEATURE.getValue(feature),
                        context = context,
                    ),
                ),
            )
        }

        return OperationOutcome.Success(
            report.copy(candidateTransports = candidateTransports(classicAdvertised, bleAdvertised)),
        )
    }

    /**
     * The platform's own statement about [feature], or [ApiAvailability.UNKNOWN] where there is none.
     *
     * `UNKNOWN` is reserved for the entries with no evidence either way, and each has a recorded
     * reason instead of being a default:
     *
     *  - A2DP and HEADSET connection state come from `getProfileConnectionState`, which is
     *    `PROFILE_CONNECTION_STATE_INSPECTION`: a Phase 3 operation, and on target 31 and above one
     *    that needs `BLUETOOTH_CONNECT`, which this module's manifest does not declare (ADR-P2-011).
     *    Asking would either throw or return a value indistinguishable from "no such profile", so the
     *    answer is not looked for rather than guessed at.
     *  - LE Audio reports only the API's existence at the running level (`BluetoothLeAudio` is in the
     *    platform's API table from 31). The SDK this module compiles against declares no public feature
     *    constant for LE Audio - `PackageManager` has `FEATURE_BLUETOOTH` and `FEATURE_BLUETOOTH_LE`
     *    and nothing else Bluetooth-shaped - so hardware support cannot be probed, and a class being
     *    present is not a device supporting it (prompt section 5.5).
     */
    private fun availabilityOf(
        feature: PlatformFeature,
        level: Int?,
        classicAdvertised: Boolean?,
        bleAdvertised: Boolean?,
    ): ApiAvailability = when (feature) {
        PlatformFeature.CLASSIC_BLUETOOTH -> fromAdvertised(classicAdvertised)
        PlatformFeature.BLE_CENTRAL -> fromAdvertised(bleAdvertised)
        PlatformFeature.RFCOMM_CLIENT -> gatedBy(level, BLUETOOTH_SOCKET_API_LEVEL) { fromAdvertised(classicAdvertised) }
        PlatformFeature.GATT_CLIENT -> gatedBy(level, GATT_API_LEVEL) { fromAdvertised(bleAdvertised) }
        PlatformFeature.LE_AUDIO -> apiLevelSupports(level, LE_AUDIO_API_LEVEL)
        PlatformFeature.ADAPTER_STATE_OBSERVATION -> apiLevelSupports(level, BLUETOOTH_MANAGER_API_LEVEL)
        PlatformFeature.A2DP_CONNECTION_STATE,
        PlatformFeature.HEADSET_CONNECTION_STATE,
        -> ApiAvailability.UNKNOWN
    }

    private fun fromAdvertised(advertised: Boolean?): ApiAvailability = when (advertised) {
        null -> ApiAvailability.UNKNOWN
        true -> ApiAvailability.AVAILABLE
        false -> ApiAvailability.UNAVAILABLE
    }

    private fun probe(featureName: String): Boolean? = runCatching { featureProbe.hasFeature(featureName) }.getOrNull()

    /** An API level below the floor rules a feature out whatever the hardware flag says; a null level cannot. */
    private fun gatedBy(level: Int?, floor: Int, otherwise: () -> ApiAvailability): ApiAvailability =
        if (level != null && level < floor) ApiAvailability.UNAVAILABLE else otherwise()

    /**
     * Transports the host declares it could carry.
     *
     * Candidates on the *phone*, derived from its own feature flags - not a claim that any headset
     * supports them, and not a claim that Phase 2 opened anything (prompt sections 5.6, 6). LE Audio
     * cannot enter this set on an inference, because an unprobed feature never reports AVAILABLE.
     */
    private fun candidateTransports(
        classicAdvertised: Boolean?,
        bleAdvertised: Boolean?,
    ): Set<TransportKind> {
        val candidates = mutableSetOf<TransportKind>()
        if (classicAdvertised == true) {
            candidates += TransportKind.CLASSIC_BLUETOOTH
            candidates += TransportKind.RFCOMM
        }
        if (bleAdvertised == true) {
            candidates += TransportKind.BLE
            candidates += TransportKind.GATT
        }
        return candidates.toSet()
    }

    private companion object {
        const val OPERATION_ID = "android-platform-capabilities"

        // Introduction levels read from the API table shipped with the compileSdk this module builds
        // against (`platforms/android-35/data/api-versions.xml`), not recalled: BluetoothSocket and
        // BluetoothAdapter since 5, BluetoothGatt since 18, BluetoothManager and Context's
        // BLUETOOTH_SERVICE since 18, and BluetoothLeAudio since 31.
        const val BLUETOOTH_SOCKET_API_LEVEL = 5
        const val GATT_API_LEVEL = 18
        const val BLUETOOTH_MANAGER_API_LEVEL = 18
        const val LE_AUDIO_API_LEVEL = 31

        val OPERATIONS_BY_FEATURE: Map<PlatformFeature, BluetoothOperation> = mapOf(
            PlatformFeature.CLASSIC_BLUETOOTH to BluetoothOperation.PLATFORM_CAPABILITY_INSPECTION,
            PlatformFeature.BLE_CENTRAL to BluetoothOperation.PLATFORM_CAPABILITY_INSPECTION,
            PlatformFeature.GATT_CLIENT to BluetoothOperation.TRANSPORT_GATT_OPEN,
            PlatformFeature.RFCOMM_CLIENT to BluetoothOperation.TRANSPORT_RFCOMM_OPEN,
            PlatformFeature.LE_AUDIO to BluetoothOperation.LE_AUDIO_SESSION_INSPECTION,
            PlatformFeature.A2DP_CONNECTION_STATE to BluetoothOperation.PROFILE_CONNECTION_STATE_INSPECTION,
            PlatformFeature.HEADSET_CONNECTION_STATE to BluetoothOperation.PROFILE_CONNECTION_STATE_INSPECTION,
            PlatformFeature.ADAPTER_STATE_OBSERVATION to BluetoothOperation.ADAPTER_STATE_OBSERVATION,
        )

        val PERMISSIONS_BY_FEATURE: Map<PlatformFeature, BluetoothPermission> = mapOf(
            PlatformFeature.CLASSIC_BLUETOOTH to BluetoothPermission.BLUETOOTH,
            PlatformFeature.BLE_CENTRAL to BluetoothPermission.BLUETOOTH,
            PlatformFeature.GATT_CLIENT to BluetoothPermission.BLUETOOTH_CONNECT,
            PlatformFeature.RFCOMM_CLIENT to BluetoothPermission.BLUETOOTH_CONNECT,
            PlatformFeature.LE_AUDIO to BluetoothPermission.BLUETOOTH_CONNECT,
            PlatformFeature.A2DP_CONNECTION_STATE to BluetoothPermission.BLUETOOTH_CONNECT,
            PlatformFeature.HEADSET_CONNECTION_STATE to BluetoothPermission.BLUETOOTH_CONNECT,
            PlatformFeature.ADAPTER_STATE_OBSERVATION to BluetoothPermission.BLUETOOTH,
        )
    }
}
