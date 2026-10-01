package com.omnibuds.core.testing

import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.persistence.DeviceRepository
import com.omnibuds.core.persistence.SavedDeviceRecord

/**
 * An in-memory [DeviceRepository] for tests.
 *
 * **THIS IS NOT STORAGE AND NOT HARDWARE.** It is a test double that lives in `src/test` so
 * that it cannot ship and cannot be wired into a production graph (Phase 1 prompt sections
 * 26, 27 and 53). It talks to no database, no file, no Android context and no device; every
 * value it returns is whatever a `MutableMap` was handed earlier in the same test. Anything a
 * real repository owes - durability across process death, encryption at rest, migration -
 * this type explicitly does not provide, and no conclusion about those properties may be
 * drawn from a test that passes here.
 *
 * It also refuses to be agreeable. The saved-device rules are enforced rather than
 * simulated: a blank `identityKey` is rejected as `INVALID_STATE`, `markSeen` will not insert
 * a record for a device the user never saved, and a scripted failure or cancellation on a
 * write leaves the store untouched, so a failed save cannot be mistaken for a stored one.
 *
 * Every call is recorded for assertions. Not thread-safe; use from one test coroutine, which
 * is the only context it was written for.
 */
class FakeDeviceRepository : DeviceRepository {

    private val records: MutableMap<String, SavedDeviceRecord> = mutableMapOf()

    /** Every record handed to [save], in call order, including the refusals. */
    val savedCalls: MutableList<SavedDeviceRecord> = mutableListOf()

    /** Every key handed to [forget], in call order. */
    val forgottenKeys: MutableList<String> = mutableListOf()

    /** Every key handed to [find], in call order. */
    val findCalls: MutableList<String> = mutableListOf()

    /** Every key-and-time pair handed to [markSeen], in call order. */
    val markSeenCalls: MutableList<MarkSeenCall> = mutableListOf()

    /** How many times [savedDevices] was asked. */
    var savedDevicesCalls: Int = 0
        private set

    /**
     * Verdicts queued for [save]. Consulted only when non-empty, and a consumed verdict
     * writes nothing: a save that failed or was cancelled must leave the list as it found it.
     */
    val saveVerdicts: ScriptedOutcome<SavedDeviceRecord> = ScriptedOutcome()

    /** Verdicts queued for [forget], with the same consume-and-write-nothing rule. */
    val forgetVerdicts: ScriptedOutcome<Unit> = ScriptedOutcome()

    /** The keys currently held, for asserting that an operation stored nothing. */
    fun storedKeys(): Set<String> = records.keys.toSet()

    override suspend fun savedDevices(): OperationOutcome<List<SavedDeviceRecord>> {
        savedDevicesCalls += 1
        return OperationOutcome.Success(records.values.sortedBy { record -> record.identityKey })
    }

    override suspend fun save(record: SavedDeviceRecord): OperationOutcome<SavedDeviceRecord> {
        savedCalls.add(record)
        if (record.identityKey.isBlank()) {
            return OperationOutcome.Failure(
                OmniBudsError.of(
                    category = OmniBudsErrorCategory.INVALID_STATE,
                    operationId = OPERATION_SAVE,
                    detail = "$DOUBLE_MARKER refusing to save: a blank identityKey identifies no " +
                        "device, and storing one could never be looked up or forgotten again",
                ),
            )
        }
        if (saveVerdicts.hasNext) return saveVerdicts.next()
        records[record.identityKey] = record
        return OperationOutcome.Success(record)
    }

    override suspend fun forget(identityKey: String): OperationOutcome<Unit> {
        forgottenKeys.add(identityKey)
        if (forgetVerdicts.hasNext) return forgetVerdicts.next()
        records.remove(identityKey)
        return OperationOutcome.Success(Unit)
    }

    override suspend fun find(identityKey: String): OperationOutcome<SavedDeviceRecord?> {
        findCalls.add(identityKey)
        return OperationOutcome.Success(records[identityKey])
    }

    override suspend fun markSeen(identityKey: String, atEpochMillis: Long): OperationOutcome<Unit> {
        markSeenCalls.add(MarkSeenCall(identityKey, atEpochMillis))
        val existing = records[identityKey]
            ?: return OperationOutcome.Failure(
                OmniBudsError.of(
                    category = OmniBudsErrorCategory.INVALID_STATE,
                    operationId = OPERATION_MARK_SEEN,
                    detail = "$DOUBLE_MARKER refusing to mark seen: this key is not a saved device, " +
                        "and inserting it here would be saving a merely detected device by the back door",
                ),
            )
        records[identityKey] = existing.copy(lastSeenEpochMillis = atEpochMillis)
        return OperationOutcome.Success(Unit)
    }

    /** One recorded `markSeen` call, kept so a test can assert the timestamp it supplied. */
    data class MarkSeenCall(val identityKey: String, val atEpochMillis: Long)

    companion object {
        /** Stamped into every refusal so a leaked double is identifiable in any log line. */
        const val DOUBLE_MARKER: String = "FakeDeviceRepository [test double, no storage]"

        private const val OPERATION_SAVE = "persistence.device.save"
        private const val OPERATION_MARK_SEEN = "persistence.device.markSeen"
    }
}
