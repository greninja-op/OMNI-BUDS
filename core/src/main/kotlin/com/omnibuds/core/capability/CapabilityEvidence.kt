package com.omnibuds.core.capability

import com.omnibuds.core.state.VerificationLevel

/**
 * Whether a capability can be used *right now*, as a dimension separate from whether it is supported.
 *
 * This is the one prompt §7 dimension the existing model does not encode. `CapabilityState` answers "what
 * does OmniBuds believe about this feature" (support, access, durability); it has no word for "supported but
 * the case is closed / the feature is mid-calibration / a prerequisite is off". Those are different facts, and
 * §7 insists they not be conflated: a supported feature may be unavailable, and an unavailable feature is
 * emphatically **not** an unsupported one (ADR-P8-002, master section 53).
 *
 * It is deliberately carried beside `FeatureCapability` (in the snapshot), never written into it:
 * availability is a momentary observation, not durable evidence, and prompt §2 forbids silently widening an
 * earlier phase's record. [UNKNOWN] is the floor and is distinct from [UNAVAILABLE] exactly as `CapabilityState.UNKNOWN`
 * is distinct from `UNSUPPORTED` — the absence of a reading is never a negative.
 */
enum class CapabilityAvailability {
    /** No availability reading exists. Not "unavailable" — nothing was observed either way. */
    UNKNOWN,

    /** Usable at this moment, on a feature whose support is established. */
    AVAILABLE,

    /** Established to be currently unusable, but still supported (e.g. a prerequisite is off). */
    UNAVAILABLE,

    /** Usable intermittently; expected to return (e.g. a mode not reachable while the case is open). */
    TEMPORARILY_UNAVAILABLE,
}

/** Whether a control may be surfaced now: supported *and* currently available. */
fun CapabilityAvailability.isAvailableNow(): Boolean =
    this == CapabilityAvailability.AVAILABLE || this == CapabilityAvailability.TEMPORARILY_UNAVAILABLE

/**
 * Where a capability claim came from, and how far that source is entitled to be trusted.
 *
 * The kind is the point (§8): "the device answered this read" and "a model that resembles this one usually
 * has it" are different *kinds* of fact, not the same fact at different confidence percentages, and the engine
 * must never average them. A marketing description or an implemented-but-unrun parser can only ever produce
 * [EvidenceKind.INFERRED_MODEL] / [EvidenceKind.VERIFIED_PROTOCOL_DESCRIPTOR]-at-low-rung evidence — never
 * device support (prompt §8's "do not treat an implemented parser as proof of hardware support").
 */
enum class EvidenceKind {
    /** The device itself answered a read; the strongest single observation. */
    EXPLICIT_DEVICE_RESPONSE,

    /** A fact from a protocol descriptor whose verification reached this far. */
    VERIFIED_PROTOCOL_DESCRIPTOR,

    /** A feature-flag bit the device reported. */
    DEVICE_FEATURE_FLAG,

    /** Model-specific protocol metadata, verified for that model. */
    MODEL_PROTOCOL_METADATA,

    /** A firmware-compatibility statement. */
    FIRMWARE_COMPATIBILITY,

    /** A capability is reachable only over a transport that is/isn't present. */
    TRANSPORT_AVAILABILITY,

    /** Reasoned from a similar model or a name — a hypothesis, never proof. */
    INFERRED_MODEL,

    /** Evidence was absent or unparseable; honestly recorded as unknown rather than a guess. */
    UNKNOWN_OR_INCOMPLETE,
}

/**
 * One provenance record behind a capability claim, so a later reader can see *why* OmniBuds believes it.
 *
 * [verification] is the rung *this piece of evidence* supports — a ceiling set by its kind, not by the code
 * that read it: an [EvidenceKind.INFERRED_MODEL] cannot exceed [VerificationLevel.INFERRED], and no JVM
 * fixture is ever [VerificationLevel.HARDWARE_VERIFIED] (ADR-P8-003). [limitation] states what the evidence
 * does *not* establish (e.g. "reports presence, not writability"), and [detail] carries no packet bytes.
 */
data class CapabilityEvidence(
    val kind: EvidenceKind,
    val source: String,
    val atEpochMillis: Long?,
    val protocolId: String?,
    val protocolVersion: String?,
    val verification: VerificationLevel,
    val detail: String? = null,
    val limitation: String? = null,
) {
    init {
        require(source.isNotBlank()) { "evidence without a source is unsourced; a blank is not a provenance" }
        require(protocolId == null || protocolId.isNotBlank()) { "protocolId is null-or-non-blank" }
        require(protocolVersion == null || protocolVersion.isNotBlank()) { "protocolVersion is null-or-non-blank" }
        // The ceiling a kind can speak to, enforced so a fixture cannot claim a device answered it did not.
        if (kind == EvidenceKind.INFERRED_MODEL || kind == EvidenceKind.UNKNOWN_OR_INCOMPLETE) {
            require(verification == VerificationLevel.INFERRED) {
                "$kind evidence is at most ${VerificationLevel.INFERRED}; it reasoned or nothing, " +
                    "and cannot claim $verification"
            }
        }
    }

    /** Whether this record establishes anything, or records the absence of evidence. */
    val isEstablishing: Boolean
        get() = kind != EvidenceKind.UNKNOWN_OR_INCOMPLETE

    companion object {
        /** The record for "nothing conclusive was found" — unknown, and honestly labelled. */
        fun unknown(source: String): CapabilityEvidence = CapabilityEvidence(
            kind = EvidenceKind.UNKNOWN_OR_INCOMPLETE,
            source = source,
            atEpochMillis = null,
            protocolId = null,
            protocolVersion = null,
            verification = VerificationLevel.INFERRED,
        )
    }
}
