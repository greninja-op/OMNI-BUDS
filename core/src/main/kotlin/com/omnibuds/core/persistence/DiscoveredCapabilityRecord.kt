package com.omnibuds.core.persistence

import com.omnibuds.core.capability.DeviceCapabilities
import com.omnibuds.core.state.VerificationLevel

/**
 * What discovery established about one device, kept so a later session does not start
 * from zero.
 *
 * This is a *cache of past conclusions*, not a record of present facts, and the difference
 * is the whole point of [verification] being carried alongside [capabilities]. A cached
 * snapshot can justify trying a protocol again, and can justify pre-filling a UI hint; it
 * can never justify reporting a state as currently true, because nothing here was read
 * from the device during this session.
 *
 * **Storing is not evidence.** A `PERSISTENCE_VERIFIED` entry in [capabilities] means that
 * some earlier session completed the full READ, WRITE, READ BACK, VERIFY, DISCONNECT,
 * RECONNECT, READ AGAIN chain on hardware that was present then (SEC-WRITE-007,
 * ADR-P0-014). Writing that conclusion to disk adds no evidence to it, and a cache hit
 * therefore earns nothing: a later phase may reuse it as a hint and must re-verify before
 * it displays a durability claim.
 *
 * Holds no identifiers beyond [identityKey]: no address, no manufacturer payload, no
 * service or characteristic UUID set (SEC-ID-004). Cached *capability* knowledge is a
 * claim about features, not an observation archive, and [DeviceCapabilities] is built that
 * way already.
 *
 * **A cache entry cannot be keyed by firmware, because `identityKey` ignores it.**
 * `DeviceFingerprint.identityKey()` deliberately excludes version evidence, so an updated
 * device re-presents the same key. That is exactly why a cached `PERSISTENCE_VERIFIED`
 * must not be presented as verified for a *different* firmware: this record simply cannot
 * tell the two firmwares apart, and treating the shared key as if it could would promote a
 * stale claim onto a device that has since changed underneath it. A consumer must compare
 * against the firmware actually read in the current session, and when nothing has been
 * read yet the honest reading of this record is "hint, unverified". A later phase that
 * genuinely needs per-firmware capability caching must extend this record with the version
 * it was established against; it must not overload `identityKey` with that meaning.
 */
data class DiscoveredCapabilityRecord(
    /** The same fingerprint-derived, address-free key [SavedDeviceRecord] uses. */
    val identityKey: String,

    /**
     * The conclusions discovery reached, feature by feature.
     *
     * An empty container is an honest entry meaning "this device was examined and nothing
     * was established"; it is not the same statement as a container holding
     * `CapabilityState.UNSUPPORTED` records, which claims absence was proven
     * (master section 53).
     */
    val capabilities: DeviceCapabilities,

    /**
     * When this snapshot was taken, or null when the store could not say.
     *
     * Null does not mean "recent". A consumer that wants to refuse a stale cache needs a
     * timestamp, and an absent one is a reason to treat the entry as a weaker hint, not as
     * a fresh result.
     */
    val discoveredAtEpochMillis: Long?,

    /**
     * The evidence tier the snapshot as a whole was taken at.
     *
     * Recorded so that a later phase can refuse a stale or under-supported cache without
     * re-reading hardware to work out whether it may trust a stored value. It is the floor
     * for the whole snapshot: individual features may sit below it, and none of them may be
     * reported above it on the strength of this record alone.
     */
    val verification: VerificationLevel,
)
