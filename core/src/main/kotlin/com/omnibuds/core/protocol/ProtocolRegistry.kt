package com.omnibuds.core.protocol

import com.omnibuds.core.device.DeviceFingerprint

/**
 * The set of protocol records currently known, as an immutable value.
 *
 * **This starts empty, and staying empty is a Phase 1 deliverable.** The records that would
 * fill it are vendor protocol facts, and Phase 1 has discovered none of them: prompt
 * sections 2, 26, 51 and 53 forbid vendor protocols, vendor packet transmission and any
 * fake that claims they work. `ProtocolRegistryTest` asserts that the default value of this
 * type holds no record, that no lookup answers, and that no fingerprint yields a candidate.
 * Combined with the fact that this module ships no other registry — [definitions] defaults to
 * empty and there is no pre-populated instance to reach — that is the machine-checked
 * statement that no vendor protocol has been implemented here. Shipping one later means
 * registering it from a reviewed protocol record, not changing this default.
 *
 * **Where the records come from later.** Phase 22's protocol database
 * (`protocol-governance.md` section 9, master section 27, PROTO-DB-001) is the source:
 * manufacturer and model scope, fingerprint rules, transport, protocol plus version,
 * firmware compatibility, service and characteristic records with evidence, commands,
 * responses, parsers, encoders, capability mappings, persistence behavior, known
 * limitations and test status. Each row becomes a [ProtocolDefinition]; this type is the
 * in-memory index over those rows, not a place facts are invented. Research tooling that
 * produces the rows is the Phase 20 Protocol Laboratory and never part of the app
 * (PROTO-RESEARCH-005).
 *
 * **What [ProtocolDefinition.confidence] decides.** A record's confidence gates its use, not
 * its presence: `INFERRED` data may be stored and parsed but may not drive a control or be
 * sent to a user's device (SEC-RES-004); `LAB_TESTED` is the floor from which a write may
 * be attempted (`protocol-governance.md` section 8 step 7); only `HARDWARE_VERIFIED` or
 * `PERSISTENCE_VERIFIED` backs a per-device support claim (PROTO-VERIFY-002, PROTO-VERIFY-006).
 * A device with no matching record here simply has *no protocol entry*, and its
 * capabilities are `UNKNOWN` rather than weakly `INFERRED` (PROTO-DB-002).
 *
 * Instances are immutable and every operation returns a new value, so a registry is safe to
 * share and no component can quietly widen what the app believes about a device
 * (master section 51 forbids hidden global state; specs.md section 5.7 confines shared
 * mutable state to one named owner). There is deliberately no application-wide singleton
 * here: whoever owns the knowledge injects a [ProtocolRegistry] value.
 */
class ProtocolRegistry(definitions: Collection<ProtocolDefinition> = emptyList()) {

    private val byId: Map<String, ProtocolDefinition> = definitions.associateBy { it.protocolId }

    init {
        require(byId.size == definitions.size) {
            "a protocol id appears more than once in the given definitions; one id cannot carry " +
                "two contradictory records"
        }
    }

    /** Whether nothing is known yet — the expected Phase 1 state. */
    val isEmpty: Boolean
        get() = byId.isEmpty()

    /** How many protocol records are held. */
    val size: Int
        get() = byId.size

    /** Every protocol id on record. */
    val protocolIds: Set<String>
        get() = byId.keys

    /** The records themselves, ordered by id so a listing is reproducible. */
    val records: List<ProtocolDefinition>
        get() = byId.values.sortedBy { it.protocolId }

    /**
     * This registry plus [definition], as a new value; the receiver is untouched.
     *
     * Re-registering an id that already exists is refused rather than silently
     * overwriting: two records for one protocol id mean two contradictory beliefs about
     * the same family, and the honest resolution is a versioned id, not last-write-wins.
     */
    fun register(definition: ProtocolDefinition): ProtocolRegistry {
        require(definition.protocolId !in byId) {
            "protocol '${definition.protocolId}' is already registered; a corrected record needs " +
                "a distinct id or a new version, not a silent replacement"
        }
        val updated = byId.toMutableMap()
        updated[definition.protocolId] = definition
        return ProtocolRegistry(updated.values.toList())
    }

    /** This registry without the record named [protocolId], as a new value. */
    fun without(protocolId: String): ProtocolRegistry {
        if (protocolId !in byId) return this
        val remaining = byId.toMutableMap()
        remaining.remove(protocolId)
        return ProtocolRegistry(remaining.values.toList())
    }

    /** The record for [protocolId], or null when no record exists. */
    fun find(protocolId: String): ProtocolDefinition? = byId[protocolId]

    /**
     * Which records the evidence in [fingerprint] names, in the order the fingerprint
     * lists its candidates.
     *
     * Matching is exact-name equality with a declared `protocolCandidates` entry and
     * nothing more: no scoring, no prefix or fuzzy match, no guessing from a device name,
     * a class or a service. Any richer rule would be an invented identification policy
     * (PROTO-DOC-004, PROTO-RESEARCH-003), and PROTO-ID-001 already settled that a display
     * name is never identity.
     *
     * The result is a candidate list to be confirmed by reads, never a verdict
     * (PROTO-ID-003), and it is empty more often than not. An empty list means "no record
     * on hand names this evidence" — which is unknown, not unsupported: it is not evidence
     * that the device has no protocol (PROTO-DB-002, master section 53). With the empty
     * registry Phase 1 ships, this is empty for every input.
     */
    fun candidatesFor(fingerprint: DeviceFingerprint): List<ProtocolDefinition> =
        fingerprint.protocolCandidates
            .asSequence()
            .mapNotNull { candidate -> find(candidate) }
            .distinctBy { candidate -> candidate.protocolId }
            .toList()

    override fun equals(other: Any?): Boolean =
        other is ProtocolRegistry && other.byId == byId

    override fun hashCode(): Int = byId.hashCode()

    override fun toString(): String = if (isEmpty) {
        "ProtocolRegistry(no protocol records)"
    } else {
        "ProtocolRegistry(${byId.size}: ${byId.keys.sorted().joinToString()})"
    }

    companion object {
        /** A registry holding nothing: the state Phase 1 ships in. */
        fun empty(): ProtocolRegistry = EMPTY

        private val EMPTY: ProtocolRegistry = ProtocolRegistry()
    }
}
