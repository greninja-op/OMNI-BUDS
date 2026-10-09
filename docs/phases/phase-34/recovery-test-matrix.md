# Phase 34 — Recovery Test Matrix

| Scenario | Expected decision | Tests |
|---|---|---|
| Transient transport failure | RETRY_OPERATION (bounded) | RecoveryPolicyTest |
| Timeout | RETRY_OPERATION / RETRY_CONNECTION | RecoveryPolicyTest |
| Budget exhausted | REQUIRE_USER_INTERVENTION | RecoveryPolicyTest |
| Ambiguous write | RECONCILE_DEVICE_STATE | RecoveryPolicyTest |
| Permission denied | REVALIDATE_PERMISSIONS | RecoveryPolicyTest |
| Bluetooth disabled | WAIT_FOR_ADAPTER | RecoveryPolicyTest |
| Unsupported protocol | ABORT_OPERATION | RecoveryPolicyTest |
| Stale state | RECONCILE_DEVICE_STATE | RecoveryPolicyTest |
| Persistence failure | RELOAD_PERSISTED_CONFIGURATION | RecoveryPolicyTest |
| Background restricted | MARK_SESSION_UNAVAILABLE | RecoveryPolicyTest |
| Cancelled | ABORT_OPERATION | RecoveryPolicyTest |
| Superseded session | ABORT_OPERATION | RecoveryPolicyTest |
| Invalid authorization | ABORT_OPERATION | RecoveryPolicyTest |
| Determinism | same input → same decision | RecoveryPolicyTest |

## Evidence

All: UNIT_TESTED. Scripted-transport scenarios reuse Phase 30
FailureInjector (existing tests). No hardware evidence claimed.
