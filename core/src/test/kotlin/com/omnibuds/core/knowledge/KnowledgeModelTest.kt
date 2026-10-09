package com.omnibuds.core.knowledge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phase 22: knowledge domain model tests.
 */
class KnowledgeModelTest {

    private fun metadata() = RecordMetadata(
        recordVersion = 1,
        lifecycle = KnowledgeLifecycle.ACTIVE,
        createdAtMillis = 1000L,
        modifiedAtMillis = 1000L,
    )

    @Test
    fun `manufacturer with distinct brand and legal name`() {
        val m = Manufacturer(
            id = ManufacturerId("m-acme"),
            name = "Acme",
            aliases = setOf("acme audio"),
            legalName = "Acme Corporation Ltd.",
            bluetoothCompanyId = 1234,
            protocolOwner = "Acme Labs",
            metadata = metadata(),
        )
        assertEquals("Acme", m.name)
        assertEquals("Acme Corporation Ltd.", m.legalName)
    }

    @Test
    fun `manufacturer requires non-blank name`() {
        assertFailsWith<IllegalArgumentException> {
            Manufacturer(ManufacturerId("m-x"), "", metadata = metadata())
        }
    }

    @Test
    fun `device model never merged by family`() {
        val a = DeviceModel(
            id = DeviceModelId("model-a"),
            manufacturerId = ManufacturerId("m-acme"),
            name = "Buds Pro",
            productFamily = "Buds",
            metadata = metadata(),
        )
        val b = DeviceModel(
            id = DeviceModelId("model-b"),
            manufacturerId = ManufacturerId("m-acme"),
            name = "Buds Pro 2",
            productFamily = "Buds",
            metadata = metadata(),
        )
        // Same family, distinct identities.
        assertTrue(a.id != b.id)
    }

    @Test
    fun `firmware unknown is explicit`() {
        val f = FirmwareProfile(
            id = FirmwareProfileId("fw-unknown"),
            deviceModelId = DeviceModelId("model-a"),
            versionConstraint = null,
            firmwareUnknown = true,
            metadata = metadata(),
        )
        assertTrue(f.firmwareUnknown)
        assertNull(f.versionConstraint)
    }

    @Test
    fun `message field meaning null means unknown`() {
        val field = MessageField(
            name = "byte3",
            offsetBytes = 3,
            lengthBytes = 1,
            type = "uint8",
            meaning = null, // unknown, not fabricated
        )
        assertNull(field.meaning)
    }

    @Test
    fun `record version must be positive`() {
        assertFailsWith<IllegalArgumentException> {
            RecordMetadata(0, KnowledgeLifecycle.DRAFT, null, null)
        }
    }

    @Test
    fun `synthetic evidence cannot claim hardware verification`() {
        assertFailsWith<IllegalArgumentException> {
            EvidenceRecord(
                id = EvidenceId("e1"),
                claimId = ClaimId("c1"),
                type = EvidenceType.SYNTHETIC_FIXTURE,
                sourceId = SourceId("s1"),
                reliability = "low",
                stance = EvidenceStance.SUPPORTS,
                verification = com.omnibuds.core.state.VerificationLevel.HARDWARE_VERIFIED,
                metadata = metadata(),
            )
        }
    }

    @Test
    fun `claim preserves contradicting evidence`() {
        val claim = Claim(
            id = ClaimId("c1"),
            subject = "model-a",
            predicate = "supports",
            objectValue = "anc",
            scope = "all firmware",
            supportingEvidenceIds = listOf(EvidenceId("e1")),
            contradictingEvidenceIds = listOf(EvidenceId("e2")),
            confidence = ClaimConfidence.MEDIUM,
            status = ClaimStatus.UNDER_REVIEW,
            metadata = metadata(),
        )
        assertEquals(1, claim.contradictingEvidenceIds.size)
    }

    @Test
    fun `codec round-trips manufacturer`() {
        val m = Manufacturer(
            id = ManufacturerId("m-acme"),
            name = "Acme",
            aliases = setOf("acme audio"),
            metadata = metadata(),
        )
        val json = KnowledgeJson.encode(KnowledgeCodecs.encode(m))
        @Suppress("UNCHECKED_CAST")
        val map = KnowledgeJson.decode(json) as Map<String, Any?>
        val decoded = KnowledgeCodecs.decodeManufacturer(map)
        assertNotNull(decoded)
        assertEquals(m, decoded)
    }

    @Test
    fun `codec round-trips message schema with unknown fields`() {
        val s = MessageSchema(
            id = MessageSchemaId("s1"),
            protocolId = ProtocolId("p1"),
            schemaVersion = 1,
            messageId = "get_battery",
            direction = MessageDirection.DEVICE_TO_HOST,
            fields = listOf(
                MessageField("level", 0, 1, "uint8", "battery percentage"),
                MessageField("unknown1", 1, 1, "uint8", null),
            ),
            metadata = metadata(),
        )
        val json = KnowledgeJson.encode(KnowledgeCodecs.encode(s))
        @Suppress("UNCHECKED_CAST")
        val map = KnowledgeJson.decode(json) as Map<String, Any?>
        val decoded = KnowledgeCodecs.decodeMessageSchema(map)
        assertNotNull(decoded)
        assertEquals(s, decoded)
        assertNull(decoded.fields[1].meaning)
    }

    @Test
    fun `decode rejects malformed record`() {
        assertNull(KnowledgeJson.decode("{bad json"))
        assertNull(KnowledgeCodecs.decodeManufacturer(mapOf("kind" to "manufacturer")))
    }

    @Test
    fun `json encoding is deterministic`() {
        val m = Manufacturer(
            id = ManufacturerId("m-acme"),
            name = "Acme",
            aliases = setOf("b", "a"),
            metadata = metadata(),
        )
        val j1 = KnowledgeJson.encode(KnowledgeCodecs.encode(m))
        val j2 = KnowledgeJson.encode(KnowledgeCodecs.encode(m))
        assertEquals(j1, j2)
    }
}
