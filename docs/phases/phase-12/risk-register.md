# Phase 12 — Risk Register

**Status:** Authoritative.

---

## RISK-P12-001 — Callers misread NOT_SELECTABLE as "broken"

**Description:** A future UI might present `NOT_SELECTABLE` as an app failure
rather than a platform limitation.
**Likelihood:** Medium | **Impact:** Medium
**Mitigation:** Evidence strings name the limitation explicitly; docs
(`platform-limitations.md`) are written for UI authors. No UI in this phase.

## RISK-P12-002 — A future Android API changes the matrix

**Description:** Android may one day ship public codec control; the hardcoded
`false` values would then be wrong.
**Likelihood:** Low | **Impact:** Medium
**Mitigation:** The resolver is an interface; the Android implementation reads
`apiLevel`. A new API level gates a new code path — the engine is unchanged.

## RISK-P12-003 — Vendor protocol arrives with partial codec commands

**Description:** A future verified protocol may support selection but not
configuration (or vice versa); the five dimensions must stay independent.
**Likelihood:** Medium | **Impact:** Low
**Mitigation:** Dimensions are already independent booleans; the adapter
interface supports partial mechanisms.

## RISK-P12-004 — Rollback re-select storms a flaky device

**Description:** Repeated verification failures could trigger repeated
rollback re-selects.
**Likelihood:** Low | **Impact:** Low
**Mitigation:** Rollback runs once per failed transaction (`isRollback`
guard); no automatic retry of the original operation.

## RISK-P12-005 — Timeout values unsuitable for real hardware

**Description:** The 10s default was chosen without hardware timing data.
**Likelihood:** Medium | **Impact:** Low
**Mitigation:** Per-operation override exists; values are documented as
unverified estimates pending hardware testing (deferred).

## RISK-P12-006 — `previousConfirmed` history unbounded

**Description:** Only one generation of history is kept; deeper undo is
impossible.
**Likelihood:** Low | **Impact:** Low
**Mitigation:** Accepted: the engine is a control plane, not a history
service. Documented in design.

## RISK-P12-007 — Physical-device behavior differs from fakes

**Description:** All control paths are tested with fakes; real platform
behavior is unverified.
**Likelihood:** High | **Impact:** Medium
**Mitigation:** Explicitly deferred per the phase boundary; no fake hardware
behavior exists in production code; verification levels never auto-promote.
