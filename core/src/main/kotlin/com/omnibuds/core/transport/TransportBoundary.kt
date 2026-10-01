package com.omnibuds.core.transport

import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.state.VerificationLevel
import com.omnibuds.core.state.atLeast

/**
 * One candidate control channel, described as far as the evidence actually reaches.
 *
 * A boundary is the unit a session reasons about when it asks "could this device be controlled over
 * RFCOMM?" without having asked a device anything yet (PROTO-XPORT-002: a transport is described as
 * a role, never as a byte, a socket parameter or a callback contract). It composes two facts
 * Phase 0 insists on keeping separate — whether the channel is usable ([availability]) and how
 * strongly anything about it is established ([supportEvidence], ADR-P0-014) — plus [notes] for the
 * prose a later reader needs.
 *
 * [availability] is a *record*, not a probe: nothing here has been sent, discovered or opened, and
 * no Phase 2 code calls [BluetoothTransport.probeAvailability] to produce one. It is the shape such
 * an answer will take, and the shape is testable now: a boundary built at `INFERRED` evidence
 * cannot report an available channel, so the claim an advertised service would tempt a caller into —
 * "the vendor UUID was observed, therefore this channel works" — cannot be represented at all
 * (PROTO-RESEARCH-003, ADR-P0-001).
 *
 * **What this type forbids, and the real bug each ban maps to:**
 *  - [kind] disagreeing with [availability]'s own kind. One row describes one channel, because a
 *    row that mixes them is how "GATT stood in for RFCOMM" happens (PROTO-XPORT-003,
 *    PROTO-XPORT-005).
 *  - `available = true` on evidence below [VerificationLevel.LAB_TESTED]. `INFERRED` means
 *    "documentation suggests it" and `IMPLEMENTED` means "code exists that was never run"
 *    (`protocol-governance.md` section 7); promoting either into a usable channel is the
 *    fabricated-support defect master section 53 names, which is why this invariant lives in an
 *    `init` block instead of a review checklist.
 *  - a blank [notes]: an empty string reads like a redacted reason. `null` means "nothing to add",
 *    which is a different statement (ADR-P0-016).
 *
 * What it does **not** claim: that the channel can carry any particular command, that a protocol
 * speaks over it, or that the device is controllable. Reaching READY with zero attached channels is
 * permitted and is read-only observation, never full support (PROTO-XPORT-004).
 */
data class TransportBoundary(
    /** The channel this row describes. */
    val kind: TransportKind,

    /** Whether that channel could be used, with the category that refused it when it could not. */
    val availability: TransportAvailability,

    /**
     * How strongly the availability statement is supported.
     *
     * The subject is the *availability claim*, not a capability: `INFERRED` says the channel was
     * reasoned to exist, `LAB_TESTED` that a constructed or simulated answer exercised the path,
     * `HARDWARE_VERIFIED` that a real device answered. What a given tier licenses a caller to do is
     * the session and capability layers' decision, not this row's.
     */
    val supportEvidence: VerificationLevel,

    /**
     * Why the row says what it says, or null when there is nothing to record.
     *
     * Free text with no machine meaning: nothing may be parsed out of it to decide a capability,
     * because a decision read from a note has no evidence tier (PROTO-VERIFY-003). It must never
     * carry a vendor or model name — a boundary is selected by its [kind], and a note that mentions
     * a brand is the first step toward a brand conditional (ADR-P1-008, PROTO-VENDOR-004).
     */
    val notes: String?,
) {

    init {
        require(kind == availability.kind) {
            "a boundary must describe one channel: kind was $kind but the availability record " +
                "answers for ${availability.kind}; a mixed-up row is how one transport silently " +
                "stands in for another (PROTO-XPORT-005)"
        }
        if (availability.available) {
            require(supportEvidence.atLeast(VerificationLevel.LAB_TESTED)) {
                "an available $kind channel cannot be claimed on $supportEvidence evidence; " +
                    "availability requires at least LAB_TESTED, because 'a UUID was observed' and " +
                    "'code exists for this' are not observations of a working channel " +
                    "(PROTO-RESEARCH-003, ADR-P0-001)"
            }
        }
        // No lower bound applies to a refused row, and none is needed: VerificationLevel has no
        // UNKNOWN member to fall back into (ADR-P0-014), so any tier is a legal description of how
        // firmly an absence was established. An absence is never evidence of unsupportedness either
        // (PROTO-ERR-004) — that conclusion belongs to the capability engine, not to this row.
        require(notes == null || notes.isNotBlank()) {
            "notes is either absent (null) or says something; a blank note reads like a redacted " +
                "reason and is not a state (ADR-P0-016)"
        }
    }

    companion object {
        /**
         * A channel reported as usable, carrying the evidence tier that claim reached.
         *
         * [supportEvidence] is a required parameter rather than a default, because "usable" means
         * different things for a simulated answer and a hardware-verified one (PROTO-VERIFY-001) and
         * a type must not guess the caller's tier. Below `LAB_TESTED` the constructor refuses the
         * row.
         */
        fun established(
            kind: TransportKind,
            supportEvidence: VerificationLevel,
            notes: String? = null,
        ): TransportBoundary = TransportBoundary(
            kind = kind,
            availability = TransportAvailability.available(kind),
            supportEvidence = supportEvidence,
            notes = notes,
        )

        /**
         * A channel that is absent or refused, with the category that says which.
         *
         * [supportEvidence] states how firmly the absence was established: a candidate nobody probed
         * keeps `INFERRED`, which is a statement about our evidence and not about the device.
         */
        fun refused(
            kind: TransportKind,
            reason: OmniBudsErrorCategory,
            supportEvidence: VerificationLevel = VerificationLevel.INFERRED,
            notes: String? = null,
        ): TransportBoundary = TransportBoundary(
            kind = kind,
            availability = TransportAvailability.unavailable(kind, reason),
            supportEvidence = supportEvidence,
            notes = notes,
        )
    }
}
