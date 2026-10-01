package com.omnibuds.core.platform

/**
 * Whether the *operating system* exposes an API, independent of whether this phone's hardware
 * can do the thing or whether the user granted permission.
 *
 * Phase 2 prompt section 5.5 requires four separate facts and forbids collapsing them:
 * OS API availability ([ApiAvailability]), phone hardware capability
 * ([com.omnibuds.core.state.CapabilityState]), runtime permission availability
 * ([PermissionState]), and actual connected-device support
 * ([com.omnibuds.core.state.CapabilityState] on a per-device capability). The classic failure is
 * reporting LE Audio as available because `BluetoothLeAudio` exists in the SDK.
 */
enum class ApiAvailability {
    /** Not determined. */
    UNKNOWN,

    /** The API exists on this OS version. */
    AVAILABLE,

    /** The API does not exist on this OS version. */
    UNAVAILABLE,
}

/**
 * Whether the phone's hardware is believed to support a feature.
 *
 * Kept distinct from [ApiAvailability] so that "the SDK has the class" can never be reported as
 * "this phone can do it". Feature detection on Android is frequently indirect, so an unproven
 * case is [UNKNOWN] rather than false (master section 53).
 */
fun apiLevelSupports(sdkInt: Int?, minimumSdk: Int): ApiAvailability = when {
    sdkInt == null || minimumSdk <= 0 -> ApiAvailability.UNKNOWN
    sdkInt >= minimumSdk -> ApiAvailability.AVAILABLE
    else -> ApiAvailability.UNAVAILABLE
}
