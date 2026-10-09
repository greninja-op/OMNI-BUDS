# Phase 22 — Protocol Knowledge Database: Requirements

**Status:** Authoritative for Phase 22 execution.
**Scope:** Structured, versioned, queryable protocol knowledge; evidence/claims;
import/export; lifecycle/invalidation. Knowledge never authorizes hardware
operations. No UI, no Phase 23, no physical hardware.
**Requirement ID scheme:** `OB-P22-REQ-001` … `OB-P22-REQ-030`.

## OB-P22-REQ-001 — Knowledge domain model
- **Description:** Typed entities: Manufacturer, DeviceModel, FirmwareProfile,
  ProtocolDefinition, MessageSchema, CapabilityDefinition, OperationDefinition,
  EvidenceRecord, Claim, Source. Stable IDs, record versions, lifecycle states.
- **Priority:** Must | **Verification:** `KnowledgeModelTest`.

## OB-P22-REQ-002 — No second protocol registry
- **Description:** Knowledge describes; `ProtocolRegistry` executes. Stable-ID
  references between them, never merged.
- **Priority:** Must | **Verification:** Review.

## OB-P22-REQ-003 — Storage integration
- **Description:** JSON documents persisted via injected read/write function
  types (the caller adapts `ConfigurationStorage`); deterministic hand-written
  codec; namespaced keys; user config separate. No sideways layer imports.
- **Priority:** Must | **Verification:** `KnowledgeRepositoryTest`.

## OB-P22-REQ-004 — Evidence model
- **Description:** Evidence records with type, source, provenance, reliability,
  sanitization status, verification status. Contradictions preserved.
- **Priority:** Must | **Verification:** `EvidenceModelTest`.

## OB-P22-REQ-005 — Claims
- **Description:** Claims with subject/predicate/object, supporting and
  contradicting evidence, confidence, status. Hypotheses never silently
  promoted.
- **Priority:** Must | **Verification:** `EvidenceModelTest`.

## OB-P22-REQ-006 — Verification transitions
- **Description:** INFERRED→IMPLEMENTED→LAB_TESTED→HARDWARE_VERIFIED→
  PERSISTENCE_VERIFIED with explicit evidence rules; synthetic cannot reach
  hardware statuses; local DB cannot reach persistence.
- **Priority:** Must | **Verification:** `EvidenceModelTest`.

## OB-P22-REQ-007 — Query engine
- **Description:** Typed queries: manufacturers by name/alias; models by
  identity evidence (ambiguity-preserving); protocols for model+firmware;
  schemas; capabilities; operations; evidence for claim; unverified/conflicting
  claims; affected-by-version-change; incomplete firmware info; deprecated entries.
- **Priority:** Must | **Verification:** `KnowledgeQueryTest`.

## OB-P22-REQ-008 — Deterministic queries
- **Description:** Bounded, paginated, deterministically ordered; no-match vs
  incomplete distinguished; no fuzzy matching granting control.
- **Priority:** Must | **Verification:** `KnowledgeQueryTest`.

## OB-P22-REQ-009 — Lifecycle
- **Description:** DRAFT/RESEARCH/REVIEW_REQUIRED/ACTIVE/DEPRECATED/DISABLED/
  SUPERSEDED with transition rules; deprecation preserves history.
- **Priority:** Must | **Verification:** `KnowledgeLifecycleTest`.

## OB-P22-REQ-010 — Import validation
- **Description:** Versioned packages; field/type validation; size/nesting
  limits; ID/reference validation; duplicate/conflict detection; dry-run.
- **Priority:** Must | **Verification:** `KnowledgeImportExportTest`.

## OB-P22-REQ-011 — Safe import
- **Description:** Transactions/staging; no partial imports; structured errors;
  import never auto-registers adapters or enables writes.
- **Priority:** Must | **Verification:** `KnowledgeImportExportTest`.

## OB-P22-REQ-012 — Export
- **Description:** Deterministic output; provenance preserved; privacy/
  licensing respected; no credentials or user identifiers.
- **Priority:** Must | **Verification:** `KnowledgeImportExportTest`.

## OB-P22-REQ-013 — Invalidation
- **Description:** Knowledge changes emit typed events; caches invalidated;
  protocol resolution re-evaluated; previously denied ops never auto-execute.
- **Priority:** Must | **Verification:** `KnowledgeLifecycleTest`.

## OB-P22-REQ-014 — Firmware compatibility
- **Description:** Version-range constraints; unknown firmware explicit;
  safe-under-uncertainty rules; narrowing triggers re-evaluation.
- **Priority:** Must | **Verification:** `KnowledgeQueryTest`.

## OB-P22-REQ-015 — Provenance
- **Description:** Source type/reference, collection details, license
  constraints, transformation history. Synthetic never labeled as capture.
- **Priority:** Must | **Verification:** `EvidenceModelTest`.

## OB-P22-REQ-016 — Schema versioning
- **Description:** Schema version metadata; explicit migrations; unsupported
  future versions rejected; corrupt records handled.
- **Priority:** Must | **Verification:** `KnowledgeRepositoryTest`.

## OB-P22-REQ-017 — Data quality
- **Description:** Duplicate detection, orphaned-reference detection,
  referential integrity, deterministic tests for conflicting identities,
  cyclic dependencies, invalid schema refs.
- **Priority:** Must | **Verification:** `KnowledgeQualityTest`.

## OB-P22-REQ-018 — Security
- **Description:** Untrusted input until validated; no eval/dynamic code;
  no executable content; path-traversal-safe; oversized/nested packages
  rejected.
- **Priority:** Must | **Verification:** `KnowledgeSecurityTest`.

## OB-P22-REQ-019 — Concurrency
- **Description:** Concurrent readers never see partial commits; safe
  repositories; deterministic ordering.
- **Priority:** Must | **Verification:** `KnowledgeRepositoryTest`.

## OB-P22-REQ-020 — Access-policy boundary
- **Description:** Knowledge lookups never bypass Phase 21 access policy;
  identity matching returns candidates, authorization stays with the policy.
- **Priority:** Must | **Verification:** `KnowledgeSecurityTest`.

## OB-P22-REQ-021 — Architecture
- **Description:** `core.knowledge` at layer 5; imports only foundational
  layers; no sideways imports to feature/vendor.
- **Priority:** Must | **Verification:** Architecture test.

## OB-P22-REQ-022 — Documentation
- **Description:** 12 required documents.
- **Priority:** Must | **Verification:** Review.

## OB-P22-REQ-023 — Regression
- **Description:** Phases 5–21 compatible.
- **Priority:** Must | **Verification:** Full test run.

## OB-P22-REQ-024 — No UI / Phase 23
- **Description:** No UI, Phase 23 not started.
- **Priority:** Must | **Verification:** Scope tests.

## OB-P22-REQ-025 — Deterministic tests
- **Description:** No hardware required.
- **Priority:** Must | **Verification:** Review.

## OB-P22-REQ-026 — Manufacturer relationships
- **Description:** Brand, legal manufacturer, BT company ID, protocol owner
  represented explicitly when they differ.
- **Priority:** Must | **Verification:** `KnowledgeModelTest`.

## OB-P22-REQ-027 — Message schemas
- **Description:** Unknown/incomplete fields representable without fabricated
  semantics.
- **Priority:** Must | **Verification:** `KnowledgeModelTest`.

## OB-P22-REQ-028 — Operation definitions
- **Description:** Descriptive metadata only; no transport access; preconditions,
  timeouts, idempotency, security constraints.
- **Priority:** Must | **Verification:** `KnowledgeModelTest`.

## OB-P22-REQ-029 — Contradictory evidence
- **Description:** Preserved, never averaged or dropped; newest not auto-selected
  among incompatible sources.
- **Priority:** Must | **Verification:** `EvidenceModelTest`.

## OB-P22-REQ-030 — Local-first
- **Description:** No telemetry, no upload, no cloud sync.
- **Priority:** Must | **Verification:** Scope tests.
