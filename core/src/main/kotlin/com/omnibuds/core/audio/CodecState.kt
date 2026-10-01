package com.omnibuds.core.audio

/**
 * What has been established about one codec of one endpoint in one session.
 *
 * Declaration order is the evidence ladder and is load-bearing: a codec may move up
 * only on evidence of the matching tier, and ordinal comparison is what
 * `CodecCapability.supportsAtLeast` relies on. `UNKNOWN` and `UNSUPPORTED` sit below
 * the ladder because neither says anything positive about a codec.
 *
 * Each rung names one state, so "supported but not active" is a single, ordinary
 * record rather than a combination of flags — that combination is precisely what the
 * product must never display as "LDAC ACTIVE" (master section 15, AUD-STATE-001).
 *
 * Two facts about the ladder's shape:
 *
 *  1. `CONFIGURABLE` from ADR-P0-015 is deliberately absent. It became the orthogonal
 *     [CodecCapability.configurable] attribute, because writability is not a degree of
 *     activity: a codec can be [ACTIVE] *and* configurable, or [ACTIVE] and read-only
 *     when the OS exposes only reads (master section 21, AUD-CONFIG-002). No
 *     single-valued ladder can express both, and forcing it onto one rung would let
 *     "configurable" masquerade as evidence of audio carrying (C-01 in
 *     `docs/phases/phase-0/audio-governance.md`).
 *  2. [ENABLED] is the rung the Phase 1 prompt calls "selected". A user or stack
 *     selection lands here and no further: it is never evidence of [NEGOTIATED] or
 *     [ACTIVE] (ADR-P0-015, AUD-STATE-002).
 */
enum class CodecState {
    /**
     * Nothing has been established: not read, read failed, source unreachable, or
     * contradicted by another claim. The default when no evidence exists, and never
     * a synonym for [UNSUPPORTED] (AUD-STATE-005, AUD-VERIFY-003, AUD-LAYER-006).
     */
    UNKNOWN,

    /**
     * Positively established that this endpoint does not implement the codec.
     * Requires an actual negative reading, not a missing lookup (specs section 2.3).
     */
    UNSUPPORTED,

    /**
     * Exposed as implementable by one endpoint — device or host — recorded per
     * endpoint and never merged across them; the pair is [SUPPORTED] only once both
     * ends support it (AUD-STATE-003). Says nothing about this session.
     */
    SUPPORTED,

    /**
     * Both endpoints support it and a read made during a live session reports it
     * usable now. Never inferred from theoretical device capability
     * (AUD-SRC-002, master section 14).
     */
    AVAILABLE,

    /**
     * Offered or preferred for this session by the host stack or by explicit user
     * selection: the "selected" rung of ADR-P0-015 and of the Phase 1 prompt section 17.
     * A selection is an input to this rung only; the codec may still fail to
     * negotiate and never carry audio (AUD-STATE-002).
     */
    ENABLED,

    /**
     * The transport negotiation result for this session includes this codec. Still
     * not audio: negotiation without a verified active read renders as "negotiated;
     * active state not verified" (audio-governance section 6 display rules).
     */
    NEGOTIATED,

    /**
     * The codec currently carries this session's media audio, per the verification
     * gate in audio-governance section 11 (AUD-VERIFY-002). The only rung that
     * licenses an "<codec> active" claim, and the only one reached by an
     * authoritative read on real hardware.
     */
    ACTIVE,
}
