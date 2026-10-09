package com.omnibuds.core.protocol.version

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProtocolSchemaMigrationTest {

    private val migrator = ProtocolSchemaMigrator()

    @Test
    fun successfulMigrationFromSchema1To2() {
        val v1Record = StoredProtocolRecord(
            protocolId = "acme.buds.v1",
            vendorNamespace = "acme",
            versionRaw = "1.0.0",
            schemaVersion = 1,
            transportRaw = "RFCOMM",
            confidenceRaw = "LAB_TESTED",
            evidenceIds = listOf("EVID-001", "EVID-002"),
        )

        val result = migrator.migrate(v1Record, targetSchemaVersion = 2)
        assertTrue(result is MigrationResult.Success)
        val migrated = result.migratedRecord
        assertEquals(2, migrated.schemaVersion)
        assertEquals(listOf("EVID-001", "EVID-002"), migrated.evidenceIds, "evidence IDs must be preserved")
        assertEquals("1", migrated.properties["migrated_from_schema"])
    }

    @Test
    fun migrationIsIdempotent() {
        val v2Record = StoredProtocolRecord(
            protocolId = "acme.buds.v2",
            vendorNamespace = "acme",
            versionRaw = "2.0.0",
            schemaVersion = 2,
            transportRaw = "RFCOMM",
            confidenceRaw = "LAB_TESTED",
        )

        val result = migrator.migrate(v2Record, targetSchemaVersion = 2)
        assertTrue(result is MigrationResult.Success)
        assertEquals(2, result.migratedRecord.schemaVersion)
    }

    @Test
    fun rejectsDowngrade() {
        val v2Record = StoredProtocolRecord(
            protocolId = "acme.buds.v2",
            vendorNamespace = "acme",
            versionRaw = "2.0.0",
            schemaVersion = 2,
            transportRaw = "RFCOMM",
            confidenceRaw = "LAB_TESTED",
        )

        val result = migrator.migrate(v2Record, targetSchemaVersion = 1)
        assertTrue(result is MigrationResult.Failure)
        assertTrue(result.reason.contains("cannot downgrade"))
    }
}
