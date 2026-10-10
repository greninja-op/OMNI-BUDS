package com.omnibuds.desktop.accessibility

import com.omnibuds.core.platform.desktop.DesktopBluetoothAvailability
import com.omnibuds.desktop.presentation.devices.DevicesScreenState
import com.omnibuds.desktop.presentation.devices.DiscoveredDeviceItem
import com.omnibuds.desktop.shell.ApplicationShellState
import com.omnibuds.desktop.theme.DesktopColors
import com.omnibuds.desktop.ui.DesktopUiRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopAccessibilityTest {

    @Test
    fun devicesScreenProducesAccessibleHierarchyWithRolesAndLabels() {
        val state = DevicesScreenState(
            availability = DesktopBluetoothAvailability.AVAILABLE,
            isDiscovering = true,
            discoveredDevices = listOf(
                DiscoveredDeviceItem(
                    platformIdentifier = "dev-1",
                    name = "Buds Live",
                    safeLabel = "Buds Live",
                    bluetoothAddress = "AA:BB:CC:11:22:33",
                    observedTransports = emptySet(),
                    rssiDbm = -50,
                    isPaired = false,
                    isConnectedAtOsLevel = true,
                    observedAtEpochMillis = 1000L,
                ),
            ),
        )

        val rootNode = DesktopUiRenderer.renderDevicesScreen(state)

        assertEquals("screen-devices", rootNode.id)
        assertEquals(AccessibilityRole.REGION, rootNode.role)

        val titleNode = rootNode.findById("devices-title")
        assertNotNull(titleNode)
        assertEquals(AccessibilityRole.HEADING, titleNode.role)

        val toggleBtn = rootNode.findById("btn-discovery-toggle")
        assertNotNull(toggleBtn)
        assertEquals(AccessibilityRole.BUTTON, toggleBtn.role)
        assertEquals("Stop Discovery", toggleBtn.label)
        assertTrue(toggleBtn.state.isBusy)

        val deviceItem = rootNode.findById("device-item-dev-1")
        assertNotNull(deviceItem)
        assertEquals(AccessibilityRole.LIST_ITEM, deviceItem.role)
        assertTrue(deviceItem.label.contains("Buds Live"))
    }

    @Test
    fun keyboardFocusManagerTraversesFocusableElementsLinearly() {
        val state = DevicesScreenState(
            availability = DesktopBluetoothAvailability.AVAILABLE,
            isDiscovering = false,
            discoveredDevices = listOf(
                DiscoveredDeviceItem(
                    platformIdentifier = "dev-1",
                    name = "Buds Live",
                    safeLabel = "Buds Live",
                    bluetoothAddress = null,
                    observedTransports = emptySet(),
                    rssiDbm = null,
                    isPaired = false,
                    isConnectedAtOsLevel = false,
                    observedAtEpochMillis = null,
                ),
            ),
        )

        val tree = DesktopUiRenderer.renderDevicesScreen(state)
        val focusManager = DesktopFocusManager()

        // First Tab
        val firstId = focusManager.moveFocusNext(tree)
        assertEquals("btn-discovery-toggle", firstId)

        // Second Tab
        val secondId = focusManager.moveFocusNext(tree)
        assertEquals("device-item-dev-1", secondId)

        // Third Tab loops back to first
        val loopId = focusManager.moveFocusNext(tree)
        assertEquals("btn-discovery-toggle", loopId)

        // Shift+Tab moves back to second
        val prevId = focusManager.moveFocusPrevious(tree)
        assertEquals("device-item-dev-1", prevId)
    }

    @Test
    fun globalErrorRendersWithAlertRole() {
        val shellState = ApplicationShellState(
            globalError = "Critical platform fault",
        )
        val dummyContent = AccessibilityNode("dummy", "Dummy", AccessibilityRole.TEXT)
        val root = DesktopUiRenderer.renderShell(shellState, dummyContent)

        val errorNode = root.findById("shell-global-error")
        assertNotNull(errorNode)
        assertEquals(AccessibilityRole.ALERT, errorNode.role)
        assertTrue(errorNode.label.contains("Critical platform fault"))
    }

    @Test
    fun highContrastThemeMeetsAccessibilityContrastRules() {
        val hc = DesktopColors.HighContrast
        assertEquals("#000000", hc.background)
        assertEquals("#FFFFFF", hc.onBackground)
        assertEquals("#FFFFFF", hc.border)
        assertEquals("#FFFF00", hc.focusRing)
    }
}
