# Phase 45 — Firmware Compatibility Policy

## 1. Safety Invariants

1. **Explicit Support Required**: A protocol implementation or capability must only be offered if the firmware revision matches verified evidence.
2. **Denylist Superiority**: An explicit incompatibility or denylist rule overrides any general family-level match.
3. **Unknown Firmware Policy**: When firmware cannot be determined, mutating operations are denied. Safe read-only behavior is permitted only when an adapter is explicitly firmware-agnostic.
4. **Freshness Enforcement**: Observations older than the allowable TTL (default 5 minutes) are deemed stale. Stale observations cannot authorize operations.
5. **No Blind Retries**: If compatibility evaluation results in an ambiguous or incompatible state, operations must fail immediately without blind retries.
