# Phase 2 — Validation

```text
Phase:   2 — Android Bluetooth Foundation
Status:  COMPLETE-WITH-DEFERRALS — VALIDATED (mocked evidence only; see "Mocked versus physical")

Build:                      PASS
Unit tests:                 PASS — 356 core + 45 platform, 0 failures
Architecture enforcement:   PASS — 17 mechanical checks
Static analysis (lint):     PASS — 0 errors, 0 warnings (2 raised, both answered by reason)
Manifest review:            PASS — zero permissions, zero components, machine-checked
Scope review:               PASS — nothing on the forbidden list exists in either module
Physical Bluetooth run:     NONE — no Phase 2 code executed on hardware
Audio path touched:         NO (verified)
UI screens:                 NONE in product code (verified)

Ready for Phase 3:  YES, conditional on the user authorising a device-hosting module
```

Recorded 2026-10-01 by the orchestrator. Every figure below came from running a command or reading a file in this repository; nothing was accepted from a sub-agent's summary. Where a claim rests on reading rather than on an automated check, it says so. Where a claim cannot be made at all, the gap is named instead of being filled.

---

## Verification basis

| Check | Command or method | Measured result |
|---|---|---|
| Full build + tests | `./gradlew --offline :core:test :platform:android:test :tools:companion-shell:assembleDebug` | **BUILD SUCCESSFUL** (debug and release unit-test variants both ran) |
| Fresh re-execution | `./gradlew --offline build :platform:android:lintDebug --rerun-tasks` | **BUILD SUCCESSFUL**, every task re-run from source rather than served from the build cache, with the same 356 / 45 counts and lint still reporting no issues |
| Static analysis | `./gradlew --offline :platform:android:lintDebug` | **BUILD SUCCESSFUL**, report: `0 errors, 2 warnings` on first run, `No issues found.` after the reasoned suppression below |
| `:core` tests | `core/build/test-results/test/*.xml`, parsed | **356 tests, 0 failures, 0 errors, 0 skipped**, across 42 test classes |
| Tests per core area | same XML, grouped by package | `protocol` 60, `capability` 56, `device` 37, `transport` 37, `audio` 31, **`platform` 31**, `diagnostics` 22, `common` 19, `config` 19, **`architecture` 17**, `session` 12, `testing` 8, `persistence` 7 |
| `:platform:android` tests | `platform/android/build/test-results/testDebugUnitTest/*.xml`, parsed | **45 tests, 0 failures, 0 errors, 0 skipped** across 5 classes — mapping 7, adapter source 9, permission provider 11, capability provider 13, composed platform 5 |
| Warning gate | `allWarningsAsErrors = true` in all three modules | zero-warning compile of every source set, including the test source sets |
| Source volume | filesystem | `:core` 96 main / 47 test files; `:platform:android` 13 main / 5 test files; `:core` `platform` area 17 files, `transport` area 12 files |
| Harness APK | `tools/companion-shell/build/outputs/apk/debug/companion-shell-debug.apk` | present, 817,289 bytes, debug variant only |
| Toolchain | Gradle 8.9 wrapper, AGP 8.7.3, Kotlin 2.0.21, JDK 17.0.20 (Microsoft OpenJDK under the user-level AppData path, selected per invocation by `JAVA_HOME`), `--offline` throughout | as recorded; no dependency was added and no version changed by this phase's close |
| Android configuration | compileSdk 35, minSdk 26, JVM target 17; `:tools:companion-shell` targetSdk 35 | read from `gradle/libs.versions.toml` and the module build scripts |
| Git state | `git rev-parse --short HEAD` | `608eba5` at the start of this close-out; Phase 2's platform sources and these documents were uncommitted while this file was written |
| Device state at close | `adb devices -l` | **no device attached**, so no new physical run was possible |

---

## Mocked versus physical — kept apart deliberately

This is the section that decides how much the rest of the file is worth.

| Evidence | What it actually covers | Level it can earn |
|---|---|---|
| 356 `:core` JVM tests | Domain and platform-foundation policy: state mapping, the single-slot observation machine, the permission matrix, the error tables, transport boundary shape, architecture rules | `IMPLEMENTED` at most (`testing-governance.md` §2.1) |
| 45 `:platform:android` JVM tests | The Android classes' *decisions*, reached through scripted seams: a `BluetoothAdapterHandle` that answers what a test tells it, a `PermissionStandingReader` that is a lambda, a `PlatformFeatureProbe` that is a map | `IMPLEMENTED` at most. **No Android framework method was executed by any of them.** |
| ADB deployment results in `docs/development/adb-deployment/validation.md` | Build → install → package listed → activity resolved → launch → `pidof` → foreground, on the connected Xiaomi/POCO HyperOS phone, for `:tools:companion-shell` | Real physical evidence — **about the deployment harness only** |
| Phase 2 Bluetooth code on hardware | **Nothing.** No Phase 2 class executed on a phone during this phase | — |

The separation is not a gap that better effort would have closed; it is structural. `:tools:companion-shell` depends on neither `:core` nor `:platform:android` (ADR-P2-010), so a green harness run cannot be read as evidence that OmniBuds' Bluetooth layer works — and conversely, Phase 2 has no module that could host the Bluetooth glue on a device without breaking that isolation. Creating one is a Phase 3 decision for the user, so it is listed under *Deferred* rather than performed here.

**What a later device run must confirm, in order:** that `BluetoothManager.getAdapter()` returns non-null on the target phone; that `getState()` answers without a prompt while the manifest stays empty; that `ACTION_STATE_CHANGED` actually arrives when Bluetooth is toggled from Settings and that the receiver is unregistered afterwards; that `hasSystemFeature` returns what the phone's own documentation claims for it; and that `checkSelfPermission` reports the six permission names as the model expects. None of these is currently claimed.

---

## Static analysis, and the two warnings it raised

`:platform:android:lintDebug` first reported **0 errors, 2 warnings**, both on the version-guarded registration in `platform/android/src/main/kotlin/com/omnibuds/android/bluetooth/adapter/SystemBluetoothAdapterHandle.kt`:

1. `InlinedApi` — `Context.RECEIVER_NOT_EXPORTED` is an API 33 constant read at minSdk 26. The value is inlined into the class file and is only reached inside the `apiLevel() >= 33` branch, so the call is guarded; lint cannot see that because the guard reads an injected seam rather than `Build.VERSION.SDK_INT`.
2. `UnspecifiedRegisterReceiverFlag` — the pre-33 branch passes no export flag. `ACTION_STATE_CHANGED` is a protected system broadcast, which is the documented exemption: the flag is required for *non-system* broadcasts.

Both are answered in code with `@SuppressLint("InlinedApi", "UnspecifiedRegisterReceiverFlag")` on `register()`, and the reasoning is in that function's KDoc rather than in a throwaway comment. The alternative — taking `androidx.core` for `ContextCompat.registerReceiver` — was rejected because it would be the module's first AndroidX dependency for no behavioural gain, and the dependency policy (Phase 1 prompt section 34) requires a reason, not a convenience. `:platform:android:lintDebug` now reports **No issues found.** A suppression is a decision with a stated basis, not a way of making a check quiet: if the guard is ever removed, the suppression becomes a defect, and this record is the thing that says so.

---

## Scope review — what Phase 2 was forbidden to do

Prompt section 6's list, scanned across `platform/android/src` and `tools/companion-shell/src` (`.kt` and `.xml`), with the mechanical rule now holding each line:

| Forbidden in Phase 2 | Occurrences | Enforced by |
|---|---|---|
| GATT traffic — `connectGatt`, `BluetoothGatt`, `writeCharacteristic` | 0 in code | `platformModuleContainsNoUnauthorisedCapabilities` |
| Discovery / scanning — `startDiscovery`, `BluetoothLeScanner`, `startScan` | 0 | same |
| RFCOMM — `createRfcommSocket`, `listenUsingRfcomm` | 0 | same |
| Quick Settings / widgets / notification listener | 0 | same |
| UI frameworks in product modules — `Activity`, `Fragment`, `ViewModel`, `ContextThemeWrapper`, `setContentView`, `startActivity`, `PendingIntent` | 0 in `:core` and `:platform:android` | `neitherModuleReferencesUiFrameworks` (rule 9, revised by ADR-P2-016) |
| Media audio — `AudioRecord`, `MediaCodec`, `MediaExtractor`, `MediaMuxer`, `MediaSession` | 0 in either module | `neitherModuleTouchesTheMediaAudioPath` |
| Android types leaking into the domain | 0 imports, 0 named framework types, 0 framework names in string literals | rules 1 and 2, plus ADR-P2-015 |
| Broadcast reach beyond listening for adapter state | `sendBroadcast`, `sendOrderedBroadcast`, `PendingIntent`, `LocalBroadcastManager` absent; receiver types confined to `bluetooth/adapter/` | `platformBroadcastUseIsConfinedToListeningForAdapterState` (new, ADR-P2-016) |
| Manifest over-declaration | no `<uses-permission>`, no `<receiver>`, `<service>`, `<activity>` or `<provider>` | `platformManifestDeclaresNothingUnjustified` (new, ADR-P2-016) |
| Unauthorised packages | every platform source under `com/omnibuds/android/bluetooth/` or `com/omnibuds/android/di/` | `platformSourcesLiveOnlyUnderTheAuthorisedPackages` (new, ADR-P2-016) |

Two scan hits are explained rather than hidden, because a reader running the same grep will find them:

- `BluetoothGatt` appears once, in a **comment** at `AndroidPlatformCapabilityProvider.kt:196`, as the provenance for an API-level floor. The guards read code lines with comments and KDoc stripped, so documenting a forbidden thing is not a violation — which is the intended behaviour of those checks, not a loophole being relied on.
- `setContentView` appears once, in `tools/companion-shell/src/main/kotlin/com/omnibuds/tools/shell/ShellActivity.kt:104`. The harness app is the one module whose entire purpose is to be looked at by the bridge, and ADR-P2-010 keeps it unable to import product code. Rules 7–11 scope to `:core` and `:platform:android`; the shell sits deliberately outside them, and its isolation is what stops that from being a hole.

**The new checks were falsified deliberately, once, before being accepted.** A scratch file `platform/android/src/main/kotlin/com/omnibuds/android/ui/Scratch.kt` was added, importing and returning `android.content.Intent` from an unauthorised package, and `:core:test --tests "*DependencyDirectionTest*"` failed exactly as intended — `platformBroadcastUseIsConfinedToListeningForAdapterState` naming the two lines, and `platformSourcesLiveOnlyUnderTheAuthorisedPackages` naming the path. The file was then deleted and the suite re-run to BUILD SUCCESSFUL. This matters because a scan that silently finds nothing is a passing test that proves nothing, which is the failure mode `DependencyDirectionTest`'s own header warns about; `platformSourcesLiveOnlyUnderTheAuthorisedPackages` additionally `fail`s when the boundary module has no `.kt` files at all, so it cannot go vacuous by accident later.

---

## Definition of done (prompt section 11)

```text
[x] Phase 0 and Phase 1 contracts respected         layer map registered (ADR-P2-001); 17 checks pass; no Phase 0 rule relaxed without an ADR
[x] Android Bluetooth platform boundary exists      platform/android/src/main/kotlin/com/omnibuds/android/ (13 files)
[x] Adapter availability can be inspected           BluetoothPlatform.readAdapterState + the report's adapterPresent field
[x] Adapter state can be observed safely            AdapterStateObserver: single slot, dedupe, teardown in finally, recorded teardownProblem
[x] Permission requirements centralised, version-aware  FrozenPermissionRequirementResolver over targetSdk (ADR-P2-009); PermissionRequirementResolverTest, 10 cases
[x] Permission failures represented correctly       seven PermissionState values; DENIED_PERMANENTLY unreachable from app code (ADR-P2-012), asserted by noInputProducesAnUnsupportedClaim
[x] Platform capability information modelled        PlatformFeatureSupport keeps API / hardware / permission separate (ADR-P2-008, ADR-P2-017)
[x] Transport abstractions established              core/transport: 12 files, kind pinned by TransportKindPinningTest (ADR-P2-013)
[x] Structured Bluetooth errors exist               7 categories added; retry-class and invalidation tables exhaustive (ADR-P2-004, ADR-P2-005)
[x] Cancellation and lifecycle cleanup tested       AdapterStateObserverTest: cancellation tears down, no duplicate registration, single-slot refusal
[x] Core independent of Android framework APIs      0 android/androidx imports in :core (test-enforced)
[x] No fake hardware capability exposed             failures resolve to UNKNOWN/UNAVAILABLE, never a defaulted ENABLED; no Fake in main (rules 4 and 12)
[x] No vendor protocol implemented                  ProtocolRegistry still empty (ADR-P1-013); no protocol package added
[x] No audio path modified                          rule 8; 0 occurrences in either module
[x] Mandatory documentation exists                  12 records in docs/phases/phase-2/, listed below
[x] Tests pass or failures documented               356 + 45 pass; untested paths named under Known issues
[x] Build and static analysis pass                  BUILD SUCCESSFUL; lintDebug 0 errors / 0 warnings; zero-warning gate held
[x] Git diff contains only authorised Phase 2 changes  staged by explicit path; the concurrent bridge workstream's files are neither edited nor committed here
[ ] Physical-device verification of Phase 2 code    NOT PERFORMED — impossible without a device-hosting module; see Deferred and Known issue 1
```

---

## Audit findings closed at close-out

Two findings the Phase 2 architecture audit raised against the orchestrator's own code were still open when the phase records were drafted, and were fixed rather than carried (ADR-P2-018):

- **R-7 — cancellation was being relabelled as a platform fault.** `AdapterStateObserver` turned a `Cancelled` initial read into `Failure(PLATFORM_EXCEPTION)`, making "the read was called off" indistinguishable from "the adapter is broken", against ADR-P1-004 and `specs.md` §5.3. Both the read branch and the previously-silent `openStateChanges()` branch now forward `OperationOutcome.Cancelled` unchanged, and the slot still releases in the `finally`. Proven by `aCancelledReadIsReportedAsCancellationNotAsABrokenAdapter` and `aCancelledRegistrationIsReportedAsCancellationAndReleasesTheSlot`.
- **R-8 — the first genuine transition after a successful read was tagged as a starting point.** The flag selecting between `INITIAL_EVENT` and `PLATFORM_EVENT` meant "an event has already been announced" instead of "a starting value has been reported". It is now `startingValueAnnounced`, set by a successful initial read, so `INITIAL_EVENT` appears only when the read did not answer. Proven by `theFirstChangeAfterASuccessfulReadIsAPlatformEventNotAnInitialOne`; the pre-existing `anOffOnOffSequenceIsPreservedRatherThanCollapsedToTheLastValue` had been asserting the mislabelling and its expectation was corrected, which is the honest direction for a test to fail.
- The same audit note objected that `ObservationKind.DEDUPED` could never be constructed. It is deleted: suppression drops a repeat rather than emitting a labelled one, and an enum entry nothing produces is vocabulary standing in for a capability.

The count moved from 353 to 356 because of these three additions, and every `:core` area figure in this file is the post-correction measurement.

---

## Documentation created

| Document | Path | Status |
|---|---|---|
| Execution prompt (staged as authorisation) | `docs/phases/phase-2/execution-prompt.md` | present |
| Architecture audit | `docs/phases/phase-2/architecture-audit.md` | present |
| Bluetooth API research, cited to source | `docs/phases/phase-2/bluetooth-api-research.md` | present |
| Transport boundaries and deferrals | `docs/phases/phase-2/transport-boundaries.md` | present |
| Decisions | `docs/phases/phase-2/decisions.md` | present — ADR-P2-001 … ADR-P2-018 |
| Requirements | `docs/phases/phase-2/requirements.md` | present |
| Design | `docs/phases/phase-2/design.md` | present |
| Specs | `docs/phases/phase-2/specs.md` | present |
| Task list | `docs/phases/phase-2/task-list.md` | present |
| Test plan | `docs/phases/phase-2/test-plan.md` | present |
| Risk register | `docs/phases/phase-2/risk-register.md` | present |
| This record | `docs/phases/phase-2/validation.md` | present |

The deployment harness's own records live under `docs/development/adb-deployment/` and `docs/security/device-access-policy.md`. They belong to a separate workstream and are referenced here rather than merged, so a reader cannot accidentally take harness evidence for product evidence.

---

## Known issues and limits

1. **No Phase 2 code has run on a device.** The platform module is a library with no host, and the only installable module in the repository is deliberately unable to import it (ADR-P2-010). Every Android claim in this phase is therefore `IMPLEMENTED`, never `HARDWARE_VERIFIED`. This is the phase's largest honest limitation.
2. **The four `System*` classes have no automated test.** `SystemBluetoothAdapterHandle`, `SystemPermissionStandingReader`, `SystemPlatformFeatureProbe` and `SystemTargetSdkProvider` are unexecuted: on a JVM the `android.jar` stubs throw, so a mock there would prove only that the mock works. They are covered by construction review and by the seams around them, and they need an instrumented run.
3. **Permission revocation during an operation is untested, and cannot be tested as Android behaves.** Research Q5 records that runtime revocation kills the process instead of delivering a callback, so there is no in-process event to assert on. The model's answer is that standing is re-read on the next observation, which is a Phase 3 lifecycle concern with a device attached.
4. **`BluetoothPlatform.permissionState` takes no operation parameter,** so `NOT_REQUIRED` cannot come from it: requirement is a per-operation judgement owned by the resolver (`AndroidPermissionStateProvider.stateFor`). A caller reading only the interface could mistake `DENIED` for "this operation is blocked", which is why the asymmetry is recorded here and in `specs.md`. The same applies to `BluetoothPlatformCapabilities.permissionStatus`, which is a flat standing table: on a target-35 build with an empty manifest it reports `BLUETOOTH` and `BLUETOOTH_ADMIN` as `DENIED` even though no authorised Phase 2 operation needs them. The per-feature `permissionState` is the operation-aware answer and is `NOT_REQUIRED` for those rows; a later UI must read the second, not the first. That field is itself paired with exactly one operation per row (`OPERATIONS_BY_FEATURE`, stated in the provider's KDoc), so a `NOT_REQUIRED` on the two inspection-paired rows is not a claim that using that transport later needs no permission.
5. **The event channel is conflated,** so intermediate adapter states can be coalesced if a collector falls behind. Correct for a latest-wins reading, wrong for a phase that must see every transition; that phase must decide it as an ADR rather than flip a constant.
6. **The Kotlin Gradle plugin is loaded in more than one subproject,** which Gradle warns "may break the build". Pre-existing since Phase 1 (`:core` applies `kotlin.jvm`, `:platform:android` applies `kotlin.android`, with no root declaration). The remedy is a root `build.gradle.kts` declaring each plugin `apply false`; it was not applied here because changing build topology is outside Phase 2's authorised scope. It reproduces in every Gradle run of this repository.
7. **No CI exists,** so "verified" means verified once, on this workstation, offline (RISK-020). Every build above used `--offline`; the dependency cache holds what the three modules need.
8. **The permission matrix is capped at API 26–35** (`ApiRange.MIN_MATRIX_SDK` / `MAX_MATRIX_SDK`), because compileSdk 35 is what was examined. Nothing here asserts Android 16 behaviour.
9. **`candidateTransports` is a host-side candidate list** derived from the phone's own feature flags. It is not evidence that any headset supports anything, and no field in the model could say so (ADR-P2-008).
10. **`openStateChanges` registers eagerly,** before collection starts, so a caller that opens a channel and never collects it must still dispose the registration it was handed. The type makes that correct behaviour possible to express and does not make the mistake impossible.

---

## Deferred, with the phase that owns it

| Deferred item | Owner | Why not Phase 2 |
|---|---|---|
| A module that hosts the Bluetooth layer on a device, and the instrumented run of the four `System*` classes | Phase 3, with the user's authorisation | Prompt sections 6 and 51–52 keep the app module and product UI out of Phase 2; ADR-P2-010 isolates the harness |
| Declaring `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT` and the request flow | Phase 3 | ADR-P2-011: a declaration not tied to an executed call is a fabricated capability |
| Connected-device inspection, bonded list, discovery, profile state | Phase 3 | `BluetoothOperation` marks them `authorizedInPhase = 3`; `PhaseTwoScopeTest` asserts none is authorised now |
| GATT and RFCOMM clients, vendor packets | Phase 6 | Boundary interfaces only exist; `transport-boundaries.md` holds the deferral list |
| LE Audio session work | Phase 10 | Master §20 treats it as an architecture in its own right; ADR-P2-017 keeps its row unclaimed |
| Raising `hardwareEvidence` above `INFERRED` | whichever phase runs the test | `isUsable` is false for every platform feature until evidence exists (ADR-P2-008) |
| Root build script for the duplicated Kotlin plugin warning | Phase 3 build task | Build topology change, out of this phase's scope (Known issue 6) |
| CI, coverage tooling | user decision | RISK-020 remains open; Phase 2 changed no build or release process |

---

## Requirement tally

Reconciled against `docs/phases/phase-2/requirements.md` at close: **22 requirements, `REQ-P2-001` … `REQ-P2-022`** — 7 `tested`, 13 `partly-tested`, 1 `inspected`, 1 `partial`. No requirement is completed by a hardware result, because none exists (see *Mocked versus physical*).

| Id | Requirement | Status at close |
|---|---|---|
| `REQ-P2-001` | The Android mechanism stays behind :platform:android, and the policy half is a registered core area | `partly-tested` |
| `REQ-P2-002` | Adapter availability is inspected as a fact with an unknown rung, never as a boolean or a crash | `partly-tested` |
| `REQ-P2-003` | Adapter-state observation reports what the adapter is now, then what changes, and suppresses duplicates without losing transitions | `tested` |
| `REQ-P2-004` | At most one adapter-state observation runs at a time, and the platform registration is torn down on every exit path | `partly-tested` |
| `REQ-P2-005` | Permission requirements are resolved centrally, version-aware, with a mandatory reason for every row | `partly-tested` |
| `REQ-P2-006` | Permission status is inspected without prompting, and "not asked" is distinguishable from "refused" and from "could not tell" | `partly-tested` |
| `REQ-P2-007` | The platform layer never produces DENIED_PERMANENTLY, and never resolves a falsy platform answer into "off" or "unsupported" | `tested` |
| `REQ-P2-008` | Platform capability is four separated facts, and the fourth one is not in this model | `partly-tested` |
| `REQ-P2-009` | An existing API class is never reported as hardware support, and a question Phase 2 may not ask is answered as unknown rather than skipped silently | `partly-tested` |
| `REQ-P2-010` | Transport boundaries are declared and nothing is opened, probed or simulated | `partly-tested` |
| `REQ-P2-011` | A transport row cannot claim more than its evidence tier, and a selection cannot name a channel that was never offered | `tested` |
| `REQ-P2-012` | The Bluetooth failure vocabulary is widened by union, pinned exhaustively, and refuses to model a state as a category | `tested` |
| `REQ-P2-013` | A platform exception becomes a structured error whose detail names the exception class and nothing else | `partly-tested` |
| `REQ-P2-014` | Blocking platform work runs off the main thread, is bounded, and is never polled | `partly-tested` |
| `REQ-P2-015` | The clock and the two Android version numbers are injected, so no core type reads a platform constant | `partly-tested` |
| `REQ-P2-016` | The seams are injectable, the composition root is manual, and the process owns exactly one platform object by convention | `inspected` |
| `REQ-P2-017` | Neither product module declares a permission, a component or a feature it does not use | `tested` |
| `REQ-P2-018` | Phase authorisation is test metadata, not a runtime gate, and no device-facing operation is reachable | `tested` |
| `REQ-P2-019` | Every architecture guard Phase 2 had to change was replaced, not deleted, and the broadcast allowance is itself confined | `tested` |
| `REQ-P2-020` | The test foundation is plain JVM in both modules, and every Phase 9 named area has a named test | `partly-tested` |
| `REQ-P2-021` | Nothing on prompt §6's forbidden list exists, and the media-audio path is untouched | `partly-tested` |
| `REQ-P2-022` | The phase is documented per prompt §8, with capability wording capped at the evidence | `partial` |

`partly-tested` means what `requirements.md`'s legend says it means: some acceptance criteria are produced by a named automated check and the rest rest on reading a named file, with the split written out per record. The single `inspected` row is `REQ-P2-016` (the composition root and its one-instance obligation) because no check counts live observer instances — that gap is `RISK-031`, not a passing claim.

**Requirements incomplete:** `REQ-P2-022` (documentation) was recorded `partial` while the phase's own records were still being written. As of this commit all eight prompt §8 documents plus the four supporting records exist in `docs/phases/phase-2/`, so the obligation is met and the `partial` marker is a snapshot, not an open item; `requirements.md` carries the dated note. Every other requirement is met at its stated evidence level, and no requirement is marked `deferred` in this phase.

---

## Stop condition

Phase 2 is closed at its boundary. No Phase 3 device detection, no discovery, no pairing, no transport traffic, no vendor protocol, no audio control and no product UI was started, and none is implied by any file in this phase. `docs/MASTER-CONTEXT.md` carries the repository-status banner, and the next phase begins only on the user's explicit authorisation.
