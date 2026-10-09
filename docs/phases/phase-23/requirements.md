# Phase 23 — Vendor-Specific Feature Framework: Requirements

**Status:** Authoritative for Phase 23 execution.
**Scope:** Vendor extension contract, namespaced identifiers, typed feature
schemas, extension registry, compatibility resolution, dependencies/conflicts,
execution contracts. No UI, no Phase 24, no physical hardware.
**Requirement ID scheme:** `OB-P23-REQ-001` … `OB-P23-REQ-030`.

## OB-P23-REQ-001 — Extension contract
- **Description:** Stable typed `VendorFeatureExtension` contract: descriptor,
  feature resolution, observation, execution. Capability-specific optional
  interfaces preferred over one mega-interface.
- **Priority:** Must | **Verification:** `ExtensionContractTest`.

## OB-P23-REQ-002 — Namespaced identifiers
- **Description:** `vendor.<vendor>.<feature>` format aligned with the
  established `FeatureId.ofVendor` grammar; validated; stable; unique;
  serialization-safe; distinct from shared IDs. Product family is descriptor
  metadata, not an identifier segment.
- **Priority:** Must | **Verification:** `ExtensionContractTest`.

## OB-P23-REQ-003 — Extension descriptor
- **Description:** Stable ID, manufacturer/protocol-family ref, version,
  compatible models, HW revision constraints, firmware rules, protocol
  IDs/versions, feature namespaces, transport requirements, dependencies,
  verification status, limitations, deprecation.
- **Priority:** Must | **Verification:** `ExtensionRegistryTest`.

## OB-P23-REQ-004 — Typed feature schema
- **Description:** Feature definitions with value types, constraints,
  defaults, access, dependencies, conflicts, required operations, applicable
  models/firmware, read-back/persistence support, evidence, verification.
- **Priority:** Must | **Verification:** `FeatureSchemaTest`.

## OB-P23-REQ-005 — Value types
- **Description:** Boolean, bounded int/float, enum, structured, validated
  lists, custom typed values. No unvalidated objects; no silent coercion.
- **Priority:** Must | **Verification:** `FeatureSchemaTest`.

## OB-P23-REQ-006 — Validation constraints
- **Description:** Min/max, enums, steps, list lengths, required fields,
  cross-field, model/firmware restrictions. Deterministic, side-effect-free.
- **Priority:** Must | **Verification:** `FeatureSchemaTest`.

## OB-P23-REQ-007 — Extension registry
- **Description:** Registration, structural validation, duplicate detection,
  lookup by ID, resolution by device+protocol, version selection,
  deprecation/disablement, deterministic ordering, audit info.
- **Priority:** Must | **Verification:** `ExtensionRegistryTest`.

## OB-P23-REQ-008 — Conflict handling
- **Description:** Reject/quarantine duplicates, conflicting definitions,
  incompatible protocols, ambiguous matches, namespace collisions, invalid
  dependencies, cycles, unsupported schema versions.
- **Priority:** Must | **Verification:** `ExtensionRegistryTest`.

## OB-P23-REQ-009 — Ambiguity preserved
- **Description:** Multiple valid candidates → explicit ambiguity result;
  restricted operations denied until resolved. Never first-match-wins.
- **Priority:** Must | **Verification:** `CompatibilityTest`.

## OB-P23-REQ-010 — Compatibility rules
- **Description:** 10 mandatory rules (§9): no manufacturer-only matching,
  no sibling assumption, no firmware assumption, unknown firmware separate,
  ambiguous identity insufficient, version-mismatch rejection, re-evaluation
  on evidence change, invalidation, no replay of denied ops, descriptor ≠
  enablement.
- **Priority:** Must | **Verification:** `CompatibilityTest`.

## OB-P23-REQ-011 — Compatibility results
- **Description:** Every result includes reason and evidence used.
- **Priority:** Must | **Verification:** `CompatibilityTest`.

## OB-P23-REQ-012 — Dependencies
- **Description:** Prerequisites, mutual exclusion, conditional availability,
  model/firmware restrictions, cycle rejection at registration, typed
  conflict results.
- **Priority:** Must | **Verification:** `DependencyTest`.

## OB-P23-REQ-013 — Execution contracts
- **Description:** Read/write/ack/read-back/idempotency/retry/volatility/
  persistence/timeout/failure declarations per operation.
- **Priority:** Must | **Verification:** `ExecutionContractTest`.

## OB-P23-REQ-014 — Write workflow
- **Description:** 12-step workflow (§11): resolve, confirm identity, resolve
  feature, validate capability, validate value, dependencies, access policy,
  execute via verified adapter, validate result, read back, update state,
  diagnostics.
- **Priority:** Must | **Verification:** `ExecutionContractTest`.

## OB-P23-REQ-015 — Access-policy integration
- **Description:** Every write passes Phase 21 access policy (injected, not
  imported — layer constraint). Extension metadata never authorizes writes.
- **Priority:** Must | **Verification:** `ExtensionSecurityTest`.

## OB-P23-REQ-016 — Ambiguous outcomes
- **Description:** Disconnect/timeout after submission → unknown outcome;
  no assumed success/failure; no auto-retry of non-idempotent ops.
- **Priority:** Must | **Verification:** `ExecutionContractTest`.

## OB-P23-REQ-017 — Unsupported operations
- **Description:** Structured unsupported results; never silent no-ops.
- **Priority:** Must | **Verification:** `ExecutionContractTest`.

## OB-P23-REQ-018 — State isolation
- **Description:** Definition, capability, requested config, execution,
  observed state, verification result kept separate. Namespaced preferences;
  no cross-device leakage.
- **Priority:** Must | **Verification:** `ExtensionSecurityTest`.

## OB-P23-REQ-019 — No control shortcuts
- **Description:** No unrestricted transport, no raw commands, no dynamic
  code loading, no downloaded scripts.
- **Priority:** Must | **Verification:** `ExtensionSecurityTest`.

## OB-P23-REQ-020 — Registration trust levels
- **Description:** Distinguish descriptive metadata, registered definition,
  available implementation, compatible implementation, approved operation,
  verified implementation.
- **Priority:** Must | **Verification:** `ExtensionRegistryTest`.

## OB-P23-REQ-021 — Architecture
- **Description:** `core.extension` at layer 5; imports only common(0),
  state(0), device(2), capability(2), protocol(4). No sideways imports.
- **Priority:** Must | **Verification:** Architecture test.

## OB-P23-REQ-022 — No second feature engine
- **Description:** Extend, don't duplicate, the shared feature engine.
- **Priority:** Must | **Verification:** Review.

## OB-P23-REQ-023 — Documentation
- **Description:** 12 required documents.
- **Priority:** Must | **Verification:** Review.

## OB-P23-REQ-024 — Regression
- **Description:** Phases 5–22 compatible; unknown-device restrictions hold.
- **Priority:** Must | **Verification:** Full test run.

## OB-P23-REQ-025 — No UI / Phase 24
- **Description:** No UI, Phase 24 not started.
- **Priority:** Must | **Verification:** Scope tests.

## OB-P23-REQ-026 — Deterministic tests
- **Description:** No hardware required.
- **Priority:** Must | **Verification:** Review.

## OB-P23-REQ-027 — Shared-feature equivalence
- **Description:** When vendor feature ≡ shared feature, use the common
  contract; vendor metadata separate.
- **Priority:** Must | **Verification:** Review.

## OB-P23-REQ-028 — Timeouts and cancellation
- **Description:** Timeout policy, cancellation cleanup, no leaked tasks.
- **Priority:** Must | **Verification:** `ExecutionContractTest`.

## OB-P23-REQ-029 — Diagnostics
- **Description:** Structured typed results; established error taxonomy;
  no raw payloads, no credentials.
- **Priority:** Must | **Verification:** `ExtensionSecurityTest`.

## OB-P23-REQ-030 — Local-first
- **Description:** No telemetry, no upload, no cloud.
- **Priority:** Must | **Verification:** Scope tests.
