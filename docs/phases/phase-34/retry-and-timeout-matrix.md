# Phase 34 — Retry and Timeout Matrix

## Existing values (preserved)

| Policy | Value | Source |
|---|---|---|
| Reconnect max attempts | 3 | `ReconnectPolicy` (Phase 28) |
| Base backoff | 5,000 ms | `ReconnectPolicy` |
| Max backoff | 60,000 ms | `ReconnectPolicy` |
| Retry eligibility | SAFE_TO_RETRY / RETRY_AFTER_REREAD / NEVER_RETRY | `RetryClass` (Phase 0) |

## Phase 34 additions

| Item | Value |
|---|---|
| Recovery retry budget | attemptCount vs maxAttempts in RecoveryContext |
| Budget exhaustion | → REQUIRE_USER_INTERVENTION |
| Ambiguous writes | never blind-replay; reconcile first |
| Per-device isolation | retry context is per device/session |

## Timeout semantics

- Timeouts come from the transport/protocol layer, not invented here.
- A timed-out operation's outcome is unknown until reconciled.
- Late responses must be correlated to their session/operation;
  responses for superseded sessions are ignored.
- Cancellation stops pending retries immediately.

## Rules

- No real-time sleeps in deterministic tests.
- Permission failures never cause retry storms.
- Unsupported/malformed never retried.
