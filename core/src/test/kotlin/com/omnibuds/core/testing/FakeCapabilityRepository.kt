package com.omnibuds.core.testing

import com.omnibuds.core.common.OmniBudsError
import com.omnibuds.core.common.OmniBudsErrorCategory
import com.omnibuds.core.common.OperationOutcome
import com.omnibuds.core.persistence.CapabilityRepository
import com.omnibuds.core.persistence.DiscoveredCapabilityRecord

/**
 * An in-memory [CapabilityRepository] for tests.
 *
 * **THIS IS NOT STORAGE AND NOT DISCOVERY.** A test double confined to `src/test` so it
 * cannot ship (Phase 1 prompt sections 26, 27 and 53). It stores what it is given and returns
 * that same instance - nothing is re-discovered, nothing is merged, and no evidence tier is
 * upgraded on the way out. A test passing against this type proves something about the code
 * under test, never about whether a capability was verified on hardware.
 *
 * The one behaviour it goes out of its way to get right: a load for a key it holds nothing
 * for returns `Success(null)`, meaning "nothing is known", and never an empty capability set
 * dressed up as a finding. Handing back a synthesised snapshot is how a fake teaches a caller
 * to report features as `UNSUPPORTED` for a device nobody ever asked (SEC-UNK-009, master
 * section 53) - the precise confusion this cache exists to avoid.
 *
 * Not thread-safe; use from one test coroutine.
 */
class FakeCapabilityRepository : CapabilityRepository {

    private val stored: MutableMap<String, DiscoveredCapabilityRecord> = mutableMapOf()

    /** Every key handed to [load], in call order. */
    val loadCalls: MutableList<String> = mutableListOf()

    /** Every record handed to [store], in call order, including refusals. */
    val storedRecords: MutableList<DiscoveredCapabilityRecord> = mutableListOf()

    /** Every key handed to [clear], in call order. */
    val clearedKeys: MutableList<String> = mutableListOf()

    /**
     * Verdicts queued for [store]. Consulted only when non-empty, and a consumed verdict
     * caches nothing: a store that failed must not leave a half-written entry behind.
     */
    val storeVerdicts: ScriptedOutcome<DiscoveredCapabilityRecord> = ScriptedOutcome()

    /** The keys currently cached, for asserting that an operation stored nothing. */
    fun cachedKeys(): Set<String> = stored.keys.toSet()

    override suspend fun load(identityKey: String): OperationOutcome<DiscoveredCapabilityRecord?> {
        loadCalls.add(identityKey)
        return OperationOutcome.Success(stored[identityKey])
    }

    override suspend fun store(record: DiscoveredCapabilityRecord): OperationOutcome<DiscoveredCapabilityRecord> {
        storedRecords.add(record)
        if (record.identityKey.isBlank()) {
            return OperationOutcome.Failure(
                OmniBudsError.of(
                    category = OmniBudsErrorCategory.INVALID_STATE,
                    operationId = OPERATION_STORE,
                    detail = "$DOUBLE_MARKER refusing to cache: a blank identityKey binds this " +
                        "snapshot to no device, so it could never be invalidated when the device changed",
                ),
            )
        }
        if (storeVerdicts.hasNext) return storeVerdicts.next()
        stored[record.identityKey] = record
        return OperationOutcome.Success(record)
    }

    override suspend fun clear(identityKey: String): OperationOutcome<Unit> {
        clearedKeys.add(identityKey)
        stored.remove(identityKey)
        return OperationOutcome.Success(Unit)
    }

    companion object {
        /** Stamped into every refusal so a leaked double is identifiable in any log line. */
        const val DOUBLE_MARKER: String = "FakeCapabilityRepository [test double, no storage]"

        private const val OPERATION_STORE = "persistence.capability.store"
    }
}
