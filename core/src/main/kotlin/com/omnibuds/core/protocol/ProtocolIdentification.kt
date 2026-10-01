package com.omnibuds.core.protocol

import com.omnibuds.core.state.VerificationLevel

/**
 * The outcome of asking "which protocol governs this device?", stated with the evidence
 * that decided it.
 *
 * The evidence is the point of this record. A protocol id on its own is a conclusion
 * without a reason, and conclusions without reasons are how a guessed protocol ends up
 * driving writes: PROTO-ID-003 makes candidate lists exactly that — candidates to be
 * confirmed by reads, never verdicts — and PROTO-RESEARCH-006 forbids issuing a control
 * read that a different protocol would interpret differently. [matchedBy] is what a
 * caller reads to know whether it is allowed to proceed.
 *
 * [MatchEvidence.FALLBACK_UNKNOWN] is a first-class member and it must not be mistaken
 * for a match. [isIdentified] is false for it by construction, so code that branches on
 * "did we identify a protocol?" cannot accidentally answer yes for a guess, and
 * [isDefinitive] names the same fact for readers who want the affirmative form. A record
 * of that kind exists so that the *absence* of identification is something a session can
 * carry and report, rather than a hole that some caller fills with a default
 * (ADR-P0-016, master section 53).
 *
 * [confidence] is the protocol's own rung, inherited from the matched record
 * (PROTO-VERIFY-001). It is capped deliberately: `FALLBACK_UNKNOWN` means nothing was
 * matched, and nothing that was not matched can be better than inferred from resemblance,
 * which [init] enforces so a fallback cannot smuggle a confident-sounding id past a
 * capability engine (SEC-RES-004).
 */
data class ProtocolIdentification(
    /** The protocol family now believed to govern the device. */
    val protocolId: String,

    /** Which kind of evidence produced this answer. */
    val matchedBy: MatchEvidence,

    /** How well the matched protocol is understood; `INFERRED` is the floor, never a ceiling. */
    val confidence: VerificationLevel,
) {

    /** How an identification was reached, from real evidence down to none. */
    enum class MatchEvidence {
        /** The fingerprint matched a recorded fingerprint rule exactly. */
        EXACT_FINGERPRINT,

        /** Some of the evidence matched and some did not; a candidate, not a verdict. */
        PARTIAL_FINGERPRINT,

        /** Matched on discovered services or characteristics rather than on identity. */
        SERVICE_DISCOVERY,

        /**
         * Nothing matched; the device is unidentified and this entry is a placeholder for
         * that fact. Not a match, and never evidence for anything.
         */
        FALLBACK_UNKNOWN,
    }

    init {
        require(protocolId.isNotBlank()) {
            "a blank protocol id identifies nothing; an unidentified device is reported by " +
                "MatchEvidence.FALLBACK_UNKNOWN, not by empty text"
        }
        if (matchedBy == MatchEvidence.FALLBACK_UNKNOWN) {
            require(confidence == VerificationLevel.INFERRED) {
                "FALLBACK_UNKNOWN matched nothing, so its confidence cannot exceed " +
                    "INFERRED; was $confidence"
            }
        }
    }

    /** Whether this record represents a real identification rather than a recorded absence. */
    val isIdentified: Boolean
        get() = matchedBy != MatchEvidence.FALLBACK_UNKNOWN

    /** Whether the evidence is conclusive enough to stop treating this as a candidate. */
    val isDefinitive: Boolean
        get() = matchedBy == MatchEvidence.EXACT_FINGERPRINT

    companion object {
        /**
         * The honest answer for a device nothing matched: no protocol is established, and
         * the capabilities it may have stay unknown (PROTO-DB-002).
         *
         * [protocolId] is a marker, not a protocol — it names the no-match state so that
         * diagnostics have something to print without inventing a family.
         */
        fun unmatched(protocolId: String = NO_PROTOCOL_MATCHED): ProtocolIdentification =
            ProtocolIdentification(
                protocolId = protocolId,
                matchedBy = MatchEvidence.FALLBACK_UNKNOWN,
                confidence = VerificationLevel.INFERRED,
            )

        /** The marker id reported when no known protocol governs the device. */
        const val NO_PROTOCOL_MATCHED: String = "no-protocol-matched"
    }
}
