# Phase 1 — Validation

```text
Phase:   1 — Project Foundation & Kotlin Architecture
Status:  COMPLETE — VALIDATED

Build:              PASS
Tests:              PASS
Architecture:       PASS
KMP readiness:      PASS — with named limits
Domain models:      PASS
Documentation:      PASS
Bluetooth implementation:  NONE (verified)

Ready for Phase 2:  YES
```

Recorded 2026-10-01 by the orchestrator. Every figure below was produced by running a command or reading a file, not reported by a sub-agent and accepted. Where a claim rests on inspection rather than an automated check, it says so.

---

## Verification basis

| Check | Command or method | Measured result |
|---|---|---|
| Full build | `./gradlew clean build` | BUILD SUCCESSFUL |
| Unit tests | `:core:test` results parsed from `core/build/test-results/test/*.xml` | **302 tests, 0 failures, 0 errors, 0 skipped**, across 37 test classes |
| Tests per area | same XML, grouped by package | `protocol` 60, `capability` 56, `device` 37, `audio` 31, `diagnostics` 22, `common` 20, `config` 19, `transport` 19, `architecture` 11, `session` 12, `testing` 8, `persistence` 7 |
| Warning gate | compiler with `allWarningsAsErrors = true`, both modules | zero-warning build |
| Android lint | `:platform:android:lintDebug` executed as part of `build` | ran and did not fail; module is source-free, so this proves the wiring, not any Android behavior |
| Source volume | filesystem | 71 main files / 5,196 lines; 41 test files / 5,885 lines |
| Suspend surface | grep of main sources | 21 `suspend fun` declarations, zero coroutine-library symbols |
| Repository | `git log --oneline`, `git status` | branch `main`, five commits before this phase's document commit |

**The warning gate bit during this phase, which is evidence it works.** Attempting to restrict `DeviceState`'s primary constructor produced `w: Non-public primary constructor is exposed via the generated 'copy()' method`, which `-Werror` turned into a build failure. That is how the limitation in *Known issue 1* was discovered rather than assumed.

---

## Definition of done (prompt section 50)

```text
[x] Repository inspected                         repository-analysis.md; toolchain discovery recorded
[x] Architecture finalized                       design.md; ADR-P1-001, 002, 003
[x] Modules established                          :core (Kotlin/JVM), :platform:android (library)
[x] Package boundaries established               11 areas, layer map enforced by test
[x] Core/domain independent of Android            0 android.*/androidx.* imports in :core (test-enforced)
[x] KMP boundary documented                       kmp-review.md, ADR-P1-002, 012
[x] Device models implemented                     identity, fingerprint, session, firmware, battery
[x] Capability model implemented                  six states + affordance consistency by construction
[x] Audio domain model implemented                transport kinds, channel/quality modes, registry
[x] Codec model implemented                       9 codecs, 3 families, aptX variants distinct
[x] Error model implemented                        16 categories with retry class + invalidation flag
[x] Protocol abstractions implemented              narrow core contract + 5 optional capability interfaces
[x] Repository abstractions established            device / capability / protocol-record contracts
[x] Coroutine rules established                    suspend surface defined; Flow deliberately absent (ADR-P1-021)
[x] Dependency injection strategy established      manual constructor injection, no framework (ADR-P1-009)
[x] Test foundation established                    JUnit 5 + kotlin-test + coroutines-test, doubles in test scope
[x] Architecture validation established            DependencyDirectionTest, 11 mechanical checks
[x] Unit tests pass                               302 / 0 / 0 / 0
[x] Build passes                                  BUILD SUCCESSFUL, zero warnings
[x] Documentation updated                         README.md + the eight records + six review documents
[x] No Bluetooth implementation accidentally added  test-enforced: identifier scan over code lines, empty registry
[x] No fake hardware implementation added           test-enforced: no production impl of a contract, no doubles in main
[x] Phase validation completed                     this document
```

---

## Architecture review (prompt section 54)

Full answers with evidence in `architecture-review.md`; verdicts summarised here.

| # | Question | Verdict | Carried by |
|---|---|---|---|
| 1 | Android Bluetooth code addable without modifying the domain? | YES | `TransportContract`, `EarbudProtocol`, `:platform:android` boundary; import rules enforced by test |
| 2 | RFCOMM and GATT devices sharing one higher-level protocol abstraction? | YES, with caveat | `TransportKind` is data on the protocol record; caveat: no notification/event channel on `TransportContract` yet, so a notify-driven vendor channel needs an addition |
| 3 | AAC, LDAC, aptX and LC3 representable without rewriting? | YES | `Codec`, `CodecFamily`, `CodecState` ladder, `CodecCapability.configurable` |
| 4 | Vendor features expressible without polluting common interfaces? | YES, with caveat | `FeatureId` vendor namespace, `VendorExtension` guard; caveat: grammar is duplicated between kernel and `VendorExtension`, so one must be retired |
| 5 | Device temporarily connectable without being permanently saved? | YES, with caveat | `SessionClassification`, `DeviceSession.save()/forget()`, `SavedDeviceRecord.identityKey` (no MAC); caveat: nothing yet *refuses* saving a temporary device — that is a Phase 3/4 gate |
| 6 | Persistent configuration verifiable across reconnect? | YES, with caveat | `CapabilityState.PERSISTENCE_VERIFIED`, `VerificationLevel`, `FeatureCapability` consistency rules; caveat: expressible, never performed — no hardware was touched, so no capability in this repository is verified |
| 7 | Core eventually compilable in a KMP environment? | YES, with caveat | zero production dependencies, zero `java.*` imports, epoch-millis time model; caveat: enforcement is import-based text scanning, not bytecode analysis |
| 8 | Every future UI surface consumes the same authoritative state? | **YES — after correction** | `DeviceState` owns connection state, `revision`, `applyIfNewer`; **this was answered NO on first review** and fixed under ADR-P1-020 |

Question 8 was not softened into a caveat. It was a real defect in the phase's own design, it was recorded as NO, it was fixed in code with tests, and the verdict changed only after the fix was verified.

---

## Implemented

Two Gradle modules with a pinned, reproducible toolchain; the shared kernel (feature identity, transport kinds, structured errors with derived retry policy, three-case outcome, capability and verification state models, connection state machine); device identity, firmware evidence, fingerprint with a deterministic identity key, session record, battery state; capability model with construction-enforced affordances, container with absent-means-`UNKNOWN` lookup, fifteen core feature identities, vendor extension records with a namespace guard; audio codec registry, ordinal codec state ladder with an orthogonal configurability attribute, transport kinds, channel and quality modes, audio transport state with unknown-preserving fields; transport and protocol contracts including command/response/capability-mapping definitions and an empty protocol registry; repository contracts for saved devices, discovered capabilities and protocol knowledge; configuration split into application, device and protocol with feature flags that cannot grant capability support; diagnostics model with opt-in-sensitive severities and a deterministic snapshot builder; a single authoritative `DeviceState` with monotonic revision and stale-update rejection; 302 unit tests including 11 mechanical architecture checks; a project README stating the hardware-truth rule; and the full Phase 1 document set.

## Not implemented — by design, not by omission

No Bluetooth of any kind, no permissions, no scanning or connection, no GATT/RFCOMM/AVRCP/HFP/LE Audio, no codec negotiation or selection, no ANC/transparency/EQ/gesture semantics, no battery reading from hardware, no firmware retrieval, no vendor protocol, no storage engine, no serialization, no UI, no notifications, no Quick Settings, no widgets, no background service, no packet logging, no redactor, no CI.

`earbud identification`, `device fingerprinting` and `connected-device session creation` are named in the Phase 2 forbidden list as well; Phase 1 correctly ships their *types* only, with no discovery logic.

## Tests

302 executed, all tier **T1 (unit / JVM)**. Ceiling justified by these tests: `IMPLEMENTED`. Nothing here reaches `LAB_TESTED` in the sense of a protocol simulation, and nothing reaches `HARDWARE_VERIFIED` or `PERSISTENCE_VERIFIED`: no device, phone, emulator or radio was involved. 0 skipped, so no test was quietly parked.

Three defects were caught by execution rather than by reading, and are worth stating because they show the suite is load-bearing:

1. A battery test asserted both that `left=0, right=100` was asymmetric and was not; the contradictory assertion was corrected (the implementation was right).
2. A codec-registry ordering test hard-coded an alphabetical expectation that did not match its own fixture names; corrected.
3. The layer-map test reported two upward/sideways edges (`common → transport`, `logging → diagnostics`) and a `state ↔ capability` cycle. All three were fixed structurally: `TransportKind` moved into `common`, the logging seam folded into `diagnostics`, and `DeviceState` moved into `session` (ADR-P1-003).

---

## Architecture checks

| Check | What it forbids |
|---|---|
| no Android imports in core main | `android.*`, `androidx.*`, `com.omnibuds.android` |
| no JVM-only imports in core main | `java.*`, `javax.*` |
| no Bluetooth/media framework identifiers | `Bluetooth*`, `AudioTrack`, `AudioManager`, `AudioRecord`, `MediaPlayer` in code lines |
| no placeholder implementations | `TODO(`, `NotImplementedError`, `error("` in main source |
| no test doubles in production | any `Fake*`/`Mock*`/`Stub*`/`Dummy*`/`Test*` type in main source |
| no production implementation of a contract | a main-source class implementing `EarbudProtocol`, `DeviceRepository`, `CapabilityRepository`, `TransportContract` |
| dependency direction | an import from an area to an equal or higher layer |
| layer map completeness | an area not registered deliberately |
| no magic protocol literals | hex constants and hard-coded UUID strings |
| package/directory agreement | a package statement that drifts from the path |
| Android module still empty | any Kotlin source under `platform/android/src/main` |

Comment and KDoc lines are excluded from the identifier scans, so documenting a forbidden concept is not a violation, and each scan **fails loudly if its source root is missing** rather than passing vacuously.

---

## Bluetooth implementation: NONE

Verified from four independent directions rather than asserted: no Bluetooth identifier appears in any code line; `ProtocolRegistry` is empty and test-asserted empty; no production type implements a transport, protocol or repository contract; and `:platform:android` contains no Kotlin source at all. `README.md` states plainly that no device is supported.

---

## Known issues

1. **`DeviceState.copy()` can bypass the state machine.** `attemptConnection` refuses illegal transitions, but the generated `copy(connection = …)` still permits one. Making the constructor `internal` does not close it: Kotlin exposes a data class's `copy()` publicly regardless of constructor visibility, and the build flags the attempt as a warning. Restricted to convention now, documented on the type, and owned by the Phase 24 single-state-owner work. **Not presented as solved.**
2. **No coverage measurement.** 302 green tests say nothing about untested branches inside `DeviceFingerprint.identityKey()`, merge paths, or validators. Gap G-3 in `testing-review.md`.
3. **`state` area has no test class of its own.** Its four vocabularies are covered indirectly through consumers and `DeviceStateTest`, not directly.
4. **Grammar duplication for vendor identities.** Both `FeatureId.isVendorExtension` and `VendorExtension.isVendorFeature` decide vendor-ness; one should be retired (architecture question 4 caveat).
5. **`DeviceSession` and `DeviceState` join only on `sessionId`.** No type prevents a state value being paired with the wrong session; Phase 4's session engine should carry the association.
6. **Retry table pins 16 categories, but `invalidatesSession` is asserted through two lists rather than one exhaustive map**, so a newly added category could slip past the invalidation assertion. Lower priority than the retry assertion, which is now exhaustive.
7. **Per-endpoint codec support is unmodelled** (ADR-P1-018, open).
8. **No diagnostic redactor** (ADR-P1-019); `SEC-LOG-002` remains an emission-boundary duty for Phase 36.
9. **No CI and no git remote.** The build is verified on one workstation; RISK-020.

## Platform limitations acknowledged

`compileSdk 35` and `minSdk 26` are configuration choices, not verified platform behavior — `minSdk` is explicitly provisional and must be re-decided in Phase 2 against the real permission model (ADR-P1-015). No Android API was invoked, so no platform restriction has been observed firsthand; the adapter-state, permission-model and background-execution constraints Phase 2 will meet are still documented expectations from Phase 0. Toolchain versions were pinned from what this workstation already provides (ADR-P1-014), which is a reproducibility risk on other machines (RISK-016). No user-level Gradle or JDK configuration was modified, and no toolchain install was touched.

## Hardware limitations

No earbud, headphone, phone, dongle or board was connected, powered, scanned or paired. Consequently **zero** facts are known about any device: no firmware version, no service or characteristic identity, no battery reading, no codec state, no capability. Every hardware-shaped concept in this phase is a contract awaiting its first contact with real silicon (master section 54, RISK-018).

## Deferred work

| Item | Owner |
|---|---|
| CI (build + `:core:test` minimum) and remote decision | Phase 2 or 51 |
| Coverage measurement; direct tests for the `state` vocabularies | Phase 2 onward |
| Restrict `DeviceState` mutation to one owner (Known issue 1) | Phase 24 |
| Retire the duplicated vendor-identity grammar | Phase 7 |
| Session↔state association beyond `sessionId` | Phase 4 |
| Per-endpoint codec support | Phase 11 |
| Serialization choice with versioning and unknown-safe decoding | Phase 17 or 22 |
| Diagnostic sink with real redaction | Phase 36 |
| `minSdk` re-decision with permission evidence | Phase 2 |
| Project instruction file (`AGENTS.md`/`QODER.md`) so sessions inherit the contract | Phase 2 (not authorised in Phase 1) |
| Module topology beyond the two established modules | each owning phase |

## Ready for Phase 2

**YES.** The Android boundary exists as a compiling module, the dependency rules are mechanically enforced, errors and outcomes have a single canonical shape, the capability and codec vocabularies Phase 2 must not fabricate are already constrained by construction, and Phase 2's forbidden list is protected by tests that will fail if it overreaches. Phase 2 should begin with its own pre-execution audit; it must also settle `minSdk` (ADR-P1-015) and decide whether CI arrives with it.

Phase 2 has been authorised separately and its prompt is staged at `docs/phases/phase-2/execution-prompt.md`. **Phase 1 stops here.** Phase 2 implementation begins only on the orchestrator's next explicit step, and nothing in this phase pre-authorised it.
