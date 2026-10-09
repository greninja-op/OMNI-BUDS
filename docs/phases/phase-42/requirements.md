# Phase 42 — Requirements

**ID scheme:** `A42-REQ-001` … `A42-REQ-014`.

## A41 reuse note
Phase 41's `VendorAdapterContractTest` harness and
`VendorEvidence` are reused here.

## A42-REQ-001 — Repository audit
- **Description:** Audit baseline; confirm no Apple code.
- **Priority:** Must. **Status:** VERIFIED.

## A42-REQ-002 — Evidence-driven research
- **Description:** Research register with legitimate sources.
- **Priority:** Must. **Status:** VERIFIED.

## A42-REQ-003 — Conservative adapter
- **Description:** `AppleAirpodsAdapter`: family matching,
  read-only scope, no AAP.
- **Priority:** Must. **Status:** VERIFIED (tests).

## A42-REQ-004 — No access-control bypass
- **Description:** No device-ID spoofing, no root-dependent
  protocol.
- **Priority:** Must. **Status:** VERIFIED (review).

## A42-REQ-005 — Capability matrix
- **Description:** airpods-capability-matrix.md with evidence.
- **Priority:** Must. **Status:** VERIFIED.

## A42-REQ-006 — Unsupported features declared
- **Description:** 14 unsupported features with reasons.
- **Priority:** Must. **Status:** VERIFIED.

## A42-REQ-007 — Battery honesty
- **Description:** Unknown battery stays null; no attribution
  without provenance.
- **Priority:** Must. **Status:** VERIFIED (tests).

## A42-REQ-008 — No invented protocol
- **Description:** No AAP packet formats invented.
- **Priority:** Must. **Status:** VERIFIED (review).

## A42-REQ-009 — Identity safety
- **Description:** Company-ID-only → Ambiguous; non-Apple →
  NotMatched; no name-based identity.
- **Priority:** Must. **Status:** VERIFIED (tests).

## A42-REQ-010 — Central authorization
- **Description:** No write operations exist; adapter cannot
  bypass authorization.
- **Priority:** Must. **Status:** VERIFIED.

## A42-REQ-011 — Contract harness
- **Description:** `AirpodsContractTest` subclasses the Phase 41
  harness.
- **Priority:** Must. **Status:** VERIFIED.

## A42-REQ-012 — Deferred hardware plan
- **Description:** deferred-hardware-verification.md.
- **Priority:** Must. **Status:** VERIFIED.

## A42-REQ-013 — No physical interaction
- **Description:** No hardware connected.
- **Priority:** Must. **Status:** VERIFIED.

## A42-REQ-014 — Regression
- **Description:** Full suite green.
- **Priority:** Must. **Status:** IN_PROGRESS.
