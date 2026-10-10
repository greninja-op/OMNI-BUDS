package com.omnibuds.desktop.battery

import com.omnibuds.core.globalstate.BatteryState
import com.omnibuds.core.globalstate.ObservationProvenance
import com.omnibuds.core.globalstate.ObservedValue
import com.omnibuds.desktop.presentation.battery.BatteryComponentReading
import com.omnibuds.desktop.presentation.battery.BatteryPresentationModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BatteryPresentationTest {

    @Test
    fun unknownBatteryStaysExplicitlyUnavailableAndNeverFabricatesZeroPercent() {
        val model = BatteryPresentationModel.fromCoreState(BatteryState.Unknown)

        assertFalse(model.isAvailable)
        assertNull(model.overallPercent)
        assertNull(model.isCharging)
        assertTrue(model.components.isEmpty())
        assertNotNull(model.unavailableReason)
        // Ensure no synthetic 0%
        assertFalse(model.overallPercent == 0)
    }

    @Test
    fun reportedBatteryLevelIsFaithfullyRepresented() {
        val now = 1_000_000L
        val obs = ObservedValue(
            value = Unit,
            provenance = ObservationProvenance(
                sourceId = "battery-source",
                observedAtMillis = now,
                receivedAtMillis = now,
                sessionId = "sess-1",
                connectionGeneration = 1L,
                protocolVersion = "1.0",
            ),
        )

        val state = BatteryState.Known(
            levelPercent = 85,
            charging = true,
            observation = obs,
        )

        val model = BatteryPresentationModel.fromCoreState(state, currentEpochMillis = now + 5000L)

        assertTrue(model.isAvailable)
        assertEquals(85, model.overallPercent)
        assertEquals(true, model.isCharging)
        assertFalse(model.isStale)
        assertEquals(1, model.components.size)
        assertEquals(85, model.components[0].levelPercent)
        assertEquals("85%", model.components[0].displayString)
    }

    @Test
    fun staleBatteryDataIdentifiedWhenObservationExceedsThreshold() {
        val oldTimestamp = 1_000_000L
        val obs = ObservedValue(
            value = Unit,
            provenance = ObservationProvenance(
                sourceId = "battery-source",
                observedAtMillis = oldTimestamp,
                receivedAtMillis = oldTimestamp,
                sessionId = "sess-1",
                connectionGeneration = 1L,
                protocolVersion = "1.0",
            ),
        )

        val state = BatteryState.Known(
            levelPercent = 40,
            charging = false,
            observation = obs,
        )

        // 70 seconds later -> exceeds 60s threshold
        val model = BatteryPresentationModel.fromCoreState(
            batteryState = state,
            currentEpochMillis = oldTimestamp + 70_000L,
            staleThresholdMillis = 60_000L,
        )

        assertTrue(model.isAvailable)
        assertEquals(40, model.overallPercent)
        assertTrue(model.isStale)
    }

    @Test
    fun multiComponentReadingSupportsNullsAndMissingCase() {
        val reading1 = BatteryComponentReading(
            componentName = "Left Earbud",
            levelPercent = 90,
            isCharging = false,
        )
        val reading2 = BatteryComponentReading(
            componentName = "Right Earbud",
            levelPercent = null,
            isCharging = null,
        )

        assertEquals("90%", reading1.displayString)
        assertEquals("Unknown", reading2.displayString)
    }
}
