package com.omnibuds.core.codec

import com.omnibuds.core.audio.Codec
import com.omnibuds.core.audio.CodecEvidence

/**
 * The five independent control-capability dimensions for one codec on one device.
 *
 * Phase 12 (OB-P12-REQ-004) extends the Phase 11 capability model. These are
 * *independent* booleans, not a ladder:
 *
 * - [observable]: the platform exposes this codec's runtime state through a
 *   legitimate API.
 * - [supported]: the device (or local platform) reports this codec as available.
 * - [selectable]: a legitimate mechanism exists to *switch to* this codec.
 * - [configurable]: a legitimate mechanism exists to *tune* this codec's
 *   parameters (quality mode, bitrate, …).
 * - [verifiable]: a legitimate mechanism exists to *confirm* a change took effect.
 *
 * For example, LDAC with supported=true, selectable=false, configurable=false is
 * valid and common on public Android APIs: the codec is known and the phone may
 * support it, but no public API lets OmniBuds select or configure it.
 *
 * All five being false is valid (fully unknown). All five being true requires
 * a genuine mechanism for each — never assumed.
 */
data class CodecControlCapability(
    val codec: Codec,
    val observable: Boolean,
    val supported: Boolean,
    val selectable: Boolean,
    val configurable: Boolean,
    val verifiable: Boolean,
    val evidence: CodecEvidence,
) {
    /**
     * True only when a control operation has any legitimate path forward.
     * Read-only observation does not count as control.
     */
    val controllable: Boolean get() = selectable || configurable

    companion object {
        /**
         * The honest default for a codec about which nothing is known: every
         * dimension false, evidence records the limitation.
         */
        fun unknown(codec: Codec, evidence: CodecEvidence): CodecControlCapability =
            CodecControlCapability(
                codec = codec,
                observable = false,
                supported = false,
                selectable = false,
                configurable = false,
                verifiable = false,
                evidence = evidence,
            )
    }
}
