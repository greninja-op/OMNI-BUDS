package com.omnibuds.core.protocol

import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.device.DeviceFingerprint
import com.omnibuds.core.device.IdentificationResult

/**
 * What protocol, if any, the evidence points at — and it is a *candidate statement*, never a verdict.
 *
 * Prompt §10 asks for deterministic resolution that preserves ambiguity, does not pick an arbitrary winner,
 * does not mistake a manufacturer match for an exact protocol, does not auto-connect, and does not execute
 * during resolution. This is the outcome shape of that contract, composed over Phase 1's [ProtocolRegistry]
 * and Phase 5's identity evidence — not a new source of truth about devices.
 *
 * [candidates] keeps every surviving descriptor for the `AMBIGUOUS` case so the deciding layer sees what was
 * offered; for `RESOLVED` it holds the single match. An empty list is `UNKNOWN` or `INSUFFICIENT_EVIDENCE`,
 * which is absence of knowledge, not a claim that the device has no protocol (PROTO-DB-002).
 */
data class ProtocolResolution(
    val outcome: ProtocolResolutionOutcome,
    val candidates: List<ProtocolDefinition>,
    /** The candidate a caller may act on; non-null only for `RESOLVED`. */
    val selected: ProtocolDefinition?,
    val reason: String?,
) {
    init {
        when (outcome) {
            ProtocolResolutionOutcome.RESOLVED -> require(
                selected != null && candidates.size == 1 && selected == candidates.single(),
            ) { "RESOLVED names exactly one candidate as selected" }
            ProtocolResolutionOutcome.AMBIGUOUS -> require(
                selected == null && candidates.size > 1,
            ) { "AMBIGUOUS offers more than one candidate and selects none (no arbitrary winner, ADR-P7-006)" }
            ProtocolResolutionOutcome.UNKNOWN,
            ProtocolResolutionOutcome.INSUFFICIENT_EVIDENCE,
            -> require(candidates.isEmpty() && selected == null) {
                "${outcome.name} is an absence of candidates, not a hidden choice"
            }
            ProtocolResolutionOutcome.UNSUPPORTED,
            ProtocolResolutionOutcome.INCOMPATIBLE_VERSION,
            -> require(selected == null) {
                "${outcome.name} offers a candidate but selects none; it could not be used here"
            }
        }
    }

    /** Whether a session may be created from this resolution without a further decision. */
    val isActionable: Boolean
        get() = outcome == ProtocolResolutionOutcome.RESOLVED

    companion object {
        fun resolved(definition: ProtocolDefinition): ProtocolResolution =
            ProtocolResolution(ProtocolResolutionOutcome.RESOLVED, listOf(definition), definition, null)

        fun ambiguous(candidates: List<ProtocolDefinition>): ProtocolResolution =
            ProtocolResolution(
                ProtocolResolutionOutcome.AMBIGUOUS,
                candidates,
                null,
                "more than one protocol matches the evidence; none is chosen (prompt section 10)",
            )

        fun unknown(): ProtocolResolution =
            ProtocolResolution(ProtocolResolutionOutcome.UNKNOWN, emptyList(), null, "no protocol record names this evidence")

        fun insufficientEvidence(): ProtocolResolution =
            ProtocolResolution(
                ProtocolResolutionOutcome.INSUFFICIENT_EVIDENCE,
                emptyList(),
                null,
                "the identity evidence collected cannot name a protocol candidate",
            )

        fun unsupported(candidate: ProtocolDefinition): ProtocolResolution =
            ProtocolResolution(
                ProtocolResolutionOutcome.UNSUPPORTED,
                listOf(candidate),
                null,
                "protocol '${candidate.protocolId}' needs ${candidate.transport}, which is not available",
            )

        fun incompatibleVersion(candidate: ProtocolDefinition): ProtocolResolution =
            ProtocolResolution(
                ProtocolResolutionOutcome.INCOMPATIBLE_VERSION,
                listOf(candidate),
                null,
                "protocol '${candidate.protocolId}' is not version-compatible with this device",
            )
    }
}

/** The six resolution outcomes prompt §10 lists. Absence is kept distinct from a negative verdict. */
enum class ProtocolResolutionOutcome {
    /** Exactly one usable, compatible candidate; a session may be created for it. */
    RESOLVED,

    /** Several candidates survive; none is chosen (preserved for a deciding layer, ADR-P7-006). */
    AMBIGUOUS,

    /** Nothing on record names this evidence — unknown, not unsupported. */
    UNKNOWN,

    /** A candidate exists but this platform cannot carry its transport. */
    UNSUPPORTED,

    /** Evidence was too thin to name a candidate; distinct from "no protocol matches". */
    INSUFFICIENT_EVIDENCE,

    /** A candidate matches by family but its version constraints exclude this device. */
    INCOMPATIBLE_VERSION,
}

/**
 * Turns gathered evidence into a [ProtocolResolution] using the registry — and nothing else.
 *
 * The resolver reads: candidate records via [ProtocolRegistry.candidatesFor], the Phase 5
 * [IdentificationResult] (so thin evidence yields `INSUFFICIENT_EVIDENCE` rather than `UNKNOWN`), the set of
 * transports actually available, and an optional caller-supplied version-compatibility predicate. It never
 * opens a transport, never calls a device, and never runs a command (prompt §10); it is a pure function, so it
 * is independently testable and deterministic. With the empty registry the shipped default returns `UNKNOWN`
 * (or `INSUFFICIENT_EVIDENCE`), which is the honest state of Phase 7: the mechanism exists, no protocol does.
 */
interface ProtocolResolver {
    fun resolve(
        fingerprint: DeviceFingerprint,
        identification: IdentificationResult,
        registry: ProtocolRegistry,
        availableTransports: Set<TransportKind>,
        versionCompatible: (ProtocolDefinition) -> Boolean = { true },
    ): ProtocolResolution
}

/** The deterministic registry-backed resolver; the only implementation Phase 7 ships. */
class RegistryProtocolResolver : ProtocolResolver {
    override fun resolve(
        fingerprint: DeviceFingerprint,
        identification: IdentificationResult,
        registry: ProtocolRegistry,
        availableTransports: Set<TransportKind>,
        versionCompatible: (ProtocolDefinition) -> Boolean,
    ): ProtocolResolution {
        val candidates = registry.candidatesFor(fingerprint)

        if (candidates.isEmpty()) {
            // Thin evidence and no-match are different sentences: one says "we could not look properly",
            // the other "nothing on record fits what we saw" (prompt §13's unknown-vs-insufficient split).
            return if (identification is IdentificationResult.InsufficientEvidence) {
                ProtocolResolution.insufficientEvidence()
            } else {
                ProtocolResolution.unknown()
            }
        }

        val usable = candidates.filter { definition -> definition.transport in availableTransports }
        if (usable.isEmpty()) {
            // Candidates exist but no available channel can carry any of them — unsupported *here*, which is
            // not the same as the device having no protocol (PROTO-DB-002).
            return ProtocolResolution.unsupported(candidates.first())
        }

        val compatible = usable.filter(versionCompatible)
        if (compatible.isEmpty()) {
            return ProtocolResolution.incompatibleVersion(usable.first())
        }

        // A manufacturer-only identity is not an exact protocol match: several compatible candidates stay
        // ambiguous rather than being decided by registry order (ADR-P7-006; PROTO-ID-003).
        return if (compatible.size == 1) {
            ProtocolResolution.resolved(compatible.single())
        } else {
            ProtocolResolution.ambiguous(compatible.sortedBy { definition -> definition.protocolId })
        }
    }
}
