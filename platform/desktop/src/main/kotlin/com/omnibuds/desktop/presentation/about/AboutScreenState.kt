package com.omnibuds.desktop.presentation.about

import com.omnibuds.core.platform.PlatformDescriptor

/**
 * Screen state for the About dialog/screen.
 */
data class AboutScreenState(
    val appName: String = "OmniBuds Desktop",
    val appVersion: String = "1.0.0",
    val buildPhase: String = "Phase 48 — Desktop Application",
    val platformDescriptor: PlatformDescriptor = PlatformDescriptor.unobserved(),
    val corePrinciple: String = "OmniBuds never simulates a hardware capability. It discovers, verifies, and controls capabilities genuinely implemented by the connected device.",
    val privacyPledge: String = "Zero network telemetry or cloud tracking. All configuration, logs, and diagnostic events remain strictly local to your machine.",
    val architectureSummary: String = "Built on Kotlin Multiplatform platform-independent core and desktop Bluetooth boundaries (BlueZ / CoreBluetooth / WinRT).",
)
