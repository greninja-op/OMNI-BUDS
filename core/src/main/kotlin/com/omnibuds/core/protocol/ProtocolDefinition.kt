package com.omnibuds.core.protocol

import com.omnibuds.core.common.FeatureId
import com.omnibuds.core.state.VerificationLevel
import com.omnibuds.core.common.TransportKind

/**
 * Everything OmniBuds claims to know about one protocol, as one structural record.
 *
 * This is the replacement for scattered magic bytes that master section 52 demands:
 * `ProtocolDefinition`, `CommandDefinition`, `ResponseDefinition`, parser, encoder,
 * `CapabilityMapping` — the six elements PROTO-NOMAGIC-001 enumerates, gathered so that a
 * reviewer can find every protocol fact in one place and so that no control path can be
 * assembled ad hoc at a call site (PROTO-NOMAGIC-004). A vendor opcode that is not
 * reachable through a record here is, by construction, not something this codebase knows.
 *
 * Phase 1 supplies the *shape* of the record and no records. The facts come from the
 * protocol database of Phase 22, populated by the research ladder of
 * `protocol-governance.md` sections 8 and 9 — never from an example, a guess, or a
 * plausible-looking constant (PROTO-DOC-004, Phase 1 prompt sections 26 and 51).
 *
 * [vendor] is the field most often got wrong, so it is nullable rather than defaulted:
 * **null means a standard, non-vendor protocol** — a well-known profile or a documented
 * generic mechanism that belongs to no brand. `""` is refused, because an empty string is
 * a fabricated vendor name standing in for "nobody knows", and that is what null already
 * says honestly (specs.md section 2.2).
 *
 * [transport] names the channel the control operations travel over and must be a
 * determined kind. [TransportKind.UNKNOWN] is refused here: a protocol record that does
 * not know its own transport is not a record about a protocol, and any control path built
 * from it would be choosing a channel by guesswork, which PROTO-XPORT-001 forbids.
 * Absence of transport knowledge belongs in a [com.omnibuds.core.transport.TransportAvailability]
 * record, not in a protocol definition.
 *
 * [confidence] is the protocol's own rung on the evidence ladder and it gates what the
 * record may be used for, not how it is worded (PROTO-VERIFY-001, PROTO-DB-002):
 * `INFERRED` is research data that may be recorded and parsed, `IMPLEMENTED` means a code
 * path exists, `LAB_TESTED` is the floor a write may be attempted from
 * (`protocol-governance.md` section 8 step 7), and only `HARDWARE_VERIFIED` or above lets
 * this record back a per-device support claim.
 *
 * The three maps are copied on construction and every lookup and derived fact reads the
 * copy, so a caller who keeps mutating the map it passed in cannot add a command, a
 * response or a mapping to a definition that has already been built: [command], [response],
 * [mappingFor], [supportsWrites], [registeredCommandIds] and [mappedFeatures] are all
 * immune to that. The [commands], [responses] and [capabilityMappings] properties report the
 * content the record was *created* with, which is what equality compares; code that needs
 * the tamper-proof view uses the lookups. Cross-references are validated in [init]: a
 * dangling command id in a mapping or a response for an undefined command is a broken
 * fact, and it is refused rather than left to fail at a call site.
 */
data class ProtocolDefinition(
    /** Stable protocol family key, e.g. `example-vendor.test-protocol`. Identity, not a label. */
    val protocolId: String,

    /** Human-facing name for the protocol family; presentation only. */
    val displayName: String,

    /** The brand this record describes, or null for a standard, non-vendor protocol. */
    val vendor: String?,

    /** Which control channel the operations in this record travel over. Never unknown. */
    val transport: TransportKind,

    /** Protocol version or family as reported by research, or null when never established. */
    val version: String?,

    /** The operations this protocol defines, keyed by [CommandDefinition.id]. */
    val commands: Map<String, CommandDefinition>,

    /** Expected response shapes, keyed by the [ResponseDefinition.commandId] they answer. */
    val responses: Map<String, ResponseDefinition>,

    /** Which feature each operation reads or sets, keyed by [CapabilityMapping.feature]. */
    val capabilityMappings: Map<FeatureId, CapabilityMapping>,

    /** How well the whole protocol is understood; never reported above its evidence. */
    val confidence: VerificationLevel,
) {

    private val commandIndex: Map<String, CommandDefinition> = commands.toMutableMap()
    private val responseIndex: Map<String, ResponseDefinition> = responses.toMutableMap()
    private val mappingIndex: Map<FeatureId, CapabilityMapping> = capabilityMappings.toMutableMap()

    init {
        require(protocolId.isNotBlank()) { "protocolId is the key a record is looked up by" }
        require(displayName.isNotBlank()) { "displayName is presentation text and cannot be blank" }
        require(vendor == null || vendor.isNotBlank()) {
            "vendor is null for a standard non-vendor protocol; a blank string is a fabricated " +
                "vendor name, which is a different claim"
        }
        require(version == null || version.isNotBlank()) {
            "version is either reported or null; a blank version is not a reported one"
        }
        require(transport != TransportKind.UNKNOWN) {
            "protocol '$protocolId' must name the channel its operations use; " +
                "TransportKind.UNKNOWN is not a protocol fact"
        }

        for ((key, command) in commandIndex) {
            require(key == command.id) {
                "command registered under '$key' identifies itself as '${command.id}'; " +
                    "a definition whose key and content disagree cannot be looked up honestly"
            }
        }

        for ((key, response) in responseIndex) {
            require(key == response.commandId) {
                "response registered under '$key' answers '${response.commandId}'"
            }
            require(key in commandIndex) {
                "protocol '$protocolId' defines a response shape for '$key' but no such command"
            }
        }

        for ((key, mapping) in mappingIndex) {
            require(key == mapping.feature) {
                "mapping filed under ${key.qualifiedName} describes ${mapping.feature.qualifiedName}"
            }
            mapping.readCommandId?.let { readId ->
                require(readId in commandIndex) {
                    "${mapping.feature.qualifiedName} reads through '$readId', which protocol " +
                        "'$protocolId' does not define"
                }
            }
            mapping.writeCommandId?.let { writeId ->
                require(writeId in commandIndex) {
                    "${mapping.feature.qualifiedName} writes through '$writeId', which protocol " +
                        "'$protocolId' does not define"
                }
            }
        }
    }

    /** The command named [id], or null when this protocol does not define it. */
    fun command(id: String): CommandDefinition? = commandIndex[id]

    /** The expected response shape for [commandId], or null when none is defined. */
    fun response(commandId: String): ResponseDefinition? = responseIndex[commandId]

    /** The command ids this record defines, read from the copied index. */
    val registeredCommandIds: Set<String>
        get() = commandIndex.keys

    /** The features this record models at all, read from the copied index. */
    val mappedFeatures: Set<FeatureId>
        get() = mappingIndex.keys

    /**
     * How [feature] is modelled here, or null when it is unmodelled.
     *
     * A null answer licenses no conclusion about the device: the feature stays
     * [com.omnibuds.core.state.CapabilityState.UNKNOWN] (PROTO-VENDOR-003, master
     * section 53).
     */
    fun mappingFor(feature: FeatureId): CapabilityMapping? = mappingIndex[feature]

    /**
     * Whether any mapping in this record names a write command.
     *
     * A structural statement about the *protocol*, not a permission: it says this record
     * contains a write path, and nothing about whether a device has been established to
     * accept it. Read-only records report false, which is the ordinary state for a
     * protocol discovered so far, and [confidence] still gates whether the write path may
     * be attempted at all.
     */
    val supportsWrites: Boolean
        get() = mappingIndex.values.any { it.hasWriteCommand }
}
