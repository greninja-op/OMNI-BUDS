# Phase 19 — First Vendor Device: Requirements

**Status:** Authoritative for Phase 19 execution.
**Scope:** Vendor-adapter infrastructure + target selection. NO vendor protocol
implemented — insufficient evidence (see target-selection.md).
**Requirement ID scheme:** `OB-P19-REQ-001` … `OB-P19-REQ-020`.

## OB-P19-REQ-001 — Target selection
- **Description:** Evaluate candidates; document selection/rejection.
- **Priority:** Must | **Status:** Complete — no candidate selected (blocked).
- **Verification:** `target-selection.md`.

## OB-P19-REQ-002 — Vendor adapter contract
- **Description:** `VendorAdapter` interface: match, protocol, firmware.
- **Priority:** Must | **Status:** Complete.
- **Verification:** `VendorRegistryTest`.

## OB-P19-REQ-003 — Deterministic matching
- **Description:** Exact/ambiguous/unknown matching; ambiguous blocks writes.
- **Priority:** Must | **Status:** Complete.
- **Verification:** `VendorRegistryTest`.

## OB-P19-REQ-004 — Registry
- **Description:** `VendorRegistry`: register, resolve, ambiguity detection.
- **Priority:** Must | **Status:** Complete.
- **Verification:** `VendorRegistryTest`.

## OB-P19-REQ-005 — Unknown-device fallback
- **Description:** No match → existing unknown-device path, unchanged.
- **Priority:** Must | **Status:** Complete (no behavior change).
- **Verification:** Existing tests.

## OB-P19-REQ-006 — No fabricated protocols
- **Description:** No vendor commands implemented without evidence.
- **Priority:** Must | **Status:** Complete — zero vendor commands.
- **Verification:** Scope tests.

## OB-P19-REQ-007 — Evidence ledger
- **Description:** `research.md` with claim IDs, sources, confidence.
- **Priority:** Must | **Status:** Complete.
- **Verification:** Review.

## OB-P19-REQ-008 — Protocol template
- **Description:** `protocol.md` structure for future protocols.
- **Priority:** Must | **Status:** Complete (aspirational, marked).
- **Verification:** Review.

## OB-P19-REQ-009 — Capability honesty
- **Description:** Null adapter declares zero capabilities.
- **Priority:** Must | **Status:** Complete.
- **Verification:** `VendorRegistryTest`.

## OB-P19-REQ-010 — Architecture
- **Description:** `core.vendor` at layer 5; no sideways imports.
- **Priority:** Must | **Status:** Complete.
- **Verification:** Architecture test.

## OB-P19-REQ-011 — Documentation
- **Description:** 11 required documents.
- **Priority:** Must | **Status:** Complete.
- **Verification:** Review.

## OB-P19-REQ-012 — Regression
- **Description:** Phases 7–18 unchanged.
- **Priority:** Must | **Status:** Complete.
- **Verification:** Full test run.

## OB-P19-REQ-013 — No UI / Phase 20
- **Description:** No UI, Phase 20 not started.
- **Priority:** Must | **Status:** Complete.
- **Verification:** Scope tests.

## OB-P19-REQ-014 — Blocked items explicit
- **Description:** All unimplemented items marked with blocker + next action.
- **Priority:** Must | **Status:** Complete.
- **Verification:** Review.

## OB-P19-REQ-015 — Fingerprint tests
- **Description:** Exact/ambiguous/unknown matching tested.
- **Priority:** Must | **Status:** Complete.
- **Verification:** `VendorRegistryTest`.

## OB-P19-REQ-016 — Security
- **Description:** No addresses retained, no payload logging.
- **Priority:** Must | **Status:** Complete.
- **Verification:** Review.

## OB-P19-REQ-017 — Battery integration
- **Description:** Only where evidenced.
- **Priority:** Should | **Status:** Blocked — no evidence.
- **Verification:** N/A.

## OB-P19-REQ-018 — Persistence verification
- **Description:** Use Phase 18 framework.
- **Priority:** Should | **Status:** Blocked — no adapter to verify.
- **Verification:** N/A.

## OB-P19-REQ-019 — Feature control
- **Description:** Only evidenced operations.
- **Priority:** Should | **Status:** Blocked — no evidence.
- **Verification:** N/A.

## OB-P19-REQ-020 — Licensing
- **Description:** Respect protocol licensing.
- **Priority:** Must | **Status:** Complete — nothing copied.
- **Verification:** Review.
