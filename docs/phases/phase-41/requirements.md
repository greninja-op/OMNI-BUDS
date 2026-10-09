# Phase 41 — Requirements

**ID scheme:** `A41-REQ-001` … `A41-REQ-014`.

## A41-REQ-001 — Repository audit
- **Description:** Audit baseline; document first-vendor status.
- **Rationale:** Evidence-based scope.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** initial-audit.md exists.
- **Verification:** Review. **Status:** VERIFIED.

## A41-REQ-002 — Candidate assessment
- **Description:** Evidence-based candidate matrix for 9
  manufacturers.
- **Rationale:** Choose evidence over breadth.
- **Dependencies:** A41-REQ-001. **Priority:** Must.
- **Acceptance:** vendor-candidate-matrix.md exists.
- **Verification:** Review. **Status:** VERIFIED.

## A41-REQ-003 — No invented integrations
- **Description:** No vendor adapter implemented without evidence.
- **Rationale:** Stop condition.
- **Dependencies:** A41-REQ-002. **Priority:** Must.
- **Acceptance:** No new real adapter.
- **Verification:** Review. **Status:** VERIFIED.

## A41-REQ-004 — Evidence records
- **Description:** `VendorEvidence` + `VendorEvidenceAssessor`.
- **Rationale:** Claims tied to evidence.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Evidence tests pass.
- **Verification:** VendorEvidenceTest. **Status:** VERIFIED.

## A41-REQ-005 — Contract test harness
- **Description:** Reusable `VendorAdapterContractTest` harness;
  applied to the null adapter.
- **Rationale:** Every future integration gets contract tests.
- **Dependencies:** A41-REQ-004. **Priority:** Must.
- **Acceptance:** Harness tests pass.
- **Verification:** NullVendorAdapterContractTest. **Status:** VERIFIED.

## A41-REQ-006 — Evidence register
- **Description:** vendor-evidence-register.md documents sources.
- **Rationale:** Provenance.
- **Dependencies:** A41-REQ-002/004. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** VERIFIED.

## A41-REQ-007 — Supported device matrix
- **Description:** supported-device-matrix.md; honest empty set.
- **Rationale:** No false claims.
- **Dependencies:** A41-REQ-002. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** VERIFIED.

## A41-REQ-008 — Supported feature matrix
- **Description:** supported-feature-matrix.md; honest empty set.
- **Rationale:** No false claims.
- **Dependencies:** A41-REQ-002. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** VERIFIED.

## A41-REQ-009 — Protocol inventory
- **Description:** protocol-implementation-inventory.md.
- **Rationale:** Explicit inventory.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** VERIFIED.

## A41-REQ-010 — Known limitations
- **Description:** known-limitations.md.
- **Rationale:** Documented limits.
- **Dependencies:** all. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** VERIFIED.

## A41-REQ-011 — Onboarding update
- **Description:** future-vendor-onboarding.md.
- **Rationale:** Repeatable workflow.
- **Dependencies:** A41-REQ-004/005. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** VERIFIED.

## A41-REQ-012 — No physical interaction
- **Description:** No hardware connection or commands.
- **Rationale:** Phase boundary.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** No Bluetooth usage added.
- **Verification:** Inspection. **Status:** VERIFIED.

## A41-REQ-013 — Documentation
- **Description:** All 16 documents exist and agree with
  implementation.
- **Rationale:** Traceability.
- **Dependencies:** all. **Priority:** Must.
- **Acceptance:** Docs complete.
- **Verification:** Review. **Status:** IN_PROGRESS.

## A41-REQ-014 — Regression
- **Description:** All existing tests pass.
- **Rationale:** No regressions.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Full suite green.
- **Verification:** Full run. **Status:** IN_PROGRESS.
