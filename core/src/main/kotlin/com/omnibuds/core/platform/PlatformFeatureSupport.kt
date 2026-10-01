package com.omnibuds.core.platform

import com.omnibuds.core.state.VerificationLevel

/**
 * What is known about one [PlatformFeature], with the three independent reasons it might be
 * unusable kept apart.
 *
 * Phase 2 prompt section 5.5 forbids collapsing these, and each collapse maps to a distinct
 * real-world lie:
 *
 *  - [apiAvailability] is about the *operating system*: does the SDK on this Android version expose
 *    the API at all. `BluetoothLeAudio` existing on API 33 says nothing about the phone.
 *  - [hardwareEvidence] is about the *phone*: how strongly we know the silicon can do it. On
 *    Android this is rarely better than [VerificationLevel.INFERRED], because a feature flag or an
 *    OEM property is not a test, and an untested case stays INFERRED rather than being promoted to
 *    "supported" (master section 54).
 *  - [permissionState] is about the *user's grant*, which can change without either of the above
 *    changing.
 *
 * Connected-device support is the fourth fact and is deliberately absent here: it belongs to the
 * per-device capability model, not to the platform's.
 */
data class PlatformFeatureSupport(
    val apiAvailability: ApiAvailability,
    val hardwareEvidence: VerificationLevel,
    val permissionState: PermissionState,
) {
    /**
     * True only when the OS exposes the API, the hardware claim is backed by at least a lab or
     * device observation, and the permission needed to use it is held or genuinely not required.
     */
    val isUsable: Boolean
        get() = apiAvailability == ApiAvailability.AVAILABLE &&
            hardwareEvidence >= VerificationLevel.LAB_TESTED &&
            (permissionState.isGranted() || permissionState.allowsSilentProceeding())

    /**
     * Which of the three facts is blocking use, or null when none is.
     *
     * Naming the reason matters for honesty: telling a user a phone "does not support" a feature
     * when the app was simply not permitted to look is a false statement about hardware.
     */
    val blockingReason: String?
        get() = when {
            apiAvailability == ApiAvailability.UNAVAILABLE -> "the platform API is not available on this Android version"
            apiAvailability == ApiAvailability.UNKNOWN -> "the platform API availability was not determined"
            hardwareEvidence < VerificationLevel.LAB_TESTED -> "phone hardware support is not verified beyond inference"
            permissionState.isRefused() -> "the required permission was refused"
            permissionState == PermissionState.UNKNOWN -> "the permission status could not be determined"
            !permissionState.isGranted() && !permissionState.allowsSilentProceeding() -> "the required permission has not been granted"
            else -> null
        }

    companion object {
        /** Nothing is known. The honest default for a feature nobody probed. */
        fun unknown(): PlatformFeatureSupport = PlatformFeatureSupport(
            apiAvailability = ApiAvailability.UNKNOWN,
            hardwareEvidence = VerificationLevel.INFERRED,
            permissionState = PermissionState.UNKNOWN,
        )
    }
}
