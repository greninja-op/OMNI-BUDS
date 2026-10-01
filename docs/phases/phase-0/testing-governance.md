# OMNIBUDS — Phase 0 — Testing & Validation Governance

**Document class:** project-wide engineering rule set; Phase 0 deliverable of the Testing & QA workstream (execution prompt §4, Agent 3).
**Authority:** subordinate to `docs/MASTER-CONTEXT.md` (master §58). Instantiated per phase by that phase's `test-plan.md` and reported in its `validation.md` (master §40, §45, §46).
**Mode:** rules only. This file introduces no test code, no test source sets, no build or dependency configuration, and runs nothing. Every name below is a rule about what may be claimed and how it must be recorded (execution prompt §2).

**Related documents (paths of sibling Phase 0 deliverables):** `docs/MASTER-CONTEXT.md` · `docs/phases/phase-0/execution-prompt.md` · `docs/phases/phase-0/architecture-governance.md` (module boundaries, dependency direction) · `docs/phases/phase-0/security-governance.md` (permissions, arbitrary writes, firmware operations, research authorization) · `docs/phases/phase-0/audio-governance.md` (CodecState semantics, audio-path isolation) · `docs/phases/phase-0/sub-agent-orchestration.md` (ownership, conflict escalation) · `docs/phases/phase-0/git-workflow.md` (branching, commits, change isolation) · `docs/phases/phase-0/requirements.md` · `docs/phases/phase-0/design.md` · `docs/phases/phase-0/specs.md` · `docs/phases/phase-0/task-list.md` · `docs/phases/phase-0/test-plan.md` · `docs/phases/phase-0/validation.md` · `docs/phases/phase-0/decisions.md` · `docs/phases/phase-0/risk-register.md` · `docs/templates/requirements-template.md` · `docs/templates/test-plan-template.md` · `docs/templates/validation-template.md`

**Terminology used verbatim (normalized per master §10, §15, §27, §30):**

- CapabilityState: `UNKNOWN | UNSUPPORTED | READ_ONLY | SUPPORTED_VOLATILE | SUPPORTED_PERSISTENT | PERSISTENCE_VERIFIED`
- CodecState: `SUPPORTED | AVAILABLE | ENABLED | NEGOTIATED | ACTIVE | CONFIGURABLE`
- VerificationLevel / ProtocolConfidence: `INFERRED | IMPLEMENTED | LAB_TESTED | HARDWARE_VERIFIED | PERSISTENCE_VERIFIED`
- Error categories: `BluetoothDisabled, PermissionDenied, DeviceDisconnected, TransportUnavailable, GattFailure, RfcommFailure, ProtocolMismatch, UnsupportedFeature, WriteRejected, VerificationFailed, Timeout, FirmwareMismatch, CodecUnavailable`
- Absence of information is `UNKNOWN` — never `false`, never `0`, never `UNSUPPORTED` (master §53).

---

## 1. Purpose and binding force

**TST-GOV-001** This is the single project-wide testing and validation standard. Every later phase applies it unchanged: a phase may add stricter rules in its own `test-plan.md` and may never relax a rule here. A relaxation requires an ADR in that phase's `decisions.md` (master §47), must be surfaced to the orchestrator (master §38), and is never applied silently.
**TST-GOV-002** A feature, task, or capability is never "complete" because code compiles (master §46). Completeness is a property only of a recorded verification result, at a named VerificationLevel, in a named environment.
**TST-GOV-003** Every test exists to establish hardware truth (master §2). A test that cannot distinguish "the device did this" from "our code expected this" proves only the second, and must be classified as such.
**TST-GOV-004** Test results are the only permitted evidence for a support claim. Documentation, vendor marketing, protocol inference, and passing simulations justify `INFERRED` or `LAB_TESTED` and nothing above (execution prompt §16).
**TST-GOV-005** Verification levels never inherit. A level justified for one device fingerprint, firmware, phone, or Android/OEM build applies to that combination only; another combination starts at `INFERRED` until §5 is satisfied for it (master §55).
**TST-GOV-006** Permission and write-authorization boundaries are owned by `docs/phases/phase-0/security-governance.md`; this file references them and never restates them (master §38). This file owns only what a test must prove and what it may claim.

---

## 2. Test tiers

**TST-TIER-001** Every test is tagged with exactly one tier token `T1`–`T8`, recorded first in the test record.
**TST-TIER-002** Tier gating is absolute: `T4` is never satisfied by a `T1`/`T2`/`T3` pass; `T5` requires a prior `T4` pass on the same device identity; `T8` preserves or lowers a level and never raises it.

### 2.1 What each tier proves, its ceiling, and its environment

| Tier | Name (master §36) | What a pass proves | Highest VerificationLevel justified | Required environment |
|---|---|---|---|---|
| T1 | Unit | Parsers, encoders, decoders, state machines, capability logic, codec-state logic, persistence logic behave as specified in isolation | `IMPLEMENTED` | No radio, no Bluetooth stack, no device; pure logic only |
| T2 | Protocol / contract | Packet and command correctness, response parsing, error handling against recorded protocol contracts | `LAB_TESTED` | Recorded/replayed traffic or simulator; no real device attached |
| T3 | Integration | Bluetooth lifecycle, connection, reconnect, capability-discovery wiring across module boundaries | `LAB_TESTED` | Faked platform-Bluetooth boundary behind the transport abstraction (master §8); no real device |
| T4 | Hardware-in-the-loop | A named physical device received the command, responded correctly, and state was read back | `HARDWARE_VERIFIED` | Real earbuds/headphones identified per §5; real transport; firmware recorded |
| T5 | Persistence | The written configuration survives a real disconnect and a real reconnect | `PERSISTENCE_VERIFIED` | T4 plus physical power-cycle/case-open and reconnection (master §24, §35) |
| T6 | Cross-device | The claim holds for each distinct model tested, model by model | `HARDWARE_VERIFIED` for that model only | Two or more distinct models, each identified and reported separately |
| T7 | Cross-phone / Android-OEM | The claim holds for that phone's stack (OEM build, Android version) with that device | `HARDWARE_VERIFIED` for that phone+device pair only | Distinct Android manufacturers/builds; never generalized (master §17) |
| T8 | Regression | Previously justified levels are still justified after a change | None — preserve-only; may lower | Identical environment to the tier being re-run, or better |

### 2.2 What each tier may and may not fake

| Tier | May fake | Must never fake or claim |
|---|---|---|
| T1 | Peer modules, clocks, coroutine dispatch, storage | Any device behavior at all; hardware presence |
| T2 | Transport bytes, response payloads, timing, injected errors | That a real device ever produced those bytes |
| T3 | Adapter state, connection broadcasts, GATT/RFCOMM callbacks, discovery results | Capability support, persistence, codec activeness |
| T4 | Nothing about the device | Reconnect/persistence (that belongs to T5) |
| T5 | Nothing about power or connection lifecycle | Any level above `PERSISTENCE_VERIFIED` for an untested capability |
| T6 | Every model not actually tested | A brand/vendor-level "supported" claim |
| T7 | Every phone build not actually tested | A platform-wide "Android supports / cannot do" claim |
| T8 | — | A raised level; a re-run that silently changed environment |

**TST-TIER-003** CodecState claims obey the same ceiling: `ACTIVE` and `NEGOTIATED` require `T4` evidence on that device with that phone; `ENABLED` (execution prompt §17 "Codec Selected"), `AVAILABLE`, `SUPPORTED`, and `CONFIGURABLE` are distinct states and are never collapsed (master §15; `docs/phases/phase-0/audio-governance.md`).
**TST-TIER-004** Each tier pass is recorded with its environment identity (fingerprint elements, firmware, phone model, Android/OEM build). A result without recorded environment identity is `NOT VERIFIED`, whatever its outcome.

---

## 3. Mandatory test record

**TST-REC-001** Every test in every phase `test-plan.md` carries all eight fields of master §45. None may be blank, `N/A`, or "TBD"; unknown is written `UNKNOWN`, and that is itself a finding.

| Field (master §45) | Rule |
|---|---|
| TEST-ID | `TEST-<SCOPE>-<NNN>`; immutable once issued |
| Purpose | The exact claim the test decides; states its tier `T1`–`T8` |
| Setup | Environment identity, including which device/phone or which fixture |
| Input | Command/characteristic/fixture and its preconditions; for writes, the §5 gate result |
| Expected result | Observable outcome expressed in CapabilityState / CodecState / error-category terms |
| Failure condition | The observable that fails the test, named with one of the 13 error categories |
| Automation possibility | One of `AUTOMATED`, `SEMI-AUTOMATED`, `MANUAL`, `NOT-AUTOMATABLE`; a manual test states who performs it and what they observe |
| Hardware requirement | One of `NONE`, `DEVICE-ANY`, `DEVICE-SPECIFIC-MODEL`, `DEVICE-SPECIFIC-FIRMWARE`, `PHONE-SPECIFIC-OEM`, `PHONE-AND-DEVICE` |

**TST-REC-002** ID grammar is `TEST-<SCOPE>-<NNN>` (likewise `REQ-<SCOPE>-<NNN>`, `TASK-<SCOPE>-<NNN>`, `ADR-<SCOPE>-<NNN>`, `RISK-<NNN>`). `SCOPE` is the domain token (`P0`, `BT`, `CAP`, `PERSIST`, `AUDIO`, …); `NNN` is three digits, allocated monotonically, never reused, never renumbered. A retired test keeps its ID and records the retirement reason. The execution prompt's `P0-TEST-001` / `P0-T001` forms are normalized to `TEST-P0-001` / `TASK-P0-001` (§12).
**TST-REC-003** Traceability is bidirectional and mandatory: every test links at least one `REQ` ID, and every `REQ` names its verification method, its tier, and the `TEST` IDs executing it (master §41). A `REQ` without a verification method is incomplete, never "implicitly understood" (master §43).
**TST-REC-004** A test referencing no `REQ` is deleted or gains a `REQ`. Orphan tests are evidence for nothing.
**TST-REC-005** Where a test's hardware requirement cannot be met, the run is recorded `NOT RUN`, the affected capability stays `UNKNOWN`, and the gap appears in `validation.md` under hardware limitations. Skipping is forbidden (master §38); a skipped test is a phase failure (§11).
**TST-REC-006** Each phase keeps a `REQ` ↔ `TEST` ↔ tier ↔ VerificationLevel matrix in its `test-plan.md`, reproduced in `validation.md`. Empty cells are reported as empty, never omitted.

---

## 4. Mocking and simulation rules

**TST-MOCK-001** A mock, fake, stub, simulator, replayed capture, or emulated adapter result may justify at most `LAB_TESTED` (execution prompt §16). It never justifies `HARDWARE_VERIFIED` or `PERSISTENCE_VERIFIED`, and never satisfies `T4` or `T5`.
**TST-MOCK-002** Every fake device or fake participant is unmistakably labelled as fake, in both the test name and the report line, using `FAKE` or `SIMULATED` plus the fixture ID — e.g. "FAKE-ANC-HEADSET (fixture FX-ANC-01) — simulated, not hardware verified". No reader may mistake such a run for real-device evidence.
**TST-MOCK-003** A mocked result never enters the capability store as verified state. Production-facing state above `UNKNOWN` requires §5 evidence; a simulation sets state only inside its own test scope (master §53).
**TST-MOCK-004** Absence of a capability in a simulator is not evidence of `UNSUPPORTED` on real hardware; there it is `UNKNOWN` (master §53, §11).
**TST-MOCK-005** Fixture provenance is recorded as `CAPTURED FROM REAL DEVICE (identity …)`, `VENDOR-DOCUMENTED`, or `HAND-AUTHORED`. A `HAND-AUTHORED` fixture is `INFERRED` evidence: the run stays `LAB_TESTED` and carries the note "based on assumed protocol".
**TST-MOCK-006** Any report containing mocked runs declares `MOCKED ENVIRONMENT` in its header and repeats the TST-MOCK-001 ceiling per test. Mocked and hardware runs are never merged into one "passed" count; they are reported on separate lines.
**TST-MOCK-007** Simulators are never presented as evidence that a subsystem works in the product (execution prompt §2: no fake placeholders that pretend these systems already work).

---

## 5. Hardware verification standard

**TST-HW-001** `HARDWARE_VERIFIED` is granted only when all applicable conditions of master §54 pass, in order, within one recorded run. This checklist is the only accepted form, and each blocking criterion is enforced.

| # | Condition (master §54) | Blocking criterion | Evidence recorded |
|---|---|---|---|
| 1 | Device identity is known | No identity ⇒ no write, no claim; identity is fingerprint fields (master §6, §7), display name alone is insufficient | Fingerprint fields, `UNKNOWN` where unavailable |
| 2 | Correct protocol is known | Protocol and transport chosen from the fingerprint, not assumed (master §8, §9) | Protocol, transport, protocol version |
| 3 | Command is understood | The command exists in the protocol definition with documented meaning; bytes of unknown meaning are never sent (master §52; prompt §15) | Command definition, expected response shape |
| 4 | Command is sent to real hardware | Simulation cannot satisfy this item (TST-MOCK-001) | Device identity, run ID, timestamp |
| 5 | Device responds correctly | Response matches the documented shape; silence is `Timeout`, not success | Raw response, parsed result |
| 6 | State is read back | Read-back is a separate read operation, not the write echo | Read-back value |
| 7 | Result matches expected behavior | Mismatch ⇒ `VerificationFailed`; the previous verified level is retained | Expected vs observed |
| 8 | Reconnect tested where persistence matters | Mandatory for `PERSISTENCE_VERIFIED`; otherwise marked `N/A — volatility-independent` with rationale | §6 run record |

**TST-HW-002** Conditions 1–3 are pre-write gates: if any fails, no write occurs and the capability stays `UNKNOWN` (master §25).
**TST-HW-003** The per-device capability report of master §55 is the only acceptable way to claim support, in any audience (docs, `validation.md`, release notes, UI copy, completion report):

```text
<Manufacturer> <Model> [<firmware>] [<phone / Android build>]

Verified:       <capability> — <VerificationLevel>
Not verified:   <capability> — reason (NOT RUN / reconnect not performed / INFERRED only)
Unsupported:    <capability> — evidence (error category / explicit device rejection)
```

**TST-HW-004** Brand-level statements are forbidden: never "OmniBuds supports Sony" (master §55). Claims are per model, per firmware, per phone combination.
**TST-HW-005** The three columns are exhaustive: every in-scope capability appears in exactly one. Untested behavior goes to `Not verified` — never omitted, never moved to `Unsupported`.
**TST-HW-006** `Unsupported` requires positive evidence — an explicit rejection such as `UnsupportedFeature` or `ProtocolMismatch`, or documented absence — never silence (master §53).
**TST-HW-007** Every `Verified` entry carries its level: `Verified (LAB_TESTED)` is valid, bare `Verified` is not.

---

## 6. Persistence verification testing

**TST-PER-001** The only accepted persistence procedure is the full eight-step sequence of master §24, recorded per attempt:

```text
1 READ        baseline state of the capability
2 WRITE       documented command, §5 gates passed
3 READ BACK   a fresh read, not the write echo
4 VERIFY      read-back == written value
5 DISCONNECT  real device disconnect (power off / case open / out of range)
6 RECONNECT   real reconnection and re-identification
7 READ AGAIN  read once the new session's discovery has completed
8 VERIFY      post-reconnect value == written value
```

**TST-PER-002** Classification by outcome:

| Outcome | CapabilityState | Note |
|---|---|---|
| Step 3–4 fail | level unchanged; `WriteRejected` / `VerificationFailed` | Write not effective |
| 3–4 pass, 5–7 not attempted | `SUPPORTED_PERSISTENT` | Persistence believed, **not verified**; never reported as verified |
| Step 8 pass | `PERSISTENCE_VERIFIED` | Requires `T5` |
| 4 pass, 8 fail (value reverted after reconnect) | `SUPPORTED_VOLATILE` | Correct classification, not a test defect |
| 8 not reached because reconnect never happened | persistence `UNKNOWN` | TST-PER-003 |
| Readable only | `READ_ONLY` | — |
| Never discovered | `UNKNOWN` | Not `UNSUPPORTED` |

**TST-PER-003** An inconclusive run is not a refutation. When disconnect or reconnect could not be performed — blocked by `DeviceDisconnected`, `TransportUnavailable`, `BluetoothDisabled`, `PermissionDenied`, or `Timeout` — record `NOT VERIFIED (reconnect not performed)` with the error category, leave persistence `UNKNOWN`, and retain the last verified level. Neither a positive nor a negative persistence claim is made (master §53).
**TST-PER-004** `DISCONNECT` and `RECONNECT` must be physical. An application-level session reset, cache clear, or re-scan is not a disconnect and yields at most `LAB_TESTED`.
**TST-PER-005** After reconnect, discovery and re-identification must complete before step 7; a read against a stale pre-disconnect handle is not evidence.
**TST-PER-006** Runs are recorded individually. One success justifies `PERSISTENCE_VERIFIED` for that identity only (TST-GOV-005); a later inconclusive run lowers nothing but raises nothing.
**TST-PER-007** Interaction effects (a mode another capability overrides) are verified per capability with the same sequence, and conflicts are recorded rather than assumed (master §29).

---

## 7. Safety constraints in testing

**TST-SAFE-001** Unknown devices are read-only in tests as in product behavior (master §25; REQ-P0-008). Discovery reads are permitted; writes are not, until §5 conditions 1–3 pass.
**TST-SAFE-002** No blind writes: a test writes only a characteristic/command whose purpose is established by evidence, never "to see what happens" (master §25; prompt §15).
**TST-SAFE-003** No fuzzing in any tier, on any device: no random byte sequences, no command-space probing, no undocumented commands (master §25, §26).
**TST-SAFE-004** Protocol research performed as testing follows the ordered workflow — identify, discover, read, observe, understand, validate, write, read back, disconnect, reconnect, verify (master §26; prompt §15) — and only on devices the team is authorized to analyze; authorization is owned by `docs/phases/phase-0/security-governance.md`. **Open ordering question:** prompt §15 places Identify before Discover while master §26 places Discover before Identify; `ADR-P0-018` proposes resolving this as *enumerate → identify* and is awaiting the user's confirmation. Until accepted, the invariant both readings agree on governs: no write may occur before identification is complete and the command is documented.
**TST-SAFE-005** Read/write retry asymmetry is a test-design rule as much as a product rule (master §30; prompt §9.5). Reads may retry under a bounded policy; a side-effecting command that timed out is never re-sent blindly — the harness re-reads state and decides from the observed value.
**TST-SAFE-006** A test whose own retry logic re-sends a write is invalid: its result is discarded and recorded as a governance finding, not as device behavior.
**TST-SAFE-007** Tests must not leave a device in a state the user cannot recover from. Firmware-affecting or otherwise irreversible operations are out of scope for every tier unless separately authorized in an ADR and in `security-governance.md`.
**TST-SAFE-008** Test logs and packet captures follow the identifier and logging rules of `security-governance.md`; a report never fabricates metadata the run did not obtain (master §6).

---

## 8. Regression rules

**TST-REG-001** After a change, re-run at minimum the tier that justified each affected capability's current level, plus `T1` for the changed logic, plus `T3` for anything touching lifecycle, connection, reconnect, or discovery (master §36).

| Change touches | Mandatory re-run |
|---|---|
| Parser / encoder / decoder | `T1` + `T2` for that protocol |
| Capability or codec state logic | `T1` + affected `T3` |
| Transport or protocol boundary | `T3`, then `T4` on at least one real device per affected fingerprint |
| Persistence logic | `T1` + `T5` on at least one real device |
| Bluetooth lifecycle | `T3` covering connect, reconnect, adapter off/on; plus `T5` where settings are affected |
| Vendor extension | `T4` on that vendor's device; `T6` if the shared interface changed |
| UI-facing state mapping | `T1` for the mapping + review that nothing above `UNKNOWN` is asserted without evidence (master §11) |

**TST-REG-002** The re-run's environment identity must match the original run (TST-TIER-004). If it cannot, the result preserves nothing and is reported `NOT VERIFIED (environment changed)`.
**TST-REG-003** `T8` never raises a level: a regression run cannot turn `LAB_TESTED` into `HARDWARE_VERIFIED`.
**TST-REG-004** A regressed capability is recorded in `validation.md` with previous level, observed level, failing `TEST-ID`, error category, environment identity, date, and disposition (open, or deferred to a follow-up `TASK-<SCOPE>-<NNN>` in `task-list.md`). It moves from `Verified` to `Not verified` in the TST-HW-003 report form until re-verified.
**TST-REG-005** Removing a capability, or downgrading one previously `HARDWARE_VERIFIED` or `PERSISTENCE_VERIFIED`, requires an ADR (`ADR-<SCOPE>-<NNN>` in that phase's `decisions.md`, master §47) naming the superseded run, the reason, and the new level. Silent removal, silent downgrade, or deleting the failing test fails the phase (§11, row 10).
**TST-REG-006** Disabled or deleted tests are never a regression result; removing a test carries the same ADR discipline as TST-REG-005.

---

## 9. Per-phase acceptance and definition of done

**TST-ACC-001** A task may be marked complete only when all of this holds (master §49 steps 11–17): its acceptance criteria were written before execution and every one is checked; every attached `TEST-ID` has an executed result — pass, fail, or `NOT RUN` with reason; no attached test was skipped or disabled; the claimed VerificationLevel equals the highest level justified by the highest tier actually passed; environment identity is recorded for every `T4`/`T5`/`T6`/`T7` result; unresolved issues are recorded (master §49 step 17); and compiling, running, or "looks right" is nowhere cited as completion evidence (master §46).
**TST-ACC-002** A phase may be declared complete only when every `REQ` in it either has its verification method executed with a recorded result, or is explicitly listed in `validation.md` under *Not implemented* / *Deferred work* (master §46; execution prompt §22).
**TST-ACC-003** Every `validation.md` records, as separate sections, the master §46 set — `Implemented`, `Not implemented`, `Tests passed`, `Tests failed`, `Known limitations`, `Platform limitations`, `Hardware limitations`, `Deferred work` — plus the Phase 0 fields of execution prompt §12 (`Phase`, `Status`, `Requirements completed`, `Requirements incomplete`, `Documentation created`, `Architecture decisions`, `Validation performed`, `Issues`, `Ready for Phase 1`). Master's list is authoritative; the prompt's list is additive (§12).
**TST-ACC-004** Untested hardware behavior is listed as **not verified**, by name, in `validation.md`, in the TST-HW-003 three-column form per device. A phase that tested nothing on real hardware states that as its hardware limitation instead of leaving the section empty.
**TST-ACC-005** `Tests passed` and `Tests failed` are reported per tier and per mocked/real split (TST-MOCK-006). A single undifferentiated pass percentage is rejected as an acceptance artifact.
**TST-ACC-006** Phase 0 tests are documentation and governance checks (`TEST-P0-001` …, execution prompt §11). A governance check verifies that a rule exists and agrees with master; it verifies no device behavior and therefore justifies no `HARDWARE_VERIFIED` claim anywhere.
**TST-ACC-007** Phase acceptance additionally requires the QA-relevant items of the orchestrator checklist — "Hardware verification documented", "Persistence verification documented", "Codec semantics documented", "Testing workflow documented", "Definition of done documented" (prompt §21, §22) — to be evidenced by this file and by that phase's `test-plan.md` instantiating it.

---

## 10. Phase boundary discipline

**TST-PHB-001** No QA output triggers the next phase. No agent and no green suite may start Phase X+1 (master §50; prompt §25; REQ-P0-015 / `ADR-P0-009`).
**TST-PHB-002** At the boundary, testing contributes exactly these blocks to the completion report shape of master §50:

```text
PHASE COMPLETE

Implemented:        <what exists, at what VerificationLevel>
Tests:              <per tier: passed / failed / not run, with TEST-IDs and environment>
Known limitations:  <what is UNKNOWN, and why>
Deferred:           <deferred tests and requirements, with owning IDs>
Next phase:         Phase X+1        # named only; not started
```

**TST-PHB-003** The report carries no support claim that is not backed by a TST-HW-003 per-device table. "Supports", "works", "compatible", and "production-ready" are banned unless qualified by device identity and VerificationLevel.
**TST-PHB-004** `Ready for Phase 1: YES / NO` (prompt §12, §24) is answered NO whenever an open §11 failure condition exists.

---

## 11. Failure conditions

**TST-FAIL-001** A phase fails from a QA standpoint when any of the following is true. Each is recorded in `validation.md` under `Issues` and blocks the completion report (execution prompt §23).

| # | Failure condition | Detection |
|---|---|---|
| 1 | Vague or absent acceptance criteria | Criterion cannot be evaluated pass/fail from a record (master §41) |
| 2 | Missing verification method on a requirement | A `REQ` row has no method, tier, or `TEST-ID` (TST-REC-003) |
| 3 | Conflated VerificationLevels | A claim above `LAB_TESTED` rests on mock, simulator, or replayed evidence, or levels are aggregated across identities (TST-MOCK-001, TST-GOV-005; prompt §16) |
| 4 | Hardware claims without hardware | Any `HARDWARE_VERIFIED`/`PERSISTENCE_VERIFIED` entry with no `T4`/`T5` run and no environment identity |
| 5 | Skipped, deleted, or disabled test presented as passing | Test list vs execution record mismatch (master §38; TST-REG-006) |
| 6 | `UNKNOWN` collapsed into `false` / `0` / `UNSUPPORTED` | State audit of records and reports (master §53) |
| 7 | Fake device not labelled as fake | Test-name and report audit against TST-MOCK-002 |
| 8 | Persistence claimed without real reconnect | TST-PER-002, TST-PER-003, TST-PER-004 |
| 9 | Blind write, or re-sent side-effecting command, in a test | TST-SAFE-002, TST-SAFE-005, TST-SAFE-006 |
| 10 | Silent removal or downgrade of a verified capability without ADR | TST-REG-005 |
| 11 | Implementation started during a governance phase, or a later phase auto-started | Repository diff against phase scope (prompt §23; master §50) |

**TST-FAIL-002** A failure above is never repaired by softening documentation: the claim is lowered to the level the evidence supports, or the test is run.
**TST-FAIL-003** These exposures are tracked as `RISK-<NNN>` rows in `docs/phases/phase-0/risk-register.md`, notably `RISK-009` persistence assumptions, `RISK-010` protocol write safety, `RISK-011` cross-device differences, `RISK-012` cross-phone differences. Recurring twice requires a mitigation entry, not a rule exception.

---

## 12. Conflicts surfaced between the two sources

Per master §58 and execution prompt §5, conflicts are surfaced, not silently resolved; master wins.

| # | Conflict | Sources | Resolution applied |
|---|---|---|---|
| 1 | Output path | prompt §4 says `docs/phase-0/testing-governance.md`; prompt §6 and master §40/§56 say `docs/phases/phase-0/` | Written under `docs/phases/phase-0/`; all sibling links use that root. Orchestrator should normalize prompt §4's paths |
| 2 | Persistence sequence | master §24: READ, WRITE, READ BACK, VERIFY, DISCONNECT, RECONNECT, READ AGAIN, VERIFY. prompt §7 REQ-P0-007 omits the baseline READ and the read-back VERIFY | Master's eight steps kept mandatory (TST-PER-001); REQ-P0-007 read as a subset, not a relaxation |
| 3 | CodecState vocabulary | master §15 has `ENABLED` and `CONFIGURABLE`; prompt §17 lists "Codec Selected" and omits `CONFIGURABLE` | "Codec Selected" mapped to `ENABLED`; `Selected` is never used as a state name; `CONFIGURABLE` retained |
| 4 | ID grammar | prompt §10/§11 use `P0-T001` / `P0-TEST-001`; master §44/§45 and the project grammar require `TASK-<SCOPE>-<NNN>` / `TEST-<SCOPE>-<NNN>` | Normalized to `TASK-P0-001` / `TEST-P0-001` (TST-REC-002) |
| 5 | Error categories | prompt §9.3 lists 10 examples; master §30 lists 13 | Master's 13 are canonical; the prompt's list is non-exhaustive |
| 6 | Level spelling | prompt §16 hyphenates `LAB-TESTED` / `HARDWARE-VERIFIED` / `PERSISTENCE-VERIFIED`; master §27 uses underscores | Underscore forms used throughout; identical meaning, no conflation |
| 7 | validation.md fields | master §46 (8 sections) vs prompt §12 (Phase 0 fields incl. `Ready for Phase 1`) | Both recorded; master authoritative, prompt additive (TST-ACC-003) |
| 8 | Tier tag added to the §45 record | This file, TST-TIER-001 / TST-REC-001 | Declared a clarifying addition that binds tiers to VerificationLevels; it relaxes no §45 field and adds no product requirement |
