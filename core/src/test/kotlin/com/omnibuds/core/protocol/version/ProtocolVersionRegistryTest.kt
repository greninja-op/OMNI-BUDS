package com.omnibuds.core.protocol.version

import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.state.VerificationLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProtocolVersionRegistryTest {

    private val registry = ProtocolVersionRegistry()

    private fun proto(
        id: String,
        version: ProtocolVersion = ProtocolVersion.Semantic(1, 0, 0),
        confidence: VerificationLevel = VerificationLevel.LAB_TESTED,
        evidenceIds: List<String> = emptyList(),
        schema: ProtocolSchemaVersion = ProtocolSchemaVersion.CURRENT,
    ) = ProtocolIdentity(
        protocolId = id,
        vendorNamespace = "acme",
        version = version,
        schemaVersion = schema,
        transport = TransportKind.RFCOMM,
        versionConstraint = VersionConstraint.Exact(version),
        confidence = confidence,
        evidenceIds = evidenceIds,
    )

    @Test
    fun registerAndLookup() {
        val p1 = proto("acme.buds.v1")
        val p2 = proto("acme.buds.v2", ProtocolVersion.Semantic(2, 0, 0))

        registry.register(p1)
        registry.register(p2)

        assertEquals(2, registry.count)
        assertEquals(p1, registry.findById("acme.buds.v1"))
        assertEquals(p2, registry.findById("acme.buds.v2"))
        assertNull(registry.findById("unknown.proto"))
    }

    @Test
    fun rejectsDuplicateRegistration() {
        val p1 = proto("acme.buds.v1")
        registry.register(p1)

        assertFailsWith<IllegalArgumentException> {
            registry.register(p1)
        }
    }

    @Test
    fun rejectsUnsupportedSchema() {
        val futureSchema = ProtocolSchemaVersion(99)
        val p = proto("acme.buds.future", schema = futureSchema)

        assertFailsWith<IllegalArgumentException> {
            registry.register(p)
        }
    }

    @Test
    fun rejectsSelfPromotedHardwareVerificationWithoutEvidence() {
        val unevidencedHw = proto(
            id = "acme.buds.unverified",
            confidence = VerificationLevel.HARDWARE_VERIFIED,
            evidenceIds = emptyList(),
        )

        assertFailsWith<SecurityException> {
            registry.register(unevidencedHw)
        }

        val evidencedHw = proto(
            id = "acme.buds.verified",
            confidence = VerificationLevel.HARDWARE_VERIFIED,
            evidenceIds = listOf("EVID-001"),
        )
        registry.register(evidencedHw)
        assertNotNull(registry.findById("acme.buds.verified"))
    }
}
