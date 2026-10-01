package com.omnibuds.core.diagnostics

import com.omnibuds.core.diagnostics.DiagnosticEvent
import com.omnibuds.core.diagnostics.DiagnosticSeverity

/**
 * The seam between core and whatever actually emits diagnostics.
 *
 * This is the whole abstraction on purpose (Phase 1 execution prompt section 41: a
 * *minimal* logging abstraction). Core describes what happened as a [DiagnosticEvent];
 * it does not decide where that goes, in what format, or whether it is kept.
 *
 * **No implementation lives here.**
 *  - An Android implementation (a Logcat-backed logger) belongs to `:platform:android`
 *    in a later phase — the dependency direction forbids core from knowing about a
 *    platform sink, and a logger would be the easiest place to smuggle that in
 *    (execution prompt section 32).
 *  - A recording fake belongs in test infrastructure, never in main source
 *    (execution prompt sections 26 and 27): a fake sink compiled into the shipped
 *    artifact is a fake capability with extra steps.
 *  - No logging framework is a dependency of `:core`, and none is anticipated here.
 *
 * **What an implementation owes.**
 *  - It may not persist a packet-level event unless the user opted in: emission of
 *    `DiagnosticSeverity.PACKET` (and `TRACE`) is gated by a recorded
 *    `com.omnibuds.core.config.DiagnosticMode`, and raw packet bytes are prohibited in
 *    release builds at any level (`SEC-LOG-004`, `SEC-LOG-005`). "Persist" here means
 *    anything that outlives the process, so retention limits and a visible wipe come
 *    with a capture (`SEC-LOG-006`).
 *  - Redaction is the sink's job at the point of emission, not on export
 *    (`SEC-LOG-002`). [DiagnosticEvent.message] arrives as pre-redaction text; a sink
 *    that writes it out verbatim is where an identifier leak actually happens.
 *  - Nothing leaves the device silently: no background upload, no log attached to a
 *    crash report or network request (`SEC-LOG-008`), local-first by default
 *    (`SEC-PRIV-001`).
 *
 * [isEnabled] exists so that building an expensive message can be skipped entirely
 * rather than created and then discarded; it is a cost gate, not a permission gate, and
 * a disabled level is still refused by the rules above if it is somehow logged.
 */
interface OmniBudsLogger {

    /** Whether events at [severity] would be emitted right now. */
    fun isEnabled(severity: DiagnosticSeverity): Boolean

    /** Hands one event to the sink. Implementations must not widen what they persist based on this call alone. */
    fun log(event: DiagnosticEvent)
}
