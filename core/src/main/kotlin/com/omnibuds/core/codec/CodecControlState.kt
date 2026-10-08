package com.omnibuds.core.codec

import com.omnibuds.core.audio.Codec
import com.omnibuds.core.audio.CodecFreshness

/**
 * Per-device codec control state: the strict requested-vs-confirmed split.
 *
 * Phase 12 (OB-P12-REQ-008):
 *
 * - [requested]: what was last asked for (codec + configuration). Set when an
 *   operation is accepted; never presented as reality.
 * - [observed]: what the platform last reported. May be UNKNOWN.
 * - [confirmed]: what verification proved. Advances ONLY on
 *   [CodecOperationResult.Verified]. This is the only field a UI or API may
 *   present as "the codec in use".
 * - [previousConfirmed]: the last confirmed state before the current request,
 *   retained for rollback (OB-P12-REQ-021). Null when none exists.
 * - [freshness]: whether this record is current or stale (OB-P12-REQ-026).
 *
 * Example of the honest flow:
 *
 *     request LDAC → requested=LDAC, confirmed=AAC (unchanged)
 *     mechanism reports applied, re-observation still AAC
 *       → result VERIFICATION_FAILED, confirmed stays AAC
 */
data class CodecControlState(
    val requestedCodec: Codec? = null,
    val requestedConfiguration: CodecConfiguration? = null,
    val observedCodec: Codec = Codec.UNKNOWN,
    val observedConfiguration: CodecConfiguration? = null,
    val confirmedCodec: Codec = Codec.UNKNOWN,
    val confirmedConfiguration: CodecConfiguration? = null,
    val previousConfirmedCodec: Codec? = null,
    val previousConfirmedConfiguration: CodecConfiguration? = null,
    val freshness: CodecFreshness = CodecFreshness.UNKNOWN,
    val updatedAtMillis: Long = 0L,
) {
    /** True when the record may be shown as current. */
    val isCurrent: Boolean get() = freshness == CodecFreshness.CURRENT

    companion object {
        fun initial(nowMillis: Long): CodecControlState =
            CodecControlState(freshness = CodecFreshness.UNKNOWN, updatedAtMillis = nowMillis)
    }
}
