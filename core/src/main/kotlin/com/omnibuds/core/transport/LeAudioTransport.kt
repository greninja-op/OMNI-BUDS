package com.omnibuds.core.transport

/**
 * LE Audio as a boundary, declared with no behaviour at all.
 *
 * **This is a marker interface and it is empty on purpose.** Master section 20 treats LE Audio as
 * "an architecture in its own right, not an A2DP codec" (`protocol-governance.md` section 2 states
 * the same and adds "separate state paths"), so it earns a boundary. But an empty marker is the
 * *entirety* of what Phase 2 can state about it, and the alternatives are worse:
 *  - any member — a broadcast-assistant, a group, a context-type, an ISO-stream question — would
 *    model an unexecuted API surface, and Phase 2 prompt section 5.5 forbids claiming "LE Audio is
 *    active merely because an API exists";
 *  - a codec member (LC3 configuration) is forbidden outright by prompt section 6 and belongs to
 *    Phase 10/12;
 *  - giving [com.omnibuds.core.common.TransportKind.LE_AUDIO] richer meaning here would imply
 *    OmniBuds opens an isochronous group, which no phase has authorised.
 *
 * An empty declaration is also the shape that cannot lie: there is no method whose only honest
 * implementation would be a fabricated success, which is what Phase 1 prompt section 53 and
 * ADR-P1-013 exist to stop. The audit for this phase reaches the same verdict from the other
 * direction — "marker interface + `ApiAvailability` only".
 *
 * Where its availability actually lives in Phase 2 — named, never imported: the platform-facts area
 * is a registered sibling at the same layer (ADR-P2-001, and ADR-P1-003's rule that an import may
 * point only strictly downward), so this package may describe those facts but no file here may
 * import them:
 *  - `PlatformFeature.LE_AUDIO` — whether the OS exposes the APIs,
 *  - `ApiAvailability` — the four-axis answer that keeps "the SDK has the class" separate from
 *    "this phone can do it",
 *  - `BluetoothPlatformCapabilities.candidateTransports` — a kind this platform *could* offer, which
 *    is a candidate, not a capability of any headset.
 *
 * A boundary that could ask the platform module directly would be a sideways edge, and the honest
 * Phase 2 statement is that availability questions are answered by the session layer combining a
 * platform capability record with a [TransportBoundary] per candidate.
 *
 * Implementing phase: Phase 10 (`BluetoothOperation.LE_AUDIO_SESSION_INSPECTION`,
 * authorizedInPhase = 10) for inspection through platform APIs; control traffic over an LE Audio
 * path is Phase 6 territory at the earliest, and nothing here claims either.
 *
 * The boundary pins [com.omnibuds.core.common.TransportKind.LE_AUDIO] as its [kind] and inherits
 * [BluetoothTransport.probeAvailability], which until Phase 10 can only ever answer from
 * platform-side facts.
 */
interface LeAudioTransport : BluetoothTransport {

    /** Pinned: this boundary answers for exactly one transport kind, never another. */
    override val kind: com.omnibuds.core.common.TransportKind
        get() = com.omnibuds.core.common.TransportKind.LE_AUDIO
}
