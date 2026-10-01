package com.omnibuds.core.protocol

/**
 * What an operation does to the device, which is the only thing that decides whether it
 * may be attempted again.
 *
 * This is the structural form of the master rule (Phase 0 `specs.md` section 4,
 * PROTO-ERR-002): `SET ANC` -&gt; `Timeout` -&gt; **do not send `SET ANC` again** -&gt;
 * read the current ANC -&gt; determine the actual state. A timed-out write is resolved
 * by re-reading state, never by re-sending, because a write that timed out may have
 * landed, and a second send compounds an effect nobody has observed.
 *
 * The asymmetry is deliberate and it is the whole reason this enum exists: reads are
 * idempotent and may have a bounded retry budget, writes are not, and no call site is
 * allowed to decide that for itself (specs.md section 4 rule 3 — the retry policy is
 * declared per operation in the protocol definition, not improvised at the call site).
 *
 * [IRREVERSIBLE_WRITE] is separated from [SIDE_EFFECTING_WRITE] because some operations
 * cannot be undone by another command — a factory reset, an update that leaves a
 * half-flashed build. Those need a stricter gate than "read back afterwards", and the
 * gate is a property of the operation rather than something a caller remembers.
 */
enum class EffectClass {
    /** Reads device state and changes nothing. The only class that may repeat itself. */
    READ,

    /** Changes device state, and a later command could change it back. */
    SIDE_EFFECTING_WRITE,

    /**
     * Changes device state in a way no command can undo.
     *
     * Phase 1 models this so that a later phase cannot accidentally treat it as an
     * ordinary write; nothing here authorises sending one.
     */
    IRREVERSIBLE_WRITE,
    ;

    /**
     * Whether the operation may be re-issued automatically after it fails.
     *
     * True for [READ] only. Both write classes are false even though
     * [SIDE_EFFECTING_WRITE] reads as milder: the question is not how bad a repeat
     * would be, it is whether repeating is *safe without knowing the current state*,
     * and for any write it is not. Re-read-then-decide is the path for a failed write
     * (`OmniBudsErrorCategory.RETRY_AFTER_REREAD`), and that is a different action from
     * a retry — building it belongs to Phase 34, not to this property (PROTO-ERR-005).
     */
    val permitsAutomaticRetry: Boolean
        get() = this == READ
}
