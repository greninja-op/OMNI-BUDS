# Phase 40 — Requirements

**ID scheme:** `A40-REQ-001` … `A40-REQ-014`.

## A40-REQ-001 — Architecture audit
- **Description:** Audit vendor architecture; produce gap report.
- **Rationale:** Extend, don't replace.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** repository-audit.md exists.
- **Verification:** Review. **Status:** VERIFIED.

## A40-REQ-002 — Integration contract versioning
- **Description:** `VendorIntegrationContract` with explicit
  contract versions.
- **Rationale:** Compatibility changes explicit and testable.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Contract tests pass.
- **Verification:** VendorIntegrationContractTest. **Status:** VERIFIED.

## A40-REQ-003 — Firmware compatibility
- **Description:** `FirmwareCompatibilityEvaluator`: VERIFIED /
  SUPPORTED_RANGE / UNKNOWN_FIRMWARE / INCOMPATIBLE.
- **Rationale:** Never invent firmware support.
- **Dependencies:** A40-REQ-002. **Priority:** Must.
- **Acceptance:** Evaluator tests pass.
- **Verification:** FirmwareCompatibilityEvaluatorTest. **Status:** VERIFIED.

## A40-REQ-004 — Typed resolution
- **Description:** `VendorResolution` with reasons; `VendorResolver`
  wraps the registry.
- **Rationale:** Diagnostics explain matching.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Resolver tests pass.
- **Verification:** VendorResolverTest. **Status:** VERIFIED.

## A40-REQ-005 — Multi-vendor isolation
- **Description:** Deterministic tests proving isolation with
  synthetic adapters.
- **Rationale:** Integrations cannot leak.
- **Dependencies:** A40-REQ-004. **Priority:** Must.
- **Acceptance:** Isolation tests pass.
- **Verification:** VendorIsolationTest. **Status:** VERIFIED.

## A40-REQ-006 — No invented protocols
- **Description:** No real vendor protocol implemented; synthetic
  adapters clearly labeled.
- **Rationale:** Evidence integrity.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** No guessed packet formats in code.
- **Verification:** Review. **Status:** VERIFIED.

## A40-REQ-007 — Authorization integrity
- **Description:** No new execution paths; vendor operations still
  use DeviceAccessPolicy.
- **Rationale:** Centralized authorization.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** No new entry points.
- **Verification:** Review. **Status:** VERIFIED.

## A40-REQ-008 — Integration contract doc
- **Description:** vendor-integration-contract.md exists.
- **Rationale:** Documented contract.
- **Dependencies:** A40-REQ-002. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** VERIFIED.

## A40-REQ-009 — Registry doc
- **Description:** vendor-registry-and-resolution.md exists.
- **Rationale:** Documented resolution.
- **Dependencies:** A40-REQ-004. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** VERIFIED.

## A40-REQ-010 — Identity policy doc
- **Description:** identity-and-compatibility-policy.md exists.
- **Rationale:** Documented matching rules.
- **Dependencies:** A40-REQ-003/004. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** VERIFIED.

## A40-REQ-011 — Isolation doc
- **Description:** vendor-isolation.md exists.
- **Rationale:** Documented isolation.
- **Dependencies:** A40-REQ-005. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** VERIFIED.

## A40-REQ-012 — Onboarding guide
- **Description:** vendor-onboarding-guide.md explains adding a
  manufacturer.
- **Rationale:** Future contributors.
- **Dependencies:** all contracts. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** VERIFIED.

## A40-REQ-013 — Documentation
- **Description:** All 15 documents exist and agree with
  implementation.
- **Rationale:** Traceability.
- **Dependencies:** all. **Priority:** Must.
- **Acceptance:** Docs complete.
- **Verification:** Review. **Status:** IN_PROGRESS.

## A40-REQ-014 — Regression
- **Description:** All existing tests pass.
- **Rationale:** No regressions.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Full suite green.
- **Verification:** Full run. **Status:** IN_PROGRESS.
