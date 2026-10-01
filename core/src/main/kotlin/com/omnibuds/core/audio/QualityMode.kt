package com.omnibuds.core.audio

/**
 * The trade a codec session is currently making between fidelity, latency and robustness.
 *
 * The named modes are LDAC's, because LDAC is the codec whose priorities a user can
 * actually perceive (master section 17). Other codecs that expose an equivalent
 * preference map onto the same vocabulary; a codec with no such parameter simply
 * leaves the mode [UNKNOWN] rather than inheriting a plausible-looking
 * [BALANCED] (AUD-QUAL-002).
 *
 * The requested or priority mode and the mode actually negotiated for the session are
 * different facts and must stay separately reportable — this enum records what was
 * read, and [CodecCapability.configurable] says whether writing it is legitimate
 * (AUD-CONFIG-005, master section 17).
 */
enum class QualityMode {
    /** Fidelity preferred over link robustness. */
    SOUND_QUALITY_PRIORITY,

    /** Middle setting: neither fidelity nor link is favoured. */
    BALANCED,

    /** Link robustness preferred over fidelity. */
    CONNECTION_QUALITY_PRIORITY,

    /** The session adjusts its own trade continuously. */
    ADAPTIVE,

    /** No mode was reported, or this codec has no such parameter. */
    UNKNOWN,
}
