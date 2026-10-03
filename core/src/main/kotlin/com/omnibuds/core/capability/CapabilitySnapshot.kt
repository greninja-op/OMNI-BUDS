package com.omnibuds.core.capability

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.common.OmniBudsErrorCategory

/**
 * The outcome of one discovery pass: what was established, what is available now, on what evidence, and what
 * did not resolve — as one immutable, deterministically-ordered value.
 *
 * It *wraps* Phase 1's [DeviceCapabilities] rather than replacing it (§13, ADR-P8-001): the support/access/
 * durability claims live in the [capabilities] container with its evidence-ladder merge, and the snapshot
 * adds the two dimensions that container does not carry — momentary [availability] and the [evidence] /
 * [partialFailures] / [unresolvedConflicts] that let partial discovery be honest. The completion state tells a
 * consumer whether "everything settled" or "some of it did" ([DiscoveryState.PARTIALLY_COMPLETE] is never read
 * as complete, §10/§14).
 *
 * [subjectRef] is an opaque reference to the device/session this describes, deliberately a `String?` and not a
 * `DeviceIdentity`: `capability` and `device` are the same layer (2) and a cross-area same-layer import is
 * forbidden by the dependency test, so the snapshot references the subject without importing its type. It is a
 * value, never persisted — §13 forbids a permanent device history, and this is a snapshot a caller holds, not
 * a store.
 */
data class CapabilitySnapshot(
    val subjectRef: String?,
    val protocolId: String?,
    val protocolVersion: String?,
    val discoveredAtEpochMillis: Long?,
    val completion: DiscoveryState,
    val capabilities: DeviceCapabilities,
    val availability: Map<FeatureId, CapabilityAvailability>,
    val evidence: List<CapabilityEvidence>,
    val partialFailures: List<PartialFailure>,
    val unresolvedConflicts: List<UnresolvedConflict>,
    val schemaVersion: Int,
) {
    init {
        require(schemaVersion >= 1) { "a snapshot carries a positive schema version for future migration" }
        // §14: complete ≠ partial. A pass is COMPLETE only if nothing failed to conclude; a pass that lost
        // at least one operation but kept the rest is PARTIALLY_COMPLETE, and the two are not interchangeable.
        if (completion == DiscoveryState.COMPLETE) {
            require(partialFailures.isEmpty()) {
                "a COMPLETE snapshot reports ${partialFailures.size} partial failure(s); that is " +
                    "PARTIALLY_COMPLETE, not COMPLETE (prompt section 14)"
            }
        }
        if (completion == DiscoveryState.PARTIALLY_COMPLETE) {
            require(partialFailures.isNotEmpty()) {
                "a PARTIALLY_COMPLETE snapshot with no failures should be COMPLETE (prompt section 14)"
            }
        }
    }

    /** Availability for [feature], defaulting to [CapabilityAvailability.UNKNOWN] when nothing read it. */
    fun availabilityOf(feature: FeatureId): CapabilityAvailability =
        availability[feature] ?: CapabilityAvailability.UNKNOWN

    /** Features that failed to conclude this pass — unknown, and *not* unsupported (§10). */
    val failedFeatures: Set<FeatureId>
        get() = partialFailures.map { it.feature }.toSet()

    companion object {
        /** The current snapshot schema version; bumped (never reused) if the shape changes. */
        const val SCHEMA_VERSION: Int = 1

        /** A pass that examined nothing: every feature unknown, nothing available, nothing failed. */
        fun empty(): CapabilitySnapshot = CapabilitySnapshot(
            subjectRef = null,
            protocolId = null,
            protocolVersion = null,
            discoveredAtEpochMillis = null,
            completion = DiscoveryState.NOT_STARTED,
            capabilities = DeviceCapabilities.empty(),
            availability = emptyMap(),
            evidence = emptyList(),
            partialFailures = emptyList(),
            unresolvedConflicts = emptyList(),
            schemaVersion = SCHEMA_VERSION,
        )
    }
}

/**
 * One operation that did not conclude — recorded so its feature stays [com.omnibuds.core.state.CapabilityState.UNKNOWN]
 * rather than being silently downgraded to unsupported (§10's "distinguish failed discovery from verified
 * unsupported"). [reason] is an existing [OmniBudsErrorCategory]; no raw exception, no packet bytes.
 */
data class PartialFailure(val feature: FeatureId, val reason: OmniBudsErrorCategory)

/**
 * Two evidence records that point at different capability states for one feature and could not be reconciled.
 * The conflict is surfaced, not resolved by strength (§8's "do not let weak evidence override explicit
 * contradictory evidence silently"). [kinds] is the set of evidence kinds that disagreed, sorted for determinism.
 */
data class UnresolvedConflict(val feature: FeatureId, val kinds: List<EvidenceKind>) {
    init {
        require(kinds.size >= 2) { "a conflict needs at least two disagreeing evidence kinds" }
    }
}
