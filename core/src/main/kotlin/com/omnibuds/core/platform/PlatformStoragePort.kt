package com.omnibuds.core.platform

import com.omnibuds.core.common.OperationOutcome

/**
 * Key-value record storage port for platform-specific persistence implementations
 * (e.g. EncryptedSharedPreferences / DataStore on Android, local file/keyring on Desktop).
 *
 * Keeps physical file I/O, database schemas, and platform storage APIs out of common code.
 */
interface PlatformStoragePort {
    suspend fun get(key: String): OperationOutcome<String?>
    suspend fun set(key: String, value: String): OperationOutcome<Unit>
    suspend fun remove(key: String): OperationOutcome<Unit>
    suspend fun contains(key: String): OperationOutcome<Boolean>
    suspend fun clear(): OperationOutcome<Unit>
}

/**
 * In-memory storage implementation for tests, ephemeral caching, and headless validation.
 */
class InMemoryStoragePort : PlatformStoragePort {
    private val store = mutableMapOf<String, String>()

    override suspend fun get(key: String): OperationOutcome<String?> {
        require(key.isNotBlank()) { "key must not be blank" }
        return OperationOutcome.Success(store[key])
    }

    override suspend fun set(key: String, value: String): OperationOutcome<Unit> {
        require(key.isNotBlank()) { "key must not be blank" }
        store[key] = value
        return OperationOutcome.Success(Unit)
    }

    override suspend fun remove(key: String): OperationOutcome<Unit> {
        require(key.isNotBlank()) { "key must not be blank" }
        store.remove(key)
        return OperationOutcome.Success(Unit)
    }

    override suspend fun contains(key: String): OperationOutcome<Boolean> {
        require(key.isNotBlank()) { "key must not be blank" }
        return OperationOutcome.Success(store.containsKey(key))
    }

    override suspend fun clear(): OperationOutcome<Unit> {
        store.clear()
        return OperationOutcome.Success(Unit)
    }
}
