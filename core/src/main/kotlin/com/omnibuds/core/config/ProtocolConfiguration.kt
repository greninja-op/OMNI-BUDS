package com.omnibuds.core.config

import com.omnibuds.core.common.TransportKind

/**
 * Configuration addressed to a protocol channel: how OmniBuds reaches a control
 * interface and how long it is willing to wait while doing so.
 *
 * Phase 1 execution prompt section 39 keeps this apart from application and device
 * configuration because it answers a different question. It has no feature ids and no
 * user settings; it names a protocol, an optional version, a transport and two bounds.
 *
 * **Bounds apply to reads only.** [maxReadAttempts] is a ceiling on repeating
 * idempotent *reads*, which are safe to issue again. There is deliberately no
 * `writeAttempts`, no `retryWrites` and no backoff field that could be repurposed for
 * a write, because a re-sent write compounds an effect nobody has observed
 * (specs.md section 4: blind automatic retry of side-effecting commands is
 * prohibited).
 *
 * **The write retry rule does not live in configuration.** Whether a failed operation
 * may be attempted again is derived from `OmniBudsErrorCategory.retryClass` —
 * `NEVER_RETRY` for `WRITE_REJECTED` and `VERIFICATION_FAILED`, `RETRY_AFTER_REREAD`
 * where the device's state is now suspect. A configuration value must never be able to
 * overrule that, which is why no field in this type can be read by a write path at all.
 *
 * Nulls follow the tiered unknown rule (specs.md section 2.2): an unreported protocol
 * version or an unset bound is `null`, rendered as unknown — never `""`, `0` or `-1`.
 */
data class ProtocolConfiguration(
    /** Stable protocol family key, e.g. a documented vendor protocol name. */
    val protocolId: String,
    /** Reported version, or null when the device did not report one. Unknown, not zero. */
    val protocolVersion: String?,
    /** Which control channel this configuration is addressed to; never assumed. */
    val transport: TransportKind,
    /** Per-operation wait bound in milliseconds, or null when the caller sets none. */
    val timeoutMillis: Long?,
    /** Ceiling on repeated reads for one logical read, or null when unset. Reads only. */
    val maxReadAttempts: Int?,
) {
    init {
        require(protocolId.isNotBlank()) { "protocol id cannot be blank; omit the configuration instead" }
        require(protocolVersion == null || protocolVersion.isNotBlank()) {
            "protocol version is either reported or null; a blank version is not a reported one"
        }
        require(timeoutMillis == null || timeoutMillis > 0) {
            "timeoutMillis must be positive when present, was $timeoutMillis"
        }
        require(maxReadAttempts == null || maxReadAttempts in MIN_READ_ATTEMPTS..MAX_READ_ATTEMPTS_LIMIT) {
            "maxReadAttempts must be in $MIN_READ_ATTEMPTS..$MAX_READ_ATTEMPTS_LIMIT when present, " +
                "was $maxReadAttempts"
        }
    }

    companion object {
        /** A read budget below one attempt is not a retry policy, it is a mistake. */
        const val MIN_READ_ATTEMPTS: Int = 1

        /**
         * Upper bound on the read-attempt budget.
         *
         * Configurable retries stop here so that no protocol configuration can create
         * an unbounded read loop; the retry conventions require bounded attempts
         * (specs.md section 4, rule 2).
         */
        const val MAX_READ_ATTEMPTS_LIMIT: Int = 5
    }
}
