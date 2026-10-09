# Phase 22 — Knowledge Import/Export

## Format

Versioned JSON document:
```json
{
  "packageVersion": 1,
  "schemaVersion": 1,
  "exporter": "string",
  "recordCount": 0,
  "records": [ { "kind": "...", ... } ]
}
```

## Import validation

1. Package size ≤ 4 MiB.
2. `packageVersion` present and ≤ current.
3. `schemaVersion` equals current.
4. Records ≤ 10,000.
5. Each record decodes by its "kind"; malformed records rejected.
6. No duplicate identifiers.
7. All cross-references resolve within the package.

## Dry-run

`KnowledgePackage.validate` performs full validation without touching
the repository. Callers must offer dry-run before applying.

## Transactional import

`KnowledgePackage.import` validates first; on success applies all records.
Validation failures write nothing. Rejected puts fail the import with a
structured reason.

## Provenance

Sources carry type, reference, license constraints, and transformation
history. Export preserves stable IDs and provenance. Synthetic data is
never labeled as a capture.

## Integrity

Checksums (`EvidenceRecord.integrity`) detect accidental corruption of
referenced artifacts; they do not prove authorship. No signing
infrastructure exists; the trusted-import boundary is documented here:
only import packages from sources you trust, after dry-run validation.

## Export rules

- Deterministic output (sorted by ID, sorted JSON keys).
- Only the records the caller selects.
- No credentials, no user-specific identifiers, no session data.
- License constraints preserved.
