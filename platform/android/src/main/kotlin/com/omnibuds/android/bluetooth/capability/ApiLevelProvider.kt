package com.omnibuds.android.bluetooth.capability

import android.os.Build

/**
 * The running Android API level, as an injectable seam.
 *
 * `Build.VERSION.SDK_INT` is read through here rather than at each use site for two reasons. The
 * first is testability: a capability matrix that reads a static field cannot be exercised across
 * API bands, and Phase 2 prompt section 9 asks for version-dependent behaviour to be tested. The
 * second is that `SDK_INT` is a property of the *phone*, while permission requirements are a
 * property of the app's `targetSdkVersion`; keeping the phone's level behind one named seam is what
 * stops those two being confused in a call site (ADR-P2-009).
 */
fun interface ApiLevelProvider {
    /** The device's API level, as `Build.VERSION.SDK_INT` reports it. */
    fun apiLevel(): Int
}

/** Reads the level from the running platform. The only production implementation. */
object SystemApiLevelProvider : ApiLevelProvider {
    override fun apiLevel(): Int = Build.VERSION.SDK_INT
}
