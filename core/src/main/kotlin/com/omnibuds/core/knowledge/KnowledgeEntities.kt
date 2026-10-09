package com.omnibuds.core.knowledge

/**
 * A manufacturer or brand.
 *
 * Phase 22 (OB-P22-REQ-026): the marketing brand, legal manufacturer,
 * Bluetooth company identifier, and protocol owner are distinct concepts.
 * They are represented explicitly when they differ.
 */
data class Manufacturer(
    val id: ManufacturerId,
    /** Canonical name. */
    val name: String,
    /** Known aliases, normalized for lookup. */
    val aliases: Set<String> = emptySet(),
    /** Legal entity name, when known and different from the brand. */
    val legalName: String? = null,
    /** Bluetooth SIG company identifier, when known. */
    val bluetoothCompanyId: Int? = null,
    /** Owner of the proprietary protocol, when different from the brand. */
    val protocolOwner: String? = null,
    val evidenceIds: List<EvidenceId> = emptyList(),
    val metadata: RecordMetadata,
) {
    init {
        require(name.isNotBlank()) { "manufacturer name must not be blank" }
        require(id.value.isNotBlank()) { "manufacturer id must not be blank" }
    }
}

/**
 * A device model.
 *
 * Phase 22: never identified solely from a Bluetooth display name; sibling
 * products are never merged by product family.
 */
data class DeviceModel(
    val id: DeviceModelId,
    val manufacturerId: ManufacturerId,
    /** Canonical model name. */
    val name: String,
    /** Marketing names and aliases, normalized for lookup. */
    val aliases: Set<String> = emptySet(),
    /** Product family, when known. Does not imply shared protocol. */
    val productFamily: String? = null,
    /** Hardware revision constraints, e.g. "rev A-C". Null when unknown. */
    val hardwareRevision: String? = null,
    /** Deterministic identification rules referencing fingerprint dimensions. */
    val identificationRules: List<String> = emptyList(),
    /** Supported transports, when evidenced. */
    val transports: Set<String> = emptySet(),
    val protocolIds: List<ProtocolId> = emptyList(),
    val evidenceIds: List<EvidenceId> = emptyList(),
    val metadata: RecordMetadata,
) {
    init {
        require(name.isNotBlank()) { "model name must not be blank" }
        require(id.value.isNotBlank()) { "model id must not be blank" }
    }
}

/**
 * Firmware compatibility profile.
 *
 * Phase 22 (OB-P22-REQ-014): unknown firmware is explicit; compatibility
 * is never assumed across versions.
 */
data class FirmwareProfile(
    val id: FirmwareProfileId,
    val deviceModelId: DeviceModelId,
    /** Version constraint, e.g. ">= 3.1.0 < 4.0.0". Null = unknown firmware. */
    val versionConstraint: String? = null,
    /** True when the firmware version is genuinely unknown. */
    val firmwareUnknown: Boolean = false,
    val hardwareRevision: String? = null,
    val compatibleProtocolIds: List<ProtocolId> = emptyList(),
    /** Known behavioral differences at this firmware. */
    val behavioralDifferences: List<String> = emptyList(),
    /** Capability differences at this firmware. */
    val capabilityDifferences: List<String> = emptyList(),
    val evidenceIds: List<EvidenceId> = emptyList(),
    val metadata: RecordMetadata,
)

/**
 * A protocol definition (specification, not implementation).
 *
 * Phase 22: distinguishes the specification from an executable adapter.
 */
data class ProtocolDefinition(
    val id: ProtocolId,
    val name: String,
    /** Protocol family, e.g. "vendor-rfcomm". */
    val family: String,
    /** Protocol version, e.g. "1.2". */
    val version: String,
    /** Required transports. */
    val transports: Set<String> = emptySet(),
    /** Framing description reference. */
    val framing: String? = null,
    /** Encoding description reference. */
    val encoding: String? = null,
    /** Schema IDs for this protocol version. */
    val schemaIds: List<MessageSchemaId> = emptyList(),
    val compatibleModelIds: List<DeviceModelId> = emptyList(),
    val compatibleFirmwareIds: List<FirmwareProfileId> = emptyList(),
    val evidenceIds: List<EvidenceId> = emptyList(),
    val metadata: RecordMetadata,
) {
    init {
        require(name.isNotBlank()) { "protocol name must not be blank" }
        require(version.isNotBlank()) { "protocol version must not be blank" }
    }
}
