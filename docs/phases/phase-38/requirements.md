# Phase 38 — Requirements

**ID scheme:** `A38-REQ-001` … `A38-REQ-016`.

## A38-REQ-001 — Infrastructure audit
- **Description:** Audit Phases 30–37 infrastructure; record reuse
  and gaps.
- **Rationale:** Extend, don't duplicate.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** repository-audit.md exists.
- **Verification:** Review. **Status:** VERIFIED.

## A38-REQ-002 — Execution environments
- **Description:** `HilEnvironment` with 6 values; physical-hardware
  distinction.
- **Rationale:** Simulated never reported as hardware.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Environment tests pass.
- **Verification:** HilEnvironmentTest. **Status:** VERIFIED.

## A38-REQ-003 — Hardware profile schema
- **Description:** Versioned `HardwareProfile` + validator; unknown
  fields explicit nulls.
- **Rationale:** No invented device metadata.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Profile tests pass.
- **Verification:** HardwareProfileValidatorTest. **Status:** VERIFIED.

## A38-REQ-004 — Safety policy
- **Description:** `HilSafetyPolicy`/`HilSafetyGate` in executable
  code: physical off, writes off, destructive never routine.
- **Rationale:** Safety enforced, not just documented.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Gate tests pass.
- **Verification:** HilSafetyGateTest. **Status:** VERIFIED.

## A38-REQ-005 — Mutating authorization
- **Description:** 7-factor `authorizeMutatingOperation`; all must
  hold.
- **Rationale:** Future writes need full authorization.
- **Dependencies:** A38-REQ-004. **Priority:** Must.
- **Acceptance:** Authorization tests pass.
- **Verification:** HilSafetyGateTest. **Status:** VERIFIED.

## A38-REQ-006 — Rig abstraction
- **Description:** `HilRig` interface + `FakeHilRig`; no Bluetooth
  API calls.
- **Rationale:** Injectable, testable rigs.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Rig tests pass.
- **Verification:** HilCampaignExecutorTest. **Status:** VERIFIED.

## A38-REQ-007 — Campaign executor
- **Description:** `HilCampaignExecutor`: 9 stages with typed
  outcomes; cancellation, cleanup failures preserved.
- **Rationale:** Deterministic, auditable campaigns.
- **Dependencies:** A38-REQ-004/006. **Priority:** Must.
- **Acceptance:** Executor tests pass.
- **Verification:** HilCampaignExecutorTest. **Status:** VERIFIED.

## A38-REQ-008 — Physical checks deferred
- **Description:** Physical checks get
  DEFERRED_TO_FINAL_HARDWARE_VERIFICATION, never PASS.
- **Rationale:** Honest evidence.
- **Dependencies:** A38-REQ-007. **Priority:** Must.
- **Acceptance:** Deferral test passes.
- **Verification:** HilCampaignExecutorTest. **Status:** VERIFIED.

## A38-REQ-009 — No physical execution
- **Description:** Dry runs perform no physical operations; fake
  rig records requested operations.
- **Rationale:** Phase boundary.
- **Dependencies:** A38-REQ-006/007. **Priority:** Must.
- **Acceptance:** No-physical test passes.
- **Verification:** HilCampaignExecutorTest. **Status:** VERIFIED.

## A38-REQ-010 — Reporting
- **Description:** `HilExecutionReport`: counts, clean flag,
  environment; outcome and evidence level separate.
- **Rationale:** Truthful reports.
- **Dependencies:** A38-REQ-007. **Priority:** Must.
- **Acceptance:** Report tests pass.
- **Verification:** HilCampaignExecutorTest. **Status:** VERIFIED.

## A38-REQ-011 — Hardware test plan
- **Description:** hardware-test-plan.md exists.
- **Rationale:** Phase 52 readiness.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** VERIFIED.

## A38-REQ-012 — Safety and consent doc
- **Description:** safety-and-consent.md exists.
- **Rationale:** Documented policy.
- **Dependencies:** A38-REQ-004. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** VERIFIED.

## A38-REQ-013 — Deferred campaigns
- **Description:** deferred-hardware-campaigns.md: campaigns A–F
  defined, all deferred to Phase 52.
- **Rationale:** Ready for later execution.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** VERIFIED.

## A38-REQ-014 — Evidence and reporting doc
- **Description:** evidence-and-reporting.md exists.
- **Rationale:** Evidence quality rules.
- **Dependencies:** A38-REQ-010. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** VERIFIED.

## A38-REQ-015 — Documentation
- **Description:** All 14 documents exist and agree with
  implementation.
- **Rationale:** Traceability.
- **Dependencies:** all. **Priority:** Must.
- **Acceptance:** Docs complete.
- **Verification:** Review. **Status:** IN_PROGRESS.

## A38-REQ-016 — Regression
- **Description:** All existing tests pass.
- **Rationale:** No regressions.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Full suite green.
- **Verification:** Full run. **Status:** IN_PROGRESS.
