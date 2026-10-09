# Phase 37 — Requirements

**ID scheme:** `A37-REQ-001` … `A37-REQ-016`.

## A37-REQ-001 — Protocol audit
- **Description:** Audit lab/testkit/security/recovery infrastructure
  and record the implementation plan.
- **Rationale:** Build on real components.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** repository-audit.md exists.
- **Verification:** Review. **Status:** VERIFIED.

## A37-REQ-002 — Versioned test-case schema
- **Description:** `ProtocolTestCase` schema v1 with validator:
  rejects bad versions, path-like fixtures, non-hex, oversized
  input, impossible timeouts.
- **Rationale:** External definitions are untrusted.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Validator tests pass.
- **Verification:** ProtocolTestCaseValidatorTest. **Status:** VERIFIED.

## A37-REQ-003 — Deterministic runner
- **Description:** `ProtocolTestRunner`: sorted execution, per-test
  timeout, campaign budget, cancellation, isolation.
- **Rationale:** Repeatable offline campaigns.
- **Dependencies:** A37-REQ-002, LabParser. **Priority:** Must.
- **Acceptance:** Runner tests pass.
- **Verification:** ProtocolTestRunnerTest. **Status:** VERIFIED.

## A37-REQ-004 — Parser conformance
- **Description:** parsed/incomplete/rejected outcomes tested per
  parser contract.
- **Rationale:** Framing correctness.
- **Dependencies:** A37-REQ-003. **Priority:** Must.
- **Acceptance:** Conformance tests pass.
- **Verification:** ProtocolTestRunnerTest. **Status:** VERIFIED.

## A37-REQ-005 — Malformed-input campaigns
- **Description:** Deterministic offline malformed/boundary cases;
  no hardware writes.
- **Rationale:** Parser safety.
- **Dependencies:** A37-REQ-003. **Priority:** Must.
- **Acceptance:** Campaign tests pass.
- **Verification:** ProtocolTestRunnerTest. **Status:** VERIFIED.

## A37-REQ-006 — Isolation
- **Description:** Failing parsers/tests cannot corrupt the next
  test; parser exceptions become error outcomes.
- **Rationale:** Campaign integrity.
- **Dependencies:** A37-REQ-003. **Priority:** Must.
- **Acceptance:** Isolation tests pass.
- **Verification:** ProtocolTestRunnerTest. **Status:** VERIFIED.

## A37-REQ-007 — Campaign selection
- **Description:** `ProtocolCampaign` with prefix includes/excludes;
  4 standard offline campaigns.
- **Rationale:** Stable, reviewable selection.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Selection tests pass.
- **Verification:** ProtocolCampaignTest. **Status:** VERIFIED.

## A37-REQ-008 — Phase 30 integration
- **Description:** `toTestResult` maps to TestResult/PASSED/FAILED.
- **Rationale:** One reporting model.
- **Dependencies:** A37-REQ-003. **Priority:** Must.
- **Acceptance:** Conversion test passes.
- **Verification:** ProtocolTestRunnerTest. **Status:** VERIFIED.

## A37-REQ-009 — Evidence policy
- **Description:** protocol-evidence-policy.md: synthetic tests never
  become hardware verification.
- **Rationale:** Honest evidence.
- **Dependencies:** Phase 31 model. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** VERIFIED.

## A37-REQ-010 — Regression catalog
- **Description:** protocol-regression-catalog.md: known regressions
  and their tests.
- **Rationale:** Regressions stay fixed.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** VERIFIED.

## A37-REQ-011 — Resource limits
- **Description:** 64 KiB input, 30 s timeout, 1000 cases/campaign;
  documented rationale.
- **Rationale:** Bounded execution.
- **Dependencies:** A37-REQ-002/003. **Priority:** Must.
- **Acceptance:** Limits tested.
- **Verification:** ProtocolTestCaseValidatorTest. **Status:** VERIFIED.

## A37-REQ-012 — Security
- **Description:** No executable test definitions; fixture paths
  cannot escape; traces never executed as commands.
- **Rationale:** Runner is not an attack surface.
- **Dependencies:** A37-REQ-002. **Priority:** Must.
- **Acceptance:** Validator tests pass.
- **Verification:** ProtocolTestCaseValidatorTest. **Status:** VERIFIED.

## A37-REQ-013 — Campaigns document
- **Description:** protocol-test-campaigns.md exists.
- **Rationale:** Documented selection.
- **Dependencies:** A37-REQ-007. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** VERIFIED.

## A37-REQ-014 — Runner document
- **Description:** protocol-test-runner.md exists.
- **Rationale:** Documented behavior.
- **Dependencies:** A37-REQ-003. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** VERIFIED.

## A37-REQ-015 — Documentation
- **Description:** All 14 documents exist and agree with implementation.
- **Rationale:** Traceability.
- **Dependencies:** all. **Priority:** Must.
- **Acceptance:** Docs complete.
- **Verification:** Review. **Status:** IN_PROGRESS.

## A37-REQ-016 — Regression
- **Description:** All existing tests pass.
- **Rationale:** No regressions.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Full suite green.
- **Verification:** Full run. **Status:** IN_PROGRESS.
