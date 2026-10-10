package com.omnibuds.core.platform.desktop

/**
 * Explicit representation of desktop Bluetooth adapter availability status.
 *
 * Grounded in operating system facts rather than optimistic assumptions.
 * Distinguishes unavailable hardware from disabled state, permission issues,
 * and unsupported OS APIs without conflating them.
 */
enum class DesktopBluetoothAvailability(val technicalName: String) {
    /** Hardware adapter exists, is enabled, authorized, and ready for operations. */
    AVAILABLE("available"),

    /** No Bluetooth hardware detected or controller is disconnected/inaccessible. */
    UNAVAILABLE("unavailable"),

    /** Bluetooth adapter exists but is powered off by user or system radio kill-switch (e.g. rfkill). */
    DISABLED("disabled"),

    /** OS authorization required before Bluetooth can be accessed (e.g. macOS TCC). */
    PERMISSION_REQUIRED("permission-required"),

    /** Access was explicitly denied or revoked by the user or OS security policy. */
    PERMISSION_DENIED("permission-denied"),

    /** Platform API or OS subsystem lacks necessary Bluetooth capabilities or is not supported. */
    UNSUPPORTED("unsupported"),

    /** Subsystem or daemon (e.g. BlueZ DBus daemon) is currently starting up or initializing. */
    INITIALIZING("initializing"),

    /** Status cannot be determined from available evidence; never guessed. */
    UNKNOWN("unknown");

    /** True only if adapter can currently execute operations. */
    val isUsable: Boolean get() = this == AVAILABLE

    /** True if failure is due to authorization. */
    val isAuthorizationIssue: Boolean get() = this == PERMISSION_REQUIRED || this == PERMISSION_DENIED

    /** True if state represents an absence or indeterminacy. */
    val isIndeterminate: Boolean get() = this == UNKNOWN || this == INITIALIZING
}
