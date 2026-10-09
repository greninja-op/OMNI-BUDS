# Phase 35 — Repository Audit

**Date:** 2026-10-09

## Confirmed controls (verified in source)

| Control | Location | Status |
|---|---|---|
| Unknown-device read-only | `core/access/DeviceAccessPolicy.kt` | Implemented; default-deny, 17 typed denial reasons |
| Log redaction at sink | `core/diagnostics/OmniBudsLogger.kt` | Implemented; redaction at emission, not export |
| Tile exported=true | manifest | Required by Android; guarded by BIND_QUICK_SETTINGS_TILE permission — correct |
| Notification receiver exported=false | manifest | Correct |
| Widget provider exported=false | manifest | Correct |
| PendingIntents | NotificationFactory, WidgetProvider | FLAG_IMMUTABLE — correct |
| Failure metadata | `core/recovery/` | No payload/secret fields — correct |
| Retry classes | `core/common/RetryClass.kt` | SAFE_TO_RETRY / RETRY_AFTER_REREAD / NEVER_RETRY |
| Recovery authorization gate | `core/recovery/RecoveryPolicy.kt` | authorizationValid required before retry |
| Notification permission checker | `platform/android/notification/` | Runtime check on API 33+ |

## Weaknesses found

1. No centralized input-validation utility with bounded limits
   (message size, collection size, nesting depth).
2. No deterministic malformed-input/overflow test suite for parsers.
3. No explicit cross-device isolation security tests (beyond
   functional isolation).
4. No persistence-tampering regression tests.
5. No secret-scan in the build pipeline (environment limitation).

## Implementation strategy

New `core/security/` package:

- `InputValidator.kt` — bounded length/collection/nesting checks,
  overflow-safe arithmetic.
- Security regression tests: malformed input, oversized input,
  cross-device isolation, stale-session writes, tampered persistence,
  redaction.

Reuse DeviceAccessPolicy, OmniBudsLogger redaction, recovery
authorization gate. No competing authorization systems.
