# Phase 32 — Permission Matrix

## Declared permissions

| Permission | API | Model | Justification |
|---|---|---|---|
| BLUETOOTH_CONNECT | 31+ runtime; 26–30 n/a | Runtime (31+) | Required for Bluetooth operations on API 31+ |

## Not declared (deliberate)

| Permission | Reason not declared |
|---|---|
| BLUETOOTH_SCAN | No scanning implemented |
| BLUETOOTH_ADVERTISE | No advertising |
| POST_NOTIFICATIONS | Library does not post notifications itself |
| Location (any) | Not required for the implemented operations; never requested as a Bluetooth workaround |
| FOREGROUND_SERVICE_* | No foreground service |

## Permission scenarios (tested)

1. Granted → Allow.
2. Denied → Refuse with reason; no crash.
3. Unavailable on API level → Refuse distinctly.
4. Restricted by policy → Refuse distinctly.
5. Unknown → Defer; recheck required.
6. SecurityException → classified as refusal.
7. Adapter disabled + granted → Degraded (observe only).
8. No adapter → CannotOperate.

## Safety rules (enforced)

- No crash on missing permission.
- No silent bypass.
- No prompt loops (policy decides; UI prompts out of scope).
- Denial ≠ hardware incompatibility.
- Grant ≠ operation success.
