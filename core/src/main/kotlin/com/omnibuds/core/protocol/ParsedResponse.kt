package com.omnibuds.core.protocol

import com.omnibuds.core.config.ConfigurationValue

/**
 * What a [ProtocolParser] actually recognised in a response, field by field.
 *
 * **A field that is absent means the device did not report it, and that is the end of
 * the story.** Lookup returns null and the caller leaves the corresponding state unknown.
 * It is never `0`, never `false`, never `""`, and never a plausible default, because a
 * defaulted field is how "we could not tell" becomes a number on screen
 * (specs.md section 2.2 tiering, master section 53, ADR-P0-016). Contrast that with a
 * field that *is* present holding `ConfigurationValue.IntValue(0)` or
 * `BooleanValue(false)`: those are real readings — a flat battery reports `0` — and this
 * type preserves them exactly.
 *
 * Only fields the parser recognised and that the value admits appear here at all. A
 * response is not a bag of bytes: whatever could not be tied to a named field is a
 * [ProtocolMismatch] from the parser rather than a missing entry here, so the presence of
 * this value always means "these fields, and only these, were understood".
 *
 * [fields] is copied for lookup so a caller who built this from a mutable map cannot edit
 * a parsed answer afterwards; equality is by content.
 */
data class ParsedResponse(
    /** The command this answer belongs to, carried so a late answer can be correlated. */
    val commandId: String,

    /** The recognised fields. An entry is a report; a missing key is an absence of one. */
    val fields: Map<String, ConfigurationValue>,
) {

    private val fieldIndex: Map<String, ConfigurationValue> = fields.toMutableMap()

    init {
        require(commandId.isNotBlank()) {
            "commandId correlates this answer to an operation; a blank one cannot be correlated"
        }
    }

    /** The value reported for [field], or null when the device did not report it. */
    operator fun get(field: String): ConfigurationValue? = fieldIndex[field]

    /** Whether [field] carries a report at all. */
    fun has(field: String): Boolean = fieldIndex.containsKey(field)

    /** Names that carry reports. Anything else is unknown, not missing-and-therefore-zero. */
    val reportedFieldNames: Set<String>
        get() = fieldIndex.keys

    /** Whether the parser recognised no field in this answer. */
    val isEmpty: Boolean
        get() = fieldIndex.isEmpty()

    /** How many fields carry reports. */
    val reportedFieldCount: Int
        get() = fieldIndex.size
}
