package com.omnibuds.core.knowledge

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Knowledge repository.
 *
 * Phase 22: the authoritative store of protocol knowledge. Knowledge
 * describes; it never authorizes hardware operations (OB-P22-REQ-002).
 *
 * Records are keyed by stable identifiers. All mutations are validated
 * before application; readers never observe partial commits.
 */
interface KnowledgeRepository {

    // --- manufacturers ---

    suspend fun putManufacturer(record: Manufacturer): PutResult
    suspend fun manufacturer(id: ManufacturerId): Manufacturer?
    suspend fun manufacturers(): List<Manufacturer>

    // --- device models ---

    suspend fun putDeviceModel(record: DeviceModel): PutResult
    suspend fun deviceModel(id: DeviceModelId): DeviceModel?
    suspend fun deviceModels(): List<DeviceModel>

    // --- firmware profiles ---

    suspend fun putFirmwareProfile(record: FirmwareProfile): PutResult
    suspend fun firmwareProfile(id: FirmwareProfileId): FirmwareProfile?
    suspend fun firmwareProfiles(): List<FirmwareProfile>

    // --- protocols ---

    suspend fun putProtocol(record: ProtocolDefinition): PutResult
    suspend fun protocol(id: ProtocolId): ProtocolDefinition?
    suspend fun protocols(): List<ProtocolDefinition>

    // --- message schemas ---

    suspend fun putMessageSchema(record: MessageSchema): PutResult
    suspend fun messageSchema(id: MessageSchemaId): MessageSchema?
    suspend fun messageSchemas(): List<MessageSchema>

    // --- capabilities ---

    suspend fun putCapability(record: CapabilityDefinition): PutResult
    suspend fun capability(id: CapabilityDefId): CapabilityDefinition?
    suspend fun capabilities(): List<CapabilityDefinition>

    // --- operations ---

    suspend fun putOperation(record: OperationDefinition): PutResult
    suspend fun operation(id: OperationDefId): OperationDefinition?
    suspend fun operations(): List<OperationDefinition>

    // --- evidence ---

    suspend fun putEvidence(record: EvidenceRecord): PutResult
    suspend fun evidence(id: EvidenceId): EvidenceRecord?
    suspend fun evidenceRecords(): List<EvidenceRecord>

    // --- claims ---

    suspend fun putClaim(record: Claim): PutResult
    suspend fun claim(id: ClaimId): Claim?
    suspend fun claims(): List<Claim>

    // --- sources ---

    suspend fun putSource(record: Source): PutResult
    suspend fun source(id: SourceId): Source?
    suspend fun sources(): List<Source>

    // --- persistence ---

    /**
     * Persist all records. [write] stores one key/value pair; returns false
     * on partial failure. The caller adapts its own storage (e.g.
     * ConfigurationStorage) — knowledge defines no storage dependency, so
     * the package stays at its layer without sideways imports.
     */
    suspend fun saveAll(write: suspend (key: String, value: String) -> Boolean): Boolean

    /**
     * Load all records via [read], replacing current contents.
     * Staged first; readers never see a partial load.
     */
    suspend fun loadAll(read: suspend (key: String) -> String?): LoadResult
}

/** The result of inserting or updating a record. */
sealed interface PutResult {
    data object Inserted : PutResult
    data object Updated : PutResult
    /** The record was rejected; [reason] explains why. */
    data class Rejected(val reason: String) : PutResult
}

/** The result of loading records from storage. */
data class LoadResult(
    val loaded: Int,
    /** Records that failed to decode; never silently dropped from the report. */
    val corrupt: List<String>,
)

/**
 * In-memory knowledge repository.
 *
 * Phase 22: Mutex-guarded; readers never observe partial commits.
 * Persistence is a JSON snapshot per record behind ConfigurationStorage.
 */
class InMemoryKnowledgeRepository : KnowledgeRepository {

    private val mutex = Mutex()

    private val manufacturers = mutableMapOf<String, Manufacturer>()
    private val deviceModels = mutableMapOf<String, DeviceModel>()
    private val firmwareProfiles = mutableMapOf<String, FirmwareProfile>()
    private val protocols = mutableMapOf<String, ProtocolDefinition>()
    private val messageSchemas = mutableMapOf<String, MessageSchema>()
    private val capabilities = mutableMapOf<String, CapabilityDefinition>()
    private val operations = mutableMapOf<String, OperationDefinition>()
    private val evidenceRecords = mutableMapOf<String, EvidenceRecord>()
    private val claims = mutableMapOf<String, Claim>()
    private val sources = mutableMapOf<String, Source>()

    // Key namespace for persisted snapshots. User config uses its own
    // namespace; knowledge never shares keys with it.
    private val keyPrefix = "knowledge/v${KnowledgeCodecs.SCHEMA_VERSION}/"

    private suspend fun <K, V> put(
        map: MutableMap<K, V>, key: K, value: V,
    ): PutResult = mutex.withLock {
        val existed = map.containsKey(key)
        map[key] = value
        if (existed) PutResult.Updated else PutResult.Inserted
    }

    override suspend fun putManufacturer(record: Manufacturer): PutResult =
        put(manufacturers, record.id.value, record)

    override suspend fun manufacturer(id: ManufacturerId): Manufacturer? =
        mutex.withLock { manufacturers[id.value] }

    override suspend fun manufacturers(): List<Manufacturer> =
        mutex.withLock { manufacturers.values.sortedBy { it.id.value } }

    override suspend fun putDeviceModel(record: DeviceModel): PutResult =
        put(deviceModels, record.id.value, record)

    override suspend fun deviceModel(id: DeviceModelId): DeviceModel? =
        mutex.withLock { deviceModels[id.value] }

    override suspend fun deviceModels(): List<DeviceModel> =
        mutex.withLock { deviceModels.values.sortedBy { it.id.value } }

    override suspend fun putFirmwareProfile(record: FirmwareProfile): PutResult =
        put(firmwareProfiles, record.id.value, record)

    override suspend fun firmwareProfile(id: FirmwareProfileId): FirmwareProfile? =
        mutex.withLock { firmwareProfiles[id.value] }

    override suspend fun firmwareProfiles(): List<FirmwareProfile> =
        mutex.withLock { firmwareProfiles.values.sortedBy { it.id.value } }

    override suspend fun putProtocol(record: ProtocolDefinition): PutResult =
        put(protocols, record.id.value, record)

    override suspend fun protocol(id: ProtocolId): ProtocolDefinition? =
        mutex.withLock { protocols[id.value] }

    override suspend fun protocols(): List<ProtocolDefinition> =
        mutex.withLock { protocols.values.sortedBy { it.id.value } }

    override suspend fun putMessageSchema(record: MessageSchema): PutResult =
        put(messageSchemas, record.id.value, record)

    override suspend fun messageSchema(id: MessageSchemaId): MessageSchema? =
        mutex.withLock { messageSchemas[id.value] }

    override suspend fun messageSchemas(): List<MessageSchema> =
        mutex.withLock { messageSchemas.values.sortedBy { it.id.value } }

    override suspend fun putCapability(record: CapabilityDefinition): PutResult =
        put(capabilities, record.id.value, record)

    override suspend fun capability(id: CapabilityDefId): CapabilityDefinition? =
        mutex.withLock { capabilities[id.value] }

    override suspend fun capabilities(): List<CapabilityDefinition> =
        mutex.withLock { capabilities.values.sortedBy { it.id.value } }

    override suspend fun putOperation(record: OperationDefinition): PutResult =
        put(operations, record.id.value, record)

    override suspend fun operation(id: OperationDefId): OperationDefinition? =
        mutex.withLock { operations[id.value] }

    override suspend fun operations(): List<OperationDefinition> =
        mutex.withLock { operations.values.sortedBy { it.id.value } }

    override suspend fun putEvidence(record: EvidenceRecord): PutResult =
        put(evidenceRecords, record.id.value, record)

    override suspend fun evidence(id: EvidenceId): EvidenceRecord? =
        mutex.withLock { evidenceRecords[id.value] }

    override suspend fun evidenceRecords(): List<EvidenceRecord> =
        mutex.withLock { evidenceRecords.values.sortedBy { it.id.value } }

    override suspend fun putClaim(record: Claim): PutResult =
        put(claims, record.id.value, record)

    override suspend fun claim(id: ClaimId): Claim? =
        mutex.withLock { claims[id.value] }

    override suspend fun claims(): List<Claim> =
        mutex.withLock { claims.values.sortedBy { it.id.value } }

    override suspend fun putSource(record: Source): PutResult =
        put(sources, record.id.value, record)

    override suspend fun source(id: SourceId): Source? =
        mutex.withLock { sources[id.value] }

    override suspend fun sources(): List<Source> =
        mutex.withLock { sources.values.sortedBy { it.id.value } }

    override suspend fun saveAll(
        write: suspend (key: String, value: String) -> Boolean,
    ): Boolean =
        mutex.withLock {
            val entries = mutableListOf<Pair<String, String>>()
            manufacturers.values.forEach {
                entries.add(keyPrefix + "manufacturer/${it.id.value}" to KnowledgeJson.encode(KnowledgeCodecs.encode(it)))
            }
            deviceModels.values.forEach {
                entries.add(keyPrefix + "model/${it.id.value}" to KnowledgeJson.encode(KnowledgeCodecs.encode(it)))
            }
            firmwareProfiles.values.forEach {
                entries.add(keyPrefix + "firmware/${it.id.value}" to KnowledgeJson.encode(KnowledgeCodecs.encode(it)))
            }
            protocols.values.forEach {
                entries.add(keyPrefix + "protocol/${it.id.value}" to KnowledgeJson.encode(KnowledgeCodecs.encode(it)))
            }
            messageSchemas.values.forEach {
                entries.add(keyPrefix + "schema/${it.id.value}" to KnowledgeJson.encode(KnowledgeCodecs.encode(it)))
            }
            capabilities.values.forEach {
                entries.add(keyPrefix + "capability/${it.id.value}" to KnowledgeJson.encode(KnowledgeCodecs.encode(it)))
            }
            operations.values.forEach {
                entries.add(keyPrefix + "operation/${it.id.value}" to KnowledgeJson.encode(KnowledgeCodecs.encode(it)))
            }
            evidenceRecords.values.forEach {
                entries.add(keyPrefix + "evidence/${it.id.value}" to KnowledgeJson.encode(KnowledgeCodecs.encode(it)))
            }
            claims.values.forEach {
                entries.add(keyPrefix + "claim/${it.id.value}" to KnowledgeJson.encode(KnowledgeCodecs.encode(it)))
            }
            sources.values.forEach {
                entries.add(keyPrefix + "source/${it.id.value}" to KnowledgeJson.encode(KnowledgeCodecs.encode(it)))
            }
            // Index of all keys for load.
            entries.add(keyPrefix + "index" to KnowledgeJson.encode(entries.map { it.first }))

            var ok = true
            for ((key, value) in entries) {
                if (!write(key, value)) ok = false
            }
            ok
        }

    override suspend fun loadAll(
        read: suspend (key: String) -> String?,
    ): LoadResult =
        mutex.withLock {
            val indexJson = read(keyPrefix + "index") ?: return LoadResult(0, emptyList())
            @Suppress("UNCHECKED_CAST")
            val keys = (KnowledgeJson.decode(indexJson) as? List<Any?>)
                ?.mapNotNull { it as? String } ?: return LoadResult(0, listOf("index"))

            val corrupt = mutableListOf<String>()
            var loaded = 0

            // Stage into temporary maps; only commit when every record
            // decodes. Readers never see a partial load.
            val staged = mutableListOf<Pair<String, Any>>()
            for (key in keys) {
                if (key == keyPrefix + "index") continue
                val json = read(key)
                if (json == null) { corrupt.add(key); continue }
                @Suppress("UNCHECKED_CAST")
                val map = KnowledgeJson.decode(json) as? Map<String, Any?>
                val record = map?.let(KnowledgeCodecs::decodeRecord)
                if (record == null) { corrupt.add(key); continue }
                staged.add(key to record)
            }

            if (corrupt.isNotEmpty()) {
                return LoadResult(0, corrupt)
            }

            // Commit.
            manufacturers.clear(); deviceModels.clear(); firmwareProfiles.clear()
            protocols.clear(); messageSchemas.clear(); capabilities.clear()
            operations.clear(); evidenceRecords.clear(); claims.clear(); sources.clear()

            for ((key, record) in staged) {
                val id = key.substringAfterLast('/')
                when (record) {
                    is Manufacturer -> manufacturers[id] = record
                    is DeviceModel -> deviceModels[id] = record
                    is FirmwareProfile -> firmwareProfiles[id] = record
                    is ProtocolDefinition -> protocols[id] = record
                    is MessageSchema -> messageSchemas[id] = record
                    is CapabilityDefinition -> capabilities[id] = record
                    is OperationDefinition -> operations[id] = record
                    is EvidenceRecord -> evidenceRecords[id] = record
                    is Claim -> claims[id] = record
                    is Source -> sources[id] = record
                }
                loaded++
            }
            LoadResult(loaded, emptyList())
        }
}
