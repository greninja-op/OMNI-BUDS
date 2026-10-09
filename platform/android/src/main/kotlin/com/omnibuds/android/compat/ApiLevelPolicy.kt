package com.omnibuds.android.compat

/**
 * API-level decision boundaries for OmniBuds.
 *
 * Phase 32: pure, JVM-testable. All decisions derive from the actual
 * SDK configuration (minSdk 26, targetSdk 35) and documented Android
 * behavior — never assumptions.
 */
object ApiLevelPolicy {

    /** Bluetooth runtime permissions (BLUETOOTH_SCAN/CONNECT) required. */
    fun bluetoothRuntimePermissionsRequired(apiLevel: Int): Boolean =
        apiLevel >= 31

    /** Legacy BLUETOOTH/BLUETOOTH_ADMIN model (install-time). */
    fun legacyBluetoothPermissions(apiLevel: Int): Boolean =
        apiLevel in 26..30

    /** POST_NOTIFICATIONS is a runtime permission. */
    fun notificationRuntimePermissionRequired(apiLevel: Int): Boolean =
        apiLevel >= 33

    /** Notification channels required (API 26+; always true at minSdk). */
    fun notificationChannelsRequired(apiLevel: Int): Boolean =
        apiLevel >= 26

    /** Quick Settings TileService available (API 24+; always true at minSdk). */
    fun tileServiceAvailable(apiLevel: Int): Boolean =
        apiLevel >= 24

    /** Widget list preview supported. */
    fun widgetPreviewSupported(apiLevel: Int): Boolean =
        apiLevel >= 31

    /** Foreground-service types enforced. */
    fun foregroundServiceTypesEnforced(apiLevel: Int): Boolean =
        apiLevel >= 29

    /** Background activity-start restrictions apply. */
    fun backgroundActivityRestricted(apiLevel: Int): Boolean =
        apiLevel >= 29

    /** Exact-alarm restrictions apply. */
    fun exactAlarmRestricted(apiLevel: Int): Boolean =
        apiLevel >= 31

    /** True when the API level is within the supported range. */
    fun isSupported(apiLevel: Int): Boolean =
        apiLevel in 26..35
}
