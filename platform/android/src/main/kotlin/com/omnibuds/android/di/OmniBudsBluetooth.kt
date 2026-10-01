package com.omnibuds.android.di

import android.content.Context
import com.omnibuds.android.bluetooth.AndroidBluetoothPlatform
import com.omnibuds.android.bluetooth.adapter.BluetoothAdapterHandle
import com.omnibuds.android.bluetooth.adapter.SystemBluetoothAdapterHandle
import com.omnibuds.android.bluetooth.capability.AndroidPlatformCapabilityProvider
import com.omnibuds.android.bluetooth.capability.ApiLevelProvider
import com.omnibuds.android.bluetooth.capability.PlatformFeatureProbe
import com.omnibuds.android.bluetooth.capability.SystemApiLevelProvider
import com.omnibuds.android.bluetooth.capability.SystemPlatformFeatureProbe
import com.omnibuds.android.bluetooth.capability.SystemTargetSdkProvider
import com.omnibuds.android.bluetooth.capability.TargetSdkProvider
import com.omnibuds.android.bluetooth.permission.AndroidPermissionStateProvider
import com.omnibuds.android.bluetooth.permission.InMemoryPermissionRequestLedger
import com.omnibuds.android.bluetooth.permission.PermissionRequestLedger
import com.omnibuds.android.bluetooth.permission.PermissionStandingReader
import com.omnibuds.android.bluetooth.permission.SystemPermissionStandingReader
import com.omnibuds.core.platform.BluetoothPlatform
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
 * Note what is *not* here: no permission request, no adapter enable prompt, no transport, no device
 * list. Those are Phase 3 and later, and a factory is not a licence to add a capability to it
 * (Phase 2 prompt section 6).
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
