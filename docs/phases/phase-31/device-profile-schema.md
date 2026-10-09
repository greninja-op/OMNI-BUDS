# Phase 31 — Device Profile Schema

## Fields

See specs.md. Key rules:

- All descriptive fields nullable; unknown stays unknown.
- `capabilities`: declared capability IDs the profile claims.
- `evidence`: profile's verification category.
- `fixtures`: backing fixture IDs.
- `limitations`: free-text known limits.

## Versioning

schemaVersion 1. Unsupported versions rejected. Future versions need
migration documentation.

## Validation

- Blank IDs rejected.
- Hardware-verified profiles require manufacturer + model.
- No guessing: a missing firmware version is unknown, not "latest".
