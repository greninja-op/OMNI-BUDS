package com.omnibuds.core.platform

/**
 * Platform-independent host process lifecycle state.
 */
enum class PlatformLifecycleState {
    /** Application is foregrounded / actively interacting with user. */
    FOREGROUND,

    /** Application is backgrounded (e.g. minimized, system tray, background service). */
    BACKGROUND,

    /** Application is suspended / paused by OS. */
    SUSPENDED,

    /** Application is shutting down. */
    TERMINATING;

    val isInteractive: Boolean
        get() = this == FOREGROUND
}
