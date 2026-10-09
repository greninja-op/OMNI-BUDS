# Phase 30 — Fixture Format

## Schema

`TestFixture(id, schema, version, provenance, payload)`.

## Known schemas (initial)

| Schema | Versions | Notes |
|---|---|---|
| capability-snapshot | 1–2 | Feature availability maps |
| transport-sequence | 1 | Ordered scripted outcomes |
| device-identity | 1 | Manufacturer/model/firmware |

## Rules

- Schemas versioned; unsupported versions rejected.
- Provenance explicit; SYNTHETIC default; REAL_CAPTURE needs consent.
- Unknown/unsupported states representable (nullable fields preserved).
- Battery nullability preserved — unknown never becomes zero.
- No network downloads; fixtures are code or local resources.
- Migrations versioned if fixtures persist.
