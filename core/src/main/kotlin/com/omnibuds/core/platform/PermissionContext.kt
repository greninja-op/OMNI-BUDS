package com.omnibuds.core.platform

/**
 * The two Android version facts a permission decision depends on.
 *
 * **Both are required, and they are not the same number.** The verified platform research for this
 * phase (`docs/phases/phase-2/bluetooth-api-research.md` section 3) established that Bluetooth
 * permission requirements key off the app's `targetSdkVersion`, while several enforcement behaviours
 * and API members depend on the device's actual API level. A resolver taking only one of them
 * answers the wrong question - for example `getProfileConnectionState()` is documented as requiring
 * `BLUETOOTH_CONNECT` for a 31+ target, but enforcement only actually bites on a device running 31+.
 *
 * Both are nullable because a caller may legitimately not know yet. Unknown is not defaulted: an
 * absent value produces an indeterminate plan rather than a guessed one (ADR-P0-016), because a
 * permission request issued on a guess is a request the user cannot be told the reason for.
 */
data class PermissionContext(
    /** The app's declared target SDK, or null when it has not been established. */
    val targetSdk: Int?,

    /** The API level the device is actually running, or null when unknown. */
    val deviceSdk: Int?,

    /**
     * Whether the app asserts `neverForLocation` on `BLUETOOTH_SCAN`.
     *
     * This is not a free optimisation: the research records that asserting it filters out some BLE
     * beacon results, so the flag must be decided alongside the scan design that needs it, not
     * defaulted to `true` because it reduces prompts.
     */
    val assertsNeverForLocation: Boolean = false,
) {
    val isDetermined: Boolean
        get() = targetSdk != null

    /** True when the modern runtime-permission model applies to this app. */
    val usesModernBluetoothModel: Boolean
        get() = (targetSdk ?: 0) >= MODERN_MODEL_TARGET_SDK

    companion object {
        /** Android 12, where `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` replaced the legacy pair. */
        const val MODERN_MODEL_TARGET_SDK = 31

        /** Android 11, where `getAlias()` and several profile behaviours begin. */
        const val ALIAS_API_LEVEL = 30

        /** Android 14, where the receiver-export flag rule applies to non-system broadcasts. */
        const val RECEIVER_EXPORT_TARGET_SDK = 34
    }
}
