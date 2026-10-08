# Phase 12 — Task List

**Status:** All tasks complete.

---

## Implementation

- [x] Audit repository + Phase 11 (Agent 1)
- [x] Verify Android codec-control API surface (Agent 1 + android.jar reflection)
- [x] Verify vendor-protocol path (Agent 4: registry empty → unavailable)
- [x] Write requirements (OB-P12-REQ-001…040)
- [x] `CodecControlCapability` — 5 dimensions + evidence
- [x] `CodecConfiguration` — normalized, all-nullable
- [x] `CodecConfigField` + `CodecFieldSupport` — per-codec field sets
- [x] `CodecOperationType`, `CodecOperation` (validated value)
- [x] `CodecOperationResult` — 14-state sealed hierarchy
- [x] `CodecVerificationStrategy`
- [x] `CodecControlCapabilityResolver` port
- [x] `CodecControlAdapter` port (+ `CodecApplyOutcome`)
- [x] `CodecControlState` — requested vs observed vs confirmed
- [x] `CodecControlEngine` — transaction, per-device mutex, timeouts, rollback
- [x] `DeviceLiveness` gate
- [x] `SideEffectClass` moved `feature` → `common` (layer fix)
- [x] `CODEC_OPERATION_FAILED` error category
- [x] `AndroidCodecControlAdapter` (honest NotAvailable)
- [x] `AndroidCodecControlCapabilityResolver` (honest all-false control)

## Testing

- [x] Capability, operation, result, configuration unit tests
- [x] Engine transaction tests (verify / verification-failed / unverified)
- [x] Per-codec tests (AAC, LDAC, aptX ×4, LC3, SBC)
- [x] Concurrency + multi-device isolation tests
- [x] Timeout, cancellation, disconnect, rollback tests
- [x] Unknown-preservation tests
- [x] Scope tests (no reflection/shell/media/fake-success/android-imports)
- [x] Android adapter honesty tests
- [x] Full regression suite green

## Documentation

- [x] requirements.md, design.md, specs.md, architecture.md
- [x] platform-limitations.md, codec-control.md (this set)
- [x] task-list.md, test-plan.md, validation.md, decisions.md, risk-register.md
- [x] Index + README updates

## Review & release

- [x] Architecture boundary review (DependencyDirectionTest)
- [x] Security review (Agent 6)
- [x] Push to `main`
- [x] Final report; Phase 13 not started
