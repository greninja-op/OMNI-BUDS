# Phase 2 — Task List

ID grammar: `TASK-P2-<NNN>` per ADR-P0-013 (`docs/phases/phase-0/decisions.md:100-104`), whose rule is one grammar `REQ|TASK|TEST|ADR-<SCOPE>-<NNN>` for the whole project. The Phase 2 prompt seeds requirement ids as `OB-P2-REQ-NNN` (`execution-prompt.md:563-568`) and seeds no task list at all; the mapping from its scope sections §5.1–§5.9, §6–§9 to this list is in the table at the end. Nothing was dropped in the translation, and the tasks with no prompt seed are the work the prompt did not anticipate (the audit, the adopt-or-set-aside decision, the guard revisions).

**Phase:** 2 — Android Bluetooth Foundation · **Scope id:** `P2` · **Owner:** Phase 2 documentation workstream (this record and `requirements.md`) inside an orchestrator-led phase.
**Document status:** written against the working tree at `main` / HEAD `608eba5`. `:core`'s Phase 2 sources and tests are committed as `5c53098`, `690f0bb`, `c1421e1`, `99a0473`; **all 13 `:platform:android` main sources and all 5 test sources are untracked**, and `core/src/test/kotlin/com/omnibuds/core/architecture/DependencyDirectionTest.kt` and `platform/android/build.gradle.kts` are modified but uncommitted. No commit hash is claimed for the Android module work, because it has none. `.gitignore` and the `tools/device-bridge/**` changes in the same working tree belong to the separate device-bridge workstream and are not Phase 2 Android tasks.

Owner legend (roles fixed by `execution-prompt.md` §4): **A** repository/architecture auditor · **B** Android Bluetooth API specialist · **C** permission/privacy · **D** state and lifecycle · **E** transport architecture · **F** testing/QA · **G** security · **H** documentation · **I** integration orchestrator.

Status legend: `done` = the acceptance criteria are verified against the repository — a named `@Test` method read on disk plus its entry in the JUnit XML report, a build run, or a file read back at a cited line. `done — partly tested` = some criteria rest on a named check, the rest only on reading a named file; the split is in the evidence line. `deferred` = out of this phase's authorised scope, with the owning phase named. **No task is `done` because an agent said it was finished, and no task is `verified-on-hardware`: no headset, GATT, RFCOMM, scan, pair list, emulator or AVD was touched, and no instrumentation test exists** (`docs/security/device-access-policy.md:59-63`).

Ownership discipline: each task below owns a file-disjoint set, per MASTER-CONTEXT §39 and `docs/phases/phase-0/sub-agent-orchestration.md`. Where two records describe the same seam, this file cites the ADR or the prompt section and defers to `decisions.md`; it restates neither.

---

## Audit, authorisation and the decision record

### TASK-P2-001 — Inspect the repository and audit the Phase 1 boundary before writing code
**Objective.** Establish ground truth and decide whether Phase 2 can proceed against the tree it found (prompt §2).
**Dependencies.** Phase 1 complete (`4d69dc4`, `a5f3bf2`). **Owner.** A.
**Files.** `docs/phases/phase-2/architecture-audit.md` (362 lines).
**Implementation notes.** Prompt §2 items 1–10; the audit's verdict (§8, `architecture-audit.md:340-342`) is "Phase 2 can proceed safely — but the tree cannot": Phase 2 code was already in the tree, uncommitted, in an unregistered area, with `ADR-P2-004` cited by a code comment that did not exist. Findings R-1 … R-15 are numbered there and every claim is cited to `file:line`.
**Required tests.** none — inspected (the audit ran no Gradle task, by its own statement at `architecture-audit.md:5`).
**Acceptance criteria.** Every §2 item has a recorded finding, including absences; the policy/mechanism split table exists (§2.2); the guard revisions are specified before they are made (§5.3).
**Status.** `done` — evidence: the file is on disk, is committed (`c1421e1`), and its snapshot is HEAD `0b28fd8`; nothing in it is a test result.

### TASK-P2-002 — Adopt the in-flight core work and close the documentation debt
**Objective.** Decide adopt-versus-set-aside, then write the ADRs the code had already cited.
**Dependencies.** TASK-P2-001. **Owner.** I.
**Files.** `docs/phases/phase-2/decisions.md`.
**Implementation notes.** Prompt §2 ("Do not silently overwrite previous work"); ARCH-GOV-002 (a boundary change needs an ADR before the check is edited). The file's own process note (`decisions.md:6`) records that `ADR-P2-003`/`ADR-P2-004` were cited before they existed, that the `platform` area entered the layer map unrecorded, and that a retry-safety assertion had been widened — ADR-P2-004 and ADR-P2-005 record the corrections, not the original mistakes.
**Required tests.** none — inspected.
**Acceptance criteria.** ADR-P2-001 … ADR-P2-015 exist with status, context, decision, alternatives and consequences, and each amendment to Phase 0/1 is named explicitly.
**Status.** `done` — evidence: `decisions.md` (104 lines) carries ADR-P2-001 … ADR-P2-015; **ADR-P2-016, cited by the uncommitted guard revision at `DependencyDirectionTest.kt:345` and `:399` and required by TASK-P2-027, is not in the file**, and this record does not invent its content.

### TASK-P2-003 — Settle the minSdk question and its permission consequence
**Objective.** Replace Phase 1's provisional floor with a user decision and state what it forces.
**Dependencies.** TASK-P2-002. **Owner.** I, C.
**Files.** `docs/phases/phase-2/decisions.md` (ADR-P2-002), `gradle/libs.versions.toml:13`.
**Implementation notes.** ADR-P2-002 closes ADR-P1-015: minSdk 26 confirmed by the user, therefore the resolver must answer for the legacy band (26–30) and the modern band (31+), and the API-level band is a value the platform supplies rather than a constant core assumes.
**Required tests.** `PermissionRequirementResolverTest.theLegacyBandStillRequiresTheInstallTimePermissionForAdapterReads` (`PermissionRequirementResolverTest.kt:48`), `.phaseTwoOperationsRequireNoPermissionForAModernTarget` (`:33`).
**Acceptance criteria.** One band table, one source of the numbers, and no single-branch permission implementation.
**Status.** `done` — evidence: `androidMinSdk = "26"` and the two-band rows at `PermissionRequirementResolver.kt:83-94,126-173`; both named tests are in `core/build/test-results/test/TEST-com.omnibuds.core.platform.PermissionRequirementResolverTest.xml` (`tests="10"`, 0 failures).

### TASK-P2-004 — Return `kotlinx-coroutines-core` to `:core` on the trigger Phase 1 recorded
**Objective.** Give the `Flow` surface its dependency, with the reason in the build file.
**Dependencies.** TASK-P2-002. **Owner.** I.
**Files.** `core/build.gradle.kts:25-35`, `gradle/libs.versions.toml:18-21`.
**Implementation notes.** ADR-P2-003, which discharges ADR-P1-021's stated condition and rejects the callback-list alternative because prompt §5.2/§5.8 name `Flow`, structured concurrency and cancellation explicitly; `api` scope because `AdapterStateSource` returns `Flow` in core API.
**Required tests.** none — inspected (the compile gate is the check: `allWarningsAsErrors` in `core/build.gradle.kts:15` plus `:core:test`).
**Acceptance criteria.** No dependency without a recorded reason; no new version coordinate invented.
**Status.** `done` — evidence: `api(libs.kotlinx.coroutines.core)` at `core/build.gradle.kts:29` with the reason comment at `:26-28`; committed as `5c53098`, with the catalog entry following in `690f0bb` ("commit the catalog entry the previous commit depends on").

## Core platform area

### TASK-P2-005 — Register `platform` at layer 1 in the area map
**Objective.** Make the new area a real boundary in the enforcement, not a directory.
**Dependencies.** TASK-P2-004. **Owner.** I.
**Files.** `core/src/test/kotlin/com/omnibuds/core/architecture/DependencyDirectionTest.kt:33-48`.
**Implementation notes.** ADR-P2-001 places the area at layer 1 (policy that depends only on `common`); audit §2.3 proposed the name `bluetooth` and the area stayed `platform`, which the audit's own addendum (`architecture-audit.md:353`) records as the orchestrator's call. Note one discrepancy for the record: ADR-P2-001 describes layer 1 as "alongside `state` and `transport`", while the map on disk also moved `state` from 1 to 0 (`DependencyDirectionTest.kt:34-37`, comment `:34-35`) so `platform` may import `state.VerificationLevel` (`PlatformFeatureSupport.kt:3`); no ADR in `decisions.md` states that re-layering.
**Required tests.** `everyCoreAreaIsRegisteredInTheLayerMap` (`DependencyDirectionTest.kt:232`), `coreAreasDependOnlyOnMoreFoundationalAreas` (`:213`), `packageStatementsMatchSourceDirectories` (`:265`).
**Acceptance criteria.** Registration is mandatory (an unregistered area fails), and `platform` may import only `common`/`state` plus itself.
**Status.** `done` — evidence: all three methods appear as passed `<testcase>` entries in `core/build/test-results/test/TEST-com.omnibuds.core.architecture.DependencyDirectionTest.xml` (suite timestamp `2026-10-01T15:48:16`).

### TASK-P2-006 — Author the adapter-state vocabulary and the registration and clock seams
**Objective.** Fix the host-side vocabulary before any mechanism exists.
**Dependencies.** TASK-P2-005. **Owner.** D.
**Files.** `core/platform/BluetoothAdapterState.kt`, `AdapterStateObservation.kt`, `PlatformRegistration.kt`, `TimeProvider.kt`.
**Implementation notes.** Prompt §5.2's nine distinctions and §5.4's cleanup rules; ADR-P0-016 for unknown-versus-off; ADR-P1-012 for the clock seam; `PlatformRegistration.kt:16-22` makes disposal idempotent by contract because a cancellation path may run it twice.
**Required tests.** `AdapterStateObserverTest.onlyUsableAdapterStatesAreReportedUsable` (`AdapterStateObserverTest.kt:228`), `.aTimestampIsRecordedOnlyWhenThePlatformSuppliesOne` (`:217`).
**Acceptance criteria.** Six states, `isUsable()` true only for `ENABLED`, `isProvablyDisabled()` false for `UNKNOWN`, timestamps nullable and never defaulted to zero.
**Status.** `done — partly tested` — evidence: the two named tests plus reading `BluetoothAdapterState.kt:36-44`. The dead `ObservationKind.DEDUPED` that used to sit here is deleted at close-out (ADR-P2-018), so duplicate suppression is claimed only as an emission count.

### TASK-P2-007 — Author the permission vocabulary and the matrix's supported band
**Objective.** Name the six permissions, the two bands and the mandatory-reason guard as data.
**Dependencies.** TASK-P2-005. **Owner.** C.
**Files.** `core/platform/PermissionState.kt`, `BluetoothPermission.kt`, `PermissionRequirement.kt`, `PermissionContext.kt`.
**Implementation notes.** Prompt §5.3's seven conceptual states are taken verbatim (`PermissionState.kt:13-34`); ADR-P2-009 keeps `targetSdk` and `deviceSdk` separate; audit finding R-9's two-owners risk is answered by naming the constants `MIN_MATRIX_SDK`/`MAX_MATRIX_SDK` rather than `minSdk`/`targetSdk` (`BluetoothPermission.kt:54-62`).
**Required tests.** `PermissionRequirementResolverTest.theLegacyScanBandCarriesAllFourPermissionsAndSaysWhyPerItem` (`PermissionRequirementResolverTest.kt:86`) exercises `ApiRange.contains` through `appliesAt`; `AndroidPermissionStateProviderTest.noInputProducesAnUnsupportedClaim` (`AndroidPermissionStateProviderTest.kt:71`) covers the state set's unreachable members.
**Acceptance criteria.** A requirement without a reason is unconstructible; a band cannot be inverted or fall outside the matrix; `UNKNOWN` is neither a denial nor a grant.
**Status.** `done — partly tested` — evidence: named tests green; **two residues read from disk** — `PermissionState.kt:39-45`'s KDoc says "Only `NOT_REQUIRED` and a genuine grant allow that" while `allowsSilentProceeding()` returns true for `NOT_REQUIRED` alone (audit finding R-15, unresolved), and `ApiRange.LEGACY_BLUETOOTH_MODEL`/`MODERN_BLUETOOTH_MODEL` (`:65,68`) plus `PermissionContext.isDetermined`/`usesModernBluetoothModel`/`ALIAS_API_LEVEL`/`RECEIVER_EXPORT_TARGET_SDK` (`:33,37,45,48`) have no caller in either module.

### TASK-P2-008 — Declare the four platform seams and the capability types
**Objective.** Give prompt §5.2, §5.5 and §5.9 the interfaces the Android module will implement.
**Dependencies.** TASK-P2-006, TASK-P2-007. **Owner.** D, C.
**Files.** `core/platform/BluetoothPlatform.kt`, `AdapterStateSource.kt`, `PlatformFeature.kt`, `PlatformFeatureSupport.kt`, `BluetoothPlatformCapabilities.kt`, `ApiAvailability.kt`.
**Implementation notes.** ADR-P2-008 (four facts, three of them here; device support is deliberately absent); ADR-P2-001 forbids `platform` importing `audio`/`device`, which is what keeps a capability row from becoming a codec claim; the prompt's illustrative `isBluetoothAvailable(): Boolean` (`execution-prompt.md:278`) was replaced by `ApiAvailability adapterPresent` so "not established" is expressible.
**Required tests.** `AdapterStateObserverTest.anUnavailableSourceReportsUnavailableAndNotEnabled` (`AdapterStateObserverTest.kt:207`), `AndroidPlatformCapabilityProviderTest.nothingIsClaimedUsableBecauseNoRadioWasOpened` (`AndroidPlatformCapabilityProviderTest.kt:118`), `.candidateTransportsComeFromThePhonesOwnFlags` (`:181`).
**Acceptance criteria.** No fabricated positive; every unprobed answer reads `UNKNOWN`; the aggregate report carries no device fact.
**Status.** `done — partly tested` — evidence: named tests green; `PlatformFeatureSupport.unknown()`'s sparse-map fallback (`BluetoothPlatformCapabilities.kt:41-42`), `unobserved()` (`:57-63`) and `usableFeatures` (`:45-46`) are named in no test, and `apiLevelSupports()`'s non-positive-floor guard (`ApiAvailability.kt:32-36`) likewise.

### TASK-P2-009 — Widen the error vocabulary and pin both of its properties exhaustively
**Objective.** Add the platform categories ADR-P2-004 admits, and close Phase 1's known issue 6 in the same act.
**Dependencies.** TASK-P2-005. **Owner.** I, G.
**Files.** `core/common/OmniBudsErrorCategory.kt:37-47`, `core/src/test/.../common/OmniBudsErrorCategoryTest.kt`.
**Implementation notes.** Prompt §5.7; ADR-P2-004 (union, not substitution; cancellation stays an outcome; `ADAPTER_STATE_UNKNOWN` refused as a category because `BluetoothAdapterState.UNKNOWN` already carries that fact); ADR-P2-005 replaces the two hand-written lists with full-coverage maps, after the auditor demonstrated the predicted failure in the same area (`architecture-audit.md:358`).
**Required tests.** `OmniBudsErrorCategoryTest.theCategorySetIsExactlyTheDocumentedOne` (`OmniBudsErrorCategoryTest.kt:21`), `.everyCategoryDeclaresItsRetryClassAndNoneIsUnspecified` (`:40`), `.aWriteOrUnknownEffectIsNeverBlindlyRetried` (`:76`), `.everyCategoryDeclaresWhetherTheDeviceStateIsNowUncertain` (`:85`), with `assertFullCoverage` (`:124`) as the mechanism.
**Acceptance criteria.** A category cannot be added without deciding its retry class and invalidation behaviour; only `READ_FAILED` may be retried blindly; no state value is smuggled in as a category.
**Status.** `done` — evidence: the four methods are green in `core/build/test-results/test/TEST-com.omnibuds.core.common.OmniBudsErrorCategoryTest.xml` (`tests="7"`, 0 failures); the set is 23 names, seven of them Phase 2 rows. `PLATFORM_API_UNAVAILABLE`, `CONNECTION_UNAVAILABLE` and `UNSUPPORTED_OPERATION` are pinned vocabulary with no Phase 2 producer — that is stated in `requirements.md` REQ-P2-012 rather than hidden.

## Adapter-state observation

### TASK-P2-010 — Implement the single-slot observer machine in `:core`
**Objective.** Turn prompt §5.4's eight requirements and §5.8's two prohibitions into behaviour that a radio-less test can reach.
**Dependencies.** TASK-P2-006, TASK-P2-008. **Owner.** D.
**Files.** `core/platform/AdapterStateObserver.kt`.
**Implementation notes.** ADR-P2-007 (single-slot `Mutex`, dedupe versus A-B-A, teardown in `finally`, a throwing disposal captured in `teardownProblem` rather than emitted into a possibly-closed channel); `AdapterStateSource.kt:25-29` states the cold-flow contract that a collector's loss is the unregistration signal.
**Required tests.** `anObservationStartsWithWhatTheAdapterIsRightNow` (`AdapterStateObserverTest.kt:40`), `.aStateThePlatformAnnouncesTwiceIsNotPassedOnTwice` (`:53`), `.anOffOnOffSequenceIsPreservedRatherThanCollapsedToTheLastValue` (`:66`), `.aSecondConcurrentObserverIsRefusedInsteadOfRegisteringTwice` (`:93`), `.theRegistrationIsTornDownWhenTheStreamEndsNormally` (`:113`), `.cancellationTearsTheRegistrationDownToo` (`:126`), `.aFailedFirstReadIsReportedAndTheChangeStreamStillRuns` (`:140`), `.aTeardownThatThrowsIsRecordedInsteadOfDisappearing` (`:164`), `.aRefusedRegistrationOpensNothingAndReportsWhy` (`:177`), `.aSingleReadDoesNotOccupyTheObserverSlot` (`:191`).
**Acceptance criteria.** All ten behaviours above are asserted, and the platform is registered with exactly once per observation.
**Status.** `done — partly tested` — evidence: **16/16** green in `TEST-com.omnibuds.core.platform.AdapterStateObserverTest.xml`. Audit findings R-7 (cancellation relabelled as `PLATFORM_EXCEPTION`) and R-5's residual are closed at close-out: `FakeAdapterStateSource` can script a cancelled read and a cancelled registration, both branches forward `OperationOutcome.Cancelled`, and three methods were added (ADR-P2-018). Still resting on convention rather than a check: the observer slot is per-instance, so "one observer per process" depends on `di/OmniBudsBluetooth.kt`'s documented single-instance obligation (audit R-5, open as a risk).

### TASK-P2-011 — Build the scripted source double and the observer suite
**Objective.** Make §9's lifecycle and cancellation tests executable without a radio.
**Dependencies.** TASK-P2-010. **Owner.** F.
**Files.** `core/src/test/.../platform/FakeAdapterStateSource.kt`, `AdapterStateObserverTest.kt`.
**Implementation notes.** Prompt §6 ("Test doubles may simulate platform dependency responses strictly inside tests"); ADR-P1-013 keeps doubles out of main source; the double counts opens, disposes and live registrations so a leak is observable (`FakeAdapterStateSource.kt:29-42`), and its read queue refuses to invent an answer once dry (`:45-56`).
**Required tests.** the ten named in TASK-P2-010, plus `aTimestampIsRecordedOnlyWhenThePlatformSuppliesOne` (`AdapterStateObserverTest.kt:217`) and `onlyUsableAdapterStatesAreReportedUsable` (`:228`).
**Acceptance criteria.** Doubles default to nothing, leaks are counted, and no test needs a device.
**Status.** `done` — evidence: `FakeAdapterStateSource` is test-only and `productionSourcesDefineNoTestDoubles` (`DependencyDirectionTest.kt:179`) fails the build if that ever stops being true; the 13-method suite is green on disk.

## Permission research and model

### TASK-P2-012 — Verify the Android Bluetooth permission behaviour instead of remembering it
**Objective.** Produce the evidence-labelled matrix prompt §5.3 demands, and report where the project's own text was wrong.
**Dependencies.** TASK-P2-003. **Owner.** B, C.
**Files.** `docs/phases/phase-2/bluetooth-api-research.md` (585 lines), `docs/security/device-access-policy.md`.
**Implementation notes.** SEC-PERM-003 ("remembered behaviour is not evidence"); ADR-P2-009 corrects Phase 0 `SEC-PERM-002` on this basis; §0 of the research file fixes the labels `[DOC]`/`[AOSP-35]`/`[INFER]`/`[UNVERIFIED]` and states that no device, emulator or `adb` was used, so nothing in it reaches `LAB_TESTED`; §6 lists nine unresolved items (U-1 … U-9), including that the compatibility-table page the brief called canonical now 404s.
**Required tests.** none — inspected (a research record has no executable check; its consequences are tested by TASK-P2-013).
**Acceptance criteria.** Every row names its source; every silence is recorded as silence; the Q1–Q7 answers exist and Q7's answer is "declare nothing".
**Status.** `done` — evidence: `bluetooth-api-research.md:9-40` (method and labels), `:81-120` (the 21-row band matrix plus Note A), `:199-421` (Q1–Q7), `:422-437` (U-1 … U-9), `:500-512` (X-1 … X-3 contradictions reported, not silently edited); committed as `c1421e1`.

### TASK-P2-013 — Write the centralised, version-aware permission resolver
**Objective.** One frozen table, keyed on `targetSdk`, with a mandatory reason per row and a refusal instead of a guess.
**Dependencies.** TASK-P2-007, TASK-P2-012. **Owner.** C.
**Files.** `core/platform/PermissionRequirementResolver.kt`.
**Implementation notes.** Prompt §5.3 ("Create a centralized permission requirement resolver", "Do not blindly declare or request every permission"); ADR-P2-009 (branch on the target, emit device-level caveats from `deviceSdk`); the Phase-3+ rows are pre-computed deliberately and are not authorisation to perform them (ADR-P2-014); "Note A" of the research is carried into the reason strings (`PermissionRequirementResolver.kt:276-277`) so a later reader cannot "fix" the guide-versus-method asymmetry back into a documentation gap.
**Required tests.** `phaseTwoOperationsRequireNoPermissionForAModernTarget` (`PermissionRequirementResolverTest.kt:33`), `.theLegacyBandStillRequiresTheInstallTimePermissionForAdapterReads` (`:48`), `.anUnknownTargetSdkProducesNoPlanRatherThanAGuessedBand` (`:61`), `.scanningNeedsLocationUntilNeverForLocationIsAsserted` (`:72`), `.theLegacyScanBandCarriesAllFourPermissionsAndSaysWhyPerItem` (`:86`), `.connectClassOperationsFollowTheGuideWhereTheMethodDocsAreSilent` (`:106`), `.leAudioOnALegacyTargetIsAnApiLimitNotAPermissionQuestion` (`:125`), `.profileStateCarriesTheDeviceLevelEnforcementCaveat` (`:135`), `.noPhaseTwoOperationEverAsksForLocation` (`:150`), `.everyRequirementNamesTheOperationItBelongsTo` (`:169`).
**Acceptance criteria.** Ten named behaviours asserted; no Phase 2 operation requires anything and none produces a prompt.
**Status.** `done — partly tested` — evidence: 10/10 green in `TEST-com.omnibuds.core.platform.PermissionRequirementResolverTest.xml`; the *content* of every band boundary remains `INFERRED` documentation, and research U-1 (legacy-band behaviour on a real API 26–30 device) is recorded as unverified, so this task is not a statement about Android.

### TASK-P2-014 — Move phase authorisation from runtime data into a check
**Objective.** Keep `authorizedInPhase` as metadata and enforce the boundary where a build can fail.
**Dependencies.** TASK-P2-013. **Owner.** I, F.
**Files.** `core/platform/BluetoothOperation.kt:10-16,92`, `core/src/test/.../platform/PhaseTwoScopeTest.kt`.
**Implementation notes.** ADR-P2-014, closing audit finding R-10: a runtime gate needs an ambient "current phase" value, which `architecture-governance.md` forbids as hidden global state, and a shipped binary has no business knowing which phase built it. The resolver answers for Phase 3 and Phase 6 rows because pre-computing them is not performing them.
**Required tests.** `theAuthorisedSetIsExactlyTheFiveInspectionOperations` (`PhaseTwoScopeTest.kt:35`), `.nothingThatTouchesADeviceIsAuthorizedInPhaseTwo` (`:42`), `.everyAuthorisedOperationHasAPlanAndNoneOfThemPromptsTheUser` (`:56`), `.noTransportBoundaryIsOpenableInPhaseTwo` (`:71`), `.theResolverRefusesRatherThanInventingForAnUndeterminedContext` (`:89`).
**Acceptance criteria.** Exactly five operations are authorised; nothing device-facing is; each authorised plan needs nothing and prompts nobody.
**Status.** `done` — evidence: 5/5 green in `TEST-com.omnibuds.core.platform.PhaseTwoScopeTest.xml`; `isAuthorizedIn` has no caller outside that test (census of both main source sets), and no check forbids a future caller.

## Transport boundaries

### TASK-P2-015 — Establish the Bluetooth transport boundary set
**Objective.** Declare the five boundaries prompt §5.6 sketches, adapting them to `TransportContract`.
**Dependencies.** TASK-P2-005. **Owner.** E.
**Files.** `core/transport/BluetoothTransport.kt`, `GattTransport.kt`, `RfcommTransport.kt`, `ClassicTransport.kt`, `BleTransport.kt`, `LeAudioTransport.kt`, `common/TransportKind.kt:11-17`.
**Implementation notes.** Prompt §5.6 ("infrastructure boundaries only") and §6; ADR-P2-013 adds `TransportKind.BLE` so `BleTransport` has a kind to pin and keeps the five interfaces otherwise empty; the prompt's `BleTransport`-as-sibling-of-`GattTransport` shape is kept because the prompt named it, but declared empty with the disagreement recorded (`transport-boundaries.md` §2.1) rather than papered over with an invented member; A2DP/AVRCP/HFP get **no** boundary because a profile a device speaks is not a control channel OmniBuds opens (§5 of that file).
**Required tests.** `everyBluetoothSubInterfaceIsABluetoothTransport` (`TransportBoundariesTest.kt:288`), `TransportKindPinningTest.everyBoundaryPinsItsOwnKind` (`TransportKindPinningTest.kt:24`), `.theFiveBoundariesAreDistinctAndNonePinsAnEscapeHatch` (`:33`), `.aPinnedKindSurvivesBeingReadThroughTheSuperType` (`:42`).
**Acceptance criteria.** Five distinct boundaries, one kind each, zero protocol members, no implementation in either module.
**Status.** `done` — evidence: 3/3 and the named boundaries test green (`TEST-com.omnibuds.core.transport.TransportKindPinningTest.xml` `tests="3"`; `TransportBoundariesTest.xml` `tests="15"`); **one prose residue:** `BleTransport.kt:20-23` still says `TransportKind` "deliberately has no `BLE` constant" while `:39-40` pins `TransportKind.BLE`.

### TASK-P2-016 — Give the boundaries their value types and construction-time claim rules
**Objective.** Make "a row cannot claim more than its evidence" a constructor failure rather than a review rule.
**Dependencies.** TASK-P2-015. **Owner.** E.
**Files.** `core/transport/TransportBoundary.kt`, `TransportNegotiation.kt`, `TransportAvailability.kt` (existing, extended use).
**Implementation notes.** `transport-boundaries.md` §3's invariant table, which names the real bug each ban prevents (PROTO-XPORT-001/003/005/007, PROTO-RESEARCH-003, ADR-P0-014); the "selected must also be available" half is recorded there as stricter than the brief.
**Required tests.** `aBoundaryCannotClaimAnAvailableChannelOnInferenceOrCodeExistenceAlone` (`TransportBoundariesTest.kt:50`), `.aBoundaryWithEvidenceBehindItsAvailabilityConstructs` (`:66`), `.oneBoundaryRowCannotDescribeTwoDifferentChannels` (`:81`), `.aRefusedCandidateIsLegalAtEveryEvidenceTier` (`:106`), `.aBlankNoteIsRejectedWhileAnAbsentNoteIsARealState` (`:123`), `.negotiationRefusesASelectionThatWasNeverOffered` (`:140`), `.negotiationRefusesASelectionItAlsoRecordsAsUnavailable` (`:157`), `.preferringUsesTheCallersOrderAndNeverItsOwn` (`:171`), `.preferringSkipsARefusedCandidateWithoutFallingThroughToOneThatWasNotAskedFor` (`:197`), `.preferringReturnsNullWhenNothingInTheCandidateListIsAvailable` (`:233`), `.anEmptyCandidateListAndAnAllRefusedListAreBothUndetermined` (`:245`), `.aNegotiationWithAUsableCandidateIsNotUndetermined` (`:265`), `.everyTransportKindCanHoldABoundaryWithoutAssumingGattIsPresent` (`:299`).
**Acceptance criteria.** Every invariant in the table is asserted, and fixtures stay fictional — a category appears because a test needed a refusal, never because anything was observed.
**Status.** `done` — evidence: 15/15 green in `TEST-com.omnibuds.core.transport.TransportBoundariesTest.xml`; the deferred-member list and the "no notification member" decision are prose in `transport-boundaries.md` §4, checked by nothing.

### TASK-P2-017 — Write the transport deferred-implementation record
**Objective.** State what each boundary is missing, which phase opens it, and why nothing was invented now.
**Dependencies.** TASK-P2-015, TASK-P2-016. **Owner.** E, H.
**Files.** `docs/phases/phase-2/transport-boundaries.md` (223 lines).
**Implementation notes.** Prompt §4 Agent E's deliverable ("Abstractions. Capability and availability boundaries. Deferred implementation list"); prompt §5.6's "infrastructure boundaries only".
**Required tests.** `noPhaseTwoTransportCodeOpensOrProbesAChannel` (`TransportBoundariesTest.kt:332`) — the machine-checkable half of the document's central claim.
**Acceptance criteria.** The `probeAvailability()` declaration exists exactly once with zero call sites, no mechanism token appears in a transport code line, and every absence has a named owning phase.
**Status.** `done` — evidence: the file's §1 (two consequences), §4 (deferred table), §7 (what later phases may build on); committed as `c1421e1`.

## Android platform module

### TASK-P2-018 — Translate raw platform state integers in exactly one place
**Objective.** Make "what does `STATE_ON` mean" a single-function question, before anything can produce a raw integer.
**Dependencies.** TASK-P2-006. **Owner.** D.
**Files.** `android/bluetooth/mapping/AdapterStateMapping.kt`.
**Implementation notes.** ARCH-AND-003 (the platform module owns the status→meaning mapping); `AdapterStateMapping.kt:13-19` states the rule that an unrecognised number is `UNKNOWN` and not `DISABLED`, because "your Bluetooth is off" sends a user to Settings for a phone that was never queried; `RAW_STATE_UNREADABLE = -1` (`:38`) is a missing-value marker chosen outside the platform's own state range so the mapping resolves it to `UNKNOWN`.
**Required tests.** `AdapterStateMappingTest.enabledAdapterStateMapsToEnabled` (`AdapterStateMappingTest.kt:19`), `.disabledAdapterStateMapsToDisabled` (`:27`), `.transitioningStatesMapToTheirOwnNames` (`:35`), `.anAbsentAdapterIsUnavailableWhateverItsLastReadingWas` (`:47`), `.aMissingReadingIsUnknownNotOff` (`:59`), `.anUnrecognisedNumberIsUnknownNotDisabled` (`:67`), `.theUnreadableMarkerIsOutsideThePlatformsStateRange` (`:78`).
**Acceptance criteria.** All six adapter states reachable by mapping and no raw value silently becoming `DISABLED`.
**Status.** `done` — evidence: 7/7 green in `platform/android/build/test-results/testDebugUnitTest/TEST-...AdapterStateMappingTest.xml` (`tests="7"`, `failures="0"`, `errors="0"`, `skipped="0"`).

### TASK-P2-019 — Confine every framework adapter call to one handle
**Objective.** Put `BluetoothManager`, `getState()` and the `ACTION_STATE_CHANGED` receiver behind a three-member interface.
**Dependencies.** TASK-P2-010, TASK-P2-018. **Owner.** D, B.
**Files.** `android/bluetooth/adapter/BluetoothAdapterHandle.kt`, `adapter/SystemBluetoothAdapterHandle.kt`.
**Implementation notes.** Prompt §5.1 (no framework leakage) and §5.2 (a missing adapter is not a crash); research Q7 and ADR-P2-011 are why the handle needs no permission; `BluetoothAdapterHandle.kt:13-17` fixes the member count at three because a fourth (enable the adapter, list bonded devices) would put an unauthorised capability behind an innocuous name; `SystemBluetoothAdapterHandle.kt:72-78` branches on API 33 for the receiver-export flag, and the two lint ids that branch produces are suppressed at `:71` with both reasons in the KDoc at `:63-69`; the unregister guard is a `compareAndSet` (`:100,106`) because double-unregistration throws, and the receiver reads its extra through `RAW_STATE_UNREADABLE` (`:86`) so the mapping stays the single translator.
**Required tests.** none — inspected: `SystemBluetoothAdapterHandle` is named in **no** file under `platform/android/src/test`, so the receiver body, the API-33 branch and the idempotent unregister are compiled and lint-checked only.
**Acceptance criteria.** Two platform calls answer all three questions; no polling; disposal idempotent; a broadcast with no extra resolves to unknown, not to `STATE_OFF`.
**Status.** `done — partly tested` — evidence: the class itself is unexercised, but the rule it implements is pinned through the seam — `adapter/AndroidAdapterStateSourceTest.anUnreadableValueIsUnknownRatherThanDisabled` (`AndroidAdapterStateSourceTest.kt:47`) covers the extra-less-broadcast case and `AdapterStateMappingTest.theUnreadableMarkerIsOutsideThePlatformsStateRange` (`AdapterStateMappingTest.kt:78`) covers the marker's range; what remains rests on reading `SystemBluetoothAdapterHandle.kt:34-110` and on `:platform:android:lintDebug` reporting no issue.

### TASK-P2-020 — Implement the Android adapter-state source behind the core seam
**Objective.** Turn the handle into `AdapterStateSource`: a one-shot read and a live stream owned by a disposable registration.
**Dependencies.** TASK-P2-018, TASK-P2-019. **Owner.** D.
**Files.** `android/bluetooth/adapter/AndroidAdapterStateSource.kt`.
**Implementation notes.** Prompt §5.8 (binder work off the main thread, no leaked receiver) and §5.7 (a thrown platform call becomes `PLATFORM_EXCEPTION` with the exception class as the only detail); the registration is created eagerly, not on first collection, so the caller can prove it exists and the observer's `finally` can dispose it even if nothing collected (`AndroidAdapterStateSource.kt:57-69`); the channel is `Channel.CONFLATED` because adapter state is latest-wins and the observer already dedupes (`:65-68`); `ClosingRegistration` (`:124-134`) makes disposal end the stream so no collector hangs on a receiver that has gone.
**Required tests.** `aReadIsTranslatedIntoTheDomainState` (`AndroidAdapterStateSourceTest.kt:33`), `.anAbsentAdapterIsReportedAsUnavailable` (`:40`), `.anUnreadableValueIsUnknownRatherThanDisabled` (`:47`), `.aThrowingReadBecomesAStructuredPlatformFailure` (`:54`), `.anEmittedStateReachesTheStreamAlreadyTranslated` (`:73`), `.aRegistrationOpenedWithoutCollectingIsStillDisposalReady` (`:90`), `.disposingTwiceReachesThePlatformOnce` (`:104`), `.disposalEndsTheStreamSoNoCollectorHangs` (`:116`), `.aFailedRegistrationIsReportedAndHandsBackNothingToDispose` (`:129`).
**Acceptance criteria.** Nine named behaviours asserted, and nothing is retried implicitly.
**Status.** `done` — evidence: 9/9 green in `TEST-...AndroidAdapterStateSourceTest.xml`; the scripted `FakeHandle` (`:148-182`) is test-only, enforced by `productionSourcesDefineNoTestDoubles` (`DependencyDirectionTest.kt:179`).

### TASK-P2-021 — Report permission standing without ever prompting, and keep "never asked" separate from "refused"
**Objective.** Implement the permission gate prompt §5.9 names, with the platform-visible half only.
**Dependencies.** TASK-P2-007, TASK-P2-013. **Owner.** C, G.
**Files.** `android/bluetooth/permission/AndroidPermissionStateProvider.kt`, `PermissionStandingReader.kt`, `PermissionRequestLedger.kt`.
**Implementation notes.** ADR-P2-012 (the Android layer never produces `DENIED_PERMANENTLY`; a falsy platform answer stays unknown rather than resolving to "off") and research Q4/Q5 for the signals that do not exist; ADR-P2-009 for the standing-versus-requirement split (`standingOf` versus `stateFor`); the ledger is in-memory and deliberately not persisted, because a stored "we already asked" would survive a permission reset and turn a temporary denial into a permanent claim (`PermissionRequestLedger.kt:15-17`).
**Required tests.** `aGrantedPermissionIsReportedAsGranted` (`AndroidPermissionStateProviderTest.kt:22`), `.anUnrequestedRuntimePermissionIsNotReportedAsADenial` (`:29`), `.aRuntimePermissionRefusedAfterARequestIsReportedAsDenied` (`:40`), `.anUndeclaredInstallTimePermissionIsDeniedRatherThanUnrequested` (`:54`), `.anUnansweredQuestionIsUnknownRatherThanDenied` (`:64`), `.noInputProducesAnUnsupportedClaim` (`:71`), `.anOperationThatNeedsNoPermissionReportsNotRequiredEvenWhenNothingIsGranted` (`:89`), `.aRequiredPermissionReportsThePlatformsStanding` (`:105`), `.theLegacyBandIsJudgedFromTheTargetSdkNotThePhone` (`:127`), `.anUnknownTargetSdkIsNotSilentlyTreatedAsRequiringNothing` (`:152`), `.theStandingReportCoversEveryPermissionInTheModel` (`:167`).
**Acceptance criteria.** Eleven named behaviours; no prompt API is called anywhere in the module; permanent denial is unproducible by exhaustive input.
**Status.** `done — partly tested` — evidence: 11/11 green in `TEST-...AndroidPermissionStateProviderTest.xml`; **no check forbids a future `requestPermissions` call** (the ban is the empty manifest plus prompt §6, read from source), and `SystemPermissionStandingReader` (`PermissionStandingReader.kt:27-32`) — the class that actually calls `checkSelfPermission` — is named in no test.

### TASK-P2-022 — Build the platform capability report from facts the phone states about itself
**Objective.** Answer prompt §5.5 with three separated fields, a nullable API level, and zero device claims.
**Dependencies.** TASK-P2-008, TASK-P2-021. **Owner.** B, C.
**Files.** `android/bluetooth/capability/AndroidPlatformCapabilityProvider.kt`, `ApiLevelProvider.kt`, `PlatformFeatureProbe.kt`.
**Implementation notes.** ADR-P2-008 (four facts; `hardwareEvidence` stays `INFERRED` because a feature flag is not a test, so `isUsable` is false everywhere in Phase 2 — `AndroidPlatformCapabilityProvider.kt:52-55`); ADR-P2-009 (`targetSdkVersion` read from `applicationInfo`, never assumed from a build constant, `:30-37`); the four API-level constants cite `platforms/android-35/data/api-versions.xml` rather than memory (`:194-201`); profile connection state answers `UNKNOWN` because asking it would need a runtime permission this module does not declare (`:127-137`).
**Required tests.** `theRunningApiLevelIsCarriedIntoTheReport` (`AndroidPlatformCapabilityProviderTest.kt:34`), `.adapterPresenceIsAnsweredFromTheHandle` (`:42`), `.aPhoneThatWouldNotAnswerThePresenceQuestionFailsTheReport` (`:54`), `.advertisedFeaturesBecomeAvailableAndUnadvertisedOnesUnavailable` (`:62`), `.aProbeThatThrowsIsUnknownRatherThanAbsent` (`:75`), `.leAudioReportsOnlyTheApiLevelItWasGiven` (`:90`), `.profileConnectionStateIsNeverClaimed` (`:103`), `.nothingIsClaimedUsableBecauseNoRadioWasOpened` (`:118`), `.thePermissionSectionReportsEveryPermissionInTheModel` (`:134`), `.anAuthorisedInspectionReportsNoPermissionInItsWay` (`:148`), `.aTransportRowReportsTheStandingOfThePermissionItWouldNeed` (`:159`), `.anUnknownTargetSdkIsNotSilentlyReadAsNoRequirement` (`:170`), `.candidateTransportsComeFromThePhonesOwnFlags` (`:181`).
**Acceptance criteria.** Thirteen named behaviours, and no entry above `INFERRED`.
**Status.** `done — partly tested` — evidence: 13/13 green in `TEST-...AndroidPlatformCapabilityProviderTest.xml`; `SystemApiLevelProvider`, `SystemPlatformFeatureProbe` and `SystemTargetSdkProvider` are named in no test, so the three production probes are compiled-and-linted only.

### TASK-P2-023 — Compose the platform without duplicating the core behaviour
**Objective.** Implement `BluetoothPlatform` as wiring, keeping the observer machine in `:core`.
**Dependencies.** TASK-P2-020, TASK-P2-021, TASK-P2-022. **Owner.** I.
**Files.** `android/bluetooth/AndroidBluetoothPlatform.kt`, `SystemTimeProvider.kt`.
**Implementation notes.** `AndroidBluetoothPlatform.kt:21-34` gives the reason: re-wiring the single-slot/dedupe/cleanup behaviour in the platform layer would put it where no unit test reaches; each entry point moves its call onto an injected dispatcher defaulting to `Dispatchers.IO` (prompt §5.8); `SystemTimeProvider.kt:5-15` is where the wall clock is allowed to be read.
**Required tests.** `aSingleReadCarriesThePlatformAnswerAndTheInjectedTime` (`AndroidBluetoothPlatformTest.kt:50`), `.anObservationReportsTheCurrentStateBeforeItReportsChanges` (`:65`), `.aSecondConcurrentObservationIsRefusedThroughThePlatformWiring` (`:80`), `.permissionStandingComesFromTheProviderWithoutPromptingAnyone` (`:93`), `.capabilitiesAreDelegatedAndCarryNoClaimThePhoneDidNotMake` (`:110`).
**Acceptance criteria.** Five named behaviours; the core machine is reached through this class, not reimplemented beside it.
**Status.** `done — partly tested` — evidence: 5/5 green in `TEST-...AndroidBluetoothPlatformTest.xml` (suite `time="1.38"`); the dispatcher default itself is not asserted — every test injects `Dispatchers.Unconfined` (`AndroidBluetoothPlatformTest.kt:146`), so "off the main thread" is a reading of `AndroidBluetoothPlatform.kt:40,51,66`.

### TASK-P2-024 — Write the manual composition root
**Objective.** Supply prompt §5.9's five dependencies without a DI framework.
**Dependencies.** TASK-P2-023. **Owner.** I.
**Files.** `android/di/OmniBudsBluetooth.kt`.
**Implementation notes.** ADR-P1-009 (the root arrives with the first Android code, inside `:platform:android`); prompt §5.9 ("Do not introduce a large dependency solely for a trivial abstraction"); the root takes `context.applicationContext` because the platform outlives the screen that asked (`OmniBudsBluetooth.kt:80-89`); it deliberately does not cache, because a hidden global is how a process ends up with a registration nobody can dispose; the one-instance-per-process rule it depends on is documented at `:85-89` and enforced by nothing.
**Required tests.** none — inspected: `omniBudsBluetoothPlatform` is named in no test source of either module (it needs a `Context`).
**Acceptance criteria.** Every seam overridable by parameter; no transport factory; no permission request; no cached global.
**Status.** `done` — evidence: `di/OmniBudsBluetooth.kt:95-122` read back, plus `./gradlew --offline :platform:android:test` succeeding under `allWarningsAsErrors` (`platform/android/build.gradle.kts:33-38`); the function has never executed, which is why the requirement it satisfies (`REQ-P2-016`) carries `inspected`, not `tested`.

### TASK-P2-025 — Give the Android module a JVM test foundation and nothing more
**Objective.** Make prompt §9's "Android-specific tests" runnable without acquiring Robolectric, `androidx.test` or a device.
**Dependencies.** TASK-P2-018 … TASK-P2-024. **Owner.** F.
**Files.** `platform/android/build.gradle.kts:40-61` (modified, uncommitted).
**Implementation notes.** Audit finding R-12 (the module declared no test dependencies at all); ADR-P1-011's dependency-free baseline, so only catalog entries that already exist are reused; `:45-48` states which classes are *not* covered this way and points at `validation.md` for the split — a file that does not exist yet, so that pointer is an outstanding debt, not a claim.
**Required tests.** the five classes named in TASK-P2-020 … TASK-P2-023, all under `testDebugUnitTest`.
**Acceptance criteria.** `:platform:android:test` runs 45 JVM tests, 0 failures, 0 errors, 0 skipped; no instrumentation or device dependency is declared.
**Status.** `done` — evidence: 5 suites summing to 45 (`tests="5"/"9"/"13"/"7"/"11"`) with `failures="0" errors="0" skipped="0"` in `platform/android/build/test-results/testDebugUnitTest/`; the file itself is **uncommitted**, so this is a working-tree state.

### TASK-P2-026 — Keep the manifest empty and let the phase that uses a permission declare it
**Objective.** Declare no permission, no feature and no component in either product-facing manifest.
**Dependencies.** TASK-P2-012, TASK-P2-021. **Owner.** C, G.
**Files.** `platform/android/src/main/AndroidManifest.xml`, checked by `DependencyDirectionTest.kt:434-454`.
**Implementation notes.** ADR-P2-011 (a declaration not tied to an executed call is a fabricated capability; a library manifest merges into every consumer silently; declaring `BLUETOOTH_CONNECT` would move the app into the Nearby devices prompt group with no feature behind it); research Q7 verified per operation that Phase 2's five inspections need nothing; the receiver is registered at runtime, so it never appears in the manifest (ADR-P2-016's confinement rule, TASK-P2-027).
**Required tests.** `platformManifestDeclaresNothingUnjustified` (`DependencyDirectionTest.kt:434`), `platformBroadcastUseIsConfinedToListeningForAdapterState` (`:379`).
**Acceptance criteria.** The allowed-permission set is the empty set, the component tags are absent, and the check fails loudly if the manifest goes missing.
**Status.** `done` — evidence: both methods green in the suite XML; `AndroidManifest.xml:11` is a bare `<manifest xmlns:android=... />`; note the file's comment (`:5-6`) still justifies the emptiness in Phase 1 terms, and Phase 2's justification is ADR-P2-011 plus research Q7.

## Guards, build and records

### TASK-P2-027 — Revise the architecture guards instead of deleting the ones Phase 2 broke
**Objective.** Keep every Phase 1 claim alive under a new, honest scope, and confine what the new mechanism makes possible.
**Dependencies.** TASK-P2-005, TASK-P2-018, TASK-P2-026. **Owner.** A, I.
**Files.** `core/src/test/kotlin/com/omnibuds/core/architecture/DependencyDirectionTest.kt` (17 `@Test` methods, 12 numbered rules; partially uncommitted).
**Implementation notes.** ADR-P2-006 replaces `platformAndroidModuleStillContainsNoSources` with `platformModuleContainsNoUnauthorisedCapabilities` (`:292`) plus `neitherModuleTouchesTheMediaAudioPath` (`:317`) and `neitherModuleReferencesUiFrameworks` (`:348`), and narrows rule 2 from an identifier prefix to named framework types (`:146-159`). The **`Intent` split** is rule 9's change: `Intent` was removed from the UI-token list (`:340-345` explains that `ACTION_STATE_CHANGED`, authorised by prompt §5.4, cannot be received without naming it) and the list now names screens and app-launched components directly (`:349-352`); **broadcast reception got its own confinement rule**, rule 10 (`:372-403`), which permits `BroadcastReceiver`/`IntentFilter`/`Intent`/`registerReceiver` only under `bluetooth/adapter/` (`:385-390`) and bans `sendBroadcast`, `sendOrderedBroadcast`, `PendingIntent` and `LocalBroadcastManager` module-wide (`:381,391-395`); rule 11 (`:405`) adds the package-envelope check (`:408`) and the manifest check (`:434`). This split is **recorded as ADR-P2-016 by the orchestrator** — it is cited at `:345` and `:399` and is not yet in `decisions.md`, so this task restates none of its content.
**Required tests.** `platformModuleContainsNoUnauthorisedCapabilities`, `neitherModuleTouchesTheMediaAudioPath`, `neitherModuleReferencesUiFrameworks`, `platformBroadcastUseIsConfinedToListeningForAdapterState`, `platformSourcesLiveOnlyUnderTheAuthorisedPackages`, `platformManifestDeclaresNothingUnjustified`, `coreReferencesNoAndroidFrameworkTypes`, `noProductionClassImplementsTheProtocolOrRepositoryContracts`, `coreMainSourcesContainNoPlaceholderImplementations`, `productionSourcesDefineNoTestDoubles` (all in this file).
**Acceptance criteria.** No check was deleted; each revised check keeps its original claim and states its new scope in its own failure message; no scan passes vacuously.
**Status.** `done — partly tested` — evidence: all 17 methods appear as passed `<testcase>` entries in `TEST-com.omnibuds.core.architecture.DependencyDirectionTest.xml` (`tests="17"`, suite timestamp `2026-10-01T15:48:16`); residues are stated, not smoothed — every token list is enumeration-bounded, the rule-11 edit is **uncommitted**, ADR-P2-016 is unwritten, and the harness module is outside the scan's scope by construction (`mainSources()` covers `:core` main only, `platformSources()` covers `platform/android/src/main` only).

### TASK-P2-028 — Add the debug-only companion shell so the bridge is verified rather than described
**Objective.** Provide an installable artifact for the device bridge that cannot be mistaken for product code.
**Dependencies.** TASK-P2-002. **Owner.** I (device-bridge workstream supplies the Python side).
**Files.** `settings.gradle.kts:32`, `gradle/libs.versions.toml:30-33`, `tools/companion-shell/build.gradle.kts`, `tools/companion-shell/src/main/AndroidManifest.xml`, `tools/companion-shell/src/main/kotlin/com/omnibuds/tools/shell/ShellActivity.kt`.
**Implementation notes.** ADR-P2-010 (user-approved): the module depends on nothing — not `:core`, not `:platform:android` — and declares no permission, so a screenshot of it is evidence about the harness only; `assembleDebug` is the only artifact the bridge consumes, and AGP's `beforeVariants` API did not resolve in this Kotlin DSL configuration, so "no release variant" is a convention the bridge follows rather than a build gate.
**Required tests.** none — inspected for Phase 2's purposes: the shell has no tests, and the Python bridge suite (`tools/device-bridge/tests`, 419 tests, 0 failures, 1 skipped) is a separate workstream and is **not** Phase 2 evidence.
**Acceptance criteria.** `:tools:companion-shell:assembleDebug` succeeds; the isolation is real.
**Status.** `done` — evidence: the assemble task is part of the BUILD SUCCESSFUL gate in TASK-P2-029; the isolation is read from `tools/companion-shell/build.gradle.kts:63-66` (empty `dependencies {}`) and its manifest (no `<uses-permission>`); `ShellActivity` being an `Activity` that calls `setContentView` (`ShellActivity.kt:34,104`) is authorised by ADR-P2-010 and is invisible to the UI guard, which does not scan this module.

### TASK-P2-029 — Run the build, both test suites, the lint check and the scope census
**Objective.** Produce the phase's measured verification base, and say what no run produces.
**Dependencies.** TASK-P2-001 … TASK-P2-028. **Owner.** I, F.
**Files.** build output only; recorded in `requirements.md` §"Verification base".
**Implementation notes.** Prompt §11's last three items (tests pass or failures documented; build and static analysis pass or blockers documented; git diff contains only authorised changes).
**Required tests.** the whole of `:core:test` and `:platform:android:test`; no new method is added by this task.
**Acceptance criteria.** AC-1 `./gradlew --offline :core:test :platform:android:test :tools:companion-shell:assembleDebug` → BUILD SUCCESSFUL. AC-2 `:core` 356 tests, 0 failures, 0 errors, 0 skipped (96 main / 47 test files). AC-3 `:platform:android` 45 tests, 0 failures, 0 errors, 0 skipped (13 main / 5 test files), all plain JVM against scripted seams. AC-4 `:platform:android:lintDebug` clean, with the two guarded-`register()` ids answered in source. AC-5 the working tree reviewed: Android module untracked, guards and Android build script modified, and `.gitignore`/`tools/device-bridge/**` changes identified as the other workstream's.
**Status.** `done` — evidence: AC-2/AC-3 re-read for this document from the JUnit XML reports (`core/build/test-results/test/` = 42 suites summing to 356; `platform/android/build/test-results/testDebugUnitTest/` = 5 suites summing to 45); AC-4 re-run on 2026-10-01 with BUILD SUCCESSFUL and `platform/android/build/reports/lint-results-debug.xml` containing zero `<issue>` elements — which is *because* both ids are suppressed at `SystemBluetoothAdapterHandle.kt:71`, so the "0 errors / 2 warnings" measurement describes the unsuppressed pattern, and `requirements.md` says so rather than repeating the number unexamined. AC-1 as measured on this workstation 2026-10-01. **Nothing here is a hardware result.**

### TASK-P2-030 — Author this phase's requirements and task records
**Objective.** Write the two records this workstream owns, with every claim traceable to a named check or a named file.
**Dependencies.** TASK-P2-029. **Owner.** H.
**Files.** `docs/phases/phase-2/requirements.md` (22 records, `REQ-P2-001` … `REQ-P2-022`), `docs/phases/phase-2/task-list.md` (this file, 32 records, `TASK-P2-001` … `TASK-P2-032`).
**Implementation notes.** Prompt §8's field lists; REQ-P0-013/REQ-P0-016; ADR-P0-013 for the grammar; the honesty rules in `requirements.md`'s status legend and this file's status legend are load-bearing, so "no hardware", "no emulator", "no instrumentation" and "this class is named in no test" appear explicitly rather than being smoothed into a passing tone.
**Required tests.** none — inspected (documentation review; no automated check exists for prose).
**Acceptance criteria.** AC-1 both records exist, from `docs/templates/`, matching the Phase 1 house structure. AC-2 every task names either an existing `@Test` method with its file:line or "none — inspected". AC-3 no invented number: the counts used are the measured ones in TASK-P2-029. AC-4 deferrals name the owning phase and ADR. AC-5 nothing claims hardware, emulator or instrumentation evidence.
**Status.** `done` — evidence: both files written against the working tree; every cited method name was read on disk and every XML count was re-read for this record.

### TASK-P2-031 — The remaining prompt §8 records
**Objective.** `design.md`, `specs.md`, `test-plan.md`, `validation.md`, `risk-register.md` for Phase 2.
**Dependencies.** TASK-P2-030. **Owner.** H, I, G, F (siblings).
**Files.** `docs/phases/phase-2/`.
**Implementation notes.** Prompt §8 and REQ-P0-013; `SystemBluetoothAdapterHandle.kt:68` and `platform/android/build.gradle.kts:48` both already point at `validation.md`, which is why its absence is a debt with two named creditors.
**Required tests.** none — inspected.
**Acceptance criteria.** The five records exist and the 23-row category table, the permission matrix and the covered/uncovered class split are written down.
**Status.** `deferred` — evidence: not in the directory at the time of writing (this workstream was authorised for exactly the two records in TASK-P2-030 and wrote nothing else); the owning workstreams and the orchestrator keep the obligation inside Phase 2, and REQ-P2-022 is marked `partial` for the same reason.

### TASK-P2-032 — Validate the Android glue on a real device
**Objective.** Run the five framework-touching classes and the composition root where they actually answer.
**Dependencies.** TASK-P2-029. **Owner.** I (user-supplied device).
**Files.** `SystemBluetoothAdapterHandle`, `SystemPermissionStandingReader`, `SystemPlatformFeatureProbe`, `SystemApiLevelProvider`, `SystemTargetSdkProvider`, `omniBudsBluetoothPlatform`.
**Implementation notes.** Prompt §9 ("Document which tests require a real Android device"); ADR-P2-011's rows and research U-1 (legacy-band behaviour on API 26–30) are explicitly unresolved pending a device; the permission matrix must be re-verified per SEC-PERM-003 when a phase first acts on a row.
**Required tests.** none exist — all six types are named in no test source of either module.
**Acceptance criteria.** A device run records real adapter state, real permission standings and a real receiver teardown; until then every such statement stays `UNKNOWN` or `INFERRED`.
**Status.** `deferred` — evidence: `docs/security/device-access-policy.md:59-63` records that no device was attached, `adb devices` returned no entries and no AVD exists, and schedules device validation as a separate step; nothing in this phase raises any Android-behaviour claim above `INFERRED`.

---

## Mapping from the prompt's scope sections

| Prompt section | Tasks | Prompt section | Tasks |
|---|---|---|---|
| §2 pre-execution audit | TASK-P2-001 | §5.5 platform capabilities | TASK-P2-008, TASK-P2-022 |
| §4 Agents A–I | owners on every task | §5.6 transport boundaries | TASK-P2-015, -016, -017 |
| §5.1 platform boundary | TASK-P2-005, -019, -027 | §5.7 Bluetooth errors | TASK-P2-009 |
| §5.2 adapter abstraction | TASK-P2-006, -008, -018 … -020, -023 | §5.8 coroutine rules | TASK-P2-010, -020, -023 |
| §5.3 permission architecture | TASK-P2-007, -012, -013, -021 | §5.9 dependency injection | TASK-P2-004, -024 |
| §5.4 adapter state observation | TASK-P2-010, -011, -019, -020 | §6 forbidden list | TASK-P2-014, -027, -028 |
| §7 audio isolation | TASK-P2-027 (rule 8), TASK-P2-022 | §8 documentation | TASK-P2-030, -031 |
| §9 testing requirements | TASK-P2-011, -025, -029, -032 | §10 git / change management | header, TASK-P2-025, -029 |
| §11 acceptance criteria | TASK-P2-029 | §12 stop condition | TASK-P2-032 (deferred by design) |

Tasks with no prompt seed, added because the work was real: TASK-P2-002 (adopt-and-document), TASK-P2-004 (the dependency discharge), TASK-P2-005 (layer-map registration), TASK-P2-009 (the error-vocabulary widening and the exhaustive-table fix), TASK-P2-014 (authorisation moved into a check), TASK-P2-024 (composition root), TASK-P2-026 (the manifest decision), TASK-P2-027 (the guard revisions, including the `Intent` split and the broadcast-confinement rule), TASK-P2-028 (the harness), TASK-P2-032 (the deferred device run).

## Rollup

**Tasks:** 32 · **done:** 20 (001, 002, 003, 004, 005, 009, 011, 012, 014, 015, 016, 017, 018, 020, 024, 025, 026, 028, 029, 030 — of which TASK-P2-001/-002/-003/-004/-012/-017/-024/-026/-028/-030 rest on reading a record, a build script or a manifest back, or on a build run, rather than on a named test) · **done — partly tested:** 10 (006, 007, 008, 010, 013, 019, 021, 022, 023, 027) · **deferred:** 2 (031, 032) · **tasks without acceptance criteria:** 0 · **tasks without a REQ link:** 0 — every task maps to a record in `requirements.md` through the section it belongs to.

Three tasks exist only because the audit found the phase proceeding ahead of its own record, and two more exist only because the code made a guard impossible to keep in its old form: TASK-P2-002, TASK-P2-009, TASK-P2-014, TASK-P2-027 and the ADR-P2-016 it waits on. Recording them as tasks rather than as quiet edits is what keeps the phase history truthful.

**Verified totals, re-measured at close-out after ADR-P2-018:** `:core` 356 tests and `:platform:android` 45 tests, both with 0 failures, 0 errors and 0 skipped; `./gradlew --offline :core:test :platform:android:test :tools:companion-shell:assembleDebug` BUILD SUCCESSFUL; `:platform:android:lintDebug` BUILD SUCCESSFUL with zero reported issues because the two warnings are suppressed at their site. **No test in either module touches a radio, a device, an emulator or an instrumentation harness**, so nothing in this list is, or may be reported as, `verified-on-hardware`.
