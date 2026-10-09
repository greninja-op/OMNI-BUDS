# Phase 22 — Knowledge Schema

## Entities

| Entity | Key | Description |
|---|---|---|
| Manufacturer | `knowledge/v1/manufacturer/<id>` | Brand/legal/company-ID/protocol-owner |
| DeviceModel | `knowledge/v1/model/<id>` | Model, aliases, family, transports |
| FirmwareProfile | `knowledge/v1/firmware/<id>` | Version constraints, compatibility |
| ProtocolDefinition | `knowledge/v1/protocol/<id>` | Spec (not implementation) |
| MessageSchema | `knowledge/v1/schema/<id>` | Message structures, fields |
| CapabilityDefinition | `knowledge/v1/capability/<id>` | Capability knowledge |
| OperationDefinition | `knowledge/v1/operation/<id>` | Operation metadata |
| EvidenceRecord | `knowledge/v1/evidence/<id>` | Supporting/contradicting evidence |
| Claim | `knowledge/v1/claim/<id>` | Protocol claims |
| Source | `knowledge/v1/source/<id>` | Provenance |
| Index | `knowledge/v1/index` | List of all record keys |

## Relationships

- DeviceModel → Manufacturer (many-to-one)
- FirmwareProfile → DeviceModel (many-to-one)
- ProtocolDefinition ↔ DeviceModel, FirmwareProfile (many-to-many via ID lists)
- MessageSchema → ProtocolDefinition
- CapabilityDefinition → OperationDefinition, DeviceModel, FirmwareProfile
- OperationDefinition → ProtocolDefinition, MessageSchema
- EvidenceRecord → Claim, Source
- Claim → EvidenceRecord (supporting + contradicting)

## Constraints

- Unique stable IDs per entity kind.
- Referential integrity validated on import (references must resolve).
- Lifecycle transitions per the transition table.

## Indexes

The index key lists all record keys for load. Query-time indexes are
in-memory scans (bounded by MAX_RECORDS); a future SQL backend can add
proper indexes without changing the domain API.

## Schema-versioning policy

- `KnowledgeCodecs.SCHEMA_VERSION` = 1: the record format version.
- `KnowledgePackage.PACKAGE_VERSION` = 1: the import/export format version.
- `RecordMetadata.recordVersion`: per-record version, bumped on breaking changes.
- Unknown future schema/package versions are rejected, never coerced.
- Unknown fields are ignored on read (forward tolerance within a version).
