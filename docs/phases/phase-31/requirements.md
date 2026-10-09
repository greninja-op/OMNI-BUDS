# Phase 31 — Cross-Device Testing: Requirements

**Status:** Authoritative for Phase 31 execution.
**Scope:** Device profiles, campaign planning, compatibility evaluation,
regression comparison, unsupported-device reporting. Builds on Phase 30;
no hardware campaigns.
**Requirement ID scheme:** `OB-P31-REQ-001` … `OB-P31-REQ-020`.

## OB-P31-REQ-001 — Versioned profile schema
- **Description:** Device profiles carry schema version, stable ID,
  manufacturer/model/firmware (nullable unknowns), transport/protocol,
  capability declarations, provenance, limitations.
- **Priority:** Must | **Verification:** `CrossDeviceTests`.

## OB-P31-REQ-002 — Profile validation
- **Description:** Invalid schemas rejected; unknown values preserved,
  never guessed.
- **Priority:** Must | **Verification:** `CrossDeviceTests`.

## OB-P31-REQ-003 — Profile categories
- **Description:** Synthetic / fixture-derived / lab-tested /
  hardware-verified / persistence-verified. Synthetic never auto-promoted.
- **Priority:** Must | **Verification:** `CrossDeviceTests`.

## OB-P31-REQ-004 — Campaign planning
- **Description:** Deterministic planner: load validated profiles,
  resolve suites, filter by capability prerequisites, distinguish
  not-applicable/skipped/blocked/failed/passed.
- **Priority:** Must | **Verification:** `CrossDeviceTests`.

## OB-P31-REQ-005 — Compatibility classification
- **Description:** Verified-compatible / synthetic-only / partial /
  incompatible / unknown / blocked / not-applicable — with scope,
  suites, results, evidence, limitations.
- **Priority:** Must | **Verification:** `CrossDeviceTests`.

## OB-P31-REQ-006 — Scope discipline
- **Description:** Claims scoped to exact model/firmware/protocol tested;
  no vendor-wide or firmware-wide inference.
- **Priority:** Must | **Verification:** `CrossDeviceTests`.

## OB-P31-REQ-007 — No evidence promotion
- **Description:** Synthetic passes never become hardware verification.
- **Priority:** Must | **Verification:** `CrossDeviceTests`.

## OB-P31-REQ-008 — Regression comparison
- **Description:** Compare campaign vs valid baseline: detect newly
  failing tests, behavior changes, new skips/blocks. Baselines never
  auto-updated.
- **Priority:** Must | **Verification:** `CrossDeviceTests`.

## OB-P31-REQ-009 — Baseline validation
- **Description:** Baselines carry source revision, profile/fixture/
  runner versions; invalid baselines fail loudly.
- **Priority:** Must | **Verification:** `CrossDeviceTests`.

## OB-P31-REQ-010 — Profile isolation
- **Description:** No cross-profile state leakage; ambiguous identities
  rejected; one profile cannot inherit another's features.
- **Priority:** Must | **Verification:** `CrossDeviceTests`.

## OB-P31-REQ-011 — Determinism
- **Description:** Identical inputs → equivalent classifications;
  profile order independent.
- **Priority:** Must | **Verification:** `CrossDeviceTests`.

## OB-P31-REQ-012 — Unsupported-device reporting
- **Description:** Unsupported profiles reported with reasons; never
  silently treated as compatible.
- **Priority:** Must | **Verification:** `CrossDeviceTests`.

## OB-P31-REQ-013 — Missing infrastructure honesty
- **Description:** Infrastructure failures distinguished from product
  failures; missing fixtures block explicitly.
- **Priority:** Must | **Verification:** `CrossDeviceTests`.

## OB-P31-REQ-014 — Firmware scoping
- **Description:** Unknown firmware → no assumptions; out-of-scope
  firmware → incompatible/blocked per evidence.
- **Priority:** Must | **Verification:** `CrossDeviceTests`.

## OB-P31-REQ-015 — Protocol variation
- **Description:** Protocol-version-aware selection; mismatch →
  incompatible or blocked per evidence.
- **Priority:** Must | **Verification:** `CrossDeviceTests`.

## OB-P31-REQ-016 — Evidence aggregation
- **Description:** Reuse Phase 30 evidence; preserve simulation vs
  lab vs hardware distinctions; contradictory evidence recorded.
- **Priority:** Must | **Verification:** `CrossDeviceTests`.

## OB-P31-REQ-017 — No fabricated profiles
- **Description:** No invented supported devices; synthetic assumptions
  clearly marked.
- **Priority:** Must | **Verification:** Review.

## OB-P31-REQ-018 — No hardware required
- **Description:** Ordinary campaigns run without physical hardware.
- **Priority:** Must | **Verification:** Review.

## OB-P31-REQ-019 — Documentation
- **Description:** 13 required documents.
- **Priority:** Must | **Verification:** Review.

## OB-P31-REQ-020 — Regression
- **Description:** All existing tests pass.
- **Priority:** Must | **Verification:** Full run.
