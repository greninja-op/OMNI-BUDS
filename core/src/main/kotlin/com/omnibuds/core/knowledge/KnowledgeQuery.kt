package com.omnibuds.core.knowledge

/**
 * Typed query interface for the knowledge database.
 *
 * Phase 22 (OB-P22-REQ-007/008): bounded, paginated, deterministically
 * ordered. Ambiguity is preserved; no-match is distinguished from
 * incomplete knowledge; no fuzzy matching grants control privileges.
 */
class KnowledgeQuery(private val repository: KnowledgeRepository) {

    /** Find manufacturers by canonical name or alias (case-insensitive). */
    suspend fun findManufacturers(query: String): List<Manufacturer> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return emptyList()
        return repository.manufacturers().filter { m ->
            m.name.lowercase() == normalized || m.aliases.any { it.lowercase() == normalized }
        }
    }

    /**
     * Find candidate device models for a display name.
     * Returns all candidates — never a single "best" match.
     * An empty result means no match, not "unknown model".
     */
    suspend fun findModelCandidates(displayName: String): List<DeviceModel> {
        val normalized = displayName.trim().lowercase()
        if (normalized.isEmpty()) return emptyList()
        return repository.deviceModels().filter { m ->
            m.name.lowercase() == normalized || m.aliases.any { it.lowercase() == normalized }
        }
    }

    /**
     * Resolve protocol definitions for a model and firmware profile.
     * Respects firmware compatibility; unknown firmware gets only
     * safe-under-uncertainty protocols (those explicitly marked compatible
     * with unknown firmware).
     */
    suspend fun protocolsForModel(
        modelId: DeviceModelId,
        firmwareId: FirmwareProfileId?,
    ): List<ProtocolDefinition> {
        val model = repository.deviceModel(modelId) ?: return emptyList()
        val all = repository.protocols().associateBy { it.id.value }
        val candidates = model.protocolIds.mapNotNull { all[it.value] }
            .filter { it.metadata.lifecycle == KnowledgeLifecycle.ACTIVE }

        if (firmwareId == null) {
            // Unknown firmware: only protocols explicitly compatible with
            // unknown firmware are safe.
            val unknownProfiles = repository.firmwareProfiles().filter {
                it.deviceModelId == modelId && it.firmwareUnknown
            }.flatMap { it.compatibleProtocolIds.map { p -> p.value } }.toSet()
            return candidates.filter { unknownProfiles.contains(it.id.value) }
        }

        val firmware = repository.firmwareProfile(firmwareId) ?: return candidates
        val compatible = firmware.compatibleProtocolIds.map { it.value }.toSet()
        return candidates.filter { compatible.contains(it.id.value) }
    }

    /** Schemas for a protocol version. */
    suspend fun schemasForProtocol(protocolId: ProtocolId): List<MessageSchema> =
        repository.messageSchemas().filter {
            it.protocolId == protocolId &&
                it.metadata.lifecycle == KnowledgeLifecycle.ACTIVE
        }.sortedBy { it.schemaVersion }

    /** Capabilities applicable to a device model. */
    suspend fun capabilitiesForModel(modelId: DeviceModelId): List<CapabilityDefinition> =
        repository.capabilities().filter {
            it.applicableModelIds.contains(modelId) &&
                it.metadata.lifecycle == KnowledgeLifecycle.ACTIVE
        }

    /** Operation definitions for a protocol. */
    suspend fun operationsForProtocol(protocolId: ProtocolId): List<OperationDefinition> =
        repository.operations().filter {
            it.protocolId == protocolId &&
                it.metadata.lifecycle == KnowledgeLifecycle.ACTIVE
        }

    /** All evidence for a claim, supporting and contradicting. */
    suspend fun evidenceForClaim(claimId: ClaimId): List<EvidenceRecord> =
        repository.evidenceRecords().filter { it.claimId == claimId }

    /** Claims that are unverified or have contradicting evidence. */
    suspend fun unverifiedOrConflictingClaims(): List<Claim> =
        repository.claims().filter { claim ->
            claim.status == ClaimStatus.HYPOTHESIS ||
                claim.status == ClaimStatus.UNDER_REVIEW ||
                claim.contradictingEvidenceIds.isNotEmpty()
        }

    /**
     * Entries affected by a protocol version change: models referencing the
     * protocol, firmware profiles with compatibility, schemas, operations.
     */
    suspend fun affectedByProtocolChange(protocolId: ProtocolId): AffectedEntries {
        val models = repository.deviceModels().filter {
            it.protocolIds.contains(protocolId)
        }
        val firmware = repository.firmwareProfiles().filter {
            it.compatibleProtocolIds.contains(protocolId)
        }
        val schemas = repository.messageSchemas().filter {
            it.protocolId == protocolId
        }
        val operations = repository.operations().filter {
            it.protocolId == protocolId
        }
        return AffectedEntries(
            modelIds = models.map { it.id },
            firmwareIds = firmware.map { it.id },
            schemaIds = schemas.map { it.id },
            operationIds = operations.map { it.id },
        )
    }

    /** Models with no firmware compatibility information at all. */
    suspend fun modelsWithIncompleteFirmwareInfo(): List<DeviceModel> {
        val modelsWithFirmware = repository.firmwareProfiles()
            .map { it.deviceModelId }.toSet()
        return repository.deviceModels().filter { !modelsWithFirmware.contains(it.id) }
    }

    /** Deprecated or disabled entries of every kind. */
    suspend fun deprecatedEntries(): List<String> {
        val out = mutableListOf<String>()
        suspend fun <T> check(items: List<T>, idOf: (T) -> String, metaOf: (T) -> RecordMetadata, kind: String) {
            items.forEach {
                val lifecycle = metaOf(it).lifecycle
                if (lifecycle == KnowledgeLifecycle.DEPRECATED ||
                    lifecycle == KnowledgeLifecycle.DISABLED
                ) {
                    out.add("$kind:${idOf(it)}")
                }
            }
        }
        check(repository.manufacturers(), { it.id.value }, { it.metadata }, "manufacturer")
        check(repository.deviceModels(), { it.id.value }, { it.metadata }, "model")
        check(repository.protocols(), { it.id.value }, { it.metadata }, "protocol")
        check(repository.messageSchemas(), { it.id.value }, { it.metadata }, "schema")
        check(repository.capabilities(), { it.id.value }, { it.metadata }, "capability")
        check(repository.operations(), { it.id.value }, { it.metadata }, "operation")
        return out.sorted()
    }

    /**
     * Paginated query helper. Deterministic ordering by the caller's
     * comparator; page is 0-based.
     */
    fun <T> paginate(
        items: List<T>,
        page: Int,
        pageSize: Int,
    ): List<T> {
        require(page >= 0) { "page must be >= 0" }
        require(pageSize in 1..1000) { "pageSize must be 1..1000" }
        val from = page * pageSize
        if (from >= items.size) return emptyList()
        return items.subList(from, minOf(from + pageSize, items.size))
    }
}

/** Entries affected by a protocol change. */
data class AffectedEntries(
    val modelIds: List<DeviceModelId>,
    val firmwareIds: List<FirmwareProfileId>,
    val schemaIds: List<MessageSchemaId>,
    val operationIds: List<OperationDefId>,
)
