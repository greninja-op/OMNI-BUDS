package com.omnibuds.core.common


/**
 * A structured failure.
 *
 * An error is a value that is returned, never a string that is parsed, and it never
 * stands in for missing data: an unread measurement stays null/unknown in the state
 * model rather than becoming zero (ADR-P0-016).
 *
 * [detail] is diagnostic text and must not contain Bluetooth addresses, device
 * names or manufacturer data; redaction happens at the point of emission
 * (SEC-LOG-002).
 */
data class OmniBudsError(
    val category: OmniBudsErrorCategory,
    val operationId: String,
    val detail: String? = null,
    val transport: TransportKind = TransportKind.UNKNOWN,
    val attempts: Int = 1,
) {
    init {
        require(attempts >= 1) { "attempts must be at least 1, was $attempts" }
    }

    /** Whether the device's real state is now uncertain and must be re-discovered. */
    val invalidatesSession: Boolean
        get() = category.invalidatesSession

    companion object {
        fun of(
            category: OmniBudsErrorCategory,
            operationId: String,
            detail: String? = null,
            transport: TransportKind = TransportKind.UNKNOWN,
        ): OmniBudsError = OmniBudsError(category, operationId, detail, transport)
    }
}
