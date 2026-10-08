# Phase 9 — Test plan

**Phase:** 9 — Hardware Feature Engine · **Scope id:** P9
**Document status:** accepted

## 1. Strategy

JVM unit tests are the primary validation path (`./gradlew :core:test`);
physical-device validation remains deferred by user directive (ADR-P3-014).
All engine tests run against the test-only scripted `FeatureProtocolPort` —
no device is implied, and the ceiling is `IMPLEMENTED`. Coroutine tests use
`runTest` with virtual time, so timeout and race tests are deterministic.

## 2. Test inventory

| ID | File | What it proves |
|---|---|---|
| TEST-P9-001 | `FeatureValueTest` | New value shapes refuse NaN/∞, blank identities, duplicate fields, unbounded collections; `FeatureValueType.accepts` is exact; constraints enforce min/max/step/modes/flags/lengths and never judge foreign kinds |
| TEST-P9-002 | `FeatureAccessTest` | Access derivation from every capability rung × availability; unavailability wins over support; `permits()` enforces read-only |
| TEST-P9-003 | `FeatureStateTest` | The transition table: seeding only from untracked; `Unknown → Pending` illegal; write lifecycle edges; cancellation-restore edges; no cross-feature moves; requested ≠ confirmed |
| TEST-P9-004 | `FeatureEngineTest` | Adopt-snapshot seeding; write Pending→Confirmed with exactly one write + one read-back; device rejection keeps last-confirmed; divergent read-back confirms device value + `STATE_VERIFICATION_FAILED`; failed read-back → `Unknown`; timeout never re-sends (1 write) with the three read-back outcomes; validation failures send nothing; reads confirm/fail/malformed; device reports override pending; invalidation poisons in-flight work; cancellation restores; write-after-invalidation re-asserts availability; `observe()` emits Available→Pending→Confirmed |
| TEST-P9-005 | `FeatureValidatorTest` | Each of the 10 steps refuses correctly with the right code, in pipeline order; refused operations never touch the port; fully-valid reads/writes pass |
| TEST-P9-006 | `FeatureDependencyEvaluatorTest` | Satisfied/missing/unknown/cycled requires; requires-one-of; active vs inert conflicts; pending-active counts as conflict; mutual exclusion; implies-as-warning; vendor exceptions recorded not blocking; real cross-definition cycle detection; construction refuses self-edges |
| TEST-P9-007 | `FeatureConcurrencyTest` | Same-feature writes serialize (second validates fresh); different features run concurrently; disconnect during read-back poisons; cancellation never strands `Pending`; a failed write doesn't block the retry |
| TEST-P9-008 | `VendorFeatureContractTest` | Vendor namespace factory; value validation parity with core; custom payload bounds; malformed namespaces refused; vendor relations evaluate like core ones |
| TEST-P9-009 | `PhaseNineScopeTest` | No production `FeatureProtocolPort` implementation; no protocol/transport imports in the feature area; no brand names in main sources; no simulation markers; value shapes live in the config area only |
| TEST-P9-010 | `StandardFeaturesTest` | Catalogue validity + uniqueness; relations reference known features; no vendor leakage; EQ preset/graphic/parametric values validate with variable band counts; invalid frequencies/gains never construct; gesture values validate incl. vendor actions; anc-mode's closed mode set |
| TEST-P9-011 | `DependencyDirectionTest` (extended) | The `feature` area is registered at layer 5; all existing rules still hold over the new sources |

## 3. Entry/exit criteria

- **Entry:** Phases 0–8 complete; the pre-execution audit found the reusable
  models and the integration points.
- **Exit:** All TEST-P9-001..011 pass; `./gradlew build` is green (or blockers
  documented); the diff contains only authorized changes; validation.md is
  filled; the phase is committed and pushed.

## 4. Non-goals for testing

No hardware-in-the-loop tests (deferred by directive); no performance
benchmarks (no hardware to measure against); no UI tests (no UI exists until
Phase 49).
