package com.omnibuds.core.battery

import com.omnibuds.core.device.DeviceIdentity
import kotlinx.coroutines.flow.Flow

/**
 * A source of battery observations.
 *
 * Phase 16 (OB-P16-REQ-010, OB-P16-REQ-011): sources are event-driven.
 * A source that cannot observe reports its limitation explicitly rather
 * than fabricating data.
 */
interface BatteryObservationSource {

    /** A human-readable source name for provenance. */
    val sourceName: String

    /**
     * Observe battery updates for a device.
     * Emits [BatteryUpdate]s as they arrive; never emits fabricated data.
     */
    fun observe(device: DeviceIdentity): Flow<BatteryUpdate>

    /**
     * Attempt a refresh where the source supports it.
     * @return [RefreshResult.Unsupported] when the source has no refresh
     * mechanism — this is a normal outcome, not an error.
     */
    suspend fun refresh(device: DeviceIdentity): RefreshResult
}

/** The outcome of a refresh attempt. */
sealed interface RefreshResult {
    /** A refresh was performed; updates flow through [BatteryObservationSource.observe]. */
    data object Refreshed : RefreshResult

    /** This source has no refresh mechanism. Normal, not an error. */
    data class Unsupported(val reason: String) : RefreshResult

    /** Refresh failed; the reason is recorded, never converted to zeros. */
    data class Failed(val reason: String) : RefreshResult
}
