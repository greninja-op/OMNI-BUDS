# Phase 35 — Security Control Matrix

| Threat | Control | Test |
|---|---|---|
| T-35-01 unauthorized write | DeviceAccessPolicy; immutable PendingIntents; recovery auth gate | existing action tests |
| T-35-02 malformed protocol | InputValidator bounds; typed failures | InputValidatorTest |
| T-35-03 cross-device leak | per-device keyed state; explicit session ids | DeviceIsolationSecurityTest |
| T-35-04 stale session write | supersession abort | Phase 34 RecoveryPolicyTest |
| T-35-05 oversized import | 16 MiB / 64 KiB bounds | InputValidatorTest |
| T-35-06 log disclosure | sink redaction; LogRedactor | LogRedactorTest |
| T-35-07 component abuse | manifest guards | review |
| T-35-08 dependency | pinned versions; minimal set | review |
