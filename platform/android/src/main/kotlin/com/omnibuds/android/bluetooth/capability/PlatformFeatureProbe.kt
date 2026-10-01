package com.omnibuds.android.bluetooth.capability

import android.content.Context
import android.content.pm.PackageManager
import com.omnibuds.core.platform.TimeProvider

/**
 * Asks the platform whether this phone advertises a hardware feature.
 *
 * `PackageManager.hasSystemFeature(String)` is the only app-callable statement a phone gives about
 * its own Bluetooth hardware classes, and it needs no permission (ADR-P2-011, research Q7). The
 * names are the platform's own constants - `FEATURE_BLUETOOTH` since API 8 and `FEATURE_BLUETOOTH_LE`
 * since API 18, both confirmed against the SDK's API table rather than from memory - so nothing here
 * invents a feature string.
 *
 * A negative answer is meaningful, a thrown answer is not: an exception is reported by the caller as
 * unknown rather than as "this phone has no Bluetooth".
 */
fun interface PlatformFeatureProbe {
    fun hasFeature(featureName: String): Boolean
}

/** Reads feature declarations from the running platform. */
class SystemPlatformFeatureProbe(context: Context) : PlatformFeatureProbe {
    private val packageManager: PackageManager = context.packageManager

    override fun hasFeature(featureName: String): Boolean = packageManager.hasSystemFeature(featureName)
}
