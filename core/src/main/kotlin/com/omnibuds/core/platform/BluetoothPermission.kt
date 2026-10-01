package com.omnibuds.core.platform

/**
 * The Bluetooth-related permissions this project may ever have to reason about.
 *
 * The values carry only facts that are stable: the manifest name, the API level the permission
 * was introduced at, and whether it is a normal (install-time) or dangerous (runtime) permission.
 * *Which* operation needs *which* permission on which Android version is policy, not a property of
 * the permission, and lives in [PermissionRequirementResolver] - so that a correction to the matrix
 * never requires editing this enum, and no call site can invent a requirement by reading a field
 * here.
 *
 * [isLegacyForTargeting31Plus] records that an app targeting API 31 or above is not granted the
 * old Bluetooth permissions on a 31+ device: they are ignored there and still apply below it. That
 * asymmetry is the whole reason the resolver is version-aware.
 */
enum class BluetoothPermission(
    /** The exact manifest name, so logs and declarations can be traced back to this model. */
    val manifestName: String,

    /** First API level at which this permission exists. Below it the name means nothing. */
    val introducedAtSdk: Int,

    /** Dangerous permissions are granted by the user at runtime; normal ones at install. */
    val isRuntimePermission: Boolean,

    /** Whether this permission belongs to the pre-API 31 model. */
    val isLegacyForTargeting31Plus: Boolean,
) {
    BLUETOOTH("android.permission.BLUETOOTH", 18, isRuntimePermission = false, isLegacyForTargeting31Plus = true),
    BLUETOOTH_ADMIN("android.permission.BLUETOOTH_ADMIN", 18, isRuntimePermission = false, isLegacyForTargeting31Plus = true),
    BLUETOOTH_SCAN("android.permission.BLUETOOTH_SCAN", 31, isRuntimePermission = true, isLegacyForTargeting31Plus = false),
    BLUETOOTH_CONNECT("android.permission.BLUETOOTH_CONNECT", 31, isRuntimePermission = true, isLegacyForTargeting31Plus = false),
    ACCESS_FINE_LOCATION("android.permission.ACCESS_FINE_LOCATION", 1, isRuntimePermission = true, isLegacyForTargeting31Plus = false),
    ACCESS_COARSE_LOCATION("android.permission.ACCESS_COARSE_LOCATION", 1, isRuntimePermission = true, isLegacyForTargeting31Plus = false),
}

/**
 * The API band a rule applies to, expressed as an inclusive range so that a hole in the matrix
 * cannot be created by a missing `else`.
 */
data class ApiRange(val minSdkInclusive: Int, val maxSdkInclusive: Int) {
    init {
        require(minSdkInclusive in MIN_SUPPORTED..MAX_SUPPORTED) {
            "range start $minSdkInclusive is outside $MIN_SUPPORTED..$MAX_SUPPORTED"
        }
        require(maxSdkInclusive >= minSdkInclusive) {
            "range $minSdkInclusive..$maxSdkInclusive is inverted"
        }
    }

    fun contains(sdkInt: Int): Boolean = sdkInt in minSdkInclusive..maxSdkInclusive

    companion object {
        const val MIN_SUPPORTED = 26
        const val MAX_SUPPORTED = 35

        /** Everything the app can be installed on below the modern permission model. */
        val LEGACY_BLUETOOTH_MODEL = ApiRange(MIN_SUPPORTED, 30)

        /** Android 12 and above, where BLUETOOTH_SCAN and BLUETOOTH_CONNECT apply. */
        val MODERN_BLUETOOTH_MODEL = ApiRange(31, MAX_SUPPORTED)
    }
}
