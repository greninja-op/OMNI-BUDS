package com.omnibuds.android.di

import android.content.Context
import com.omnibuds.android.bluetooth.AndroidBluetoothPlatform
import com.omnibuds.android.bluetooth.SystemTimeProvider
import com.omnibuds.android.bluetooth.adapter.BluetoothAdapterHandle
import com.omnibuds.android.bluetooth.adapter.SystemBluetoothAdapterHandle
import com.omnibuds.android.bluetooth.capability.AndroidPlatformCapabilityProvider
import com.omnibuds.android.bluetooth.capability.ApiLevelProvider
import com.omnibuds.android.bluetooth.capability.PlatformFeatureProbe
import com.omnibuds.android.bluetooth.capability.SystemApiLevelProvider
import com.omnibuds.android.bluetooth.capability.SystemPlatformFeatureProbe
import com.omnibuds.android.bluetooth.capability.SystemTargetSdkProvider
import com.omnibuds.android.bluetooth.capability.TargetSdkProvider
import com.omnibuds.android.bluetooth.connection.AndroidConnectedDeviceSource
import com.omnibuds.android.bluetooth.connection.SystemConnectedDeviceHandle
import com.omnibuds.android.bluetooth.permission.AndroidPermissionStateProvider
import com.omnibuds.android.bluetooth.permission.InMemoryPermissionRequestLedger
import com.omnibuds.android.bluetooth.permission.PermissionRequestLedger
import com.omnibuds.android.bluetooth.permission.PermissionStandingReader
import com.omnibuds.android.bluetooth.permission.SystemPermissionStandingReader
import com.omnibuds.core.platform.AdapterStateSource
import com.omnibuds.core.platform.BluetoothPlatform
import com.omnibuds.core.platform.ConnectedDeviceObserver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * The Android parts wired together, with every seam still replaceable.
 *
 * Manual construction rather than a dependency-injection library (ADR-P1-009): Phase 2 has six seams,
 * and a framework would cost a build dependency and a generated module graph to do what twenty lines of
 * readable code do. `:core`'s tests replace these seams directly; so can an app.
 *
 * The application context is taken and not the incoming one, because a Bluetooth platform outlives the
 * screen that first asked for it, and holding the requesting context would keep that screen reachable
 * after the user left it.
 *
 * **One instance per process.** [com.omnibuds.core.platform.AdapterStateObserver]'s single-slot rule is
 * per object, so two platforms would mean two platform registrations for one fact - the leak the mutex
 * exists to stop, reproduced by constructing twice. Whoever owns this object owns that uniqueness; the
 * function itself deliberately does not cache, because a hidden global is how a process ends up with a
 * registration nobody can dispose.
 *
 * Note what is *not* here: no permission request, no adapter enable prompt, no transport. Phase 3 added
 * the device-observation half, which is the other factory below; it added no capability to this one, and
 * a factory is not a licence to widen the phase that owns it (Phase 2 prompt section 6).
 */
fun omniBudsBluetoothPlatform(
    context: Context,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    apiLevel: ApiLevelProvider = SystemApiLevelProvider,
    standingReader: PermissionStandingReader = SystemPermissionStandingReader(context.applicationContext),
    requestLedger: PermissionRequestLedger = InMemoryPermissionRequestLedger(),
    featureProbe: PlatformFeatureProbe = SystemPlatformFeatureProbe(context.applicationContext),
    targetSdk: TargetSdkProvider = SystemTargetSdkProvider(context.applicationContext),
): BluetoothPlatform {
    val appContext = context.applicationContext
    val handle: BluetoothAdapterHandle = SystemBluetoothAdapterHandle(appContext, apiLevel)
    val permissionProvider = AndroidPermissionStateProvider(
        reader = standingReader,
        ledger = requestLedger,
    )
    return AndroidBluetoothPlatform(
        handle = handle,
        capabilityProvider = AndroidPlatformCapabilityProvider(
            apiLevel = apiLevel,
            featureProbe = featureProbe,
            permissionProvider = permissionProvider,
            targetSdk = targetSdk,
            adapterPresent = { handle.adapterPresent },
        ),
        permissionProvider = permissionProvider,
        dispatcher = dispatcher,
    )
}

/**
 * The connected-device observation, composed the same way and subject to the same uniqueness rule.
 *
 * Phase 3 put the reconciliation engine in `:core` (ADR-P3-003), so what this function builds is the
 * platform half of it: the one framework-facing handle, the source that owns the ordering rule
 * (standing before enumeration, ADR-P3-009), and the engine that turns announcements and snapshots into
 * one projection. Nothing about *how* devices are decided lives here, because a composition root that
 * re-implemented the fold would be a second copy of the rules the `:core` tests cover.
 *
 * [adapterStates] is a parameter rather than something built here, and that is the "one instance per
 * process" note being carried forward rather than repeated. The observer watches the adapter so a device
 * projection can be marked unobservable when the phone stops reporting itself, and a second adapter
 * source constructed here would be a second `ACTION_STATE_CHANGED` receiver for one fact - the exact
 * leak the note above describes, arriving through a different door. Whoever owns the platform from
 * [omniBudsBluetoothPlatform] should hand its adapter source to this factory; the rule that one
 * connected-device observer exists per process is now the rule for the profile bindings too, because
 * `getProfileProxy` owns a service binding that outlives a careless second observer and this phase cannot
 * reclaim it (ADR-P3-008's cleanup obligation).
 *
 * What is not here, and cannot be reached through anything wired here: any permission request, any
 * pairing, connect, enable, discover, scan or write to a device, and any audio-path or codec surface
 * (Phase 3 prompt sections 16, 17; research section 8.2). The guard in `DependencyDirectionTest` rule 7
 * refuses those by name in `src/main` and in `src/androidTest` alike.
 */
fun omniBudsConnectedDeviceObserver(
    context: Context,
    adapterStates: AdapterStateSource,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    apiLevel: ApiLevelProvider = SystemApiLevelProvider,
    standingReader: PermissionStandingReader = SystemPermissionStandingReader(context.applicationContext),
    requestLedger: PermissionRequestLedger = InMemoryPermissionRequestLedger(),
    targetSdk: TargetSdkProvider = SystemTargetSdkProvider(context.applicationContext),
): ConnectedDeviceObserver {
    val appContext = context.applicationContext
    val source = AndroidConnectedDeviceSource(
        handle = SystemConnectedDeviceHandle(appContext, apiLevel),
        permissionProvider = AndroidPermissionStateProvider(
            reader = standingReader,
            ledger = requestLedger,
        ),
        apiLevel = apiLevel,
        targetSdk = targetSdk,
        time = SystemTimeProvider,
        dispatcher = dispatcher,
    )
    return ConnectedDeviceObserver(
        source = source,
        adapterStates = adapterStates,
        time = SystemTimeProvider,
    )
}
