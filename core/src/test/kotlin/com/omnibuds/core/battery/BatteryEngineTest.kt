package com.omnibuds.core.battery

import com.omnibuds.core.device.DeviceIdentity
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase 16 §24: engine tests — partial updates, freshness, multi-device,
 * disconnect, stale sessions, no-inference rules.
 */
class BatteryEngineTest {

    private val deviceA = DeviceIdentity(displayName = "Buds A")
    private val deviceB = DeviceIdentity(displayName = "Buds B")

    private var now = 1_000_000L
    private fun engine() = BatteryEngine(clock = { now })

    private fun update(
        component: BatteryComponent,
        level: Int? = null,
        charging: ChargingState? = null,
        generation: Long = 0,
        levelMode: String = "set",
    ): BatteryUpdate {
        val levelField: UpdateField<BatteryLevel> = when {
            levelMode == "omitted" -> UpdateField.Omitted
            levelMode == "explicitUnknown" -> UpdateField.ExplicitUnknown
            level == null -> UpdateField.Omitted
            else -> UpdateField.Set(BatteryLevel.of(level)!!)
        }
        val chargingField: UpdateField<ChargingState> = when (charging) {
            null -> UpdateField.Omitted
            else -> UpdateField.Set(charging)
        }
        return BatteryUpdate(
            component = component,
            sessionGeneration = generation,
            level = levelField,
            charging = chargingField,
            observedAtMillis = now,
            evidence = null,
            sourceName = "test",
        )
    }

    @Test
    fun `initial state is unknown`() = runTest {
        val e = engine()
        val snapshot = e.observe(deviceA).value
        assertTrue(snapshot.isEntirelyUnknown)
        assertEquals(BatteryFreshness.UNKNOWN, snapshot.overallFreshness)
    }

    @Test
    fun `left update does not disturb right or case`() = runTest {
        val e = engine()
        e.applyUpdate(deviceA, update(BatteryComponent.LEFT_EARBUD, level = 72))
        e.applyUpdate(deviceA, update(BatteryComponent.RIGHT_EARBUD, level = 68))
        e.applyUpdate(deviceA, update(BatteryComponent.CHARGING_CASE, level = 91))
        // Now update only left; right and case must survive.
        e.applyUpdate(deviceA, update(BatteryComponent.LEFT_EARBUD, level = 70))

        val snapshot = e.latest(deviceA)!!
        assertEquals(70, snapshot.components[BatteryComponent.LEFT_EARBUD]!!.level!!.percentage)
        assertEquals(68, snapshot.components[BatteryComponent.RIGHT_EARBUD]!!.level!!.percentage)
        assertEquals(91, snapshot.components[BatteryComponent.CHARGING_CASE]!!.level!!.percentage)
    }

    @Test
    fun `omitted field differs from explicit unknown`() = runTest {
        val e = engine()
        e.applyUpdate(deviceA, update(BatteryComponent.LEFT_EARBUD, level = 72))
        // Omitted: level must survive.
        e.applyUpdate(
            deviceA,
            update(BatteryComponent.LEFT_EARBUD, levelMode = "omitted", charging = ChargingState.CHARGING),
        )
        assertEquals(72, e.latest(deviceA)!!.components[BatteryComponent.LEFT_EARBUD]!!.level!!.percentage)

        // Explicit unknown: level must be cleared.
        e.applyUpdate(deviceA, update(BatteryComponent.LEFT_EARBUD, levelMode = "explicitUnknown"))
        assertNull(e.latest(deviceA)!!.components[BatteryComponent.LEFT_EARBUD]!!.level)
    }

    @Test
    fun `explicit zero remains zero`() = runTest {
        val e = engine()
        e.applyUpdate(deviceA, update(BatteryComponent.HEADPHONES, level = 0))
        val level = e.latest(deviceA)!!.components[BatteryComponent.HEADPHONES]!!.level
        assertEquals(0, level!!.percentage) // Zero, not unknown.
    }

    @Test
    fun `stale session updates are rejected`() = runTest {
        val e = engine()
        e.applyUpdate(deviceA, update(BatteryComponent.LEFT_EARBUD, level = 72, generation = 5))
        // Older generation: rejected.
        val result = e.applyUpdate(deviceA, update(BatteryComponent.LEFT_EARBUD, level = 10, generation = 3))
        assertNull(result)
        assertEquals(72, e.latest(deviceA)!!.components[BatteryComponent.LEFT_EARBUD]!!.level!!.percentage)
    }

    @Test
    fun `disconnect preserves battery but invalidates charging`() = runTest {
        val e = engine()
        e.applyUpdate(
            deviceA,
            update(BatteryComponent.LEFT_EARBUD, level = 72, charging = ChargingState.CHARGING),
        )
        e.onDisconnect(deviceA)

        val snapshot = e.latest(deviceA)!!
        // Battery preserved (not zeroed), marked stale.
        assertEquals(72, snapshot.components[BatteryComponent.LEFT_EARBUD]!!.level!!.percentage)
        assertEquals(BatteryFreshness.STALE, snapshot.components[BatteryComponent.LEFT_EARBUD]!!.freshness)
        // Charging assumption invalidated.
        assertEquals(ChargingState.UNKNOWN, snapshot.components[BatteryComponent.LEFT_EARBUD]!!.charging)
    }

    @Test
    fun `devices are isolated`() = runTest {
        val e = engine()
        e.applyUpdate(deviceA, update(BatteryComponent.LEFT_EARBUD, level = 72))
        e.applyUpdate(deviceB, update(BatteryComponent.DEVICE, level = 64))

        // A updates; B unchanged.
        e.applyUpdate(deviceA, update(BatteryComponent.LEFT_EARBUD, level = 70))
        assertEquals(64, e.latest(deviceB)!!.components[BatteryComponent.DEVICE]!!.level!!.percentage)

        // A disconnects; B keeps reporting.
        e.onDisconnect(deviceA)
        assertEquals(BatteryFreshness.CURRENT, e.latest(deviceB)!!.components[BatteryComponent.DEVICE]!!.freshness)
    }

    @Test
    fun `duplicate updates are suppressed`() = runTest {
        val e = engine()
        val first = e.applyUpdate(deviceA, update(BatteryComponent.LEFT_EARBUD, level = 72))
        val second = e.applyUpdate(deviceA, update(BatteryComponent.LEFT_EARBUD, level = 72))
        assertTrue(first != null)
        assertNull(second) // No meaningful change.
    }

    @Test
    fun `charging state is independent of percentage`() = runTest {
        val e = engine()
        // 100% with unknown charging.
        e.applyUpdate(deviceA, update(BatteryComponent.HEADPHONES, level = 100))
        assertEquals(ChargingState.UNKNOWN, e.latest(deviceA)!!.components[BatteryComponent.HEADPHONES]!!.charging)
        // 0% with unknown charging.
        e.applyUpdate(deviceB, update(BatteryComponent.HEADPHONES, level = 0))
        assertEquals(ChargingState.UNKNOWN, e.latest(deviceB)!!.components[BatteryComponent.HEADPHONES]!!.charging)
        // Charging changes without percentage changing.
        e.applyUpdate(deviceA, update(BatteryComponent.HEADPHONES, charging = ChargingState.CHARGING))
        val c = e.latest(deviceA)!!.components[BatteryComponent.HEADPHONES]!!
        assertEquals(100, c.level!!.percentage)
        assertEquals(ChargingState.CHARGING, c.charging)
    }

    @Test
    fun `freshness policy ages observations`() = runTest {
        val e = BatteryEngine(clock = { now }, staleAfterMillis = 1000L)
        e.applyUpdate(deviceA, update(BatteryComponent.LEFT_EARBUD, level = 72))
        now += 2000L // Advance past the staleness threshold.
        e.applyFreshnessPolicy(deviceA)
        assertEquals(BatteryFreshness.STALE, e.latest(deviceA)!!.components[BatteryComponent.LEFT_EARBUD]!!.freshness)
    }
}
