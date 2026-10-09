package com.omnibuds.android.bluetooth.storage

import com.omnibuds.core.configuration.ConfigurationStorage
import com.omnibuds.core.configuration.StorageException
import java.io.File

/**
 * File-based configuration storage with atomic writes.
 *
 * Phase 17 (OB-P17-REQ-011, OB-P17-REQ-012): writes go to a temp file then
 * rename — readers never observe a partial write. Each key maps to one
 * file; keys are sanitized to safe filenames.
 *
 * Android platform adapter: the core domain never touches `java.io`
 * directly (core forbids JVM-only libraries). This adapter lives behind
 * the platform boundary.
 */
class FileConfigurationStorage(
    private val directory: File,
) : ConfigurationStorage {

    init {
        require(directory.isDirectory || directory.mkdirs()) {
            "Cannot create configuration directory: $directory"
        }
    }

    private fun fileFor(key: String): File {
        // Sanitize: only alphanumerics, dash, underscore, dot.
        val safe = key.replace(Regex("[^A-Za-z0-9._-]"), "_")
        require(safe.isNotBlank()) { "key sanitizes to empty filename" }
        return File(directory, "$safe.json")
    }

    override suspend fun read(key: String): String? {
        val file = fileFor(key)
        return if (file.isFile) {
            try {
                file.readText(Charsets.UTF_8)
            } catch (e: Exception) {
                throw StorageException("read failed for key $key", e)
            }
        } else {
            null
        }
    }

    override suspend fun write(key: String, value: String): Boolean {
        val file = fileFor(key)
        val temp = File(directory, "${file.name}.tmp")
        return try {
            temp.writeText(value, Charsets.UTF_8)
            // Atomic rename: readers never see a partial file.
            if (!temp.renameTo(file)) {
                temp.delete()
                false
            } else {
                true
            }
        } catch (e: Exception) {
            temp.delete()
            false
        }
    }

    override suspend fun delete(key: String): Boolean {
        val file = fileFor(key)
        return if (file.exists()) file.delete() else true
    }
}
