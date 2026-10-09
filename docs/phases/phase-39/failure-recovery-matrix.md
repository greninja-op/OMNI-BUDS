# Phase 39 — Failure/Recovery Matrix

All failure paths exercise the Phase 34 recovery engine and Phase 21
read-only policy.

| Failure | Policy | Evidence |
|---|---|---|
| Device identity mismatch | Fail; unknown-device fallback | VendorMatchingHardeningTest |
| Unsupported firmware/version | Deny; no adapter resolves | Phase 19/21 tests |
| Unsupported capability | UNSUPPORTED; no actionable control | Phase 8/21 tests |
| Authorization denial | DeviceAccessPolicy denies | Phase 35 tests |
| Invalid parameters | InputValidator rejects | Phase 35 tests |
| Malformed protocol data | Parser rejects; never hardware write | Phase 37 tests |
| Timeout | Bounded retry per RecoveryPolicy | Phase 34 tests |
| Cancellation | Cleanup; no replay | Phase 34/38 tests |
| Connection loss | ReconnectPolicy (3 attempts, backoff) | Phase 28/34 tests |
| Correlation failure | Safe rejection; no cross-device completion | Phase 7/37 tests |
| Read-back mismatch | NOT_VERIFIED; no persistence claim | Phase 18 tests |
| Persistence uncertainty | INCONCLUSIVE | Phase 18 tests |
| Diagnostic sink failure | Isolated; operation continues | Phase 36 tests |

No blind write retries. No endless reconnect loops.
