# Phase 32 — Requirements

**ID scheme:** `A32-REQ-001` … `A32-REQ-016`.

## A32-REQ-001 — SDK configuration documented
- **Description:** minSdk 26, targetSdk 35, compileSdk 35 recorded.
- **Rationale:** Compatibility claims need a baseline.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** repository-audit.md records actual values.
- **Verification:** Review. **Status:** VERIFIED.

## A32-REQ-002 — API-level matrix
- **Description:** Evidence-based matrix of relevant API boundaries
  (26–35): Bluetooth permissions (31), notification permission (33),
  foreground-service types (29/34), background restrictions.
- **Rationale:** Risk-based coverage of real boundaries.
- **Dependencies:** A32-REQ-001. **Priority:** Must.
- **Acceptance:** android-version-matrix.md exists with tested/gap marks.
- **Verification:** Review. **Status:** VERIFIED.

## A32-REQ-003 — Pure API-level decisions
- **Description:** `ApiLevelPolicy` — JVM-testable boundary logic.
- **Rationale:** Decisions must not depend on assumptions.
- **Dependencies:** A32-REQ-002. **Priority:** Must.
- **Acceptance:** All boundaries unit-tested.
- **Verification:** ApiLevelPolicyTest. **Status:** VERIFIED.

## A32-REQ-004 — Permission matrix
- **Description:** permission-matrix.md documents version-specific
  requirements for declared permissions.
- **Rationale:** Only BLUETOOTH_CONNECT is declared; document why.
- **Dependencies:** A32-REQ-001. **Priority:** Must.
- **Acceptance:** Matrix exists and matches the manifest.
- **Verification:** Review. **Status:** VERIFIED.

## A32-REQ-005 — Permission-state model
- **Description:** GRANTED/DENIED/UNAVAILABLE/RESTRICTED/UNKNOWN with
  pure `PermissionPolicy` decisions.
- **Rationale:** Denial ≠ unavailability; unknown must defer.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** All five states tested; no crash paths.
- **Verification:** PermissionPolicyTest. **Status:** VERIFIED.

## A32-REQ-006 — Bluetooth platform decisions
- **Description:** `BluetoothPlatformPolicy` — adapter
  present/enabled + permission → MayOperate/CannotOperate/Degraded.
- **Rationale:** Platform limits reported explicitly, never as device
  capabilities.
- **Dependencies:** A32-REQ-005. **Priority:** Must.
- **Acceptance:** All scenarios tested.
- **Verification:** BluetoothPlatformPolicyTest. **Status:** VERIFIED.

## A32-REQ-007 — Tile audit
- **Description:** Phase 25 tile verified: manifest, API guards,
  permission-missing and adapter-disabled behavior.
- **Rationale:** No redesign; verify existing behavior.
- **Dependencies:** A32-REQ-005. **Priority:** Must.
- **Acceptance:** Audit recorded; no new issues found.
- **Verification:** Review + existing tile tests. **Status:** VERIFIED.

## A32-REQ-008 — Notification audit
- **Description:** Phase 26 notifications verified: channels (26+),
  runtime permission (33+), denied-permission safety.
- **Rationale:** Denied permission must not crash or misreport.
- **Dependencies:** A32-REQ-005. **Priority:** Must.
- **Acceptance:** Audit recorded; NotificationPermissionChecker exists.
- **Verification:** Review. **Status:** VERIFIED.

## A32-REQ-009 — Widget audit
- **Description:** Phase 27 widget verified: provider metadata,
  unknown-battery rendering, instance isolation, background limits.
- **Rationale:** No aggressive polling; honest stale state.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Audit recorded.
- **Verification:** Review + existing widget tests. **Status:** VERIFIED.

## A32-REQ-010 — Background execution audit
- **Description:** Phase 28 lifecycle verified against SDK rules; no
  foreground service; bounded reconnect.
- **Rationale:** Compliance without an unnecessary service.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Audit recorded.
- **Verification:** Review. **Status:** VERIFIED.

## A32-REQ-011 — Manifest checks
- **Description:** Automated checks: required declarations, exported
  components, justified permissions, no debug leakage.
- **Rationale:** Manifest errors ship silently.
- **Dependencies:** A32-REQ-001. **Priority:** Should.
- **Acceptance:** Manifest reviewed against targetSdk 35 rules.
- **Verification:** Review. **Status:** VERIFIED.

## A32-REQ-012 — OEM notes
- **Description:** oem-compatibility-notes.md; UNVERIFIED where no
  evidence exists.
- **Rationale:** OEM variation is real but unmeasured here.
- **Dependencies:** none. **Priority:** Should.
- **Acceptance:** Document exists with evidence labels.
- **Verification:** Review. **Status:** VERIFIED.

## A32-REQ-013 — Test strategy
- **Description:** platform-test-strategy.md mapping layers A–D and
  evidence classifications.
- **Rationale:** Honest about what JVM tests prove.
- **Dependencies:** Phase 30/31 docs. **Priority:** Must.
- **Acceptance:** Document exists.
- **Verification:** Review. **Status:** VERIFIED.

## A32-REQ-014 — No fake capabilities
- **Description:** No permission bypasses, no false CONNECTED states,
  no unnecessary permissions introduced.
- **Rationale:** Core product principle.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Code review of new files.
- **Verification:** Review. **Status:** VERIFIED.

## A32-REQ-015 — Documentation
- **Description:** All 13 documents exist and agree with implementation.
- **Rationale:** Traceability.
- **Dependencies:** all. **Priority:** Must.
- **Acceptance:** Docs complete.
- **Verification:** Review. **Status:** IN_PROGRESS.

## A32-REQ-016 — Regression
- **Description:** All existing tests pass.
- **Rationale:** No regressions.
- **Dependencies:** none. **Priority:** Must.
- **Acceptance:** Full suite green.
- **Verification:** Full run. **Status:** IN_PROGRESS.
