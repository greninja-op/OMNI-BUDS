# Phase 4 — Validation

```text
Phase:   4 — Device Session & Lifecycle Management
Status:  COMPLETE-WITH-DEFERRALS — VALIDATED (mocked evidence only; see "Mocked versus physical")

Build:                      PASS
Unit tests:                 PASS — 494 core + 90 platform per variant, 0 failures
New Phase 4 tests:          61 (41 engine, 14 structural purity, 6 phase-scope)
Architecture enforcement:   PASS — 17 existing checks + 6 Phase 4 scope checks
Static analysis (lint):     PASS — "No issues found."
Instrumented tests:         12 methods, all Phase 3's, COMPILE PASS / EXECUTION NONE — unchanged by this phase
Physical run:               NONE — no Phase 4 code executed on hardware (and none of it reaches a radio)
Persistence:                NONE — no store, no import of the persistence area, machine-checked
UI screens:                 NONE in product code (verified)

Ready for Phase 5:  YES — the projection now has a single owner with an authoritative
                    published state; device verification remains deferred by user directive
                    (ADR-P3-014) and is not a gate.
```

Recorded 2026-10-02 by the orchestrator. Every figure below came from running a command or reading a file in this repository. Six records for this phase were drafted by sub-agents, and this close-out is where their claims were checked line by line: three of the four defects named under *Audit findings* below were found that way, and one of them was a defect in **my own** ADRs rather than in anybody's code. Where a claim rests on reading rather than on a check, it says so.

---

## Verification basis

| Check | Command or method | Measured result |
|---|---|---|
| Full gate | `./gradlew --offline :core:test :platform:android:test :platform:android:compileDebugAndroidTestKotlin :platform:android:lintDebug --rerun-tasks` | **BUILD SUCCESSFUL, 70 actionable tasks executed** — every task re-run from source |
| `:core` tests | `core/build/test-results/test/*.xml`, parsed | **494 tests, 0 failures, 0 errors, 0 skipped** across **50** classes |
| `:platform:android` tests | `testDebugUnitTest/*.xml` and `testReleaseUnitTest/*.xml`, parsed | **90 per variant** across 7 classes; unchanged by Phase 4 (no platform-module tests were added) |
| JVM total | same XML | **674 executions** (494 + 90 + 90); **584** distinct cases |
| Phase 4's own suites | same XML | `DeviceSessionEngineTest` 41, `SessionStatePurityTest` 14, `PhaseFourScopeTest` 6 = **61** |
| Architecture enforcement | `DependencyDirectionTest` (17) + `PhaseFourScopeTest` (6) | PASS; the layer map is unchanged — Phase 4 added no area and no module |
| Static analysis | `:platform:android:lintDebug` | `No issues found.` in `lint-results-debug.txt` |
| Warning gate | `allWarningsAsErrors = true` | zero-warning compile of every source set, test sources included |
| Instrumented sources | `compileDebugAndroidTestKotlin` | compiles; **12 methods** (`BluetoothInstrumentedSmokeTest` 4, `ConnectedDeviceObservationInstrumentedTest` 8), **none executed**, none added by Phase 4 |
| Requirement tally | `requirements.md` §Summary, re-checked against the tree | **30 requirements** — see *Requirement tally* |
| Source volume | filesystem | 7 new `:core` main files in `core/session/` totalling **1,105 lines** (engine 527); 2 amended Phase 1/3 sources; 1 amended DI file. `:core` main is now 112 files |
| Change volume | `git diff --stat c246480..HEAD` | 5 commits: +2,290 lines of Kotlin and tests across 17 files, plus the record set |
| Toolchain | Gradle 8.9 wrapper, AGP 8.7.3, Kotlin 2.0.21, JDK 17.0.20 (user-level AppData path, `JAVA_HOME` per invocation), `--offline` throughout | unchanged; no toolchain touched, no dependency added or version changed |
| Device state at close | no `adb` call made in this session | **no device attached and none sought** — ADR-P3-014 makes this the standing condition, and Phase 4 wrote no code that could reach one |

---

## Mocked versus physical — kept apart deliberately

| Evidence | What it actually covers | Level it can earn |
|---|---|---|
| 494 `:core` JVM tests, 61 of them Phase 4's | The state machine, the mapping table, the grace rule, duplicate suppression, ambiguity handling, event ordering, cancellation semantics, the authorisation set | `IMPLEMENTED` at most — tier T1, `testing-governance.md:39` |
| 90-per-variant `:platform:android` tests | Phase 2's and Phase 3's decisions against scripted seams. Phase 4 added none | `IMPLEMENTED` at most |
| 12 instrumented methods | That a device run is possible and compiles | nothing; **they have never executed** |
| Phase 4 code on hardware | **Nothing, and nothing new to run** — Phase 4's only platform-module line is `omniBudsDeviceSessionEngine`, a constructor | — |

The separation here has a different shape than in Phases 2 and 3, and it is worth naming because it is easy to misread as weaker evidence than it is. Phase 4 added **no Android-facing behaviour**: the engine's entire input is a `ConnectedDeviceSnapshot` that Phase 3 already published, and the only handset-facing question Phase 4 can get wrong is one it inherited. So the honest statement is not "Phase 4 is unverified on hardware" but **"Phase 4's claims are only as true as Phase 3's transcription, and Phase 3's transcription has never been checked against a handset"** — which is RISK-065, and why the register says the severe entries are carried rather than reduced.

Two claims Phase 4 makes are additionally unverifiable by any device session, and the register says so rather than parking them: that the **one-round grace** is the right window (it is a policy choice, settleable only by deciding what a user should see), and that **a session is the right unit** at all for a binaural pair (RISK-053 carried forward; the product's subject is one product and this model holds two devices).

**What the deferred session must confirm for this layer specifically:** that a real disconnect arrives as a positive report rather than as absence (if it arrives only as absence, `PROVEN_DISCONNECT_PAST_GRACE` fires one round later than the UI should); whether a stack ever lets a link reading vanish while still reporting the device observable — `aTrackedDeviceWhoseLinkReadingVanishesIsRefusedRatherThanRewritten` pins today's answer, which is that the engine refuses the move and holds the state; and whether unkeyed devices are common enough for the one-round ambiguity churn to be user-visible. Recorded as an ordered list in `test-plan.md`, and nothing on it has run.

---

## The session state machine, as implemented

Prompt §7 sketched `CREATED → OBSERVING → ACTIVE → TEMPORARILY_UNAVAILABLE → RECONNECTED → DISCONNECTED → ENDED`. Phase 1 already owned that territory: `ConnectionState` has eleven members and `ConnectionStateTransitions` is a total forward map. The phase therefore **reused the machine and constrained the reachable set** rather than drawing a second one (ADR-P4-002, ADR-P4-003).

Reachable — derived from `allowedNext(UNKNOWN)` minus `ERROR`, so it cannot drift from the table (`TrackedDeviceSession.kt:125-128`), and pinned by `theEngineCanOnlyReachSixStatesAndNoneOfThemImpliesCapabilityWork`:

```text
UNKNOWN ⇄ DISCOVERED ⇄ CONNECTED ⇄ TEMPORARILY_UNAVAILABLE
   ↓          ↓           ↓                    ↓
 PAIRED   DISCONNECTED ← ← ← ← ← ← ← ← ← ←
               ↓
        (one round of grace) → session ended, record dropped
```

Unreachable and machine-pinned as such: `IDENTIFYING` (Phase 5), `CAPABILITY_DISCOVERY` (Phase 8), `READY` and `CONTROL_SESSION` (Phase 6), and `ERROR` — refused not because it is dangerous but because it would read as *the device* faulting when the only honest sentence is that this app's evidence ran out (ADR-P4-003; prompt §15's requirement is met by `SessionObservationStatus` carrying the platform's category instead).

Prompt §7's `CREATED`/`ENDED` became events plus a bounded timeline rather than states; `ACTIVE` became a derived read (`isActive` is `connection == CONNECTED`), because a second word for the same fact is the defect Phase 1 deleted from `DeviceSession` and this phase refused to re-add.

## The reconnection policy, stated once

Prompt §9 asked for a policy that is consistent and testable. ADR-P4-006 is it, and all three rules are tested:

1. **A blip resumes the same session.** `CONNECTED → TEMPORARILY_UNAVAILABLE → CONNECTED` keeps the `sessionId`, never sets `disconnectedAt`, and publishes `SESSION_RECONNECTED`.
2. **A proven disconnect gets exactly one further published round, then the session ends.** `DISCONNECTED` is reached either from a positive report or from absence in a *complete* union (ADR-P3-015 rule 4); the record is kept one round so a consumer can see the transition, and is dropped — with `SESSION_ENDED(termination = PROVEN_DISCONNECT_PAST_GRACE)` — if the next completed union still omits it. A repeated positive `DISCONNECTED` uses the same window rather than extending it.
3. **A reappearance after termination is a new session with a new id.** No lineage pointer is retained, because the alternative is a device history and prompt §10 forbids one.

The window is **rounds, not seconds** — deliberately: a timer would make engine behaviour depend on wall-clock scheduling instead of on observations, and prompt §14 forbids unbounded polling. Phase 1 had already half-decided this in prose (`DeviceSession.kt:27-28`: a session id is "not stable across reconnects"); the ADR records that the phase inherited a decision rather than inventing one.

---

## Scope review — what Phase 4 was forbidden to do

Prompt §17's list, with what enforces each line:

| Forbidden | Status | Enforced by |
|---|---|---|
| Fingerprinting, manufacturer/model identification | absent — `identityOf` reads a reported name and nothing else; `DeviceFingerprint` stays null on every session | `aSessionCarriesNoEvidenceThisPhaseDoesNotHave`; ADR-P4-003's state ban |
| Vendor protocol discovery, GATT service discovery, RFCOMM sessions | absent — no `protocol`/`transport` import is reachable from `session` (layer 3 vs 4/1) | layer rule, `theSessionLayerReachesNoControlSeam`, `theSessionLayerNamesNothingThatImpliesAControlSession` |
| Battery, ANC, transparency, EQ, gestures, firmware, codec configuration | absent — `DeviceState.initial()` leaves every one of them unknown and nothing calls a `with…` mutator | `aSessionCarriesNoEvidenceThisPhaseDoesNotHave`, `anIdentityFillIsNotAStateMoveAndNeverMovesTheRevision` |
| Audio processing, stream interception | absent — rule 8 clean in both modules | `neitherModuleTouchesTheMediaAudioPath` |
| **Persistent saved-device database** | absent — `session` cannot import `persistence` (same layer), and every session is `TEMPORARY` | layer rule, `theSessionLayerStillImportsNoPersistenceContract`, `everySessionThisEngineMakesIsTemporary` |
| Quick Settings, notifications, widgets, production UI | absent — no UI framework reference exists in either product module | `neitherModuleReferencesUiFrameworks` |
| Automatic pairing, automatic connection attempts | absent — Phase 4 added no Bluetooth reach at all | `nothingNewIsAuthorisedAtPhaseFour` (the authorised set at 4 equals the set at 3, element-wise) |
| Firmware updates | absent | same |
| A second observer or scanner | absent — the engine consumes `ConnectedDeviceSnapshot` values and cannot reach `ConnectedDeviceSource`/`ConnectedDeviceObserver` | `theSessionLayerReachesNoControlSeam` |

**Two deliberate divergences from the prompt's own sketch**, each decided in the open rather than quietly: prompt §12's `DeviceSessionRepository` name was not used, because `Repository` already means the saved-device contract (`persistence/DeviceRepository.kt`) and ADR-P3-004 refused the word for a projection for the same reason; and its two `StateFlow`s became one published snapshot with derived views, because two authoritative lists of one set of devices is the failure Phase 1 §24 exists to prevent. Prompt §6's `Instant` timestamps became nullable epoch millis per ADR-P1-012, and its `deviceKey: String` became the typed `DeviceObservationKey`, because a printable string holding a Bluetooth address would put SEC-ID-003's "rotating handle on a person's belongings" into a loggable field.

**The identifier guard is real, and its one weak seam is named.** `aSessionIdCarriesNoPartOfTheDeviceThatProducedIt`, `nothingTheEnginePublishesPrintsADeviceIdentifier` and `aNameFillingInPublishesCountsAndNeverTheNameItself` check printed text against an address-shaped pattern. They can only catch what is printed *in tests*; Phase 3's RISK-043 still stands underneath them — `displayName` reaches a session unchanged and a `DeviceSessionSnapshot` is a data class, so a future `Log.i(tag, snapshot.toString())` would leak a label, not an address. Nothing in Phase 4 can emit: `diagnostics/OmniBudsLogger` is untouched and ADR-P1-019's redaction duty still has no owner before Phase 36.

---

## Definition of done (prompt §20)

```text
[x] Previous phase contracts respected            ADR-P4-001..012 all cite and consume Phase 0/1/3 decisions; Phase 1's and Phase 3's suites re-executed green and unweakened
[x] Centralized session management exists         DeviceSessionEngine, one StateFlow<DeviceSessionSnapshot>, one Mutex owner
[x] Session lifecycle is documented               ADR-P4-002/003/006, design §7-§8, specs §5
[x] Observation reconciliation is implemented     targetStateOf + applyReport + applyAbsence + createSession, one table, every row named
[x] Disconnect handling is correct                active projection updates; repeated disconnects are idempotent; the state never goes stale silently (refusals recorded)
[x] Reconnection behavior is deterministic        three rules above, all tested, no timers involved
[x] Multiple devices supported                    two-key and three-key rounds, creation-order publication, independent transitions
[x] Duplicate sessions prevented                  key-indexed; same-key-in-one-round now merged rather than order-decided (close-out fix 3)
[x] Incomplete identity handled conservatively    AMBIGUOUS basis, one-round lifetime, never matched, never merged, no address-derived id
[x] Temporary vs saved separation held            every session TEMPORARY; no persistence import; guard-checked
[x] Reactive session state authoritative          one published value; views derived at read; revision monotonic
[x] Concurrency and cancellation tested           mutex atomicity, NonCancellable teardown on cancel and on completion, restart-after-stop; multi-thread contention NOT tested (known issue 4)
[x] No vendor control functionality introduced    scope table above
[x] No production UI introduced                   rule 9 green; prompt §16 satisfied as API shape only
[x] No unnecessary permanent device history       terminated sessions leave the store; no lineage retained; nothing written to disk
[x] Automated tests pass or failures documented   674 executions, 0 failures; 21 gap ids in test-plan.md name what is untested
[x] Build and static analysis pass                BUILD SUCCESSFUL --rerun-tasks; lintDebug "No issues found."
[x] Mandatory documentation exists                all eight prompt §18 records plus the audit, in docs/phases/phase-4/
[x] Physical-device validation explicitly deferred ADR-P3-014; test-plan's deferred section is an ordered instruction, not a claim
[x] Git diff contains only authorized changes     staged by explicit path across five commits; the concurrent device-bridge workstream's files remain untracked and untouched
[x] Final validation report is complete           this file
```

Twenty-one of twenty-one. The one that reads best is also the one to distrust least-productively: **nothing here rests on a handset**, and the box above is checked because the tests exist, not because a device agreed.

---

## Audit findings closed at close-out

Four things were wrong when the record set was assembled. All four were found by reading records against the tree, and one by an agent contradicting me — which is the only reason to believe this section.

1. **Four ADR claims that the code did not support** (found by the risk-register agent). ADR-P4-003 counted "five states plus `ERROR`" when six are reachable and `ERROR` is deliberately never produced; ADR-P4-004 claimed the mapping table consults a record's `arrival` when no session file reads that field; ADR-P4-006 listed a fifth termination reason the code cannot produce; ADR-P4-009 said ordering was "guarded by `applyIfNewer`" when the engine never calls it. All four were **rewritten in the ADRs** rather than defended: an ADR that overstates its own implementation is worse than an unimplemented requirement, because the next phase will trust it. `RISK-080` and `RISK-081` now describe the live residue (a handset may never deliver `CONNECTING`/`DISCONNECTING`; single-writer serialisation is the actual protection) instead of a contradiction that no longer exists.
2. **Six dead members** (found by the specs agent): `TrackedDeviceSession.termination`/`terminated()`/`isLive`/`withSession`, `SessionTimeline.endedAtEpochMillis`/`closedAt`, and `SessionTermination.ABSENT_FROM_COMPLETE_UNION`. The engine drops a terminated session in the same round it publishes the end, so each was a field whose only writer was code that never ran. Deleted at `e68c808`, and the deletion is recorded in ADR-P4-002 as a correction, not retrofitted into the original design. The absent-from-union *termination* was the instructive one: absence is evidence of a **disconnect**, and only the round after it is evidence of an end — having both in the same enum would have let one quiet round delete a session.
3. **A same-key overwrite that made arrival order authoritative** (found by the specs agent, then fixed): `keyedReports[record.key] = record` meant that if a projection ever carried one device twice, the session state was decided by which row came last — the exact ordering-by-accident ADR-P4-009 refuses for timestamps. It now folds through Phase 3's own `DeviceObservation.mergedWith`, so a live link beats a report of no link regardless of position, pinned from both directions by `twoReportsOfOneKeyInOneRoundAreMergedAndNeverChosenByArrivalOrder` (`b9b82d1`).
4. **Four event types asserted only by construction.** `SessionUpdated`, `SessionDisconnected`, `SessionIdentityChanged` and `SessionObservationFailed` appeared in the suite as hand-built values fed to the no-address scan — which proves the type compiles, not that the engine ever emits it. Four emission tests were added (`4c18a9d`), plus two mapping-row tests and one revision-stability test, taking `:core` from 486 to 494. This is the category of gap a green suite hides best: a test file that names an event is not a test that the event happens.

One earlier finding worth repeating because it changes how the rest of this file should be read: two claims in a *different* sub-agent's audit report were checked and rejected before being written down (that `LINKED_STATES` had acquired a bonded filter, and that an unreadable bond demotes to `NONE`). Neither was true. Every citation in this record was verified by opening the file.

---

## Documentation created

| Record | Content |
|---|---|
| `execution-prompt.md` | The authorising prompt, verbatim (800 lines) |
| `architecture-audit.md` | Pre-execution inspection: the existing contracts, the layer arithmetic, the naming collision, the Phase 1 three-way deferral conflict, the id sequences, the integration plan |
| `decisions.md` | `ADR-P4-001` … `ADR-P4-012`, with four dated close-out corrections |
| `requirements.md` | `REQ-P4-001` … `REQ-P4-030`, each with the seven §18 fields, plus traceability and the not-met list |
| `design.md` | Prompt §5's diagram adapted to what exists, the dataflow, the mapping table, identity, lifecycle, grace, concurrency, privacy, UI-compatibility, divergences |
| `specs.md` | Type-level specification with `file:line` per claim, the 13-row mapping table, the grace window as rounds, the guard set, and 16 open items |
| `task-list.md` | `TASK-P4-001` … `TASK-P4-018` with status and evidence, prompt-section mapping, rollup |
| `test-plan.md` | `TEST-P4-001` … `TEST-P4-031` — executing clusters, the coverage matrix against prompt §19's seven headings, the gap table, and the deferred device session |
| `risk-register.md` | `RISK-065` … `RISK-085` (21 entries), continuing the single project-wide sequence, plus the re-scored carry-forwards |
| `validation.md` | This record |

**Requirement tally.** Reconciled against `requirements.md` at close: **30 requirements** — 6 `tested`, 19 `partly-tested`, 2 `inspected`, 1 `deferred` (`REQ-P4-029`, the device session). 28 `MUST`, 2 `SHOULD`, 0 `MAY`; no untraced requirement. `partly-tested` is again the honest majority: the state machine and the mapping table are genuinely pinned, while requirements about *ordering under contention*, the *right grace window* and *no lying source* have criteria that rest on reading, because nothing in this repository can execute them.

---

## Known issues and limits

1. **Nothing has run on hardware, and this phase added nothing that could.** Every claim is `IMPLEMENTED`; the register's RISK-065 states the inheritance precisely: Phase 4's correctness is bounded by Phase 3's transcription, which has never met a phone.
2. **The composition root is prose.** `omniBudsDeviceSessionEngine` is constructed by no test, and the two-call wiring that connects `observer.observe()` to `engine.apply()` lives in its KDoc as a ` ```text ` fence. A wiring mistake — collecting the snapshot without driving the observation, or driving it without applying — would be invisible until a device is attached. `TEST-P4-025`.
3. **`refresh()` still has no caller.** ADR-P4-011 declines it with a reason (the engine consumes published projections and must not hold the observer), so a permission granted while the radio is quiet is picked up on the next announcement rather than immediately. Phase 3 assigned this to Phase 4; Phase 4 re-scores it rather than closing it, which is the register's job and not a way of hiding an unmet obligation.
4. **Concurrency is proven on one thread.** The mutex serialises reconcile-and-publish, and cancellation is proven — but no test interleaves `consume` with `apply`, or two collectors, or a census arriving out of order. The test that would settle part of it needs no device and has not been written (`RISK-073`, `TEST-P4-026`).
5. **The engine cannot detect a lying projection.** It trusts `ConnectedDeviceSnapshot` completely; a projection that reports a complete union containing nobody would end every session, correctly by its own rules, and no test would notice (`RISK-067`, Phase 3's `RISK-049` one layer up).
6. **Sessions are unbounded.** A keyed session is retained as long as the union keeps answering without it being present; the refusal window is bounded at eight and nothing else is. A long-lived observation over a phone that flaps many devices grows the list, and `activeSessions` grows with it (`RISK-072`).
7. **Event consumers can lose edges.** `replay = 0` with `DROP_OLDEST` is the decision (ADR-P4-008): the state flow is authoritative and a lagging collector misses an edge. That is correct design and a trap in one sentence, so it is recorded as `RISK-071` rather than presented as settled.
8. **A renamed device keeps the old name.** Identity merge is fill-only by Phase 1's design, so a restated name is never adopted and never even recorded as a disagreement — a session's displayed label can be permanently stale and nothing in Phase 4 can say the earlier reading was wrong.
9. **The one-round grace is unvalidated as a product decision.** It is deterministic and testable, which is what prompt §9 asked for, and it may be wrong for a phone whose announcements are slow. Only the deferred session can tell, and the honest current statement is that a wrong value has a lifetime of one round.
10. **`applyIfNewer` has no production caller.** Deliberate (ADR-P4-009 as corrected): with one writer there is nothing to filter. The moment Phase 6's transport can send a command whose response arrives late, that method is the gate, and the revision-stability test added at close-out is what keeps its meaning intact until then.
11. **`docs/phases/phase-0/bluetooth-governance.md` still does not exist** after four Bluetooth-adjacent phases and thirty-one ADRs (RISK-038 carried forward). Nothing in Phase 4 filled the hole with a fabricated rulebook.
12. **No CI, no coverage, no mutation measurement** (RISK-020 enlarged): "verified" means verified once, on this workstation, offline. The Kotlin-plugin duplicate-load warning still prints in every Gradle run and remains out of scope.

---

## Outstanding risks

`RISK-065` … `RISK-085` are this phase's 21 entries; the severe band carries forward rather than being re-declared closed — Phase 4 **closes none of the evidence gaps Phase 3 left**, and says so in its own register. The ones a Phase 5 author must actually read before building on this layer: `RISK-065` (every claim is inherited transcription), `RISK-066` (deferral with no owner, expiry or trigger), `RISK-067` (the engine trusts its projection completely), `RISK-071` (event loss is designed in), `RISK-072` (unbounded sessions), `RISK-073` (single-threaded proof of concurrent code), `RISK-075` (the composition root nothing constructs), `RISK-080` (two mapping rows depend on states a handset may never deliver), and `RISK-082` (`STOPPED` is not a terminal seal — a later `apply` restarts the engine). Re-scored under their own numbers, not duplicated: `RISK-044`, `045`, `047`, `049`, `051`, `052`, `053`, `054`, `056`, `057`, `058`, `062`, `063`, `064`.

The register's own framing of the central exposure carries into Phase 5 unchanged, and it is the sentence to keep: **no check in this repository can currently detect a wrong transcription of Android's behaviour**, and Phase 4 is the fourth consecutive phase for which that is true.

---

## Deferred physical-device verification

Recorded as `NOT RUN` per TST-REC-005 — never as skipped, never as passed:

- Phase 4's own handset questions: whether a disconnect arrives as a report or as absence; whether a link reading can vanish while a device stays observable; whether unkeyed devices make the one-round ambiguity churn visible to a user.
- The carried-forward Phase 3 list: prompt §19's ten manual steps, and all 12 compiled instrumented methods (`thePairedCollectionRefusesRatherThanReportingNoPairings` and its eleven siblings), with U-1 … U-12 as the questions they answer.
- The device bridge's own deferred steps, owned by its workstream and untouched by this phase.

`HARDWARE_VERIFIED` appears nowhere in Phase 4's records. The user's standing directive (ADR-P3-014, reaffirmed at close of Phase 3) is that the application gets built first and hardware is collected at the end; this record exists so that the accumulated list is a list rather than a memory.

---

## Git

| Item | Value |
|---|---|
| Branch / remote | `main` → `origin/main` (`greninja-op/OMNI-BUDS`) |
| Phase 4 commits | `e5ff7bb` (engine + vocabulary + records + tests + DI), `4a17fb5` (prompt, twelve ADRs, audit), `e68c808` (dead-member deletion), `4c18a9d` (event-emission tests), `b9b82d1` (same-key merge fix + mapping/revision tests), then the record set |
| Base | `c246480` — Phase 3's close-out commit, so the phase boundary is one commit wide in history |
| Working tree | `tools/device-bridge/**` remains untracked and untouched by Phase 4; a concurrent workstream owns those files |
| Diff hygiene | explicit-path staging on every commit; no `git add -A`, no `--no-verify`, no force, no amended published commits |

---

## Stop condition

Phase 4 is closed at its boundary. No fingerprinting, no manufacturer or model identification, no vendor protocol discovery, no GATT or RFCOMM session, no battery, ANC, transparency, EQ, gesture, firmware or codec path, no audio processing, no saved-device store, no Quick Settings, notification or widget surface, no production UI, no pairing or connection attempt, and no Phase 5 work was started — and `PhaseFourScopeTest` now machine-checks the two shapes of that claim (no new authorised operation; no session-layer reach into an observation control seam).

**Readiness for Phase 5 (Device Fingerprinting & Identification): yes.** What Phase 5 may build on: a single authoritative owner of per-device session state, keyed by the redacted Phase 3 key, with an opaque minted session id, a connection state produced only from platform evidence and never from a name, a live paired census beside it, and typed refusals wherever the observation could not answer. What it must not read into that: earbud-ness, capability, support, or a control channel — and the state machine is deliberately incapable of expressing them, so `IDENTIFYING` becoming reachable is a decision Phase 5 has to make in an ADR and a test, not a rename. Phase 5 is also where `ADR-P0-018` (research ladder ordering, still awaiting the user) starts to matter, and where `applyIfNewer` stops being unused on purpose.

The device session stays deferred at your direction, and no part of this record should be read as evidence about a phone. Implementation stops here; Phase 5 begins only on your explicit authorisation.
