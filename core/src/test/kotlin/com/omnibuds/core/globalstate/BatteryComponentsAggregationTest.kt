package com.omnibuds.core.globalstate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Iteration 2: per-component battery readings propagate through the
 * live global-state pipeline unchanged, stay isolated per device, and
 * respect sequence ordering.
 */
class BatteryComponentsAggregationTest {

    private fun batteryEvent(
        device: String,
        sequence: Long,
        level: Int?,
        components: List<BatteryState.ComponentLevel>,
    ) = DeviceStateEvent.BatteryUpdated(
        deviceId = GlobalDeviceId(device), sessionId = null,
        sequence = sequence, sourceId = "test",
        battery = BatteryState.Known(level, null, null, components),
    )

    @Test
    fun `components survive aggregation`() = runTest {
        val agg = DeviceStateAggregator(GlobalDeviceId("dev-1"))
        val result = agg.ingest(
            batteryEvent(
                "dev-1", 0, 70,
                listOf(
                    BatteryState.ComponentLevel(BatteryState.Component.LEFT_EARBUD, 80, false),
                    BatteryState.ComponentLevel(BatteryState.Component.RIGHT_EARBUD, 75, false),
                    BatteryState.ComponentLevel(BatteryState.Component.CHARGING_CASE, 60, true),
                ),
            ),
        )
        assertTrue(result == IngestResult.Applied)
        val battery = agg.snapshot().battery as BatteryState.Known
        assertEquals(70, battery.levelPercent)
        assertEquals(3, battery.components.size)
        assertEquals(
            60,
            battery.components.first { it.component == BatteryState.Component.CHARGING_CASE }.levelPercent,
        )
    }

    @Test
    fun `stale sequence cannot overwrite newer components`() = runTest {
        val agg = DeviceStateAggregator(GlobalDeviceId("dev-1"))
        agg.ingest(
            batteryEvent(
                "dev-1", 5, 70,
                listOf(BatteryState.ComponentLevel(BatteryState.Component.LEFT_EARBUD, 80, null)),
            ),
        )
        agg.ingest(
            batteryEvent(
                "dev-1", 3, 10,
                listOf(BatteryState.ComponentLevel(BatteryState.Component.LEFT_EARBUD, 5, null)),
            ),
        )
        val battery = agg.snapshot().battery as BatteryState.Known
        assertEquals(80, battery.components.single().levelPercent)
    }

    @Test
    fun `components stay isolated between devices`() = runTest {
        val repo = GlobalDeviceStateRepository()
        repo.ingest(
            batteryEvent(
                "dev-a", 0, null,
                listOf(BatteryState.ComponentLevel(BatteryState.Component.LEFT_EARBUD, 91, null)),
            ),
        )
        repo.ingest(
            batteryEvent(
                "dev-b", 0, null,
                listOf(BatteryState.ComponentLevel(BatteryState.Component.LEFT_EARBUD, 33, null)),
            ),
        )
        val a = repo.getDevice(GlobalDeviceId("dev-a"))!!.battery as BatteryState.Known
        val b = repo.getDevice(GlobalDeviceId("dev-b"))!!.battery as BatteryState.Known
        assertEquals(91, a.components.single().levelPercent)
        assertEquals(33, b.components.single().levelPercent)
    }
}
