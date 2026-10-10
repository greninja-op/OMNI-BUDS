package com.omnibuds.android.presentation.settings

import com.omnibuds.android.presentation.diagnostics.DiagnosticsViewModel
import com.omnibuds.android.presentation.theme.AndroidThemeMode
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingsAndDiagnosticsTest {

    private val testScope = TestScope()

    private class InMemorySettingsStorage : AndroidSettingsStoragePort {
        val stringMap = mutableMapOf<String, String>()
        val boolMap = mutableMapOf<String, Boolean>()
        val intMap = mutableMapOf<String, Int>()

        override suspend fun readString(key: String, default: String): String = stringMap[key] ?: default
        override suspend fun readBoolean(key: String, default: Boolean): Boolean = boolMap[key] ?: default
        override suspend fun readInt(key: String, default: Int): Int = intMap[key] ?: default
        override suspend fun writeString(key: String, value: String) { stringMap[key] = value }
        override suspend fun writeBoolean(key: String, value: Boolean) { boolMap[key] = value }
        override suspend fun writeInt(key: String, value: Int) { intMap[key] = value }
    }

    @Test
    fun `settings updates persist to storage port off main thread`() = runTest {
        val storage = InMemorySettingsStorage()
        val vm = SettingsViewModel(scope = testScope, storage = storage)
        testScope.testScheduler.advanceUntilIdle()

        vm.setThemeMode(AndroidThemeMode.DARK)
        testScope.testScheduler.advanceUntilIdle()

        assertEquals(AndroidThemeMode.DARK, vm.state.value.themeMode)
        assertEquals("DARK", storage.stringMap["theme_mode"])

        vm.setHighContrast(true)
        testScope.testScheduler.advanceUntilIdle()

        assertTrue(vm.state.value.isHighContrast)
        assertEquals(true, storage.boolMap["high_contrast"])
    }

    @Test
    fun `discovery timeout is clamped within safe bounds`() = runTest {
        val storage = InMemorySettingsStorage()
        val vm = SettingsViewModel(scope = testScope, storage = storage)
        testScope.testScheduler.advanceUntilIdle()

        vm.setDiscoveryTimeout(2) // Below min of 5
        testScope.testScheduler.advanceUntilIdle()
        assertEquals(5, vm.state.value.discoveryTimeoutSeconds)

        vm.setDiscoveryTimeout(99) // Above max of 60
        testScope.testScheduler.advanceUntilIdle()
        assertEquals(60, vm.state.value.discoveryTimeoutSeconds)
    }

    @Test
    fun `diagnostics logs events with MAC address redaction`() {
        val vm = DiagnosticsViewModel()

        vm.recordEvent(
            category = "BLUETOOTH_CONNECT",
            message = "Connected to earbud at AA:BB:CC:DD:EE:FF successfully",
        )

        val event = vm.state.value.recentEvents.first()
        assertTrue(event.message.contains("AA:BB:CC:XX:XX:XX"))
        assertFalse(event.message.contains("DD:EE:FF"))
    }

    @Test
    fun `diagnostics bounds history to configured limit`() {
        val vm = DiagnosticsViewModel(maxEventHistory = 5)

        for (i in 1..10) {
            vm.recordEvent(category = "TEST", message = "Event $i")
        }

        assertEquals(5, vm.state.value.recentEvents.size)
        // Most recent event is Event 10
        assertEquals("Event 10", vm.state.value.recentEvents.first().message)
    }

    @Test
    fun `local diagnostic export generates safe formatted text`() {
        val vm = DiagnosticsViewModel()
        vm.recordEvent(category = "SECURITY", message = "Centralized authorization checked for dev-1")

        val export = vm.generateLocalExport()

        assertNotNull(export)
        assertTrue(export.contains("OmniBuds Diagnostics Export"))
        assertTrue(export.contains("Centralized authorization checked"))
        assertNotNull(vm.state.value.statusBanner)

        vm.clearExport()
        assertNull(vm.state.value.exportedData)
    }
}
