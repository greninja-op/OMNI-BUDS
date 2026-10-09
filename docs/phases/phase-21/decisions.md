# Phase 21 — Decisions

## D-21-01: separate facts, not a single state
**Decision:** classification, identity confidence, protocol verification,
and write authorization are separate fields.
**Rationale:** Merging them invites exactly the confusion this phase
exists to prevent (unknown ≠ unsupported ≠ unauthorized).

## D-21-02: policy at the control boundary
**Decision:** `DeviceAccessPolicy.evaluate` is a pure function called by
control code, not a UI concern.
**Rationale:** Hiding buttons is not a security boundary.

## D-21-03: ambiguity sticky
**Decision:** Once ambiguous, re-classification preserves ambiguity until
explicit resolution.
**Rationale:** New evidence arriving after a conflict does not erase the
conflict.

## D-21-04: stale = unknown
**Decision:** Stale evidence downgrades to UNKNOWN_DEVICE rather than
keeping the old classification.
**Rationale:** Reusing stale evidence as current is a known failure mode.

## D-21-05: hard denials
**Decision:** Raw writes, firmware updates, config resets are denied for
all classifications.
**Rationale:** No device state justifies these through this policy.
