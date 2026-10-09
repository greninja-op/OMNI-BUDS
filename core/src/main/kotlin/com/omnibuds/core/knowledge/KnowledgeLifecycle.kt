package com.omnibuds.core.knowledge

/**
 * Knowledge change events.
 *
 * Phase 22 (OB-P22-REQ-013): typed events notify relevant components when
 * knowledge changes. Changes never cause a previously denied operation to
 * execute automatically.
 */
sealed interface KnowledgeChangeEvent {
    /** A protocol version was deprecated or disabled. */
    data class ProtocolDeprecated(val protocolId: ProtocolId) : KnowledgeChangeEvent

    /** Firmware compatibility was narrowed for a model. */
    data class FirmwareCompatibilityNarrowed(
        val modelId: DeviceModelId,
        val firmwareId: FirmwareProfileId,
    ) : KnowledgeChangeEvent

    /** A capability claim was invalidated. */
    data class CapabilityInvalidated(val capabilityId: CapabilityDefId) : KnowledgeChangeEvent

    /** A message schema was replaced. */
    data class SchemaReplaced(
        val oldSchemaId: MessageSchemaId,
        val newSchemaId: MessageSchemaId,
    ) : KnowledgeChangeEvent

    /** An operation was disabled. */
    data class OperationDisabled(val operationId: OperationDefId) : KnowledgeChangeEvent

    /** A device identity rule became ambiguous. */
    data class IdentityRuleAmbiguous(val modelId: DeviceModelId) : KnowledgeChangeEvent

    /** Any record's lifecycle changed. */
    data class LifecycleChanged(
        val recordKind: String,
        val recordId: String,
        val from: KnowledgeLifecycle,
        val to: KnowledgeLifecycle,
    ) : KnowledgeChangeEvent
}

/**
 * Lifecycle transition rules.
 *
 * Phase 22 (OB-P22-REQ-009).
 */
object KnowledgeLifecycleRules {

    /**
     * Whether a transition from [from] to [to] is legal.
     * Returns null when legal, or a reason when illegal.
     */
    fun checkTransition(from: KnowledgeLifecycle, to: KnowledgeLifecycle): String? {
        if (from == to) return null
        val legal = when (from) {
            KnowledgeLifecycle.DRAFT -> setOf(
                KnowledgeLifecycle.RESEARCH,
                KnowledgeLifecycle.REVIEW_REQUIRED,
                KnowledgeLifecycle.DISABLED,
            )
            KnowledgeLifecycle.RESEARCH -> setOf(
                KnowledgeLifecycle.REVIEW_REQUIRED,
                KnowledgeLifecycle.DRAFT,
                KnowledgeLifecycle.DISABLED,
            )
            KnowledgeLifecycle.REVIEW_REQUIRED -> setOf(
                KnowledgeLifecycle.ACTIVE,
                KnowledgeLifecycle.DRAFT,
                KnowledgeLifecycle.DISABLED,
            )
            KnowledgeLifecycle.ACTIVE -> setOf(
                KnowledgeLifecycle.DEPRECATED,
                KnowledgeLifecycle.DISABLED,
                KnowledgeLifecycle.SUPERSEDED,
            )
            KnowledgeLifecycle.DEPRECATED -> setOf(
                KnowledgeLifecycle.DISABLED,
                KnowledgeLifecycle.SUPERSEDED,
                KnowledgeLifecycle.ACTIVE, // re-activation after review
            )
            KnowledgeLifecycle.DISABLED -> setOf(
                KnowledgeLifecycle.ACTIVE, // re-enable after review
                KnowledgeLifecycle.DEPRECATED,
            )
            KnowledgeLifecycle.SUPERSEDED -> setOf(
                KnowledgeLifecycle.DISABLED,
                // SUPERSEDED is terminal for the record itself; the
                // superseding record carries forward.
            )
        }
        return if (legal.contains(to)) {
            null
        } else {
            "illegal lifecycle transition $from -> $to"
        }
    }

    /**
     * Validate that a record may become ACTIVE: structural requirements.
     */
    fun checkActivatable(record: Any): String? = when (record) {
        is Manufacturer -> if (record.name.isBlank()) "manufacturer name blank" else null
        is DeviceModel -> if (record.name.isBlank()) "model name blank" else null
        is ProtocolDefinition ->
            if (record.schemaIds.isEmpty()) "protocol has no schemas" else null
        is Claim ->
            if (record.supportingEvidenceIds.isEmpty() &&
                record.status == ClaimStatus.ACCEPTED
            ) {
                "accepted claim has no supporting evidence"
            } else {
                null
            }
        else -> null
    }
}

/**
 * Simple in-memory event bus for knowledge changes.
 * Listeners are notified synchronously in registration order.
 */
class KnowledgeEventBus {
    private val listeners = mutableListOf<(KnowledgeChangeEvent) -> Unit>()

    fun subscribe(listener: (KnowledgeChangeEvent) -> Unit) {
        listeners.add(listener)
    }

    fun publish(event: KnowledgeChangeEvent) {
        // Snapshot to avoid concurrent-modification if a listener subscribes
        // during dispatch.
        listeners.toList().forEach { it(event) }
    }
}
