# Phase 38 — Repository Audit

**Date:** 2026-10-09

## Existing infrastructure (verified, Phases 30–37)

| Component | Location | Reuse |
|---|---|---|
| `TestCase` contract | `core/testkit/` | Campaign check descriptors |
| `ScriptedTransport` / `FailureInjector` | `core/testkit/` | Dry-run transport simulation |
| `TestResult` / `TestResultCategory` | `core/testkit/TestEvidence.kt` | Result reporting |
| `DeviceProfile` | `core/testkit/crossdevice/` | Hardware profile basis |
| `LabParser` / `ParseOutcome` | `core/lab/` | Protocol checks |
| `ProtocolTestRunner` / `ProtocolCampaign` | `core/protocoltest/` | Campaign selection model |
| `DiagnosticStore` / `DiagnosticHealth` | `core/diagnostics/` | Evidence collection |
| `LogRedactor` / `InputValidator` | `core/security/` | Redaction, bounds |
| `RecoveryEventSink` / failure contracts | `core/recovery/` | Failure classification |
| `DeviceAccessPolicy` | `core/access/` | Read-only enforcement |

## Gaps

1. No explicit execution-environment model (simulated vs hardware).
2. No hardware profile schema with verification status.
3. No centralized HIL safety policy in executable code.
4. No rig abstraction (host, DUT, session, evidence collector).
5. No campaign executor with typed stage outcomes.
6. No HIL report distinguishing outcome from evidence level.

## Implementation strategy

New `core/hil/` package at layer 5:

- `HilEnvironment.kt` — environment enum.
- `HardwareProfile.kt` — versioned profile + validator.
- `HilSafetyPolicy.kt` — executable safety gates (physical off by
  default, writes off by default).
- `HilRig.kt` — rig interfaces + deterministic fakes.
- `HilCampaign.kt` — campaign/stage model with typed outcomes.
- `HilReport.kt` — report with outcome/evidence-level separation.

No physical execution. No Bluetooth API calls. No UI.
