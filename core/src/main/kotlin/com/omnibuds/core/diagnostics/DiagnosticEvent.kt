package com.omnibuds.core.diagnostics

import com.omnibuds.core.common.OmniBudsError

/**
 * One recorded observation.
 *
 * The field list is the whole privacy surface of a log line, so it is what it is by
 * decision:
 *
 *  - **No address, no device name, no manufacturer data, no payload.** There is no
 *    field on this type that a caller could fill in with a Bluetooth address or a
 *    device name, because a field named for a thing is an invitation to populate it
 *    (`SEC-LOG-002`, `SEC-ID-001`). Where a log must refer to a device, the sanctioned
 *    handle is a purpose-built local reference (`SEC-LOG-003`), which is not this type's
 *    job to mint in Phase 1. [operationId] correlates one operation's events; it is not
 *    a device identifier and must not be derived from an address.
 *  - **[timestampEpochMillis] is permitted content** (`SEC-LOG-001`) and is epoch
 *    millis rather than a `java.time`/`java.util.Date` type so that the core module
 *    stays Kotlin-Multiplatform-safe (execution prompt section 8).
 *  - **[error] is a structured value, not a message string**, so a report can group by
 *    `OmniBudsErrorCategory` without parsing text (specs.md section 3).
 *
 * **[message] is pre-redaction text.** It is the author's raw sentence, held here
 * unwrapped so that nothing in Phase 1 pretends a log line is safe merely because it
 * passed through this type. It must not contain an unredacted Bluetooth address, a
 * device name bound to an address, or manufacturer data (`SEC-LOG-002`); the honest
 * reading of that rule is "do not put it in", because no check here enforces it.
 *
 * **Known gap, recorded rather than papered over:** this type ships no `redacted()`
 * helper. A real redactor has to catch addresses, address-bound names, manufacturer
 * data and identifier-bearing characteristic payloads in free text; a pattern-matcher
 * that only masks colon-separated MACs would report success on text that still contains
 * a device name, and a caller who trusts a half redactor leaks with confidence. So
 * redaction stays at the emission boundary, owned by the platform logging
 * implementation in a later phase, and this layer passes text through untouched.
 */
data class DiagnosticEvent(
    /** When it happened, in milliseconds since the Unix epoch. */
    val timestampEpochMillis: Long,
    /** How loud it is, and whether recording it needs an opt-in; see [DiagnosticSeverity]. */
    val severity: DiagnosticSeverity,
    /** Where it came from; see [DiagnosticCategory]. */
    val category: DiagnosticCategory,
    /**
     * Pre-redaction human-readable text. No unredacted Bluetooth address, device name
     * bound to an address, or manufacturer data (`SEC-LOG-002`).
     */
    val message: String,
    /** Correlation id for the operation that produced this, or null when unattributed. */
    val operationId: String?,
    /** The structured failure this event reports, or null when nothing failed. */
    val error: OmniBudsError?,
) {
    init {
        require(timestampEpochMillis >= 0L) { "epoch millis cannot be negative, was $timestampEpochMillis" }
        require(message.isNotBlank()) { "a diagnostic message must say something; blank is not a redacted value" }
    }
}
