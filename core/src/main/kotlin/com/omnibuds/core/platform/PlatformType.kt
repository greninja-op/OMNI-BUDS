package com.omnibuds.core.platform

/**
 * Operating system and runtime execution environments supported or targeted by OmniBuds.
 *
 * Designed to cleanly distinguish mobile from desktop hosts without leaking platform-specific
 * runtime classes into the portable core domain.
 */
enum class PlatformType(val technicalName: String) {
    ANDROID("android"),
    LINUX("linux"),
    MACOS("macos"),
    WINDOWS("windows"),
    DESKTOP_GENERIC("desktop-generic"),
    UNKNOWN("unknown");

    val isDesktop: Boolean
        get() = this == LINUX || this == MACOS || this == WINDOWS || this == DESKTOP_GENERIC

    val isMobile: Boolean
        get() = this == ANDROID
}
