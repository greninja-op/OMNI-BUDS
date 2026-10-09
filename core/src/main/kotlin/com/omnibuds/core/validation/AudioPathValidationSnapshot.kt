package com.omnibuds.core.validation

import com.omnibuds.core.audio.AudioTransportKind
import com.omnibuds.core.audio.Codec
import com.omnibuds.core.device.DeviceIdentity
import com.omnibuds.core.quality.NegotiationState

/**
 * One coherent evaluation of a known set of observations.
 *
 * Phase 14 (OB-P14-REQ-014): immutable. Represents a single device and a
 * single session generation — never mixes devices or generations. The
 * [overallStatus] is derived by [ValidationAggregator]; it is stored here
 * so consumers never recompute it inconsistently.
 */
data class AudioPathValidationSnapshot(
    val snapshotId: String,
    val device: DeviceIdentity,
    val sessionGeneration: Long,
    val timestampMillis: Long,
    val observedTransport: AudioTransportKind,
    val routeActive: Boolean?,
    val codec: Codec,
    val negotiationState: NegotiationState,
    val freshnessSummary: String,
    val results: List<ValidationResult>,
    val overallStatus: ValidationStatus,
    val unevaluatedRules: List<String> = emptyList(),
    val evidenceReferences: List<String> = emptyList(),
    val limitations: List<String> = emptyList(),
) {
    init {
        require(snapshotId.isNotBlank()) { "snapshotId must identify the snapshot" }
        require(timestampMillis >= 0) { "timestampMillis must be non-negative" }
        require(results.isNotEmpty()) { "a snapshot must contain at least one result" }
    }
}
