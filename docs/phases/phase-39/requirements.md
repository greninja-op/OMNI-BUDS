# Phase 39 — Requirements

**ID scheme:** `A39-REQ-001` … `A39-REQ-014`.

## A39-REQ-001 — Vendor audit
- **Description:** Audit the first-vendor implementation and report
  its evidence status.
- **Rationale:** No invented vendor support.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** repository-audit.md records the blocker.
- **Verification:** Review. **Status:** VERIFIED.

## A39-REQ-002 — No arbitrary vendor
- **Description:** Do not implement an adapter for an arbitrary
  vendor when the target is not identifiable.
- **Rationale:** Stop condition.
- **Dependencies:** A39-REQ-001. **Priority:** Must.
- **Acceptance:** No new vendor adapter in this phase.
- **Verification:** Review. **Status:** VERIFIED.

## A39-REQ-003 — Matching hardening
- **Description:** Deterministic tests for ambiguity, conflicts,
  missing metadata, name-only evidence.
- **Rationale:** Safe identity semantics.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Hardening tests pass.
- **Verification:** VendorMatchingHardeningTest. **Status:** VERIFIED.

## A39-REQ-004 — Readiness matrix
- **Description:** production-readiness-matrix.md with
  evidence-backed statuses.
- **Rationale:** Honest readiness.
- **Dependencies:** A39-REQ-001. **Priority:** Must.
- **Acceptance:** Document exists; device items BLOCKED.
- **Verification:** Review. **Status:** VERIFIED.

## A39-REQ-005 — Compatibility matrix
- **Description:** device-compatibility-matrix.md; no unsupported
  claims.
- **Rationale:** No false compatibility.
- **Dependencies:** A39-REQ-001. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** VERIFIED.

## A39-REQ-006 — Operation inventory
- **Description:** protocol-operation-inventory.md documents the
  empty operation set and future requirements.
- **Rationale:** Explicit, not assumed.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** VERIFIED.

## A39-REQ-007 — Failure/recovery matrix
- **Description:** failure-recovery-matrix.md maps failures to
  policies.
- **Rationale:** Typed failure handling.
- **Dependencies:** Phase 34. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** VERIFIED.

## A39-REQ-008 — Performance assessment
- **Description:** performance-assessment.md; no invented numbers.
- **Rationale:** Honest performance claims.
- **Dependencies:** none. **Priority:** Should.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** VERIFIED.

## A39-REQ-009 — Deferred verification
- **Description:** deferred-hardware-verification.md defines Phase
  52 requirements.
- **Rationale:** Hardware stays deferred.
- **Dependencies:** Phase 38. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** VERIFIED.

## A39-REQ-010 — No physical interaction
- **Description:** No phone/earbud connection, pairing, or commands.
- **Rationale:** Phase boundary.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** No Bluetooth API usage added.
- **Verification:** Inspection. **Status:** VERIFIED.

## A39-REQ-011 — Authorization integrity
- **Description:** No vendor entry point bypasses DeviceAccessPolicy.
- **Rationale:** Centralized authorization.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** No new entry points added.
- **Verification:** Review. **Status:** VERIFIED.

## A39-REQ-012 — Documentation
- **Description:** All 14 documents exist and agree with
  implementation.
- **Rationale:** Traceability.
- **Dependencies:** all. **Priority:** Must.
- **Acceptance:** Docs complete.
- **Verification:** Review. **Status:** IN_PROGRESS.

## A39-REQ-013 — Regression
- **Description:** All existing tests pass.
- **Rationale:** No regressions.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Full suite green.
- **Verification:** Full run. **Status:** IN_PROGRESS.

## A39-REQ-014 — Truthful statuses
- **Description:** No device capability marked PASS without
  hardware evidence.
- **Rationale:** Evidence integrity.
- **Dependencies:** A39-REQ-004. **Priority:** Must.
- **Acceptance:** Matrix review.
- **Verification:** Review. **Status:** VERIFIED.
