package com.omnibuds.android.hardware.capability

import com.omnibuds.android.ui.compose.model.AudioFormFactor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BatteryTopologyResolverTest {

    @Test
    fun `realme T300 keeps pogo-pin cached case topology`() {
        assertEquals(
            BatteryTopologyMode.POGO_PIN_CACHED_CASE,
            BatteryTopologyResolver.resolve("realme", "realme Buds T300", AudioFormFactor.STEM_IN_EAR),
        )
    }

    @Test
    fun `CMF Buds 1 hides the case completely`() {
        assertEquals(
            BatteryTopologyMode.SPLIT_LR_NO_CASE,
            BatteryTopologyResolver.resolve("CMF by Nothing", "CMF Buds", AudioFormFactor.STEM_IN_EAR),
        )
    }

    @Test
    fun `AirPods Pro 2 uses always-live three-way topology`() {
        assertEquals(
            BatteryTopologyMode.ALWAYS_LIVE_3_WAY,
            BatteryTopologyResolver.resolve("Apple", "AirPods Pro 2 (USB-C)", AudioFormFactor.STEM_IN_EAR),
        )
    }

    @Test
    fun `neckband and over-ear use single unit topology`() {
        assertEquals(
            BatteryTopologyMode.SINGLE_UNIT_HEADSET,
            BatteryTopologyResolver.resolve("Sony", "WH-1000XM5", AudioFormFactor.HEADSET_OVER_ON_EAR),
        )
        assertEquals(
            BatteryTopologyMode.SINGLE_UNIT_HEADSET,
            BatteryTopologyResolver.resolve("CMF by Nothing", "CMF Neckband Pro", AudioFormFactor.NECKBAND),
        )
    }

    @Test
    fun `unknown budget TWS falls back to dynamic combined`() {
        assertEquals(
            BatteryTopologyMode.DYNAMIC_COMBINED_FALLBACK,
            BatteryTopologyResolver.resolve("Acme", "SoundPods X9", AudioFormFactor.STEM_IN_EAR),
        )
    }
}
