package com.omnibuds.core.access

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phase 21: observation-engine tests.
 */
class ObservationEngineTest {

    private val deviceKey = "device-001"

    @Test
    fun `valid public metadata recorded`() {
        val engine = ObservationEngine()
        engine.record(
            DeviceObservation(
                observationId = "connection_state",
                deviceKey = deviceKey,
                propertyId = null,
                value = ObservationValue.EnumValue("connected", setOf("connected", "disconnected")),
                source = ObservationSource.ANDROID_PUBLIC_API,
                observedAtMillis = 1000L,
                freshness = ObservationFreshness.CURRENT,
                confidence = ObservationConfidence.HIGH,
                channel = "classic",
                sessionId = "s1",
                limitations = emptyList(),
            ),
        )
        val latest = engine.latest(deviceKey, "connection_state")
        assertEquals(ObservationFreshness.CURRENT, latest?.freshness)
    }

    @Test
    fun `unknown battery stays unknown not zero`() {
        val engine = ObservationEngine()
        engine.recordUnknown(
            deviceKey = deviceKey,
            observationId = "battery",
            propertyId = "battery_level",
            source = ObservationSource.ANDROID_PUBLIC_API,
            observedAtMillis = 1000L,
            limitation = "Android does not expose battery for this device",
        )
        val obs = engine.latest(deviceKey, "battery")
        // Unknown is null — never a fabricated 0%.
        assertNull(obs?.value)
        assertEquals(ObservationFreshness.UNAVAILABLE, obs?.freshness)
    }

    @Test
    fun `unknown ANC state stays unknown`() {
        val engine = ObservationEngine()
        engine.recordUnknown(
            deviceKey = deviceKey,
            observationId = "anc",
            propertyId = "anc_mode",
            source = ObservationSource.ANDROID_PUBLIC_API,
            observedAtMillis = 1000L,
            limitation = "ANC state not exposed by Android public APIs",
        )
        assertNull(engine.latest(deviceKey, "anc")?.value)
    }

    @Test
    fun `unknown codec state stays unknown`() {
        val engine = ObservationEngine()
        engine.recordUnknown(
            deviceKey = deviceKey,
            observationId = "codec",
            propertyId = "active_codec",
            source = ObservationSource.ANDROID_PUBLIC_API,
            observedAtMillis = 1000L,
            limitation = "codec not exposed",
        )
        assertNull(engine.latest(deviceKey, "codec")?.value)
    }

    @Test
    fun `stale observations marked on disconnect`() {
        val engine = ObservationEngine()
        engine.record(
            DeviceObservation(
                observationId = "connection_state", deviceKey = deviceKey,
                propertyId = null,
                value = ObservationValue.EnumValue("connected", setOf("connected")),
                source = ObservationSource.ANDROID_PUBLIC_API,
                observedAtMillis = 1000L,
                freshness = ObservationFreshness.CURRENT,
                confidence = ObservationConfidence.HIGH,
                channel = null, sessionId = null, limitations = emptyList(),
            ),
        )
        engine.markStale(deviceKey, "disconnected")
        val obs = engine.latest(deviceKey, "connection_state")
        assertEquals(ObservationFreshness.STALE, obs?.freshness)
        assertTrue(engine.current(deviceKey).isEmpty())
    }

    @Test
    fun `multiple devices isolated`() {
        val engine = ObservationEngine()
        engine.recordUnknown(deviceKey, "battery", "battery_level",
            ObservationSource.ANDROID_PUBLIC_API, 1000L, "unknown")
        engine.recordUnknown("device-002", "battery", "battery_level",
            ObservationSource.ANDROID_PUBLIC_API, 1000L, "unknown")
        // Staling one device does not affect the other.
        engine.markStale(deviceKey, "disconnected")
        assertEquals(
            ObservationFreshness.UNAVAILABLE,
            engine.latest("device-002", "battery")?.freshness,
        )
        assertEquals(
            ObservationFreshness.STALE,
            engine.latest(deviceKey, "battery")?.freshness,
        )
    }
}
