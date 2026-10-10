package com.omnibuds.desktop.ui

import com.omnibuds.desktop.accessibility.AccessibilityAction
import com.omnibuds.desktop.accessibility.AccessibilityNode
import com.omnibuds.desktop.accessibility.AccessibilityRole
import com.omnibuds.desktop.accessibility.AccessibilityState
import com.omnibuds.desktop.navigation.DesktopDestination
import com.omnibuds.desktop.presentation.about.AboutScreenState
import com.omnibuds.desktop.presentation.devices.DevicesScreenState
import com.omnibuds.desktop.presentation.diagnostics.DiagnosticsScreenState
import com.omnibuds.desktop.presentation.settings.SettingsScreenState
import com.omnibuds.desktop.presentation.workspace.WorkspaceScreenState
import com.omnibuds.desktop.shell.ApplicationShellState

/**
 * Pure-Kotlin UI component rendering boundary.
 * Maps presentation states into virtual component and accessibility trees
 * without requiring a running display server or native toolkit.
 */
object DesktopUiRenderer {

    /**
     * Renders the top-level application shell into an accessible component tree.
     */
    fun renderShell(
        state: ApplicationShellState,
        contentNode: AccessibilityNode,
    ): AccessibilityNode {
        val navItems = listOf(
            AccessibilityNode(
                id = "nav-devices",
                label = "Devices",
                role = AccessibilityRole.TAB,
                state = AccessibilityState(
                    isSelected = state.currentDestination is DesktopDestination.Devices,
                    isEnabled = true,
                ),
                supportedActions = setOf(AccessibilityAction.CLICK, AccessibilityAction.FOCUS),
            ),
            AccessibilityNode(
                id = "nav-diagnostics",
                label = "Diagnostics",
                role = AccessibilityRole.TAB,
                state = AccessibilityState(
                    isSelected = state.currentDestination is DesktopDestination.Diagnostics,
                    isEnabled = true,
                ),
                supportedActions = setOf(AccessibilityAction.CLICK, AccessibilityAction.FOCUS),
            ),
            AccessibilityNode(
                id = "nav-settings",
                label = "Settings",
                role = AccessibilityRole.TAB,
                state = AccessibilityState(
                    isSelected = state.currentDestination is DesktopDestination.Settings,
                    isEnabled = true,
                ),
                supportedActions = setOf(AccessibilityAction.CLICK, AccessibilityAction.FOCUS),
            ),
            AccessibilityNode(
                id = "nav-about",
                label = "About",
                role = AccessibilityRole.TAB,
                state = AccessibilityState(
                    isSelected = state.currentDestination is DesktopDestination.About,
                    isEnabled = true,
                ),
                supportedActions = setOf(AccessibilityAction.CLICK, AccessibilityAction.FOCUS),
            ),
        )

        val navBar = AccessibilityNode(
            id = "shell-navigation-rail",
            label = "Main Navigation",
            role = AccessibilityRole.REGION,
            children = navItems,
        )

        val children = mutableListOf(navBar)

        state.globalError?.let { err ->
            children.add(
                AccessibilityNode(
                    id = "shell-global-error",
                    label = "Global Error: $err",
                    role = AccessibilityRole.ALERT,
                    state = AccessibilityState(errorMessage = err),
                ),
            )
        }

        children.add(contentNode)

        return AccessibilityNode(
            id = "shell-root",
            label = state.windowState.title,
            role = AccessibilityRole.REGION,
            children = children,
        )
    }

    /**
     * Renders the Devices screen into an accessible component tree.
     */
    fun renderDevicesScreen(state: DevicesScreenState): AccessibilityNode {
        val headerNodes = mutableListOf<AccessibilityNode>()

        headerNodes.add(
            AccessibilityNode(
                id = "devices-title",
                label = "Bluetooth Devices",
                role = AccessibilityRole.HEADING,
            ),
        )

        headerNodes.add(
            AccessibilityNode(
                id = "adapter-status-indicator",
                label = "Adapter Status: ${state.availability.technicalName}",
                role = AccessibilityRole.STATUS,
                state = AccessibilityState(
                    valueDescription = state.availability.technicalName,
                ),
            ),
        )

        val discoveryButton = AccessibilityNode(
            id = "btn-discovery-toggle",
            label = if (state.isDiscovering) "Stop Discovery" else "Start Discovery",
            role = AccessibilityRole.BUTTON,
            state = AccessibilityState(
                isEnabled = state.availability.isUsable,
                isBusy = state.isDiscovering,
            ),
            hint = if (!state.availability.isUsable) state.permissionGuidance else null,
            supportedActions = setOf(AccessibilityAction.CLICK, AccessibilityAction.FOCUS),
        )
        headerNodes.add(discoveryButton)

        state.errorBanner?.let { banner ->
            headerNodes.add(
                AccessibilityNode(
                    id = "devices-error-banner",
                    label = banner,
                    role = AccessibilityRole.ALERT,
                    state = AccessibilityState(errorMessage = banner),
                ),
            )
        }

        val deviceNodes = mutableListOf<AccessibilityNode>()
        if (state.isEmptyList) {
            deviceNodes.add(
                AccessibilityNode(
                    id = "empty-devices-label",
                    label = if (state.isDiscovering) "Scanning for Bluetooth devices..." else "No devices found.",
                    role = AccessibilityRole.TEXT,
                ),
            )
        } else {
            for (dev in state.discoveredDevices) {
                val isSelected = dev.platformIdentifier == state.selectedDeviceIdentifier
                deviceNodes.add(
                    AccessibilityNode(
                        id = "device-item-${dev.platformIdentifier}",
                        label = "${dev.safeLabel} (${if (dev.isConnectedAtOsLevel) "Connected" else "Discovered"})",
                        role = AccessibilityRole.LIST_ITEM,
                        state = AccessibilityState(
                            isSelected = isSelected,
                            isEnabled = true,
                        ),
                        hint = "Select device to view workspace",
                        supportedActions = setOf(AccessibilityAction.CLICK, AccessibilityAction.FOCUS),
                    ),
                )
            }
        }

        val listNode = AccessibilityNode(
            id = "devices-list",
            label = "Discovered Devices",
            role = AccessibilityRole.LIST,
            children = deviceNodes,
        )

        return AccessibilityNode(
            id = "screen-devices",
            label = "Devices Screen",
            role = AccessibilityRole.REGION,
            children = headerNodes + listNode,
        )
    }

    /**
     * Renders the Device Workspace screen into an accessible component tree.
     */
    fun renderWorkspaceScreen(state: WorkspaceScreenState): AccessibilityNode {
        val children = mutableListOf<AccessibilityNode>()

        val overview = state.overview
        val title = overview?.displayName ?: "Device Workspace"

        children.add(
            AccessibilityNode(
                id = "workspace-heading",
                label = title,
                role = AccessibilityRole.HEADING,
            ),
        )

        if (overview != null) {
            val overviewFacts = mutableListOf<AccessibilityNode>()
            overviewFacts.add(
                AccessibilityNode(
                    id = "overview-identity",
                    label = "Identity: ${overview.manufacturer ?: "Unknown"} ${overview.model ?: ""} (Confidence: ${overview.identityConfidence})",
                    role = AccessibilityRole.TEXT,
                ),
            )
            overviewFacts.add(
                AccessibilityNode(
                    id = "overview-connection",
                    label = "Connection: ${if (overview.connectionSessionState.isConnectedAtOsLevel) "OS Connected" else "Disconnected"}",
                    role = AccessibilityRole.STATUS,
                ),
            )
            overviewFacts.add(
                AccessibilityNode(
                    id = "overview-controllable",
                    label = "Vendor Protocol: ${if (overview.connectionSessionState.isVendorControllable) "Active & Controllable" else "Unavailable"}",
                    role = AccessibilityRole.STATUS,
                ),
            )

            children.add(
                AccessibilityNode(
                    id = "workspace-overview-region",
                    label = "Device Overview",
                    role = AccessibilityRole.REGION,
                    children = overviewFacts,
                ),
            )
        }

        // Battery
        val batteryModel = state.battery
        val batteryLabel = if (batteryModel.isAvailable) {
            "Battery: ${batteryModel.overallPercent}% ${if (batteryModel.isCharging == true) "(Charging)" else ""}"
        } else {
            "Battery: Unavailable (${batteryModel.unavailableReason ?: "Unknown"})"
        }
        children.add(
            AccessibilityNode(
                id = "workspace-battery-indicator",
                label = batteryLabel,
                role = AccessibilityRole.STATUS,
                state = AccessibilityState(
                    valueDescription = batteryModel.overallPercent?.toString(),
                ),
            ),
        )

        // Audio & Codec
        val audioModel = state.audio
        val audioLabel = if (audioModel.hasObservedCodec) {
            "Active Codec: ${audioModel.activeCodecName}"
        } else {
            "Codec: ${audioModel.unavailableReason ?: "Not observable"}"
        }
        children.add(
            AccessibilityNode(
                id = "workspace-audio-indicator",
                label = audioLabel,
                role = AccessibilityRole.STATUS,
            ),
        )

        // Hardware Controls
        val controlNodes = mutableListOf<AccessibilityNode>()
        for (ctrl in state.controls) {
            val ctrlLabel = "${ctrl.displayName}: ${ctrl.observedValue ?: "Unknown"}"
            controlNodes.add(
                AccessibilityNode(
                    id = "control-${ctrl.featureId}",
                    label = ctrlLabel,
                    role = AccessibilityRole.BUTTON,
                    state = AccessibilityState(
                        isEnabled = ctrl.isActionable,
                        isBusy = ctrl.isPending,
                        valueDescription = ctrl.observedValue,
                        errorMessage = ctrl.rejectionOrFailureReason,
                    ),
                    hint = if (!ctrl.isActionable) ctrl.rejectionOrFailureReason else "Activate to change setting",
                    supportedActions = if (ctrl.isActionable) setOf(AccessibilityAction.CLICK, AccessibilityAction.FOCUS) else emptySet(),
                ),
            )
        }

        children.add(
            AccessibilityNode(
                id = "workspace-controls-region",
                label = "Hardware Controls",
                role = AccessibilityRole.REGION,
                children = controlNodes,
            ),
        )

        state.errorBanner?.let { err ->
            children.add(
                AccessibilityNode(
                    id = "workspace-error-banner",
                    label = err,
                    role = AccessibilityRole.ALERT,
                    state = AccessibilityState(errorMessage = err),
                ),
            )
        }

        return AccessibilityNode(
            id = "screen-workspace-${state.deviceIdentifier}",
            label = "Device Workspace: $title",
            role = AccessibilityRole.REGION,
            children = children,
        )
    }

    /**
     * Renders the Diagnostics screen into an accessible component tree.
     */
    fun renderDiagnosticsScreen(state: DiagnosticsScreenState): AccessibilityNode {
        val children = mutableListOf<AccessibilityNode>()

        children.add(
            AccessibilityNode(
                id = "diag-title",
                label = "Diagnostics Dashboard",
                role = AccessibilityRole.HEADING,
            ),
        )

        children.add(
            AccessibilityNode(
                id = "diag-system-info",
                label = "App v${state.applicationVersion} | OS: ${state.platformDescriptor.osName ?: "Unknown"} | Adapter: ${state.adapterAvailability.technicalName}",
                role = AccessibilityRole.TEXT,
            ),
        )

        val exportBtn = AccessibilityNode(
            id = "btn-export-diagnostics",
            label = "Export Sanitized JSON",
            role = AccessibilityRole.BUTTON,
            state = AccessibilityState(isEnabled = true, isBusy = state.isExporting),
            supportedActions = setOf(AccessibilityAction.CLICK, AccessibilityAction.FOCUS),
        )
        children.add(exportBtn)

        state.exportResult?.let { res ->
            children.add(
                AccessibilityNode(
                    id = "diag-export-status",
                    label = res,
                    role = AccessibilityRole.STATUS,
                ),
            )
        }

        val eventNodes = state.events.map { ev ->
            AccessibilityNode(
                id = "diag-event-${ev.sequence}",
                label = "[${ev.severity}] ${ev.category}: ${ev.message}",
                role = AccessibilityRole.LIST_ITEM,
            )
        }

        children.add(
            AccessibilityNode(
                id = "diag-event-list",
                label = "Recent Diagnostic Events (${state.events.size})",
                role = AccessibilityRole.LIST,
                children = eventNodes,
            ),
        )

        return AccessibilityNode(
            id = "screen-diagnostics",
            label = "Diagnostics",
            role = AccessibilityRole.REGION,
            children = children,
        )
    }

    /**
     * Renders the Settings screen into an accessible component tree.
     */
    fun renderSettingsScreen(state: SettingsScreenState): AccessibilityNode {
        val children = mutableListOf<AccessibilityNode>()

        children.add(
            AccessibilityNode(
                id = "settings-title",
                label = "Application Settings",
                role = AccessibilityRole.HEADING,
            ),
        )

        children.add(
            AccessibilityNode(
                id = "setting-theme",
                label = "Theme Mode: ${state.themeMode.name}",
                role = AccessibilityRole.BUTTON,
                supportedActions = setOf(AccessibilityAction.CLICK, AccessibilityAction.FOCUS),
            ),
        )

        children.add(
            AccessibilityNode(
                id = "setting-density",
                label = "UI Density: ${state.density.name}",
                role = AccessibilityRole.BUTTON,
                supportedActions = setOf(AccessibilityAction.CLICK, AccessibilityAction.FOCUS),
            ),
        )

        children.add(
            AccessibilityNode(
                id = "setting-timeout",
                label = "Discovery Timeout: ${state.discoveryTimeoutSeconds}s",
                role = AccessibilityRole.SLIDER,
                state = AccessibilityState(valueDescription = "${state.discoveryTimeoutSeconds} seconds"),
                supportedActions = setOf(AccessibilityAction.SET_VALUE, AccessibilityAction.FOCUS),
            ),
        )

        val saveBtn = AccessibilityNode(
            id = "btn-save-settings",
            label = "Save Settings",
            role = AccessibilityRole.BUTTON,
            state = AccessibilityState(isEnabled = state.isDirty),
            supportedActions = setOf(AccessibilityAction.CLICK, AccessibilityAction.FOCUS),
        )
        children.add(saveBtn)

        return AccessibilityNode(
            id = "screen-settings",
            label = "Settings",
            role = AccessibilityRole.REGION,
            children = children,
        )
    }

    /**
     * Renders the About screen into an accessible component tree.
     */
    fun renderAboutScreen(state: AboutScreenState): AccessibilityNode {
        val children = listOf(
            AccessibilityNode(
                id = "about-title",
                label = "${state.appName} v${state.appVersion}",
                role = AccessibilityRole.HEADING,
            ),
            AccessibilityNode(
                id = "about-phase",
                label = state.buildPhase,
                role = AccessibilityRole.TEXT,
            ),
            AccessibilityNode(
                id = "about-principle",
                label = "Core Principle: ${state.corePrinciple}",
                role = AccessibilityRole.TEXT,
            ),
            AccessibilityNode(
                id = "about-privacy",
                label = "Privacy Pledge: ${state.privacyPledge}",
                role = AccessibilityRole.TEXT,
            ),
            AccessibilityNode(
                id = "about-arch",
                label = state.architectureSummary,
                role = AccessibilityRole.TEXT,
            ),
        )

        return AccessibilityNode(
            id = "screen-about",
            label = "About OmniBuds",
            role = AccessibilityRole.REGION,
            children = children,
        )
    }
}
