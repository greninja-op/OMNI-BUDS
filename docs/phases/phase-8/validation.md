# Phase 8 — Validation

**Phase:** 8 — Capability Discovery Engine · **Tree:** code `968adb4` (parent `caea118`, Phase 7's head)
Every number below was re-summed from the JUnit XML of this run, not quoted from a prior document. Physical
device verification is deferred (`NOT RUN`), never reported as passing.

## 1. The gate, exactly as run

```bash
export JAVA_HOME="C:/Users/Athira Aswin/AppData/Local/jdk-17"
./gradlew --offline :core:test :platform:android:test \
  :platform:android:compileDebugAndroidTestKotlin :platform:android:lintDebug --rerun-tasks
```

JDK 17, Gradle wrapper `--offline`, `allWarningsAsErrors = true` (so an unused import or a dead suppression
fails the build), compileSdk 35 / minSdk 26.

## 2. Results

| target | result | count |
|---|---|---|
| `:core:test` | **BUILD SUCCESSFUL**, 0 failures / 0 errors / **0 skipped** | **628** tests |
| `:platform:android:testDebugUnitTest` | pass | 102 |
| `:platform:android:testReleaseUnitTest` | pass | 102 |
| `:platform:android:compileDebugAndroidTestKotlin` | compiles clean | — |
| `:platform:android:lintDebug` | no new issues | — |

`:core` moved **580 → 628**, a **+48** delta, all attributable to Phase 8 (Phase 8 added no platform files, so
`:platform:android` stays at 102/variant). Phase 8 test-class breakdown:

| class | tests |
|---|---|
| `CapabilityEvidenceTest` | 6 |
| `CapabilityDependencyTest` | 9 |
| `DiscoveryStateTest` | 6 |
| `CapabilitySnapshotTest` | 9 |
| `CapabilityDiscoveryEngineTest` | 14 |
| `PhaseEightScopeTest` | 4 |
| **sum** | **48** |

## 3. Architecture checks

- `DependencyDirectionTest` — **17 checks green**. The engine's only cross-area imports are `common`/`state`
  (L0) and `platform` (L1, `TimeProvider`); the L2→L4 ban that shaped ADR-P8-005 is enforced by
  `coreAreasDependOnlyOnMoreFoundationalAreas`, and `everyCoreAreaIsRegisteredInTheLayerMap` still passes
  (no new area was introduced; all five files sit in `capability`).
- `TransportBoundariesTest` — 15 checks green (untouched by Phase 8; re-run clean).
- `PhaseEightScopeTest` — 4 checks: no production discovery source, engine read-only, capability imports
  nothing upward, capability reads no clock/Android.
- No placeholder/stub, no production test double, no magic protocol literal, no UI token, no reverse module
  dependency — all inherited guards still pass; Phase 8 widened none of them and touched none of their token
  lists.

## 4. Git diff inspection

`git show --stat 968adb4` → 11 files, all under `core/src/{main,test}/.../capability`, +1575 lines, no
deletions. No edit to any earlier phase's source, no edit to a guard token list, **no change under
`tools/device-bridge/`** (that workstream is never staged here). The record set (this directory + the three
index documents) lands in the following docs commit only.

## 5. Verification of the "no fabricated capability" rules

- The engine ships **no** `CapabilityDiscoverySource` implementation in `src/main`; the only implementer is a
  private test class. → `PhaseEightScopeTest.noProductionSourceImplementsTheCapabilityDiscoverySource`.
- Against the empty protocol state the engine establishes **nothing**: a pass with all-failed reads is
  `FAILED` with zero capabilities, and an empty attemptable set is `COMPLETE` with zero capabilities. No test
  asserts a real device supports anything.
- Every `FeatureCapability` the fixtures emit was constructed through the Phase 1 guards, which cap persistence
  claims at the matching `VerificationLevel` — so no JVM fixture can mint a `HARDWARE_VERIFIED` claim.

## 6. Deferred — `NOT RUN` (explicitly, not `SKIPPED`, not a blocker)

| item | why deferred | owner |
|---|---|---|
| Discovery over a real `EarbudProtocol`/`ProtocolSession` | no protocol ships; the `ProtocolSession → CapabilityDiscoverySource` binding is L3/L4 wiring | a later protocol-backed phase |
| Any rung above `LAB_TESTED` (`HARDWARE_VERIFIED`, `PERSISTENCE_VERIFIED`) | requires a physical device | the end-of-project device session (ADR-P8-010) |
| Byte-level malformed-response handling | no packet parser ships this phase | the phase that ships a real protocol parser |
| Timeout-driven re-read policy | discovery is read-only; re-read orchestration is a session-layer flow | a later phase |

## 7. Phase-8 boundary check

Nothing beyond the discovery foundation was built: no ANC/EQ/gesture/battery/firmware control, no vendor
encoder, no write path, no automatic connection, no UI, no persistence. The stop-at-boundary rule (ADR-P0-009)
is respected: Phase 9 is not started and needs its own explicit execution prompt.

**Conclusion.** Phase 8's authorized scope is implemented and validated on the JVM at `968adb4`: a
deterministic, read-only capability discovery engine that distinguishes support, access, availability and
verification, keeps unknown from unsupported, preserves evidence provenance and conflicts, validates
dependencies with cycle detection, and claims no hardware capability it has not been given. All device-dependent
behaviour is recorded as `NOT RUN`.
