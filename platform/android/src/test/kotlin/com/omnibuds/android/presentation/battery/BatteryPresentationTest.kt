package com.omnibuds.android.presentation.battery

import com.omnibuds.core.globalstate.BatteryState
import com.omnibuds.core.globalstate.ObservationProvenance
import com.omnibuds.core.globalstate.ObservedValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BatteryPresentationTest {

    @Test
    fun `unknown battery state preserves nullability and does not synthesize zero percent`() {
        val model = BatteryPresentationModel.fromCoreState(BatteryState.Unknown)

        assertFalse(model.isAvailable)
        assertFalse(model.hasAnyData)
        assertNull(model.overallPercent)
        assertTrue(model.components.isEmpty())
        assertTrue(model.summaryText.contains("unavailable") || model.summaryText.contains("No public Android API"))
    }

    @Test
    fun `known battery observation renders correctly`() {
        val now = 1_000_000L
        val batteryState = BatteryState.Known(
            levelPercent = 85,
            charging = true,
            observation = ObservedValue(
                value = Unit,
                provenance = ObservationProvenance(
                    sourceId = "vendor_gatt",
                    observedAtMillis = now,
                    receivedAtMillis = now,
                    sessionId = null,
                    connectionGeneration = null,
                    protocolVersion = null,
                ),
            ),
        )

        val model = BatteryPresentationModel.fromCoreState(batteryState, currentEpochMillis = now + 5000L)

        assertTrue(model.isAvailable)
        assertTrue(model.hasAnyData)
        assertEquals(85, model.overallPercent)
        assertEquals(true, model.isCharging)
        assertFalse(model.isStale)
        assertTrue(model.summaryText.contains("85%"))
    }

    @Test
    fun `component battery values render with charging icons`() {
        val now = 2_000_000L
        val model = BatteryPresentationModel.fromComponents(
            left = 90,
            right = 85,
            case = 100,
            leftCharging = false,
            rightCharging = false,
            caseCharging = true,
            observedAt = now,
            currentEpochMillis = now + 1000L,
        )

        assertTrue(model.isAvailable)
        assertEquals(3, model.components.size)
        assertTrue(model.summaryText.contains("Left: 90%"))
        assertTrue(model.summaryText.contains("Case: 100% ⚡"))
        assertTrue(model.talkBackDescription.contains("Left 90 percent"))
    }

    @Test
    fun `stale reading after sixty seconds is flagged as stale`() {
        val observedAt = 100_000L
        val currentEpoch = observedAt + 65_000L // 65s later

        val model = BatteryPresentationModel.fromComponents(
            left = 50,
            right = 50,
            case = null,
            observedAt = observedAt,
            currentEpochMillis = currentEpoch,
        )

        assertTrue(model.isStale)
        assertTrue(model.summaryText.contains("stale"))
        assertTrue(model.talkBackDescription.contains("outdated"))
    }
}
