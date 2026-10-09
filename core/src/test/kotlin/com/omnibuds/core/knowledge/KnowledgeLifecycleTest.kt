package com.omnibuds.core.knowledge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Phase 22: lifecycle tests.
 */
class KnowledgeLifecycleTest {

    @Test
    fun `draft to review to active is legal`() {
        assertNull(
            KnowledgeLifecycleRules.checkTransition(
                KnowledgeLifecycle.DRAFT, KnowledgeLifecycle.REVIEW_REQUIRED,
            ),
        )
        assertNull(
            KnowledgeLifecycleRules.checkTransition(
                KnowledgeLifecycle.REVIEW_REQUIRED, KnowledgeLifecycle.ACTIVE,
            ),
        )
    }

    @Test
    fun `draft directly to active is illegal`() {
        val reason = KnowledgeLifecycleRules.checkTransition(
            KnowledgeLifecycle.DRAFT, KnowledgeLifecycle.ACTIVE,
        )
        assertTrue(reason != null)
    }

    @Test
    fun `superseded is terminal except disable`() {
        assertNull(
            KnowledgeLifecycleRules.checkTransition(
                KnowledgeLifecycle.SUPERSEDED, KnowledgeLifecycle.DISABLED,
            ),
        )
        val reason = KnowledgeLifecycleRules.checkTransition(
            KnowledgeLifecycle.SUPERSEDED, KnowledgeLifecycle.ACTIVE,
        )
        assertTrue(reason != null)
    }

    @Test
    fun `active protocol without schemas cannot activate`() {
        val p = ProtocolDefinition(
            id = ProtocolId("p1"), name = "P", family = "f", version = "1",
            schemaIds = emptyList(),
            metadata = RecordMetadata(1, KnowledgeLifecycle.REVIEW_REQUIRED, null, null),
        )
        val reason = KnowledgeLifecycleRules.checkActivatable(p)
        assertTrue(reason != null)
    }

    @Test
    fun `accepted claim without evidence cannot activate`() {
        val c = Claim(
            id = ClaimId("c1"), subject = "s", predicate = "p",
            objectValue = "o", scope = "all",
            supportingEvidenceIds = emptyList(),
            confidence = ClaimConfidence.HIGH,
            status = ClaimStatus.ACCEPTED,
            metadata = RecordMetadata(1, KnowledgeLifecycle.REVIEW_REQUIRED, null, null),
        )
        val reason = KnowledgeLifecycleRules.checkActivatable(c)
        assertTrue(reason != null)
    }

    @Test
    fun `event bus notifies listeners`() {
        val bus = KnowledgeEventBus()
        val received = mutableListOf<KnowledgeChangeEvent>()
        bus.subscribe { received.add(it) }
        bus.publish(KnowledgeChangeEvent.ProtocolDeprecated(ProtocolId("p1")))
        assertEquals(1, received.size)
        assertTrue(received[0] is KnowledgeChangeEvent.ProtocolDeprecated)
    }
}
