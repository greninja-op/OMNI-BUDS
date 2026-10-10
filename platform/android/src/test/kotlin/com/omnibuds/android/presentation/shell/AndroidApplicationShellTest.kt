package com.omnibuds.android.presentation.shell

import com.omnibuds.android.presentation.devices.DevicesViewModel
import com.omnibuds.android.presentation.diagnostics.DiagnosticsViewModel
import com.omnibuds.android.presentation.integration.AndroidSurfaceCoordinator
import com.omnibuds.android.presentation.navigation.AndroidDestination
import com.omnibuds.android.presentation.navigation.AndroidNavigationCoordinator
import com.omnibuds.android.presentation.settings.SettingsViewModel
import com.omnibuds.android.presentation.theme.AndroidThemeMode
import com.omnibuds.android.presentation.theme.AndroidWindowSizeClass
import com.omnibuds.core.globalstate.GlobalDeviceStateRepository
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AndroidApplicationShellTest {

    private val testScope = TestScope()
    private val repo = GlobalDeviceStateRepository()
    private val navCoordinator = AndroidNavigationCoordinator()
    private val devicesVm = DevicesViewModel(scope = testScope)
    private val diagnosticsVm = DiagnosticsViewModel()
    private val settingsVm = SettingsViewModel(scope = testScope)
    private val surfaceCoordinator = AndroidSurfaceCoordinator(repository = repo)

    private fun createShell(widthDp: Int = 360): AndroidApplicationShell = AndroidApplicationShell(
        navigationCoordinator = navCoordinator,
        devicesViewModel = devicesVm,
        diagnosticsViewModel = diagnosticsVm,
        settingsViewModel = settingsVm,
        surfaceCoordinator = surfaceCoordinator,
        initialWindowWidthDp = widthDp,
    )

    @Test
    fun `initial destination is Devices and compact layout by default`() {
        val shell = createShell(widthDp = 390)
        val state = shell.shellState.value

        assertEquals(AndroidDestination.Devices, state.currentDestination)
        assertEquals(AndroidWindowSizeClass.COMPACT, state.windowSizeClass)
        assertFalse(state.useNavigationRail)
    }

    @Test
    fun `window width determines adaptive layout mode`() {
        val shell = createShell(widthDp = 360)
        assertFalse(shell.shellState.value.useNavigationRail)

        shell.updateWindowWidth(700)
        assertEquals(AndroidWindowSizeClass.MEDIUM, shell.shellState.value.windowSizeClass)
        assertTrue(shell.shellState.value.useNavigationRail)

        shell.updateWindowWidth(1024)
        assertEquals(AndroidWindowSizeClass.EXPANDED, shell.shellState.value.windowSizeClass)
        assertTrue(shell.shellState.value.useNavigationRail)
    }

    @Test
    fun `navigation forward and back gesture work predictably`() {
        val shell = createShell()

        shell.navigateTo(AndroidDestination.Settings)
        assertEquals(AndroidDestination.Settings, shell.shellState.value.currentDestination)

        shell.navigateTo(AndroidDestination.DeviceWorkspace("dev-123"))
        assertEquals(AndroidDestination.DeviceWorkspace("dev-123"), shell.shellState.value.currentDestination)

        val handled1 = shell.handleBackGesture()
        assertTrue(handled1)
        assertEquals(AndroidDestination.Settings, shell.shellState.value.currentDestination)

        val handled2 = shell.handleBackGesture()
        assertTrue(handled2)
        assertEquals(AndroidDestination.Devices, shell.shellState.value.currentDestination)

        // At root destination, back is not consumed
        val handled3 = shell.handleBackGesture()
        assertFalse(handled3)
        assertEquals(AndroidDestination.Devices, shell.shellState.value.currentDestination)
    }

    @Test
    fun `process recreation restores navigation state without fabricating connection`() = runTest {
        val shell = createShell()
        shell.navigateTo(AndroidDestination.DeviceWorkspace("bud-456"))
        devicesVm.selectDevice("bud-456")

        val savedBundle = shell.saveInstanceState()

        val newNavCoordinator = AndroidNavigationCoordinator()
        val newDevicesVm = DevicesViewModel(scope = testScope)
        val newShell = AndroidApplicationShell(
            navigationCoordinator = newNavCoordinator,
            devicesViewModel = newDevicesVm,
            diagnosticsViewModel = DiagnosticsViewModel(),
            settingsViewModel = settingsVm,
            surfaceCoordinator = surfaceCoordinator,
            initialWindowWidthDp = 360,
        )

        newShell.restoreInstanceState(savedBundle)

        assertEquals(AndroidDestination.DeviceWorkspace("bud-456"), newShell.shellState.value.currentDestination)
        assertEquals("bud-456", newDevicesVm.state.value.selectedDeviceId)
        // Discovered list remains empty until real observations arrive
        assertTrue(newDevicesVm.state.value.discoveredDevices.isEmpty())
    }
}
