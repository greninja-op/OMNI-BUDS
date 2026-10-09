# Phase 23 — Design

## Architecture

New package `com.omnibuds.core.extension` at layer 5:

- **VendorFeatureId.kt** — namespaced identifiers (`vendor.<mfg>.<family>.<feature>`,
  `ext.<mfg>.<name>`).
- **VendorExtensionDescriptor.kt** — extension metadata, trust levels,
  lifecycle.
- **VendorFeatureDefinition.kt** — typed feature schema, value types,
  constraints, validation.
- **ExtensionRegistry.kt** — registration, duplicate/cycle detection,
  deterministic resolution.
- **ExtensionCompatibility.kt** — 10-rule compatibility evaluation.
- **FeatureDependencies.kt** — prerequisite/conflict checking.
- **ExtensionExecution.kt** — `VendorFeatureExtension` contract, optional
  reader/writer interfaces, typed read/write results, operation contracts.

## Module boundaries

Shared feature engine (Phase 9) owns generic execution. The extension
framework owns vendor-specific definitions and resolution. Protocol
adapters (Phase 19) own transport. Access policy (Phase 21) owns
authorization (injected, not imported). Knowledge DB (Phase 22) owns
descriptive metadata.

## Registries

`ExtensionRegistry`: Mutex-guarded; registration validates structure,
duplicates, namespace declarations, and cycles; resolution returns all
candidates (never picks one); deprecate/disable preserve history.

## Feature resolution

Descriptors → compatibility evaluation → candidate extensions →
feature definitions → dependency check → value validation → access policy
(injected) → verified adapter execution.

## Value validation

Closed `VendorFeatureValue` shapes; `ValueConstraints` checked
deterministically with no side effects; no silent coercion.

## Dependencies

Prerequisites must be satisfied by observed device state, not local
preferences. Cycles rejected at registration. Conflicts return typed
results.

## Protocol integration

Extensions reference protocol IDs; execution goes through verified
adapters. The framework holds no transport handles.

## Security

Default-deny preserved; extension metadata never authorizes writes;
no dynamic code; bounded values; structured results.

## Concurrency

Mutex-guarded registry; deterministic ordering; cancellation via typed
results.

## Failure handling

Typed outcomes for every failure mode; ambiguous outcomes explicit;
no silent no-ops; no auto-retry of non-idempotent operations.
