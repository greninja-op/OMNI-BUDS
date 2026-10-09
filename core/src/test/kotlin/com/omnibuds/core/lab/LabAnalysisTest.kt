package com.omnibuds.core.lab

import com.omnibuds.core.common.TransportKind
import com.omnibuds.core.state.VerificationLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlinx.coroutines.test.runTest

/**
 * Phase 20: correlation, timeline, differential, schema, fixture, evidence tests.
 */
class LabAnalysisTest {

    private fun trace(vararg events: TraceEvent) = ProtocolTrace.create(
        traceId = "t1",
        sourceType = TraceSourceType.SYNTHETIC_FIXTURE,
        transport = TransportKind.RFCOMM,
        events = events.toList(),
        provenance = "test",
        nowMillis = 1000L,
    )

    private fun event(
        id: String,
        seq: Long,
        dir: TraceDirection,
        cid: String? = null,
    ) = TraceEvent(
        eventId = id,
        sequence = seq,
        relativeMillis = seq * 10,
        direction = dir,
        category = "test",
        payload = byteArrayOf(0x01),
        declaredLength = 1,
        correlationId = cid,
        redacted = false,
    )

    @Test
    fun `explicit correlation pairs request and response`() {
        val t = trace(
            event("req", 0, TraceDirection.HOST_TO_DEVICE, "c1"),
            event("resp", 1, TraceDirection.DEVICE_TO_HOST, "c1"),
        )
        val result = CorrelationEngine.correlate(t)
        assertEquals(1, result.pairs.size)
        assertEquals(CorrelationConfidence.EXPLICIT_ID, result.pairs.first().confidence)
        assertTrue(result.unmatchedRequests.isEmpty())
    }

    @Test
    fun `unmatched request preserved`() {
        val t = trace(event("req", 0, TraceDirection.HOST_TO_DEVICE, "c1"))
        val result = CorrelationEngine.correlate(t)
        assertEquals(1, result.unmatchedRequests.size)
        assertTrue(result.pairs.isEmpty())
    }

    @Test
    fun `ambiguous correlation not paired`() {
        val t = trace(
            event("req", 0, TraceDirection.HOST_TO_DEVICE, "c1"),
            event("r1", 1, TraceDirection.DEVICE_TO_HOST, "c1"),
            event("r2", 2, TraceDirection.DEVICE_TO_HOST, "c1"),
        )
        val result = CorrelationEngine.correlate(t)
        // Ambiguous — not paired as a confident pair.
        assertTrue(result.pairs.isEmpty())
    }

    @Test
    fun `timeline preserves order`() {
        val t = trace(
            event("e2", 1, TraceDirection.DEVICE_TO_HOST),
            event("e1", 0, TraceDirection.HOST_TO_DEVICE),
        )
        val timeline = TimelineBuilder.build(t)
        assertEquals("e1", timeline.first().eventId)
        assertEquals("e2", timeline.last().eventId)
    }

    @Test
    fun `differential reports differences without semantics`() {
        val a = trace(event("e1", 0, TraceDirection.HOST_TO_DEVICE))
        val b = trace(
            event("e1", 0, TraceDirection.HOST_TO_DEVICE).copy(
                payload = byteArrayOf(0x02),
            ),
        )
        val diff = DifferentialAnalyzer.compare(a, b)
        assertTrue(diff.differences.isNotEmpty())
        // No hypothesis auto-created.
        assertTrue(diff.hypotheses.isEmpty())
        // Difference text must not claim meaning.
        assertFalse(diff.differences.any { it.contains("ANC", ignoreCase = true) })
    }

    @Test
    fun `schema registry rejects conflicting versions`() = runTest {
        val registry = SchemaRegistry()
        val schema = LabSchema(
            protocolId = "test",
            messageType = "msg",
            version = 1,
            compatibleFirmware = null,
            fieldDefinitions = emptyMap(),
            encodingRules = "test",
            framingRules = "test",
            semanticMeaning = null,
            evidenceRef = "test",
            verificationStatus = VerificationLevel.INFERRED,
        )
        assertEquals(RegisterOutcome.Registered, registry.register(schema))
        // Same version again → rejected.
        assertTrue(registry.register(schema) is RegisterOutcome.Rejected)
        // Newer version → updated.
        assertEquals(
            RegisterOutcome.Updated,
            registry.register(schema.copy(version = 2)),
        )
    }

    @Test
    fun `fixture preserves synthetic classification`() {
        val t = trace(event("e1", 0, TraceDirection.HOST_TO_DEVICE))
        val fixture = FixtureGenerator.fromEvent("f1", t, t.events.first(), "parsed")
        assertTrue(fixture.isSynthetic)
        assertEquals(FixtureOrigin.SYNTHETIC, fixture.origin)
    }

    @Test
    fun `test runner is deterministic`() {
        val parser = LengthPrefixedParser()
        val fixtures = listOf(
            ParserFixture("f1", FixtureOrigin.SYNTHETIC, null, true, false, "00030102", "parsed", null, 1),
            ParserFixture("f2", FixtureOrigin.SYNTHETIC, null, true, false, "00", "incomplete", null, 1),
        )
        val report1 = ParserTestRunner.run(parser, fixtures)
        val report2 = ParserTestRunner.run(parser, fixtures)
        assertEquals(report1.passed, report2.passed)
        assertEquals(2, report1.total)
    }

    @Test
    fun `synthetic cannot promote to hardware verified`() {
        assertFalse(
            EvidenceWorkflow.canPromote(
                VerificationLevel.LAB_TESTED,
                VerificationLevel.HARDWARE_VERIFIED,
                hasRealDeviceEvidence = false,
            ),
        )
        assertTrue(
            EvidenceWorkflow.canPromote(
                VerificationLevel.LAB_TESTED,
                VerificationLevel.HARDWARE_VERIFIED,
                hasRealDeviceEvidence = true,
            ),
        )
    }

    @Test
    fun `evidence requirements defined per transition`() {
        assertTrue(
            EvidenceWorkflow.requirementFor(
                VerificationLevel.INFERRED,
                VerificationLevel.IMPLEMENTED,
            ) != null,
        )
        // No skipping levels.
        assertEquals(
            null,
            EvidenceWorkflow.requirementFor(
                VerificationLevel.INFERRED,
                VerificationLevel.HARDWARE_VERIFIED,
            ),
        )
    }
}
