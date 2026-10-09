# Phase 35 — Requirements

**ID scheme:** `A35-REQ-001` … `A35-REQ-016`.

## A35-REQ-001 — Threat model
- **Description:** STRIDE-grounded threat model linked to real code
  paths, with attacker capability, entry point, impact, controls,
  mitigation, residual risk.
- **Rationale:** Security work must be grounded, not generic.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** threat-model.md exists with linked components.
- **Verification:** Review. **Status:** VERIFIED.

## A35-REQ-002 — Attack-surface inventory
- **Description:** attack-surface-inventory.md: components, trust
  boundaries, exposure.
- **Rationale:** Know what is exposed.
- **Dependencies:** A35-REQ-001. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** VERIFIED.

## A35-REQ-003 — Input validation
- **Description:** `InputValidator` — bounded message/collection/
  nesting/import sizes, overflow-safe arithmetic, frame checks.
- **Rationale:** Untrusted input must not exhaust resources or crash.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Boundary tests pass.
- **Verification:** InputValidatorTest. **Status:** VERIFIED.

## A35-REQ-004 — Unknown-device read-only
- **Description:** Preserve default-deny; unknown devices cannot write.
- **Rationale:** Non-negotiable principle.
- **Dependencies:** existing DeviceAccessPolicy. **Priority:** Must.
- **Acceptance:** Policy unchanged; invariant pinned by test.
- **Verification:** DeviceIsolationSecurityTest. **Status:** VERIFIED.

## A35-REQ-005 — Authorization cannot be bypassed
- **Description:** Recovery, notifications, widgets, tiles cannot
  bypass authorization (existing gates verified).
- **Rationale:** One authorization model.
- **Dependencies:** Phase 34 policy. **Priority:** Must.
- **Acceptance:** Gates reviewed; no new write paths.
- **Verification:** Review. **Status:** VERIFIED.

## A35-REQ-006 — Log redaction
- **Description:** `LogRedactor` — pure redaction for MACs, tokens;
  never throws; complements sink-side redaction.
- **Rationale:** No secrets in diagnostics.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Redaction tests pass.
- **Verification:** LogRedactorTest. **Status:** VERIFIED.

## A35-REQ-007 — Component exposure
- **Description:** Manifest reviewed: tile exported=true with
  BIND_QUICK_SETTINGS_TILE (required); receiver/provider
  exported=false; PendingIntents immutable.
- **Rationale:** Narrowest appropriate exposure.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Review recorded; no changes needed.
- **Verification:** Review. **Status:** VERIFIED.

## A35-REQ-008 — Cross-device isolation
- **Description:** Regression tests: device A cannot act as device B;
  sessions bound by explicit identifiers.
- **Rationale:** Isolation is a security property.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Tests pass.
- **Verification:** DeviceIsolationSecurityTest. **Status:** VERIFIED.

## A35-REQ-009 — Privacy inventory
- **Description:** privacy-data-inventory.md: data categories, purpose,
  retention, transmission (none).
- **Rationale:** Data minimization.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** VERIFIED.

## A35-REQ-010 — No new permissions/data
- **Description:** No new permissions, no analytics, no network
  transmission, no audio capture introduced.
- **Rationale:** Least privilege.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Manifest and deps unchanged.
- **Verification:** Review. **Status:** VERIFIED.

## A35-REQ-011 — Resource bounds
- **Description:** Justified limits: 64 KiB messages, 1024
  collections, 16 nesting, 16 MiB imports, 256 diagnostic events.
- **Rationale:** Bounded resource use.
- **Dependencies:** A35-REQ-003. **Priority:** Must.
- **Acceptance:** Limits documented and tested.
- **Verification:** InputValidatorTest. **Status:** VERIFIED.

## A35-REQ-012 — Dependency review
- **Description:** Dependencies reviewed: kotlinx-coroutines,
  kotlin-test, JUnit — no new deps; no secrets in config.
- **Rationale:** Supply-chain hygiene.
- **Dependencies:** none. **Priority:** Should.
- **Acceptance:** Review recorded.
- **Verification:** Review. **Status:** VERIFIED.

## A35-REQ-013 — Security control matrix
- **Description:** security-control-matrix.md mapping threats to
  controls and tests.
- **Rationale:** Traceability.
- **Dependencies:** A35-REQ-001. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** VERIFIED.

## A35-REQ-014 — Security test matrix
- **Description:** security-test-matrix.md: test IDs, threat IDs,
  fixtures, expected behavior, evidence.
- **Rationale:** Evidence.
- **Dependencies:** all tests. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** IN_PROGRESS.

## A35-REQ-015 — Documentation
- **Description:** All 14 documents exist and agree with implementation.
- **Rationale:** Traceability.
- **Dependencies:** all. **Priority:** Must.
- **Acceptance:** Docs complete.
- **Verification:** Review. **Status:** IN_PROGRESS.

## A35-REQ-016 — Regression
- **Description:** All existing tests pass.
- **Rationale:** No regressions.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Full suite green.
- **Verification:** Full run. **Status:** IN_PROGRESS.
