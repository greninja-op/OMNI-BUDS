package com.omnibuds.desktop.shell

import com.omnibuds.core.diagnostics.DiagnosticStore
import com.omnibuds.core.globalstate.GlobalDeviceStateRepository
import com.omnibuds.core.platform.PlatformDescriptor
import com.omnibuds.core.platform.PlatformLifecycleState
import com.omnibuds.core.platform.PlatformType
import com.omnibuds.desktop.SimulatedDesktopAdapter
import com.omnibuds.desktop.navigation.DesktopDestination
import com.omnibuds.desktop.platform.DesktopPlatformLifecycleSource
import com.omnibuds.desktop.platform.DesktopPlatformStoragePort
import com.omnibuds.desktop.theme.ThemeMode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopApplicationShellTest {

    private fun createShell(scope: kotlinx.coroutines.CoroutineScope): DesktopApplicationShell {
        val adapter = SimulatedDesktopAdapter()
        val repo = GlobalDeviceStateRepository()
        val diag = DiagnosticStore()
        val storage = DesktopPlatformStoragePort()
        val lifecycle = DesktopPlatformLifecycleSource()
        val descriptor = PlatformDescriptor(platformType = PlatformType.LINUX, osName = "Linux")
        return DesktopApplicationShell(
            desktopAdapter = adapter,
            stateRepository = repo,
            diagnosticStore = diag,
            storagePort = storage,
            lifecycleSource = lifecycle,
            platformDescriptor = descriptor,
            scope = scope,
        )
    }

    @Test
    fun initialLaunchSetsDefaultDestinationAndWindowState() = runTest {
        val shell = createShell(backgroundScope)
        val state = shell.shellState.value

        assertEquals(DesktopDestination.Devices, state.currentDestination)
        assertFalse(state.canGoBack)
        assertTrue(state.isRunning)
        assertEquals(1024, state.windowState.width)
        assertEquals(720, state.windowState.height)
        assertNull(state.globalError)
    }

    @Test
    fun navigationBetweenSectionsUpdatesDestinationAndBackStack() = runTest {
        val shell = createShell(backgroundScope)

        shell.navigateTo(DesktopDestination.Diagnostics)
        assertEquals(DesktopDestination.Diagnostics, shell.shellState.value.currentDestination)
        assertTrue(shell.shellState.value.canGoBack)

        shell.navigateTo(DesktopDestination.Settings)
        assertEquals(DesktopDestination.Settings, shell.shellState.value.currentDestination)

        shell.navigateTo(DesktopDestination.About)
        assertEquals(DesktopDestination.About, shell.shellState.value.currentDestination)

        // Go back
        assertTrue(shell.goBack())
        assertEquals(DesktopDestination.Settings, shell.shellState.value.currentDestination)

        assertTrue(shell.goBack())
        assertEquals(DesktopDestination.Diagnostics, shell.shellState.value.currentDestination)

        assertTrue(shell.goBack())
        assertEquals(DesktopDestination.Devices, shell.shellState.value.currentDestination)
        assertFalse(shell.shellState.value.canGoBack)
    }

    @Test
    fun windowControllerManagesGeometryAndVisibility() = runTest {
        val shell = createShell(backgroundScope)

        shell.resizeWindow(1280, 800)
        assertEquals(1280, shell.shellState.value.windowState.width)
        assertEquals(800, shell.shellState.value.windowState.height)

        shell.windowController.toggleMaximize()
        testScheduler.runCurrent()
        assertTrue(shell.shellState.value.windowState.isMaximized)

        shell.windowController.minimize()
        testScheduler.runCurrent()
        assertTrue(shell.shellState.value.windowState.isMinimized)

        shell.windowController.restore()
        testScheduler.runCurrent()
        assertFalse(shell.shellState.value.windowState.isMinimized)
    }

    @Test
    fun globalKeyboardShortcutsNavigateCorrectly() = runTest {
        val shell = createShell(backgroundScope)

        assertTrue(shell.handleGlobalShortcut("2", isCtrl = true))
        assertEquals(DesktopDestination.Diagnostics, shell.shellState.value.currentDestination)

        assertTrue(shell.handleGlobalShortcut("3", isCtrl = true))
        assertEquals(DesktopDestination.Settings, shell.shellState.value.currentDestination)

        assertTrue(shell.handleGlobalShortcut("4", isCtrl = true))
        assertEquals(DesktopDestination.About, shell.shellState.value.currentDestination)

        assertTrue(shell.handleGlobalShortcut("1", isCtrl = true))
        assertEquals(DesktopDestination.Devices, shell.shellState.value.currentDestination)
    }

    @Test
    fun themePreferenceRestorationPropagatesToShell() = runTest {
        val shell = createShell(backgroundScope)

        shell.settingsViewModel.updateTheme(ThemeMode.LIGHT)
        shell.settingsViewModel.saveSettings()
        testScheduler.runCurrent()

        assertEquals(ThemeMode.LIGHT, shell.shellState.value.theme.mode)
        assertEquals("#FFFFFF", shell.shellState.value.theme.colors.surface)

        shell.settingsViewModel.updateTheme(ThemeMode.HIGH_CONTRAST)
        testScheduler.runCurrent()
        assertEquals(ThemeMode.HIGH_CONTRAST, shell.shellState.value.theme.mode)
        assertEquals("#000000", shell.shellState.value.theme.colors.background)
    }

    @Test
    fun globalErrorPresentationAndDismissal() = runTest {
        val shell = createShell(backgroundScope)

        shell.setGlobalError("Unexpected Bluetooth subsystem error")
        assertEquals("Unexpected Bluetooth subsystem error", shell.shellState.value.globalError)

        shell.clearGlobalError()
        assertNull(shell.shellState.value.globalError)
    }

    @Test
    fun applicationShutdownTransitionsLifecycle() = runTest {
        val shell = createShell(backgroundScope)

        shell.shutdown()
        assertEquals(PlatformLifecycleState.TERMINATING, shell.lifecycleSource.currentState)
        assertFalse(shell.shellState.value.isRunning)
    }
}
