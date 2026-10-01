package com.omnibuds.core.audio

/**
 * What OmniBuds knows about one codec in one session.
 *
 * Replaces the six independent booleans sketched in Phase 1 prompt section 17
 * (`supported`, `available`, `enabled`, `negotiated`, `active`, `configurable`),
 * which permitted nonsense such as `active = true, supported = false` and left every
 * contradictory combination representable. Accepted as ADR-P1-005.
 *
 * The improved model splits the concept in two:
 *
 * - [state] holds where the codec has got to on the [CodecState] evidence ladder, so
 *   the ladder can never be reported out of order and "supported but not in use" is
 *   one ordinary value rather than a flag pattern (AUD-STATE-001).
 * - [configurable] is orthogonal, because a codec can be [CodecState.ACTIVE] *and*
 *   configurable, or active and read-only (master section 21).
 *
 * The prompt's example `supported = true, available = true, enabled = true,
 * negotiated = false, active = false, configurable = false` becomes
 * `CodecCapability(LDAC, CodecState.ENABLED, configurable = false)` — the same
 * information, with the impossible readings no longer expressible.
 *
 * This is a record of observations. It performs no negotiation and controls no
 * platform codec (Phase 1 prompt sections 2 and 51).
 */
data class CodecCapability(
    /** Which codec this record is about; identity, not a label. */
    val codec: Codec,
    /** The highest rung with valid evidence, or [CodecState.UNKNOWN] when none exists. */
    val state: CodecState,
    /**
     * Orthogonal writability property: at least one parameter of this codec in this
     * session may legitimately be written. False when the OS exposes only reads, and
     * never a guess from another phone or firmware version (AUD-CONFIG-001..003).
     */
    val configurable: Boolean,
) {

    /**
     * True only at the [CodecState.ACTIVE] rung. Never computed from
     * `negotiated || enabled` (AUD-QUAL-004): the LDAC-selected/AAC-running case the
     * product must display honestly depends on this staying exact.
     */
    val isActive: Boolean
        get() = state == CodecState.ACTIVE

    /**
     * Whether the codec could carry audio in this session right now, on the evidence
     * read: [CodecState.AVAILABLE], [CodecState.ENABLED], [CodecState.NEGOTIATED] or
     * [CodecState.ACTIVE]. Merely [CodecState.SUPPORTED] is not usability, and
     * [CodecState.UNKNOWN] is not [CodecState.UNSUPPORTED] (AUD-SRC-002).
     */
    val isUsable: Boolean
        get() = supportsAtLeast(CodecState.AVAILABLE)

    /**
     * Whether [state] is at least as strong as [target] on the evidence ladder.
     *
     * Ordinal comparison is sound because [CodecState] is declared in ladder order.
     * Both sides are gated on that order's evidence floor so that [CodecState.UNKNOWN]
     * and [CodecState.UNSUPPORTED] satisfy no positive target — an unread or rejected
     * codec cannot be talked into supporting something
     * (AUD-STATE-005, AUD-VERIFY-003).
     */
    fun supportsAtLeast(target: CodecState): Boolean =
        state.ordinal >= ladderFloor &&
            target.ordinal >= ladderFloor &&
            state.ordinal >= target.ordinal

    companion object {
        /** Ordinal of [CodecState.SUPPORTED], the first rung that carries positive evidence. */
        private val ladderFloor: Int = CodecState.SUPPORTED.ordinal

        /**
         * A codec one endpoint positively does not implement. The claim requires a
         * real negative reading; use [unknown] when nothing has been read.
         */
        fun unsupported(codec: Codec): CodecCapability =
            CodecCapability(codec, CodecState.UNSUPPORTED, configurable = false)

        /** Nothing is known about this codec: not read, read failed, or not reachable. */
        fun unknown(codec: Codec): CodecCapability =
            CodecCapability(codec, CodecState.UNKNOWN, configurable = false)
    }
}
