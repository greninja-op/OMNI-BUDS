package com.omnibuds.core.globalstate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Phase 24: event aggregation tests.
 */
class AggregationTest {

    private fun prov(source: String = "test") = ObservationProvenance(
        sourceId = source,
        observedAtMillis = 1000L,
        receivedAtMillis = 1001L,
        sessionId = "s-1",
        connectionGeneration = 1L,
        protocolVersion = null,
    )

    @Test
    fun `connection then protocol then capabilities`() = runTest {
        val agg = DeviceStateAggregator(GlobalDeviceId("dev-1"))
        val r1 = agg.ingest(
            DeviceStateEvent.ConnectionChanged(
                deviceId = GlobalDeviceId("dev-1"), sessionId = "s-1",
                sequence = 0, sourceId = "session",
                connection = ConnectionState.Connected("s-1", 1L, "rfcomm"),
                generation = 1L,
            ),
        )
        assertTrue(r1 == IngestResult.Applied)
        val r2 = agg.ingest(
            DeviceStateEvent.ProtocolResolved(
                deviceId = GlobalDeviceId("dev-1"), sessionId = "s-1",
                sequence = 1, sourceId = "protocol",
                protocol = ProtocolState.Resolved("p-1", "1.0", true),
            ),
        )
        assertTrue(r2 == IngestResult.Applied)
        val state = agg.snapshot()
        assertTrue(state.connection is ConnectionState.Connected)
        assertTrue((state.protocol as ProtocolState.Resolved).compatible)
    }

    @Test
    fun `duplicate events safe`() = runTest {
        val agg = DeviceStateAggregator(GlobalDeviceId("dev-1"))
        val event = DeviceStateEvent.BatteryUpdated(
            deviceId = GlobalDeviceId("dev-1"), sessionId = null,
            sequence = 0, sourceId = "battery",
            battery = BatteryState.Known(50, false, null),
        )
        assertTrue(agg.ingest(event) == IngestResult.Applied)
        assertTrue(agg.ingest(event) == IngestResult.Duplicate)
        val state = agg.snapshot()
        assertEquals(50, (state.battery as BatteryState.Known).levelPercent)
    }

    @Test
    fun `out-of-order events rejected`() = runTest {
        val agg = DeviceStateAggregator(GlobalDeviceId("dev-1"))
        agg.ingest(
            DeviceStateEvent.BatteryUpdated(
                deviceId = GlobalDeviceId("dev-1"), sessionId = null,
                sequence = 5, sourceId = "battery",
                battery = BatteryState.Known(50, false, null),
            ),
        )
        val result = agg.ingest(
            DeviceStateEvent.BatteryUpdated(
                deviceId = GlobalDeviceId("dev-1"), sessionId = null,
                sequence = 3, sourceId = "battery",
                battery = BatteryState.Known(90, false, null),
            ),
        )
        assertTrue(result is IngestResult.Rejected)
        // The newer state stands.
        assertEquals(50, (agg.snapshot().battery as BatteryState.Known).levelPercent)
    }

    @Test
    fun `late events from old session rejected`() = runTest {
        val agg = DeviceStateAggregator(GlobalDeviceId("dev-1"))
        // Session s-1 connects.
        agg.ingest(
            DeviceStateEvent.ConnectionChanged(
                deviceId = GlobalDeviceId("dev-1"), sessionId = "s-1",
                sequence = 0, sourceId = "session",
                connection = ConnectionState.Connected("s-1", 1L, "rfcomm"),
                generation = 1L,
            ),
        )
        // Session s-2 connects (new generation).
        agg.ingest(
            DeviceStateEvent.ConnectionChanged(
                deviceId = GlobalDeviceId("dev-1"), sessionId = "s-2",
                sequence = 1, sourceId = "session",
                connection = ConnectionState.Connected("s-2", 2L, "rfcomm"),
                generation = 2L,
            ),
        )
        // Late event from s-1.
        val result = agg.ingest(
            DeviceStateEvent.BatteryUpdated(
                deviceId = GlobalDeviceId("dev-1"), sessionId = "s-1",
                sequence = 2, sourceId = "battery",
                battery = BatteryState.Known(10, false, null),
            ),
        )
        assertTrue(result is IngestResult.Rejected)
        assertTrue((result as IngestResult.Rejected).reason is EventRejection.OldSession)
    }

    @Test
    fun `feature request then observation`() = runTest {
        val agg = DeviceStateAggregator(GlobalDeviceId("dev-1"))
        var seq = 0L
        agg.ingest(
            DeviceStateEvent.FeatureDesiredChanged(
                deviceId = GlobalDeviceId("dev-1"), sessionId = null,
                sequence = seq++, sourceId = "config",
                featureId = "anc", value = ObservedValue("on", prov("config"), Freshness.CURRENT),
            ),
        )
        agg.ingest(
            DeviceStateEvent.FeatureOperationChanged(
                deviceId = GlobalDeviceId("dev-1"), sessionId = null,
                sequence = seq++, sourceId = "feature",
                featureId = "anc", status = OperationStatus.Pending(1000L),
            ),
        )
        agg.ingest(
            DeviceStateEvent.FeatureObserved(
                deviceId = GlobalDeviceId("dev-1"), sessionId = null,
                sequence = seq++, sourceId = "feature",
                featureId = "anc", value = ObservedValue("on", prov("feature"), Freshness.CURRENT),
            ),
        )
        val state = agg.snapshot()
        assertEquals("on", state.features.desired["anc"]?.value)
        assertEquals("on", state.features.observed["anc"]?.value)
        assertTrue(state.features.executing["anc"] is OperationStatus.Pending)
    }

    @Test
    fun `wrong device rejected`() = runTest {
        val agg = DeviceStateAggregator(GlobalDeviceId("dev-1"))
        val result = agg.ingest(
            DeviceStateEvent.BatteryUpdated(
                deviceId = GlobalDeviceId("dev-2"), sessionId = null,
                sequence = 0, sourceId = "battery",
                battery = BatteryState.Known(50, false, null),
            ),
        )
        assertTrue(result is IngestResult.Rejected)
    }
}
