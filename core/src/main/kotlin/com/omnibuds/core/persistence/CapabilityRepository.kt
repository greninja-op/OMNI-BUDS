package com.omnibuds.core.persistence

import com.omnibuds.core.common.OperationOutcome

/**
 * The seam to the per-device cache of what discovery established.
 *
 * Contracts only in Phase 1 (Phase 1 prompt section 25): nothing here defines how a record
 * is serialised, where it lives, or how long it survives, and this package must not grow a
 * storage implementation. Separating this from [DeviceRepository] is not ceremony - the two
 * hold different things for different reasons:
 *
 * - [DeviceRepository] holds the devices the *user chose to keep*. Its contents answer a
 *   question the user asked, and only the user can change the answer.
 * - This interface holds what *discovery concluded* about a device. Its contents are
 *   machine-derived, re-derivable, and safe to discard: losing the entire cache costs a
 *   re-discovery pass, while losing a saved device costs the user something they made.
 *
 * Because the cache is re-derivable, an implementation may evict it, expire it or drop it
 * wholesale. Because saved devices are not, no such licence exists for [DeviceRepository].
 *
 * **A cache hit is a hint, never a finding.** [load] returning a record says that some past
 * session reached some conclusion; it does not say the conclusion still holds, and it is
 * not evidence for one (SEC-WRITE-007, master section 54). A cached `PERSISTENCE_VERIFIED`
 * may not be presented as verified for a *different* firmware than the one it was
 * established against - `DiscoveredCapabilityRecord.identityKey` cannot distinguish the
 * two, so only a firmware read in the current session can. Consumers that have no such
 * reading present the cached state as unknown-or-hinted, not as established, and the
 * recorded `verification` level exists so that refusing a stale cache is a check a later
 * phase can actually perform rather than a rule it has to guess at.
 */
interface CapabilityRepository {

    /**
     * The cached snapshot for [identityKey], or `Success(null)` when nothing is known.
     *
     * `null` is the only honest "no data" answer here. An implementation must not return an
     * empty-but-unsupported capability set in its place: absence of a cache entry and a
     * proven absence of features are different statements, and conflating them is how "we
     * never looked" becomes "this device cannot do it" (SEC-UNK-009, master section 53).
     */
    suspend fun load(identityKey: String): OperationOutcome<DiscoveredCapabilityRecord?>

    /**
     * Replaces the cached snapshot for the record's `identityKey`.
     *
     * Writing here asserts nothing about the device: it records that a discovery pass
     * happened, with the evidence tier that pass claimed. Storing is not evidence, and a
     * stored claim may not be reported above the tier [DiscoveredCapabilityRecord.verification]
     * carries.
     */
    suspend fun store(record: DiscoveredCapabilityRecord): OperationOutcome<DiscoveredCapabilityRecord>

    /**
     * Discards the cached snapshot for [identityKey].
     *
     * Called when the device is forgotten, when its firmware changed under the cache, and
     * when a session decides a stale conclusion is worse than no conclusion. Clearing the
     * cache is always safe; that asymmetry with [DeviceRepository.forget] is deliberate.
     */
    suspend fun clear(identityKey: String): OperationOutcome<Unit>
}
