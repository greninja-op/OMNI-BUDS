# Phase 34 — Repository Audit

**Date:** 2026-10-09

## Existing failure/recovery mechanisms

| Mechanism | Location | Notes |
|---|---|---|
| `RetryClass` | `core/common/RetryClass.kt` | SAFE_TO_RETRY / RETRY_AFTER_REREAD / NEVER_RETRY — derived from error category, never chosen at call site |
| `ReconnectPolicy` | `core/lifecycle/ReconnectPolicy.kt` | Bounded (3 attempts), exponential backoff, denies on bluetooth-off/permission-lost/unpaired |
| `ProcessRecoveryPlanner` | `core/lifecycle/ProcessRecoveryPlanner.kt` | Invalidates unprovable sessions after process death; marks interrupted ops |
| `FailureInjector` | `core/testkit/FailureInjector.kt` | Seeded deterministic failure injection (Phase 30) |
| `ScriptedTransport` | `core/testkit/ScriptedTransport.kt` | Fail-closed scripted transport |
| Device sessions | `core/session/` | TrackedDeviceSession, lifecycle events |
| Compat policies | `platform/android/compat/` | Permission/Bluetooth decisions (Phase 32) |

## Gaps

1. No centralized typed failure classification (categories scattered).
2. No deterministic recovery-policy engine mapping failures → decisions.
3. No explicit recovery state machine document.
4. No ambiguous-write (may-have-executed) handling contract.
5. No bounded diagnostic event model for recovery.

## Implementation strategy

New `core/recovery/` package:

- `FailureClassification.kt` — typed categories + metadata, mapping
  from existing `RetryClass`/error categories.
- `RecoveryPolicy.kt` — deterministic failure + context → decision.
- `RecoveryStateMachine.kt` — explicit recovery lifecycle.
- `RecoveryEvents.kt` — bounded, privacy-conscious diagnostic events.

Reuse `RetryClass`, `ReconnectPolicy`, `FailureInjector`. No duplicate
retry loops; no competing state machines.
