# Phase 5 — Requirements

**Phase:** 5 — Device Fingerprinting & Identification · **Scope id:** `OB-P5`
**Document status:** authored against the committed tree at `a5a177d` (`feat(core): identify devices
from evidence and let an unidentified one stay unknown`), the phase's only code commit. No `.kt` file
was edited while these lines were written; every acceptance-criteria reference below names a test that
exists in that commit. `docs/phases/phase-5/decisions.md` holds ADR-P5-001 … ADR-P5-012; requirements
cite them by id and never restate them.

**The condition this phase ships in, stated once and referred to everywhere after.** The identification
registry is empty (ADR-P5-006), so no requirement below is satisfied by a real device being named. Each
is satisfied by the *mechanism* that would name a device if a rule with real evidence existed, and by a
test that proves the mechanism refuses to name one when it does not. That is the honest reading of
prompt §5's "identify manufacturers when evidence is sufficient" when the phase has collected no
sufficient evidence: the engine answers correctly, and the correct answer for every device is currently
`Unknown`.

Priority key: `P0` blocks the phase boundary · `P1` in scope and required for acceptance · `P2` in scope,
verifiable but not boundary-blocking.

---

## Identity signals

### OB-P5-REQ-001 — Identity signals are modelled as typed evidence with a stated quality
Every unit of identity evidence is an `IdentitySignal` carrying a `kind`, a `quality`, an optional value,
a `source` and a `reliability`. Prompt §6 requires this and forbids a value-less "present/absent"
boolean.
**Rationale.** A signal that cannot say *how* it is known is indistinguishable from a guess, and prompt
§9's first named error is reading a weak signal as a strong one.
**Priority.** P0 · **Dependencies.** none · **Verification.** `IdentitySignalTest` (7 cases).

### OB-P5-REQ-002 — A signal distinguishes observed, derived, inferred, unknown, unavailable and invalid
`SignalQuality` has exactly those six members and no collapse between them (`IdentitySignal.kt`).
`UNAVAILABLE` is about the phase, `UNKNOWN` is about the round, `INVALID` is a rejected value; none is
read as a negative about the device.
**Rationale.** ADR-P0-016's three-tier unknown widened to identity (ADR-P5-003); prompt §6 lists the six.
**Priority.** P0 · **Dependencies.** OB-P5-REQ-001 · **Verification.**
`IdentitySignalTest.unavailableAndUnknownAreDifferentSentencesAboutDifferentThings`,
`.oversizedVendorTextIsRejectedAndItsPayloadDiscarded`, `.aControlCharacterAnywhereInTheValueIsRejected`.

### OB-P5-REQ-003 — Missing information is never a fabricated default
A blank or whitespace-only reported value yields quality `UNKNOWN` with a null value, never `""`; an
over-long or control-character-bearing value yields `INVALID` with the text discarded.
**Rationale.** Prompt §6's "do not represent missing information as a fabricated default" and §15's
"validate lengths and formats". The factory is the boundary where untrusted platform text stops being
trusted input.
**Priority.** P0 · **Dependencies.** OB-P5-REQ-001 · **Verification.**
`IdentitySignalTest.blankAndMissingTextBecomeUnknownNotAFabricatedValue`,
`.surroundingWhitespaceIsTrimmedButInnerTextIsPreserved`.

### OB-P5-REQ-004 — An absent signal is not negative evidence about the device
`MANUFACTURER_DATA` and `CHARACTERISTIC_UUID` carry kinds and answer `UNAVAILABLE`; an empty cached
service list answers `UNKNOWN`. Neither satisfies nor refutes a rule; the engine treats them as
insufficient, not as absence-of-capability.
**Rationale.** ADR-P5-007, prompt §6's closing rule and §13's unknown-handling.
**Priority.** P0 · **Dependencies.** OB-P5-REQ-002 · **Verification.**
`IdentityEngineTest.onlyUnusableSignalsIsInsufficientEvidenceNotUnknown`,
`.aPartialRuleMatchDoesNotFireOnTheSatisfiedHalfAlone`.

### OB-P5-REQ-005 — Signal sources separate platform reports, pairing records, normalization and observation
`SignalSource` names `PLATFORM`, `PAIRING_RECORD`, `NORMALIZED`, `OBSERVATION`; a user alias and a
reported name are both text but different sources and different weight.
**Rationale.** Prompt §6's "signal source"; without it Settings text becomes vendor evidence (§9 example 1).
**Priority.** P1 · **Dependencies.** OB-P5-REQ-001 · **Verification.**
`IdentitySignalTest.derivedTextCarriesTheNormalizerSourceAndIsUsable`.

---

## Normalization and fingerprinting

### OB-P5-REQ-006 — Normalization is trim, whitespace-collapse and case-fold only, and is versioned
`IdentityNormalizer.normalize` performs exactly those three locale-independent operations and publishes
`NORMALIZATION_VERSION = 1`; every derived value travels with the version that made it.
**Rationale.** Prompt §7's "must not destroy meaningful identity distinctions"; ADR-P5-005 refuses
transliteration, brand-stripping and camelCase-splitting because each produces a match a human would
reject. `:core` forbids `java.text.Normalizer`, so accent folding is not even available.
**Priority.** P0 · **Dependencies.** none · **Verification.** `IdentityNormalizerTest` (7 cases),
including the `XM3`-vs-`XM4` and `LinkBuds`-vs-`LinkBuds S` distinctions.

### OB-P5-REQ-007 — Fingerprint generation is deterministic for equivalent inputs
`IdentityEngine.buildFingerprint` folds the same signal set to the same `DeviceFingerprint`, whose
`identityKey()` is order-stable over the structural dimensions.
**Rationale.** Prompt §3's "stable fingerprint results" and §7's determinism requirement.
**Priority.** P0 · **Dependencies.** OB-P5-REQ-006 · **Verification.**
`IdentityEngineTest.identicalSignalsProduceAnIdenticalResultAndKey`, plus Phase 1's
`DeviceFingerprintTest` for the key.

### OB-P5-REQ-008 — Phase 5 consumes Phase 1's fingerprint model rather than a parallel type
The engine produces `com.omnibuds.core.device.DeviceFingerprint`; no second fingerprint type and no new
source area is introduced. Identification types live in `core.device` (layer 2) as new files.
**Rationale.** ADR-P5-001; prompt §2's "do not create duplicate identity models".
**Priority.** P0 · **Dependencies.** OB-P5-REQ-007 · **Verification.**
`PhaseFiveScopeTest.phaseFiveAddedNoNewSourceArea`.

### OB-P5-REQ-009 — The fingerprint fills only dimensions with a producer and leaves the rest explicitly empty
Service UUIDs (from cache) and device class are populated; manufacturer data, characteristic UUIDs,
transport candidates and protocol candidates stay empty because this phase produces none, and
`isEntirelyUnobserved` keeps "nothing observed" distinct from "sparse device".
**Rationale.** ADR-P5-007; §17's prohibition on GATT discovery; the decision not to promote a passive
device-type read into a transport claim.
**Priority.** P1 · **Dependencies.** OB-P5-REQ-008 · **Verification.**
`IdentityEngineTest.theFingerprintCarriesOnlyDimensionsWithAProducer`,
`PhaseFiveSessionEnrichmentTest.theFingerprintCarriesNoDimensionPhaseFiveCannotSee`.

### OB-P5-REQ-010 — Byte-array equality is correct
Manufacturer-data and service values enter the key as normalized tokens, so two observations of equal
content produce equal fingerprints without reference to array identity.
**Rationale.** Prompt §7's explicit byte-array-equality requirement; §19's fingerprint matrix.
**Priority.** P1 · **Dependencies.** OB-P5-REQ-007 · **Verification.** `DeviceFingerprintTest` (Phase 1),
exercised through `buildFingerprint` in `IdentityEngineTest`.

---

## Matching

### OB-P5-REQ-011 — Model matching is rule-based and documented, not heuristic
An `IdentificationRule` declares named `MatchCondition`s over signal kinds; the engine fires a rule only
when every condition has a usable signal that satisfies it.
**Rationale.** Prompt §11's field list and §3's "matching known device models using explicit rules";
closures could not be audited for whether the evidence is real.
**Priority.** P0 · **Dependencies.** OB-P5-REQ-001, OB-P5-REQ-006 · **Verification.** `IdentityEngineTest`
(12 cases).

### OB-P5-REQ-012 — Exact, likely, manufacturer-only, unknown, ambiguous, insufficient and invalid are seven distinct outcomes
`IdentificationResult` is a sealed set of seven; each carries status, matched rule ids, evidence,
confidence, rule-set/registry version and known limitations.
**Rationale.** Prompt §8's outcome set and its "every result must contain" field list.
**Priority.** P0 · **Dependencies.** OB-P5-REQ-011 · **Verification.**
`IdentityEngineTest.theProductionEmptyRegistryIdentifiesNothing`, `.twoIndependentKindsAtHighProduceAnExactMatch`,
`.aModellessRuleEstablishesTheManufacturerOnly`, `.noRuleFiringIsUnknownNotAFailure`,
`.onlyUnusableSignalsIsInsufficientEvidenceNotUnknown`, `.malformedInputBecomesInvalidEvidenceAndNeverThrows`.

### OB-P5-REQ-013 — Ambiguous identities stay ambiguous and name no winner
When survivors point at distinct manufacturer/model targets the result is `Ambiguous` carrying candidates;
the type exposes no single `manufacturer`/`model` field, so a caller cannot take an arbitrary winner.
**Rationale.** Prompt §8's "do not convert an ambiguous result into an arbitrary winner"; ADR-P5-008.
**Priority.** P0 · **Dependencies.** OB-P5-REQ-012 · **Verification.**
`IdentityEngineTest.disagreeingSurvivorsStayAmbiguousAndNameNoWinner`.

### OB-P5-REQ-014 — Manufacturer normalization is structured and evidence-gated
`ManufacturerIdentity` holds a canonical slug, a display name and normalized aliases; the slug is a
trimmed lower-case id by construction, and a distinct `DeviceIdentity` (reported text) stays separate
from it.
**Rationale.** Prompt §10's field list; a name containing a brand is not proof of a manufacturer, so the
canonical id exists only inside a registry entry, not inferred from free text.
**Priority.** P1 · **Dependencies.** OB-P5-REQ-006 · **Verification.**
`PhaseFiveRegistryTest`, `ManufacturerIdentity` construction guard exercised via `IdentityEngineTest`.

### OB-P5-REQ-015 — Matching does not merge two active devices incorrectly
Two devices that share a display name remain two signals-sets and two identifications; ambiguity within
one device is a distinct concept from two devices colliding, and attribution to a device is not this
phase's job.
**Rationale.** Prompt §14's matching requirement and §3's "multiple devices with similar names".
**Priority.** P1 · **Dependencies.** OB-P5-REQ-013 · **Verification.** Phase 4's
`DeviceSessionEngineTest.twoDevicesCarryingTheSameNameStayTwoSessions` (session layer),
`PhaseFiveSessionEnrichmentTest` (identity is attached per session id, never re-derived from a name).

---

## Confidence

### OB-P5-REQ-016 — Confidence categories are categorical and have written evidence requirements
`IdentificationConfidence` is `VERIFIED / HIGH / MODERATE / LOW / UNKNOWN`, each with
`independentKindsRequired`, a `minimumEvidence` rung and a reachability flag. No numeric score exists.
**Rationale.** Prompt §9's "defined evidence requirements" and its ban on arbitrary percentages; averaging
two signals lets a weak one borrow the strong one's confidence.
**Priority.** P0 · **Dependencies.** OB-P5-REQ-012 · **Verification.**
`PhaseFiveRegistryTest.verifiedIsDeclaredButUnreachableFromThisPhase`, `IdentityEngineTest` capping cases.

### OB-P5-REQ-017 — Confidence is computed from evidence, not copied from the rule
The engine grants a rule's requested rung only when the required number of *independent* signal kinds
corroborate; a rule asking `HIGH` on one kind lands at `MODERATE` and the downgrade is reported.
**Rationale.** ADR-P5-004; prompt §9's third example (a manufacturer id proves manufacturer, not model).
**Priority.** P0 · **Dependencies.** OB-P5-REQ-016 · **Verification.**
`IdentityEngineTest.aHighRuleBackedByOneKindIsCappedAndLandsAtLikely`,
`.twoIndependentKindsAtHighProduceAnExactMatch`.

### OB-P5-REQ-018 — A device name alone never establishes verified model identity
`VERIFIED` requires `HARDWARE_VERIFIED` evidence no passive read produces and is unreachable from Phase 5
code; a name is one kind and caps at `MODERATE`.
**Rationale.** Prompt §3's closing rule, §9's first example, ADR-P5-004; TST-HW-003 caps hardware claims.
**Priority.** P0 · **Dependencies.** OB-P5-REQ-017 · **Verification.**
`PhaseFiveRegistryTest.aRuleAskingForVerifiedConfidenceIsRefused`,
`PhaseFiveRegistryTest.verifiedIsDeclaredButUnreachableFromThisPhase`.

---

## Registry

### OB-P5-REQ-019 — The global identification registry is versioned and separate from user and session data
`DeviceIdentityRegistry` carries `registryVersion` and `ruleSetVersion`, holds manufacturers and rules,
and lives in `core.device`; it is not the saved-device store and not session state.
**Rationale.** Prompt §12's three-way separation; §18's "registry versioning" documentation requirement.
**Priority.** P0 · **Dependencies.** OB-P5-REQ-014 · **Verification.**
`IdentityEngineTest.everyOutcomeTravelsWithTheRegistryVersions`,
`PhaseFiveScopeTest.theIdentificationSourcesReachNoControlTransportOrPersistenceSeam` (no `persistence` import).

### OB-P5-REQ-020 — The production registry ships empty and no invented signature exists in main sources
`DeviceIdentityRegistry.empty()` returns no rules; a rule may not carry `ASSIGNED_INTERNALLY` evidence,
empty conditions, `VERIFIED` confidence or a blank citation, and no `src/main` file constructs a rule.
**Rationale.** ADR-P5-006 with ADR-P1-013 precedent; prompt §11's "do not invent real device signatures /
do not add models to grow the registry".
**Priority.** P0 · **Dependencies.** OB-P5-REQ-019 · **Verification.**
`PhaseFiveRegistryTest.theProductionRegistryShipsEmpty`, `.noMainSourceDeclaresARuleOrAMatchCondition`,
`.aRuleBuiltFromInventedEvidenceIsRefused`, `.aRuleWithNoConditionsIsRefusedBecauseItWouldMatchEverything`.

### OB-P5-REQ-021 — Rules are extensible without shipping device history
`builtIn(...)` takes rules as parameters (tests, and the phase that lands real evidence); the empty
registry is an explicit `empty()` call, never a forgotten fallback, and the unknown manufacturer is
built in and cannot be redeclared.
**Rationale.** Prompt §4 Agent E's "extensibility plan"; §11's rule-version requirement.
**Priority.** P1 · **Dependencies.** OB-P5-REQ-019 · **Verification.** `PhaseFiveRegistryTest`,
`IdentityEngineTest` builds registries through `builtIn`.

---

## Session integration

### OB-P5-REQ-022 — Identity enrichment never recreates a session and never moves the connection
`DeviceSessionEngine.enrichIdentity(sessionId, fingerprint, result)` attaches a product identity to an
existing session by its engine id; it cannot mint or end a session, change `connection`, or move
`DeviceState.revision`. An unknown id is refused, not created.
**Rationale.** ADR-P5-009; prompt §14's five integration requirements.
**Priority.** P0 · **Dependencies.** OB-P5-REQ-012 · **Verification.**
`PhaseFiveSessionEnrichmentTest` (6 cases: attach, no-connection-move, refusal-does-not-create, event,
manufacturer-not-overwritten, fingerprint-dimensions).

### OB-P5-REQ-023 — Session identity and product identity remain separate concepts
The reported `DeviceIdentity` and the inferred `IdentificationResult` are sibling fields on
`TrackedDeviceSession`; a matched manufacturer never writes into the reported one, and a changed display
name re-attributes nothing.
**Rationale.** Prompt §14's "session identity and product identity must remain separate concepts";
§7's "changed display name must not create a new physical device".
**Priority.** P0 · **Dependencies.** OB-P5-REQ-022 · **Verification.**
`PhaseFiveSessionEnrichmentTest.aMatchedManufacturerNeverOverwritesAReportedOne`,
`.enrichmentAttachesTheProductIdentityAndLeavesTheSessionIntact`.

### OB-P5-REQ-024 — An enrichment publishes an edge without a connection transition
The engine emits `SessionIdentityEnriched` — the eighth event, carrying only session id, confidence,
identification-boolean and time — and never a `SessionUpdated`, because identification is not a state move.
**Rationale.** ADR-P5-009; ADR-P4-008's "no restatement"; prompt §8's "authoritative reactive state".
**Priority.** P1 · **Dependencies.** OB-P5-REQ-022 · **Verification.**
`PhaseFiveSessionEnrichmentTest.enrichmentPublishesItsEdgeWithoutAConnectionMove`.

---

## Privacy and security

### OB-P5-REQ-025 — No Bluetooth address is an identity signal and none is printed
`IdentitySignalKind` has no address member and no signal text carries one; attribution stays Phase 3's
`DeviceObservationKey`. Identity types declare no address-bearing field.
**Rationale.** ADR-P5-010, ADR-P3-010; SEC-ID-003/004.
**Priority.** P0 · **Dependencies.** OB-P5-REQ-001 · **Verification.**
`PhaseFiveScopeTest.identitySignalTypesDeclareNoAddressBearingMember`,
`PhaseFiveSessionEnrichmentTest` (session id carries no device content, asserted in Phase 4).

### OB-P5-REQ-026 — Identification is local and deterministic with no network dependency
No signal source, registry or engine step reaches a network; identification is pure synchronous logic over
a versioned in-process registry.
**Rationale.** Prompt §15's "no cloud-based identification / keep identification deterministic and local";
ADR-P5-011.
**Priority.** P0 · **Dependencies.** OB-P5-REQ-011 · **Verification.**
`PhaseFiveScopeTest.theIdentificationSourcesReachNoControlTransportOrPersistenceSeam`; the `:core`
Android-independence architecture test.

### OB-P5-REQ-027 — Malformed metadata cannot crash or mislead the matcher
Oversized and control-character-bearing values become `INVALID` with text discarded, and the engine
returns `InvalidEvidence` rather than throwing on any input.
**Rationale.** Prompt §15's "prevent malformed metadata from crashing the matching engine"; §19's malformed
signal matrix.
**Priority.** P0 · **Dependencies.** OB-P5-REQ-003, OB-P5-REQ-012 · **Verification.**
`IdentityEngineTest.malformedInputBecomesInvalidEvidenceAndNeverThrows`,
`IdentitySignalTest.aControlCharacterAnywhereInTheValueIsRejected`.

### OB-P5-REQ-028 — Nothing user-facing is stored; unidentified devices are not persisted
Phase 5 persists nothing; identity data is application knowledge (the empty registry) and per-session
enrichment, never a saved-device record or a history of every unseen device.
**Rationale.** Prompt §12's "do not permanently store every unidentified device"; ADR-P0-004 (saving is an
explicit user action); §17 forbids persistent saved-device management here.
**Priority.** P0 · **Dependencies.** OB-P5-REQ-019 · **Verification.**
`PhaseFiveScopeTest` (no `persistence` import in identity sources); the empty production registry.

---

## Scope boundary

### OB-P5-REQ-029 — No forbidden capability is implemented and no transport operation is newly authorized
Phase 5 opens no scanner, no GATT, no RFCOMM; identifies but resolves no protocol; ships no UI, ANC/EQ/
battery/firmware, or saved-device management. The discovery-scan tag is asserted false at Phase 5.
**Rationale.** Prompt §17's list; ADR-P5-007 and ADR-P5-012.
**Priority.** P0 · **Dependencies.** all · **Verification.**
`PhaseFiveScopeTest.nothingNewIsAuthorisedAtPhaseFive`,
`.theIdentificationSourcesReachNoControlTransportOrPersistenceSeam`; Phase 4's
`PhaseFourScopeTest.thePhaseThatFollowsIsStillNotAuthorisedToReachAnythingThisOneInvented`.

### OB-P5-REQ-030 — Identification never claims a device is supported
`maySupportProtocolResolution` is a per-rung statement that a result *may be read as evidence toward* a
later protocol decision; no Phase 5 type or result asserts capability, and protocol resolution itself is
not implemented (prompt §5 names it future work).
**Rationale.** SEC-PRIV-008's ban on "OmniBuds supports Sony"; prompt §17's closing line; ADR-P5-001.
**Priority.** P0 · **Dependencies.** OB-P5-REQ-016 · **Verification.**
`IdentificationResult.maySupportProtocolResolution` is false for five of seven outcomes
(`IdentityEngineTest.disagreeingSurvivorsStayAmbiguousAndNameNoWinner`), and no capability type is
reached by any Phase 5 source (`PhaseFiveScopeTest`).
