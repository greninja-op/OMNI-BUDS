package com.omnibuds.core.presentation.components

/**
 * Standard component classifications across OmniBuds applications.
 */
enum class ComponentKind {
    PRIMARY_BUTTON,
    SECONDARY_BUTTON,
    STATUS_BADGE,
    DEVICE_CARD,
    HARDWARE_CONTROL_CARD,
    SEGMENTED_MODE_SELECTOR,
    BATTERY_CARD,
    AUDIO_CARD,
    EMPTY_STATE_VIEW,
    ERROR_BANNER_VIEW,
    NAVIGATION_CONTAINER,
}

/**
 * Specification for a reusable UI component in the OmniBuds design system.
 */
data class ComponentCatalogEntry(
    val kind: ComponentKind,
    val name: String,
    val purpose: String,
    val interactionStates: List<String>,
    val accessibilityRole: String,
    val behaviorOnUnknownData: String,
)

/**
 * Authoritative registry of all consolidated UI components.
 */
object ComponentCatalogSpec {

    val entries: List<ComponentCatalogEntry> = listOf(
        ComponentCatalogEntry(
            kind = ComponentKind.PRIMARY_BUTTON,
            name = "PrimaryButton",
            purpose = "Initiates primary actions (e.g. Scan, Connect, Save).",
            interactionStates = listOf("Idle", "Hover", "Pressed", "Focused", "Disabled", "Loading"),
            accessibilityRole = "BUTTON",
            behaviorOnUnknownData = "Disabled if required parameter is missing.",
        ),
        ComponentCatalogEntry(
            kind = ComponentKind.SECONDARY_BUTTON,
            name = "SecondaryButton",
            purpose = "Initiates secondary or non-destructive actions (e.g. Refresh, Clear).",
            interactionStates = listOf("Idle", "Hover", "Pressed", "Focused", "Disabled"),
            accessibilityRole = "BUTTON",
            behaviorOnUnknownData = "Disabled if action is unavailable.",
        ),
        ComponentCatalogEntry(
            kind = ComponentKind.STATUS_BADGE,
            name = "StatusBadge",
            purpose = "Glanceable display of hardware or connection status.",
            interactionStates = listOf("Static"),
            accessibilityRole = "STATUS",
            behaviorOnUnknownData = "Displays 'Unknown' with neutral gray badge.",
        ),
        ComponentCatalogEntry(
            kind = ComponentKind.DEVICE_CARD,
            name = "DeviceCard",
            purpose = "Displays discovered or paired device with identity, connection, and signal telemetry.",
            interactionStates = listOf("Idle", "Hover", "Focused", "Selected"),
            accessibilityRole = "LIST_ITEM",
            behaviorOnUnknownData = "Shows redacted address and fallback device icon if name is missing.",
        ),
        ComponentCatalogEntry(
            kind = ComponentKind.HARDWARE_CONTROL_CARD,
            name = "HardwareControlCard",
            purpose = "Provides toggles or option selectors for specific verified hardware features.",
            interactionStates = listOf("Idle", "Pending (In-flight)", "Disabled (Unsupported/Read-only)", "Error"),
            accessibilityRole = "REGION",
            behaviorOnUnknownData = "Disables control and displays capability limitation explanation.",
        ),
        ComponentCatalogEntry(
            kind = ComponentKind.SEGMENTED_MODE_SELECTOR,
            name = "SegmentedModeSelector",
            purpose = "Mutually-exclusive mode selection (ANC, Transparency, Normal).",
            interactionStates = listOf("Selected", "Unselected", "Pending", "Disabled"),
            accessibilityRole = "TAB_PANEL",
            behaviorOnUnknownData = "Highlights observed mode or shows no active selection if unknown.",
        ),
        ComponentCatalogEntry(
            kind = ComponentKind.BATTERY_CARD,
            name = "BatteryCard",
            purpose = "Surfaces honest multi-component battery telemetry (Left, Right, Case).",
            interactionStates = listOf("Normal", "Stale (>60s)", "Unavailable"),
            accessibilityRole = "REGION",
            behaviorOnUnknownData = "Displays 'Unknown' / honest telemetry unavailable note. Never displays 0%.",
        ),
        ComponentCatalogEntry(
            kind = ComponentKind.AUDIO_CARD,
            name = "AudioCard",
            purpose = "Surfaces audio path, observed codec, and platform codec limitations.",
            interactionStates = listOf("Normal", "Unavailable"),
            accessibilityRole = "REGION",
            behaviorOnUnknownData = "States clearly that active codec is unobservable without root/private APIs.",
        ),
        ComponentCatalogEntry(
            kind = ComponentKind.EMPTY_STATE_VIEW,
            name = "EmptyStateView",
            purpose = "Informs user when a list is empty and offers clear recovery action.",
            interactionStates = listOf("Static + Action"),
            accessibilityRole = "REGION",
            behaviorOnUnknownData = "Presents guidance prompt and primary action.",
        ),
        ComponentCatalogEntry(
            kind = ComponentKind.ERROR_BANNER_VIEW,
            name = "ErrorBannerView",
            purpose = "Surfaces recoverable or terminal error notices with retry action.",
            interactionStates = listOf("Visible", "Dismissible"),
            accessibilityRole = "ALERT",
            behaviorOnUnknownData = "Surfaces generic recoverable error message with option to dismiss.",
        ),
        ComponentCatalogEntry(
            kind = ComponentKind.NAVIGATION_CONTAINER,
            name = "NavigationContainer",
            purpose = "Top-level shell navigation container (rail, tabs, bottom bar).",
            interactionStates = listOf("Active", "Inactive", "Focused"),
            accessibilityRole = "REGION",
            behaviorOnUnknownData = "Falls back to default Devices destination.",
        ),
    )
}
