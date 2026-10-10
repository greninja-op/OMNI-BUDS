# Phase 45 — Metadata Migration Plan

## 1. Schema Versioning
Firmware compatibility metadata is managed under `FirmwareMetadataSchema`:
- `CURRENT_SCHEMA_VERSION = 1`
- Supported versions: `setOf(1)`

## 2. DTO Record Structure
Records are represented via `FirmwareCompatibilityRuleRecord`:
- `ruleId: String`
- `manufacturer: String`
- `applicableModels: List<String>`
- `constraintType: String` (`ANY`, `EXACT`, `ALLOWLIST`, `DENYLIST`, `AT_LEAST`)
- `constraintValue: String`
- `targetScope: String?`
- `outcome: String` (`COMPATIBLE`, `COMPATIBLE_WITH_LIMITATIONS`, `INCOMPATIBLE`, `UNKNOWN_OR_WITHDRAWN`)
- `evidenceReference: String`
- `verificationLevel: String`
- `limitations: List<String>`
- `rationale: String`

## 3. Migration Safety Guarantees
- Unrecognized enum values safely fall back to `INFERRED` verification level or reject the record.
- Empty or blank mandatory fields (`ruleId`, `manufacturer`, `evidenceReference`) cause record rejection (returns `null`).
- Migration is idempotent and side-effect free.
