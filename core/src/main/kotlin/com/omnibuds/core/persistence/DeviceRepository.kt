package com.omnibuds.core.persistence

import com.omnibuds.core.common.OperationOutcome

/**
 * The seam between the app and whatever stores the user's saved devices.
 *
 * Phase 1 defines this contract and deliberately implements nothing (Phase 1 prompt
 * sections 25 and 51): no database, no file, no serialization format. Its job is to keep
 * a UI, a service or a future session manager from touching storage directly, so that the
 * two product rules below live in exactly one place instead of being re-decided at every
 * call site.
 *
 * **Rule 1 - saving happens only by explicit user action.** Every write reaching this
 * interface originates from the user choosing "Add to My Devices" (or from an equally
 * deliberate edit they made). No code path may save a device as a side effect of
 * connecting to it, reconnecting, discovering it, reading its state, or deciding it looks
 * familiar (ADR-P0-004, master section 5, SEC-ID-005).
 *
 * **Rule 2 - a device that was merely detected is never written here.** Scanning is not
 * saving (SEC-ID-006). A device that appeared in a scan result and went away belongs to no
 * store at all: it is a transient observation whose correct lifetime is the session that
 * observed it. An implementation that keeps a "recently seen" table behind this interface
 * has broken ADR-P0-004 no matter what this interface's method names say.
 *
 * The consequence that makes the two rules worth having: [savedDevices] is the user's
 * list, and it is short by construction. An empty result is the normal state for a new
 * install and must never be repaired by re-populating it from scan history.
 */
interface DeviceRepository {

    /**
     * The devices the user saved, in whatever order the store prefers.
     *
     * An empty list is a real answer, not a failure and not a gap to fill.
     */
    suspend fun savedDevices(): OperationOutcome<List<SavedDeviceRecord>>

    /**
     * Stores [record] as a device the user chose to keep.
     *
     * A blank `identityKey` is refused with `OmniBudsErrorCategory.INVALID_STATE` rather
     * than stored: a blank key identifies nothing, and accepting it would put an entry in
     * the user's list that cannot be looked up, updated, or deleted by [forget] in one
     * action as SEC-ID-007 requires.
     *
     * Saving never implies a write to the device. It records what the app already knows
     * locally; it does not tell the headset anything about being saved, and no command is
     * sent on the user's behalf from here (SEC-WRITE-001, Phase 1 prompt section 51).
     */
    suspend fun save(record: SavedDeviceRecord): OperationOutcome<SavedDeviceRecord>

    /**
     * Deletes the local record for [identityKey], completely.
     *
     * **This is a local operation only.** It removes OmniBuds's own data about the device;
     * it must not imply that anything was removed *from the device*. Pairing, bonding and
     * saved settings live in the operating system and in the headset, and OmniBuds neither
     * owns nor unlinks them - a delete that also sent an "unlink" command would be a write
     * to hardware that the user never asked for.
     *
     * Complete local deletion covers identifier, fingerprint re-derivations, captured
     * metadata, cached protocol observations for that device and derived logs
     * (SEC-ID-007); an implementation that clears the record but leaves the capability
     * cache behind has not forgotten the device.
     *
     * Forgetting a key that is not stored succeeds without changing anything: the user's
     * intent, deletion, is already satisfied, and reporting a failure would claim a device
     * was still tracked.
     */
    suspend fun forget(identityKey: String): OperationOutcome<Unit>

    /**
     * The stored record for [identityKey], or `Success(null)` when the user never saved it.
     *
     * `null` here means "not in the user's list", which says nothing about whether the
     * device exists, was ever seen, or is connected right now.
     */
    suspend fun find(identityKey: String): OperationOutcome<SavedDeviceRecord?>

    /**
     * Restates `lastSeenEpochMillis` on an already-saved device.
     *
     * The one thing this may never do is create a record. Seeing a device is an
     * observation, and observations do not earn a place in the user's list (SEC-ID-006);
     * an implementation asked to mark a key it does not hold must report that rather than
     * quietly inserting a row, because an insert here is exactly how "the app kept every
     * device it ever saw" starts.
     */
    suspend fun markSeen(identityKey: String, atEpochMillis: Long): OperationOutcome<Unit>
}
