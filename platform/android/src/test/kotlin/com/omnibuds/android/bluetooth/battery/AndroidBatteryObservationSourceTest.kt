package com.omnibuds.android.bluetooth.battery

import com.omnibuds.core.battery.RefreshResult
import com.omnibuds.core.device.DeviceIdentity
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase 16 §24I: Android battery adapter tests.
 * The platform exposes no public Bluetooth battery API; the adapter must
 * report that honestly rather than fabricate readings.
 */
class AndroidBatteryObservationSourceTest {

    private val device = DeviceIdentity(displayName = "Test Buds")

    @Test
    fun `source reports unsupported`() {
        val source = AndroidBatteryObservationSource()
        // No public API exists; the source must say so.
        assertTrue(!source.isSupported())
    }

    @Test
    fun `observe emits nothing`() = runTest {
        val source = AndroidBatteryObservationSource()
        val updates = source.observe(device).toList()
        assertTrue(updates.isEmpty())
    }

    @Test
    fun `refresh reports unsupported with reason`() = runTest {
        val source = AndroidBatteryObservationSource()
        val result = source.refresh(device)
        assertTrue(result is RefreshResult.Unsupported)
        val reason = (result as RefreshResult.Unsupported).reason
        assertTrue(reason.isNotBlank())
    }
}
