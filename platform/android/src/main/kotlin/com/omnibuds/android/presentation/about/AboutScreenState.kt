package com.omnibuds.android.presentation.about

/**
 * Immutable presentation model for the About screen on Android.
 */
data class AboutScreenState(
    val appName: String = "OmniBuds",
    val appVersion: String = "1.0.0 (Phase 49)",
    val architectureSummary: String = "Kotlin Multiplatform core with native Android Bluetooth library boundary and Jetpack Compose UI.",
    val privacyPledge: String = "OmniBuds contains zero analytics, zero cloud telemetry, and never requests RECORD_AUDIO. All device controls and telemetry remain strictly local to your Android device.",
    val minSdk: Int = 26,
    val targetSdk: Int = 35,
    val compileSdk: Int = 35,
    val verifiedPlatforms: List<String> = listOf("Android 8.0+ (API 26..35)", "Linux x86_64", "macOS", "Windows"),
    val honestLimitations: List<String> = listOf(
        "Active Bluetooth audio codec cannot be observed without root/private APIs on Android.",
        "Third-party apps cannot force codec selection on public Android APIs.",
        "Earbuds battery telemetry requires vendor-specific protocol support; generic Android public APIs do not expose it.",
    ),
)
