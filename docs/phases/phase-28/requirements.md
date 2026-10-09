# Phase 28 — Background Device Lifecycle: Requirements

**Status:** Authoritative for Phase 28 execution.
**Scope:** Lifecycle orchestration, process recovery, bounded reconnection,
resource cleanup. No new vendor protocols, no UI, no permanent services.
**Requirement ID scheme:** `OB-P28-REQ-001` … `OB-P28-REQ-028`.

## OB-P28-REQ-001 — Lifecycle architecture
- **Description:** `BackgroundLifecycleCoordinator`, `ProcessRecoveryPlanner`,
  `ReconnectPolicy`, `ResourceLifecycleRegistry`, `LifecycleStateReconciler`,
  Android `OmniBudsLifecycleMonitor`.
- **Priority:** Must | **Verification:** Review + tests.

## OB-P28-REQ-002 — Explicit ownership
- **Description:** One authoritative owner per connection/session; explicit
  coroutine scope ownership; cancellation on session end.
- **Priority:** Must | **Verification:** `LifecycleOwnershipTest`.

## OB-P28-REQ-003 — Lifecycle state machine
- **Description:** Documented transitions: UNKNOWN → FOREGROUND ↔ BACKGROUND;
  device states reconciled separately. Invalid transitions rejected.
- **Priority:** Must | **Verification:** `LifecycleStateMachineTest`.

## OB-P28-REQ-004 — Process recovery
- **Description:** On process start: invalidate unprovable sessions, mark
  observations by age/provenance, reconcile with platform state, never
  restore CONNECTED from a persisted flag alone.
- **Priority:** Must | **Verification:** `ProcessRecoveryTest`.

## OB-P28-REQ-005 — Interrupted operations
- **Description:** Pending-at-death operations marked interrupted; never
  blindly replayed.
- **Priority:** Must | **Verification:** `ProcessRecoveryTest`.

## OB-P28-REQ-006 — Bounded reconnection
- **Description:** Eligible triggers, max attempts, backoff, cancellation;
  no endless loops; no background scanning.
- **Priority:** Must | **Verification:** `ReconnectPolicyTest`.

## OB-P28-REQ-007 — Permission revocation
- **Description:** Revocation stops unauthorized work; sessions invalidated;
  state updated honestly.
- **Priority:** Must | **Verification:** `LifecyclePermissionTest`.

## OB-P28-REQ-008 — Adapter disabled
- **Description:** Adapter-off cancels/suspends affected operations safely.
- **Priority:** Must | **Verification:** `LifecyclePermissionTest`.

## OB-P28-REQ-009 — Resource cleanup
- **Description:** Registry tracks callbacks, collectors, jobs, transports;
  explicit release; no leaks.
- **Priority:** Must | **Verification:** `ResourceLifecycleTest`.

## OB-P28-REQ-010 — No permanent service
- **Description:** No foreground service by default; eligibility analysis
  documented; none justified in this phase.
- **Priority:** Must | **Verification:** `service-eligibility.md`.

## OB-P28-REQ-011 — Provenance preservation
- **Description:** Recovery preserves source, timestamp, freshness, session
  identity, ordering. Stale never overwrites newer.
- **Priority:** Must | **Verification:** `LifecycleReconciliationTest`.

## OB-P28-REQ-012 — Presentation consistency
- **Description:** Quick Settings, notifications, widgets consume shared
  state; safe after process recreation.
- **Priority:** Must | **Verification:** Review.

## OB-P28-REQ-013 — No UI dependency
- **Description:** Lifecycle never depends on UI components being alive.
- **Priority:** Must | **Verification:** Review.

## OB-P28-REQ-014 — Duplicate/stale events
- **Description:** Duplicate recovery triggers coalesced; stale events
  rejected.
- **Priority:** Must | **Verification:** `LifecycleEventTest`.

## OB-P28-REQ-015 — Battery safeguards
- **Description:** No polling, no aggressive reconnect, no unnecessary
  transport sessions. Budget documented.
- **Priority:** Must | **Verification:** `battery-and-resource-budget.md`.

## OB-P28-REQ-016 — Android integration
- **Description:** Activity lifecycle callbacks + trim-memory; API-level
  background rules documented.
- **Priority:** Must | **Verification:** `android-background-execution.md`.

## OB-P28-REQ-017 — Error handling
- **Description:** Typed handling for all §16 cases; privacy-safe diagnostics.
- **Priority:** Must | **Verification:** `LifecycleErrorTest`.

## OB-P28-REQ-018 — No scope creep
- **Description:** No vendor protocols, UI, or desktop lifecycle.
- **Priority:** Must | **Verification:** Scope review.

## OB-P28-REQ-019 — Deterministic tests
- **Description:** No hardware required; fake clocks/dispatchers.
- **Priority:** Must | **Verification:** Review.

## OB-P28-REQ-020 — Multi-device recovery
- **Description:** Independent session identity and ownership per device.
- **Priority:** Must | **Verification:** `ProcessRecoveryTest`.

## OB-P28-REQ-021 — Session invalidation
- **Description:** Sessions that cannot be proven valid are invalidated,
  never assumed.
- **Priority:** Must | **Verification:** `ProcessRecoveryTest`.

## OB-P28-REQ-022 — Capability rediscovery
- **Description:** Rediscovery only when required, not unconditionally.
- **Priority:** Must | **Verification:** `ProcessRecoveryTest`.

## OB-P28-REQ-023 — Documentation
- **Description:** 13 required documents.
- **Priority:** Must | **Verification:** Review.

## OB-P28-REQ-024 — Regression
- **Description:** All existing tests pass.
- **Priority:** Must | **Verification:** Full run.

## OB-P28-REQ-025 — No fabricated state
- **Description:** Recovery never manufactures confirmed state from history.
- **Priority:** Must | **Verification:** `ProcessRecoveryTest`.

## OB-P28-REQ-026 — Cancellation propagation
- **Description:** Session end cancels owned work; no orphaned jobs.
- **Priority:** Must | **Verification:** `ResourceLifecycleTest`.

## OB-P28-REQ-027 — Background restrictions
- **Description:** Background-restricted apps defer work; no evasion.
- **Priority:** Must | **Verification:** `android-background-execution.md`.

## OB-P28-REQ-028 — Local-first
- **Description:** No telemetry, no upload.
- **Priority:** Must | **Verification:** Scope review.
