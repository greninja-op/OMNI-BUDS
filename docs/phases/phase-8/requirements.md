# Phase 8 — Requirements

**Phase:** 8 — Capability Discovery Engine · **Scope id:** `OB-P8`
**Document status:** authored against the committed tree at `968adb4` (`feat(capability): add the
deterministic, read-only capability discovery engine`). Every acceptance reference names a test that exists
and passes at that commit. `docs/phases/phase-8/decisions.md` holds ADR-P8-001 … 010; requirements cite them
by id and never restate them.

**The condition this phase ships in.** Phases 1 and 2 did not leave the capability model unbuilt —
`core.capability` holds `DeviceCapabilities`, `FeatureCapability`, `CapabilityDefinition`, `CoreFeature`,
`FeatureCategory`, `VendorExtension`, `VendorFeatureMetadata`; `core.state` holds `CapabilityState` and
`VerificationLevel`. Phase 8 reuses that vocabulary and adds only the discovery machinery — evidence
provenance, the one missing "right now" axis, dependency validation, a discovery lifecycle, a deterministic
snapshot and the engine — and ships **no protocol, no production discovery source and no capability claim
about any real device** (ADR-P8-010). The whole output ceiling is `IMPLEMENTED`/`LAB_TESTED`; discovery
never writes, never connects, and never turns "not discovered" into "unsupported".

Priority: `P0` blocks the boundary · `P1` in scope, required · `P2` in scope, not boundary-blocking.

## Reuse and boundary

### OB-P8-REQ-001 — Reuse the Phase 1 capability model; no `CapabilityId`, no second container, no four parallel enums
`FeatureId` is the capability identity, `FeatureCapability` the descriptor, `DeviceCapabilities` the
container, `CapabilityState` the support/access/durability ladder. Phase 8 adds no `CapabilityId`, no
`CapabilitySet`, and no `CapabilitySupport`/`Access`/`Verification` enums.
**Rationale.** Prompt §2 "reuse valid existing models / do not duplicate capability registries"; §7 "do not
create competing verification systems"; ADR-P8-001.
**Priority.** P0 · **Dependencies.** none · **Verification.** `DeviceCapabilitiesTest`, `FeatureCapabilityTest`,
`CoreFeatureTest` (Phase 1) still pass unchanged and now serve as Phase 8 regression guards;
`PhaseEightScopeTest` shows no parallel type was introduced.

### OB-P8-REQ-002 — The capability area imports nothing upward; the engine never imports the protocol layer
`core.capability` (L2) may depend on `common`/`state` (L0) and `platform` (L1) only; it imports no
`core.protocol` (L4), `core.session`/`persistence` (L3) nor same-layer `core.device`.
**Rationale.** Prompt §2; the layer map; ADR-P8-005; ADR-P6-005 analogue (transport resolver).
**Priority.** P0 · **Dependencies.** OB-P8-REQ-001 · **Verification.**
`PhaseEightScopeTest.theCapabilityAreaImportsNothingUpward` + `DependencyDirectionTest.coreAreasDependOnlyOnMoreFoundationalAreas`.

### OB-P8-REQ-003 — No production source implements the read-only discovery seam
A device/protocol-bound `CapabilityDiscoverySource` in `src/main` would speak for hardware that has never
been read; the seam is implemented only in test source.
**Rationale.** ADR-P8-010; ADR-P1-013/ADR-P7-010; prompt §18 "test-only scripted responses".
**Priority.** P0 · **Dependencies.** OB-P8-REQ-002 · **Verification.**
`PhaseEightScopeTest.noProductionSourceImplementsTheCapabilityDiscoverySource`.

## Support, access and availability kept distinct

### OB-P8-REQ-004 — Availability is a new orthogonal axis, carried beside the record, never inside it
`CapabilityAvailability{UNKNOWN, AVAILABLE, UNAVAILABLE, TEMPORARILY_UNAVAILABLE}` is added in the snapshot;
`FeatureCapability` is not widened.
**Rationale.** Prompt §7 "supported but temporarily unavailable"; ADR-P8-002.
**Priority.** P0 · **Dependencies.** OB-P8-REQ-001 · **Verification.** `CapabilitySnapshotTest.supportedAndUnavailableAreIndependentAxesAndNeitherMeansUnsupported`.

### OB-P8-REQ-005 — An unknown availability is not an unavailable one
`availabilityOf` returns `UNKNOWN` for a feature with no reading; `isAvailableNow()` is true only for
`AVAILABLE`/`TEMPORARILY_UNAVAILABLE`.
**Rationale.** Prompt §7; master §53 (unknown ≠ negative); ADR-P8-002.
**Priority.** P0 · **Dependencies.** OB-P8-REQ-004 · **Verification.**
`CapabilityEvidenceTest.unknownAvailabilityIsNotUnavailable` + `.availableAndTemporarilyUnavailableBothCountAsReachableNow` +
`CapabilitySnapshotTest.aFeatureAbsentFromAvailabilityReadsAsUnknownNotUnavailable`.

### OB-P8-REQ-006 — A feature that is supported-but-unavailable stays supported and never reads as unsupported
Availability gating does not erase support: a `SUPPORTED_PERSISTENT` feature with `UNAVAILABLE` availability
remains in `capabilities.controllable` and is absent from `capabilities.unsupported`.
**Rationale.** Prompt §7; §10; ADR-P8-002.
**Priority.** P0 · **Dependencies.** OB-P8-REQ-004 · **Verification.**
`CapabilitySnapshotTest.supportedAndUnavailableAreIndependentAxesAndNeitherMeansUnsupported` +
`CapabilityDiscoveryEngineTest.aSupportedFeatureThatIsTemporarilyUnavailableIsStillOfferableAsSupport`.

### OB-P8-REQ-007 — Read access and write access stay reused from `FeatureCapability` and state-cross-constrained
`readable`/`writable` come from the existing record whose `init` refuses any combination the state does not
license; Phase 8 does not re-decide access semantics.
**Rationale.** Prompt §7; ADR-P8-001; PROTO-CAP-005.
**Priority.** P1 · **Dependencies.** OB-P8-REQ-001 · **Verification.** `FeatureCapabilityTest` (Phase 1) as a
regression guard; engine readings carry a `FeatureCapability` produced through those guards.

## Evidence and verification

### OB-P8-REQ-008 — Every capability claim carries structured provenance
`CapabilityEvidence(kind, source, atEpochMillis, protocolId, protocolVersion, verification, detail, limitation)`
records where a claim came from and what it does not establish.
**Rationale.** Prompt §8 "preserve evidence provenance"; ADR-P8-006.
**Priority.** P0 · **Dependencies.** OB-P8-REQ-001 · **Verification.**
`CapabilityEvidenceTest.explicitDeviceResponseMayCarryHardwareVerification` + `.anUnknownRecordEstablishesNothingAndNamesNoProtocolBlankly`.

### OB-P8-REQ-009 — An evidence kind caps the rung it may claim; inference never claims device verification
`INFERRED_MODEL`/`UNKNOWN_OR_INCOMPLETE` evidence is construct-refused above `VerificationLevel.INFERRED`; a
blank source or blank protocol id is refused.
**Rationale.** Prompt §8 "do not treat an implemented parser as proof of hardware support"; ADR-P8-003.
**Priority.** P0 · **Dependencies.** OB-P8-REQ-008 · **Verification.**
`CapabilityEvidenceTest.inferredEvidenceCannotClaimMoreThanInferredVerification` + `.aBlankSourceOrBlankProtocolIdIsRefusedNotStoredAsMeaningless`.

### OB-P8-REQ-010 — Weak evidence never silently overwrites stronger, and disagreement is surfaced
Readings fold through `DeviceCapabilities.mergedWith` (the evidence ladder); a feature reported at genuinely
different states is kept and recorded as an `UnresolvedConflict(feature, kinds)`, never resolved by dropping
either; a single read never fabricates a conflict.
**Rationale.** Prompt §8; ADR-P8-006.
**Priority.** P0 · **Dependencies.** OB-P8-REQ-008 · **Verification.**
`CapabilityDiscoveryEngineTest.aWeakSecondReadCannotOverwriteAnEstablishedStrongerOne` +
`.twoDisagreeingReadsAreSurfacedAsAnUnresolvedConflictNotResolvedSilently` + `.aSingleReadNeverFabricatesAConflictOutOfItself`.

## The discovery engine

### OB-P8-REQ-011 — Discovery is read-only and never connects or writes
The engine calls only `attemptableFeatures()`/`read()`; there is no open, exchange, write, command or scan
path.
**Rationale.** Prompt §9 "do not automatically issue hardware-changing commands / do not automatically
connect"; §19; ADR-P8-005.
**Priority.** P0 · **Dependencies.** OB-P8-REQ-002 · **Verification.**
`PhaseEightScopeTest.theDiscoveryEngineOnlyReadsAndNeverWritesOrOpens`.

### OB-P8-REQ-012 — A single failed read is isolated and keeps that feature unknown, not unsupported
One feature's `Failure` becomes a `PartialFailure` and leaves it `CapabilityState.UNKNOWN`; unrelated
successes are kept.
**Rationale.** Prompt §10; master §53; ADR-P8-008.
**Priority.** P0 · **Dependencies.** OB-P8-REQ-011 · **Verification.**
`CapabilityDiscoveryEngineTest.aFailedReadIsIsolatedAndKeepsThatFeatureUnknownNotUnsupported`.

### OB-P8-REQ-013 — Partial, complete and failed are three distinguishable outcomes
Any failure with at least one established feature is `PARTIALLY_COMPLETE`; failures with no establishment at
all are `FAILED`; a clean run over conclusions is `COMPLETE`; a pass over an empty attemptable set is
`COMPLETE` but claims nothing.
**Rationale.** Prompt §10/§14; ADR-P8-008.
**Priority.** P0 · **Dependencies.** OB-P8-REQ-012 · **Verification.**
`CapabilityDiscoveryEngineTest.aPassThatEstablishesNothingFailsRatherThanReportsPartialSuccess` +
`.anEmptyAttemptableSetIsACompletePassThatClaimsNothing` + `.aSuccessfulPassFoldsEveryReadingIntoEstablishedState`.

### OB-P8-REQ-014 — Cancellation ends the pass and preserves what was gathered, inventing nothing
A `Cancelled` read returns a `CANCELLED` snapshot holding every earlier reading; later features are not read
and nothing is fabricated.
**Rationale.** Prompt §9/§14; ADR-P8-008.
**Priority.** P0 · **Dependencies.** OB-P8-REQ-012 · **Verification.**
`CapabilityDiscoveryEngineTest.cancellationEndsThePassAndPreservesEverythingGatheredBeforeIt`.

### OB-P8-REQ-015 — Malformed input cannot crash a pass
A read whose record describes a different feature than the one asked is refused as a `PartialFailure` with
`INVALID_STATE` (the engine's `MALFORMED_RESPONSE_CATEGORY`), not filed and not thrown.
**Rationale.** Prompt §17 "treat capability responses as untrusted / prevent malformed data from crashing
discovery"; ADR-P8-004.
**Priority.** P0 · **Dependencies.** OB-P8-REQ-011 · **Verification.**
`CapabilityDiscoveryEngineTest.aMislabeledReadBecomesAMalformedFailureAndCannotCrashThePass`.

### OB-P8-REQ-016 — Discovery results are deterministic
Features are read in sorted identity order and the snapshot's collections are ordered, so the same source
always yields the identical snapshot regardless of the source's listing order.
**Rationale.** Prompt §3/§9/§13 "deterministic discovery results / deterministic ordering".
**Priority.** P0 · **Dependencies.** OB-P8-REQ-012 · **Verification.**
`CapabilityDiscoveryEngineTest.featureOrderFromTheSourceDoesNotChangeTheSnapshot`.

### OB-P8-REQ-017 — No timestamp is invented; time arrives only through the injected clock
`discoveredAtEpochMillis` is null when no `TimeProvider` is supplied; the core reads no wall clock.
**Rationale.** ADR-P1-012; prompt §17; ADR-P0-016 (unknown, not zero).
**Priority.** P1 · **Dependencies.** OB-P8-REQ-016 · **Verification.**
`CapabilityDiscoveryEngineTest.aMissingClockYieldsNoTimestampRatherThanAnInventedOne` +
`PhaseEightScopeTest.theCapabilityAreaReadsNoWallClockOrAndroidClass`.

## Dependencies and constraints

### OB-P8-REQ-018 — Dependencies are per-protocol/firmware data, never catalogue facts
`CapabilityDependency(feature, requires, protocolId?, firmwareConstraint?)` lives in discovery, not on
`CapabilityDefinition` (which is device-agnostic); a self-edge is refused at construction.
**Rationale.** Prompt §11 "these dependencies do not universally apply"; ADR-P8-007.
**Priority.** P0 · **Dependencies.** OB-P8-REQ-001 · **Verification.**
`CapabilityDependencyTest.aFeatureMayNotDependOnItself` + `.aVendorPrerequisiteIsResolvedLikeAnyOther`.

### OB-P8-REQ-019 — A missing prerequisite is explicit; an unknown prerequisite is not a negative
A positively `UNSUPPORTED` prerequisite yields `MISSING_PREREQUISITE`; an unknown one yields
`UNKNOWN_PREREQUISITE` and does not block; the dependent's availability is set `UNAVAILABLE` only for a
missing/cyclic edge, and its support is never erased.
**Rationale.** Prompt §11; master §53; ADR-P8-007.
**Priority.** P0 · **Dependencies.** OB-P8-REQ-018 · **Verification.**
`CapabilityDependencyTest.anUnknownPrerequisiteLeavesTheDependentUnknownNotBlocked` +
`.aPositivelyUnsupportedPrerequisiteBlocksTheDependent` +
`CapabilityDiscoveryEngineTest.aBlockedFeatureBecomesUnavailableButStaysSupported`.

### OB-P8-REQ-020 — Cycles are detected and reported, never silently broken, and never auto-enable a prerequisite
`DependencyValidator` reports cycles and blocks their members; it never infers a prerequisite's support from
a dependent and never enables one.
**Rationale.** Prompt §11 "detect dependency cycles / do not infer support for a prerequisite / must not
automatically enable prerequisite features"; ADR-P8-007.
**Priority.** P0 · **Dependencies.** OB-P8-REQ-018 · **Verification.**
`CapabilityDependencyTest.aDependencyCycleIsReportedAndItsMembersAreNotOffered` +
`.cycleDiscoveryIsDeterministicForTheSameGraph` + `.validatingNeverEnablesAPrerequisiteNorMutatesCapabilities`.

## Snapshot, lifecycle and vendor

### OB-P8-REQ-021 — The snapshot is immutable, deterministically ordered, wraps `DeviceCapabilities`, and is schema-versioned
`CapabilitySnapshot` carries the `DeviceCapabilities` plus discovery metadata and a positive `schemaVersion`
(=1); the constructor refuses a `COMPLETE` snapshot with failures and a `PARTIALLY_COMPLETE` snapshot without
them; `empty()` is a legal `NOT_STARTED` value.
**Rationale.** Prompt §13/§14; ADR-P8-008.
**Priority.** P0 · **Dependencies.** OB-P8-REQ-013 · **Verification.**
`CapabilitySnapshotTest.aCompleteSnapshotMayNotCarryPartialFailures` +
`.aPartialSnapshotMustCarryTheFailureThatMadeItPartial` + `.anEmptySnapshotIsANotStartedPassThatEstablishesNothing` +
`.theSchemaVersionIsPositiveAndPinned`.

### OB-P8-REQ-022 — The discovery lifecycle is a fourth axis with locked invariants
`DiscoveryState`/`DiscoveryStateTransitions`: completion is reachable only through `DISCOVERING`, the
failure/cancel sinks are always reachable from in-flight states, a terminal pass does not revive, and only
`COMPLETE`/`PARTIALLY_COMPLETE` owe mergeable evidence.
**Rationale.** Prompt §14; ADR-P8-008.
**Priority.** P0 · **Dependencies.** OB-P8-REQ-021 · **Verification.**
`DiscoveryStateTest.completionIsReachableOnlyThroughDiscovering` +
`.failureAndCancellationAreReachableFromEveryInFlightState` + `.terminalStatesHaveNoOutgoingForwardEdgeAndDoNotRevive` +
`.onlyCompletedAndPartialPassesOweMergableEvidence`.

### OB-P8-REQ-023 — Vendor extensions are reused and preserved, and no vendor command is implemented
Namespaced `FeatureId`s and `VendorExtension`/`VendorFeatureMetadata` are reused; an untypable extension is
preserved rather than coerced into a common feature; Phase 8 implements no vendor packets.
**Rationale.** Prompt §12/§19; ADR-P8-009.
**Priority.** P1 · **Dependencies.** OB-P8-REQ-001 · **Verification.** `VendorExtensionTest` (Phase 1) +
`DeviceCapabilitiesTest.anUnregisteredVendorFeatureIsUnknownRatherThanUnsupported`;
`PhaseEightScopeTest` shows no vendor command surface.

### OB-P8-REQ-024 — Discovery establishes no capability without a source reading
The engine starts from the empty container and files nothing it did not read; an unknown subject answers
`UNKNOWN` everywhere and no control is offered.
**Rationale.** Prompt §10 "avoid replacing known information with fabricated defaults"; §16; ADR-P8-010.
**Priority.** P0 · **Dependencies.** OB-P8-REQ-012 · **Verification.**
`DeviceCapabilitiesTest.theCoreCatalogueAnsweredAgainstAnEmptySnapshotIsUnknownEverywhere` (reused) +
`CapabilityDiscoveryEngineTest.anEmptyAttemptableSetIsACompletePassThatClaimsNothing`.

## Scope and deferral

### OB-P8-REQ-025 — No capability UI is built; the snapshot is the forward contract
Phase 8 produces a capability-driven snapshot a later UI can render (control visible only when supported,
unknown never shown as confirmed), but ships no screen, widget or Quick-Settings tile.
**Rationale.** Prompt §16/§19; ADR-P8-008.
**Priority.** P1 · **Dependencies.** OB-P8-REQ-021 · **Verification.** `DependencyDirectionTest.neitherModuleReferencesUiFrameworks`
(unchanged) — no UI token appears anywhere.

### OB-P8-REQ-026 — Nothing is persisted; the snapshot is a value a caller holds
Phase 8 introduces no storage; `CapabilitySnapshot` is not written to any store.
**Rationale.** Prompt §13/§19; ADR-P8-008.
**Priority.** P2 · **Dependencies.** OB-P8-REQ-021 · **Verification.** no new persistence call site; the
`persistence` area is untouched (`PhaseEightScopeTest` imports nothing from it).

### OB-P8-REQ-027 — No actual ANC, EQ, gesture, battery or firmware control is implemented
Phase 8 builds the discovery foundation only; the hardware features named in prompt §1 are vocabulary, not
behaviour, and none is commanded here.
**Rationale.** Prompt §3/§19; ADR-P8-005.
**Priority.** P0 · **Dependencies.** OB-P8-REQ-011 · **Verification.**
`PhaseEightScopeTest.theDiscoveryEngineOnlyReadsAndNeverWritesOrOpens` + `FeatureCapabilityTest` (no write path).

### OB-P8-REQ-028 — The engine ships with no protocol and the hardware ceiling is explicitly deferred
Driven by the empty protocol state the engine answers all-`UNKNOWN`; every capability claim caps at
`IMPLEMENTED`/`LAB_TESTED`; physical-device discovery is `NOT RUN`, never reported as working.
**Rationale.** ADR-P1-013/ADR-P5-006/ADR-P7-010; the standing "build the product, skip the phone" directive;
prompt §22.
**Priority.** P0 · **Dependencies.** OB-P8-REQ-003 · **Verification.** all Phase 8 tests run on the JVM; the
`validation.md` `NOT RUN` list names the deferred device work; no test asserts a real device.
