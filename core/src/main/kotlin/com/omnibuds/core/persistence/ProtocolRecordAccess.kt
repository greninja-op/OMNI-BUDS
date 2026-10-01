package com.omnibuds.core.persistence

import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.device.DeviceFingerprint

/**
 * The read-only seam to the global protocol knowledge base.
 *
 * **Two databases, on purpose.** OmniBuds keeps a *user device database* - which headsets
 * this user saved, what was discovered about them - and a *global protocol database* -
 * model-level knowledge such as "this service-and-characteristic shape is spoken by protocol
 * X, at confidence Y, verified against firmware Z" (master section 28). The second is a
 * shared reference work; the first is one person's belongings. `SEC-PRIV-004` makes the
 * boundary binding: a protocol entry holds model-level knowledge only, never an address, a
 * device name or a per-user timeline.
 *
 * **Why one class cannot read both.** A single repository would hold the only handle that
 * joins the two, and the join is precisely what must stay deliberate. Reading both from one
 * place makes it natural to key protocol knowledge by user device, or - the worse direction,
 * the one that leaks - to let a user's saved device list seed the global knowledge base.
 * Contribution of laboratory findings outward is a reviewed, explicit act, stripped of
 * user-identifying material first (`SEC-PRIV-005`); it is not a side effect of a cache miss.
 * Keeping the seams apart is what makes "no user identifier ever entered the protocol
 * database" a structural fact rather than a promise that has to be re-checked at every call
 * site.
 *
 * Read-only, and deliberately without any write method. Phase 1 has no protocol-authoring
 * story (master section 26 places that in the protocol laboratory, a much later phase), and a
 * write method here would be an unused affordance in a contract whose whole job is to gate
 * access.
 *
 * **Ids only.** Both operations return protocol *identifiers* and nothing else: never a
 * `ProtocolDefinition`, `CommandDefinition` or response shape, all of which belong to
 * `com.omnibuds.core.protocol`. That is a dependency-direction decision, not a gap in the
 * design: the protocol layer owns those types and indexes them in memory in its own
 * `ProtocolRegistry`, which is keyed by exactly the `protocolId` string handed out here. This
 * seam is where the durable knowledge base is *read from*, and handing back a protocol type
 * would make the persistence package depend on the protocol package for something whose only
 * job is to name a record - and would let the storage layer appear to own protocol truth.
 * A caller takes an id from here and resolves it through the protocol layer, which is where
 * `SEC-WRITE-001`'s "present in the protocol database as a structured entry, not reconstructed
 * at a call site" gets enforced (Phase 1 prompt section 52).
 *
 * [fingerprint] is an input, not stored data: it is the evidence handed in by the caller's
 * current discovery pass. Passing the fingerprint rather than a saved-device row is also what
 * keeps this seam usable for a device the user never saved - an unknown device may be looked
 * up, and finding no match is the normal, correct answer (SEC-UNK-001).
 */
interface ProtocolRecordAccess {

    /**
     * Ids of protocol records whose evidence matches [fingerprint], in the order the
     * knowledge base prefers candidates be tried.
     *
     * An empty list means "no documented protocol claims this shape", which leaves the
     * device unknown and therefore read-only (SEC-UNK-001). It is not a failure, and it is
     * not a hint to guess: selecting a write path by name match or heuristic is prohibited
     * outright (`SEC-UNK-008`), so the only thing a caller may do with an empty result is
     * keep reading.
     */
    suspend fun recordsFor(fingerprint: DeviceFingerprint): OperationOutcome<List<String>>

    /**
     * The stored entry named by [protocolId], or `Success(null)` when the knowledge base
     * holds no such id.
     *
     * A blank id is a lookup for nothing and is refused rather than resolved. `null` means
     * unknown, never "empty protocol". What comes back on a hit is text from the store, not a
     * materialised protocol record: an id is turned into a structured, evidence-tiered
     * definition by the protocol layer, and this seam only reports whether the store knows
     * the name.
     */
    suspend fun record(protocolId: String): OperationOutcome<String?>
}
