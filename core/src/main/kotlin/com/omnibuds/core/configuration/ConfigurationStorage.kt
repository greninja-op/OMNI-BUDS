package com.omnibuds.core.configuration

/**
 * Low-level key/value storage for serialized configurations.
 *
 * Phase 17 (OB-P17-REQ-011): the domain never touches storage directly.
 * Implementations must guarantee that a successful write is committed —
 * callers rely on this for the "Saved means committed" contract.
 */
interface ConfigurationStorage {

    /** Read the raw serialized value, or null when absent. */
    suspend fun read(key: String): String?

    /**
     * Write atomically. Returns true only when committed.
     * Implementations must not report success on partial writes.
     */
    suspend fun write(key: String, value: String): Boolean

    /** Delete the key. Returns true when the key is gone (or was absent). */
    suspend fun delete(key: String): Boolean
}

/**
 * In-memory storage for tests and ephemeral use.
 *
 * Not for production persistence — data does not survive restart.
 * Thread-safe via synchronized map.
 */
class InMemoryConfigurationStorage : ConfigurationStorage {
    private val map = mutableMapOf<String, String>()
    private val lock = Any()

    /** When true, writes fail — for testing failure paths. */
    var failWrites: Boolean = false

    /** When true, reads fail — for testing failure paths. */
    var failReads: Boolean = false

    override suspend fun read(key: String): String? = synchronized(lock) {
        if (failReads) throw StorageException("simulated read failure")
        map[key]
    }

    override suspend fun write(key: String, value: String): Boolean = synchronized(lock) {
        if (failWrites) return false
        map[key] = value
        true
    }

    override suspend fun delete(key: String): Boolean = synchronized(lock) {
        map.remove(key)
        true
    }
}

/** Storage failures surface as structured results, not silent corruption. */
class StorageException(message: String, cause: Throwable? = null) : Exception(message, cause)
