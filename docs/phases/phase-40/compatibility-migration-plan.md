# Phase 40 — Compatibility Migration Plan

## Current state

One contract version (1). No shipped vendor integrations. No
persisted compatibility data to migrate.

## Future migrations

- Contract version bumps: adapters declare their version;
  unknown versions rejected.
- Protocol version changes: explicit compatibility records;
  never silently reinterpret persisted data.
- Feature definition changes: namespaced IDs keep old and new
  distinct.
- Knowledge records: preserve provenance across migrations.

## Rules

- No silent reinterpretation of existing data.
- Migrations are explicit, testable, and auditable.
