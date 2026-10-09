package com.omnibuds.core.knowledge

/** Message direction. */
enum class MessageDirection {
    HOST_TO_DEVICE,
    DEVICE_TO_HOST,
    BIDIRECTIONAL,
}

/**
 * A documented message structure.
 *
 * Phase 22 (OB-P22-REQ-027): unknown or incomplete fields are representable
 * without fabricated semantics — a field with null meaning is unknown, not
 * "reserved" or "padding".
 */
data class MessageSchema(
    val id: MessageSchemaId,
    val protocolId: ProtocolId,
    val schemaVersion: Int,
    /** Message identifier within the protocol. */
    val messageId: String,
    val direction: MessageDirection,
    /** Framing constraints, e.g. "length-prefixed, 2-byte big-endian". */
    val framingConstraints: List<String> = emptyList(),
    val fields: List<MessageField> = emptyList(),
    /** Validation constraints on the whole message. */
    val validationConstraints: List<String> = emptyList(),
    /** How responses correlate to requests. */
    val correlationRules: List<String> = emptyList(),
    val evidenceIds: List<EvidenceId> = emptyList(),
    val metadata: RecordMetadata,
)

/** One field in a message schema. */
data class MessageField(
    val name: String,
    /** Offset in bytes, or null when variable/unknown. */
    val offsetBytes: Int? = null,
    /** Length in bytes, or null when variable/unknown. */
    val lengthBytes: Int? = null,
    val type: String,
    /** Semantic interpretation. Null = unknown; never fabricated. */
    val meaning: String? = null,
    /** Constraints on the field value. */
    val constraints: List<String> = emptyList(),
)

/**
 * Capability knowledge (general definition, not per-device support).
 *
 * Phase 22: a general capability definition is not evidence that a
 * particular device supports it.
 */
data class CapabilityDefinition(
    val id: CapabilityDefId,
    val category: String,
    val name: String,
    /** Supported value types, e.g. ["enum", "range"]. */
    val valueTypes: Set<String> = emptySet(),
    val constraints: List<String> = emptyList(),
    /** Required protocol operations (operation definition IDs). */
    val requiredOperationIds: List<OperationDefId> = emptyList(),
    /** Capability IDs this depends on. */
    val dependencies: List<CapabilityDefId> = emptyList(),
    /** Capability IDs this conflicts with. */
    val conflicts: List<CapabilityDefId> = emptyList(),
    val applicableModelIds: List<DeviceModelId> = emptyList(),
    val applicableFirmwareIds: List<FirmwareProfileId> = emptyList(),
    /** True when the value can be observed; false when write-only/unknown. */
    val readable: Boolean = false,
    /** True when a write path is documented. */
    val writable: Boolean = false,
    /** Whether persistence verification is possible for this capability. */
    val persistenceVerifiable: Boolean = false,
    val evidenceIds: List<EvidenceId> = emptyList(),
    val metadata: RecordMetadata,
)

/**
 * A documented operation.
 *
 * Phase 22 (OB-P22-REQ-028): descriptive metadata only. It provides no
 * transport access and authorizes nothing.
 */
data class OperationDefinition(
    val id: OperationDefId,
    val protocolId: ProtocolId,
    val schemaIds: List<MessageSchemaId> = emptyList(),
    val category: String,
    val name: String,
    /** Input contract description. */
    val inputContract: String? = null,
    /** Output contract description. */
    val outputContract: String? = null,
    val preconditions: List<String> = emptyList(),
    val validationRules: List<String> = emptyList(),
    /** Timeout in milliseconds, when known. */
    val timeoutMillis: Long? = null,
    /** Retry constraints description. */
    val retryConstraints: String? = null,
    /** Read-back behavior description. */
    val readBackBehavior: String? = null,
    /** Idempotency classification. */
    val idempotency: String? = null,
    val securityConstraints: List<String> = emptyList(),
    val evidenceIds: List<EvidenceId> = emptyList(),
    val metadata: RecordMetadata,
)
