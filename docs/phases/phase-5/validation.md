# Phase 5 — Validation

**Phase:** 5 — Device Fingerprinting & Identification · **Owner:** orchestrator
**Code commit:** `a5a177d` — `feat(core): identify devices from evidence and let an unidentified one stay
unknown` (16 files, 2,020 insertions, 10 deletions; 10 new source/test files + 6 amended).
**Full gate command:** `./gradlew --offline :core:test :platform:android:test
:platform:android:compileDebugAndroidTestKotlin :platform:android:lintDebug --rerun-tasks`
**Gate result:** `BUILD SUCCESSFUL`. Core JVM **537 tests, 0 failures** (Phase 5 added **43**);
`:platform:android` unit **90 tests** per variant, 0 failures; `compileDebugAndroidTestKotlin` and
`lintDebug` clean. `allWarningsAsErrors` and the 17 architecture checks pass unchanged — Phase 5 widened
no dependency rule to admit its own code.
**Environment header (TST-MOCK-006):** MOCKED ENVIRONMENT. No Bluetooth radio, no handset, no network.

---

## 1. Acceptance criteria (prompt §20), each with the evidence that holds it

- [x] **Previous phase contracts respected.** No 13th source area; `device` stays L2 and `session` L3
  import downward (`PhaseFiveScopeTest.phaseFiveAddedNoNewSourceArea`, `DependencyDirectionTest`);
  Phase 4's connection-authority and event rules unchanged; the one Phase 4/3 touch is a data correction
  (scan tag, ADR-P5-012) guarded by `nothingNewIsAuthorisedAtPhaseFive`.
- [x] **Identity signals are modelled.** `IdentitySignal` + 9 kinds + 6 qualities (`OB-P5-REQ-001/002`),
  `IdentitySignalTest` (8).
- [x] **Fingerprint generation is deterministic.** `buildFingerprint` over Phase 1's type;
  `identicalSignalsProduceAnIdenticalResultAndKey`; inherited `DeviceFingerprintTest`.
- [x] **Manufacturer normalization exists.** `ManufacturerIdentity` canonical slug + aliases;
  `IdentityNormalizer` v1 (`IdentityNormalizerTest`, 7).
- [x] **Model matching is rule-based and documented.** `IdentificationRule` with the full §11 field set;
  `IdentityEngineTest` (12).
- [x] **Confidence semantics are explicit.** Categorical `IdentificationConfidence`, per-rung evidence
  requirements, engine-side capping (`aHighRuleBackedByOneKindIsCappedAndLandsAtLikely`).
- [x] **Ambiguous identities remain ambiguous.** `Ambiguous` carries candidates and exposes no single
  manufacturer/model field (`disagreeingSurvivorsStayAmbiguousAndNameNoWinner`).
- [x] **Unknown devices are handled correctly.** `Unknown`/`InsufficientEvidence`/`InvalidEvidence`
  distinct and reachable; the empty registry answers `Unknown` (`theProductionEmptyRegistryIdentifiesNothing`).
- [x] **Identity enrichment does not corrupt sessions.** `enrichIdentity` cannot mint, move connection, or
  bump state revision; 6 assertions in `PhaseFiveSessionEnrichmentTest`.
- [x] **Global registry and user device data remain separate.** No `persistence` import in identity sources;
  registry lives in `device`, sessions hold only their conclusion
  (`theIdentificationSourcesReachNoControlTransportOrPersistenceSeam`).
- [x] **Sensitive identifiers are protected.** No address kind/value/member; `identityKey` folds `':'`;
  enrichment event carries no device text (`identitySignalTypesDeclareNoAddressBearingMember`; SEC-ID-003).
- [x] **No unsupported device signatures are invented.** Production registry empty and a source scan
  forbids any `src/main` rule (`PhaseFiveRegistryTest.theProductionRegistryShipsEmpty`,
  `.noMainSourceDeclaresARuleOrAMatchCondition`).
- [x] **No vendor protocol is implemented.** `maySelectProtocol = false` by construction; protocol
  resolution absent; `BluetoothOperation` set unchanged at 5.
- [x] **No production UI is introduced.** Nothing beyond `:core` domain types; no `:platform` UI, no
  collector.
- [x] **Automated tests pass.** 537 core + 90 android, 0 failures.
- [x] **Build and static analysis pass.** `BUILD SUCCESSFUL`, `lintDebug` clean, `-Werror` clean, 17 arch
  checks green.
- [x] **All mandatory documentation exists.** `requirements.md`, `design.md`, `specs.md`, `task-list.md`,
  `test-plan.md`, `validation.md`, `decisions.md` (ADR-P5-001…012), `risk-register.md` (RISK-086…097),
  plus `architecture-audit.md` and the staged `execution-prompt.md`.
- [x] **Physical-device verification is marked deferred.** See §3; recorded `NOT RUN`, never skipped.
- [x] **Git diff contains only authorized changes.** `git show --stat a5a177d` is 16 Phase 5 paths; the
  concurrent `tools/device-bridge/**` workstream was never staged.

## 2. Claims and their honest ceiling (TST-REC/TST-MOCK-001)

Every Phase 5 capability caps at **`IMPLEMENTED`**. Specifically: identification *machinery* is
implemented; **no device is identified** (empty registry); **no identification accuracy is claimed**
(TST-HW-003); `VERIFIED` confidence is declared and unreachable; the passive platform reads the signal
model anticipates are **not exercised** (no collector built, per the standing directive). The engine
correctly reports `Unknown` for every input the shipped registry can receive — that is the phase's
condition, stated rather than papered over.

## 3. Deferred physical-device verification (standing directive; ADR-P3-014)

`NOT RUN` (not `SKIPPED`): confirming a real handset's reported name/alias/type/class/cached-`getUuids()`
reads populate signals as the model predicts; that the cached UUID list is genuinely empty-until-queried on
device; that no passive read accidentally triggers air traffic; and any statement above `IMPLEMENTED`
about identification accuracy. These accumulate on the project-wide deferred-device list for the end of
the project and gate no phase boundary.

## 4. Findings closed inside the phase (not left as drift)

- **Blank-value fabrication defect, found by a test.** `IdentitySignal.observed` initially kept `""` as
  `rawValue` for a blank reported value; `blankAndMissingTextBecomeUnknownNotAFabricatedValue` caught it
  and the factory was fixed to null the value for anything not `OBSERVED` (prompt §6). Committed in
  `a5a177d`.
- **Scan tag contradicted the phase.** `DEVICE_DISCOVERY_SCAN` was `authorizedInPhase = 5` while Phase 5
  opens no scanner; moved to 6 and guarded (ADR-P5-012, RISK-094).
- **Over-strict test expectation corrected.** `aHighRuleBackedByOneKindIsCappedAndLandsAtLikely` first
  asserted a `MODERATE` single-kind result could not inform a protocol; that expectation was wrong (a
  genuine `MODERATE` manufacturer match *may* inform a later decision), so the assertion — not the code —
  was removed. No capability or protocol claim was loosened as a result.

## 5. Outstanding risks

RISK-086…097 in `risk-register.md`; the two that remain genuinely open for later phases are **RISK-095**
(the future collector must respect the passive boundary) and **RISK-097** (no hardware claim until the
deferred device workstream). The empty registry (RISK-086) is a designed state, not a defect, and its test
makes any quiet departure a build failure.

## 6. Phase 6 readiness

Phase 5 delivers the identification seam Phase 6 (transport selection) consumes: `IdentitySignal`s of
stated quality, a deterministic `DeviceFingerprint`, and an `IdentificationResult` carrying a capped
confidence and a `maySupportProtocolResolution` that is *advisory only*. Phase 6 must not read a model
match as transport compatibility (ADR-P5-004, PROTO-RESEARCH-006) — the type already refuses that reading.
Nothing here authorizes Phase 6; it waits for its own execution prompt (ADR-P0-009).
