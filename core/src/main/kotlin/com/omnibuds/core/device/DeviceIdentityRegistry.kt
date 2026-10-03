package com.omnibuds.core.device

import com.omnibuds.core.state.VerificationLevel

/**
 * A manufacturer as the registry knows it: a canonical id, a display name, and the aliases that
 * evidence has to be normalised through to reach it.
 *
 * [canonicalId] is a slug (`"sony"`), stable and safe to key on; [displayName] is presentation only.
 * Aliases are stored already normalized through [IdentityNormalizer] — the alternative, normalizing
 * at match time, means a rule's behaviour depends on which code path asked, which is how a brand
 * comparison ends up case-sensitive in one caller and not in another.
 */
data class ManufacturerIdentity(
    val canonicalId: String,
    val displayName: String,
    val aliases: Set<String> = emptySet(),
) {
    init {
        require(canonicalId.isNotBlank() && canonicalId == canonicalId.trim().lowercase()) {
            "a canonical manufacturer id is a trimmed lower-case slug; blank identifies nothing"
        }
    }

    companion object {
        /** The registry entry for a device nobody has matched, and it names no manufacturer. */
        val UNKNOWN = ManufacturerIdentity(canonicalId = UNKNOWN_ID, displayName = UNKNOWN_ID)

        /** [UNKNOWN] carries this id so a printed result says "unidentified", not an empty string. */
        const val UNKNOWN_ID = "unidentified"
    }
}

/**
 * Where a rule's claim came from, because prompt §11 requires the citation and forbids inventing
 * device signatures.
 *
 * [ASSIGNED_INTERNALLY] is the shape a fabricated signature takes — someone needed a rule and wrote
 * one — so ADR-P5-006 makes it a construction error for a rule to carry it, and the rule's own
 * evidence tier is capped by whichever this is: a documented naming convention is
 * [VerificationLevel.IMPLEMENTED] at best until a device confirms it, never higher.
 */
enum class EvidenceSource(val maximumVerification: VerificationLevel) {
    /** A vendor's own published datasheet or naming convention, cited by URL and retrieval date. */
    VENDOR_PUBLICATION(VerificationLevel.IMPLEMENTED),

    /** A standards-body assignment (a SIG company identifier, an assigned UUID). */
    STANDARDS_ASSIGNMENT(VerificationLevel.IMPLEMENTED),

    /** Third-party documentation, weaker, and stated as such. */
    THIRD_PARTY_DOCUMENTATION(VerificationLevel.INFERRED),

    /** Confirmed by a real device, per device identity and TST-HW-003. Not reachable in Phase 5. */
    HARDWARE_OBSERVATION(VerificationLevel.HARDWARE_VERIFIED),

    /** Invented for the purpose of having a rule. Rejected by construction; see ADR-P5-006. */
    ASSIGNED_INTERNALLY(VerificationLevel.INFERRED),
}

/**
 * One documented condition a device's signals must satisfy for this rule to fire.
 *
 * Conditions are data rather than lambdas so a rule can be printed, audited and versioned — a rule
 * whose test is a closure cannot be reviewed by a reader deciding whether the evidence is real,
 * which is the whole point of prompt §11's field list.
 */
sealed interface MatchCondition {
    /** What this condition asks about, for the independence arithmetic in [IdentificationConfidence]. */
    val signalKind: IdentitySignalKind

    /** The normalized reported name equals this text exactly. No substring, no fuzzy match (ADR-P5-005). */
    data class NormalizedNameEquals(val normalized: String) : MatchCondition {
        override val signalKind: IdentitySignalKind get() = IdentitySignalKind.REPORTED_NAME
    }

    /** The normalized reported name begins with this prefix - the one pattern form allowed, for product families. */
    data class NormalizedNamePrefix(val normalized: String) : MatchCondition {
        override val signalKind: IdentitySignalKind get() = IdentitySignalKind.REPORTED_NAME
    }

    /** One of the cached service UUIDs equals this value. Empty caches never satisfy it (UNKNOWN, not false). */
    data class CachedServiceUuidEquals(val uuid: String) : MatchCondition {
        override val signalKind: IdentitySignalKind get() = IdentitySignalKind.SERVICE_UUID
    }

    /** The reported class-of-device falls in this range. Corroborating only; never a sole condition. */
    data class DeviceClassInRange(val min: Int, val max: Int) : MatchCondition {
        init {
            require(min <= max) { "an empty range matches nothing and reads like a typo, so it is refused" }
        }

        override val signalKind: IdentitySignalKind get() = IdentitySignalKind.DEVICE_CLASS
    }

    /** The device type equals this value. */
    data class DeviceTypeEquals(val type: String) : MatchCondition {
        override val signalKind: IdentitySignalKind get() = IdentitySignalKind.DEVICE_TYPE
    }
}

/**
 * One identification rule, with everything prompt §11 requires attached to it.
 *
 * A rule is a claim about a naming convention, not about a capability: nothing here may be read as
 * "this device supports ANC", and [maySelectProtocol] is false by construction because protocol
 * resolution is Phase 7's decision on a transport it can actually probe (ADR-P0-016, prompt §17's
 * "do not claim a device is fully supported because its model was identified").
 *
 * The construction guard is the interesting part: a rule whose [evidence] is
 * [EvidenceSource.ASSIGNED_INTERNALLY] refuses to exist, and a rule with no conditions would match
 * everything, so both are `require` failures rather than conventions. ADR-P5-006's empty production
 * registry is what that discipline buys.
 */
data class IdentificationRule(
    val ruleId: String,
    val manufacturer: ManufacturerIdentity,
    /** Null when the rule establishes a manufacturer only — the common case for passive evidence. */
    val model: ModelIdentity? = null,
    val conditions: List<MatchCondition>,
    val confidence: IdentificationConfidence,
    val ruleVersion: Int,
    val evidence: EvidenceSource,
    val evidenceReference: String,
    val knownLimitations: String,
) {
    init {
        require(ruleId.isNotBlank() && ruleVersion > 0) { "a rule needs an id and a positive version" }
        require(conditions.isNotEmpty()) { "a rule with no conditions matches every device" }
        require(evidence != EvidenceSource.ASSIGNED_INTERNALLY) {
            "a rule must cite external evidence; ASSIGNED_INTERNALLY is what an invented signature looks like"
        }
        require(evidenceReference.isNotBlank()) { "an evidence source without a reference is not a citation" }
        require(confidence != IdentificationConfidence.VERIFIED) {
            "VERIFIED requires hardware evidence this phase cannot produce (ADR-P5-004)"
        }
        require(knownLimitations.isNotBlank()) { "every rule states what it cannot establish" }
    }

    /** How many signal kinds this rule consumes, for the specificity ordering in the engine. */
    val specificity: Int
        get() = conditions.map { condition -> condition.signalKind }.distinct().size

    /** Protocol resolution is never a Phase 5 rule's consequence (prompt §5 names it as future work). */
    val maySelectProtocol: Boolean
        get() = false
}

/**
 * A model as the registry knows it: an identifier plus the manufacturer it belongs to.
 *
 * Kept separate from [com.omnibuds.core.device.DeviceIdentity.model], which is a *reported* value,
 * because a conclusion and an observation with the same field name is how one becomes the other
 * (ADR-P5-009's separation).
 */
data class ModelIdentity(
    val manufacturerId: String,
    val modelId: String,
    val displayName: String,
) {
    init {
        require(manufacturerId.isNotBlank() && modelId.isNotBlank()) {
            "a model identity names its manufacturer or it is only a model number"
        }
    }
}

/**
 * The versioned, immutable set of manufacturers and rules that identification reads from.
 *
 * **The production instance is empty**, and that is a decision with a precedent rather than an
 * absence of work: ADR-P1-013 ships `ProtocolRegistry` empty with a test asserting it, because
 * Phase 1 had discovered no protocols and prompt §53 forbids a fake that implies otherwise. Phase 5
 * has documented no device signatures for the same reason, and prompt §11 forbids adding models to
 * grow the registry. `PhaseFiveRegistryTest` asserts the emptiness and that no `src/main` source
 * declares a rule, so the first real rule has to arrive with a citation and a diff.
 *
 * [builtIn] takes rules as parameters rather than constructing a default list for exactly that
 * reason: an empty registry must be the explicit choice (`empty()`), never a fallback a caller
 * forgets to override.
 */
data class DeviceIdentityRegistry(
    val registryVersion: Int,
    val ruleSetVersion: Int,
    val manufacturers: List<ManufacturerIdentity>,
    val rules: List<IdentificationRule>,
) {
    init {
        require(registryVersion > 0 && ruleSetVersion > 0) { "versions are positive integers" }
        require(manufacturers.map { id -> id.canonicalId }.distinct().size == manufacturers.size) {
            "two manufacturers with one canonical id would make a rule's target ambiguous"
        }
        require(rules.map { rule -> rule.ruleId }.distinct().size == rules.size) {
            "duplicate rule ids cannot be versioned independently"
        }
    }

    /** Every rule for one canonical manufacturer, in declaration order. */
    fun rulesFor(manufacturerId: String): List<IdentificationRule> =
        rules.filter { rule -> rule.manufacturer.canonicalId == manufacturerId }

    fun manufacturer(canonicalId: String): ManufacturerIdentity? =
        manufacturers.firstOrNull { entry -> entry.canonicalId == canonicalId }

    companion object {
        /** The registry Phase 5 ships: no rules, and a test that fails if that stops being true. */
        fun empty(): DeviceIdentityRegistry = DeviceIdentityRegistry(
            registryVersion = INITIAL_VERSION,
            ruleSetVersion = INITIAL_VERSION,
            manufacturers = listOf(ManufacturerIdentity.UNKNOWN),
            rules = emptyList(),
        )

        /** A registry with rules supplied by the caller — tests, and the phase that lands evidence. */
        fun builtIn(
            registryVersion: Int = INITIAL_VERSION,
            ruleSetVersion: Int = INITIAL_VERSION,
            manufacturers: List<ManufacturerIdentity> = emptyList(),
            rules: List<IdentificationRule> = emptyList(),
        ): DeviceIdentityRegistry {
            require(manufacturers.none { entry -> entry.canonicalId == ManufacturerIdentity.UNKNOWN_ID }) {
                "the unknown manufacturer is built in and cannot be redeclared"
            }
            return DeviceIdentityRegistry(
                registryVersion = registryVersion,
                ruleSetVersion = ruleSetVersion,
                manufacturers = listOf(ManufacturerIdentity.UNKNOWN) + manufacturers,
                rules = rules,
            )
        }

        private const val INITIAL_VERSION = 1
    }
}
