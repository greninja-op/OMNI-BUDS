# Phase 22 — Database Migrations

## Strategy

- Schema version metadata on every record (`RecordMetadata.recordVersion`)
  and on the format (`KnowledgeCodecs.SCHEMA_VERSION`).
- Explicit migrations: when the schema version increments, a migration
  function transforms vN records to vN+1. No silent coercion.
- Current schema version is 1; there are no legacy schemas, so no
  migrations exist yet. This is honest: we do not invent legacy schemas
  to claim migration tests.

## Compatibility rules

- Unknown future schema versions are rejected on load and import.
- Unknown fields are ignored on read within a supported version.
- Missing required fields fail explicitly (record reported corrupt).

## Recovery

- `loadAll` stages all records before committing; any corrupt record
  blocks the load and is reported with its key.
- `saveAll` reports false on partial write failure.
- Import validates fully before applying anything.

## Migration tests

When schema v2 is introduced, migration tests must cover v1→v2 on real
v1 snapshots. Until then, the versioning machinery (rejection of future
versions, forward-tolerant reads) is tested.
