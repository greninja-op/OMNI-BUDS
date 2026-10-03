# Phase 4 — Architecture Audit

Pre-execution inspection required by prompt section 2, performed before any file was edited, against
`main` at `c246480`. This is the record of what the repository already said about sessions, which is
the part of Phase 4 that a fresh reader would otherwise rebuild.

**The headline finding is that Phase 4 has less to invent than its prompt implies.** Prompt sections
6, 7 and 12 sketch a `DeviceSession` with a `connectionState` and a `lifecycleState`, an eleven-state
lifecycle diagram, and a `DeviceSessionRepository` with three flows. Phase 1 had already designed a
session model, rejected the duplicate connection field, written the transition table, and then
*refused to build an engine* — and Phase 3 had already produced the observation that engine consumes.
So most of this phase is the join between two things that exist, and the design work is mostly about
not undoing the decisions in them.

---

## 1. What was read

| Record | Read for |
|---|---|
| `docs/MASTER-CONTEXT.md` | product principles, the evidence ladder, the 52-phase roadmap rows for Phases 4, 5, 24 |
| `docs/README.md` | authority order and the phase record map |
| `docs/phases/phase-0/` | engineering contract, `testing-governance.md` tiers, `security-governance.md` identifier rules, `specs.md` flow and ownership rules, `architecture-governance.md` boundary rules |
| `docs/phases/phase-1/` | `design.md`, `domain-model-review.md`, `repository-analysis.md`, `validation.md`, `decisions.md` |
| `docs/phases/phase-2/` | `validation.md` (its deferred list names Phase 4 items), `transport-boundaries.md` |
| `docs/phases/phase-3/` | `validation.md` stop condition, `decisions.md` ADR-P3-001/004/010/015/017/018, `risk-register.md` entries the phase left for Phase 4, `test-plan.md` gap table |
| `core/src/main/kotlin/com/omnibuds/core/` | `session/`, `device/`, `state/`, `platform/`, `persistence/` sources in full |
| `core/src/test/kotlin/com/omnibuds/core/architecture/` | `DependencyDirectionTest.kt` — all 17 checks and the layer map |
| `platform/android/src/main/kotlin/com/omnibuds/android/di/` | the composition root, to find where a session owner would attach |
| Git | branch `main`, HEAD `c246480`, working tree clean except the concurrent `tools/device-bridge/` untracked files |

## 2. Existing contracts that Phase 4 must consume, not recreate

- **`core/state/ConnectionState.kt:14-26`** — eleven states, already ordered as the master's
  Android flow (`UNKNOWN, DISCOVERED, PAIRED, CONNECTED, IDENTIFYING, CAPABILITY_DISCOVERY, READY,
  CONTROL_SESSION, DISCONNECTED, TEMPORARILY_UNAVAILABLE, ERROR`), with `UNKNOWN` explicitly documented
  as "not disconnected".
- **`ConnectionState.kt:35-109`** — `ConnectionStateTransitions`: a total forward map, `allowedNext`
  adding `ERROR` and self, `canTransition`, and `isOperational` limited to `READY`/`CONTROL_SESSION`.
  Prompt section 7's lifecycle diagram is a subset of this with different names; the table already
  answers "is this move legal" and refuses to answer it twice.
- **`core/session/DeviceState.kt`** — the authoritative per-device record: `sessionId`, `identity`,
  `connection`, `capabilities`, `battery`, `audio`, `revision`, `lastUpdatedEpochMillis`. It carries
  `attemptConnection` (`:109-129`) which refuses an illegal move as `Failure(INVALID_STATE)` rather than
  performing it, `applyIfNewer` (`:138-139`) for stale-callback safety, `invalidatedForDisconnect`
  (`:147-156`), and `initial(...)` which starts everything unknown. Its KDoc at `:30-36` states the
  copy-bypass limitation and says in terms that confining mutation is a later phase's job.
- **`core/device/DeviceSession.kt:9-15`** — identity, fingerprint, classification and creation time,
  with the connection field **deleted on purpose** because two values disagreeing about one device is
  the failure Phase 1's prompt section 24 forbids. `:27-28` defines `sessionId` as "not stable across
  reconnects", which turned out to be half of the reconnection policy prompt section 9 asks for.
- **`core/device/DeviceIdentity.kt:16-19`** — the address is deliberately absent from identity, with
  SEC-ID-001/003/004 cited. This rules out prompt section 6's `deviceKey: String` if that string is the
  address, which is the single most likely way to build the sketch and break a security rule.
- **`core/state/SessionClassification.kt`** — `TEMPORARY`/`SAVED`, with "saving is an explicit user
  action" as the enum's own comment. Prompt section 10's separation is already modelled.
- **`core/platform/` (Phase 3)** — `ConnectedDeviceSnapshot` (`ConnectedDeviceObserver.kt:75-163`) with
  `records`, `pairedDevices`, `stage`, `isUnionComplete`, `restsOnCompletedRound`,
  `pairedRoundRefusal`, `refusedTransitions`; `DeviceObservation` with three axes plus profiles and an
  arrival; `DeviceObservationKey` which redacts itself and is the only join.
- **`core/persistence/DeviceRepository.kt:8-28`** — the saved-device contract, whose KDoc names the
  exact failure Phase 4 must avoid: "an implementation that keeps a 'recently seen' table behind this
  interface has broken ADR-P0-004 no matter what this interface's method names say".

## 3. Layer arithmetic, checked rather than remembered

`DependencyDirectionTest.kt:37-52` registers twelve areas. `session` is layer 3, `persistence` is also
layer 3, `protocol` is 4, `platform` is 1, `device`/`capability`/`audio`/`config`/`diagnostics` are 2,
`common`/`state` are 0. The rule at `:256-272` rejects any import whose target layer is `>=` the source
layer unless it is the same area.

Consequences for the design, all of them helpful:

- `session` may import `platform`, `device`, `state`, `common` — the whole input contract Phase 4 needs.
- `session` may **not** import `persistence` or `protocol`, so the "temporary versus saved" separation
  prompt section 10 asks for, and the "no vendor protocol" rule prompt section 17 asks for, are enforced
  by the build rather than by review. A test that *also* checks it (`PhaseFourScopeTest`) is belt and
  braces, justified because the layer rule permits same-area imports and a future `session` file could
  reach a control seam inside `platform` without crossing any layer boundary.
- `platform` may not import `session`, so Phase 3 can never learn about sessions, which is what keeps
  the observation honest about what it is.

## 4. The naming collision, and why prompt section 12's interface name was not used

Prompt section 12 sketches `DeviceSessionRepository` with `sessions: StateFlow<List<DeviceSession>>`,
`activeSessions: StateFlow<List<DeviceSession>>` and `sessionEvents: Flow<DeviceSessionEvent>`. Three
findings made that name and shape unusable as written:

1. `Repository` already means *the storage contract* in this project: `persistence/DeviceRepository.kt`,
   `persistence/CapabilityRepository.kt`, and ADR-P3-004 which refused the word for Phase 3's projection
   precisely because it "already has a settled meaning". A phase that is forbidden to persist anything
   naming its state owner `Repository` would teach the next reader that the word means something other
   than what the persistence records say it means.
2. The architecture guard at `DependencyDirectionTest.kt:237-251` refuses *production implementations*
   of exactly four contracts (`EarbudProtocol`, `DeviceRepository`, `CapabilityRepository`,
   `TransportContract`). A new `DeviceSessionRepository` would not trip it — so the guard provides no
   protection and the decision has to be made in prose and honoured by a name (`DeviceSessionEngine`).
3. Two authoritative state flows over the same list is two owners of one truth, which is the failure
   Phase 1 section 24 exists to prevent (`docs/phases/phase-0/specs.md:147` rule 5.7 says the
   session/state engine owns device state and others read). The published value is one
   `StateFlow<DeviceSessionSnapshot>`; the other views are derived properties.

`docs/phases/phase-0/architecture-governance.md:258` (OQ-03) assigns persistence *technology* to
"Phases 4/22", which is the one governance row naming Phase 4 as a storage owner. It was read as
technology, not licence: prompt section 10 forbids the saved-device feature here, so no store arrives
and the row is untouched.

## 5. A real conflict in the earlier records, reported rather than resolved quietly

Phase 1 assigned "the session holder" to three different phases and never reconciled them:

- `phase-1/validation.md:138` and its deferred table at `:162` send "restrict `DeviceState` mutation to
  one owner" to **Phase 24** (`MASTER-CONTEXT.md:1709` — "PHASE 24 / Global Device State Engine").
- `phase-1/validation.md:142` (known issue 5) says the `DeviceSession`/`DeviceState` association is
  **Phase 4's** ("Phase 4's session engine should carry the association"), with the deferred table at
  `:164` agreeing.
- `phase-1/domain-model-review.md:143,146,148` (D-7, D-10, D-12) put the same cluster in **Phase 2**,
  which Phase 2's own prompt never asked for and Phase 2's validation does not claim.

Per the master's rule that a conflict is reported and recorded rather than silently resolved
(`MASTER-CONTEXT.md` section 58), the boundary was drawn in ADR-P4-001 rather than assumed: Phase 4
carries the association and owns session state; the mutation-confinement obligation stays Phase 24's and
is *not* claimed here; D-12's "Phase 2" is read as superseded by Phase 1's own later records, which name
Phase 4 and Phase 24 instead. The same ADR answers D-12's objection that a composite owning both "is the
singleton ADR-P1-009 forbids" — ADR-P1-009 (`phase-1/decisions.md:72`) forbids a *global mutable*
singleton, and a per-device immutable value inside the one owner's list is a different thing.

## 6. What Phase 3 left specifically for this phase

| Left by Phase 3 | Where it says so | Phase 4's answer |
|---|---|---|
| The three axes must become a `ConnectionState`; Phase 3 refused to | `ADR-P3-001` consequences; `phase-3/validation.md:226` | `targetStateOf` and the mapping table, ADR-P4-004 |
| `refresh()` has no owner | `phase-3/risk-register.md:126-132`, owner "Phase 4 / Phase 49" | declined, with the reason and the consequence recorded — ADR-P4-011, re-scored in this phase's register |
| The projection never forgets; needs an eviction rule | `phase-3/risk-register.md:110-116` | session-side termination rules give the *session* list a bound by policy; the projection itself is still Phase 3's shape and its risk stays open |
| Concurrency proven single-threaded | `phase-3/risk-register.md:118-124` | Phase 4 inherits the limit; its own contention tests are gaps, not claims |
| A bond disagreement resolved in silence | `phase-3/risk-register.md:175` | surfaces as `PAIRED` in a session, which makes it user-visible, so it is re-scored rather than closed |
| Paired census exists but nothing consumes it | `ADR-P3-017` | deliberately unconsumed by Phase 4: opening a session for a census-only device would be reading a bond as a connection |

**One asymmetry Phase 3 left was corrected here.** `ConnectedDeviceSnapshot` carried
`pairedRoundRefusal: OmniBudsError?` but the link round's refusal existed only as
`restsOnCompletedRound: false` — a consumer could ask "why are there no pairings" but not "why are there
no devices". Phase 4 needs the reason to satisfy prompt section 12's six distinctions, so
`deviceRoundRefusal` was added as its exact mirror (ADR-P4-007) rather than having the session engine
guess.

## 7. Governance rules that bound what Phase 4 may claim

- **Tiers** (`phase-0/testing-governance.md:39-41`): T1 pure logic caps at `IMPLEMENTED`; T3 —
  "Bluetooth lifecycle, connection, reconnect, capability-discovery wiring" with a faked platform
  boundary — caps at `LAB_TESTED`. Phase 4's tests are T1: the input is a projection a test built, not a
  faked radio, so the ceiling is `IMPLEMENTED` and claiming T3 would be inflating a level by
  re-describing the harness. TST-MOCK-001 (`:91`) and TST-REC-005 (`:84`) apply unchanged: nothing here
  reaches `HARDWARE_VERIFIED`, and a hardware obligation that cannot run is recorded `NOT RUN` with a gap
  id rather than skipped.
- **Identifiers** (`phase-0/security-governance.md:69-78`): SEC-ID-005's active-session row is "cleared
  with the session; no durable record", SEC-ID-006 is "scanning is not saving", SEC-ID-007 is complete
  deletion. Together they decide ADR-P4-005 (minted ids, no address-derived key) and ADR-P4-006 (a
  terminated session leaves the store, with no lineage chain kept).
- **Flows** (`phase-0/specs.md:141-149`): rule 5.1 (a named scope owner, no `GlobalScope`, no launch
  from a domain object) forces `consume` into the caller's job; rule 5.5 requires every flow to declare
  buffering and overflow, which is written out with the reason beside it; rule 5.6 (StateFlow for state,
  not commands) and 5.7 (one owner writes) settle the authoritative-value shape.
- **Boundaries** (`ADR-P3-003`): areas are earned by dependency shapes, not nouns — Phase 4 adds no area
  and no module, and `PhaseFourScopeTest.phaseFourAddedNoNewSourceArea` keeps it that way.

## 8. Sequences found at audit time, to continue rather than restart

`REQ-P3-*` to 027 · `ADR-P3-*` to 019 · `TASK-P3-*` to 021 · `TEST-P3-*` to 049 (gaps included) ·
`RISK-*` to **064** project-wide. Phase 4 therefore starts `REQ-P4-001`, `ADR-P4-001`, `TASK-P4-001`,
`TEST-P4-001` and `RISK-065`. A stray `RISK-065` citation had been corrected in Phase 3's register
without minting the entry (`phase-3/risk-register.md:5`), so the number was genuinely free — verified by
grepping the whole `docs/` tree for numbers above 064 before allocating any.

## 9. Blockers and unfinished work

None of Phase 2's or Phase 3's open items blocks Phase 4. Every blocker-shaped entry
(RISK-044/045, the deferred handset session) is an evidence limit that Phase 4 inherits rather than a
missing mechanism, and ADR-P3-014 removed hardware from the completion criteria by user directive. The
one thing that would have blocked is missing observation output, and Phase 3 shipped it: the projection
is published, typed, and refuses to lie about why it is empty.

Two unfinished Phase 2 records were noted and left alone because Phase 4 does not depend on them:
`docs/phases/phase-0/bluetooth-governance.md` still does not exist (RISK-038), and
`phase-2/transport-boundaries.md` still carries the note ADR-P3-002 promised to amend (REQ-P3-004's
residue). Both are recorded again in this phase's risk register rather than repaired here, because
inventing a rulebook to satisfy a guard is the failure Phase 2 named in the open.

## 10. Integration plan as executed

1. Two small amendments first, so the engine would need no guesses: `DeviceState.withIdentity`, and
   `ConnectedDeviceSnapshot.deviceRoundRefusal`.
2. Vocabulary and records: `SessionVocabulary.kt`, `SessionTimeline.kt`, `SessionObservationStatus.kt`,
   `TrackedDeviceSession.kt`, `DeviceSessionSnapshot.kt`, `DeviceSessionEvent.kt`.
3. The engine, with the mapping table, the grace rule and the ambiguity rule in one file, because they
   are one decision seen three times.
4. Construction-only DI, with the wiring shown as two calls in prose and named as unexecuted.
5. Three test suites: behaviour, purity, scope — the last of which is also the guard that keeps the
   engine from reaching the observation's controls.
6. Twelve ADRs before the record set, so the records could cite decisions rather than invent them.

The plan deliberately produced **no Android-facing code**: Phase 4's subject is application state, and
the only new platform-module line is a factory that injects a clock. That is why this phase adds no
instrumented tests and inherits every handset question from Phase 3 rather than raising a new one.
