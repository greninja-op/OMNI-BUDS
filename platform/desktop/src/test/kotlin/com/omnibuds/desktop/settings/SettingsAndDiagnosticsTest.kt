package com.omnibuds.desktop.settings

import com.omnibuds.core.diagnostics.DiagnosticCategory
import com.omnibuds.core.diagnostics.DiagnosticEvent
import com.omnibuds.core.diagnostics.DiagnosticSeverity
import com.omnibuds.core.diagnostics.DiagnosticStore
import com.omnibuds.core.platform.PlatformDescriptor
import com.omnibuds.core.platform.PlatformType
import com.omnibuds.desktop.SimulatedDesktopAdapter
import com.omnibuds.desktop.platform.DesktopPlatformStoragePort
import com.omnibuds.desktop.presentation.diagnostics.DiagnosticsViewModel
import com.omnibuds.desktop.presentation.settings.SettingsViewModel
import com.omnibuds.desktop.theme.ThemeMode
import com.omnibuds.desktop.theme.UiDensity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SettingsAndDiagnosticsTest {

    @Test
    fun settingsPersistenceAndRestorationSavesAndLoadsValues() = runTest {
        val storage = DesktopPlatformStoragePort()
        val vm = SettingsViewModel(storage, backgroundScope)
        testScheduler.runCurrent()

        vm.updateTheme(ThemeMode.LIGHT)
        vm.updateDensity(UiDensity.COMPACT)
        vm.updateDiscoveryTimeout(30)
        vm.updateAutoRefresh(true)
        vm.updateDiagnosticLimit(1024)
        assertTrue(vm.state.value.isDirty)

        vm.saveSettings()
        testScheduler.runCurrent()
        assertFalse(vm.state.value.isDirty)
        assertNotNull(vm.state.value.lastSavedEpochMillis)

        // New VM reading from same storage
        val vm2 = SettingsViewModel(storage, backgroundScope)
        testScheduler.runCurrent()
        assertEquals(ThemeMode.LIGHT, vm2.state.value.themeMode)
        assertEquals(UiDensity.COMPACT, vm2.state.value.density)
        assertEquals(30, vm2.state.value.discoveryTimeoutSeconds)
        assertTrue(vm2.state.value.autoRefreshDevices)
        assertEquals(1024, vm2.state.value.diagnosticRetentionLimit)
    }

    @Test
    fun corruptedPreferencesFallbackSafelyToClampedDefaults() = runTest {
        val storage = DesktopPlatformStoragePort(
            mapOf(
                SettingsViewModel.KEY_THEME to "CORRUPTED_THEME",
                SettingsViewModel.KEY_DISCOVERY_TIMEOUT to "99999", // Way beyond 120s limit
                SettingsViewModel.KEY_DIAG_LIMIT to "-50", // Negative
            ),
        )

        val vm = SettingsViewModel(storage, backgroundScope)
        testScheduler.runCurrent()
        // Corrupted theme -> fallback to DARK
        assertEquals(ThemeMode.DARK, vm.state.value.themeMode)
        // 99999 clamped to 120
        assertEquals(120, vm.state.value.discoveryTimeoutSeconds)
        // -50 clamped to 64
        assertEquals(64, vm.state.value.diagnosticRetentionLimit)
    }

    @Test
    fun diagnosticEventsRedactSensitiveAddressesAndSecrets() = runTest {
        val store = DiagnosticStore()
        val adapter = SimulatedDesktopAdapter()
        val descriptor = PlatformDescriptor(platformType = PlatformType.LINUX)
        val vm = DiagnosticsViewModel(store, adapter, descriptor, backgroundScope)
        testScheduler.runCurrent()

        store.record(
            DiagnosticEvent(
                timestampEpochMillis = 1000L,
                severity = DiagnosticSeverity.INFO,
                category = DiagnosticCategory.BLUETOOTH,
                message = "Connected to device with address 11:22:33:44:55:66 using token=secretKey123",
                operationId = "op-99",
                error = null,
            ),
        )

        vm.refreshState()
        testScheduler.runCurrent()
        val event = vm.state.value.events.first()

        assertFalse(event.message.contains("11:22:33:44:55:66"))
        assertFalse(event.message.contains("secretKey123"))
        assertTrue(event.message.contains("[REDACTED]"))
    }

    @Test
    fun exportSanitizedJsonProducesValidRedactedDocument() = runTest {
        val store = DiagnosticStore()
        val adapter = SimulatedDesktopAdapter()
        val descriptor = PlatformDescriptor(platformType = PlatformType.LINUX, osName = "Linux")
        val vm = DiagnosticsViewModel(store, adapter, descriptor, backgroundScope)
        testScheduler.runCurrent()

        store.record(
            DiagnosticEvent(
                timestampEpochMillis = 1000L,
                severity = DiagnosticSeverity.WARN,
                category = DiagnosticCategory.PROTOCOL,
                message = "Protocol handshake warning for AA:BB:CC:DD:EE:FF",
                operationId = "op-1",
                error = null,
            ),
        )

        vm.refreshState()
        testScheduler.runCurrent()
        val json = vm.exportSanitizedJson()

        assertTrue(json.contains("\"applicationVersion\": \"1.0.0\""))
        assertTrue(json.contains("\"events\": ["))
        assertFalse(json.contains("AA:BB:CC:DD:EE:FF"))
        assertTrue(json.contains("[REDACTED]"))
        assertNotNull(vm.state.value.exportResult)
    }
}
