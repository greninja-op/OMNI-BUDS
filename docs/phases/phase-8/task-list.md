# Phase 8 — Task List

**Phase:** 8 — Capability Discovery Engine · **Owner:** orchestrator (Agent K role)
Sequencing actually followed on the tree from `caea118` (Phase 7's head) to `968adb4`. Every "done" task names
the file or test that carries it; nothing is marked done that is not in the committed tree.

| id | task | status | carried by |
|---|---|---|---|
| P8-T-001 | Inspect the whole repository and every prior phase record; inventory the existing capability model | done | `architecture-audit.md` §1 |
| P8-T-002 | Settle the prompt-vs-tree reconciliations as ADRs before code | done | `decisions.md` ADR-P8-001 … 010 |
| P8-T-003 | Verify the layer arithmetic that binds the engine's placement (capability L2 ↮ protocol L4) | done | `architecture-audit.md` §4; `DependencyDirectionTest` re-run green |
| P8-T-004 | Add the availability axis and the evidence/provenance model | done | `CapabilityEvidence.kt` (19/34/46/80/109) |
| P8-T-005 | Add dependency edges, statuses, report and the cycle-detecting validator | done | `DependencyValidator.kt` (15/29/44/73/75/101) |
| P8-T-006 | Add the discovery lifecycle state machine | done | `DiscoveryState.kt` (18/50/67/72/75/82) |
| P8-T-007 | Add the immutable, versioned snapshot wrapping `DeviceCapabilities` + `PartialFailure`/`UnresolvedConflict` | done | `CapabilitySnapshot.kt` (23/54/63/66/87/94) |
| P8-T-008 | Add the read-only source seam and the deterministic engine | done | `CapabilityDiscoveryEngine.kt` (13/33/67/72/136/179) |
| P8-T-009 | Fold reads through the evidence ladder; surface genuine conflicts; refuse malformed input | done | engine `discover`/`conflictsFrom`; `CapabilityDiscoveryEngineTest` (10/11 conflict + mislabeled) |
| P8-T-010 | Derive `COMPLETE`/`PARTIALLY_COMPLETE`/`FAILED`/`CANCELLED` and lock the snapshot invariants | done | engine completion derivation + `CapabilitySnapshot.init` |
| P8-T-011 | Apply dependency blocking to availability only, never to support | done | engine blockedFeatures → `UNAVAILABLE`; `aBlockedFeatureBecomesUnavailableButStaysSupported` |
| P8-T-012 | Route discovery errors onto existing categories, none new | done | `specs.md` §6; `MALFORMED_RESPONSE_CATEGORY = INVALID_STATE` |
| P8-T-013 | Read time only through the injected `TimeProvider`; null when none supplied | done | engine `snapshot(...)` + `aMissingClockYieldsNoTimestampRatherThanAnInventedOne` |
| P8-T-014 | Write the capability-model tests (availability, evidence ceiling, dependencies/cycles, snapshot invariants, lifecycle) | done | `CapabilityEvidenceTest` (6), `CapabilityDependencyTest` (9), `DiscoveryStateTest` (6), `CapabilitySnapshotTest` (9) |
| P8-T-015 | Write the engine tests (success/partial/failed/cancel/malformed/determinism/conflict/availability/clock) | done | `CapabilityDiscoveryEngineTest` (14) — scripted `CapabilityDiscoverySource`, test source only |
| P8-T-016 | Write the Phase 8 scope guard (no production source, engine read-only, no upward import, no clock/Android) | done | `PhaseEightScopeTest` (4) |
| P8-T-017 | Run the automated gate and re-sum test totals from the JUnit XML | done | `validation.md` — `:core` 628 (+48), `:platform:android` 102/variant, lint clean |
| P8-T-018 | Reconcile inherited docs to the shipped API surface in the open | done | `decisions.md`/`architecture-audit.md` corrected to match code (`TEMPORARILY_UNAVAILABLE`, source method names, category map, `subjectRef`) |
| P8-T-019 | Author the Phase 8 record set | done | this directory (requirements/design/specs/task-list/test-plan/validation/risk-register/decisions) |
| P8-T-020 | Update the live ADR index, the docs index and the master context to Phase 8 | done | `docs/decisions/README.md`, `docs/README.md`, `docs/MASTER-CONTEXT.md` |
| P8-T-021 | Commit code, then records; push `origin/main`; stop at the Phase 8 boundary | in progress | two commits; no Phase 9 work started |
| P8-T-022 | Bind a `CapabilityDiscoverySource` to a real `EarbudProtocol`/`ProtocolSession` | `DEFERRED` | needs a protocol (L3/L4 adapter) — a later phase |
| P8-T-023 | Raise any capability rung above `IMPLEMENTED`/`LAB_TESTED`; run device/persistence discovery | `NOT RUN` | device testing deferred to project end (ADR-P8-010) |
