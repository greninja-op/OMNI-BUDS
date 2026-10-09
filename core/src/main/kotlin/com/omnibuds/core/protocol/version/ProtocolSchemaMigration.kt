package com.omnibuds.core.protocol.version

import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.state.VerificationLevel

/**
 * Persisted protocol metadata entity representing stored definitions in repositories.
 */
data class StoredProtocolRecord(
    val protocolId: String,
    val vendorNamespace: String?,
    val versionRaw: String,
    val schemaVersion: Int,
    val transportRaw: String,
    val confidenceRaw: String,
    val supportedModels: List<String> = emptyList(),
    val verifiedFirmware: List<String>? = null,
    val evidenceIds: List<String> = emptyList(),
    val properties: Map<String, String> = emptyMap(),
)

/**
 * Migration outcome for protocol metadata records.
 */
sealed interface MigrationResult {
    data class Success(val migratedRecord: StoredProtocolRecord) : MigrationResult
    data class Failure(val reason: String, val cause: Throwable? = null) : MigrationResult
}

/**
 * Migrator interface for stepping protocol metadata across schema revisions.
 */
fun interface ProtocolSchemaMigrationStep {
    fun migrate(record: StoredProtocolRecord): StoredProtocolRecord
}

/**
 * Transactional schema migration pipeline.
 *
 * Ensures migrations preserve evidence provenance, author data, and are idempotent.
 */
class ProtocolSchemaMigrator {

    private val migrationSteps = mapOf<Int, ProtocolSchemaMigrationStep>(
        // Schema 1 -> Schema 2: Normalizes transport and verifies evidenceIds provenance list
        1 to ProtocolSchemaMigrationStep { record ->
            require(record.schemaVersion == 1) { "source schema version must be 1" }
            record.copy(
                schemaVersion = 2,
                properties = record.properties + ("migrated_from_schema" to "1"),
            )
        },
    )

    /**
     * Migrate a stored record to [targetSchemaVersion].
     */
    fun migrate(
        record: StoredProtocolRecord,
        targetSchemaVersion: Int = ProtocolSchemaVersion.CURRENT.version,
    ): MigrationResult {
        if (record.schemaVersion == targetSchemaVersion) {
            return MigrationResult.Success(record)
        }

        if (record.schemaVersion > targetSchemaVersion) {
            return MigrationResult.Failure(
                "cannot downgrade schema from ${record.schemaVersion} to $targetSchemaVersion",
            )
        }

        var current = record
        val initialEvidence = record.evidenceIds.toList()

        try {
            while (current.schemaVersion < targetSchemaVersion) {
                val step = migrationSteps[current.schemaVersion]
                    ?: return MigrationResult.Failure(
                        "no migration step registered from schema ${current.schemaVersion} to ${current.schemaVersion + 1}",
                    )

                val next = step.migrate(current)
                require(next.schemaVersion == current.schemaVersion + 1) {
                    "migration step did not increment schema version correctly"
                }

                // Invariant: Evidence provenance must be preserved across migration
                require(next.evidenceIds == initialEvidence) {
                    "migration step altered or discarded historical evidence IDs"
                }

                current = next
            }
            return MigrationResult.Success(current)
        } catch (e: Exception) {
            return MigrationResult.Failure(
                "migration failed at schema ${current.schemaVersion}: ${e.message}",
                e,
            )
        }
    }
}
