package com.omnibuds.core.capability

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.platform.TimeProvider
import com.omnibuds.core.state.CapabilityState

/**
 * What one feature's read produced: the established record, its momentary availability, and the evidence
 * behind it. The engine assembles these into a [CapabilitySnapshot]; it does not invent any of the three.
 */
data class DiscoveryReading(
    val capability: FeatureCapability,
    val availability: CapabilityAvailability,
    val evidence: CapabilityEvidence,
)

/**
 * The read-only seam the discovery engine drives. It is the reason the engine can live at layer 2 without
 * importing the protocol layer at layer 4 (ADR-P8-005): the engine is *handed* a source, and whatever binds
 * a source to a real Phase 7 `ProtocolSession` is wired at layer 3/4, where depending down on `capability` is
 * legal.
 *
 * Everything here is a read. There is no write, no connect, no command with a side effect — discovery must
 * never change the device (§9, §19). A source answers about the features it can attempt and yields a typed
 * [OperationOutcome] per feature so a single failure is isolated (one feature's [OperationOutcome.Failure]
 * must not corrupt the others, §10). A source that cannot reach the device at all signals that by returning a
 * failing read for every feature — see [DiscoveryState.FAILED], which the engine derives from that, so
 * §15's SESSION_UNAVAILABLE and PROTOCOL_UNRESOLVED reach the snapshot through an existing
 * [OmniBudsErrorCategory] without this engine importing the session or protocol layers.
 */
interface CapabilityDiscoverySource {
    /** The features this device/protocol makes attemptable for discovery, in a stable order. */
    suspend fun attemptableFeatures(): List<FeatureId>

    /** Read what is known about one feature; failure is a [PartialFailure], never an unsupported verdict. */
    suspend fun read(feature: FeatureId): OperationOutcome<DiscoveryReading>
}

/**
 * The deterministic discovery coordinator: run the source's reads, fold their evidence into established
 * capability state, resolve availability and dependencies, and produce a [CapabilitySnapshot] that says how
 * complete the pass was — inventing nothing it did not observe (§9).
 *
 * Its guarantees, each tested:
 *  - **Read-only and non-connecting.** It calls only [CapabilityDiscoverySource.read]; there is no path to
 *    a write or an open here.
 *  - **Failure isolation.** A feature that fails to read is a [PartialFailure] and stays
 *    [CapabilityState.UNKNOWN]; unrelated successes are kept (§10).
 *  - **Partial ≠ complete ≠ failed.** Any failure with at least one success makes the pass
 *    [DiscoveryState.PARTIALLY_COMPLETE]; failures with *no* success at all are [DiscoveryState.FAILED] (the
 *    pass could not establish anything, which is not the same as a partial result), and only a clean run over
 *    conclusions is [DiscoveryState.COMPLETE] (§14).
 *  - **Cancellation.** A cancelled source read ends the pass [DiscoveryState.CANCELLED] with everything
 *    gathered so far preserved, nothing fabricated.
 *  - **Determinism.** Features are read in a sorted order and the snapshot's collections are ordered, so the
 *    same source yields the same snapshot.
 *  - **Malformed input cannot crash a pass.** A read whose record is about a different feature than the one
 *    asked — the kind of mislabelling an untrusted device response can produce — is recorded as a
 *    [PartialFailure] with a §15 category, never allowed to throw out of [discover] (§17).
 *  - **No silent downgrade, conflicts surfaced.** Readings fold through the [DeviceCapabilities] evidence
 *    ladder, so a weak record cannot overwrite a stronger one quietly; a feature reported at genuinely
 *    different states by two reads is recorded as an [UnresolvedConflict] carrying the kinds that disagreed,
 *    not resolved by dropping either (§8).
 */
class CapabilityDiscoveryEngine(
    private val source: CapabilityDiscoverySource,
    private val dependencies: List<CapabilityDependency> = emptyList(),
    private val time: TimeProvider? = null,
) {
    suspend fun discover(subjectRef: String?, protocolId: String?, protocolVersion: String?): CapabilitySnapshot {
        val features = source.attemptableFeatures().sortedBy { feature -> feature.qualifiedName }
        var capabilities = DeviceCapabilities.empty()
        val availability = LinkedHashMap<FeatureId, CapabilityAvailability>()
        val evidence = mutableListOf<CapabilityEvidence>()
        val failures = mutableListOf<PartialFailure>()
        // Every (state, kind) seen per feature, so a genuine multi-read disagreement is detectable while a
        // single read never fabricates a conflict out of itself.
        val observations = LinkedHashMap<FeatureId, MutableList<Pair<CapabilityState, EvidenceKind>>>()
        var establishedCount = 0

        for (feature in features) {
            when (val result = source.read(feature)) {
                is OperationOutcome.Success -> {
                    val reading = result.value
                    // A source that answers about feature B when asked about A is malformed (§17). Refuse the
                    // record rather than letting DeviceCapabilities.with throw and abort the whole pass.
                    if (reading.capability.feature != feature) {
                        failures += PartialFailure(feature, MALFORMED_RESPONSE_CATEGORY)
                        continue
                    }
                    // Fold through the ladder: an equal-or-weaker later read never overwrites a stronger
                    // established record, so no evidence is silently lost (§8).
                    capabilities = capabilities.mergedWith(DeviceCapabilities.empty().with(feature, reading.capability))
                    availability[feature] = reading.availability
                    evidence += reading.evidence
                    observations.getOrPut(feature) { mutableListOf() } += reading.capability.state to reading.evidence.kind
                    establishedCount++
                }

                is OperationOutcome.Failure -> failures += PartialFailure(feature, result.error.category)

                OperationOutcome.Cancelled -> {
                    // Whatever concluded before the cancel is kept; nothing after it is invented (§9, §14).
                    return snapshot(
                        subjectRef, protocolId, protocolVersion, DiscoveryState.CANCELLED,
                        capabilities, availability, evidence, failures, conflictsFrom(observations),
                    )
                }
            }
        }

        val report = DependencyValidator.validate(dependencies, capabilities)
        // A blocked feature is unavailable now, but never *unsupported* — its prerequisite is merely unresolved.
        for (feature in report.blockedFeatures) {
            availability[feature] = CapabilityAvailability.UNAVAILABLE
        }

        val completion = when {
            failures.isEmpty() -> DiscoveryState.COMPLETE
            establishedCount == 0 -> DiscoveryState.FAILED
            else -> DiscoveryState.PARTIALLY_COMPLETE
        }
        return snapshot(
            subjectRef, protocolId, protocolVersion, completion,
            capabilities, availability, evidence, failures, conflictsFrom(observations),
        )
    }

    /**
     * Features the source reported at two or more genuinely different states, each as a conflict carrying the
     * evidence kinds that disagreed. Determinism: disagreements are detected from sorted kinds, so the same
     * set of readings always yields the same [UnresolvedConflict] list.
     */
    private fun conflictsFrom(
        observations: Map<FeatureId, List<Pair<CapabilityState, EvidenceKind>>>,
    ): List<UnresolvedConflict> {
        val conflicts = mutableListOf<UnresolvedConflict>()
        for ((feature, seen) in observations) {
            val distinctStates = seen.map { it.first }.distinct()
            if (distinctStates.size >= 2) {
                val kinds = seen.map { it.second }.sortedBy { kind -> kind.ordinal }
                conflicts += UnresolvedConflict(feature, kinds)
            }
        }
        return conflicts
    }

    private fun snapshot(
        subjectRef: String?,
        protocolId: String?,
        protocolVersion: String?,
        completion: DiscoveryState,
        capabilities: DeviceCapabilities,
        availability: Map<FeatureId, CapabilityAvailability>,
        evidence: List<CapabilityEvidence>,
        failures: List<PartialFailure>,
        conflicts: List<UnresolvedConflict>,
    ): CapabilitySnapshot = CapabilitySnapshot(
        subjectRef = subjectRef,
        protocolId = protocolId,
        protocolVersion = protocolVersion,
        discoveredAtEpochMillis = time?.nowEpochMillis(),
        completion = completion,
        capabilities = capabilities,
        availability = availability,
        evidence = evidence.sortedWith(compareBy({ it.source }, { it.kind.ordinal })),
        partialFailures = failures.sortedBy { featureFailure -> featureFailure.feature.qualifiedName },
        unresolvedConflicts = conflicts.sortedBy { conflict -> conflict.feature.qualifiedName },
        schemaVersion = CapabilitySnapshot.SCHEMA_VERSION,
    )

    companion object {
        /**
         * The category §15's MALFORMED_CAPABILITY_RESPONSE maps onto (ADR-P8-004): a record that does not
         * describe the feature asked for is not a valid state of that feature. No new category is created.
         */
        val MALFORMED_RESPONSE_CATEGORY: OmniBudsErrorCategory = OmniBudsErrorCategory.INVALID_STATE
    }
}
