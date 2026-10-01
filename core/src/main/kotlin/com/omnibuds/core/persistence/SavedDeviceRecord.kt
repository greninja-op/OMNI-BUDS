package com.omnibuds.core.persistence

import com.omnibuds.core.device.DeviceIdentity
import com.omnibuds.core.device.FirmwareInfo

/**
 * One entry in the user's "My Devices" list: what OmniBuds keeps after the user
 * explicitly chose to save a device (Phase 1 prompt section 25, ADR-P0-004).
 *
 * **Identifier minimisation.** This record holds no Bluetooth MAC address and no raw
 * manufacturer data, and a `DeviceFingerprint` is deliberately *not* a field here even
 * though a fingerprint is exactly what discovery produces. A fingerprint carries service
 * and characteristic UUIDs and manufacturer payloads — durable evidence about a device
 * that is equally a durable handle on a person's belongings. Storing it would turn "the
 * user saved this headset" into "the app archives every observation ever made of it",
 * which `docs/phases/phase-0/security-governance.md` SEC-ID-004 (keep the least
 * identifier content that makes the feature work, for the shortest period it is needed),
 * SEC-ID-006 and SEC-ID-007 argue against. Re-deriving a fingerprint for a saved device
 * is a fresh discovery pass, and discovery re-observes rather than reading an archive.
 *
 * [identityKey] is not an address either. It is the stable, address-free key produced by
 * `DeviceFingerprint.identityKey()`, whose canonical address separator `':'` is folded
 * out of every token so an address cannot be smuggled in through a free-text field
 * (SEC-ID-001, SEC-ID-003). Identity comes from the fingerprint-derived key, never from
 * an address.
 *
 * The two timestamps are supplied by the caller and nullable. This type reads no clock:
 * core stays platform-independent (Phase 1 prompt sections 7, 8 and 32), so an
 * implementation that cannot stamp a save stores null rather than guessing, and a
 * fabricated timestamp would be indistinguishable from a real one later.
 */
data class SavedDeviceRecord(
    /**
     * The fingerprint-derived, address-free key this device is filed under.
     *
     * A blank value is not a key: it is a caller that has not identified anything, and a
     * repository must refuse it rather than store it (see [DeviceRepository.save]).
     */
    val identityKey: String,

    /** Descriptive identity as established: manufacturer, model, name, model id. */
    val identity: DeviceIdentity,

    /**
     * Firmware evidence as read, or null when no version has ever been read.
     *
     * Null here is "not read", never "the device has no firmware version" (SEC-PRIV-006).
     * It also keeps an update from looking like a new device: a saved device whose
     * firmware changed is the same device with different version text.
     */
    val firmware: FirmwareInfo?,

    /**
     * When the user saved this device, or null when the save happened before stamping
     * existed or the stamp could not be taken. Null is not "never saved" — presence in the
     * repository is that statement.
     */
    val savedAtEpochMillis: Long?,

    /**
     * When this saved device was most recently seen, or null when it has not been seen
     * since the record was created.
     *
     * Purely informative. Being seen does not create this record and does not renew it:
     * `lastSeen` moving is an observation about a device the user already saved, never a
     * reason to save a device they did not.
     */
    val lastSeenEpochMillis: Long?,
) {
    companion object {
        /**
         * The record a save starts from, for a device whose identity is the only thing
         * established so far.
         *
         * Nothing is defaulted into a claim: [firmware] stays null because no version was
         * read, and no timestamp is invented for a save the caller has not stamped.
         */
        fun of(
            identityKey: String,
            identity: DeviceIdentity,
            firmware: FirmwareInfo? = null,
            savedAtEpochMillis: Long? = null,
        ): SavedDeviceRecord = SavedDeviceRecord(
            identityKey = identityKey,
            identity = identity,
            firmware = firmware,
            savedAtEpochMillis = savedAtEpochMillis,
            lastSeenEpochMillis = null,
        )
    }
}
