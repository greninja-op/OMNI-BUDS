package com.omnibuds.core.verification

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Storage seam for verification records.
 *
 * Phase 18 (§11): structurally mirrors Phase 17's ConfigurationStorage
 * but lives in this package to respect layer boundaries (both are layer 5;
 * sideways imports are forbidden). Callers bridge the two with a trivial
 * adapter.
 */
interface VerificationStorage {
    suspend fun read(key: String): String?
    suspend fun write(key: String, value: String): Boolean
    suspend fun delete(key: String): Boolean
}

/**
 * Persists verification records and evidence.
 *
 * Phase 18 (§11): reuses Phase 17's storage mechanisms via the
 * [VerificationStorage] seam — no parallel database. Records are versioned;
 * interrupted verifications can be recovered; recovery never marks
 * incomplete work as complete.
 */
class VerificationRepository(
    private val storage: VerificationStorage,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val mutex = Mutex()

    companion object {
        const val SCHEMA_VERSION: Int = 1
        internal fun recordKey(id: VerificationId): String = "verification:${id.value}"
        internal fun deviceIndexKey(deviceKey: String): String = "verification-index:$deviceKey"
    }

    /** Store or update a verification record. */
    suspend fun save(record: VerificationRecord): Boolean = mutex.withLock {
        try {
            storage.write(recordKey(record.id), VerificationRecordCodec.encode(record))
        } catch (e: Exception) {
            false
        }
    }

    /** Load a record, or null when absent or corrupt. */
    suspend fun load(id: VerificationId): VerificationRecord? = mutex.withLock {
        val raw = try {
            storage.read(recordKey(id))
        } catch (e: Exception) {
            return@withLock null
        } ?: return@withLock null
        VerificationRecordCodec.decode(raw)
    }

    /** All verification IDs for a device (for recovery and retention). */
    suspend fun listForDevice(deviceKey: String): List<VerificationId> = mutex.withLock {
        val raw = try {
            storage.read(deviceIndexKey(deviceKey))
        } catch (e: Exception) {
            return@withLock emptyList()
        } ?: return@withLock emptyList()
        raw.split(",").filter { it.isNotBlank() }.map { VerificationId.of(it) }
    }

    /** Index a record under its device. */
    suspend fun index(record: VerificationRecord): Boolean = mutex.withLock {
        val existing = try {
            storage.read(deviceIndexKey(record.deviceKey))
        } catch (e: Exception) {
            ""
        } ?: ""
        val ids = (existing.split(",").filter { it.isNotBlank() } + record.id.value).distinct()
        try {
            storage.write(deviceIndexKey(record.deviceKey), ids.joinToString(","))
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Recover interrupted verifications for a device.
     * Returns records that were not terminal at last save. Callers must
     * re-evaluate — recovery never completes a verification.
     */
    suspend fun recoverInterrupted(deviceKey: String): List<VerificationRecord> {
        val ids = listForDevice(deviceKey)
        return ids.mapNotNull { load(it) }.filter { !it.isTerminal }
    }

    /** Delete a verification record and de-index it. */
    suspend fun delete(id: VerificationId, deviceKey: String): Boolean = mutex.withLock {
        val ok = try {
            storage.delete(recordKey(id))
        } catch (e: Exception) {
            false
        }
        if (!ok) return@withLock false
        val raw = try {
            storage.read(deviceIndexKey(deviceKey))
        } catch (e: Exception) {
            return@withLock true
        } ?: return@withLock true
        val ids = raw.split(",").filter { it.isNotBlank() && it != id.value }
        try {
            storage.write(deviceIndexKey(deviceKey), ids.joinToString(","))
        } catch (e: Exception) {
            false
        }
    }
}
