package com.omnibuds.core.knowledge

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Phase 22: data-quality tests.
 */
class KnowledgeQualityTest {

    private fun metadata() = RecordMetadata(
        recordVersion = 1,
        lifecycle = KnowledgeLifecycle.ACTIVE,
        createdAtMillis = 1000L,
        modifiedAtMillis = 1000L,
    )

    @Test
    fun `duplicate aliases detected across manufacturers`() = runTest {
        val repo = InMemoryKnowledgeRepository()
        repo.putManufacturer(
            Manufacturer(
                id = ManufacturerId("m-a"), name = "Alpha",
                aliases = setOf("shared-alias"), metadata = metadata(),
            ),
        )
        repo.putManufacturer(
            Manufacturer(
                id = ManufacturerId("m-b"), name = "Beta",
                aliases = setOf("shared-alias"), metadata = metadata(),
            ),
        )
        val q = KnowledgeQuery(repo)
        val found = q.findManufacturers("shared-alias")
        // Ambiguity preserved: both returned.
        assertTrue(found.size == 2)
    }

    @Test
    fun `orphaned model reference detected`() = runTest {
        val repo = InMemoryKnowledgeRepository()
        repo.putDeviceModel(
            DeviceModel(
                id = DeviceModelId("model-orphan"),
                manufacturerId = ManufacturerId("m-missing"),
                name = "Orphan", metadata = metadata(),
            ),
        )
        // The model exists but its manufacturer does not.
        val model = repo.deviceModel(DeviceModelId("model-orphan"))
        val manufacturer = model?.let { repo.manufacturer(it.manufacturerId) }
        assertTrue(manufacturer == null)
    }

    @Test
    fun `cyclic capability dependencies detectable`() = runTest {
        val repo = InMemoryKnowledgeRepository()
        repo.putCapability(
            CapabilityDefinition(
                id = CapabilityDefId("cap-a"), category = "audio", name = "A",
                dependencies = listOf(CapabilityDefId("cap-b")),
                metadata = metadata(),
            ),
        )
        repo.putCapability(
            CapabilityDefinition(
                id = CapabilityDefId("cap-b"), category = "audio", name = "B",
                dependencies = listOf(CapabilityDefId("cap-a")),
                metadata = metadata(),
            ),
        )
        // Both stored; a cycle detector can traverse dependencies.
        val a = repo.capability(CapabilityDefId("cap-a"))
        val b = repo.capability(CapabilityDefId("cap-b"))
        assertTrue(a?.dependencies?.contains(CapabilityDefId("cap-b")) == true)
        assertTrue(b?.dependencies?.contains(CapabilityDefId("cap-a")) == true)
    }

    @Test
    fun `conflicting model identities preserved`() = runTest {
        val repo = InMemoryKnowledgeRepository()
        repo.putDeviceModel(
            DeviceModel(
                id = DeviceModelId("model-x1"),
                manufacturerId = ManufacturerId("m-a"),
                name = "Buds", metadata = metadata(),
            ),
        )
        repo.putDeviceModel(
            DeviceModel(
                id = DeviceModelId("model-x2"),
                manufacturerId = ManufacturerId("m-b"),
                name = "Buds", metadata = metadata(),
            ),
        )
        val q = KnowledgeQuery(repo)
        // Same display name, different manufacturers — both candidates.
        assertTrue(q.findModelCandidates("Buds").size == 2)
    }
}
