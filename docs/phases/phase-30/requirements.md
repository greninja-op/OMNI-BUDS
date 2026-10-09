# Phase 30 — Comprehensive Device Test Framework: Requirements

**Status:** Authoritative for Phase 30 execution.
**Scope:** Reusable test infrastructure (fixtures, scripted transport,
failure injection, evidence, reporting). Dogfoods itself with framework
tests. No hardware campaigns (Phases 31–33).
**Requirement ID scheme:** `OB-P30-REQ-001` … `OB-P30-REQ-022`.

## OB-P30-REQ-001 — Test-case contract
- **Description:** Structured test-case model: stable ID, name, category,
  requirement refs, preconditions, fixtures, inputs, steps, expected
  outcomes, timeout, cleanup, evidence, tags, determinism classification.
- **Priority:** Must | **Verification:** `TestkitContractTest`.

## OB-P30-REQ-002 — Versioned fixtures
- **Description:** Fixtures carry schema + version; invalid fixtures fail
  validation before execution; provenance explicit (synthetic, never
  claimed as real captures).
- **Priority:** Must | **Verification:** `FixtureValidationTest`.

## OB-P30-REQ-003 — Scripted transport
- **Description:** `ScriptedTransport` implements `TransportContract` with
  deterministic sequences: success, failure, timeout, disconnect,
  malformed, partial, duplicate, out-of-order, closed-during-op.
- **Priority:** Must | **Verification:** `ScriptedTransportTest`.

## OB-P30-REQ-004 — Failure injection
- **Description:** Test-only injection, scoped per test, no production
  backdoors, seeded randomness only.
- **Priority:** Must | **Verification:** `FailureInjectionTest`.

## OB-P30-REQ-005 — Evidence model
- **Description:** Structured results: id, suite, requirements, fixture
  version, revision, assertions, error category, determinism, evidence
  level, timestamp. Categories: passed/failed/skipped/blocked/cancelled/
  invalid/infrastructure-error.
- **Priority:** Must | **Verification:** `TestEvidenceTest`.

## OB-P30-REQ-006 — No evidence overstatement
- **Description:** A passing fake-transport test never upgrades a
  capability's verification status.
- **Priority:** Must | **Verification:** `TestEvidenceTest`.

## OB-P30-REQ-007 — Isolation
- **Description:** No shared mutable state, no order dependence, cleanup
  of coroutines/files/DBs, scoped injection.
- **Priority:** Must | **Verification:** `TestIsolationTest`.

## OB-P30-REQ-008 — Determinism
- **Description:** Deterministic suites use no wall-clock timing or
  uncontrolled sleeps; repeat runs give identical results.
- **Priority:** Must | **Verification:** `TestIsolationTest`.

## OB-P30-REQ-009 — Domain coverage
- **Description:** Framework-validating tests across transport,
  capability, feature, persistence, lifecycle, audio/codec/battery,
  dependency-conflict domains.
- **Priority:** Must | **Verification:** `DeviceTestSuiteTest`.

## OB-P30-REQ-010 — Negative tests
- **Description:** Malformed input, invalid framing, truncated messages,
  unknown types, oversized inputs rejected per contract.
- **Priority:** Must | **Verification:** `ScriptedTransportTest`.

## OB-P30-REQ-011 — Battery nullability
- **Description:** Unknown battery never becomes zero; null preserved.
- **Priority:** Must | **Verification:** `DeviceTestSuiteTest`.

## OB-P30-REQ-012 — Persistence distinction
- **Description:** Local persistence vs hardware persistence; DB write
  never reported as hardware retention.
- **Priority:** Must | **Verification:** `TestEvidenceTest`.

## OB-P30-REQ-013 — Conflict-engine integration
- **Description:** Hard conflicts never produce executable plans;
  planning free of hardware side effects.
- **Priority:** Must | **Verification:** `DeviceTestSuiteTest`.

## OB-P30-REQ-014 — Report generation
- **Description:** Structured reports preserve failure/blocked statuses;
  no sensitive data.
- **Priority:** Must | **Verification:** `TestEvidenceTest`.

## OB-P30-REQ-015 — Reproducible commands
- **Description:** Build/test commands documented and reproducible.
- **Priority:** Must | **Verification:** Review.

## OB-P30-REQ-016 — Coverage
- **Description:** Coverage measured where supported; critical gaps
  documented; no invented percentages.
- **Priority:** Must | **Verification:** Review.

## OB-P30-REQ-017 — No network/hardware
- **Description:** Ordinary suites need no Bluetooth adapter or internet.
- **Priority:** Must | **Verification:** Review.

## OB-P30-REQ-018 — No production backdoors
- **Description:** Test hooks unreachable from ordinary application input.
- **Priority:** Must | **Verification:** `FailureInjectionTest`.

## OB-P30-REQ-019 — Timeout policy
- **Description:** Every test case declares a timeout; hangs become
  infrastructure errors, not silent passes.
- **Priority:** Must | **Verification:** `TestkitContractTest`.

## OB-P30-REQ-020 — Skipped/blocked honesty
- **Description:** Skipped/blocked never converted to passes.
- **Priority:** Must | **Verification:** `TestEvidenceTest`.

## OB-P30-REQ-021 — Documentation
- **Description:** 13 required documents.
- **Priority:** Must | **Verification:** Review.

## OB-P30-REQ-022 — Regression
- **Description:** All existing tests pass.
- **Priority:** Must | **Verification:** Full run.
