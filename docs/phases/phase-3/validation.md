# Phase 3 — Validation

```text
Phase:   3 — Connected Device Detection
Status:  COMPLETE-WITH-DEFERRALS — VALIDATED (mocked evidence only; see "Mocked versus physical")

Build:                      PASS
Unit tests:                 PASS — 433 core + 90 platform per variant, 0 failures
Instrumented tests:         COMPILE PASS / EXECUTION NONE — 12 methods, never run
Architecture enforcement:   PASS — 17 mechanical checks
Static analysis (lint):     PASS — "No issues found."
Manifest review:            PASS — exactly one declaration, machine-checked against an allowed set
Scope review:               PASS — nothing on prompt §16's forbidden list exists in either module
Physical Bluetooth run:     NONE — no Phase 3 code executed on hardware
Audio path touched:         NO (verified by guard; U-6 keeps the negative claim narrow)
UI screens:                 NONE in product code (verified)

Ready for Phase 4:  YES — the projection is published and the deferred device session is,
                    by user directive and ADR-P3-014, no longer a gate on it.
```

Recorded 2026-10-02 by the orchestrator. Every figure came from running a command or reading a file in this repository; nothing was accepted from a sub-agent's summary, and two claims that reached me from one turned out to be false and are named under *Audit findings* rather than repeated. Where a claim rests on reading rather than on an automated check, it says so. Where a claim cannot be made at all, the gap is named instead of filled.

---

## Verification basis

| Check | Command or method | Measured result |
|---|---|---|
| Full gate | `./gradlew --offline :core:test :platform:android:test :platform:android:compileDebugAndroidTestKotlin :platform:android:lintDebug --rerun-tasks` | **BUILD SUCCESSFUL in 40s, 70 actionable tasks executed** — every task re-run from source, nothing served from cache |
| `:core` tests | `core/build/test-results/test/*.xml`, parsed | **433 tests, 0 failures, 0 errors, 0 skipped**, across **47** test classes |
| `:platform:android` tests | `platform/android/build/test-results/test{Debug,Release}UnitTest/*.xml`, parsed | **90 tests per variant**, 0 failures, across **7** classes; **180 executions** over the two variants |
| JVM total | same XML | **613 executions**, 0 failures |
| Instrumented sources | `compileDebugAndroidTestKotlin` + filesystem | compiles; **12 `@Test` methods on disk** (4 smoke + 8 connection), **none executed** |
| Architecture enforcement | `DependencyDirectionTest`, 17 checks | PASS, now scanning production **and** instrumented **and** JVM unit sources |
| Static analysis | `:platform:android:lintDebug` | `No issues found.` in `lint-results-debug.txt` |
| Warning gate | `allWarningsAsErrors = true` in all modules | zero-warning compile of every source set including `androidTest` |
| Requirement tally | `docs/phases/phase-3/requirements.md` §Summary, re-checked against the tree | **27 requirements** — see *Requirement tally* |
| Source volume | filesystem | `:core` 105 main / 53 test files; `:platform:android` 16 main / 8 test / 2 instrumented. Phase 3 added 9 `:core` main files (2,010 lines) and 3 platform connection files (1,372 lines) |
| Change volume | `git diff --stat 01b8b09..HEAD` | 40 files, **+9,824 / −51** committed for Phase 3, before the close-out commits below |
| Toolchain | Gradle 8.9 wrapper, AGP 8.7.3, Kotlin 2.0.21, JDK 17.0.20 (user-level AppData path, selected per invocation by `JAVA_HOME`), `--offline` throughout | as recorded; no toolchain was modified, no dependency version changed at close |
| Android configuration | compileSdk 35, minSdk 26, JVM target 17 | read from `gradle/libs.versions.toml` and the module build scripts |
| Device state at close | no `adb` call made in this session | **no device attached and none sought** — the deferred session is a user decision (ADR-P3-014), and the device-access policy was not exercised |

---

## Mocked versus physical — kept apart deliberately

| Evidence | What it actually covers | Level it can earn |
|---|---|---|
| 433 `:core` JVM tests | The observation engine: three-axis vocabulary, transition legality, the reconciliation fold, dedupe, refusal, invalidation, snapshot/event ordering, the paired census, the authorised-operation set | `IMPLEMENTED` at most |
| 90 `:platform:android` JVM tests (per variant) | The Android half's *decisions*, reached through scripted seams: a `ConnectedDeviceHandle` that answers what a test tells it, a standing reader that is a lambda, a clock that is a map | `IMPLEMENTED` at most. **No Android framework method body executed in any of them.** |
| `compileDebugAndroidTestKotlin` | That 12 instrumented methods are type-correct against the real SDK and that the five test coordinates resolve | Evidence that a run is *possible*, not that one happened |
| Phase 3 code on hardware | **Nothing.** No Phase 3 class has run on a phone | — |

The separation is structural, not a gap better effort would have closed. `SystemConnectedDeviceHandle` calls `getSystemService`, `getProfileProxy`, `getDevicesMatchingConnectionStates`, `getBondedDevices`, `registerReceiver` and the `ServiceListener` bridge, and every one of those is transcribed from compileSdk 35 stubs and `android-15.0.0_r11` reference source — which is evidence about documents, not about a handset (research §0.1 caps `[SDK]` at existence and `[AOSP-35]` at one implementation of one release; OEMs fork it). A green JVM suite therefore proves the engine honours its own rules against a scripted platform, and proves nothing about whether the script matches the phone.

**What the deferred device session must confirm, in order.** The ten steps of prompt §19 (pair through Settings, connect normally, open observation, verify the device appears, disconnect, verify the projection updates, reconnect, check for duplicate records, disable Bluetooth, check that stale state is not presented as active), plus the twelve compiled instrumented methods: `theInstrumentationRunsAgainstOurOwnPackage`, `theBluetoothManagerIsObtainableFromTheSystemService`, `anAdapterStateReadAnswersWithoutHoldingAPermission`, `permissionStandingIsReadWithoutAnyPrompt`, `aRefusedStandingProducesATypedRefusalAndNoDeviceList`, `aRefusedStandingNeverAsksWhichProfilesCouldAnswer`, `theProfileReportCoversEveryEntryInTheMaintainedUnion`, `aDecliningPlatformNeverLooksLikeAnEmptyRoom`, `theConnectionChannelRegistersAndReleasesCleanly`, `noProfileBeyondTheHandsetsApiLevelIsReportedAnswerable`, `noReportedObservationCarriesADeviceIdentifier`, `thePairedCollectionRefusesRatherThanReportingNoPairings`. The research's twelve named unknowns (U-1 … U-12) are the questions those runs answer, and the sharpest is U-4: whether an exported-registered receiver actually receives Bluetooth-stack broadcasts on the target handset. If it does not, event-driven observation as designed here does not work, and that is ADR-P3-008's premise failing rather than a test being red.

No claim about a handset is made anywhere else in this record. `docs/security/device-access-policy.md` records the run boundary before the run; `test-plan.md` §9 is the ordered script.

---

## Android APIs used, and the platform limitations they carry

Used, all from the public surface of compileSdk 35, all reads:

- `Context.getSystemService(BluetoothManager::class.java)`, `BluetoothManager.adapter`, `BluetoothManager.getDevicesMatchingConnectionStates(int, int[])`, `BluetoothManager.getConnectionState(BluetoothDevice, int)`
- `BluetoothAdapter.state`, `BluetoothAdapter.getProfileProxy(Context, BluetoothProfile.ServiceListener, int)`, `BluetoothAdapter.closeProfileProxy(int, BluetoothProfile)`, `BluetoothAdapter.getBondedDevices()`
- `BluetoothProfile.getDevicesMatchingConnectionStates(int[])`, `BluetoothProfile.getConnectionState(BluetoothDevice)`, `BluetoothProfile.ServiceListener`
- `BluetoothDevice.getAddress()`, `getName()`, `getBondState()`, and the extras `EXTRA_DEVICE`, `EXTRA_STATE`, `EXTRA_BOND_STATE`, `EXTRA_PROFILE`
- Broadcasts: `BluetoothDevice.ACTION_ACL_CONNECTED`, `ACTION_ACL_DISCONNECTED`, `ACTION_BOND_STATE_CHANGED`, `BluetoothA2dp`/`BluetoothHeadset`/`BluetoothLeAudio`/`BluetoothHearingAid`/`BluetoothCsipSetCoordinator` `.ACTION_CONNECTION_STATE_CHANGED` — an eight-action filter
- `Context.registerReceiver` (exported on API 33+, plain below) and `unregisterReceiver`, `IntentFilter`, `Intent.getIntExtra`/`getParcelableExtra`
- Phase 2's `BluetoothAdapter.state` read is reused rather than re-won; the bond read takes its own local copy of that fact because the platform makes the bond result depend on it

Limitations that are properties of the platform, recorded rather than worked around:

1. **There is no API that lists all connected devices.** `BluetoothManager.getConnectedDevices(int)` is documented for GATT and GATT_SERVER only and returns empty on error. Observation is therefore a union over a maintained seven-profile list plus two link-layer and one bond broadcast, and an empty union means different things depending on whether the profiles answered (ADR-P3-008).
2. **Refusal is indistinguishable from emptiness on every enumeration path.** A denied `BLUETOOTH_CONNECT` yields empty collections, `STATE_DISCONNECTED`, and broadcasts that are silently not delivered; only the legacy `getProfileProxy` branch throws. The defence is ordering plus types: standing is settled before the enumeration and before the bond read, and `snapshot()`/`bondedDevices()` return `ObservationRound`, which has no representation for "a list from a round that was not allowed to produce one" (ADR-P3-009).
3. **`getBondedDevices()` answers four worlds with one value** — no pairings, adapter off, no adapter, refused standing — per its own Javadoc ("If Bluetooth state is not STATE_ON, this API will return an empty set", "or null on error"). Hence `BondedListing`'s four cases, and only `Reported` against an adapter present and on may carry an empty list (ADR-P3-017).
4. **`getProfileProxy` returns a bare boolean with no error code,** and its Javadoc names five profiles while the reference implementation refuses any profile it has no constructor for. A `false` is recorded as a refusal of *that profile*, never as an empty device list. Whether a bind that returned `true` ever calls back is U-3, unverified.
5. **`CONNECTING`/`DISCONNECTING` exist in the vocabulary because the platform's state constants name them**, not because any path here has observed one. The engine synthesises neither (ADR-P3-015 rule 2).
6. **HID is enumerable and never announceable** — no public connection-state action exists for it in the compile SDK. Stated as a gap in the action table.
7. **`BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED` is excluded deliberately:** it is an aggregate edge with no device extra, so a second earbud produces nothing and its silence cannot be attributed.
8. **The matrix is capped at API 26–35** because compileSdk 35 is what was examined; nothing here asserts Android 16 behaviour.
9. **Address stability is unverified** (U-2): `DeviceObservationKey` assumes a stable bonded-classic address, which is a design assumption about a handset, not a platform promise this project has collected evidence for.

---

## Static analysis and the one shipped defect Phase 3 corrected in Phase 2's code

`:platform:android:lintDebug` reports **No issues found.** The two warnings the version-guarded registration raises are suppressed with the reasoning in the function's own KDoc and recorded in `docs/phases/phase-2/validation.md`; the alternative — taking `androidx.core` for `ContextCompat.registerReceiver` — was refused because it would be the boundary module's first AndroidX dependency for no behavioural gain.

**The correction is the more consequential result of this phase.** Phase 2 registered `ACTION_STATE_CHANGED` with `Context.RECEIVER_NOT_EXPORTED`, reasoning that a protected system broadcast needs no export. That conflates two rules: protection governs who may *send* a broadcast, the export flag governs who may *reach* a receiver, and the platform's guidance names Bluetooth as a highly privileged app whose broadcasts a not-exported receiver does not get. `SystemBluetoothAdapterHandle` was changed to register exported (`883e16d`, ADR-P3-013) — a fix to shipped behaviour, made from documentation before any handset was consulted, because the failure it prevents is the silent-empty one this phase refused everywhere else. It is also the one change in this phase that could itself be wrong in a way only a handset can reveal, which RISK-063 records rather than resolves.

---

## Scope review — what Phase 3 was forbidden to do

Prompt §16's list, enforced by `DependencyDirectionTest` (17 checks) plus `PhaseThreeScopeTest` (8), across `src/main`, `src/androidTest` and `src/test`:

| Forbidden in Phase 3 | Status | Enforced by |
|---|---|---|
| Fingerprinting, manufacturer/model inference | absent; `DeviceObservation` has no manufacturer, model, class or confidence field, and no name-based inference exists | `DeviceObservationVocabularyTest`, reading `DeviceObservation.kt` — **no check forbids adding such a field** |
| Vendor protocol selection, RFCOMM/GATT control sessions | absent; `TRANSPORT_GATT_OPEN` and `TRANSPORT_RFCOMM_OPEN` are `authorizedInPhase = 6` | `noScanTransportOrSessionOperationIsAuthorisedAtPhaseThree`, rule 7's token bans (`connectGatt`, `createRfcommSocket`, …) |
| GATT service/characteristic discovery, SDP | absent | rule 7 forbids `fetchUuidsWithSdp` by name (an over-the-air transaction that looks like a query) |
| Battery, ANC, transparency, EQ, gesture, firmware, codec control | absent — nothing in this phase writes to a device | rules 7 and 11; `AndroidConnectedDeviceSourceTest` pins that no operation asks for a write permission |
| Discovery / scanning | **absent, and the model no longer claims otherwise** — `DEVICE_DISCOVERY_SCAN` was corrected from `authorizedInPhase = 3` to `5` (`caf286e`, ADR-P3-018) | `PhaseThreeScopeTest`, by name and again by `transport.`/`leaudio.` prefix |
| Persistent saved-device database | absent; no `persistence` import is reachable from `platform`, the paired census is replaced each round and never accumulated | layer rule, `eachPairedRoundRestatesTheCollectionInsteadOfAccumulatingIt` |
| Automatic pairing or connection attempts | absent; `createBond` and connection calls are not reachable | rule 7 |
| Audio path (A2DP stream, routing, codec, forced profile) | untouched; binding a profile proxy is the closest this phase comes and it is a documented unknown (U-6) | `neitherModuleTouchesTheMediaAudioPath`, rule 7's voice-recognition bans |
| Production UI, Quick Settings, notification controls | absent in both product modules | `neitherModuleReferencesUiFrameworks`; the only installable artifact is the debug harness, which cannot import product code |
| Permission dialogs from a background observer | absent — no `requestPermissions` call exists anywhere in the module | rule 7, `everyAuthorisedOperationHasAPlanAndNoneOfThemPromptsTheUser` |
| Address in logs | the key type cannot print its address; `detail` strings are fixed sentences, counts or exception class names | `DeviceObservationKeyTest`, `aKeyNeverPrintsTheAddressItWraps` |

**Two honest exceptions, stated because a reader grepping will find them.** `displayName` is carried as reported and a `DeviceObservation` is a data class, so a record's `toString()` includes the name — the redaction guarantee is a property of `DeviceObservationKey`, not of the record. With the paired census this is now true of two collections rather than one, and it is a live risk (RISK-043, re-scored) whose mitigation is that Phase 3 has no log sink: `diagnostics/OmniBudsLogger` is untouched and ADR-P1-019's redaction duty still has no owner before Phase 36.

**The new guards were falsified in both directions before being trusted.** A scratch `androidTest` file calling `fetchUuidsWithSdp` failed rule 7 and named `androidTest/ScratchProbeTest.kt`; an injected second `<uses-permission>` failed the manifest check; both probes were then removed (`ADR-P3-016`). Each source-root scan also fails the build if the root is missing or contains no Kotlin, so a widened scan cannot go vacuous later.

---

## Definition of done (prompt §20)

```text
[x] Phase 0, 1 and 2 contracts respected        layer map unchanged in shape; Phase 2's guards widened, never deleted; ADR-P2-016 amended by ADR-P3-013/-016 rather than rewritten
[x] Connected and bonded states are distinct    three independent axes; BondedDeviceObservation has no link field and no route into the connected projection
[x] Android connection observation exists       SystemConnectedDeviceHandle over profile proxies, ACL/bond broadcasts and the bond list — compiled, never executed
[x] Initial snapshot and event reconciliation   snapshot-before-registration order in the source, engine-side fold, ADR-P3-009's standing gate, TEST-P3-009/-015
[x] Multiple devices supported                  keyed by DeviceObservationKey; two devices sharing a label stay two records; unkeyed reports stay separate
[x] Multiple profiles without duplication       profiles merge onto one record by key; a drop on one service cannot disconnect a device another still holds
[x] Disconnection updates the active projection link-layer disconnect clears every reported profile; the projection filter is isReportedConnected
[x] Adapter disablement handled                 every record becomes UNAVAILABLE with link back to UNKNOWN; the census is marked stale, not emptied; nothing claims a headset powered off
[x] Permission failures represented accurately  refusal is ObservationRound.Failure, never an empty list; denied, unasked and unreadable all stay distinct
[x] Device identity remains conservative        key + reported name + three axes + arrival + time; no class, no manufacturer, no confidence field
[x] No unsupported manufacturer inference       none exists; no check forbids a later phase adding one (named in known issues)
[x] No permanent device history                 no persistence import; unkeyed records replaced each round; each paired round restates rather than accumulates
[x] No vendor control protocol                  nothing under transport/ was touched; protocol registry still empty
[x] No normal audio path modified               guard-clean, with U-6 kept as the reason the negative is narrow
[x] Unit tests pass or failures documented      613 executions, 0 failures; 12 instrumented methods recorded as unrun, not as passing
[x] Architecture tests pass                     17 checks
[x] Mandatory Phase 3 documentation exists      8 records in docs/phases/phase-3/ (§18's set), plus the prompt and two research/audit records
[ ] Physical-device results honestly recorded   there are none; this line is the honest record of that absence, and it is left open so it cannot be read as a device claim
[x] Git diff contains only authorised changes   staged by explicit path; no `git add -A`; the concurrent device-bridge workstream's untracked files are neither edited, committed nor deleted
[x] Final validation report is complete         this record, with the deferred-verification list and the evidence ceiling stated before any capability claim
```

Nineteen of twenty are met outright. The box left open is the hardware one, and it is left open deliberately: §20 asks that physical-device results be "honestly recorded", and the honest record is that there are none — ticking it would read as a device claim, which is the exact failure this project's evidence ladder exists to prevent. So no box above rests on a handset, and the phase's real gap is not an unchecked line but the level never reached: `HARDWARE_VERIFIED` appears nowhere in Phase 3, and RISK-044 carries why.

---

## Audit findings closed at close-out

Four claims the tree made about itself were false, and all four were corrected in the code or in the record rather than carried:

- **The answerability ladder was inverted** (found by the orchestrator reading `ConnectedDeviceHandle.kt` against its own KDoc). `evidenceRank()` ranked `REFUSED` above `AWAITING_CALLBACK`, which is what the enum's declaration order would have produced and what both KDocs named as the failure to avoid: one channel's `getProfileProxy` returning `false` could outvote a channel that had not called back, turning "this round happened early" into "the platform declined this profile" — and a declined profile is what makes a union count as having seen nothing. Fixed and pinned in both directions by `ProfileAnswerabilityTest` (ADR-P3-019). The rank had no test before, which is why a green suite passed it.
- **A model row contradicted the phase it named.** `DEVICE_DISCOVERY_SCAN` carried `authorizedInPhase = 3` while prompt §16 forbids discovery. Nothing read the tag, so nothing could notice; `PhaseThreeScopeTest` now reads it and the tag names Phase 5 (ADR-P3-018, closing gap `TEST-P3-036`).
- **A guard excluded a source set on a reason that was not true.** ADR-P3-016 said `src/test` stayed out of the capability scan because those files hold no framework type; three Phase 2 files there import `android.bluetooth.BluetoothAdapter`. The scan now covers them through the same single list (`d3b03df`), and the suite still passes — the widening bought coverage, not a code change.
- **A record claimed a test that does not exist.** ADR-P3-006's consequences said "the mapping is asserted by a test that walks §15's shapes to a category". It does not; five of nine shapes are pinned by named assertions and two have no producer. The paragraph now says that, dated, rather than keeping the claim.

Two claims that reached the orchestrator from a sub-agent's audit were checked and rejected before being written down, which is the reason this section exists: a report that `LINKED_STATES` had begun to include a bonded filter (it does not — it is still CONNECTED/CONNECTING/DISCONNECTING), and a report that an unreadable bond demotes to `NONE` (it does not — `bondStateOf` maps `UNREADABLE` to `UNKNOWN`). Both were verified against the files before either statement appeared in this record.

---

## Documentation created

| Record | Content |
|---|---|
| `execution-prompt.md` | The authorising prompt, verbatim (744 lines) |
| `architecture-audit.md` | Pre-execution audit of the tree, integration plan, §10's forbidden-capability list |
| `connection-observation-research.md` | API compatibility matrix, observation mechanisms, twelve named unknowns, with the orchestrator's §0.2a re-verification of four claims the architecture now rests on |
| `requirements.md` | `REQ-P3-001` … `REQ-P3-027`, each with rationale, priority, dependencies, acceptance criteria, verification and named inspection residue |
| `design.md` | The two-collection fold, the refusal ladder, the profile union, the receiver model |
| `specs.md` | Type-level specs with the file:line each claim lives at and the check that pins it |
| `decisions.md` | `ADR-P3-001` … `ADR-P3-019`, including two dated amendments and three corrections |
| `task-list.md` | `TASK-P3-001` … with dependencies and required tests |
| `test-plan.md` | `TEST-P3-001` … per suite, with tier, expected, failure mode, hardware status, level and linkage |
| `risk-register.md` | `RISK-044` … `RISK-064` — twenty-one entries continuing the single project-wide sequence — plus the re-scored carried-forward entries |
| `validation.md` | This record |
| `docs/security/device-access-policy.md` | The instrumented-run boundary written before the first run, corrected for ADR-P3-013 |

**Requirement tally.** Reconciled against `requirements.md` at close: **27 requirements** — 6 `tested`, 19 `partly-tested`, 1 `deferred` (`REQ-P3-025`, the device session), 1 `inspected` (`REQ-P3-027`, the record set itself, closed at close-out by reading it rather than by a check). `partly-tested` is the honest majority here and means what the file's legend says: some acceptance criteria are produced by a named automated check and the rest rest on reading a named file, with the split written out per record. Two rows moved during close-out: `REQ-P3-019`'s "collection B is not produced by any Phase 3 code path" became a produced-and-replaced census, and `REQ-P3-023`'s residue no longer has to say that rules 8 and 9 scan `src/main` only. Nothing is completed by a hardware result, because none exists.

---

## Known issues and limits

1. **No Phase 3 code has run on a device, and the phase that would have run it was removed as a gate.** The scaffolding exists and compiles; ADR-P3-014 defers the session to the end of the project at the user's direction. Every Android claim here is `IMPLEMENTED`, never `HARDWARE_VERIFIED`. RISK-044 and RISK-045 carry this, and RISK-045 is the process half: nothing now forces the deferred state to change.
2. **The engine cannot detect a lying source.** A fake that reports every profile `ANSWERABLE` and then returns nobody satisfies every JVM check in the suite. `TEST-P3-035` records this; call-order assertions are the only real defence, and they are a guard against a mistake, not against a wrong platform.
3. **`CONNECTING`/`DISCONNECTING` are modelled because the constants exist**, and no path here has observed one. A phase that must show a live spinner cannot build on this projection.
4. **No re-snapshot is taken when permission standing improves.** A round refused for standing stays refused until the next round; §14's `refresh()` exists, and nothing calls it on a grant.
5. **Record count is unbounded.** The projection holds every attributed device; only the refusal window is bounded (eight). A phone with a long pairing history and many live profile records is unmodelled.
6. **Concurrency is tested single-threaded.** The slot mutex and the projection lock are exercised by sequential `runTest` cases; contention between a live collector and a concurrent `refresh()` has no test.
7. **No check forbids the inference this phase refused.** Absence of a manufacturer field, of a name-based merge, of an `ObservedProfile.toTransportKind()` and of a `DeviceObservation`-into-`ConnectionState` mapping are all readings of a file, not properties of a check (`TEST-P3-039` and others).
8. **A display-name change between two observations of one device is not driven by any test.** Joining is name-blind by construction and change detection compares `displayName`, so the combination follows from two tests plus a reading; nothing checks it.
9. **`resourceLeakOnPartialOpen` and the handle's `dispose`/`abort` paths have never executed**, on a JVM or elsewhere — they are framework-facing (`REQ-P3-020`).
10. **The Kotlin Gradle plugin is loaded in more than one subproject** and Gradle warns "may break the build". Pre-existing since Phase 1, unchanged here, because build topology is outside this phase's scope.
11. **No CI exists** (RISK-020), so "verified" means verified once, on this workstation, offline. The instrumented coordinates additionally require one online resolve on a fresh machine before `--offline` works again.
12. **`docs/phases/phase-0/bluetooth-governance.md` still does not exist** while Phase 3 wrote nineteen Bluetooth ADRs about receivers, export flags, profile unions, device keys and permission ordering (RISK-038, re-scored). None of them is a rulebook and none can be cited as one.

---

## Outstanding risks

`RISK-044` (the framework half has never executed and every device claim in it is a transcription), `RISK-045` (deferral has no owner, no expiry and no trigger) and `RISK-048` (permission denial is structurally invisible to the platform's own answers, so the whole defence is an ordering rule with one implementation) are the phase's three severe entries, and none is closed here: one is a state of the evidence, one is that nothing any longer forces it to change, and one is the mechanism that keeps the first two honest.

The entries a Phase 4 consumer meets first: `RISK-047` (a permission revoked mid-observation is invisible and the projection keeps claiming connections), `RISK-049` (the engine trusts its source, and no test catches a source that lies or merely re-orders itself), `RISK-050` (the maintained union silently omits every profile not in it), `RISK-053` (a binaural pair is two devices to this model while the product's subject is one product), `RISK-054` (a masked or rotating address changes the key and fragments a device across reconnects), `RISK-055` (two devices sharing a display name can never be merged, by design, and the ambiguity passes to whoever renders it), `RISK-056` (the projection never forgets, so a long-lived observation is an in-process device history by accumulation even though nothing persists), `RISK-057` (concurrency is proven only on one thread) and `RISK-058` (`refresh()` has no owner while three failure modes recover through it). Two are specific to what this close-out changed: `RISK-063` (the receiver-export correction ships unconfirmed, and it is the one decision that can make the whole engine silent) and `RISK-064` (two mechanisms disagreeing about a bond are resolved by rank, in silence, by a data class). Behind them the re-scored carry-forwards `RISK-035`, `RISK-038`, `RISK-039`, `RISK-041`, `RISK-042` and `RISK-043`. The register's own framing of the central exposure is the sentence to carry into Phase 4: **no check in this repository can currently detect a wrong transcription of Android's behaviour**, and Phase 3 is the third consecutive phase for which that is true.

---

## Git

| Item | Value |
|---|---|
| Branch / remote | `main` → `origin/main` (`greninja-op/OMNI-BUDS`) |
| Phase 3 commits | `8b44b00` … `d3b03df` — audit, research, five ADR batches, instrumented route, axes, core engine, Android layer, adapter-receiver correction, prompt staging, guard truthing, discovery authorisation, paired census, guard widening |
| Close-out commits | `ac135c2` (paired census + ladder), `d3b03df` (guard widening), then this record set |
| Working tree | `tools/device-bridge/**` remains untracked and untouched by Phase 3 — a concurrent workstream's files, deliberately not staged, not committed, not deleted |
| Diff hygiene | every commit staged explicit paths; no `git add -A`, no `--no-verify`, no force operations |

---

## Stop condition

Phase 3 is closed at its boundary. No device session, no fingerprinting, no manufacturer identification, no vendor protocol selection, no GATT or RFCOMM control channel, no battery, ANC, transparency, EQ, gesture, firmware or codec path, no audio interception, no saved-device store, no Quick Settings or notification control and no product UI was started, and none is implied by any file in this phase. `docs/MASTER-CONTEXT.md` carries the repository-status banner.

**Readiness for Phase 4 (Device Session & Lifecycle Management): yes.** What Phase 4 may build on is the projection — a keyed, three-axis, profile-attributed view of what the platform reports a link to, published as a `StateFlow`, with a separate live census of the phone's pairings, a typed refusal wherever the platform could not answer, and an authorised-operation set pinned by enumeration. What it must not read into that: identity, earbud-ness, capability, support, or a session. `ConnectedDeviceObserver` deliberately never produces a `com.omnibuds.core.state.ConnectionState`; that translation is Phase 4's own decision, and the three axes stay unfused so it has something honest to translate from. The deferred device session stays deferred at the user's direction and is not a condition on starting Phase 4.

Implementation stops here. Phase 4 begins only on the user's explicit authorisation.
