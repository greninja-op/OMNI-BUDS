package com.omnibuds.core.transport

import com.omnibuds.core.common.TransportKind

/**
 * The transports one device or platform could offer, and which one — if any — was chosen.
 *
 * Offering is not selecting. A device may present a GATT service, an RFCOMM channel and a classic
 * path simultaneously (master section 8 lists those roles; PROTO-XPORT-005 states that one device
 * may use BLE for advertisements, RFCOMM for control, A2DP for audio and a proprietary mechanism for
 * configuration — all recorded at once for a single session). [candidates] is that record, and
 * [selected] is a separate statement about it. The reason the two are not collapsed into one value
 * is PROTO-XPORT-007: a refused candidate stays visible as a refused candidate, because a model that
 * only stores the winner erases the evidence that the others were absent.
 *
 * Ordering is a caller's input, never a property of this type. There is no ranking, no score and no
 * preference inferred from a kind's name or from what other devices usually do — see
 * [preferring] for the only selection this phase permits, and ADR-P0-003 for why "usually GATT" is
 * exactly the assumption the transport abstraction exists to break.
 *
 * A selection with no channel behind it is the bug this type is built to make unrepresentable: a
 * command written to a transport nobody established is a command written nowhere
 * (`TransportAvailability` documentation). So [selected] must name a candidate *and* that candidate
 * must be the available one — when a chosen channel later becomes unavailable, the honest record is
 * a new negotiation with [selected] set to null and the old row marked refused, not a stale
 * selection kept alive beside a contradiction.
 */
data class TransportNegotiation(
    /**
     * Every transport considered for this device or platform, including the refused ones.
     *
     * Empty is legal and means "nothing has been proposed yet", which is a different fact from
     * "everything was tried and refused" — [isUndetermined] reads true in both cases, and the
     * per-row [TransportAvailability.reason] is what distinguishes them.
     */
    val candidates: List<TransportBoundary>,

    /**
     * The channel chosen for this purpose, or null while none has been.
     *
     * Null is the ordinary Phase 2 answer: nothing here is attached. When non-null it must match a
     * candidate whose availability says it is usable.
     */
    val selected: TransportKind?,
) {

    init {
        if (selected != null) {
            val chosen = candidates.filter { it.kind == selected }
            require(chosen.isNotEmpty()) {
                "selected $selected does not appear among the candidates " +
                    "(${candidates.map { it.kind }}); a transport cannot be chosen for a device " +
                    "that never offered it, and choosing it anyway is the 'assume GATT' defect in " +
                    "a new costume (PROTO-XPORT-001)"
            }
            require(chosen.any { it.availability.available }) {
                "selected $selected is only present as a refused candidate; a channel reported " +
                    "unavailable cannot also be the selected one, because a command would then be " +
                    "addressed to a channel nobody established (PROTO-XPORT-007)"
            }
        }
    }

    /**
     * Nothing is usable and nothing is chosen.
     *
     * True for an empty candidate list and for a wholly refused one, which is the state a Phase 2
     * session is always in: no channel has been probed, opened or attached, so a caller reading this
     * must not treat the absence as "unsupported" — an unanswered transport question keeps the device
     * unidentified rather than refuted (PROTO-ERR-004, master section 53).
     */
    val isUndetermined: Boolean
        get() = candidates.none { it.availability.available }

    /**
     * The candidate the caller's [order] picks, or null when none of the available candidates is
     * mentioned by it.
     *
     * Deterministic and nothing else: for each kind in the order given, take the first candidate
     * row for that kind whose availability reports usable, and stop at the first such row. Kinds in
     * [order] with no candidate are skipped rather than substituted — an unavailable channel does
     * not cause a fallthrough to a different transport (PROTO-XPORT-007), and a caller that wants
     * another channel must ask for it by name. An empty [order] answers null even when candidates
     * are available: no preference stated is not a licence to guess one.
     *
     * [order] is a caller's policy input. This type holds none: it does not know whether GATT should
     * precede RFCOMM for any purpose, and the first component that claims to is the component that
     * starts inventing device support (ADR-P0-003).
     */
    fun preferring(order: List<TransportKind>): TransportBoundary? {
        for (kind in order) {
            val candidate = candidates.firstOrNull { it.kind == kind && it.availability.available }
            if (candidate != null) return candidate
        }
        return null
    }
}
