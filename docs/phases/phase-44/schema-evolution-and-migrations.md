# Phase 44 — Schema Evolution and Migrations

---

## 1. Schema Versioning Rules

- Each protocol metadata record carries a `schemaVersion: ProtocolSchemaVersion`.
- The current repository metadata schema is `SchemaVersion(1)`.
- When evolving metadata (e.g. introducing new constraint fields or security policies), a new schema version `SchemaVersion(2)` is declared.
- All migrations between schemas must be implemented via `ProtocolSchemaMigrator`:
  - `migrate(record: StoredProtocolRecord): MigrationResult<StoredProtocolRecord>`
  - Must be pure and side-effect free.
  - Must verify source schema version == target schema version - 1.
  - Must preserve evidence IDs, author provenance, and creation timestamps.
  - Must validate invariants on the migrated record before returning.

---

## 2. Rejection of Unsupported Schemas

- Attempting to load a schema version newer than the runtime understands must result in `UnsupportedSchemaVersionException` or `CompatibilityStatus.UNSUPPORTED_SCHEMA`.
- Stored records are never partially migrated or modified in-place upon failure.
