package com.omnibuds.core.device

/**
 * Turns identity signals into a fingerprint and, through a registry, into a result.
 *
 * The engine is a pure function over its inputs: no coroutine, no clock read, no cache, no I/O
 * (ADR-P5-011). Determinism is therefore testable without a scheduler, which matters because the
 * prompt's own success criteria are about repeatability rather than speed.
 *
 * Three rules do the work:
 *
 *  - **A rule fires only on evidence it names.** Every [MatchCondition] must be satisfied by a
 *    *usable* signal of the kind it asks about; `UNKNOWN`, `UNAVAILABLE` and `INVALID` satisfy
 *    nothing, so an empty cache cannot help a rule and a rejected value cannot either.
 *  - **Survivors that disagree become ambiguity, never a winner.** Ordering by specificity, then
 *    rule version, then id decides *presentation* order only; two distinct manufacturers or models
 *    surviving ends the decision as [IdentificationResult.Ambiguous] (ADR-P5-008).
 *  - **Confidence is computed from the evidence, not copied from the rule.** A rule may ask for
 *    [IdentificationConfidence.HIGH], but the engine grants it only when two independent
 *    [IdentitySignalKind] families corroborate (ADR-P5-004), so a rule that overclaims is capped
 *    rather than believed.
 */
class IdentityEngine(private val registry: DeviceIdentityRegistry) {

    /**
     * The fingerprint these signals describe, in Phase 1's type.
     *
     * Only the dimensions with a producer are filled. Manufacturer data and characteristic UUIDs
     * stay empty - not because the device lacks them but because this phase cannot see them
     * (ADR-P5-007) - and [DeviceFingerprint.isEntirelyUnobserved] keeps that distinguishable from a
     * device nobody looked at. [DeviceFingerprint.transportCandidates] and
     * [DeviceFingerprint.protocolCandidates] stay empty too: a passive device-type read does not
     * establish which transport reaches a device, and prompt section 17 keeps protocol and transport
     * resolution ahead of this phase, so the device type is preserved as an [IdentitySignal] here
     * rather than promoted into a transport claim.
     */
    fun buildFingerprint(signals: List<IdentitySignal>): DeviceFingerprint {
        val usable = signals.filter { signal -> signal.isUsable }
        val serviceUuids = usable
            .filter { signal -> signal.kind == IdentitySignalKind.SERVICE_UUID }
            .mapNotNull { signal -> signal.rawValue?.uppercase() }
            .toSet()
        val deviceClass = usable
            .firstOrNull { signal -> signal.kind == IdentitySignalKind.DEVICE_CLASS }
            ?.rawValue?.toIntOrNull()

        return DeviceFingerprint(
            serviceUuids = serviceUuids,
            deviceClass = deviceClass,
        )
    }

    /** Identifies one device from its signals. Never throws: malformed input becomes a result. */
    fun identify(signals: List<IdentitySignal>): IdentificationResult {
        val evidence = signals.toList()
        val registryVersion = registry.registryVersion
        val ruleSetVersion = registry.ruleSetVersion

        // Rejected metadata is decided before anything else: a value that failed validation has its
        // text discarded, so the engine cannot match on it, and reporting it as "unknown device"
        // would fold a data-quality fact into an absence (prompt section 13, section 15).
        val rejectedKinds = signals
            .filter { signal -> signal.quality == SignalQuality.INVALID }
            .map { signal -> signal.kind }
            .distinct()
        if (rejectedKinds.isNotEmpty()) {
            return IdentificationResult.InvalidEvidence(
                rejectedKinds = rejectedKinds,
                evidence = evidence,
                registryVersion = registryVersion,
                ruleSetVersion = ruleSetVersion,
            )
        }

        // An empty registry is the shipped production state (ADR-P5-006): nothing to match against,
        // which is a recorded absence rather than a device resembling no rule.
        if (registry.rules.isEmpty()) {
            return IdentificationResult.Unknown(
                evidence = evidence,
                registryVersion = registryVersion,
                ruleSetVersion = ruleSetVersion,
            )
        }

        val usable = signals.filter { signal -> signal.isUsable }
        if (usable.isEmpty()) {
            // Signals were collected but none can carry a decision. This is not a negative about the
            // device: it is the engine refusing to read absence as evidence (prompt section 9).
            val missing = signals.map { signal -> signal.kind }.distinct()
            return IdentificationResult.InsufficientEvidence(
                missingKinds = missing,
                evidence = evidence,
                registryVersion = registryVersion,
                ruleSetVersion = ruleSetVersion,
            )
        }

        val survivors = registry.rules.filter { rule -> holds(rule, usable) }
        if (survivors.isEmpty()) {
            return IdentificationResult.Unknown(
                evidence = evidence,
                registryVersion = registryVersion,
                ruleSetVersion = ruleSetVersion,
            )
        }

        val distinctTargets = survivors
            .map { rule -> TargetKey(rule.manufacturer.canonicalId, rule.model?.modelId) }
            .distinct()

        if (distinctTargets.size > 1) {
            return IdentificationResult.Ambiguous(
                candidates = distinctTargets.map { target ->
                    val forTarget = survivors.filter { rule ->
                        TargetKey(rule.manufacturer.canonicalId, rule.model?.modelId) == target
                    }
                    IdentificationResult.Ambiguous.Candidate(
                        manufacturer = forTarget.first().manufacturer,
                        model = forTarget.first().model,
                        ruleIds = forTarget.map { rule -> rule.ruleId },
                    )
                },
                evidence = evidence,
                registryVersion = registryVersion,
                ruleSetVersion = ruleSetVersion,
            )
        }

        val winner = survivors.maxWithOrNull(
            compareBy<IdentificationRule> { rule -> rule.specificity }
                .thenBy { rule -> rule.ruleVersion }
                .thenBy { rule -> rule.ruleId },
        ) ?: survivors.first()

        val granted = cap(winner.confidence, corroboratingKinds(winner, usable))
        val matched = listOf(winner.ruleId)
        val limitations = winner.knownLimitations

        return when {
            winner.model != null && granted == IdentificationConfidence.HIGH ->
                IdentificationResult.Exact(
                    manufacturer = winner.manufacturer,
                    model = winner.model,
                    confidence = granted,
                    matchedRuleIds = matched,
                    evidence = evidence,
                    registryVersion = registryVersion,
                    ruleSetVersion = ruleSetVersion,
                    limitations = limitations,
                )

            winner.model != null -> IdentificationResult.Likely(
                manufacturer = winner.manufacturer,
                model = winner.model,
                confidence = granted,
                matchedRuleIds = matched,
                evidence = evidence,
                registryVersion = registryVersion,
                ruleSetVersion = ruleSetVersion,
                limitations = limitations,
            )

            else -> IdentificationResult.ManufacturerOnly(
                manufacturer = winner.manufacturer,
                confidence = granted,
                matchedRuleIds = matched,
                evidence = evidence,
                registryVersion = registryVersion,
                ruleSetVersion = ruleSetVersion,
                limitations = limitations,
            )
        }
    }

    /** True when every condition has a usable signal of its kind that satisfies it. */
    private fun holds(rule: IdentificationRule, usable: List<IdentitySignal>): Boolean =
        rule.conditions.all { condition -> satisfies(condition, usable) }

    private fun satisfies(condition: MatchCondition, usable: List<IdentitySignal>): Boolean {
        val candidates = usable.filter { signal -> signal.kind == condition.signalKind }
        return when (condition) {
            is MatchCondition.NormalizedNameEquals -> candidates.any { signal ->
                IdentityNormalizer.normalize(signal.rawValue) == condition.normalized
            }

            is MatchCondition.NormalizedNamePrefix -> candidates.any { signal ->
                IdentityNormalizer.normalize(signal.rawValue)?.startsWith(condition.normalized) == true
            }

            is MatchCondition.CachedServiceUuidEquals -> candidates.any { signal ->
                signal.rawValue?.uppercase() == condition.uuid.uppercase()
            }

            is MatchCondition.DeviceClassInRange -> candidates.mapNotNull { signal ->
                signal.rawValue?.toIntOrNull()
            }.any { value -> value in condition.min..condition.max }

            is MatchCondition.DeviceTypeEquals -> candidates.any { signal ->
                signal.rawValue.equals(condition.type, ignoreCase = true)
            }
        }
    }

    /**
     * The rung the evidence actually supports, which is at most what the rule asked for.
     * [IdentificationConfidence.HIGH] requires two independent kinds; a rule asking for it on one
     * kind lands at `MODERATE` instead, and the downgrade is reported rather than hidden.
     */
    private fun cap(asked: IdentificationConfidence, kinds: Int): IdentificationConfidence = when {
        asked.independentKindsRequired > 0 && kinds >= asked.independentKindsRequired -> asked
        kinds >= 2 -> IdentificationConfidence.HIGH
        kinds == 1 -> IdentificationConfidence.MODERATE
        else -> IdentificationConfidence.LOW
    }

    private fun corroboratingKinds(rule: IdentificationRule, usable: List<IdentitySignal>): Int =
        rule.conditions
            .filter { condition -> satisfies(condition, usable) }
            .map { condition -> condition.signalKind }
            .distinct()
            .size

    /** The comparison key that decides whether surviving rules actually disagree. */
    private data class TargetKey(val manufacturerId: String, val modelId: String?)
}
