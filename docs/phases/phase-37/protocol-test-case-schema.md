# Phase 37 — Protocol Test-Case Schema

## Version

Schema version 1 (only supported version).

## Fields

testCaseId, schemaVersion, protocolId, protocolVersion, fixtureId,
fixtureOrigin, inputHex, expectedOutcome, expectedFailureCategory?,
timeoutMillis, campaigns, requirementRefs.

## Validation rules

- Blank IDs rejected.
- Unknown schema versions rejected.
- fixtureId must be a plain identifier (no `/`, `\`, `..`).
- inputHex: valid hex, ≤ 131,072 chars (64 KiB).
- timeoutMillis: 1–30,000 ms.
- expectedOutcome required.

## Security

Declarative data only. No executable code, shell commands,
reflection targets, or device writes can be expressed.
