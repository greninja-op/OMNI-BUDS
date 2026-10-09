package com.omnibuds.core.globalstate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Phase 24: multi-device isolation tests.
 */
class MultiDeviceTest {

    @Test
    fun `two independent devices`() = runTest {
        val repo = GlobalDeviceStateRepository()
        repo.ingest(
            DeviceStateEvent.BatteryUpdated(
                deviceId = GlobalDeviceId("dev-1"), sessionId = null,
                sequence = 0, sourceId = "battery",
                battery = BatteryState.Known(50, false, null),
            ),
        )
        repo.ingest(
            DeviceStateEvent.BatteryUpdated(
                deviceId = GlobalDeviceId("dev-2"), sessionId = null,
                sequence = 0, sourceId = "battery",
                battery = BatteryState.Known(90, true, null),
            ),
        )
        val d1 = repo.getDevice(GlobalDeviceId("dev-1"))!!
        val d2 = repo.getDevice(GlobalDeviceId("dev-2"))!!
        assertEquals(50, (d1.battery as BatteryState.Known).levelPercent)
        assertEquals(90, (d2.battery as BatteryState.Known).levelPercent)
    }

    @Test
    fun `one disconnects while other stays connected`() = runTest {
        val repo = GlobalDeviceStateRepository()
        for (id in listOf("dev-1", "dev-2")) {
            repo.ingest(
                DeviceStateEvent.ConnectionChanged(
                    deviceId = GlobalDeviceId(id), sessionId = "s-$id",
                    sequence = 0, sourceId = "session",
                    connection = ConnectionState.Connected("s-$id", 1L, "rfcomm"),
                    generation = 1L,
                ),
            )
        }
        // dev-1 disconnects.
        repo.ingest(
            DeviceStateEvent.ConnectionChanged(
                deviceId = GlobalDeviceId("dev-1"), sessionId = null,
                sequence = 1, sourceId = "session",
                connection = ConnectionState.Disconnected,
                generation = 1L,
            ),
        )
        val d1 = repo.getDevice(GlobalDeviceId("dev-1"))!!
        val d2 = repo.getDevice(GlobalDeviceId("dev-2"))!!
        assertTrue(d1.connection is ConnectionState.Disconnected)
        assertTrue(d2.connection is ConnectionState.Connected)
        assertTrue(DerivedState.isConnected(d2))
        assertFalse(DerivedState.isConnected(d1))
    }

    @Test
    fun `no cross-device leakage`() = runTest {
        val repo = GlobalDeviceStateRepository()
        repo.ingest(
            DeviceStateEvent.FeatureObserved(
                deviceId = GlobalDeviceId("dev-1"), sessionId = null,
                sequence = 0, sourceId = "feature",
                featureId = "anc",
                value = ObservedValue(
                    "on",
                    ObservationProvenance("feature", 1L, 1L, null, null, null),
                    Freshness.CURRENT,
                ),
            ),
        )
        val d2 = repo.getDevice(GlobalDeviceId("dev-2"))
        assertNull(d2) // dev-2 never observed
        val d1 = repo.getDevice(GlobalDeviceId("dev-1"))!!
        assertTrue(d1.features.observed.containsKey("anc"))
    }

    @Test
    fun `removed device distinct from disconnected`() = runTest {
        val repo = GlobalDeviceStateRepository()
        repo.ingest(
            DeviceStateEvent.ConnectionChanged(
                deviceId = GlobalDeviceId("dev-1"), sessionId = "s-1",
                sequence = 0, sourceId = "session",
                connection = ConnectionState.Connected("s-1", 1L, "rfcomm"),
                generation = 1L,
            ),
        )
        repo.removeDeviceState(GlobalDeviceId("dev-1"), DeviceStateRemovalReason.USER_REMOVED)
        assertNull(repo.getDevice(GlobalDeviceId("dev-1")))
        assertTrue(repo.isRemoved(GlobalDeviceId("dev-1")))
        // Events for removed devices are rejected.
        val result = repo.ingest(
            DeviceStateEvent.BatteryUpdated(
                deviceId = GlobalDeviceId("dev-1"), sessionId = null,
                sequence = 1, sourceId = "battery",
                battery = BatteryState.Known(50, false, null),
            ),
        )
        assertTrue(result is IngestResult.Rejected)
    }
}
