package com.omnibuds.android.presentation.battery

import com.omnibuds.core.globalstate.BatteryState
import com.omnibuds.core.globalstate.ObservationProvenance
import com.omnibuds.core.globalstate.ObservedValue
import com.omnibuds.core.presentation.battery.BatteryComponentKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Iteration 2: per-component battery must flow from the core state to
 * the presentation model by kind, and the aggregate must never be
 * manufactured from components (or vice versa).
 */
class BatteryComponentsPresentationTest {

    private fun provenance(observedAt: Long) = ObservationProvenance(
        sourceId = "test",
        observedAtMillis = observedAt,
        receivedAtMillis = observedAt,
        sessionId = null,
        connectionGeneration = 1L,
        protocolVersion = null,
    )

    @Test
    fun `components map by kind and aggregate stays the aggregate`() {
        val state = BatteryState.Known(
            levelPercent = 70,
            charging = null,
            observation = ObservedValue(Unit, provenance(1_000L)),
            components = listOf(
                BatteryState.ComponentLevel(BatteryState.Component.LEFT_EARBUD, 80, false),
                BatteryState.ComponentLevel(BatteryState.Component.CHARGING_CASE, 60, true),
            ),
        )
        val model = BatteryPresentationModel.fromCoreState(state, currentEpochMillis = 2_000L)
        assertEquals(70, model.overallPercent)
        assertEquals(2, model.components.size)
        val left = model.components.first { it.kind == BatteryComponentKind.LEFT }
        val case = model.components.first { it.kind == BatteryComponentKind.CASE }
        assertEquals(80, left.levelPercent)
        assertEquals(60, case.levelPercent)
        assertEquals(true, case.isCharging)
    }

    @Test
    fun `components-only state has no invented aggregate`() {
        val state = BatteryState.Known(
            levelPercent = null,
            charging = null,
            observation = ObservedValue(Unit, provenance(1_000L)),
            components = listOf(
                BatteryState.ComponentLevel(BatteryState.Component.RIGHT_EARBUD, 55, null),
            ),
        )
        val model = BatteryPresentationModel.fromCoreState(state, currentEpochMillis = 2_000L)
        assertTrue(model.isAvailable)
        assertNull(model.overallPercent)
        assertEquals(55, model.components.single().levelPercent)
    }

    @Test
    fun `empty known state is unavailable with no zero substitution`() {
        val state = BatteryState.Known(
            levelPercent = null,
            charging = null,
            observation = null,
            components = emptyList(),
        )
        val model = BatteryPresentationModel.fromCoreState(state, currentEpochMillis = 2_000L)
        assertFalse(model.isAvailable)
        assertNull(model.overallPercent)
        assertTrue(model.components.isEmpty())
    }

    @Test
    fun `fromComponents no longer borrows the first component as overall`() {
        val model = BatteryPresentationModel.fromComponents(
            left = 80, right = 90, case = null,
            observedAt = 1_000L, currentEpochMillis = 2_000L,
        )
        assertNull(model.overallPercent)
        assertEquals(2, model.components.size)
        assertEquals(
            BatteryComponentKind.LEFT,
            model.components.first { it.componentName == "Left" }.kind,
        )
    }

    @Test
    fun `stale observation is flagged from provenance age`() {
        val state = BatteryState.Known(
            levelPercent = 40,
            charging = null,
            observation = ObservedValue(Unit, provenance(1_000L)),
        )
        val fresh = BatteryPresentationModel.fromCoreState(state, currentEpochMillis = 30_000L)
        val stale = BatteryPresentationModel.fromCoreState(state, currentEpochMillis = 1_000_000L)
        assertFalse(fresh.isStale)
        assertTrue(stale.isStale)
    }

    @Test
    fun `component level range is validated`() {
        var threw = false
        try {
            BatteryState.ComponentLevel(BatteryState.Component.LEFT_EARBUD, 140, null)
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }
}
