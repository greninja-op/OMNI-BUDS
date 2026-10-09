package com.omnibuds.core.knowledge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Phase 22: import/export tests.
 */
class KnowledgeImportExportTest {

    private fun metadata() = RecordMetadata(
        recordVersion = 1,
        lifecycle = KnowledgeLifecycle.ACTIVE,
        createdAtMillis = 1000L,
        modifiedAtMillis = 1000L,
    )

    private fun samplePackage(): String {
        val m = Manufacturer(
            id = ManufacturerId("m-acme"), name = "Acme", metadata = metadata(),
        )
        val model = DeviceModel(
            id = DeviceModelId("model-a"),
            manufacturerId = ManufacturerId("m-acme"),
            name = "Buds Pro", metadata = metadata(),
        )
        return KnowledgePackage.export(
            manufacturers = listOf(m),
            deviceModels = listOf(model),
            firmwareProfiles = emptyList(),
            protocols = emptyList(),
            messageSchemas = emptyList(),
            capabilities = emptyList(),
            operations = emptyList(),
            evidenceRecords = emptyList(),
            claims = emptyList(),
            sources = emptyList(),
            exporter = "test",
        )
    }

    @Test
    fun `export is deterministic`() {
        assertEquals(samplePackage(), samplePackage())
    }

    @Test
    fun `valid package validates`() {
        val result = KnowledgePackage.validate(samplePackage())
        assertTrue(result is PackageValidation.Valid)
        assertEquals(2, (result as PackageValidation.Valid).records.size)
    }

    @Test
    fun `malformed package rejected`() {
        val result = KnowledgePackage.validate("{not json")
        assertTrue(result is PackageValidation.Invalid)
    }

    @Test
    fun `unsupported version rejected`() {
        val pkg = samplePackage().replace(
            "\"packageVersion\":1", "\"packageVersion\":99",
        )
        val result = KnowledgePackage.validate(pkg)
        assertTrue(result is PackageValidation.Invalid)
        assertTrue((result as PackageValidation.Invalid).errors.any { it.contains("99") })
    }

    @Test
    fun `unresolved reference rejected`() {
        // Model references a manufacturer not in the package.
        val model = DeviceModel(
            id = DeviceModelId("model-x"),
            manufacturerId = ManufacturerId("m-missing"),
            name = "Ghost", metadata = metadata(),
        )
        val pkg = KnowledgePackage.export(
            manufacturers = emptyList(),
            deviceModels = listOf(model),
            firmwareProfiles = emptyList(),
            protocols = emptyList(),
            messageSchemas = emptyList(),
            capabilities = emptyList(),
            operations = emptyList(),
            evidenceRecords = emptyList(),
            claims = emptyList(),
            sources = emptyList(),
            exporter = "test",
        )
        val result = KnowledgePackage.validate(pkg)
        assertTrue(result is PackageValidation.Invalid)
    }

    @Test
    fun `duplicate identifiers rejected`() {
        val m = Manufacturer(
            id = ManufacturerId("m-acme"), name = "Acme", metadata = metadata(),
        )
        // Export twice the same record by crafting a package manually.
        val doc = mapOf(
            "packageVersion" to 1,
            "schemaVersion" to KnowledgeCodecs.SCHEMA_VERSION,
            "exporter" to "test",
            "recordCount" to 2,
            "records" to listOf(KnowledgeCodecs.encode(m), KnowledgeCodecs.encode(m)),
        )
        val result = KnowledgePackage.validate(KnowledgeJson.encode(doc))
        assertTrue(result is PackageValidation.Invalid)
        assertTrue((result as PackageValidation.Invalid).errors.any { it.contains("duplicate") })
    }

    @Test
    fun `import applies valid package`() = runTest {
        val repo = InMemoryKnowledgeRepository()
        val result = KnowledgePackage.import(samplePackage(), repo)
        assertTrue(result is ImportResult.Applied)
        assertEquals(2, (result as ImportResult.Applied).recordCount)
        assertEquals("Acme", repo.manufacturer(ManufacturerId("m-acme"))?.name)
    }

    @Test
    fun `failed import writes nothing`() = runTest {
        val repo = InMemoryKnowledgeRepository()
        val result = KnowledgePackage.import("{bad", repo)
        assertTrue(result is ImportResult.Failed)
        assertTrue(repo.manufacturers().isEmpty())
    }

    @Test
    fun `oversized package rejected`() {
        val big = "x".repeat(KnowledgePackage.MAX_PACKAGE_BYTES + 1)
        val result = KnowledgePackage.validate(big)
        assertTrue(result is PackageValidation.Invalid)
    }
}
