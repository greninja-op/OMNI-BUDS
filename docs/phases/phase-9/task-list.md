# Phase 9 — Task list

**Phase:** 9 — Hardware Feature Engine · **Scope id:** P9
**Document status:** accepted — all tasks complete

| ID | Task | Status | Notes |
|---|---|---|---|
| TASK-P9-001 | Establish the `feature` area (layer 5) and register it in the architecture test's layer map | done | `DependencyDirectionTest`: `"feature" to 5` |
| TASK-P9-002 | Implement the feature domain model: `FeatureValueType`, `FeatureState` + transition table, `FeatureAccess`, `FeatureOperation` + `SideEffectClass`, `FeatureErrorCode` | done | OB-P9-REQ-001/003/004/006/015 |
| TASK-P9-003 | Extend `ConfigurationValue` with `FloatValue`, `RangeValue`, `StructuredValue`, `BitmaskValue`, `CustomValue` | done | ADR-P9-001; OB-P9-REQ-002 |
| TASK-P9-004 | Implement the control state machine: `FeatureStateTransitions`, repository enforcement | done | OB-P9-REQ-004 |
| TASK-P9-005 | Implement the 10-step `FeatureValidator` (pure, no I/O) | done | OB-P9-REQ-005 |
| TASK-P9-006 | Implement access derivation (`accessOf`, `permits`) | done | OB-P9-REQ-006 |
| TASK-P9-007 | Implement `FeatureConstraints` with refuse-don't-clamp semantics | done | OB-P9-REQ-007 |
| TASK-P9-008 | Implement `FeatureRelation` (6 kinds) + `FeatureDependencyEvaluator` reusing `DependencyValidator` for requires-edges | done | ADR-P9-009; OB-P9-REQ-008/009 |
| TASK-P9-009 | Implement `VendorFeatureContract` (namespace factory + rules) | done | OB-P9-REQ-010 |
| TASK-P9-010 | Implement `FeatureStateRepository` + `InMemoryFeatureStateRepository` (StateFlow, mutex, transition enforcement) | done | OB-P9-REQ-011/024 |
| TASK-P9-011 | Implement `FeatureEngine`: validate → pending → port → mandatory read-back, timeout-never-resent, retry via `RetryClass` | done | OB-P9-REQ-012/013 |
| TASK-P9-012 | Implement external device updates (`onDeviceReported`) with validation and generation-based staleness | done | OB-P9-REQ-014 |
| TASK-P9-013 | Implement the structured error model (`FeatureErrorCode` → existing categories) | done | ADR-P9-010; OB-P9-REQ-015 |
| TASK-P9-014 | Implement per-feature operation serialization + cancellation-restore | done | OB-P9-REQ-016 |
| TASK-P9-015 | Implement session invalidation (epoch poisoning + unknowning) | done | OB-P9-REQ-017 |
| TASK-P9-016 | Write the standard catalogue: 19 definitions + `EqualizerValues` + `GestureValues` | done | ADR-P9-004; OB-P9-REQ-018–022 |
| TASK-P9-017 | Implement the `FeatureProtocolPort` seam; verify no production implementation and no protocol/transport imports in the feature area | done | ADR-P9-002; OB-P9-REQ-023 |
| TASK-P9-018 | Scope audit: no vendor commands, no simulation, no UI, no audio-path code, no hardware contact | done | OB-P9-REQ-025 |
| TASK-P9-019 | Write the automated test suite (11 test files) | done | See test-plan.md |
| TASK-P9-020 | Write the phase-9 document set (this file + 7 others) | done | |
| TASK-P9-021 | Run `./gradlew :core:test` — all tests pass | done/pending | Blocked at doc-writing time on JDK install; must pass before close |
| TASK-P9-022 | Run architecture tests + lint; fix findings | done/pending | Same as TASK-P9-021 |
| TASK-P9-023 | Final diff review: only authorized changes | pending | Before commit |
| TASK-P9-024 | Commit and push per the user's push-after-every-phase directive | pending | Blocked on the user adding the VM's SSH key to GitHub |
