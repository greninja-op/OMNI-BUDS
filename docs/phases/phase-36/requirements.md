# Phase 36 — Requirements

**ID scheme:** `A36-REQ-001` … `A36-REQ-016`.

## A36-REQ-001 — Diagnostics audit
- **Description:** Audit existing logging: DiagnosticEvent model,
  OmniBudsLogger seam, recovery events, redaction controls.
- **Rationale:** Build on what exists.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** repository-audit.md records findings.
- **Verification:** Review. **Status:** VERIFIED.

## A36-REQ-002 — Bounded event store
- **Description:** `DiagnosticStore` — capacity 512, oldest-first
  eviction, dropped/invalid counters, message truncation.
- **Rationale:** Bounded storage; no unbounded accumulation.
- **Dependencies:** existing DiagnosticEvent. **Priority:** Must.
- **Acceptance:** Bounds tested.
- **Verification:** DiagnosticStoreTest. **Status:** VERIFIED.

## A36-REQ-003 — Retention and overflow
- **Description:** Overflow preserves recency; drops counted; no
  recursive logging about drops.
- **Rationale:** Predictable degradation.
- **Dependencies:** A36-REQ-002. **Priority:** Must.
- **Acceptance:** Tests pass.
- **Verification:** DiagnosticStoreTest. **Status:** VERIFIED.

## A36-REQ-004 — Redaction before retention
- **Description:** Export redacts via LogRedactor; store holds
  pre-redaction text by design (existing contract).
- **Rationale:** Privacy.
- **Dependencies:** Phase 35 LogRedactor. **Priority:** Must.
- **Acceptance:** Export redaction test passes.
- **Verification:** DiagnosticExporterTest. **Status:** VERIFIED.

## A36-REQ-005 — Diagnostic health
- **Description:** `DiagnosticHealth`/`DiagnosticHealthTracker` —
  sink availability, saturation, dropped/invalid/persistence counts.
- **Rationale:** Self-observability without recursion.
- **Dependencies:** A36-REQ-002. **Priority:** Must.
- **Acceptance:** Health tests pass.
- **Verification:** DiagnosticHealthTest. **Status:** VERIFIED.

## A36-REQ-006 — Sink failure isolation
- **Description:** Failing sinks cannot break recording or critical
  operations.
- **Rationale:** Diagnostics must not be a failure source.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Isolation tests pass.
- **Verification:** SinkFailureIsolationTest. **Status:** VERIFIED.

## A36-REQ-007 — Safe local export
- **Description:** `DiagnosticExporter` — versioned JSON, 512-event /
  256 KiB caps, sanitized, never automatic, never uploaded.
- **Rationale:** Debuggable without leaking.
- **Dependencies:** A36-REQ-002/004. **Priority:** Must.
- **Acceptance:** Export tests pass.
- **Verification:** DiagnosticExporterTest. **Status:** VERIFIED.

## A36-REQ-008 — Event schema
- **Description:** Reuse DiagnosticEvent; export schema version 1
  documented.
- **Rationale:** No competing schemas.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** diagnostic-event-schema.md exists.
- **Verification:** Review. **Status:** VERIFIED.

## A36-REQ-009 — Redaction policy
- **Description:** redaction-policy.md: what is redacted, where, and
  the known gap (free-text device names).
- **Rationale:** Honest privacy claims.
- **Dependencies:** A36-REQ-004. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** VERIFIED.

## A36-REQ-010 — Retention policy
- **Description:** retention-and-storage-policy.md: limits, overflow,
  cleanup, rationale.
- **Rationale:** Documented behavior.
- **Dependencies:** A36-REQ-002/003. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** VERIFIED.

## A36-REQ-011 — Export specification
- **Description:** diagnostic-export-specification.md: format, caps,
  safety rules.
- **Rationale:** Safe export contract.
- **Dependencies:** A36-REQ-007. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** VERIFIED.

## A36-REQ-012 — No telemetry
- **Description:** No cloud telemetry, analytics, uploads, or audio
  collection introduced.
- **Rationale:** Privacy.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Nothing added.
- **Verification:** Review. **Status:** VERIFIED.

## A36-REQ-013 — Test matrix
- **Description:** diagnostic-test-matrix.md: tests, coverage, evidence.
- **Rationale:** Evidence.
- **Dependencies:** all tests. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** IN_PROGRESS.

## A36-REQ-014 — Phase 30–35 integration
- **Description:** Reuses DiagnosticEvent, LogRedactor, recovery
  events; no competing runner.
- **Rationale:** One diagnostics model.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Code review.
- **Verification:** Review. **Status:** VERIFIED.

## A36-REQ-015 — Documentation
- **Description:** All 14 documents exist and agree with implementation.
- **Rationale:** Traceability.
- **Dependencies:** all. **Priority:** Must.
- **Acceptance:** Docs complete.
- **Verification:** Review. **Status:** IN_PROGRESS.

## A36-REQ-016 — Regression
- **Description:** All existing tests pass.
- **Rationale:** No regressions.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Full suite green.
- **Verification:** Full run. **Status:** IN_PROGRESS.
