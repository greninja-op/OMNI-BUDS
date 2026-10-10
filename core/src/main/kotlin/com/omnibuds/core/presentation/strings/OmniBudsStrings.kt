package com.omnibuds.core.presentation.strings

/**
 * Authoritative user-facing string dictionary for OmniBuds.
 * Centralizes terminology to ensure consistent naming across Android and Desktop.
 */
object OmniBudsStrings {
    // App & Identity
    const val APP_NAME: String = "OmniBuds"
    const val APP_TAGLINE: String = "Universal Hardware Control for Bluetooth Audio Devices"
    const val CORE_PRINCIPLE: String = "OmniBuds never simulates a hardware capability. It discovers, verifies, and controls capabilities genuinely implemented by the connected device."
    const val PRIVACY_PLEDGE: String = "Zero network telemetry or cloud tracking. All configuration, logs, and diagnostic events remain strictly local to your device."

    // Navigation Destinations
    const val NAV_DEVICES: String = "Devices"
    const val NAV_WORKSPACE: String = "Workspace"
    const val NAV_DIAGNOSTICS: String = "Diagnostics"
    const val NAV_SETTINGS: String = "Settings"
    const val NAV_ABOUT: String = "About"

    // Hardware Controls & Features
    const val FEATURE_ANC: String = "Active Noise Cancellation"
    const val FEATURE_TRANSPARENCY: String = "Transparency Mode"
    const val FEATURE_NORMAL: String = "Normal (Off)"
    const val FEATURE_EQUALIZER: String = "Equalizer"
    const val FEATURE_SPATIAL_AUDIO: String = "Spatial Audio"
    const val FEATURE_HEAD_TRACKING: String = "Head Tracking"
    const val FEATURE_MULTIPOINT: String = "Multipoint Connection"
    const val FEATURE_WEAR_DETECTION: String = "In-Ear Detection"
    const val FEATURE_GESTURES: String = "Touch Controls"
    const val FEATURE_GAMING_MODE: String = "Low Latency (Gaming) Mode"
    const val FEATURE_SIDETONE: String = "Sidetone"
    const val FEATURE_VOICE_PROMPTS: String = "Voice Prompts"

    // Hardware Capability States
    const val CAPABILITY_UNSUPPORTED: String = "Unsupported by connected device"
    const val CAPABILITY_UNKNOWN: String = "Capability unknown or not verified"
    const val CAPABILITY_READ_ONLY: String = "Read-only capability"
    const val CAPABILITY_VOLATILE: String = "Session setting (resets on restart)"
    const val CAPABILITY_PERSISTENT: String = "Stored setting"
    const val CAPABILITY_PERSISTENCE_VERIFIED: String = "Hardware-verified persisted setting"

    // Operation Feedback
    const val OP_WORKING: String = "Working…"
    const val OP_SUCCEEDED: String = "Setting updated"
    const val OP_REJECTED: String = "Operation was rejected by device or access controller"
    const val OP_TIMED_OUT: String = "Operation timed out without device response"
    const val OP_AMBIGUOUS: String = "Connection dropped before hardware acknowledged change"
    const val OP_FAILED: String = "Operation failed"

    // Battery Presentation
    const val BATTERY_TITLE: String = "Battery Status"
    const val BATTERY_UNAVAILABLE: String = "Battery telemetry unavailable"
    const val BATTERY_STALE_BADGE: String = "Outdated"
    const val BATTERY_STALE_SUFFIX: String = "(stale)"

    // Audio & Codec Presentation
    const val AUDIO_TITLE: String = "Audio & Codec"
    const val CODEC_UNAVAILABLE_REASON: String = "Active codec is not observable on host platform public APIs without private/root privileges."
    const val CODEC_UNSELECTABLE_REASON: String = "Codec switching is controlled by host operating system."

    // Permission & Adapter Guidance
    const val ADAPTER_DISABLED_GUIDANCE: String = "Bluetooth radio is disabled. Please turn it on in system settings to scan or connect."
    const val ADAPTER_UNAVAILABLE_GUIDANCE: String = "No Bluetooth adapter detected on this host system."
    const val PERMISSION_REQUIRED_GUIDANCE: String = "Bluetooth permission is required. Please grant permission in system settings to discover nearby audio devices."
    const val PERMISSION_DENIED_GUIDANCE: String = "Bluetooth permission was denied. Please allow OmniBuds to access Bluetooth in system settings."
}
