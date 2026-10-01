package com.omnibuds.core.protocol

import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.config.ConfigurationValue

/**
 * Turns named fields into the bytes of one command, or refuses.
 *
 * The `Encoder` element of PROTO-NOMAGIC-001: domain value -&gt; bytes, in exactly one
 * documented place, with the command's framing defined by the [CommandDefinition] being
 * encoded rather than assembled at the call site (PROTO-NOMAGIC-004 refuses "any control
 * path assembled ad hoc at a call site").
 *
 * An encoder here has three obligations, and each one is a lie it could tell:
 *  - **Unknown values are not encoded.** A field the device never reported is absent from
 *    the caller's map; filling it in with a default to make the byte layout work would
 *    write a fabricated setting into real hardware (specs.md section 2.2, ADR-P0-016).
 *    Returning a failure is the honest answer.
 *  - **Values outside the definition are refused.** A [ConfigurationValue.ModeValue] whose
 *    technical name the protocol does not model, or a number outside the range the
 *    command documents, yields a failure —
 *    [com.omnibuds.core.common.OmniBudsErrorCategory.UNSUPPORTED_FEATURE] for the mode,
 *    `INVALID_STATE` for the range — never a clamped or approximated encoding.
 *  - **Inference does not encode.** A [CommandDefinition] at
 *    [com.omnibuds.core.state.VerificationLevel.INFERRED] is a hypothesis and must not be
 *    sent to a user's device (SEC-RES-004), which is why [command] is passed in rather
 *    than just its id: the gate travels with the definition.
 *
 * Encoding is not sending. This contract produces bytes; whether those bytes may go to a
 * device is decided by the write gates in `protocol-governance.md` sections 6 and 8, which
 * no encoder may shortcut. It also never retries: a re-encoded resend of a timed-out write
 * is exactly the failure [EffectClass] exists to make impossible.
 *
 * Implementations arrive with the protocol records of later phases. Phase 1 defines the
 * contract only, because an encoder written before the encoding has been discovered would
 * be invented (Phase 1 prompt sections 26, 51 and 53).
 */
interface ProtocolEncoder {

    /**
     * Encode [fields] as the body of [command].
     *
     * [fields] is keyed by the same symbolic field names a [ResponseDefinition] declares,
     * so a command and its answer speak one vocabulary.
     */
    fun encode(
        command: CommandDefinition,
        fields: Map<String, ConfigurationValue>,
    ): OperationOutcome<ByteArray>
}
