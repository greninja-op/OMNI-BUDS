package com.omnibuds.core.knowledge

import com.omnibuds.core.state.VerificationLevel

/**
 * Entity codecs: each knowledge record encodes to a JSON object with a
 * "kind" discriminator and decodes back. Unknown fields are ignored on
 * read (forward tolerance); missing required fields fail explicitly.
 *
 * Phase 22 (OB-P22-REQ-003/016).
 */
object KnowledgeCodecs {

    const val SCHEMA_VERSION = 1

    // --- metadata ---

    fun encodeMetadata(m: RecordMetadata): Map<String, Any?> = mapOf(
        "recordVersion" to m.recordVersion,
        "lifecycle" to m.lifecycle.name,
        "createdAtMillis" to m.createdAtMillis,
        "modifiedAtMillis" to m.modifiedAtMillis,
        "supersededBy" to m.supersededBy,
        "limitations" to m.limitations,
    )

    fun decodeMetadata(map: Map<String, Any?>): RecordMetadata? {
        return try {
        RecordMetadata(
            recordVersion = KnowledgeJson.int(map, "recordVersion") ?: return null,
            lifecycle = KnowledgeLifecycle.valueOf(
                KnowledgeJson.string(map, "lifecycle") ?: return null,
            ),
            createdAtMillis = KnowledgeJson.long(map, "createdAtMillis"),
            modifiedAtMillis = KnowledgeJson.long(map, "modifiedAtMillis"),
            supersededBy = KnowledgeJson.stringList(map, "supersededBy"),
            limitations = KnowledgeJson.stringList(map, "limitations"),
        )
    } catch (e: Exception) {
        null
    }
    }

    // --- manufacturer ---

    fun encode(m: Manufacturer): Map<String, Any?> = mapOf(
        "kind" to "manufacturer",
        "id" to m.id.value,
        "name" to m.name,
        "aliases" to m.aliases.toList(),
        "legalName" to m.legalName,
        "bluetoothCompanyId" to m.bluetoothCompanyId,
        "protocolOwner" to m.protocolOwner,
        "evidenceIds" to m.evidenceIds.map { it.value },
        "metadata" to encodeMetadata(m.metadata),
    )

    fun decodeManufacturer(map: Map<String, Any?>): Manufacturer? {
        return try {
        val meta = KnowledgeJson.obj(map, "metadata")?.let(::decodeMetadata) ?: return null
        Manufacturer(
            id = ManufacturerId(KnowledgeJson.string(map, "id") ?: return null),
            name = KnowledgeJson.string(map, "name") ?: return null,
            aliases = KnowledgeJson.stringSet(map, "aliases"),
            legalName = KnowledgeJson.string(map, "legalName"),
            bluetoothCompanyId = KnowledgeJson.int(map, "bluetoothCompanyId"),
            protocolOwner = KnowledgeJson.string(map, "protocolOwner"),
            evidenceIds = KnowledgeJson.stringList(map, "evidenceIds").map(::EvidenceId),
            metadata = meta,
        )
    } catch (e: Exception) {
        null
    }
    }

    // --- device model ---

    fun encode(m: DeviceModel): Map<String, Any?> = mapOf(
        "kind" to "deviceModel",
        "id" to m.id.value,
        "manufacturerId" to m.manufacturerId.value,
        "name" to m.name,
        "aliases" to m.aliases.toList(),
        "productFamily" to m.productFamily,
        "hardwareRevision" to m.hardwareRevision,
        "identificationRules" to m.identificationRules,
        "transports" to m.transports.toList(),
        "protocolIds" to m.protocolIds.map { it.value },
        "evidenceIds" to m.evidenceIds.map { it.value },
        "metadata" to encodeMetadata(m.metadata),
    )

    fun decodeDeviceModel(map: Map<String, Any?>): DeviceModel? {
        return try {
        val meta = KnowledgeJson.obj(map, "metadata")?.let(::decodeMetadata) ?: return null
        DeviceModel(
            id = DeviceModelId(KnowledgeJson.string(map, "id") ?: return null),
            manufacturerId = ManufacturerId(
                KnowledgeJson.string(map, "manufacturerId") ?: return null,
            ),
            name = KnowledgeJson.string(map, "name") ?: return null,
            aliases = KnowledgeJson.stringSet(map, "aliases"),
            productFamily = KnowledgeJson.string(map, "productFamily"),
            hardwareRevision = KnowledgeJson.string(map, "hardwareRevision"),
            identificationRules = KnowledgeJson.stringList(map, "identificationRules"),
            transports = KnowledgeJson.stringSet(map, "transports"),
            protocolIds = KnowledgeJson.stringList(map, "protocolIds").map(::ProtocolId),
            evidenceIds = KnowledgeJson.stringList(map, "evidenceIds").map(::EvidenceId),
            metadata = meta,
        )
    } catch (e: Exception) {
        null
    }
    }

    // --- firmware profile ---

    fun encode(f: FirmwareProfile): Map<String, Any?> = mapOf(
        "kind" to "firmwareProfile",
        "id" to f.id.value,
        "deviceModelId" to f.deviceModelId.value,
        "versionConstraint" to f.versionConstraint,
        "firmwareUnknown" to f.firmwareUnknown,
        "hardwareRevision" to f.hardwareRevision,
        "compatibleProtocolIds" to f.compatibleProtocolIds.map { it.value },
        "behavioralDifferences" to f.behavioralDifferences,
        "capabilityDifferences" to f.capabilityDifferences,
        "evidenceIds" to f.evidenceIds.map { it.value },
        "metadata" to encodeMetadata(f.metadata),
    )

    fun decodeFirmwareProfile(map: Map<String, Any?>): FirmwareProfile? {
        return try {
        val meta = KnowledgeJson.obj(map, "metadata")?.let(::decodeMetadata) ?: return null
        FirmwareProfile(
            id = FirmwareProfileId(KnowledgeJson.string(map, "id") ?: return null),
            deviceModelId = DeviceModelId(
                KnowledgeJson.string(map, "deviceModelId") ?: return null,
            ),
            versionConstraint = KnowledgeJson.string(map, "versionConstraint"),
            firmwareUnknown = KnowledgeJson.bool(map, "firmwareUnknown"),
            hardwareRevision = KnowledgeJson.string(map, "hardwareRevision"),
            compatibleProtocolIds = KnowledgeJson.stringList(map, "compatibleProtocolIds")
                .map(::ProtocolId),
            behavioralDifferences = KnowledgeJson.stringList(map, "behavioralDifferences"),
            capabilityDifferences = KnowledgeJson.stringList(map, "capabilityDifferences"),
            evidenceIds = KnowledgeJson.stringList(map, "evidenceIds").map(::EvidenceId),
            metadata = meta,
        )
    } catch (e: Exception) {
        null
    }
    }

    // --- protocol definition ---

    fun encode(p: ProtocolDefinition): Map<String, Any?> = mapOf(
        "kind" to "protocol",
        "id" to p.id.value,
        "name" to p.name,
        "family" to p.family,
        "version" to p.version,
        "transports" to p.transports.toList(),
        "framing" to p.framing,
        "encoding" to p.encoding,
        "schemaIds" to p.schemaIds.map { it.value },
        "compatibleModelIds" to p.compatibleModelIds.map { it.value },
        "compatibleFirmwareIds" to p.compatibleFirmwareIds.map { it.value },
        "evidenceIds" to p.evidenceIds.map { it.value },
        "metadata" to encodeMetadata(p.metadata),
    )

    fun decodeProtocol(map: Map<String, Any?>): ProtocolDefinition? {
        return try {
        val meta = KnowledgeJson.obj(map, "metadata")?.let(::decodeMetadata) ?: return null
        ProtocolDefinition(
            id = ProtocolId(KnowledgeJson.string(map, "id") ?: return null),
            name = KnowledgeJson.string(map, "name") ?: return null,
            family = KnowledgeJson.string(map, "family") ?: return null,
            version = KnowledgeJson.string(map, "version") ?: return null,
            transports = KnowledgeJson.stringSet(map, "transports"),
            framing = KnowledgeJson.string(map, "framing"),
            encoding = KnowledgeJson.string(map, "encoding"),
            schemaIds = KnowledgeJson.stringList(map, "schemaIds").map(::MessageSchemaId),
            compatibleModelIds = KnowledgeJson.stringList(map, "compatibleModelIds")
                .map(::DeviceModelId),
            compatibleFirmwareIds = KnowledgeJson.stringList(map, "compatibleFirmwareIds")
                .map(::FirmwareProfileId),
            evidenceIds = KnowledgeJson.stringList(map, "evidenceIds").map(::EvidenceId),
            metadata = meta,
        )
    } catch (e: Exception) {
        null
    }
    }

    // --- message schema ---

    fun encode(s: MessageSchema): Map<String, Any?> = mapOf(
        "kind" to "messageSchema",
        "id" to s.id.value,
        "protocolId" to s.protocolId.value,
        "schemaVersion" to s.schemaVersion,
        "messageId" to s.messageId,
        "direction" to s.direction.name,
        "framingConstraints" to s.framingConstraints,
        "fields" to s.fields.map { f ->
            mapOf(
                "name" to f.name,
                "offsetBytes" to f.offsetBytes,
                "lengthBytes" to f.lengthBytes,
                "type" to f.type,
                "meaning" to f.meaning,
                "constraints" to f.constraints,
            )
        },
        "validationConstraints" to s.validationConstraints,
        "correlationRules" to s.correlationRules,
        "evidenceIds" to s.evidenceIds.map { it.value },
        "metadata" to encodeMetadata(s.metadata),
    )

    fun decodeMessageSchema(map: Map<String, Any?>): MessageSchema? {
        return try {
        val meta = KnowledgeJson.obj(map, "metadata")?.let(::decodeMetadata) ?: return null
        MessageSchema(
            id = MessageSchemaId(KnowledgeJson.string(map, "id") ?: return null),
            protocolId = ProtocolId(KnowledgeJson.string(map, "protocolId") ?: return null),
            schemaVersion = KnowledgeJson.int(map, "schemaVersion") ?: return null,
            messageId = KnowledgeJson.string(map, "messageId") ?: return null,
            direction = MessageDirection.valueOf(
                KnowledgeJson.string(map, "direction") ?: return null,
            ),
            framingConstraints = KnowledgeJson.stringList(map, "framingConstraints"),
            fields = KnowledgeJson.list(map, "fields").mapNotNull { item ->
                @Suppress("UNCHECKED_CAST")
                val fm = item as? Map<String, Any?> ?: return@mapNotNull null
                MessageField(
                    name = KnowledgeJson.string(fm, "name") ?: return@mapNotNull null,
                    offsetBytes = KnowledgeJson.int(fm, "offsetBytes"),
                    lengthBytes = KnowledgeJson.int(fm, "lengthBytes"),
                    type = KnowledgeJson.string(fm, "type") ?: "bytes",
                    meaning = KnowledgeJson.string(fm, "meaning"),
                    constraints = KnowledgeJson.stringList(fm, "constraints"),
                )
            },
            validationConstraints = KnowledgeJson.stringList(map, "validationConstraints"),
            correlationRules = KnowledgeJson.stringList(map, "correlationRules"),
            evidenceIds = KnowledgeJson.stringList(map, "evidenceIds").map(::EvidenceId),
            metadata = meta,
        )
    } catch (e: Exception) {
        null
    }
    }

    // --- capability definition ---

    fun encode(c: CapabilityDefinition): Map<String, Any?> = mapOf(
        "kind" to "capability",
        "id" to c.id.value,
        "category" to c.category,
        "name" to c.name,
        "valueTypes" to c.valueTypes.toList(),
        "constraints" to c.constraints,
        "requiredOperationIds" to c.requiredOperationIds.map { it.value },
        "dependencies" to c.dependencies.map { it.value },
        "conflicts" to c.conflicts.map { it.value },
        "applicableModelIds" to c.applicableModelIds.map { it.value },
        "applicableFirmwareIds" to c.applicableFirmwareIds.map { it.value },
        "readable" to c.readable,
        "writable" to c.writable,
        "persistenceVerifiable" to c.persistenceVerifiable,
        "evidenceIds" to c.evidenceIds.map { it.value },
        "metadata" to encodeMetadata(c.metadata),
    )

    fun decodeCapability(map: Map<String, Any?>): CapabilityDefinition? {
        return try {
        val meta = KnowledgeJson.obj(map, "metadata")?.let(::decodeMetadata) ?: return null
        CapabilityDefinition(
            id = CapabilityDefId(KnowledgeJson.string(map, "id") ?: return null),
            category = KnowledgeJson.string(map, "category") ?: return null,
            name = KnowledgeJson.string(map, "name") ?: return null,
            valueTypes = KnowledgeJson.stringSet(map, "valueTypes"),
            constraints = KnowledgeJson.stringList(map, "constraints"),
            requiredOperationIds = KnowledgeJson.stringList(map, "requiredOperationIds")
                .map(::OperationDefId),
            dependencies = KnowledgeJson.stringList(map, "dependencies").map(::CapabilityDefId),
            conflicts = KnowledgeJson.stringList(map, "conflicts").map(::CapabilityDefId),
            applicableModelIds = KnowledgeJson.stringList(map, "applicableModelIds")
                .map(::DeviceModelId),
            applicableFirmwareIds = KnowledgeJson.stringList(map, "applicableFirmwareIds")
                .map(::FirmwareProfileId),
            readable = KnowledgeJson.bool(map, "readable"),
            writable = KnowledgeJson.bool(map, "writable"),
            persistenceVerifiable = KnowledgeJson.bool(map, "persistenceVerifiable"),
            evidenceIds = KnowledgeJson.stringList(map, "evidenceIds").map(::EvidenceId),
            metadata = meta,
        )
    } catch (e: Exception) {
        null
    }
    }

    // --- operation definition ---

    fun encode(o: OperationDefinition): Map<String, Any?> = mapOf(
        "kind" to "operation",
        "id" to o.id.value,
        "protocolId" to o.protocolId.value,
        "schemaIds" to o.schemaIds.map { it.value },
        "category" to o.category,
        "name" to o.name,
        "inputContract" to o.inputContract,
        "outputContract" to o.outputContract,
        "preconditions" to o.preconditions,
        "validationRules" to o.validationRules,
        "timeoutMillis" to o.timeoutMillis,
        "retryConstraints" to o.retryConstraints,
        "readBackBehavior" to o.readBackBehavior,
        "idempotency" to o.idempotency,
        "securityConstraints" to o.securityConstraints,
        "evidenceIds" to o.evidenceIds.map { it.value },
        "metadata" to encodeMetadata(o.metadata),
    )

    fun decodeOperation(map: Map<String, Any?>): OperationDefinition? {
        return try {
        val meta = KnowledgeJson.obj(map, "metadata")?.let(::decodeMetadata) ?: return null
        OperationDefinition(
            id = OperationDefId(KnowledgeJson.string(map, "id") ?: return null),
            protocolId = ProtocolId(KnowledgeJson.string(map, "protocolId") ?: return null),
            schemaIds = KnowledgeJson.stringList(map, "schemaIds").map(::MessageSchemaId),
            category = KnowledgeJson.string(map, "category") ?: return null,
            name = KnowledgeJson.string(map, "name") ?: return null,
            inputContract = KnowledgeJson.string(map, "inputContract"),
            outputContract = KnowledgeJson.string(map, "outputContract"),
            preconditions = KnowledgeJson.stringList(map, "preconditions"),
            validationRules = KnowledgeJson.stringList(map, "validationRules"),
            timeoutMillis = KnowledgeJson.long(map, "timeoutMillis"),
            retryConstraints = KnowledgeJson.string(map, "retryConstraints"),
            readBackBehavior = KnowledgeJson.string(map, "readBackBehavior"),
            idempotency = KnowledgeJson.string(map, "idempotency"),
            securityConstraints = KnowledgeJson.stringList(map, "securityConstraints"),
            evidenceIds = KnowledgeJson.stringList(map, "evidenceIds").map(::EvidenceId),
            metadata = meta,
        )
    } catch (e: Exception) {
        null
    }
    }

    // --- evidence ---

    fun encode(e: EvidenceRecord): Map<String, Any?> = mapOf(
        "kind" to "evidence",
        "id" to e.id.value,
        "claimId" to e.claimId.value,
        "type" to e.type.name,
        "sourceId" to e.sourceId.value,
        "sourceDateMillis" to e.sourceDateMillis,
        "collectedAtMillis" to e.collectedAtMillis,
        "applicableModelId" to e.applicableModelId?.value,
        "applicableFirmwareId" to e.applicableFirmwareId?.value,
        "protocolId" to e.protocolId?.value,
        "reproducibility" to e.reproducibility,
        "sanitized" to e.sanitized,
        "reliability" to e.reliability,
        "limitations" to e.limitations,
        "stance" to e.stance.name,
        "verification" to e.verification.name,
        "integrity" to e.integrity,
        "metadata" to encodeMetadata(e.metadata),
    )

    fun decodeEvidence(map: Map<String, Any?>): EvidenceRecord? {
        return try {
        val meta = KnowledgeJson.obj(map, "metadata")?.let(::decodeMetadata) ?: return null
        EvidenceRecord(
            id = EvidenceId(KnowledgeJson.string(map, "id") ?: return null),
            claimId = ClaimId(KnowledgeJson.string(map, "claimId") ?: return null),
            type = EvidenceType.valueOf(KnowledgeJson.string(map, "type") ?: return null),
            sourceId = SourceId(KnowledgeJson.string(map, "sourceId") ?: return null),
            sourceDateMillis = KnowledgeJson.long(map, "sourceDateMillis"),
            collectedAtMillis = KnowledgeJson.long(map, "collectedAtMillis"),
            applicableModelId = KnowledgeJson.string(map, "applicableModelId")
                ?.let(::DeviceModelId),
            applicableFirmwareId = KnowledgeJson.string(map, "applicableFirmwareId")
                ?.let(::FirmwareProfileId),
            protocolId = KnowledgeJson.string(map, "protocolId")?.let(::ProtocolId),
            reproducibility = KnowledgeJson.string(map, "reproducibility"),
            sanitized = KnowledgeJson.bool(map, "sanitized"),
            reliability = KnowledgeJson.string(map, "reliability") ?: return null,
            limitations = KnowledgeJson.stringList(map, "limitations"),
            stance = EvidenceStance.valueOf(
                KnowledgeJson.string(map, "stance") ?: return null,
            ),
            verification = VerificationLevel.valueOf(
                KnowledgeJson.string(map, "verification") ?: return null,
            ),
            integrity = KnowledgeJson.string(map, "integrity"),
            metadata = meta,
        )
    } catch (e: Exception) {
        null
    }
    }

    // --- claim ---

    fun encode(c: Claim): Map<String, Any?> = mapOf(
        "kind" to "claim",
        "id" to c.id.value,
        "subject" to c.subject,
        "predicate" to c.predicate,
        "objectValue" to c.objectValue,
        "scope" to c.scope,
        "supportingEvidenceIds" to c.supportingEvidenceIds.map { it.value },
        "contradictingEvidenceIds" to c.contradictingEvidenceIds.map { it.value },
        "confidence" to c.confidence.name,
        "status" to c.status.name,
        "review" to c.review,
        "metadata" to encodeMetadata(c.metadata),
    )

    fun decodeClaim(map: Map<String, Any?>): Claim? {
        return try {
        val meta = KnowledgeJson.obj(map, "metadata")?.let(::decodeMetadata) ?: return null
        Claim(
            id = ClaimId(KnowledgeJson.string(map, "id") ?: return null),
            subject = KnowledgeJson.string(map, "subject") ?: return null,
            predicate = KnowledgeJson.string(map, "predicate") ?: return null,
            objectValue = KnowledgeJson.string(map, "objectValue") ?: return null,
            scope = KnowledgeJson.string(map, "scope") ?: return null,
            supportingEvidenceIds = KnowledgeJson.stringList(map, "supportingEvidenceIds")
                .map(::EvidenceId),
            contradictingEvidenceIds = KnowledgeJson.stringList(map, "contradictingEvidenceIds")
                .map(::EvidenceId),
            confidence = ClaimConfidence.valueOf(
                KnowledgeJson.string(map, "confidence") ?: return null,
            ),
            status = ClaimStatus.valueOf(
                KnowledgeJson.string(map, "status") ?: return null,
            ),
            review = KnowledgeJson.string(map, "review"),
            metadata = meta,
        )
    } catch (e: Exception) {
        null
    }
    }

    // --- source ---

    fun encode(s: Source): Map<String, Any?> = mapOf(
        "kind" to "source",
        "id" to s.id.value,
        "type" to s.type.name,
        "reference" to s.reference,
        "collectedAtMillis" to s.collectedAtMillis,
        "licenseConstraints" to s.licenseConstraints,
        "transformationHistory" to s.transformationHistory,
        "metadata" to encodeMetadata(s.metadata),
    )

    fun decodeSource(map: Map<String, Any?>): Source? {
        return try {
        val meta = KnowledgeJson.obj(map, "metadata")?.let(::decodeMetadata) ?: return null
        Source(
            id = SourceId(KnowledgeJson.string(map, "id") ?: return null),
            type = SourceType.valueOf(KnowledgeJson.string(map, "type") ?: return null),
            reference = KnowledgeJson.string(map, "reference") ?: return null,
            collectedAtMillis = KnowledgeJson.long(map, "collectedAtMillis"),
            licenseConstraints = KnowledgeJson.string(map, "licenseConstraints"),
            transformationHistory = KnowledgeJson.stringList(map, "transformationHistory"),
            metadata = meta,
        )
    } catch (e: Exception) {
        null
    }
    }

    /**
     * Decode any record by its "kind" discriminator.
     * Returns null for unknown kinds or malformed records.
     */
    fun decodeRecord(map: Map<String, Any?>): Any? = when (map["kind"]) {
        "manufacturer" -> decodeManufacturer(map)
        "deviceModel" -> decodeDeviceModel(map)
        "firmwareProfile" -> decodeFirmwareProfile(map)
        "protocol" -> decodeProtocol(map)
        "messageSchema" -> decodeMessageSchema(map)
        "capability" -> decodeCapability(map)
        "operation" -> decodeOperation(map)
        "evidence" -> decodeEvidence(map)
        "claim" -> decodeClaim(map)
        "source" -> decodeSource(map)
        else -> null
    }
}
