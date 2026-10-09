# Phase 17 — Persistent Configuration Engine: Requirements

**Status:** Authoritative for Phase 17 execution.
**Scope:** Store, retrieve, validate, migrate, and manage OmniBuds
configuration. Strict separation: app preferences ≠ device config ≠
observed hardware ≠ firmware persistence. No UI, no invented capabilities.
**Requirement ID scheme:** `OB-P17-REQ-001` … `OB-P17-REQ-024`.

---

## OB-P17-REQ-001 — Configuration domains

- **Description:** Separate global app preferences, device-specific
  preferences, applied-config metadata, and hardware-observed state
  (owned by runtime engines, never the config engine).
- **Rationale:** Different owners, different lifetimes.
- **Priority:** Must
- **Acceptance criteria:** Four domains modeled.
- **Verification:** `ConfigurationEngineTest`.

## OB-P17-REQ-002 — Typed configuration values

- **Description:** Reuse `ConfigurationValue` (Phase 9): boolean, int,
  string, mode, float, range, structured, bitmask, custom. No arbitrary
  executable values.
- **Rationale:** Types prevent ambiguity.
- **Priority:** Must
- **Acceptance criteria:** All 9 types supported.
- **Verification:** `ConfigurationCodecTest`.

## OB-P17-REQ-003 — Device identity

- **Description:** `DeviceConfigurationKey` wraps fingerprint-derived
  identityKey. Blank refused. No raw Bluetooth addresses. Ambiguous
  identity → no automatic restore.
- **Rationale:** Isolation without privacy cost.
- **Priority:** Must
- **Acceptance criteria:** Key validation; isolation tests.
- **Verification:** `ConfigurationEngineTest`.

## OB-P17-REQ-004 — Repository contract

- **Description:** Explicit result types: Found/NotFound/Invalid/
  NeedsMigration/ReadFailed; Saved/ValidationFailed/WriteFailed.
  Saved means committed.
- **Rationale:** Nullable hides outcomes.
- **Priority:** Must
- **Acceptance criteria:** All outcomes tested.
- **Verification:** `ConfigurationEngineTest`.

## OB-P17-REQ-005 — Storage

- **Description:** `ConfigurationStorage` interface; `InMemory` for tests;
  `FileConfigurationStorage` (Android module) with atomic temp+rename
  writes. Core stays JVM-free.
- **Rationale:** Testable, portable, safe.
- **Priority:** Must
- **Acceptance criteria:** Atomicity; core has no java.io.
- **Verification:** Architecture test; storage tests.

## OB-P17-REQ-006 — Validation

- **Description:** Validate before persistence and before application:
  schema, keys, lengths, counts, value shapes. Never silently clamp.
- **Rationale:** Invalid in → invalid out is a bug.
- **Priority:** Must
- **Acceptance criteria:** Validation tests.
- **Verification:** `ConfigurationValidationTest`.

## OB-P17-REQ-007 — Migrations

- **Description:** Versioned schemas; deterministic migrations; contiguous
  chain; future schemas throw (never overwrite). Failed migrations →
  structured errors.
- **Rationale:** Data survives evolution.
- **Priority:** Must
- **Acceptance criteria:** Migration tests.
- **Verification:** `ConfigurationMigrationTest`.

## OB-P17-REQ-008 — Corruption recovery

- **Description:** Malformed/truncated/unknown data → Invalid (not silent
  default). No sensitive logging.
- **Rationale:** Corruption is explicit.
- **Priority:** Must
- **Acceptance criteria:** Corruption tests.
- **Verification:** `ConfigurationEngineTest`.

## OB-P17-REQ-009 — Reset

- **Description:** Scoped reset: global, one device, never others. Reset
  deletes local prefs; never claims hardware was reset.
- **Rationale:** Scope matters.
- **Priority:** Must
- **Acceptance criteria:** Reset tests.
- **Verification:** `ConfigurationEngineTest`.

## OB-P17-REQ-010 — Concurrency

- **Description:** Per-device mutexes; global lock separate. No global
  lock for independent devices. No main-thread blocking.
- **Rationale:** Devices are independent.
- **Priority:** Must
- **Acceptance criteria:** Concurrency tests.
- **Verification:** `ConfigurationEngineTest`.

## OB-P17-REQ-011 — Capability-aware eligibility

- **Description:** Before application: capability known, supported,
  writable. Unknown/unsupported/read-only → NotEligible with reason.
  Saved ≠ eligible ≠ applied.
- **Rationale:** Preferences don't override reality.
- **Priority:** Must
- **Acceptance criteria:** Eligibility tests.
- **Verification:** `ApplicationEligibilityTest`.

## OB-P17-REQ-012 — Preference vs hardware

- **Description:** Saved preference never implies hardware state.
  Local persistence never implies firmware persistence.
- **Rationale:** The phase's core honesty rule.
- **Priority:** Must
- **Acceptance criteria:** Scope tests ban claim vocabulary.
- **Verification:** `ConfigurationScopeTest`.

## OB-P17-REQ-013 — Flow observation

- **Description:** Immutable published configs; dedup; per-device flows;
  cancellation-safe.
- **Rationale:** Reactive without leaks.
- **Priority:** Should
- **Acceptance criteria:** Flow tests.
- **Verification:** `ConfigurationEngineTest`.

## OB-P17-REQ-014 — Defaults

- **Description:** Distinguish app default / user preference / device
  default / unknown. Never invent hardware defaults.
- **Rationale:** Defaults have owners.
- **Priority:** Should
- **Acceptance criteria:** Documented.
- **Verification:** Review.

## OB-P17-REQ-015 — EQ/ANC/codec/gesture prefs

- **Description:** Model as typed preferences; validate against known
  constraints; never present as applied.
- **Rationale:** Preferences, not commands.
- **Priority:** Should
- **Acceptance criteria:** Typed models exist.
- **Verification:** Review.

## OB-P17-REQ-016 — Security/privacy

- **Description:** No addresses, no secrets, no payload logging, local only.
- **Rationale:** Data minimization.
- **Priority:** Must
- **Acceptance criteria:** Scope tests; review.
- **Verification:** `ConfigurationScopeTest`.

## OB-P17-REQ-017 — No UI

- **Description:** No UI code.
- **Rationale:** Boundary.
- **Priority:** Must
- **Acceptance criteria:** Scope test.
- **Verification:** `ConfigurationScopeTest`.

## OB-P17-REQ-018 — Architecture

- **Description:** `core.configuration` at layer 5; Android storage behind
  platform boundary; core has no JVM-only imports.
- **Rationale:** Boundaries.
- **Priority:** Must
- **Acceptance criteria:** `DependencyDirectionTest` passes.
- **Verification:** Architecture test.

## OB-P17-REQ-019 — KMP future

- **Description:** Core is platform-agnostic; file I/O behind adapter.
- **Rationale:** Portability.
- **Priority:** Should
- **Acceptance criteria:** No platform types in core.
- **Verification:** Architecture test.

## OB-P17-REQ-020 — Performance

- **Description:** No main-thread blocking; bounded histories; no
  duplicate repos; no arbitrary retries.
- **Rationale:** Lightweight.
- **Priority:** Must
- **Acceptance criteria:** Review.
- **Verification:** Review.

## OB-P17-REQ-021 — Documentation

- **Description:** Eight mandatory records.
- **Rationale:** Understandability.
- **Priority:** Must
- **Acceptance criteria:** All present.
- **Verification:** Review.

## OB-P17-REQ-022 — Regression

- **Description:** All Phase 0–16 tests pass.
- **Rationale:** No regressions.
- **Priority:** Must
- **Acceptance criteria:** Full suite green.
- **Verification:** Full test run.

## OB-P17-REQ-023 — Stop condition

- **Description:** Phase 18 not started; no UI; no physical device.
- **Rationale:** Boundary.
- **Priority:** Must
- **Acceptance criteria:** Final audit.
- **Verification:** Scope tests.

## OB-P17-REQ-024 — JSON codec

- **Description:** Deterministic pure-Kotlin JSON for ConfigurationValue.
  Unknown types fail explicitly; malformed input → null (corruption path).
- **Rationale:** No new dependencies; format under our control.
- **Priority:** Must
- **Acceptance criteria:** Round-trip tests.
- **Verification:** `ConfigurationCodecTest`.
