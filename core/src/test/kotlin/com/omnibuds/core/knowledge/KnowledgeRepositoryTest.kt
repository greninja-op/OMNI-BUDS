package com.omnibuds.core.knowledge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Phase 22: repository tests.
 */
class KnowledgeRepositoryTest {

    private fun metadata() = RecordMetadata(
        recordVersion = 1,
        lifecycle = KnowledgeLifecycle.ACTIVE,
        createdAtMillis = 1000L,
        modifiedAtMillis = 1000L,
    )

    private fun manufacturer() = Manufacturer(
        id = ManufacturerId("m-acme"),
        name = "Acme",
        metadata = metadata(),
    )

    @Test
    fun `put and get manufacturer`() = runTest {
        val repo = InMemoryKnowledgeRepository()
        val result = repo.putManufacturer(manufacturer())
        assertTrue(result is PutResult.Inserted)
        assertEquals("Acme", repo.manufacturer(ManufacturerId("m-acme"))?.name)
    }

    @Test
    fun `update returns updated`() = runTest {
        val repo = InMemoryKnowledgeRepository()
        repo.putManufacturer(manufacturer())
        val result = repo.putManufacturer(manufacturer().copy(name = "Acme Inc."))
        assertTrue(result is PutResult.Updated)
        assertEquals("Acme Inc.", repo.manufacturer(ManufacturerId("m-acme"))?.name)
    }

    @Test
    fun `listings are deterministically ordered`() = runTest {
        val repo = InMemoryKnowledgeRepository()
        repo.putManufacturer(manufacturer().copy(id = ManufacturerId("m-b"), name = "B"))
        repo.putManufacturer(manufacturer().copy(id = ManufacturerId("m-a"), name = "A"))
        val names = repo.manufacturers().map { it.name }
        assertEquals(listOf("A", "B"), names)
    }

    @Test
    fun `save and load round-trip`() = runTest {
        val repo = InMemoryKnowledgeRepository()
        repo.putManufacturer(manufacturer())
        repo.putDeviceModel(
            DeviceModel(
                id = DeviceModelId("model-a"),
                manufacturerId = ManufacturerId("m-acme"),
                name = "Buds Pro",
                metadata = metadata(),
            ),
        )

        val store = mutableMapOf<String, String>()
        assertTrue(repo.saveAll(write = { k, v -> store[k] = v; true }))

        val repo2 = InMemoryKnowledgeRepository()
        val result = repo2.loadAll(read = { store[it] })
        assertEquals(0, result.corrupt.size)
        assertEquals(2, result.loaded)
        assertEquals("Acme", repo2.manufacturer(ManufacturerId("m-acme"))?.name)
        assertEquals("Buds Pro", repo2.deviceModel(DeviceModelId("model-a"))?.name)
    }

    @Test
    fun `corrupt record blocks load and is reported`() = runTest {
        val repo = InMemoryKnowledgeRepository()
        repo.putManufacturer(manufacturer())
        val store = mutableMapOf<String, String>()
        repo.saveAll(write = { k, v -> store[k] = v; true })
        // Corrupt one record.
        val key = store.keys.first { it.contains("manufacturer/m-acme") }
        store[key] = "{corrupted"

        val repo2 = InMemoryKnowledgeRepository()
        val result = repo2.loadAll(read = { store[it] })
        assertEquals(0, result.loaded)
        assertTrue(result.corrupt.isNotEmpty())
        // Nothing was committed.
        assertEquals(null, repo2.manufacturer(ManufacturerId("m-acme")))
    }

    @Test
    fun `save failure reported`() = runTest {
        val repo = InMemoryKnowledgeRepository()
        repo.putManufacturer(manufacturer())
        val ok = repo.saveAll(write = { _, _ -> false })
        assertTrue(!ok)
    }
}
