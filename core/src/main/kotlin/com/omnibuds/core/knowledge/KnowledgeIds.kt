package com.omnibuds.core.knowledge

/**
 * Stable identifiers for knowledge records.
 *
 * Phase 22 (OB-P22-REQ-001): every record has a stable, unique identifier
 * that survives renames, merges, and version bumps. Joins use these, never
 * display names.
 */
@JvmInline
value class ManufacturerId(val value: String)

@JvmInline
value class DeviceModelId(val value: String)

@JvmInline
value class FirmwareProfileId(val value: String)

@JvmInline
value class ProtocolId(val value: String)

@JvmInline
value class MessageSchemaId(val value: String)

@JvmInline
value class CapabilityDefId(val value: String)

@JvmInline
value class OperationDefId(val value: String)

@JvmInline
value class EvidenceId(val value: String)

@JvmInline
value class ClaimId(val value: String)

@JvmInline
value class SourceId(val value: String)

/**
 * Record lifecycle states.
 *
 * Phase 22 (OB-P22-REQ-009).
 */
enum class KnowledgeLifecycle {
    DRAFT,
    RESEARCH,
    REVIEW_REQUIRED,
    ACTIVE,
    DEPRECATED,
    DISABLED,
    SUPERSEDED,
}

/**
 * Common record metadata. Every knowledge entity carries this.
 */
data class RecordMetadata(
    /** Monotonically increasing per record; schema changes bump this. */
    val recordVersion: Int,
    val lifecycle: KnowledgeLifecycle,
    val createdAtMillis: Long?,
    val modifiedAtMillis: Long?,
    /** Stable IDs of superseding records, when SUPERSEDED. */
    val supersededBy: List<String> = emptyList(),
    /** Human-readable limitations, always present with results. */
    val limitations: List<String> = emptyList(),
) {
    init {
        require(recordVersion >= 1) { "record version must be >= 1" }
    }
}
