# Phase 3 — Specs (contract reference, as realised)

**Phase:** 3 — Connected Device Detection · **Scope id:** `P3` · **Owner agent:** Phase 3 documentation workstream
**Document status:** written from the working tree at `main` / HEAD `335c31d`, every Phase 3 file read in full. Every signature below is copied from the file it cites, not paraphrased; every member of every enum is listed; every numeric value has a source. **The tree moved while this file was written.** Commit `335c31d` landed part-way through: `DependencyDirectionTest.kt` rules 8 and 9 began scanning the instrumented sources (`:385, 419`), `platform/PlatformFeature.kt:9-14` gained the KDoc paragraph naming the `ObservedProfile` separation, `docs/phases/phase-2/transport-boundaries.md` gained ADR-P3-002's amendment note and `docs/security/device-access-policy.md`'s Phase 2-receiver paragraph was corrected for ADR-P3-013. One edit is still uncommitted as this file closes: ADR-P3-006's amended consequences paragraph in `decisions.md`. §16 therefore states the guard coverage rule by rule and §17.8 dates each claim about it. A line number in `DependencyDirectionTest.kt` is the likeliest place for further drift, and drift there is an erratum against this file, not a defect in the code. Section numbering continues Phase 1's and Phase 2's `specs.md` so a citation of the form "specs.md section 5.5" in source KDoc still resolves.
**Scope.** The nine Phase 3 files of `core/src/main/kotlin/com/omnibuds/core/platform/` (1,671 lines; the area is now 26 files / 2,874 lines), the three files of `platform/android/src/main/kotlin/com/omnibuds/android/bluetooth/connection/` (1,135 lines), the Phase 3 changes to `bluetooth/adapter/SystemBluetoothAdapterHandle.kt`, `di/OmniBudsBluetooth.kt`, `platform/android/src/main/AndroidManifest.xml`, `platform/android/build.gradle.kts`, `gradle/libs.versions.toml`, and the architecture test that enforces them.
**Authority.** `docs/MASTER-CONTEXT.md` → Phase 0 → Phase 1 → Phase 2 → `docs/phases/phase-3/decisions.md` (ADR-P3-001 … ADR-P3-019, cited by id, never restated) → this file. `docs/phases/phase-3/architecture-audit.md` is subordinate to the ADRs and is stale in two places that §17 names. Deviations are marked ⚠ and §17 collects them.
**Evidence ceiling.** **No hardware was touched and none will be in this phase.** The instrumented suite compiles and has never been executed; physical validation is deferred by user directive (ADR-P3-014). Nothing here is `LAB_TESTED` or higher about Android, the highest rung any claim reaches is `IMPLEMENTED`, and every platform fact is traceable to `docs/phases/phase-3/connection-observation-research.md` (which carries the `[DOC]`/`[SDK]`/`[AOSP-35]` citations) or to a comment in the module that names its SDK source. The research's twelve UNVERIFIED items **U-1 … U-12** (research §11) stay named, and §8 and §14 say which spec rows each one can still break.

## 0. What enforces what

| Enforced by the build | Enforced by review or prose only |
|---|---|
| Kotlin compiles for JVM target 17, AGP 8.7.3, Gradle 8.9 (`gradle/wrapper/gradle-wrapper.properties:3`); `allWarningsAsErrors = true` in both modules (`core/build.gradle.kts:15`, `platform/android/build.gradle.kts:40`) | whether a KDoc or ADR sentence about the guard set or the platform matches the code — §17 names four places where it does not |
| 12 architecture rules / 17 checks in `core/src/test/kotlin/com/omnibuds/core/architecture/DependencyDirectionTest.kt`, as §16 tabulates; rules 7, 8 and 9 scan `src/androidTest` as well as `src/main` through one shared list (`:134`, consumed at `:349, 385, 419`) — rules 8 and 9 since `335c31d`, which landed while this file was written (§17.8) | whether the composition creates one observer and one platform per process (`di/OmniBudsBluetooth.kt:39-43, 87-95` states it; nothing checks it) |
| No Android framework type name in `:core` code lines (rule 2, `:181-194`); no media-audio type name in either module (rule 8, `:379-398`) | whether a `detail` string carries an identifier — Phase 3 emits only fixed sentences, counts, `technicalName`s and `${problem::class.simpleName}` (§9) |
| Platform sources confined to `bluetooth/` and `di/` (rule 11, `:482-505`, which **fails when the module has no sources**, `:484-490`); the manifest carries exactly one permission and no component (rule 11, `:508-532`) | whether a seam's default implementation is the honest one |
| No unauthorised capability token in the platform module **or its instrumented sources** (rule 7, `:327-367`, whose list gained `fetchUuidsWithSdp`, `startVoiceRecognition`, `stopVoiceRecognition`, `setPriorityPolicy` at `:346`) | whether a profile should be added to `ObservedProfile.enumerationUnion` — each entry carries a `reason` (§2.6) and a human decides |
| Receiver tokens confined to `bluetooth/adapter/` **and `bluetooth/connection/`**; no send-capable broadcast API anywhere in the platform module (rule 10, `:449-477`) | whether an announcement actually reaches an exported receiver on a handset — that is U-4, deferred |
| The 23-category error set and both property tables, unchanged (`core/src/test/.../common/OmniBudsErrorCategoryTest.kt:20-128`, ADR-P2-005, ADR-P3-006) | whether a category that exists is ever produced — §9's last column |
| The transition table's exhaustiveness (`everyPlatformStateHasATransitionRow`, `DeviceObservationVocabularyTest.kt:27-32`) and the two refused moves (`:46-58`) | whether the table matches a real stack's behaviour — the table is documentation of what the platform *could* report, and no handset has confirmed it |
| The join rule and the redaction guarantee, against three address shapes and the exact `toString()` text (`DeviceObservationKeyTest.kt:25-39, 131-140`) | whether an address is stable per device over time — U-2, and the design caps the failure mode instead (§4) |
| Standing-before-looking, empty-union refusal, duplicate suppression, adapter invalidation, slot refusal, teardown on every exit, cancellation propagation — 30 cases against a scripted source (`ConnectedDeviceObserverTest`) | dispatcher choice: no rule inspects one, and `:core` is forbidden from exposing one (Phase 1 `specs.md` §5.2 rule 2) |
| `:core` 433 declared cases across 47 classes / 0 failures and `:platform:android` 90 per variant across 7 classes / 0 failures — 613 executions over the three test tasks — re-run with `--rerun-tasks`; `lintDebug` reports no issues | the 12 `androidTest` cases (4 smoke, 8 connection) are compiled and never run (ADR-P3-014) |

Because both modules fail the build on a warning, an edit that leaves an unused import in a touched file is a broken build (Phase 1 `specs.md` §0, unchanged).

## 1. Naming conventions as realised (inherits Phase 0 §1, Phase 1 §1, Phase 2 §1)

| Kind | Rule as Phase 3 built it | Examples with file:line |
|---|---|---|
| Core seam | `<Subject>Source`, `<Subject>Observer`, `<Subject>Channel`, `<Subject>Report` | `ConnectedDeviceSource.kt:90`, `ConnectedDeviceObserver.kt:163`, `ConnectedDeviceEventChannel.kt:58` (declared in `ConnectedDeviceSource.kt`), `ProfileSupportReport` (`ObservedProfile.kt:154`) |
| Android implementation of a core interface | `Android<Thing>` | `AndroidConnectedDeviceSource.kt:73` |
| Android implementation that touches the framework | `System<Thing>` | `SystemConnectedDeviceHandle.kt:56` |
| Platform-side narrow seam | `<Thing>Handle` | `ConnectedDeviceHandle.kt:29` (Phase 2 precedent: `BluetoothAdapterHandle`) |
| Axis type | `<Subject><State>` for a reported condition, bare `<Subject>` for a lifecycle position | `DeviceConnectionState.kt:22`, `DeviceBondState.kt:16`, `DeviceAvailability.kt:16`, `ObservationStage.kt:20` |
| ⚠ Name deliberately **not** reused | `DeviceConnectionState`, not `ConnectionState`: two types with one name in two areas is the "same fact modelled two ways" error ADR-P3-001 refuses, so the distinction lives in the name rather than in a package path | `DeviceConnectionState.kt:6-10`; `state/ConnectionState.kt:15-27` untouched |
| ⚠ Name deliberately **not** reused | `ConnectedDeviceSnapshot`, not the prompt §14's `ConnectedDeviceRepository` and not the audit's `ConnectedDeviceProjection`: a `Repository` is a persistence contract in this codebase, and a phase that persists nothing may not borrow the word (ADR-P3-004) | `ConnectedDeviceObserver.kt:68-128` |
| Key type | `<Subject>Key`, sealed, with an explicit absence member | `DeviceObservationKey.kt:29`, `NotReported` `:83` |
| Derived boolean / predicate | `is`/`has`/`can` prefixes stay reserved for facts computed from state (Phase 1 §1.1's amendment): `isConnected`, `isProvablyDisconnected`, `isUnsettled`, `isKnown`, `isBonded`, `isObservable`, `isUnread`, `isAnswerable`, `isActive`, `isAttributable`, `isReportedConnected`, `hasReportedName`, `isUnionComplete`, `isUnattributable`, `canIdentifyAcrossObservations` | `DeviceConnectionState.kt:31,40,43,49`, `DeviceBondState.kt:31,34`, `DeviceAvailability.kt:33,41`, `ObservationStage.kt:32`, `DeviceObservation.kt:77,81,85`, `ConnectedDeviceObserver.kt:102,107` |
| Stored boolean | Phase 3 stores exactly two, both in the projection's constructor and both named as asserted facts rather than as conclusions the type computed: `profileSupportConsulted`, `restsOnCompletedRound`. Nothing in the axes, the key or the observation stores a boolean — each is an enum or a nullable, so no `Boolean?` stands in for a state (Phase 0 §2.2) | `ConnectedDeviceObserver.kt:84, 93`; derived counterparts at `:102-103, 107-108` |
| Join function | `<key>joinableWith`, an extension so the rule has one owner | `DeviceObservationKey.kt:114-115` |
| Copy-on-write mutator | `with…` returns a copy, never mutates: `copy(…)` on the projection; the fold helpers are named `withSnapshotReported`, `withLinkEvent`, `withBondEvent` | `ConnectedDeviceObserver.kt:530,624,675` |
| Factory | `notStarted`, `reported`, `unavailable`, `of`, `from` | `ConnectedDeviceObserver.kt:118`, `DeviceObservation.kt:129`, `ConnectedDeviceSource.kt:129`, `DeviceObservationKey.kt:69,101` |
| Test class | `<Subject>Test`; the vocabulary guard is `DeviceObservationVocabularyTest`, the join guard `DeviceObservationKeyTest`, the engine guard `ConnectedDeviceObserverTest` | `core/src/test/kotlin/com/omnibuds/core/platform/` |
| ⚠ Test method | behaviour-plus-condition lowerCamel, no backticks, **no `TEST-P3-<NNN>` id in any method name** — ids are issued by `test-plan.md`, a sibling workstream's file | `aUnionWhereNothingAnsweredCannotReportAnEmptyRoom` (`ConnectedDeviceObserverTest.kt:380`), `twoReportsWithNoKeyStayTwoDevices` (`DeviceObservationKeyTest.kt:93`), `theSupportQuestionRefusesBeforeReadingAnyBindingState` (`AndroidConnectedDeviceSourceTest.kt:80`), `aRefusedStandingNeverAsksWhichProfilesCouldAnswer` (`ConnectedDeviceObservationInstrumentedTest.kt:100`) |
| operationId strings | kebab-case, one owner per string: `device-observer.observe` (`ConnectedDeviceObserver.kt:749`), `android-connected-device-source` (`AndroidConnectedDeviceSource.kt:288`), `device-source.unavailable` (`ConnectedDeviceSource.kt:153`), `fake-device-source.snapshot` / `.open` (test-only, `FakeConnectedDeviceSource.kt:70, 83`) | plus three platform-failure *subjects* interpolated into the `detail` rather than into the id: `profile-support` (`:92`), `device-snapshot` (`:119`), `device-events` (`:149`) |
| Area | `com.omnibuds.core.platform`, layer 1 (ADR-P3-003). No 13th area was registered and no new Gradle module was created | `DependencyDirectionTest.kt:35-50` |

## 2. Enums, every member

### 2.1 `DeviceConnectionState` — the platform link axis, five members
`platform/DeviceConnectionState.kt:22-28`, declaration order `UNKNOWN, CONNECTING, CONNECTED, DISCONNECTING, DISCONNECTED`. Meanings: `UNKNOWN` = "no report was obtained — permission refused, adapter unreadable, device not enumerated", and explicitly *not* `DISCONNECTED` (`:18-20`); `CONNECTING`/`DISCONNECTING` "exist because Android's profile state constants name them", and whether a given mechanism can *deliver* one is a platform question no code path may answer by synthesis (`:12-16`); `CONNECTED` is the one state that means a transport is up; `DISCONNECTED` is a positive report of no link. Predicates (`:31, 40, 43-46, 49`): `isConnected()` only `CONNECTED`; `isProvablyDisconnected()` only `DISCONNECTED`; `isUnsettled()` = `UNKNOWN | CONNECTING | DISCONNECTING`; `isKnown()` = anything but `UNKNOWN`. The KDoc order is not an ordinal ladder and nothing compares ordinals here.

### 2.2 `DeviceBondState` — the pairing axis, four members
`platform/DeviceBondState.kt:16-28`: `UNKNOWN` ("no report was obtained. Not 'unpaired'"), `BONDED` ("bonded with the phone and seen in the bond list"), `BONDING` ("a bond is in progress. Read-only here: Phase 3 never starts one"), `NONE` ("the platform positively reports no bond exists"). Predicates `isBonded()` (`:31`, only `BONDED`) and `isKnown()` (`:34`, `BONDED | BONDING | NONE`). Note the declaration order is not an evidence ladder — `BONDED` sits above `BONDING` and `NONE`, and the merge ranks them separately (§5.1's `bondRank`).

### 2.3 `DeviceAvailability` — the observability axis, three members
`platform/DeviceAvailability.kt:16-30`: `UNKNOWN` ("not determined. Never collapsed into `UNAVAILABLE`"), `AVAILABLE` ("the platform reported this device, so its other fields mean something"), `UNAVAILABLE` ("the platform declined to report the device — refused, masked, or silent about this mechanism"). Predicates `isObservable()` (`:33`) and `isUnread()` (`:41`, true only of `UNKNOWN`, because `UNAVAILABLE` "is a positive report of refusal"). The type mirrors `ApiAvailability`'s three-value shape deliberately (`:11-14`) and is the third axis ADR-P3-001 requires.

### 2.4 `ObservationStage` and `ObservationArrival` — lifecycle, and how a record got here
`platform/ObservationStage.kt:20-29`: `NOT_STARTED` ("created, never started"), `OBSERVING` ("a snapshot has been taken and updates are being accepted"), `STOPPED` ("ended by request or by cancellation. A restart is legal and is not a repair"). `isActive()` is true only of `OBSERVING` (`:32`). Three of prompt §14's seven names — permission-required, bluetooth-disabled, unsupported — are **absent from this enum by decision** (ADR-P3-005) and asserted forbidden: `stageAnswersLifecycleAndNeverExplainsAFailure` (`DeviceObservationVocabularyTest.kt:127-136`) fails the build if `PERMISSION_DENIED`, `BLUETOOTH_DISABLED`, `UNSUPPORTED` or `FAILED` ever becomes a stage member. `STOPPED` is a stage rather than a failure because "a deliberately ended observation is a successful thing to have done" (`ObservationStage.kt:16-18`).
`platform/DeviceObservation.kt:12-18`: `ObservationArrival` {`SNAPSHOT`, `EVENT`} — "was this read in the union, or announced while we were reading it?" (§9 of the prompt), the fact that makes the ordering in §3 decidable.

### 2.5 `ObservationRound` — the answer of one round
`platform/ObservationStage.kt:45-54`, a sealed interface of three cases:

```kotlin
sealed interface ObservationRound<out T> {
    data class Success<T>(val devices: List<T>, val stage: ObservationStage) : ObservationRound<T>
    data class Failure<T>(val error: OmniBudsError) : ObservationRound<T>
    data object Cancelled : ObservationRound<Nothing>
}
```

Only `Success` has a device list, so the empty-vs-refused confusion prompt §14 forbids has no representation to arrive in (ADR-P3-005, ADR-P2-012). `Failure` and `Cancelled` are not the same value and `Cancelled` is not a category — asserted by `cancellationIsAnOutcomeCaseAndNeverAnErrorCategory`, which walks all 23 categories and fails on any name containing `CANCEL` (`DeviceObservationVocabularyTest.kt:171-182`). **The `stage` a source sets inside a `Success` is ignored and restated by the engine** (`ConnectedDeviceObserver.kt:359` returns `outcome.snapshot.stage`; `ADR-P3-015` rule 10): a source that claimed to know whether an observation was running would be reporting a fact outside its own view (`ConnectedDeviceSource.kt:105-107`, `AndroidConnectedDeviceSource.kt:114-117`). The test fixture proves it by scripting the wrong stage on purpose — `ConnectedDeviceObserverTest.kt:700-702` builds every round with `NOT_STARTED`.

### 2.6 `ObservedProfile` — the maintained enumeration union, seven members
`platform/ObservedProfile.kt:35-115`; constructor `(technicalName: String, introducedApiLevel: Int, reason: String)` (`:37, 43, 46`).

| Member | `technicalName` | `introducedApiLevel` | Why it is in the union (abridged from `reason`) | Framework constant |
|---|---|---|---|---|
| `HEADSET` | `profile.headset` | 11 | the call-service link; one of two classic audio profiles a device can hold independently (`:48-53`) | 1 |
| `A2DP` | `profile.a2dp` | 11 | media audio; documented one-device-at-a-time, "so its absence is news" (`:54-59`) | 2 |
| `GATT` | `profile.att-protocol` | 18 | the only way a never-bonded LE device is enumerable at all; answered by the manager object without a bind (`:60-65`) | 7 |
| `HID_DEVICE` | `profile.hid-device` | 28 | an input-report link held by devices neither audio profile lists (`:66-71`) | 19 |
| `HEARING_AID` | `profile.hearing-aid` | 29 | the regulated hearing-access path; binding gated on capability, not API level (`:72-78`) | 21 |
| `LE_AUDIO` | `profile.le-audio` | 33 | reports a set, and in the binaural case two independently transitioning entries for one product (`:79-84`) | 22 |
| `CSIP_SET_COORDINATOR` | `profile.coordinated-set-joiner` | 33 | what makes an LE Audio pair's two entries attributable to one set (`:85-90`) | 25 |

Constant values are read from the compileSdk 35 stub source, with the level each entry's *constant* arrived at rather than its class (ADR-P3-015 rule 1), and the table lives in the platform module (`SystemConnectedDeviceHandle.kt:564-588`) because `:core` may not carry framework numbers. Members: `fun availabilityAt(deviceSdk: Int?): ApiAvailability` (`:101`) delegating to `apiLevelSupports` (Phase 2, `ApiAvailability.kt:32-36`), and `val enumerationUnion: List<ObservedProfile> = entries.toList()` (`:113`). Asserted: `theEnumerationUnionCarriesItsReasonAndItsIntroductionLevel` (`ConnectedDeviceObserverTest.kt:633-655`) requires the union to equal `entries`, every `reason` non-blank, every level positive, `availabilityAt(null) == UNKNOWN`, and pins `LE_AUDIO` at `UNAVAILABLE` for 31 / `AVAILABLE` for 33. **Excluded, each with a reason** (`ObservedProfile.kt:22-33`, ADR-P3-015 rule 1): the deprecated health profile, profiles with a public constant but no public proxy type, the attribute-protocol server role, and everything absent from the compile SDK (research §3.2). The cost: a device held only by an excluded service is invisible to Phase 3 (§15 of `design.md`).

### 2.7 `ProfileObservationSupport` and `ProfileSupportReport`
`platform/ObservedProfile.kt:130-142`: `UNKNOWN` ("not asked, or asked and the answer did not arrive. Never collapsed into `NOT_ANSWERABLE`"), `ANSWERABLE` ("the mechanism bound and answered"), `NOT_ANSWERABLE` ("the platform declined the mechanism: no binding, an unsupported profile, or a service that went away"), with `isAnswerable()` (`:145`). The wording is about *answering* rather than the audit's proposed `NOT_SUPPORTED_BY_PLATFORM` because a handset can implement a profile and still decline the bind (research §3.3). `ProfileSupportReport(entries: Map<ObservedProfile, ProfileObservationSupport>)` (`:154-170`): `supportFor` falls back to `UNKNOWN` for an unkeyed entry (`:158-159`) so a profile an implementation forgot is not a profile that declined, and `unansweredWithin(enumerated)` (`:168-169`) qualifies the *caller's* list rather than the report's own.

### 2.8 `RefusalKind`
`platform/ConnectedDeviceObserver.kt:28-40`: `IMPOSSIBLE_TRANSITION` ("the move is one the platform cannot report, so the state the observer held is still held") and `UNATTRIBUTABLE_REPORT` ("the report named no device. Nothing was retained and nothing was applied"). Cancellation is **not** a refusal kind, and neither is a refusal of permission — those are outcomes (§2.5).

### 2.9 Raw-state enums, platform module only
`connection/ConnectedDeviceHandle.kt`:
- `RawLinkState` {`CONNECTED, CONNECTING, DISCONNECTING, DISCONNECTED, UNREADABLE`} (`:82-88`) — `UNREADABLE` "is not `DISCONNECTED`. It means a broadcast arrived without a value this code can transcribe, or a number outside the platform's own set" (`:78-80`).
- `RawBondState` {`BONDED, BONDING, NONE, UNREADABLE`} (`:90-96`).
- `ProfileAnswerability` {`NOT_REQUESTED, AWAITING_CALLBACK, ANSWERABLE, REFUSED`} (`:109-121`) plus `evidenceRank()` (`:132-137`), the combining order used when several channels answer differently: `NOT_REQUESTED` 0, `AWAITING_CALLBACK` 1, `REFUSED` 2, `ANSWERABLE` 3. ⚠ Its KDoc sentence (`:127-128`) and the call site comment (`SystemConnectedDeviceHandle.kt:85-87`) both describe a refusal as *weaker* than a bind still in flight; the numbers put it above. The ranks are the behaviour. §17.
- `ProfileEnumeration` sealed {`Reported(devices)`, `Unanswered`} (`:144-150`) — "a list is evidence and the absence of a mechanism is not".
Private to the implementation: `AnnouncementKind` {`LINK, BOND`} (`SystemConnectedDeviceHandle.kt:506`).

## 3. State machines

### 3.1 The platform link transition table, as built
`object DeviceConnectionStateTransitions` (`platform/DeviceConnectionState.kt:70-125`), a `Map<DeviceConnectionState, Set<DeviceConnectionState>>` (`:72-102`) plus three members.

```kotlin
fun isLegal(from: DeviceConnectionState, to: DeviceConnectionState): Boolean =
    from == to || legal.getValue(from).contains(to)                       // :105-106

fun refusalReason(from: DeviceConnectionState, to: DeviceConnectionState): String? =
    if (isLegal(from, to)) null
    else "the platform cannot report $from becoming $to; the observation was kept at $from"  // :115-120

val coveredStates: Set<DeviceConnectionState>                              // :123-124 — every row's key
```

| From → To | `UNKNOWN` | `CONNECTING` | `CONNECTED` | `DISCONNECTING` | `DISCONNECTED` |
|---|---|---|---|---|---|
| `UNKNOWN` | legal (self) | **legal** | **legal** | **legal** | **legal** |
| `CONNECTING` | **legal** | legal (self) | **legal** | **NEVER REPORTED** | **legal** |
| `CONNECTED` | **legal** | refused | legal (self) | **legal** | **legal** |
| `DISCONNECTING` | **legal** | refused | **NEVER REPORTED** | legal (self) | **legal** |
| `DISCONNECTED` | **legal** | **legal** | **legal** | refused | legal (self) |

Legend, because the refusals are not the same kind of thing: **NEVER REPORTED** marks the two moves the platform never makes and which the table exists to refuse — `CONNECTING → DISCONNECTING` and `DISCONNECTING → CONNECTED`; a lowercase *refused* marks one of the three moves that simply have no row in `legal` (`CONNECTED → CONNECTING`, `DISCONNECTING → CONNECTING`, `DISCONNECTED → DISCONNECTING`). Both kinds come back as the same refusal sentence, and only the two marked moves are the phase's claim about Android — and only those two are asserted by name (`DeviceObservationVocabularyTest.kt:46-58`).

Two properties differ from Phase 1's `ConnectionStateTransitions`, and both are the platform's rather than ours (`:52-68`). **`UNKNOWN` may be followed by any state**, because the first report about a device is whatever the stack happens to say (`:55-57`; asserted at `DeviceObservationVocabularyTest.kt:61-68`). **Two moves are refused because the platform does not make them**: `CONNECTING → DISCONNECTING` — a connection attempt reports connected or disconnected, not the other transition — and `DISCONNECTING → CONNECTED` without a settled report in between (`:57-60`; asserted with both a boolean and a non-null reason at `:46-58`). A same-state repeat is idempotent rather than illegal (`:62-64`, asserted for all five states at `:35-43`), because broadcast delivery promises neither uniqueness nor order. The table constrains *reports*, not inferences: "an engine that has decided a device is probably connecting now has not received a transition; it has fabricated one" (`:65-68`). Where the table and a fold disagree the fold obeys the table and publishes a refusal (`ConnectedDeviceObserver.kt:604-613, 639-647`), so a stale callback cannot assert a state the device was never in. **`UNKNOWN` may be followed by any state**, because the first report about a device is whatever the stack happens to say (`:55-57`; asserted at `DeviceObservationVocabularyTest.kt:61-68`). **Two moves are refused because the platform does not make them**: `CONNECTING → DISCONNECTING` — a connection attempt reports connected or disconnected, not the other transition — and `DISCONNECTING → CONNECTED` without a settled report in between (`:57-60`; asserted with both a boolean and a non-null reason at `:46-58`). A same-state repeat is idempotent rather than illegal (`:62-64`, asserted for all five states at `:35-43`), because broadcast delivery promises neither uniqueness nor order. The table constrains *reports*, not inferences: "an engine that has decided a device is probably connecting now has not received a transition; it has fabricated one" (`:65-68`). Where the table and a fold disagree the fold obeys the table and publishes a refusal (`ConnectedDeviceObserver.kt:604-613, 639-647`), so a stale callback cannot assert a state the device was never in.

### 3.2 The bond and availability axes have no table
`DeviceBondState` and `DeviceAvailability` carry no `canTransition`: the bond axis moves only on a positive report (`withBondEvent`, `:691`) and the availability axis moves only by engine rule (§3.3's slots and §12 of `design.md`). That is a deliberate difference from §3.1, not an omission — no platform mechanism in Phase 3 announces an availability change, so there is no sequence to make illegal.

### 3.3 The slot machine, and the projection's stage
`unlocked → tryLock succeeds → OBSERVING → finally { cancel pumps; dispose under NonCancellable; stage = STOPPED; unlock if held }`, with the parallel `unlocked → tryLock fails → one ObservationRound.Failure(RESOURCE_UNAVAILABLE) → closed` (`ConnectedDeviceObserver.kt:206-318`). `isObserving` reads `slot.isLocked` (`:178-179`) and `stage` reads the projection (`:182-183`). Terminal states: `STOPPED` is not terminal — a restart is legal and is not a repair (`ObservationStage.kt:27-28`). The projection's own lifecycle is `NOT_STARTED → OBSERVING → STOPPED → OBSERVING …` and no failure reason ever enters it (ADR-P3-005).

## 4. `DeviceObservationKey` — the join rule and the redaction guarantee

```kotlin
// platform/DeviceObservationKey.kt:29-104
sealed interface DeviceObservationKey {
    val canIdentifyAcrossObservations: Boolean                                   // :38

    class AddressBacked private constructor(private val value: String) : DeviceObservationKey {  // :50
        override val canIdentifyAcrossObservations: Boolean get() = true         // :51-52
        override fun equals(other: Any?): Boolean                                // :54
        override fun hashCode(): Int                                             // :56
        override fun toString(): String                                          // :58-59
        companion object { fun from(reported: String?): AddressBacked? }         // :69-73
    }

    data object NotReported : DeviceObservationKey {                             // :83
        override val canIdentifyAcrossObservations: Boolean get() = false        // :84-86
        override fun toString(): String = "DeviceObservationKey.NotReported"     // :87
    }

    companion object { fun ofReportedAddress(reported: String?): DeviceObservationKey }  // :101-102
}

fun DeviceObservationKey.joinableWith(other: DeviceObservationKey): Boolean       // :114-115
fun DeviceObservationKey.isUnattributable(): Boolean                              // :118
```

**The join rule, stated once** (`:114-115`): `canIdentifyAcrossObservations && other.canIdentifyAcrossObservations && this == other`. Equality alone is *not* the join, because `NotReported` equals itself and two unkeyed records are two devices (prompt §12); `DeviceObservationKeyTest.kt:93-105` asserts exactly that pair — `assertEquals(first, second)` **and** `assertFalse(first.joinableWith(second))`. Every engine lookup goes through `joinableWith` (`ConnectedDeviceObserver.kt:553, 628, 679, 708`), never through a map keyed by equality; `AddressBacked` *is* usable as a `LinkedHashMap` key for the fold (`:534, 540`), which `aKeyCanBeUsedAsAMapKeyWithoutAnyoneReadingTheAddress` pins (`DeviceObservationKeyTest.kt:119-128`).

**The redaction guarantee.** `value` is `private` and has no getter (`:50`); `toString()` is the only readable form and it states kind and length — the exact text is `DeviceObservationKey.AddressBacked(kind=link-address, length=17)` for the 17-character fixture, pinned at `:131-140`; `equals`/`hashCode` use the value, so redaction costs nothing in joining (`:54-56`). `DeviceObservation.reported` is the only route a platform adapter should use and `AddressBacked.from` the only route to a key (`:61-74`), which is what keeps the blank-text demotion from being a convention a call site can skip.

**Normalisation, and what is not rewritten** (`:93-100`): trim, then `takeIf { isNotEmpty() }`, then `uppercase()`. Blank, whitespace-only and null all yield `NotReported` — `""` is one of the falsy platform returns ADR-P2-012 records, and making it a key would give every silent device one shared identity (`:96-98`; asserted over `""`, `"   "`, `"\t\n "` and `null` at `DeviceObservationKeyTest.kt:81-90`). Case and padding differences between two mechanisms reporting one device still join (`:70-78`). This is a normalisation of the *comparison*, performed once, and not a rewrite of what was observed.

**Not identity** (`:24-27`): `device/DeviceIdentity` has no address field and gains none; an address that may be a rotating random value on a LE link is not a statement about which device something is. The stability question is **U-2** (research §5.4), and the designed consequence of a wrong answer is "the same product appears twice", never "two products became one".

## 5. Data models

### 5.1 `DeviceObservation`
```kotlin
// platform/DeviceObservation.kt:44-75 — eight fields, in this order, no defaults
data class DeviceObservation(
    val key: DeviceObservationKey,
    val displayName: String?,
    val link: DeviceConnectionState,
    val bond: DeviceBondState,
    val availability: DeviceAvailability,
    val observedProfiles: Set<ObservedProfile>,
    val arrival: ObservationArrival,
    val observedAtEpochMillis: Long?,
)
```
Derived: `isAttributable` (`:77-78`), `isReportedConnected` = `availability.isObservable() && link.isConnected()` (`:81-82`) — both halves, so an unobservable device cannot appear in `connectedDevices`; `hasReportedName` (`:85-86`). The factory:

```kotlin
fun reported(
    key: DeviceObservationKey,
    link: DeviceConnectionState,
    bond: DeviceBondState,
    availability: DeviceAvailability,
    observedProfiles: Set<ObservedProfile> = emptySet(),
    displayName: String? = null,
    arrival: ObservationArrival = ObservationArrival.SNAPSHOT,
    observedAtEpochMillis: Long? = null,
): DeviceObservation                                                        // :129-147
```

The primary constructor stays public because "a domain type may not silently rewrite evidence", and the blank-name demotion lives in the factory instead (ADR-P3-015 rule 12, `:122-128`) — which is why `hasReportedName` exists as a guard: a directly-constructed record *can* hold `"   "`. `normalisedText` (`:157`) is the trim-and-demote rule, the second copy of `DeviceIdentity`'s because layer 1 may not import layer 2 (`:149-156`).

`mergedWith` (`:103-118`) is the only merge the model offers and it `require`s a joinable pair (`:104-106`, message "two observations that cannot be attributed to one device must not be merged", asserted against both a different address and a shared absence at `ConnectedDeviceObserverTest.kt:678-698`). Its conflict rules are ranks, not first-wins: `linkRank` `UNKNOWN 0 < DISCONNECTED 1 < DISCONNECTING 2 < CONNECTING 3 < CONNECTED 4` (`:201-207`) so `UNKNOWN` never overrules a report (`:191-199`); `bondRank` `UNKNOWN 0 < NONE 1 < BONDING 2 < BONDED 3` (`:172-177`); `availabilityRank` `UNKNOWN 0 < UNAVAILABLE 1 < AVAILABLE 2` (`:179-183`); names fill gaps only (`:109`); profiles union (`:113`); the **incoming** record's `arrival` wins, because the merge runs in arrival order (`:114-115`); timestamps take the later non-null reading (`:185-189`). Nothing averages and nothing splices a hybrid: a `BONDED` report keeps that answer against a `NONE` one, "because two mechanisms disagreeing is a finding for a human" (`:96-101`).

Deliberately absent, with the reason in the file: manufacturer, model, device class, UUIDs, any earbud-side classification (`:30-37`), because prompt §16 forbids the inference and research §5.2 forecloses the principled version — the platform's own class matcher "errs on the side of false positives".

### 5.2 `ConnectedDeviceSnapshot` — the projection, and what each boolean prevents
```kotlin
// platform/ConnectedDeviceObserver.kt:68-128
data class ConnectedDeviceSnapshot(
    val stage: ObservationStage,                    // :70  lifecycle only; a failure reason never appears here
    val records: List<DeviceObservation>,           // :73  everything reported and not forgotten
    val unansweredProfiles: Set<ObservedProfile>,   // :81  profiles whose silence proves nothing
    val profileSupportConsulted: Boolean,           // :84
    val restsOnCompletedRound: Boolean,             // :93
    val refusedTransitions: List<RefusedDeviceTransition>,   // :96  newest last, capped at REFUSAL_WINDOW
    val observedAtEpochMillis: Long?,               // :99
) {
    val connectedDevices: List<DeviceObservation>          // :102-103  records.filter { it.isReportedConnected }
    val isUnionComplete: Boolean                    // :107-108
    companion object { fun notStarted(enumerated: List<ObservedProfile>): ConnectedDeviceSnapshot }  // :118-126
}
```
| Member | What it prevents |
|---|---|
| `unansweredProfiles` | reading a union over silent profiles as a census. Starts as **every** enumerated profile, before the platform has answered anything (`:110-126`) |
| `profileSupportConsulted` | reading that opening default as the platform's answer. Set once, at `:443-450`, after `source.profileSupport()` returns `Success` |
| `restsOnCompletedRound` | the case the other two miss: a projection populated only by **announcements**, or one whose every round was refused, could otherwise report a complete union of nobody. Set only by a fold that ran (`:561`); the two are different questions because "a refusal leaves it as it found it" (`:86-92`) |
| `refusedTransitions` | swallowing a report the engine could not apply — prompt §11. Bounded at 8 (`REFUSAL_WINDOW`, `:750`) because an unbounded window is the device history prompt §10 forbids |
| `stage` | putting a failure reason in a lifecycle field (ADR-P3-005) |
| `observedAtEpochMillis` | a projection that looks like it has a reading when the clock gave none (`:99`, `:422`, `:563`) |

`isUnionComplete` (`:107-108`) is the conjunction: consulted **and** rests on a completed round **and** `unansweredProfiles.isEmpty()`. `RefusedDeviceTransition` (`:49-56`) carries `key`, `kind`, `retained: DeviceConnectionState?`, `attempted: DeviceConnectionState?`, `reason`, `refusedAtEpochMillis: Long?` — both states nullable "when there was no record to retain" and "when the refused report carried no link state at all", so an absence is never stated as a plausible value (`:43-48`).

### 5.3 Event and report types
```kotlin
// platform/ConnectedDeviceSource.kt:22-55
sealed interface DeviceConnectionEvent {
    val key: DeviceObservationKey                                   // :24
    val observedAtEpochMillis: Long?                                // :27
    data class LinkChanged(key, profile: ObservedProfile?, link: DeviceConnectionState, observedAtEpochMillis) : …  // :38-43
    data class BondChanged(key, bond: DeviceBondState, observedAtEpochMillis) : …                                    // :50-54
}
// connection/ConnectedDeviceHandle.kt:160-197
data class DeviceReport(val reportedAddress: String?, val reportedName: String?,
                        val link: RawLinkState, val bond: RawBondState)
sealed interface DeviceAnnouncement {
    data class Link(reportedAddress: String?, profile: ObservedProfile?, link: RawLinkState) : …    // :187-191
    data class Bond(reportedAddress: String?, bond: RawBondState) : …                                // :193-196
}
```
An event carries **no display name** — the platform's name is a cache read that scanning fills and Phase 3 does not scan, so a field here would make absence look like a fact about the device (`ConnectedDeviceSource.kt:17-21`). `profile` is nullable because the link-layer announcements name no service, and that null is a *rule*, not a hole: the engine reads a link-layer disconnect as ending every reported profile (ADR-P3-015 rule 3, `:28-37`). `DeviceReport`/`DeviceAnnouncement` speak raw (design §2) and `reportedAddress` is nullable rather than filtered in the receiver, because a broadcast that named nobody is something that happened and dropping it would be prompt §11's silent swallow (`ConnectedDeviceHandle.kt:174-185`).

One internal union completes the picture: `private sealed interface Arrival` (`ConnectedDeviceObserver.kt:761-764`), `Arrival.Announcement(DeviceConnectionEvent)` and `Arrival.AdapterSwitch(BluetoothAdapterState)`, is how the two streams share one ordered queue (`:220, 245-264`) so the single read loop at `:298-304` applies them in the order they arrived. It is private to the engine and deliberately not vocabulary a consumer can hold.

## 6. Public interfaces — `:core`, signatures as written

```kotlin
// platform/ConnectedDeviceSource.kt:90-131
interface ConnectedDeviceSource {
    suspend fun profileSupport(): OperationOutcome<ProfileSupportReport>          // :100
    suspend fun snapshot(): ObservationRound<DeviceObservation>                   // :114
    suspend fun openConnectionEvents(): OperationOutcome<ConnectedDeviceEventChannel>  // :125
    companion object { fun unavailable(): ConnectedDeviceSource }                  // :129
}

// platform/ConnectedDeviceSource.kt:58-70
interface ConnectedDeviceEventChannel {
    val registration: PlatformRegistration                                        // :59
    val events: Flow<DeviceConnectionEvent>                                       // :69  cold, by contract
}

// platform/ConnectedDeviceObserver.kt:163-193, :206, :327
class ConnectedDeviceObserver(
    private val source: ConnectedDeviceSource,
    private val adapterStates: AdapterStateSource,
    private val enumeratedProfiles: List<ObservedProfile> = ObservedProfile.enumerationUnion,
    private val time: TimeProvider = NoTimeProvider,
) {
    val snapshot: StateFlow<ConnectedDeviceSnapshot>                               // :175
    val isObserving: Boolean                                                       // :178-179
    val stage: ObservationStage                                                    // :182-183
    var teardownProblem: OmniBudsError?; private set                               // :192-193
    fun observe(): Flow<ObservationRound<DeviceObservation>>                       // :206  cold channelFlow
    suspend fun refresh(): ObservationRound<DeviceObservation>                     // :327
}
```

**The contract, and the impossibility its return types create.** ADR-P3-009's finding is that the platform cannot separate a refusal from an empty room, and "that cannot be enforced by a rule written in this file's prose, so it is not written there: `snapshot()` returns an `ObservationRound`, which has no representation for 'a list, from a round that was not allowed to produce one'" (`ConnectedDeviceSource.kt:79-89`). The three obligations an implementation owes, stated where they live: `profileSupport()` is asked **once per observation, before the snapshot**, and a refusal there is a `Failure`, not an empty report — "an empty report is a real answer about a handset with no profiles, and the two must not be the same value" (`:91-99`); `snapshot()` — a source "may only return that after its standing check passed, and it must not substitute an empty list for a refusal it could not distinguish" (`:108-113`); `openConnectionEvents()` hands back the registration so the owner can prove the teardown happened even if the stream was never collected, because a bound profile service that outlives its observer is a resource this phase cannot reclaim (`:116-124`, ADR-P3-008's consequence).

`refresh()` is prompt §14's "refresh" made typed: it re-reads and restates **without taking the slot** and opens nothing (`:320-327`; asserted at `ConnectedDeviceObserverTest.kt:600-614`, which checks `snapshotCalls == 1`, `openCalls == 0`, `stage == NOT_STARTED` and `isObserving == false`), and its result is the round itself so "a refresh that 'succeeds' while returning nothing must not read as a successful observation of zero devices" (ADR-P3-004).

`ConnectedDeviceSource.unavailable()` (`:129`, backed by the private object at `:142-156`) answers all three questions with `Failure(ADAPTER_UNAVAILABLE)`, operationId `device-source.unavailable`, detail "no connected-device source was available" — the Phase 2 audit's R-6 discipline (a wiring fact is not a hardware negative), and it is asserted never to answer with an empty list (`ConnectedDeviceObserverTest.kt:617-630`).

## 7. Public interfaces — the Android handle seam

```kotlin
// connection/ConnectedDeviceHandle.kt:29-73
interface ConnectedDeviceHandle {
    val adapterPresent: Boolean                                                     // :31
    fun answerability(profile: ObservedProfile): ProfileAnswerability               // :42
    fun enumerate(profile: ObservedProfile): ProfileEnumeration                     // :53
    fun openAnnouncements(profiles: List<ObservedProfile>,
                          emit: (DeviceAnnouncement) -> Unit): PlatformRegistration  // :69-72
}

// connection/AndroidConnectedDeviceSource.kt:73-81
class AndroidConnectedDeviceSource(
    private val handle: ConnectedDeviceHandle,
    private val permissionProvider: AndroidPermissionStateProvider,
    private val apiLevel: ApiLevelProvider,
    private val targetSdk: TargetSdkProvider,
    private val time: TimeProvider = SystemTimeProvider,
    private val enumeratedProfiles: List<ObservedProfile> = ObservedProfile.enumerationUnion,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ConnectedDeviceSource

// connection/SystemConnectedDeviceHandle.kt:56-59
class SystemConnectedDeviceHandle(
    private val context: Context,
    private val apiLevel: ApiLevelProvider,
) : ConnectedDeviceHandle
```

Four members "because four questions are authorised", and the members that were not added — pair, connect, enable, ask a device for something — are named in the file's own KDoc as a fifth that would be "a capability this phase forbids behind a name that sounds like a read" (`ConnectedDeviceHandle.kt:23-27`; architecture audit §10, research §8.2). `openAnnouncements` deliberately fuses receiver registration and proxy binding into one call and one registration, because both halves are the same resource and a bind started outside it would be a proxy with no owner left to close it (`:55-68`). The implementation must make disposal idempotent and complete — it releases the receiver *and* every proxy this call bound, once — because Android throws at an unregister it does not recognise and a bound service outlives the observer that asked for it (`:62-67`; realised at `SystemConnectedDeviceHandle.kt:237-259`).

## 8. The Android mapping tables

### 8.1 Raw state → domain state (total, no `else` that could swallow a new value)
`AndroidConnectedDeviceSource.kt:290-305`, private functions in the companion.

| Raw | Domain | | Raw | Domain |
|---|---|---|---|---|
| `RawLinkState.CONNECTED` | `DeviceConnectionState.CONNECTED` | | `RawBondState.BONDED` | `DeviceBondState.BONDED` |
| `RawLinkState.CONNECTING` | `DeviceConnectionState.CONNECTING` | | `RawBondState.BONDING` | `DeviceBondState.BONDING` |
| `RawLinkState.DISCONNECTING` | `DeviceConnectionState.DISCONNECTING` | | `RawBondState.NONE` | `DeviceBondState.NONE` |
| `RawLinkState.DISCONNECTED` | `DeviceConnectionState.DISCONNECTED` | | `RawBondState.UNREADABLE` | `DeviceBondState.UNKNOWN` |
| `RawLinkState.UNREADABLE` | `DeviceConnectionState.UNKNOWN` | | | |

`UNREADABLE → UNKNOWN`, never `DISCONNECTED` or `NONE`: "reading it as DISCONNECTED would turn a broadcast that arrived without a state extra into an announcement that a link went down" (`:295-297`). The raw values come from `rawLinkStateOf`/`rawBondStateOf` (`SystemConnectedDeviceHandle.kt:457-470`), whose `else ->` arm is reached by any integer outside the platform's own sets and by the sentinel `RAW_EXTRA_UNREADABLE = -1` (`:438`), chosen to sit outside every platform range. An announcement's profile is passed through unchanged, so "names no service" survives to the engine as the rule it is (`AndroidConnectedDeviceSource.kt:202-211`; `AndroidConnectedDeviceSourceTest.kt:483-499`).

### 8.2 Standings → refusal → category
`AndroidConnectedDeviceSource.kt:257-279`, via Phase 2's `AndroidPermissionStateProvider.stateFor` (`permission/AndroidPermissionStateProvider.kt:75-96`) and `FrozenPermissionRequirementResolver`.

| `stateFor(operation, permission, context)` answer | Round outcome | Category |
|---|---|---|
| `GRANTED` for `BLUETOOTH_CONNECT` | clears, the work proceeds | — (`:275`) |
| `NOT_REQUIRED` for the modern row **and** `GRANTED`/`NOT_REQUIRED` for `BLUETOOTH` | clears | — (`:276-278`) |
| `DENIED`, `NOT_REQUESTED`, `UNKNOWN`, `DENIED_PERMANENTLY`, `REQUIRES_USER_ACTION` | refused before any platform question | `PERMISSION_DENIED` (`:260-266`) |
| resolver plan `Failure`/`Cancelled` (e.g. unknown `targetSdk`) | becomes `UNKNOWN` in the provider, therefore refused | `PERMISSION_DENIED` — the cause is not relabelled as `INVALID_STATE` here (`:258` + `AndroidPermissionStateProvider.kt:91-95`) |
| standing reader throws | treated as a refusal, not as a platform exception | `PERMISSION_DENIED` (`:258`, KDoc `:252-256`) |

Operations consulted: `PROFILE_CONNECTION_STATE_INSPECTION` for `profileSupport()` (`:84`), `CONNECTED_DEVICE_INSPECTION` for both `snapshot()` and `openConnectionEvents()` (`:97, 135`), and `BONDED_DEVICE_LIST_INSPECTION` for `bondedDevices()` (`:158`) — three entry points, three standings, each settled by `refusalFor` before any framework question is asked (ADR-P3-009, ADR-P3-017).

### 8.3 Per-profile answerability, the two gates in order
`AndroidConnectedDeviceSource.kt:227-240`, and the handle's own view at `SystemConnectedDeviceHandle.kt:77-90, 176-183`.

| Gate 1 — `ObservedProfile.availabilityAt(deviceSdk)` | Gate 2 — `handle.answerability(profile)` | `ProfileObservationSupport` |
|---|---|---|
| `UNAVAILABLE` | (not asked — the OS answer makes it moot) | `NOT_ANSWERABLE` |
| `UNKNOWN` | (not asked) | `UNKNOWN` |
| `AVAILABLE` | `ANSWERABLE` (bound proxy, or `GATT` with a live manager) | `ANSWERABLE` |
| `AVAILABLE` | `REFUSED` (bind returned false, listener gave null, or `onServiceDisconnected`) | `NOT_ANSWERABLE` |
| `AVAILABLE` | `NOT_REQUESTED` | `UNKNOWN` |
| `AVAILABLE` | `AWAITING_CALLBACK` | `UNKNOWN` |

The order is the reason a profile the running OS has no class for is declined "without asking the handset" (`:220-226`; test `AndroidConnectedDeviceSourceTest.kt:217-238`). `GATT` is special-cased to answer from the manager object with no binding at all (`SystemConnectedDeviceHandle.kt:81-83`), which is why it never reaches the binder. A refusal to answer the *whole* report is an `OperationOutcome.Failure`, never an empty `ProfileSupportReport` (`ConnectedDeviceSource.kt:94-99`).

### 8.4 The action table, and what it excludes
`SystemConnectedDeviceHandle.kt:520-554` builds eight entries of `Announcement(action, profile, kind, fixedState)` (`:557-562`); `fixedState` overrides any extra for the two link-layer announcements (`:457`, `rawLinkStateOf(fixed ?: raw)`).

| Action constant | `profile` | `kind` | `fixedState` | Line |
|---|---|---|---|---|
| `BluetoothDevice.ACTION_ACL_CONNECTED` | none | LINK | `STATE_CONNECTED` | 521 |
| `BluetoothDevice.ACTION_ACL_DISCONNECTED` | none | LINK | `STATE_DISCONNECTED` | 522-527 |
| `BluetoothDevice.ACTION_BOND_STATE_CHANGED` | none | BOND | none | 528 |
| `BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED` | `A2DP` | LINK | none | 529 |
| `BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED` | `HEADSET` | LINK | none | 530-535 |
| `BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED` | `LE_AUDIO` | LINK | none | 536-541 |
| `BluetoothHearingAid.ACTION_CONNECTION_STATE_CHANGED` | `HEARING_AID` | LINK | none | 542-547 |
| `BluetoothCsipSetCoordinator.ACTION_CSIS_CONNECTION_STATE_CHANGED` | `CSIP_SET_COORDINATOR` | LINK | none | 548-553 |

Excluded by name, each with the reason in the comment at `:440-453`: `BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED` (an aggregate edge with no device extra — research §2b, U-5); `ACTION_ACL_DISCONNECT_REQUESTED` (a request is not a change of state); `ACTION_NAME_CHANGED`, `ACTION_CLASS_CHANGED`, `ACTION_ALIAS_CHANGED` (no link fact, and every descriptor is re-read at the next snapshot — and the class one especially, "since a reader would be tempted to finish the inference prompt section 16 forbids"); and `HID_DEVICE`, which has **no public connection-state action in the compile SDK** and so is enumerable but never announceable. Extras are read as `EXTRA_DEVICE` (nullable, never guessed, `:369`), `BluetoothProfile.EXTRA_STATE` (`:376`) and `BluetoothDevice.EXTRA_BOND_STATE` (`:386`), each against `RAW_EXTRA_UNREADABLE`.

### 8.5 The enumeration's state filter
`SystemConnectedDeviceHandle.kt:429-433`: `LINKED_STATES = intArrayOf(STATE_CONNECTED, STATE_CONNECTING, STATE_DISCONNECTING)`. `STATE_DISCONNECTED` is **absent on purpose** (`:419-428`): asking for the disconnected set "would put every device a handset has ever kept a profile record for into the projection — a larger claim than was asked for, and one that would read as a device census". Absence from the union becomes a disconnect only under ADR-P3-015 rule 4, which the engine owns (§12 of `design.md`); this list and that rule must be read together. No `getBondedDevices()` call exists anywhere in the production sources, so the bond axis is populated only by per-device `getBondState()` reads on enumerated devices (`:496`) and by `ACTION_BOND_STATE_CHANGED` (§8.4).

### 8.6 The bond-list read: one question, four platform worlds, and a refusal for each
`ConnectedDeviceSource.bondedDevices()` (`core/.../platform/ConnectedDeviceSource.kt:147`) is Phase 3's second standing-gated question and prompt §10's collection B; `AndroidConnectedDeviceSource.kt:158-201` is its only producer, and `SystemConnectedDeviceHandle.kt:153-161` the only caller of `getBondedDevices()`. The seam's shape is the argument: the platform answers four different worlds with one empty collection or a `null` — its own Javadoc says "If Bluetooth state is not STATE_ON, this API will return an empty set" and "@return unmodifiable set of `BluetoothDevice`, or null on error", and the reference implementation answers empty to a refused caller as well — so ADR-P3-009's rule can only be honoured by a type that can still tell the four apart (`BondedListing`, `connection/ConnectedDeviceHandle.kt:195-206`).

| Handle answer | Reached only when | Round returned | Category | The claim the refusal preserves |
|---|---|---|---|---|
| standing refused | `refusalFor(BONDED_DEVICE_LIST_INSPECTION)` is non-null, checked before the adapter is read | `Failure` | `PERMISSION_DENIED` | the app was not allowed to look, which is not a statement about pairings |
| `BondedListing.AdapterAbsent` | `manager.adapter` was null | `Failure` | `ADAPTER_UNAVAILABLE` | "an absent adapter is not a phone with no pairings" — said in the `detail` verbatim |
| `BondedListing.AdapterNotOn` | `adapter.state` read, and was not `STATE_ON` | `Failure` | `BLUETOOTH_DISABLED` | the platform's empty set here is contractual, so it says nothing |
| `BondedListing.ReadFailed` | the call returned `null`, or threw | `Failure` | `RESOURCE_UNAVAILABLE` | the platform's error answer, not a census |
| `BondedListing.Reported(devices)` | adapter present **and** reading itself on **and** a set came back | `Success` | — | the one case where an empty list means a phone with no pairings |

`Reported` is the only case that reaches `ObservationRound.Success`, and that is the gate the phase's central confusion would have arrived through. Records are transcribed through `BondedDeviceObservation.reported` (`core/.../platform/DeviceObservation.kt:226-270`), which carries exactly four fields — `key`, `displayName`, `bond`, `observedAtEpochMillis` — and no link, profile set, availability axis, device class, manufacturer or last-seen; the absence is prompt §6's prohibition made structural, since a bond entry is a stored link key and says nothing about a connection. The fold is three functions in `ConnectedDeviceObserver`: `pairedCensusOf` (`:497-508`) joins by key and keeps unkeyed records separate, `foldingPaired` (`:472-487`) **replaces** `pairedDevices` on an answered round and on a refused one keeps the entries while setting `pairedRoundRefusal` so `isPairedCensus` (`:161-163`) goes false, and `creditingBondAxis` (`:520-527`) applies `DeviceObservation.creditingBond` (`:159-160`) to joinable records only. There is no function in this file that can put a paired-only device into `records`, which is why prompt §10.A and §10.B cannot be conflated by a consumer that forgot to filter. Proven by `BondedDeviceObservationTest` (17) and the ten bond cases in `AndroidConnectedDeviceSourceTest` (39).

## 9. Error contracts

23 categories, unchanged by Phase 3 (ADR-P3-006); `OmniBudsErrorCategory.kt:20-47` and the exhaustive pair in `OmniBudsErrorCategoryTest.kt:40-128`. This phase's column — what it actually produces, and with what `detail`.

| Category | retryClass / invalidatesSession | Produced in Phase 3 by | `detail` shape |
|---|---|---|---|
| `PERMISSION_DENIED` | NEVER_RETRY / false | `AndroidConnectedDeviceSource.kt:260-266` — before every platform question, all three entry points | fixed sentence naming `operation.technicalName` (`:263`) |
| `BLUETOOTH_DISABLED` | RETRY_AFTER_REREAD / false | `ConnectedDeviceObserver.kt:427-431` on `isProvablyDisabled()` | fixed sentence + a **record count** + the non-inference disclaimer (`:433-435`) |
| `ADAPTER_UNAVAILABLE` | RETRY_AFTER_REREAD / false | `ConnectedDeviceObserver.kt:430` (adapter `UNAVAILABLE`), `AndroidConnectedDeviceSource.kt:106-111` (no adapter on the phone), `ConnectedDeviceSource.kt:151-155` (unwired seam) | fixed sentences about the seam |
| `RESOURCE_UNAVAILABLE` | RETRY_AFTER_REREAD / false | `ConnectedDeviceObserver.kt:210-215` (slot held), `:371-376` (empty union nothing could see), `:498-503` (teardown incomplete) | fixed sentence + first cause class + release counts |
| `PLATFORM_EXCEPTION` | RETRY_AFTER_REREAD / true | `AndroidConnectedDeviceSource.kt:281-285` only (`profile-support`, `device-snapshot`, `device-events`) | `"the $what reported ${problem::class.simpleName}"` — the message is **not** carried, because a framework exception text can contain a device address (`:68-71`) |
| `UNKNOWN_FAILURE` | NEVER_RETRY / true | nothing in main source; the scripted queue-ran-dry case (`FakeConnectedDeviceSource.kt:66-74`) | test-only |
| the other 17 | as pinned in Phase 2 §8 | **nothing** — including `DEVICE_DISCONNECTED`, `CONNECTION_UNAVAILABLE`, `UNSUPPORTED_OPERATION` (explicitly refused as the empty-union mapping, ADR-P3-015 rule 7), `UNKNOWN_DEVICE` (ADR-P3-006: no `DEVICE_NOT_FOUND` twin) and `TIMEOUT` (nothing here awaits a device, §11) | — |

Rules realised: `OperationOutcome` (`common/OperationOutcome.kt:16-44`) and `ObservationRound` are different types for different subjects — a *round* is an observation, an *outcome* is a question — and both keep `Cancelled` a case; a failure never becomes a fabricated value, so every "could not look" leaves a field null or an axis `UNKNOWN`; `detail` never carries an address, a name or manufacturer data (`common/OmniBudsError.kt:11-13`), and no Phase 3 `detail` string interpolates device data at all; `attempts >= 1` is required and is never incremented (`:20-24`); `invalidatesSession` stays delegated to the category, which keeps Phase 2's recorded mismatch intact — `PLATFORM_EXCEPTION` says `true` for host-side reads that cannot invalidate any device session.

## 10. Coroutine, cancellation and Flow contracts

| Contract | As built | File |
|---|---|---|
| Suspended entry points | all three `ConnectedDeviceSource` members, `PlatformRegistration.dispose`, `refresh()`, `reconcileSnapshot`/`reconcileAnnouncement`/`reconcileAdapterSwitch`/`updateProjection`/`touchProjection`/`disposeAll` | `ConnectedDeviceSource.kt:100,114,125`, `ConnectedDeviceObserver.kt:327,341,379,407,443,452,457,466,482` |
| Dispatcher | never a `:core` signature; confined to the platform module, defaulted to `Dispatchers.IO`, every framework read inside `withContext` | `AndroidConnectedDeviceSource.kt:80,83,96,134` |
| Scope | none declared anywhere; `observe()` launches three children of its own `channelFlow` scope and joins them via the closer | `ConnectedDeviceObserver.kt:245-247, 262-264, 268-272` |
| Flow shapes | the projection is a `StateFlow` (`MutableStateFlow` + `asStateFlow`) — prompt §14's `observationState`, and Phase 0 §5 rule 5's "latest value matters" half; the rounds stream is a cold `channelFlow`, which is what makes "collecting registers, losing the collector unregisters" expressible; the event stream is a `receiveAsFlow` over an unbounded channel | `ConnectedDeviceObserver.kt:172-175, 206`, `AndroidConnectedDeviceSource.kt:138, 322` |
| Buffering and overflow | announcement channel `Channel.UNLIMITED` — "announcements are transitions, not a latest-wins value… the volume is a handful of earbuds rather than a stream"; engine arrival channel `Channel.UNLIMITED`; no `MutableSharedFlow`, no replay, no `buffer`, no `flowOn` anywhere | `AndroidConnectedDeviceSource.kt:129-131, 138`, `ConnectedDeviceObserver.kt:220` |
| ⚠ Phase 0 §5 rule 5, cold flows | the rule forbids a cold flow *for device state*; the device state is now a `StateFlow`, so that half is met, while the **rounds** surface stays cold and **no ADR in 001-016 amends the rule** — carried forward from Phase 2 `design.md` §12, not resolved | — |
| Non-suspending producer | `emit` is a plain `(DeviceAnnouncement) -> Unit` handed into the handle, delivered with `trySend` from `onReceive`; the result is discarded, so a delivery after the channel is closed is lost rather than thrown | `AndroidConnectedDeviceSource.kt:139-141`, `SystemConnectedDeviceHandle.kt:134-137, 361-391` |
| Cancellation | forwarded as `ObservationRound.Cancelled` and never relabelled (ADR-P2-018); a cancelled *first* round ends the observation, a mid-stream cancellation of one read ends nothing; the slot and every registration still release | `ConnectedDeviceObserver.kt:239-242, 256-259, 281-284, 290-296, 305-317` |
| Shared mutable state | the observer's two mutexes, its `MutableStateFlow`, its `teardownProblem`; the handle's `CopyOnWriteArrayList`, per-channel `LinkedHashMap` guarded by `synchronized`, one `AtomicBoolean` open-gate per channel plus a `@Volatile` registration flag; every Phase 3 data model is an immutable value | `ConnectedDeviceObserver.kt:169-172, 192`, `SystemConnectedDeviceHandle.kt:72, 163-167, 188-189, 243-247, 304-313` |
| Main-thread safety | declared in the class KDoc and delivered by the default dispatcher; the framework's own callback arrives on the main thread, which is why `Binding.proxy`/`refused`/`connected` are `@Volatile` | `AndroidConnectedDeviceSource.kt:62-66`, `SystemConnectedDeviceHandle.kt:300-313` |

## 11. Timeouts, retries and validation rules

**No timeout mechanism exists, and that is the specification.** No `withTimeout`, `timeout(`, `delay`, `Timer` or `Handler` appears in either module's main source; nothing in Phase 3 awaits a *device*, and the two awaits that exist — a profile bind and a broadcast — are reported as unanswered rather than bounded. `ADR-P3-008` bans polling and `SystemConnectedDeviceHandle.kt:44-47` explains why nothing blocks on the bind: the platform promises no completion for a `true` return, so waiting on one would turn an UNVERIFIED promise into this project's own hang. A binder call that hangs would hang the `Dispatchers.IO` thread it is on; that is the honest state of the phase, and the bound belongs to the phase that first exchanges bytes (as in Phase 2 §10).

| Operation class | Timeout | Retry | Budget | Read-back | Validation rule |
|---|---|---|---|---|---|
| Standing read (`stateFor`) | none | none — a refusal is reported once | 1 | n/a | a throwing reader is a refusal, not an exception (`AndroidConnectedDeviceSource.kt:258`) |
| Profile-support question | none | none; the next round asks again | 1 | n/a | a `Failure` here is never an empty report (`ConnectedDeviceSource.kt:94-99`) |
| Snapshot union | none | none; `refresh()` is the caller's decision | 1 | n/a | empty `Success` refused when nothing answered (`ConnectedDeviceObserver.kt:349-350, 508-511`) |
| Announcement stream | n/a — unbounded by construction, ended by the collector | n/a | n/a | n/a | duplicate announcements republish nothing (`:668-670`) |
| Teardown (`dispose`) | none | none, deliberately: a half-torn-down registration is recorded in `teardownProblem`, not looped | 1 attempt per registration, all attempted, idempotent-by-contract handle | n/a | `AtomicBoolean` CAS makes a second call a no-op (`SystemConnectedDeviceHandle.kt:238`) |
| Device-facing exchange | **no such operation exists in Phase 3** | Phase 0 §4's read/write asymmetry inherited unchanged: only `READ_FAILED` is `SAFE_TO_RETRY` | — | — | — |

Validation rules that run in Phase 3: `mergedWith`'s joinability `require` (`DeviceObservation.kt:104-106`); `coveredStates` exhaustiveness for the transition table (`DeviceObservationVocabularyTest.kt:27-32`); the union/reason/introduction-level assertions (`ConnectedDeviceObserverTest.kt:633-655`); the key's blank-text demotion (`DeviceObservationKey.kt:69-73, 101-102`); the factory's name normalisation (`DeviceObservation.kt:140, 157`); `OmniBudsError`'s `attempts >= 1` (Phase 1, unchanged). Phase 1's capability-rung guards and Phase 2's transport invariants are untouched and unreached by this phase.

## 12. Resource-cleanup rules

1. **Every registration this observation made is disposed in one `finally`, under `NonCancellable`.** Pumps and closer cancelled first, then `disposeAll`, then `stage = STOPPED`, then the slot released if held (`ConnectedDeviceObserver.kt:305-317`).
2. **All registrations are attempted even after one throws**, and the report is the first cause class plus how many did and did not release (`:482-505`) — "one of two leaked" and "both leaked" are different sizes of bug.
3. **A teardown problem is recorded, not emitted**, because by the time a caller can look the channel may already be closed by the very cancellation that made teardown matter (`:185-193`).
4. **One channel owns one receiver and its own proxies**, so a second open cannot overwrite a first channel's receiver or let one teardown unregister somebody else's listener (`SystemConnectedDeviceHandle.kt:64-72, 156-167`).
5. **Unregister and close are idempotent and both are attempted**: a CAS gate, then `unregisterReceiver`, then `closeProfileProxy` for every proxy this instance bound, then removal from the channel list, then the first problem rethrown (`:237-259`).
6. **A half-started channel unwinds without suspension.** `openAnnouncements` adds the channel before starting it and calls `abort()` — best-effort, nothing rethrown — if the start throws, so the caller sees one exception and leaves no orphan (`:126-147, 269-285`).
7. **Disposing the channel's registration also closes the announcement channel**, before any re-raise, so no collector hangs on a receiver that has gone away (`AndroidConnectedDeviceSource.kt:326-346`).
8. **A failed open hands back nothing live**: the channel is closed before the failure is returned (`:147-150`; asserted `AndroidConnectedDeviceSourceTest.kt:428-441`).
9. **A registration never collected is still disposable** — `openConnectionEvents()` registers eagerly and returns the handle (`:133-152`).
10. **A refused profile is not re-bound and a dropped service is not re-armed**: `onServiceDisconnected` marks the profile `refused` with no re-request, because a silent re-request is the retry loop this phase forbids (`SystemConnectedDeviceHandle.kt:340-350`).
11. **Nothing to unregister in the manifest**, and nothing persisted to clean up: no component, no `LifecycleOwner`, and the projection is process-lifetime state only (`di/OmniBudsBluetooth.kt:97-100`, rules 10-11).

## 13. DI seams and their default production implementations

Manual constructor injection, no container (ADR-P1-009). A default is the production implementation; a test replaces it by passing something else.

| Seam | Default production implementation | Replacement seen in tests | Cited |
|---|---|---|---|
| `ConnectedDeviceHandle` | `SystemConnectedDeviceHandle(appContext, apiLevel)` | `FakeConnectedDeviceHandle` | `di/OmniBudsBluetooth.kt:113`, `AndroidConnectedDeviceSourceTest.kt` throughout, `connection/FakeConnectedDeviceHandle.kt:28` |
| `ConnectedDeviceSource` | `AndroidConnectedDeviceSource(handle, permissionProvider, apiLevel, targetSdk, SystemTimeProvider, enumerationUnion, Dispatchers.IO)` | `FakeConnectedDeviceSource.asSource()`, `ConnectedDeviceSource.unavailable()` | `di/OmniBudsBluetooth.kt:112-122`, `ConnectedDeviceObserverTest.kt:57`, `ConnectedDeviceSource.kt:129` |
| `AdapterStateSource` (the adapter half the engine watches) | **not built here** — injected as a required parameter so a second `ACTION_STATE_CHANGED` receiver is not constructed behind the first platform's back | `FakeAdapterStateSource.asSource()`; the instrumented suite builds the real pair itself | `di/OmniBudsBluetooth.kt:87-95, 104`, `ConnectedDeviceObserverTest.kt:41`, `androidTest/.../ConnectedDeviceObservationInstrumentedTest.kt:315-317` |
| `AndroidPermissionStateProvider` | `AndroidPermissionStateProvider(SystemPermissionStandingReader(appContext), InMemoryPermissionRequestLedger())` | `PermissionStandingReader { false }` to script a refusal | `di/OmniBudsBluetooth.kt:114-117`, `androidTest …:100, 279-280` |
| `PermissionRequirementResolver` | `FrozenPermissionRequirementResolver()` (Phase 2 default inside the provider) | any alternate table | `permission/AndroidPermissionStateProvider.kt:38` |
| `ApiLevelProvider` / `TargetSdkProvider` | `SystemApiLevelProvider` / `SystemTargetSdkProvider(appContext)` | test lambdas | `di/OmniBudsBluetooth.kt:106, 109` |
| `TimeProvider` | `SystemTimeProvider` on both halves | `NoTimeProvider` is the observer's default, so a round without a clock leaves every timestamp unset (`ConnectedDeviceObserverTest.kt:557-580`) | `di/OmniBudsBluetooth.kt:120, 126`, `ConnectedDeviceObserver.kt:167` |
| `List<ObservedProfile>` | `ObservedProfile.enumerationUnion` | a narrower list may be passed to the observer; a caller "may not quietly forget one here" | `ConnectedDeviceObserver.kt:166`, `ObservedProfile.kt:104-113` |
| `CoroutineDispatcher` | `Dispatchers.IO` | `Dispatchers.Unconfined` under `runTest` | `AndroidConnectedDeviceSource.kt:80`, `di/OmniBudsBluetooth.kt:105` |
| A permission-request surface | **not supplied, and unconstructible** — no request API exists in either module and rule 7 bans the names | — | §15, §16 |

`omniBudsConnectedDeviceObserver` takes a `Context` and keeps `context.applicationContext` (`di/OmniBudsBluetooth.kt:111`) and neither factory caches — "a hidden global is how a process ends up with a registration nobody can dispose" (`:39-43`). The observer factory is called from no source file in the tree (design §2).

## 14. The manifest, and compatibility requirements

**One entry.** `platform/android/src/main/AndroidManifest.xml:39` is `<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />` and it is the only declaration in the file; ADR-P3-012 amends ADR-P2-011's zero-permission answer, and the file's comment block (`:2-37`) is the justification, not this document. Its shape: the enumeration and announcement calls this phase makes name that permission in their own per-member documentation, and the stack sends every one of these broadcasts *with `BLUETOOTH_CONNECT` as the receiver permission*, so an ungranted app "is not told it was refused, it simply hears nothing" (`:14-16`). An undeclared permission here would not be a bypass; it would be a refusal the app chose for itself, and the refusal Android reports is an empty device list — the confusion ADR-P3-009 exists to close (`:17-19`).

| Not declared | Why, and where the refusal is recorded |
|---|---|
| `BLUETOOTH_SCAN` | Phase 3 neither scans nor starts discovery; declaring it would move the app into the Nearby devices prompt group for a feature that does not exist, and a library manifest merges silently into every consumer (`:22-24`; ADR-P3-012) |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | no connection-observation member in the public surface mentions location; the only device broadcast that does is `ACTION_FOUND`, a scan result and out of scope (`:25-27`; research §5.3) |
| `BLUETOOTH`, `BLUETOOTH_ADMIN` | install-time permissions the modern band ignores; the legacy band's resolver rows already exist and that band's phase declares them with its own justification (`:28-30`) |
| Any component | every receiver is context-registered and owned by the object that opened it; most of these actions cannot reach a manifest receiver anyway (`:32-35`; research §4.4) |

| Compatibility item | Value | Source |
|---|---|---|
| `minSdk` | 26 | `gradle/libs.versions.toml:13`, applied at `platform/android/build.gradle.kts:24` (ADR-P2-002) |
| `compileSdk` | 35 | `libs.versions.toml:12`, `platform/android/build.gradle.kts:21` |
| `targetSdk` | 35 in the catalog (`:14`), applied by **no** Android library module; judged at runtime from `applicationInfo.targetSdkVersion` | `AndroidConnectedDeviceSource.kt:270-273`; research §1 |
| JVM target / Java | 17 | `platform/android/build.gradle.kts:31-42`, `core/build.gradle.kts` |
| Kotlin / AGP / Gradle | 2.0.21 / 8.7.3 / Gradle 8.9 | `libs.versions.toml:9,11`, wrapper properties |
| Coroutines | `kotlinx-coroutines-core` 1.9.0 as `api` on `:core` (Phase 2). `kotlinx-coroutines-android` still **not** used — `Dispatchers.Main` has no reason to exist here | `core/build.gradle.kts:29`, ADR-P2-003 |
| AndroidX in production | none. `getSystemService`, `registerReceiver` and `Context.RECEIVER_EXPORTED` are framework API, so `androidx.core` was not added (ADR-P2-016 stands) | `platform/android/build.gradle.kts:44-67` — the five coordinates below are `androidTestImplementation` only |
| Instrumentation runner | `androidx.test.runner.AndroidJUnitRunner` | `platform/android/build.gradle.kts:28` |
| Test-only coordinates (ADR-P3-007, five) | `androidx.test:runner` 1.6.1, `androidx.test:core` 1.6.1, `androidx.test.ext:junit` 1.2.1, `junit:junit` 4.13.2, `kotlin-test` | `libs.versions.toml:15-19, 34-37`, configurations at `platform/android/build.gradle.kts:62-66`; all `androidTestImplementation`, none visible to `src/main`, `src/test` or `:core` |
| Receiver export flag | `RECEIVER_EXPORTED` from API 33, no flag below where the overload does not exist — both receivers | `SystemConnectedDeviceHandle.kt:410-416` (`RECEIVER_FLAG_API_LEVEL = 33`, `:435`), `SystemBluetoothAdapterHandle.kt:84-91` (`:126`); ADR-P3-013 |
| Lint suppressions | `InlinedApi` + `UnspecifiedRegisterReceiverFlag` on each register body; `InlinedApi` on the action table and the constant table; `MissingPermission` on `enumerate` and `deviceReportOf`; `DEPRECATION` on `getParcelableExtra(String)` | `SystemConnectedDeviceHandle.kt:409, 519, 579, 109, 487, 360`, `SystemBluetoothAdapterHandle.kt:84`; each names its reason in the comment above it |
| Profile introduction levels used as gates | 11, 11, 18, 28, 29, 33, 33 | `ObservedProfile.kt:48-90`, read per ADR-P2-017 from the SDK's own table |
| Verification ceiling | nothing above `IMPLEMENTED`; U-1 … U-12 all open | ADR-P3-014, research §11 |
| Device status | instrumented suite authored, compiled, **never run**; no `connectedDebugAndroidTest` executed by this phase | `ConnectedDeviceObservationInstrumentedTest.kt:34-43`, `docs/security/device-access-policy.md:88-102` |

## 15. Test-double seams, and the instrumented surface

| Double | Seam it stands in for | How it refuses to invent | Cited |
|---|---|---|---|
| `FakeConnectedDeviceSource` (core test) | `ConnectedDeviceSource`, `PlatformRegistration`, `ConnectedDeviceEventChannel` | rounds come from a fixed `vararg` queue and answer `Failure(UNKNOWN_FAILURE)` when it runs dry (`:66-74`); `snapshot()` **yields before answering** so mid-read cases test the buffer, not the scheduler (`:63-66`); `failOpen` refuses and `failDisposal` throws (`:52-53, 79-87, 97-99`); counts `snapshotCalls`, `openCalls`, `supportCalls`, `disposeCalls`, `activeRegistrations` (`:37-50`); `keepEventsOpen` uses `awaitCancellation()` so slot and cancellation behaviour need no sleep (`:110-113`) | `core/src/test/.../platform/FakeConnectedDeviceSource.kt:30-123` |
| `allProfilesAnswerable` / `profilesAnswerable` / `noProfilesAnswerable` | a `ProfileSupportReport` | the third is built over the whole union so "nothing answered" is scripted as a fact rather than as an absence | `:126-139` |
| `FakeConnectedDeviceHandle` (Android test) | `ConnectedDeviceHandle` + its registrations | an unscripted profile is `NOT_REQUESTED` and enumerates to `Unanswered`, so a test cannot assert against an answer nobody gave (`:59-69`); records every question so "refused before enumerating" is an observation (`:45-47`); counts unregistrations and proxy closures (`:50-54, 119-127`); never prints an address (`:24-26`) | `connection/FakeConnectedDeviceHandle.kt:28-149` |
| `FakeAdapterStateSource` (Phase 2, reused) | `AdapterStateSource` — the adapter half the engine watches | unchanged; `ConnectedDeviceObserverTest.kt:41` holds one quiet instance so adapter cases are opt-in | Phase 2 `specs.md` §14 |
| `PermissionStandingReader { false }` (instrumented) | the standing reader | scripts a refusal rather than changing the device, because a test run nobody is watching may not become a background component that asks | `androidTest …:82, 101`, `docs/security/device-access-policy.md:98` |
| `BluetoothInstrumentedSmokeTest` (4 cases, never run) | nothing — it is the first real-framework wiring check | targets `com.omnibuds.android` only (`:57-63`); obtains `BluetoothManager` (`:65-73`); reads adapter state and checks it against the handle's own presence report (`:75-114`); walks all six `BluetoothPermission` entries asserting no unreachable standing (`:116-146`); `assertNoAddressLeak` over every diagnostic string (`:154-160`) | `androidTest/.../BluetoothInstrumentedSmokeTest.kt` |
| `ConnectedDeviceObservationInstrumentedTest` (7 cases, never run) | nothing — it is the falsifiable form of ADR-P3-009/-015 rules 4 and 7 | refusal-before-list on the real object graph (`:81-89`); refusal before the support question (`:100-107`); the union's completeness (`:119-138`); a declining platform may never look like an empty room (`:154-172`); register/dispose under the exported flag (`:184-202`); no profile above the handset's API level reported answerable (`:213-227`); no identifier in any produced text, whole records included (`:239-251`); `describeRound` as the only form a round may take (`:288-292`) | `androidTest/.../connection/ConnectedDeviceObservationInstrumentedTest.kt` |

Guard on the other side: `productionSourcesDefineNoTestDoubles` (`DependencyDirectionTest.kt:214-226`) still rejects any main-source class named `Fake…`/`Mock…`/`Stub…`/`Dummy…`/`Test…` — which is why `SystemConnectedDeviceHandle.Channel` and `AnnouncementReceiver` carry implementation names rather than double-shaped ones — and `noProductionClassImplementsTheProtocolOrRepositoryContracts` (`:229-243`) still covers `TransportContract`, so Phase 3 implemented no transport.

## 16. The architecture guard set as it now stands

`core/src/test/kotlin/com/omnibuds/core/architecture/DependencyDirectionTest.kt`: **12 numbered rules, 17 `@Test` checks.** Everything below is the set as Phase 3 left it; the Phase 2 delta against Phase 1 is in Phase 2 `specs.md` §15.

| # | Check | What it scans | Phase 3 status |
|---|---|---|---|
| 1 | `coreMainSourcesDoNotImportAndroidFrameworks` (`:156`) | `:core` main imports | unchanged |
| 1 | `coreMainSourcesDoNotImportJvmOnlyLibraries` (`:165`) | `:core` main imports | unchanged — so `SystemConnectedDeviceHandle.kt:20-21`'s `java.util.concurrent` imports are legal only because they are in the platform module |
| 2 | `coreReferencesNoAndroidFrameworkTypes` (`:181`, tokens `:187-190`) | `:core` main **code lines** | unchanged; `ObservedProfile`'s constants deliberately live in the platform module (`SystemConnectedDeviceHandle.kt:564-588`) so a framework number never sits beside a core enum |
| 3 | `coreMainSourcesContainNoPlaceholderImplementations` (`:199`), `productionSourcesDefineNoTestDoubles` (`:214`), `noProductionClassImplementsTheProtocolOrRepositoryContracts` (`:229`) | `:core` main | unchanged |
| 4 | `coreAreasDependOnlyOnMoreFoundationalAreas` (`:248`), `everyCoreAreaIsRegisteredInTheLayerMap` (`:267`) | `:core` main imports; `areaLayer` `:35-50` | unchanged — `platform` stays at 1, no 13th area registered (ADR-P3-003), so the observation types cannot import `device`, `session` or `persistence` |
| 5 | `coreContainsNoHardCodedProtocolLiterals` (`:282`) | `:core` main code lines | unchanged; Phase 3 declares no hex and no UUID, and the profile constants are in the platform module |
| 6 | `packageStatementsMatchSourceDirectories` (`:300`) | `:core` main | unchanged |
| 7 | `platformModuleContainsNoUnauthorisedCapabilities` (`:327`) | **`src/main` + `src/androidTest` + `src/test`**, via `platformCapabilitySources()` (`:134-136`) | **widened three times.** (a) ADR-P3-016: one shared source list so widening the scan cannot be forgotten at one of two call sites; each root fails the build if it is missing or holds no Kotlin (`:136-151`); violations are labelled with the source set they came from (`labelOf`, `:370-374`). (b) Four new prohibited names at `:346`, each a call that reads as a query and is not one: `fetchUuidsWithSdp` — an over-the-air SDP transaction against a named device; `startVoiceRecognition` / `stopVoiceRecognition` — open and shut down the Bluetooth audio path; `setPriorityPolicy` — writes an LE Audio policy, and does **not** exist in the compileSdk 35 public surface, so the entry cannot fire today and is kept as the forward guard it becomes the first time the compile SDK widens (`:341-345`). (c) **At close-out `src/test` joined the union**, because the reason ADR-P3-016 gave for leaving it out — that those files declare no framework type — was found false (§17.9), and the guard now sees all three roots. The method keeps its Phase 2 name, which now reads narrower than its body, because five Phase 2 records cite it by name (ADR-P3-016) |
| 8 | `neitherModuleTouchesTheMediaAudioPath` (`:379`) | `:core` main + platform main **and `src/androidTest`**, via `platformCapabilitySources()` (`:385`) | token list unchanged (Phase 2's five); Phase 3's handle subscribes to no audio-routing or codec action (research §8.2 item 8). The instrumented half of the scan arrived at `335c31d`, after the Phase 3 code commits (§17.8) |
| 9 | `neitherModuleReferencesUiFrameworks` (`:410`) | `:core` main + platform main **and `src/androidTest`** (`:419`) | token list unchanged; one rule *further* than ADR-P3-016 names, which speaks of "rules 7 and 8" — the stronger direction, so it is recorded rather than argued about (§17.8) |
| 10 | `platformBroadcastUseIsConfinedToListeningForAdapterState` (`:449`) | platform main | **widened in location only.** `authorisedListenerPackages` is now a set of two — `bluetooth/adapter/` and `bluetooth/connection/` (`:454`) — because the ACL, bond and per-profile connection-state listeners belong in `connection/` by ADR-P3-008 and a listener outside the adapter package is the same capability aimed at a different subject. What did **not** move: the token list (`:450-451`), the sender ban everywhere (`:464-468`), and a receiver in a third package still fails (ADR-P3-016) |
| 11 | `platformSourcesLiveOnlyUnderTheAuthorisedPackages` (`:482`) | platform main | unchanged — `connection/` sits under the existing `bluetooth/` root, so no new top-level package was needed |
| 11 | `platformManifestDeclaresNothingUnjustified` (`:508`) | the boundary manifest | **allowed-set of one**: `setOf("android.permission.BLUETOOTH_CONNECT")` (`:517`), `declared.filterNot { it in allowed }` plus the four component tags (`:531`). It was given an allowed name rather than relaxed, and still refuses every other permission and any component (ADR-P3-012) |
| 12 | `theAndroidModuleDependsOnCoreAndNotTheReverse` (`:538`) | both build scripts | unchanged; the five new coordinates are `androidTestImplementation` and so do not create a `:core`-visible dependency |

**What the scans still cannot see**, unchanged except where noted: a fully qualified inline framework reference with no import evades the prefix checks — rules 7, 8 and 9 close most of it in main, instrumented **and** JVM-unit sources by scanning code lines (`:349, 385, 419`), but rules 10 and 11 still consume `platformSources()` (main only, `:456, 464, 483`), so a send-capable broadcast API or a fourth-package receiver written only in `src/androidTest` or `src/test` would pass; `internal` and same-package types are invisible to the area rule; nothing inspects a dispatcher; nothing checks that one observer exists per process; nothing can check that a KDoc or an ADR sentence about the platform is honest — which is exactly the failure mode §17 records, and §17.9 is now the instance of it that was caught; and nothing measures that the *instrumented* suite is never run, which is a duty `validation.md` discharges by stating the twelve methods as compiled-and-unexecuted rather than by checking it.

## 17. Divergences: prose that overstates code, and documents behind it

1. **Closed at close-out: `ProfileAnswerability.evidenceRank()` ranked a refusal above a pending bind,** contradicting its own KDoc and ADR-P3-008, which is what let one channel's `getProfileProxy` `false` outvote another channel that had not called back and report "the platform declined" where the truth was "this round happened early". The ladder is now `NOT_REQUESTED < REFUSED < AWAITING_CALLBACK < ANSWERABLE` (`connection/ConnectedDeviceHandle.kt:152-157`) and `ProfileAnswerabilityTest` (6) pins it in both report orders; the decision and its reasoning are ADR-P3-019, and the reason this was ever reachable is recorded as `RISK-049`'s sibling rather than presented as coverage.
2. ⚠ **`DeviceObservation.toString()` carries the display name.** The redaction guarantee is a property of `DeviceObservationKey`, not of the record (§4), and the instrumented suite's check is address-*shaped* (`ConnectedDeviceObservationInstrumentedTest.kt:303`), so a name that is not address-shaped would pass. Nothing in Phase 3 can emit it — there is no sink (ADR-P1-019) — and the duty at emission belongs to Phase 36.
3. **The audit is stale in two named places.** `architecture-audit.md` §7's `ConnectedDeviceProjection` was settled as `ConnectedDeviceSnapshot` (ADR-P3-004) and its `ObservationPhase` was realised as `ObservationStage` (`platform/ObservationStage.kt:20-29`); ADR-P3-015's consequences paragraph records both. The audit's per-profile vocabulary (`SUPPORTED / NOT_SUPPORTED_BY_PLATFORM / UNKNOWN`) was realised as `ProfileObservationSupport` (`ANSWERABLE / NOT_ANSWERABLE / UNKNOWN`) for the answering-not-support reason at `ObservedProfile.kt:121-129`. The audit defers to the ADRs by the authority order, so these are the audit's errata.
4. **Half closed; the other half is now pinned.** This item read "two model rows are data with no code path": `BONDED_DEVICE_LIST_INSPECTION` now has one — `AndroidConnectedDeviceSource.bondedDevices()` settles it before the bond read (§8.6, ADR-P3-017). `DEVICE_DISCOVERY_SCAN` no longer claims Phase 3 at all: it moved to `authorizedInPhase = 5` in `caf286e`, and both rows are read by `PhaseThreeScopeTest` (8), which pins the eight operations authorised at 3 and refuses scan, transport and LE Audio session by name and by prefix (ADR-P3-018). The remaining truth in the original sentence is the one ADR-P2-014 chose: `isAuthorizedIn` gates nothing at runtime, so the tags are data a test reads, not a permission the app enforces.
5. **One `detail` string interpolates a count.** `ConnectedDeviceObserver.kt:433-435` embeds `${next.records.size}`. That is a number about the observation, not about any device, and §9's rule is stated that way on purpose — a reader should not mistake "no identifier ever appears" for "nothing dynamic ever appears".
6. **A cross-reference in the security policy.** `docs/security/device-access-policy.md:98` still cites ADR-P3-011 for "the suite never calls any request API and grants nothing"; the decisions file assigns that posture to ADR-P3-007 (the instrumented route's two prohibitions) and the permission question to ADR-P3-009/-012, while ADR-P3-011 is the receiver-export decision. The *next* paragraph (`:100`) cites ADR-P3-011 correctly for the exported registration and was itself corrected at `335c31d` to record that Phase 2's flag choice *was* rewritten by ADR-P3-013 — so the document is being repaired as it goes, and only the `:98` citation remains out of step. Left as authored: this file edits no other workstream's document.
7. **Phase 2 documents that Phase 3 changed underneath.** Phase 2 `specs.md` §11 rule 4 and `design.md` §11 describe `SystemBluetoothAdapterHandle`'s receiver as registered `RECEIVER_NOT_EXPORTED`; `SystemBluetoothAdapterHandle.kt:84-91` now registers it exported (ADR-P3-013, commit `883e16d`), and that file's KDoc carries the correction in place. Phase 2's records stand as authored history, which is the treatment ADR-P2-009 gave Phase 0's `SEC-PERM-002`.
8. ⚠ **ADR-P3-016's first clause was realised for one rule when Phase 3's code landed; it is now realised for three.** ADR-P3-016 states that "rules 7 and 8 scan production **and** instrumented sources through one list, so widening the scan cannot be forgotten at one of two call sites". In `45f9976`, which added the `androidTest` source set to the guard's world, the shared list `platformCapabilitySources()` (`DependencyDirectionTest.kt:134`) was consumed by rule 7 only (`:349`): rules 8 (`:385`), 9 (`:419`), 10 (`:456, 464`) and 11 (`:483`) read `platformSources()`, so a media-audio or UI token in `src/androidTest` would have passed the whole suite and the ADR's sentence described an intention rather than the code. Commit `335c31d`, which landed while these documents were being written, changed lines 385 and 419 to consume the shared list — so rule 7 is as the ADR says, rule 8 now is too, and rule 9 is covered one rule *further* than the decision names, in the stronger direction and with no new argument needed. Rules 10 and 11 remain main-only, which is the residue a reader should know: a send-capable broadcast API or a fourth-package receiver written only in instrumented code is still invisible. Nothing prohibited was present in `src/androidTest` under either reading — the two files carry none of rule 8's or rule 9's tokens — and ADR-P3-016's consequences paragraph records the guard being falsified in both directions before it was trusted.
9. **Closed at close-out: the stated reason for excluding `src/test` from the scan was half true, and the half that was false was load-bearing.** `DependencyDirectionTest.kt:131-132` kept that source set out because "those sources hold no framework type to begin with, because the seam classes they script declare none", and ADR-P3-016 repeated the claim. The second clause holds — `BluetoothAdapterHandle`, `ConnectedDeviceHandle` and the rest of the seam surface declare no framework type (`ConnectedDeviceHandle.kt:1-4`, imports of `:core` only) — but the first did not: three Phase 2 files in `src/test` import `android.bluetooth.BluetoothAdapter` at line 3 each (`AndroidBluetoothPlatformTest.kt:3`, `adapter/AndroidAdapterStateSourceTest.kt:3`, `mapping/AdapterStateMappingTest.kt:3`) for the inlined `STATE_ON`/`STATE_OFF` constants. The remedy was the widening rather than the softening: `src/test` is now inside `platformCapabilitySources()`, all 17 checks still pass, and nothing in `src/test` had to move — which is the honest shape of the finding, since the exclusion was costing coverage rather than tolerating a violation. ADR-P3-016 carries the dated amendment and `RISK-060` records how an exemption resting on a comment behaves when the comment goes false.

## 18. Which file owns which spec entry

| Entry | Owner file |
|---|---|
| §2.1 link axis, predicates and §3.1's transition table | `core/src/main/kotlin/com/omnibuds/core/platform/DeviceConnectionState.kt` |
| §2.2 bond axis | `core/.../platform/DeviceBondState.kt` |
| §2.3 availability axis | `core/.../platform/DeviceAvailability.kt` |
| §2.4 stage, §2.5 `ObservationRound`; §3.3 stage machine | `core/.../platform/ObservationStage.kt` |
| §2.4 `ObservationArrival`, §5.1 `DeviceObservation`, its factory and its ranks | `core/.../platform/DeviceObservation.kt` |
| §8.6 `BondedDeviceObservation`, `creditingBond`, the shared text and rank helpers | `core/.../platform/DeviceObservation.kt:159, 226-313` |
| §2.6 `ObservedProfile`, `enumerationUnion`; §2.7 `ProfileObservationSupport`, `ProfileSupportReport` | `core/.../platform/ObservedProfile.kt` |
| §4 the key, the join rule, the redaction guarantee | `core/.../platform/DeviceObservationKey.kt` |
| §6 `ConnectedDeviceSource`, `ConnectedDeviceEventChannel`, `DeviceConnectionEvent`, `unavailable()`, `bondedDevices()`; §12 rule 9 | `core/.../platform/ConnectedDeviceSource.kt` |
| §2.8 `RefusalKind`, §5.2 `ConnectedDeviceSnapshot` and `RefusedDeviceTransition`, §6 `ConnectedDeviceObserver`, §3.2-3.3 folds, §9 `RESOURCE_UNAVAILABLE`/`BLUETOOTH_DISABLED`, §10 flow and cancellation, §12 rules 1-3, §11 empty-union refusal | `core/.../platform/ConnectedDeviceObserver.kt` |
| §3.1 exhaustiveness, §2.4 stage-name ban, §2.5 cancellation-is-not-a-category | `core/src/test/kotlin/com/omnibuds/core/platform/DeviceObservationVocabularyTest.kt` |
| §4 join and redaction proofs | `core/src/test/.../platform/DeviceObservationKeyTest.kt` |
| §3.3, §5.1, §9, §11, §12 engine proofs and the scripted-union assertions | `core/src/test/.../platform/ConnectedDeviceObserverTest.kt` |
| §15 `FakeConnectedDeviceSource` | `core/src/test/.../platform/FakeConnectedDeviceSource.kt` |
| §7 handle seam and its raw-state types | `platform/android/src/main/kotlin/com/omnibuds/android/bluetooth/connection/ConnectedDeviceHandle.kt` |
| §8.1 raw→domain, §8.2 standing→category, §8.3 answerability gates, §5.3 mapped events, §10 buffering, §12 rules 7-9 | `platform/android/.../bluetooth/connection/AndroidConnectedDeviceSource.kt` |
| §8.4 action table, §8.5 state filter, profile-constant table, §10 volatile reads, §12 rules 4-6, 10, §14 flag rows and suppressions | `platform/android/.../bluetooth/connection/SystemConnectedDeviceHandle.kt` |
| §12 rule 5's Phase 2 counterpart, §17.7 | `platform/android/.../bluetooth/adapter/SystemBluetoothAdapterHandle.kt` |
| §13 DI seams and the one-instance rules | `platform/android/.../di/OmniBudsBluetooth.kt` |
| §14 the single manifest entry and its refused set | `platform/android/src/main/AndroidManifest.xml` |
| §14 build facts, runner, five test-only coordinates | `platform/android/build.gradle.kts`, `gradle/libs.versions.toml`, `core/build.gradle.kts`, `settings.gradle.kts` |
| §15 instrumented surface and the never-run status | `platform/android/src/androidTest/kotlin/com/omnibuds/android/bluetooth/BluetoothInstrumentedSmokeTest.kt`, `…/bluetooth/connection/ConnectedDeviceObservationInstrumentedTest.kt` |
| §8.6 the paired fold, the four refusals and the census rules | `core/src/test/kotlin/com/omnibuds/core/platform/BondedDeviceObservationTest.kt`, `core/src/test/kotlin/com/omnibuds/core/platform/FakeConnectedDeviceSource.kt` |
| §12 ADR-P3-018, the authorised set as data a check reads | `core/src/test/kotlin/com/omnibuds/core/platform/PhaseThreeScopeTest.kt` |
| ADR-P3-019, the answerability ladder | `platform/android/src/test/kotlin/com/omnibuds/android/bluetooth/connection/ProfileAnswerabilityTest.kt` |
| §15 Android-side scripted handle | `platform/android/src/test/kotlin/com/omnibuds/android/bluetooth/connection/FakeConnectedDeviceHandle.kt`, `…/AndroidConnectedDeviceSourceTest.kt` |
| §16 the guard set, §0's left column | `core/src/test/kotlin/com/omnibuds/core/architecture/DependencyDirectionTest.kt` |
| §9 unchanged 23-category set and its two exhaustive tables | `core/.../common/OmniBudsErrorCategory.kt`, `core/src/test/.../common/OmniBudsErrorCategoryTest.kt` |
| §8.2 permission rows consumed from Phase 2, unchanged | `core/.../platform/PermissionRequirementResolver.kt`, `platform/android/.../permission/AndroidPermissionStateProvider.kt` |
| §10 Phase 0 §5 rule 5 (the cold-flow clause) | `docs/phases/phase-0/specs.md:145` — amended in practice by nothing |
| §17.3 what the audit got stale | `docs/phases/phase-3/architecture-audit.md` §7, corrected by `docs/phases/phase-3/decisions.md` ADR-P3-004/-015 |
| §14, §15 the device-access boundary | `docs/security/device-access-policy.md` |
