package com.omnibuds.core.knowledge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Phase 22: query-engine tests.
 */
class KnowledgeQueryTest {

    private fun metadata() = RecordMetadata(
        recordVersion = 1,
        lifecycle = KnowledgeLifecycle.ACTIVE,
        createdAtMillis = 1000L,
        modifiedAtMillis = 1000L,
    )

    private suspend fun populated(): InMemoryKnowledgeRepository {
        val repo = InMemoryKnowledgeRepository()
        repo.putManufacturer(
            Manufacturer(
                id = ManufacturerId("m-acme"), name = "Acme",
                aliases = setOf("acme audio"), metadata = metadata(),
            ),
        )
        repo.putDeviceModel(
            DeviceModel(
                id = DeviceModelId("model-a"),
                manufacturerId = ManufacturerId("m-acme"),
                name = "Buds Pro", aliases = setOf("acme buds pro"),
                protocolIds = listOf(ProtocolId("proto-1")),
                metadata = metadata(),
            ),
        )
        repo.putDeviceModel(
            DeviceModel(
                id = DeviceModelId("model-b"),
                manufacturerId = ManufacturerId("m-acme"),
                name = "Buds Pro", // same display name, different model
                metadata = metadata(),
            ),
        )
        repo.putFirmwareProfile(
            FirmwareProfile(
                id = FirmwareProfileId("fw-a1"),
                deviceModelId = DeviceModelId("model-a"),
                versionConstraint = ">= 3.0.0",
                compatibleProtocolIds = listOf(ProtocolId("proto-1")),
                metadata = metadata(),
            ),
        )
        repo.putProtocol(
            ProtocolDefinition(
                id = ProtocolId("proto-1"), name = "AcmeProto",
                family = "vendor-rfcomm", version = "1.0",
                metadata = metadata(),
            ),
        )
        return repo
    }

    @Test
    fun `find manufacturers by alias`() = runTest {
        val q = KnowledgeQuery(populated())
        val found = q.findManufacturers("acme audio")
        assertEquals(1, found.size)
        assertEquals("Acme", found[0].name)
    }

    @Test
    fun `model candidates preserve ambiguity`() = runTest {
        val q = KnowledgeQuery(populated())
        val candidates = q.findModelCandidates("Buds Pro")
        // Both models share the display name — both returned, never one picked.
        assertEquals(2, candidates.size)
    }

    @Test
    fun `protocols for model and firmware`() = runTest {
        val q = KnowledgeQuery(populated())
        val protocols = q.protocolsForModel(
            DeviceModelId("model-a"), FirmwareProfileId("fw-a1"),
        )
        assertEquals(1, protocols.size)
        assertEquals("AcmeProto", protocols[0].name)
    }

    @Test
    fun `unknown firmware gets only safe protocols`() = runTest {
        val q = KnowledgeQuery(populated())
        // No firmware profile marked compatible with unknown firmware.
        val protocols = q.protocolsForModel(DeviceModelId("model-a"), null)
        assertTrue(protocols.isEmpty())
    }

    @Test
    fun `models with incomplete firmware info`() = runTest {
        val q = KnowledgeQuery(populated())
        val incomplete = q.modelsWithIncompleteFirmwareInfo()
        assertEquals(1, incomplete.size)
        assertEquals(DeviceModelId("model-b"), incomplete[0].id)
    }

    @Test
    fun `affected by protocol change`() = runTest {
        val q = KnowledgeQuery(populated())
        val affected = q.affectedByProtocolChange(ProtocolId("proto-1"))
        assertTrue(affected.modelIds.contains(DeviceModelId("model-a")))
        assertTrue(affected.firmwareIds.contains(FirmwareProfileId("fw-a1")))
    }

    @Test
    fun `pagination is deterministic`() = runTest {
        val q = KnowledgeQuery(populated())
        val items = listOf(3, 1, 2).sorted()
        assertEquals(listOf(1, 2), q.paginate(items, 0, 2))
        assertEquals(listOf(3), q.paginate(items, 1, 2))
        assertEquals(emptyList(), q.paginate(items, 5, 2))
    }

    @Test
    fun `unverified claims found`() = runTest {
        val repo = populated()
        repo.putClaim(
            Claim(
                id = ClaimId("c1"), subject = "model-a", predicate = "supports",
                objectValue = "anc", scope = "all",
                status = ClaimStatus.HYPOTHESIS,
                confidence = ClaimConfidence.SPECULATIVE,
                metadata = metadata(),
            ),
        )
        val q = KnowledgeQuery(repo)
        assertEquals(1, q.unverifiedOrConflictingClaims().size)
    }
}
