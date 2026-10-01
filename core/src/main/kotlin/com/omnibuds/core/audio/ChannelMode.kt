package com.omnibuds.core.audio

/**
 * How many audio channels a session is carrying.
 *
 * [UNKNOWN] is required because a partial read is a valid result: a session that
 * reported a sample rate but no channel mode keeps this unknown rather than assuming
 * stereo (AUD-QUAL-001, and its "never render as" table forbidding `"stereo"` as a
 * default guess).
 */
enum class ChannelMode {
    /** One channel. */
    MONO,

    /** Two channels. */
    STEREO,

    /** Not reported by the source, so not stated by OmniBuds. */
    UNKNOWN,
}
