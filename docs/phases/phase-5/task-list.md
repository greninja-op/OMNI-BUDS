# Phase 5 — Task List

**Phase:** 5 · **Owner:** orchestrator
**Document status:** the tasks the phase actually ran, mapped to the commit that landed them
(`a5a177d`) and the test that proves them. A task is `done` only where a `src/test` case passes at that
commit.

| id | task | deliverable | requirement | status |
|---|---|---|---|---|
| P5-T-001 | Pre-execution audit of identity/fingerprint types and their producers | `architecture-audit.md` | REQ-001, REQ-008 | done (found Phase 1 built the types with no producer; Phase 3 refused two signals) |
| P5-T-002 | Settle ADR-P5-001 … ADR-P5-012; close the stale `ADR-P0-018` | `decisions.md` | all | done |
| P5-T-003 | Model typed identity signals with six qualities and validation | `IdentitySignal.kt` | REQ-001…005 | done — `IdentitySignalTest` |
| P5-T-004 | Versioned normalizer (trim/collapse/case-fold only) | `IdentityNormalizer.kt` | REQ-006 | done — `IdentityNormalizerTest` |
| P5-T-005 | Confidence ladder with written evidence requirements | `IdentificationConfidence.kt` | REQ-016…018 | done — `PhaseFiveRegistryTest` capping/reachability cases |
| P5-T-006 | Manufacturer/model registry with construction guards, shipping empty | `DeviceIdentityRegistry.kt` | REQ-014, REQ-019…021 | done — `PhaseFiveRegistryTest` |
| P5-T-007 | Seven-outcome sealed result model | `IdentificationResult.kt` | REQ-012, REQ-030 | done — `IdentityEngineTest` |
| P5-T-008 | Pure synchronous matcher and fingerprint builder | `IdentityEngine.kt` | REQ-007…013, REQ-017 | done — `IdentityEngineTest` (12 cases) |
| P5-T-009 | Session integration: `productIdentity` field, `enrichedWith`, `enrichIdentity`, 8th event | `TrackedDeviceSession.kt`, `DeviceSessionEngine.kt`, `DeviceSessionEvent.kt` | REQ-022…024 | done — `PhaseFiveSessionEnrichmentTest` (6 cases) |
| P5-T-010 | Move the discovery-scan authorization tag off Phase 5 | `BluetoothOperation.kt` | REQ-029 | done — `PhaseFiveScopeTest` |
| P5-T-011 | Phase 5 scope guard (no new operation, no control/transport/persistence seam, no address member, no new area) | `PhaseFiveScopeTest.kt` | REQ-008, REQ-025, REQ-026, REQ-028, REQ-029 | done (4 cases) |
| P5-T-012 | Full gate green (core JVM, android unit, androidTest compile, lint) | `validation.md` | REQ-… acceptance | done — 537 core + 90 android, lint clean |

## Explicitly not run (out of Phase 5's authorized scope)

- A platform identity *collector* (`platform/android/.../identity`) reading name/alias/type/class/cached
  UUIDs from `BluetoothDevice`. Deferred: the standing directive is to build the product before the phone,
  so no `BluetoothOperation` was newly authorized and no Android handle was opened. The signal model and
  its factories are the seam that collector will target.
- Registry population with real manufacturer/model rules. Refused by ADR-P5-006 until evidence is cited.
- Protocol resolution / transport selection / GATT / RFCOMM / scanning / battery / ANC / EQ / firmware /
  saved-device persistence / UI — all prompt §17 prohibitions, none implemented.
- Physical-device identification testing — deferred to end of project (ADR-P3-014), recorded `NOT RUN`.
