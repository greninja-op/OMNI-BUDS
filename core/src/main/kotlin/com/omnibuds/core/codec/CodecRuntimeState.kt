package com.omnibuds.core.codec

import com.omnibuds.core.audio.Codec
import com.omnibuds.core.audio.CodecEvidence
import com.omnibuds.core.audio.CodecFreshness
import com.omnibuds.core.audio.CodecMetadata
import com.omnibuds.core.audio.CodecState

/**
 * What a codec is doing right now, as observed.
 *
 * This is the runtime half of the Phase 11 §7 split: [CodecCapability][com.omnibuds.core.audio.CodecCapability]
 * answers "what can this codec do" (static capability); this answers "what is it
 * doing" (live state). The two are never merged into one oversized model.
 *
 * Every field is honest about absence: [activeParameters] is null when the
 * platform did not report parameters, [observedAtMillis] is null when nothing
 * was ever observed, and [freshness] says whether the record may still be
 * trusted. A device that disconnects does not keep a [CodecState.ACTIVE]
 * record rendering as current — it becomes [CodecFreshness.STALE].
 */
data class CodecRuntimeState(
    /** Which codec this record is about; identity, not a label. */
    val codec: Codec,
    /**
     * The ladder rung with valid runtime evidence. [CodecState.ACTIVE] only on
     * authoritative runtime evidence (Phase 11 RULE 2); [CodecState.UNKNOWN]
     * when the platform does not expose the active codec.
     */
    val state: CodecState,
    /** Parameters currently in effect as reported, or null when unreported. */
    val activeParameters: CodecMetadata? = null,
    /** Epoch millis of the observation, or null when never observed. */
    val observedAtMillis: Long? = null,
    /** Whether the record may still be trusted. */
    val freshness: CodecFreshness = CodecFreshness.UNKNOWN,
    /** Why OmniBuds believes this record. */
    val evidence: CodecEvidence = CodecEvidence.unknown(),
) {
    companion object {
        /** A runtime record that claims nothing and is explicitly stale-safe. */
        fun unknown(codec: Codec): CodecRuntimeState =
            CodecRuntimeState(codec, CodecState.UNKNOWN)
    }
}
