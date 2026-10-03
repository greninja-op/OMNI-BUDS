# Phase 5 — Test Plan

**Phase:** 5 · **Owner:** orchestrator
**Document status:** the deterministic tests that hold Phase 5's contracts, all tier **T1** (JVM, no
radio, no scheduler — the engine is a pure function). Physical-device identification is **deferred**
(ADR-P3-014, standing "build the product before the phone" directive) and recorded `NOT RUN`, never
`SKIPPED` (TST-REC-005). No test in this phase asserts that a real device was identified; the shipped
registry is empty, so the honest result for every fixture is `Unknown` and the tests prove the machinery,
not a population.

**43 tests, all passing at `a5a177d`.** Mocked environment only (TST-MOCK-006): every rule is an inert
fixture with a fake citation, never a claim about a product (ADR-P5-006).

## 1. Signal validation — `IdentitySignalTest` (8) · REQ-001…005, REQ-027

| test | contract |
|---|---|
| `aReportedValueIsObservedAndKeepsItsText` | a clean value is `OBSERVED` and usable |
| `blankAndMissingTextBecomeUnknownNotAFabricatedValue` | null/empty/whitespace → `UNKNOWN`, `rawValue == null` (no `""` default) |
| `surroundingWhitespaceIsTrimmedButInnerTextIsPreserved` | trim only outer whitespace |
| `oversizedVendorTextIsRejectedAndItsPayloadDiscarded` | `> MAX_VALUE_LENGTH` → `INVALID`, text dropped |
| `aControlCharacterAnywhereInTheValueIsRejected` | embedded ISO control char → `INVALID` (prompt §15 malformed input) |
| `unavailableAndUnknownAreDifferentSentencesAboutDifferentThings` | `UNAVAILABLE` ≠ `UNKNOWN` ≠ absence |
| `derivedTextCarriesTheNormalizerSourceAndIsUsable` | a `DERIVED` signal sources from `NORMALIZED` and is usable |
| `aRejectedValueNeverSurvivesIntoReliability` | an unusable signal carries reliability `NONE` |

## 2. Normalization — `IdentityNormalizerTest` (7) · REQ-006

| test | contract |
|---|---|
| `trimsCollapsesInternalWhitespaceAndCaseFolds` | the three v1 operations and nothing else |
| `aModelNumberSuffixSurvivesBecauseItIsTheWholeDistinction` | `XM3` ≠ `XM4` (no digit stripping) |
| `brandAndModelTokenAreNotSplitEvenWhenCaseDiffers` | `LinkBuds` ≠ `LinkBuds S` |
| `punctuationIsKeptRatherThanStripped` | `Pro-500` keeps its `-` |
| `blankAndMissingTextNormalizeToNullNotToAnEmptyString` | blank → `null`, not `""` |
| `theVersionedFormTagsTheValueWithTheRuleVersionThatMadeIt` | `normalizeVersioned` publishes v1 |
| `equivalentInputsAcrossRoundsNormalizeIdentically` | case/whitespace variants collapse to one key |

## 3. Fingerprinting — within `IdentityEngineTest` and Phase 1's `DeviceFingerprintTest` · REQ-007…010

`identicalSignalsProduceAnIdenticalResultAndKey` and `theFingerprintCarriesOnlyDimensionsWithAProducer`
cover determinism and the deliberate-empty dimensions; `PhaseFiveSessionEnrichmentTest
.theFingerprintCarriesNoDimensionPhaseFiveCannotSee` covers the collector-facing shape. Byte-array/content
equality and the address-folding key are held by the inherited `DeviceFingerprintTest` (fingerprint version
changes and invalid input are covered there). `buildFingerprint` emits no transport/protocol candidate, so
the Phase 1 `NOTHING_KNOWN_KEY` path stays reachable for a pass that observed nothing.

## 4. Matching & confidence — `IdentityEngineTest` (12) · REQ-011…013, REQ-016…018

| test | outcome |
|---|---|
| `theProductionEmptyRegistryIdentifiesNothing` | `Unknown` — shipped state |
| `twoIndependentKindsAtHighProduceAnExactMatch` | `Exact`, `HIGH` (2 independent kinds) |
| `aHighRuleBackedByOneKindIsCappedAndLandsAtLikely` | rule asked `HIGH`, one kind → capped to `MODERATE`, `Likely` |
| `aModellessRuleEstablishesTheManufacturerOnly` | `ManufacturerOnly` — most a name phase can honestly reach |
| `disagreeingSurvivorsStayAmbiguousAndNameNoWinner` | `Ambiguous`, no single manufacturer field, `maySupportProtocolResolution == false` |
| `noRuleFiringIsUnknownNotAFailure` | `Unknown` |
| `onlyUnusableSignalsIsInsufficientEvidenceNotUnknown` | `InsufficientEvidence` |
| `malformedInputBecomesInvalidEvidenceAndNeverThrows` | `InvalidEvidence`, total function |
| `aPartialRuleMatchDoesNotFireOnTheSatisfiedHalfAlone` | a missing signal is not a negative; rule does not fire |
| `identicalSignalsProduceAnIdenticalResultAndKey` | determinism |
| `theFingerprintCarriesOnlyDimensionsWithAProducer` | only service-uuid + class populated |
| `everyOutcomeTravelsWithTheRegistryVersions` | version travel |

## 5. Registry discipline — `PhaseFiveRegistryTest` (6) · REQ-014…021, REQ-028

| test | contract |
|---|---|
| `theProductionRegistryShipsEmpty` | zero rules, only the built-in unknown manufacturer |
| `noMainSourceDeclaresARuleOrAMatchCondition` | source scan: no `src/main` constructs a rule (ADR-P5-006) |
| `aRuleBuiltFromInventedEvidenceIsRefused` | `ASSIGNED_INTERNALLY` → constructor failure |
| `aRuleAskingForVerifiedConfidenceIsRefused` | `VERIFIED` rule → constructor failure |
| `aRuleWithNoConditionsIsRefusedBecauseItWouldMatchEverything` | empty conditions → constructor failure |
| `verifiedIsDeclaredButUnreachableFromThisPhase` | `VERIFIED` needs `HARDWARE_VERIFIED`; `HIGH` reachable |

## 6. Session integration — `PhaseFiveSessionEnrichmentTest` (6) · REQ-022…024

| test | contract |
|---|---|
| `enrichmentAttachesTheProductIdentityAndLeavesTheSessionIntact` | attaches result; session id unchanged |
| `enrichmentNeverMovesTheConnectionOrBumpsTheStateRevision` | `connection` and `DeviceState.revision` untouched |
| `aMatchedManufacturerNeverOverwritesAReportedOne` | reported `DeviceIdentity` stays null; conclusion is separate |
| `enrichingAnUnknownSessionIdIsRefusedAndCreatesNothing` | unknown id → `Failure(INVALID_STATE)`, no session minted |
| `enrichmentPublishesItsEdgeWithoutAConnectionMove` | `SessionIdentityEnriched` emitted; no `SessionUpdated` |
| `theFingerprintCarriesNoDimensionPhaseFiveCannotSee` | the attached fingerprint has no forbidden dimension |

## 7. Scope & privacy — `PhaseFiveScopeTest` (4) · REQ-008, REQ-025, REQ-026, REQ-028, REQ-029

| test | contract |
|---|---|
| `nothingNewIsAuthorisedAtPhaseFive` | operations authorized at 5 == at 4; scan is false at 5 (ADR-P5-012) |
| `theIdentificationSourcesReachNoControlTransportOrPersistenceSeam` | no observer/transport/persistence/protocol reference |
| `identitySignalTypesDeclareNoAddressBearingMember` | no `val …Address…` in the identity types (SEC-ID-003) |
| `phaseFiveAddedNoNewSourceArea` | all identification code stays in `core.device` (ADR-P5-001) |

## 8. Inherited guards that must stay green

`DeviceFingerprintTest` (fingerprint determinism, byte equality, address folding),
`DependencyDirectionTest`/architecture checks (`:core` Android-independent; device stays layer 2, session
layer 3 importing down), and the Phase 2–4 scope tests. These are re-run in the full gate, not re-authored.

## 9. Deferred physical-device verification (`NOT RUN`)

No real handset was connected (standing directive). The following remain unexecuted and are listed in
`validation.md` as deferred, not skipped: identifying a manufacturer on a bonded device's alias/type/class
read; confirming the cached-`getUuids()` behaviour on a specific handset; and any claim above
`IMPLEMENTED` for identification accuracy. Each becomes a `HARDWARE_VERIFIED` obligation for the deferred
device workstream (TST-HW-003), never a present-tense claim.
