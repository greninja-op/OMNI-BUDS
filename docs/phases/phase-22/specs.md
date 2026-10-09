# Phase 22 — Specifications

## Data models

All entities carry `RecordMetadata(recordVersion, lifecycle, createdAtMillis,
modifiedAtMillis, supersededBy, limitations)`. Identifiers are value classes
wrapping non-blank strings.

### Manufacturer
id, name, aliases, legalName?, bluetoothCompanyId?, protocolOwner?,
evidenceIds, metadata.

### DeviceModel
id, manufacturerId, name, aliases, productFamily?, hardwareRevision?,
identificationRules, transports, protocolIds, evidenceIds, metadata.

### FirmwareProfile
id, deviceModelId, versionConstraint?, firmwareUnknown, hardwareRevision?,
compatibleProtocolIds, behavioralDifferences, capabilityDifferences,
evidenceIds, metadata.

### ProtocolDefinition
id, name, family, version, transports, framing?, encoding?, schemaIds,
compatibleModelIds, compatibleFirmwareIds, evidenceIds, metadata.

### MessageSchema
id, protocolId, schemaVersion, messageId, direction, framingConstraints,
fields[] (name, offsetBytes?, lengthBytes?, type, meaning?, constraints),
validationConstraints, correlationRules, evidenceIds, metadata.

### CapabilityDefinition
id, category, name, valueTypes, constraints, requiredOperationIds,
dependencies, conflicts, applicableModelIds, applicableFirmwareIds,
readable, writable, persistenceVerifiable, evidenceIds, metadata.

### OperationDefinition
id, protocolId, schemaIds, category, name, inputContract?, outputContract?,
preconditions, validationRules, timeoutMillis?, retryConstraints?,
readBackBehavior?, idempotency?, securityConstraints, evidenceIds, metadata.

### EvidenceRecord
id, claimId, type, sourceId, sourceDateMillis?, collectedAtMillis?,
applicableModelId?, applicableFirmwareId?, protocolId?, reproducibility?,
sanitized, reliability, limitations, stance, verification, integrity?,
metadata. Invariant: SYNTHETIC_FIXTURE ⇒ verification ∉
{HARDWARE_VERIFIED, PERSISTENCE_VERIFIED}.

### Claim
id, subject, predicate, objectValue, scope, supportingEvidenceIds,
contradictingEvidenceIds, confidence, status, review?, metadata.

### Source
id, type, reference, collectedAtMillis?, licenseConstraints?,
transformationHistory, metadata.

## Identifiers and relationships

Stable IDs; joins by ID; aliases normalized case-insensitively for lookup
only, never for joins.

## Constraints

- Non-blank names/IDs.
- recordVersion ≥ 1.
- Lifecycle transitions per `KnowledgeLifecycleRules`.
- Accepted claims require supporting evidence.
- Protocols require schemas to activate.

## Query contracts

All queries are suspend functions returning lists; pagination helper with
page/pageSize bounds; deterministic ordering.

## Version semantics

Schema version (format) vs record version (per-record) vs package version
(import/export) are distinct.

## Error models

`PutResult`: Inserted/Updated/Rejected(reason). `LoadResult`: loaded count +
corrupt key list. `PackageValidation`: Valid(records)/Invalid(errors).
`ImportResult`: Applied(count)/Failed(errors).

## Migration behavior

Staged loads; corrupt records block the load and are reported; unsupported
schema versions rejected.

## Validation rules

Import: size ≤ 4 MiB, records ≤ 10,000, package/schema versions checked,
duplicate IDs rejected, references must resolve within the package.
