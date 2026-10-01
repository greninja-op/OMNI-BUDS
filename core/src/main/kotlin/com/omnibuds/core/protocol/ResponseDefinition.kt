package com.omnibuds.core.protocol

/**
 * The shape a response to one command is expected to have, recorded as structure.
 *
 * PROTO-NOMAGIC-001 gives `ResponseDefinition` the job of holding "response shape and
 * error indications", and this is that element. It says *which named fields* an answer
 * is expected to carry; it says nothing about byte offsets, widths or encodings, which
 * belong to the parser that reads them and to the protocol record that documents them.
 * Keeping the shape declarative is what lets an unparseable answer be detected as
 * [com.omnibuds.core.common.OmniBudsErrorCategory.PROTOCOL_MISMATCH] instead of being
 * quietly defaulted (specs.md section 3 rule 5).
 *
 * A response is keyed to the command it answers, so [commandId] is a reference into
 * [ProtocolDefinition.commands], and a definition must not register a shape for a command
 * it does not define. Not every command has a response body: an acknowledgement-only
 * exchange is simply a command with no [ResponseDefinition], which is a legal absence
 * rather than a gap to fill in.
 *
 * [acceptsNotification] records whether the device may deliver this response
 * asynchronously (a GATT notification or an unsolicited indication) rather than only as
 * a direct reply. It is a structural fact about the protocol, not a subscription, and it
 * gates nothing by itself: an unexpected notification is still a
 * [ProtocolParser]'s problem to refuse.
 *
 * [fieldNames] are symbolic field identities — `level`, `charging`, `preset` — never byte
 * positions, and specs.md section 1.5 requires names that describe meaning.
 */
data class ResponseDefinition(
    /** The command whose answer this shape describes. */
    val commandId: String,

    /**
     * The field names the response is expected to carry, in declaration order.
     *
     * Empty is legal and means "no fields are expected". Duplicates are refused: two
     * fields with one name cannot be addressed distinctly by a parser or a mapping.
     */
    val fieldNames: List<String>,

    /** Whether the device may report this response as an asynchronous notification. */
    val acceptsNotification: Boolean,
) {

    init {
        require(commandId.isNotBlank()) {
            "commandId links this shape to the operation it answers; a blank one cannot"
        }
        require(fieldNames.none { it.isBlank() }) {
            "field names are how a parser addresses a value; blank entries name nothing"
        }
        require(fieldNames.distinct().size == fieldNames.size) {
            "fieldNames repeats a name, which would let one response carry two meanings for " +
                "one key: $fieldNames"
        }
    }

    /** Whether this shape expects a body at all, as opposed to an acknowledgement. */
    val expectsFields: Boolean
        get() = fieldNames.isNotEmpty()

    /** Whether [field] is part of the expected shape; anything else is unmodelled, not zero. */
    fun declaresField(field: String): Boolean = field in fieldNames
}
