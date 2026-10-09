# Phase 23 — Specifications

## API contracts

### VendorFeatureExtension
- `val descriptor: VendorExtensionDescriptor`
- `suspend fun resolveFeatures(context: VendorFeatureContext): VendorFeatureResolution`

### VendorFeatureReader (optional)
- `suspend fun observeFeature(request: VendorFeatureReadRequest): VendorFeatureReadResult`

### VendorFeatureWriter (optional)
- `suspend fun executeFeature(request: VendorFeatureWriteRequest): VendorFeatureWriteResult`

## Data models

See design.md and source. Key types: `VendorFeatureId`, `VendorExtensionId`,
`VendorExtensionDescriptor`, `VendorFeatureDefinition`, `VendorFeatureValue`,
`ValueConstraints`, `OperationContract`, `CompatibilityResult`,
`DependencyResult`, `RegistrationResult`, `VendorFeatureReadResult`,
`VendorFeatureWriteResult`, `VendorFeatureResolution`.

## Identifier rules

- Feature: `vendor.<vendor>.<feature>` (3 segments, lowercase alphanumerics
  and hyphens) — aligned with `common.FeatureId.ofVendor` and
  `feature.VendorFeatureContract`. Product family is descriptor metadata,
  not an identifier segment.
- Extension: `ext.<vendor>.<name>`.
- Validated at construction; invalid rejected.

## Registration lifecycle

DRAFT → ACTIVE → DEPRECATED → DISABLED. Duplicates rejected. Cycles
rejected. Namespace must be declared. Owning extension must be registered.

## Compatibility constraints

Manufacturer ID (stable, not display name); exact model match; hardware
revision; firmware rules (`>= X`, `<= X`, `== X`, exact); protocol ID;
transport. Unknown firmware → `UnknownFirmware` result. Ambiguous identity
flagged on compatible results.

## Feature values

Boolean, Int, Float, Enum, Structured, List. Constraints: min/max, steps,
allowed enums, list lengths, required/allowed fields. No coercion.

## Execution contracts

Per-operation: readable, writable, ack expected, read-back, idempotent,
retry-safe, volatility, verifiable scopes, required evidence, timeout.

## Error semantics

Typed sealed results for every outcome. `Denied` for policy denials.
`Unknown` for ambiguous post-submission outcomes. `InvalidValue`,
`DependencyFailed`, `NotWritable`, `Unsupported`, `Rejected`,
`ReadBackMismatch`, `Cancelled`.

## Versioning behavior

Extension versions are strings; resolution selects among ACTIVE versions
deterministically (sorted by ID). Breaking changes require new extension
versions, not silent mutation.
