package com.omnibuds.core.protocol

import com.omnibuds.core.common.OperationOutcome

/**
 * Turns the bytes of one response into named fields, or refuses.
 *
 * This is the `Parser` element of PROTO-NOMAGIC-001 and master section 52: bytes in,
 * domain-shaped values out, with the interpretation living in exactly one documented
 * place instead of at every call site that notices a byte.
 *
 * **An unparseable response is
 * [com.omnibuds.core.common.OmniBudsErrorCategory.PROTOCOL_MISMATCH], never a defaulted
 * value.** That single rule is what keeps a wrong protocol guess from turning into
 * readable-looking state: a body that does not match the [ResponseDefinition] for
 * [commandId] — too short, an unrecognised tag, a field that cannot be decoded — yields a
 * mismatch and leaves the capability unknown (PROTO-ERR-001, specs.md section 3 rule 5,
 * master section 53). Returning `ParsedResponse` with the doubtful fields filled in would
 * be the fabrication this interface exists to prevent.
 *
 * The distinction that must survive parsing is absence, not failure. A well-formed
 * response that simply does not carry a field yields success with that key missing; a
 * response that contradicts the definition yields failure. Both are honest, and only the
 * second one demotes confidence (PROTO-ERR-001 retry class: no retry, downgrade).
 *
 * Implementations arrive with the protocol records of Phase 22 and later, and belong to
 * the platform or protocol-extension layer that owns them. Phase 1 defines the contract
 * only: there is no parser in `:core`, because a parser written before the bytes it
 * decodes have been discovered would necessarily be invented
 * (Phase 1 prompt sections 26, 51 and 53).
 */
interface ProtocolParser {

    /**
     * Parse the body of an answer to [commandId].
     *
     * [raw] is consumed read-only; a parser that needs to keep bytes must copy them,
     * because the transport owns the buffer it handed over.
     */
    fun parse(commandId: String, raw: ByteArray): OperationOutcome<ParsedResponse>
}
