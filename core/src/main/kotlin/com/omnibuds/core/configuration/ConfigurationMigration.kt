package com.omnibuds.core.configuration

import com.omnibuds.core.config.ConfigurationValue

/**
 * Schema migrations.
 *
 * Phase 17 (OB-P17-REQ-019): deterministic, testable, atomic where storage
 * permits. Unknown future schemas are never silently overwritten.
 */

/** One migration step: fromVersion → fromVersion + 1. */
interface ConfigurationMigration {
    val fromVersion: Int
    val toVersion: Int get() = fromVersion + 1

    /**
     * Migrate preferences. Must preserve valid data; may add defaults with
     * documented rationale. Must not invent hardware state.
     */
    fun migrate(preferences: Map<String, ConfigurationValue>): Map<String, ConfigurationValue>
}

/** Registry of migrations, applied in order. */
class MigrationRegistry(
    private val migrations: List<ConfigurationMigration>,
) {
    init {
        val sorted = migrations.sortedBy { it.fromVersion }
        // Versions must form a contiguous chain.
        sorted.forEachIndexed { index, m ->
            require(m.fromVersion == ConfigurationSchema.MIN_SUPPORTED_VERSION + index) {
                "migration chain must be contiguous from version ${ConfigurationSchema.MIN_SUPPORTED_VERSION}"
            }
        }
    }

    /**
     * Migrate from [storedVersion] to current.
     * @throws MigrationException when no path exists (e.g. future schema).
     */
    fun migrateToCurrent(
        storedVersion: Int,
        preferences: Map<String, ConfigurationValue>,
    ): Map<String, ConfigurationValue> {
        if (storedVersion > ConfigurationSchema.CURRENT_VERSION) {
            throw MigrationException(
                "stored schema v$storedVersion is newer than supported v${ConfigurationSchema.CURRENT_VERSION}; refusing to overwrite",
            )
        }
        var current = storedVersion
        var prefs = preferences
        val byFrom = migrations.associateBy { it.fromVersion }
        while (current < ConfigurationSchema.CURRENT_VERSION) {
            val migration = byFrom[current]
                ?: throw MigrationException("no migration from v$current")
            prefs = migration.migrate(prefs)
            current++
        }
        return prefs
    }
}

class MigrationException(message: String) : Exception(message)
