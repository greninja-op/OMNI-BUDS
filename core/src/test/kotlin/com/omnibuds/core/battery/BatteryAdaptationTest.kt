package com.omnibuds.core.battery

import com.omnibuds.core.device.BatteryState as LegacyBatteryState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase 16: legacy adapter and conflict-resolver tests.
 */
class BatteryAdaptationTest {

    @Test
    fun `legacy state maps to per-component updates`() {
        val legacy = LegacyBatteryState(
            leftLevel = 72,
            rightLevel = 68,
            caseLevel = null,
            leftCharging = true,
            rightCharging = null,
            caseCharging = null,
        )
        val adaptation = LegacyBatteryStateAdapter.toUpdates(
            legacy = legacy,
            sessionGeneration = 0,
            observedAtMillis = 1000L,
            evidence = null,
            sourceName = "protocol",
        )
        // Left, right reported; case omitted entirely.
        assertEquals(2, adaptation.updates.size)
        val left = adaptation.updates.first { it.component == BatteryComponent.LEFT_EARBUD }
        assertEquals(72, (left.level as UpdateField.Set).value.percentage)
        assertEquals(ChargingState.CHARGING, (left.charging as UpdateField.Set).value)
        val right = adaptation.updates.first { it.component == BatteryComponent.RIGHT_EARBUD }
        assertEquals(68, (right.level as UpdateField.Set).value.percentage)
        assertTrue(right.charging is UpdateField.Omitted) // null → omitted, not NOT_CHARGING.
        assertTrue(adaptation.warnings.isEmpty())
    }

    @Test
    fun `legacy model rejects invalid values at construction`() {
        // The Phase 1 BatteryState validates in init; invalid values never
        // reach the adapter. The adapter's lenient path exists for platform
        // values that bypass the legacy model.
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException::class.java,
        ) {
            LegacyBatteryState(leftLevel = 150)
        }
        // BatteryLevel.parseLenient drops invalid platform values safely.
        assertEquals(null, BatteryLevel.parseLenient(150))
    }

    @Test
    fun `legacy charging false maps to not-charging`() {
        val legacy = LegacyBatteryState(leftLevel = 50, leftCharging = false)
        val adaptation = LegacyBatteryStateAdapter.toUpdates(
            legacy = legacy,
            sessionGeneration = 0,
            observedAtMillis = 1000L,
            evidence = null,
            sourceName = "protocol",
        )
        val left = adaptation.updates.first()
        assertEquals(ChargingState.NOT_CHARGING, (left.charging as UpdateField.Set).value)
    }

    @Test
    fun `legacy model never manufactures FULL`() {
        val legacy = LegacyBatteryState(leftLevel = 100, leftCharging = true)
        val adaptation = LegacyBatteryStateAdapter.toUpdates(
            legacy = legacy,
            sessionGeneration = 0,
            observedAtMillis = 1000L,
            evidence = null,
            sourceName = "protocol",
        )
        val left = adaptation.updates.first()
        // 100% + charging=true → CHARGING, never FULL (legacy has no FULL signal).
        assertEquals(ChargingState.CHARGING, (left.charging as UpdateField.Set).value)
    }

    @Test
    fun `conflict resolver prefers fresher observation`() {
        val old = ComponentBatteryState.unknown(BatteryComponent.LEFT_EARBUD).copy(
            level = BatteryLevel.of(50),
            observedAtMillis = 1000L,
            freshness = BatteryFreshness.STALE,
        )
        val fresh = ComponentBatteryState.unknown(BatteryComponent.LEFT_EARBUD).copy(
            level = BatteryLevel.of(72),
            observedAtMillis = 2000L,
            freshness = BatteryFreshness.CURRENT,
        )
        val resolution = BatteryConflictResolver.resolve(
            existing = old,
            incoming = fresh,
            incomingSourceRank = BatteryConflictResolver.SourceRank.ANDROID_PLATFORM,
            existingSourceRank = BatteryConflictResolver.SourceRank.VERIFIED_PROTOCOL,
        )
        // Fresher wins even against a higher-ranked stale source; conflict recorded.
        assertEquals(72, resolution.winner.level!!.percentage)
        assertTrue(resolution.warning != null)
    }

    @Test
    fun `conflict resolver keeps existing when incoming is older`() {
        val existing = ComponentBatteryState.unknown(BatteryComponent.LEFT_EARBUD).copy(
            level = BatteryLevel.of(72),
            observedAtMillis = 2000L,
            freshness = BatteryFreshness.CURRENT,
        )
        val stale = ComponentBatteryState.unknown(BatteryComponent.LEFT_EARBUD).copy(
            level = BatteryLevel.of(50),
            observedAtMillis = 1000L,
            freshness = BatteryFreshness.STALE,
        )
        val resolution = BatteryConflictResolver.resolve(
            existing = existing,
            incoming = stale,
            incomingSourceRank = BatteryConflictResolver.SourceRank.VERIFIED_PROTOCOL,
            existingSourceRank = BatteryConflictResolver.SourceRank.ANDROID_PLATFORM,
        )
        assertEquals(72, resolution.winner.level!!.percentage)
    }
}
