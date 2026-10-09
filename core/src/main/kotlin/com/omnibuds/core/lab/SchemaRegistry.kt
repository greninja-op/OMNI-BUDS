package com.omnibuds.core.lab

import com.omnibuds.core.state.VerificationLevel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Registry for parser/schema definitions.
 *
 * Phase 20 (OB-P20-REQ-016): versioned, provenance-bearing, no silent
 * overwrites. Describing a schema never authorizes transmitting it.
 */
class SchemaRegistry {
    private val mutex = Mutex()
    private val schemas = mutableMapOf<String, LabSchema>()

    /**
     * Register a schema. Rejects duplicates unless [LabSchema.version]
     * is newer than the existing one.
     */
    suspend fun register(schema: LabSchema): RegisterOutcome = mutex.withLock {
        val key = "${schema.protocolId}:${schema.messageType}"
        val existing = schemas[key]
        if (existing == null) {
            schemas[key] = schema
            return@withLock RegisterOutcome.Registered
        }
        if (schema.version > existing.version) {
            schemas[key] = schema
            return@withLock RegisterOutcome.Updated
        }
        RegisterOutcome.Rejected("conflicting or older version: ${schema.version} vs ${existing.version}")
    }

    /** Look up a schema. */
    suspend fun find(protocolId: String, messageType: String): LabSchema? = mutex.withLock {
        schemas["$protocolId:$messageType"]
    }

    /** All registered schemas. */
    suspend fun all(): List<LabSchema> = mutex.withLock { schemas.values.toList() }
}

/** A parser/schema definition. */
data class LabSchema(
    val protocolId: String,
    val messageType: String,
    /** Schema version; higher wins on conflict. */
    val version: Int,
    val compatibleFirmware: Set<String>?,
    val fieldDefinitions: Map<String, String>,
    val encodingRules: String,
    val framingRules: String,
    val semanticMeaning: String?,
    val evidenceRef: String,
    val verificationStatus: VerificationLevel,
    val limitations: List<String> = emptyList(),
) {
    init {
        require(protocolId.isNotBlank()) { "protocolId must not be blank" }
        require(messageType.isNotBlank()) { "messageType must not be blank" }
        require(version >= 1) { "version must be >= 1" }
    }
}

/** The outcome of a registration attempt. */
sealed interface RegisterOutcome {
    data object Registered : RegisterOutcome
    data object Updated : RegisterOutcome
    data class Rejected(val reason: String) : RegisterOutcome
}
