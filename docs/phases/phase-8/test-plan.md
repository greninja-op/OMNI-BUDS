# Phase 8 — Test Plan

**Phase:** 8 — Capability Discovery Engine · **Tree:** `968adb4`
Primary verification is JVM unit tests plus architecture guards (prompt §18). Scripted protocol responses
appear **only** in test source (`CapabilityDiscoveryEngineTest`'s `ScriptedSource`) and are never presented as
real device support. Tier key: **T1** = the test proves the code's own logic; **T3** = `NOT RUN`, needs a
device.

## 1. Capability model — support / access / availability / verification / identity

| target | test | tier |
|---|---|---|
| `UNKNOWN` availability ≠ `UNAVAILABLE`; `isAvailableNow` truth table | `CapabilityEvidenceTest.unknownAvailabilityIsNotUnavailable`, `.availableAndTemporarilyUnavailableBothCountAsReachableNow` | T1 |
| Access (`readable`/`writable`) stays state-cross-constrained (no fork) | `FeatureCapabilityTest` (Phase 1, reused as a guard) | T1 |
| Support ladder + deterministic merge unchanged | `DeviceCapabilitiesTest` (Phase 1, reused) | T1 |
| Stable identity; vendor feature unknown not unsupported | `FeatureIdTest`, `DeviceCapabilitiesTest.anUnregisteredVendorFeatureIsUnknownRatherThanUnsupported` (reused) | T1 |
| Verification ceiling: inference cannot claim device verification | `CapabilityEvidenceTest.inferredEvidenceCannotClaimMoreThanInferredVerification` | T1 |
| Blank source/protocol refused; `unknown` record establishes nothing | `CapabilityEvidenceTest.aBlankSourceOrBlankProtocolIdIsRefusedNotStoredAsMeaningless`, `.anUnknownRecordEstablishesNothingAndNamesNoProtocolBlankly` | T1 |

## 2. Discovery engine

| scenario | test | expected |
|---|---|---|
| Successful discovery | `aSuccessfulPassFoldsEveryReadingIntoEstablishedState` | `COMPLETE`, both records established |
| Partial discovery (one fail, one ok) | `aFailedReadIsIsolatedAndKeepsThatFeatureUnknownNotUnsupported` | `PARTIALLY_COMPLETE`, failed feature `UNKNOWN` (not `UNSUPPORTED`) |
| Unsupported-vs-failed kept apart | same test's `!in unsupported` assertion | failed read is never a positive absence |
| All reads fail | `aPassThatEstablishesNothingFailsRatherThanReportsPartialSuccess` | `FAILED`, nothing established |
| Protocol unresolved | `anUnresolvedProtocolSurfacesThroughAnExistingCategoryNotANewOne` | `FAILED`, reason `PROTOCOL_MISMATCH` |
| Session unavailable | `aPassThatEstablishesNothingFails...` (all `CONNECTION_UNAVAILABLE`) | `FAILED` |
| Cancellation | `cancellationEndsThePassAndPreservesEverythingGatheredBeforeIt` | `CANCELLED`, earlier reading kept, later never read |
| Empty evidence / empty attemptable set | `anEmptyAttemptableSetIsACompletePassThatClaimsNothing` | `COMPLETE`, zero capabilities, zero evidence |
| Malformed response (mislabeled record) | `aMislabeledReadBecomesAMalformedFailureAndCannotCrashThePass` | refused as `PartialFailure(INVALID_STATE)`, no crash |
| Determinism | `featureOrderFromTheSourceDoesNotChangeTheSnapshot` | identical snapshot for reordered source |
| No fabricated conflict from one read | `aSingleReadNeverFabricatesAConflictOutOfItself` | conflicts empty |

## 3. Evidence evaluation

| target | test | tier |
|---|---|---|
| Explicit-support vs inferred evidence kept distinct in kind | `CapabilityEvidenceTest` (device-response vs inferred ceiling) | T1 |
| Weak evidence cannot overwrite strong (no silent downgrade) | `CapabilityDiscoveryEngineTest.aWeakSecondReadCannotOverwriteAnEstablishedStrongerOne` | T1 |
| Contradictory evidence surfaced, not resolved | `.twoDisagreeingReadsAreSurfacedAsAnUnresolvedConflictNotResolvedSilently` | T1 |
| Provenance carried on every claim | `.aSuccessfulPassFoldsEveryReadingIntoEstablishedState` (evidence count) | T1 |
| Verification-level constraint on persistence claims | `FeatureCapabilityTest` (Phase 1, floors `SUPPORTED_PERSISTENT`/`PERSISTENCE_VERIFIED`) | T1 |
| Real device evidence | — | **T3 `NOT RUN`** (ADR-P8-010) |

## 4. Dependencies

| target | test | expected |
|---|---|---|
| Valid dependency | `CapabilityDependencyTest.anEstablishedPrerequisiteSatisfiesTheEdge` | `SATISFIED` |
| Missing prerequisite (positive unsupported) | `.aPositivelyUnsupportedPrerequisiteBlocksTheDependent` | `MISSING_PREREQUISITE`, blocked |
| Unknown prerequisite not a negative | `.anUnknownPrerequisiteLeavesTheDependentUnknownNotBlocked` | `UNKNOWN_PREREQUISITE`, **not** blocked |
| Cycle detection | `.aDependencyCycleIsReportedAndItsMembersAreNotOffered` | `CYCLE`, members blocked, cycle reported once |
| Determinism of cycle finding | `.cycleDiscoveryIsDeterministicForTheSameGraph` | same cycles for reordered input |
| Never auto-enables a prerequisite | `.validatingNeverEnablesAPrerequisiteNorMutatesCapabilities` | prerequisite stays `UNKNOWN` |
| Self-edge refused | `.aFeatureMayNotDependOnItself` | `IllegalArgumentException` |
| Vendor prerequisite resolved by identity | `.aVendorPrerequisiteIsResolvedLikeAnyOther` | `SATISFIED` |
| Blocking affects availability, not support | `CapabilityDiscoveryEngineTest.aBlockedFeatureBecomesUnavailableButStaysSupported` | availability `UNAVAILABLE`, state `SUPPORTED`, still `controllable` |

## 5. Snapshot

| target | test | tier |
|---|---|---|
| Schema version positive and pinned | `CapabilitySnapshotTest.theSchemaVersionIsPositiveAndPinned` | T1 |
| `COMPLETE` cannot carry failures / `PARTIALLY_COMPLETE` must | `.aCompleteSnapshotMayNotCarryPartialFailures`, `.aPartialSnapshotMustCarryTheFailureThatMadeItPartial` | T1 |
| Empty snapshot establishes nothing | `.anEmptySnapshotIsANotStartedPassThatEstablishesNothing` | T1 |
| Unknown availability default | `.aFeatureAbsentFromAvailabilityReadsAsUnknownNotUnavailable` | T1 |
| Support ⊥ availability | `.supportedAndUnavailableAreIndependentAxesAndNeitherMeansUnsupported` | T1 |
| Failed features surfaced | `.failedFeaturesSurfaceTheirIdentitiesForTheNextPass` | T1 |
| Conflict needs ≥ 2 kinds | `.aConflictNeedsAtLeastTwoDisagreeingEvidenceKinds` | T1 |
| Deterministic ordering | `CapabilityDiscoveryEngineTest.featureOrderFromTheSourceDoesNotChangeTheSnapshot` | T1 |

## 6. Lifecycle

| target | test | tier |
|---|---|---|
| Completion only through `DISCOVERING` | `DiscoveryStateTest.completionIsReachableOnlyThroughDiscovering` | T1 |
| Failure/cancel always reachable in flight | `.failureAndCancellationAreReachableFromEveryInFlightState` | T1 |
| Terminal states do not revive | `.terminalStatesHaveNoOutgoingForwardEdgeAndDoNotRevive` | T1 |
| Evidence-owing states | `.onlyCompletedAndPartialPassesOweMergableEvidence` | T1 |
| The three "not all resolved" states stay distinct | `.partialAndCompleteAndFailedRemainDistinctStates` | T1 |

## 7. Architecture guards (extended this phase)

| rule | test | tier |
|---|---|---|
| No production `CapabilityDiscoverySource` | `PhaseEightScopeTest.noProductionSourceImplementsTheCapabilityDiscoverySource` | T1 |
| Engine never writes/opens/commands/scans | `.theDiscoveryEngineOnlyReadsAndNeverWritesOrOpens` | T1 |
| Capability imports nothing upward (L2 ↮ L4/L3/device) | `.theCapabilityAreaImportsNothingUpward` + `DependencyDirectionTest.coreAreasDependOnlyOnMoreFoundationalAreas` | T1 |
| No wall clock / no Android in capability | `.theCapabilityAreaReadsNoWallClockOrAndroidClass` + `DependencyDirectionTest.coreMainSourcesDoNotImportJvmOnlyLibraries` | T1 |
| No UI / no persistence reached | `DependencyDirectionTest.neitherModuleReferencesUiFrameworks` (unchanged) | T1 |
| Test doubles stay in test source | `DependencyDirectionTest.productionSourcesDefineNoTestDoubles`, `TestDoublesAreNotHardwareTest` (reused) | T1 |

## 8. Deferred to the device session (`NOT RUN`, never reported as passing)

- Discovery against a real `ProtocolSession` / `EarbudProtocol.discoverCapabilities`.
- Any rung above `LAB_TESTED`: `HARDWARE_VERIFIED`/`PERSISTENCE_VERIFIED` capability claims.
- Byte-level malformed-response handling (no parser ships this phase).
- Timeout-driven re-read policy, and the L3/L4 `ProtocolSession → CapabilityDiscoverySource` binding.

Each is owned by a later phase or the end-of-project device session (ADR-P8-010; standing
build-application-first directive).
