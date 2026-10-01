# OMNIBUDS — PHASE 0 · SUB-AGENT ORCHESTRATION AND OWNERSHIP CONTRACT

**Rule id grammar:** `ORCH-<NNN>`. **Sources of authority:** `docs/MASTER-CONTEXT.md` (cited "master") §37–§40, §44, §49–§51, §58 and `docs/phases/phase-0/execution-prompt.md` (cited "P0 prompt") §4, §5, §20, §21, §23–§25. Master wins on conflict; conflicts surfaced in §10. **Companion rule file:** `docs/phases/phase-0/git-workflow.md` — it owns the landing/branch/PR half of every isolation rule referenced here.

---

## 1. Purpose

- **ORCH-001** — This is the **contract between the orchestrator and the specialised agents** it dispatches: what each role may write, what each must be told before it starts, how independence is guaranteed, what is forbidden, and how disagreement is settled.
- **ORCH-002** — It binds every phase, including this one: Phase 0's own governance documents were produced by parallel workstreams under these rules (P0 prompt §4). It confers no implementation authority — an agent's brief is the only thing that authorises work, and no brief may reach outside the authorised phase (master §57).
- **ORCH-003** — Where the environment does not support sub-agents, the orchestrator executes the same role catalogue serially, one role per pass, with the same write sets and the same prohibitions. Consolidating roles into one agent does not relax ownership.

## 2. Role catalogue and ownership boundaries

Base: the P0 prompt §20 ownership table (`bluetooth/`, `audio/`, `protocols/`, `core/`, `testing/`, `docs/`), extended to cover the roles master §37 names and P0 prompt §4 instantiates. Ownership is **per file, one owner at a time**; during documentation phases the territories are documentation paths, during implementation phases they are module directories.

| Role | Owns | Deliverables | Must not touch |
| --- | --- | --- | --- |
| Architecture / governance | `docs/architecture/`, phase `design.md`, dependency-direction and KMP-boundary rules | Module boundaries, dependency direction (P0 prompt §8.1–§8.4), abstraction shapes for transport/protocol/capability | Platform API specifics, vendor protocol content, test-execution machinery |
| Bluetooth | `bluetooth/` | Adapter state handling, discovery, Android permission boundaries (master §4) | Audio-path decisions, capability semantics, protocol databases |
| Protocol research | `protocols/`, `docs/protocols/` | Fingerprint rules, command/response definitions, protocol confidence per `ProtocolConfidence` (master §27) | Any device write outside authorised research (master §25, §26); UI logic |
| Audio | `audio/`, `docs/audio/` | Codec registry, `CodecState` semantics, audio-path isolation (master §3, §14–§21) | Media-path processing implementations outside authorised scope; capability truth rules |
| Core | `core/` | Domain model: `DeviceFingerprint`, session model, capability engine (master §5–§7, §10) | Platform-specific calls past the platform boundary (P0 prompt §8.3) |
| Testing / QA | `testing/`, phase `test-plan.md`, `validation.md` | Unit/protocol/integration/HIL/persistence/cross-device test architecture (master §36) | Weakening acceptance criteria to pass a build (master §46) |
| Security / privacy | `docs/security/` | Permission, identifier-retention, packet-logging, unknown-device and background-operation rules (P0 prompt §4 Agent 4) | Silent retention policies for device identifiers (master §6) |
| Documentation | `docs/`, `docs/templates/` | Phase templates: `requirements-template.md` … `risk-register-template.md` (P0 prompt §6) | Inventing requirements not in master or the phase prompt |
| Integration | Merge points only; owns no feature file | Combining agent output, resolving per `git-workflow.md` §7, verifying claimed files exist | Authoring content another role owns |
| Git / workflow | `docs/phases/phase-<N>/git-workflow.md` | Branch, commit, isolation, PR, rollback, tag rules | Executing git machinery during a governance phase (see `GIT-004`) |

- **ORCH-004** — No role owns `docs/MASTER-CONTEXT.md`. It is amended only by an explicit user instruction, through the orchestrator (master §58).
- **ORCH-005** — Creating a new top-level module or documentation directory is an architecture change: it needs an `ADR-<SCOPE>-<NNN>` before the directory is created, not after.
- **ORCH-006** — Shared interface files (a contract both a Bluetooth and an Audio workstream must modify) have exactly one owner, normally Architecture or Core; every other role reaches them through **proposal** (`GIT-022`), never through edit.
- **ORCH-007** — The catalogue is instantiated per phase: the phase `task-list.md` records `owner` for every task (P0 prompt §10), and that field is the ownership record of reference.

## 3. The mandatory brief

**ORCH-008** — No agent starts without a brief containing all six items master §37 requires. A brief missing any item is not a brief; the agent must refuse to start and report the gap.

| Required item (master §37) | Brief content that satisfies it |
| --- | --- |
| Specific objective | One sentence naming the deliverable and the `TASK-<SCOPE>-<NNN>` id it discharges |
| Relevant files | Read set (may inspect) **and** write set (may create/edit) — the write set is the isolation contract |
| Constraints | Phase scope limit, master sections that bind the work, platform/safety limits, "do not implement" list for governance phases (P0 prompt §2) |
| Expected outputs | Exact deliverable paths and the document/section shape required |
| Test requirements | `TEST-<SCOPE>-<NNN>` ids to satisfy, or the explicit statement that the phase has no executable tests and what governance validation applies (P0 prompt §11) |
| Stop conditions | Phase boundary, discovery of an ownership conflict, discovery of a contradiction with master, need for a file outside the write set |

- **ORCH-009** — Every brief additionally carries: the owning role from §2; the ID grammar; the escalation route (§5); the statement that no later phase may be entered; and the terminology contract below, verbatim.
- **ORCH-010 (terminology contract)** — Parallel agents that name states differently produce output that cannot be merged. Every brief must carry: `CapabilityState` = `UNKNOWN | UNSUPPORTED | READ_ONLY | SUPPORTED_VOLATILE | SUPPORTED_PERSISTENT | PERSISTENCE_VERIFIED` (master §10); `CodecState` = `SUPPORTED | AVAILABLE | ENABLED | NEGOTIATED | ACTIVE | CONFIGURABLE` (master §15); `VerificationLevel` / `ProtocolConfidence` = `INFERRED | IMPLEMENTED | LAB_TESTED | HARDWARE_VERIFIED | PERSISTENCE_VERIFIED` (master §27); ID grammar `REQ-<SCOPE>-<NNN>`, `TASK-<SCOPE>-<NNN>`, `TEST-<SCOPE>-<NNN>`, `ADR-<SCOPE>-<NNN>`, `RISK-<NNN>`.
- **ORCH-011** — `UNKNOWN` is never collapsed into `UNSUPPORTED`, and `not verified` is never written as `supported` (master §53); a brief that requires a claim above the available evidence must be refused and escalated.

## 4. Parallelisation rules

**ORCH-012** — Parallel work is used **only where tasks are independent** (master §39). Reproduced as the project's canonical examples:

- Good parallelization: Agent A → Bluetooth architecture research; Agent B → Audio/codec architecture research; Agent C → Protocol database design; Agent D → Testing architecture; Agent E → Security/privacy review.
- Bad parallelization: Agent A edits `DeviceManager`; Agent B simultaneously rewrites `DeviceManager`; Agent C changes the `DeviceManager` API.

- **ORCH-013** — Operational rule: agents run in parallel **only over file-disjoint deliverables**. Two agents never edit the same file concurrently, and never under a shared working tree (`GIT-020`, `GIT-021`).
- **ORCH-014** — Sequencing is not negotiated between agents: it is decided by the dependency graph in the phase `task-list.md` (master §44 "dependencies"; §49 step 6–7). An agent whose task depends on unfinished work waits; it does not stub the dependency.
- **ORCH-015** — Read-only roles (for instance the repository auditor, P0 prompt §4 Agent 1) may run alongside any number of writers; parallelism is bounded by write-set disjointness, not by agent count.
- **ORCH-016** — Before launching, the orchestrator intersects every planned write set. Any non-empty intersection is resolved before dispatch by re-scoping, serialising, or designating an owner under ORCH-006.

## 5. Sub-agent prohibitions, as enforceable rules

Restated from master §38. Each is checkable by the orchestrator at review, not aspirational.

| Prohibition (master §38) | Enforcement |
| --- | --- |
| Must not duplicate another agent's work | Write-set intersection test (ORCH-016); duplicate deliverable ⇒ ownership violation, §7 |
| Must not overwrite unrelated work | Diff scope audit: every hunk traceable to the brief's write set (`GIT-024`) |
| Must not make architecture decisions silently | ADR gate: boundary/dependency/state-enum change requires `ADR-<SCOPE>-<NNN>` (`GIT-016`) |
| Must not invent unsupported hardware capabilities | Claims limited to evidence: nothing above `INFERRED` may be stated as verified (master §2, §53, REQ-P0-002) |
| Must not assume protocol behaviour | No UUID purpose or packet-byte meaning without recorded evidence (P0 prompt §15; master §26) |
| Must not skip tests | Task without a `TEST-<SCOPE>-<NNN>` mapping or an explicit hardware-deferral line is incomplete (master §45) |
| Must not change project-wide architecture without reporting it | Report before edit; the orchestrator, not the agent, decides architecture |

- **ORCH-017** — Also prohibited for every agent: entering a later phase, creating placeholder implementations that pretend a system works (P0 prompt §2), and asserting machinery that does not exist — for example reporting commits or branches while the repository is not under version control (`GIT-001`).
- **ORCH-018 (escalation path)** — When a conflict is discovered, the flow is fixed and may not be short-circuited: **agent reports → orchestrator evaluates → architecture decision → decision documented** (master §38). The agent stops at the point of conflict, states both readings and the evidence, and does not pick one to "keep moving". A conflict that touches boundaries, dependency direction, or a state enumeration is architecture-level and must become an ADR (master §58).

## 6. Orchestrator responsibilities

- **ORCH-019** — Per P0 prompt §5, after agents finish the orchestrator must: review all outputs; detect contradictions; resolve obvious documentation conflicts; **not silently change major architectural decisions**; record unresolved architectural decisions; merge compatible documentation; enforce terminology consistency; confirm every master-context requirement is represented; perform a final repository review; and run only safe, phase-appropriate validation.
- **ORCH-020** — **Review, not trust.** The orchestrator must not simply accept sub-agent output (P0 prompt §5 closing line). Concretely: it opens each claimed deliverable at its claimed path, confirms the file exists, is non-empty, is not a placeholder, and covers the brief's stated objective — an agent's *claim* is evidence of nothing until the file is read.
- **ORCH-021** — Contradiction detection is cross-document, not per-document: the orchestrator checks that two agents have not described the same boundary, state model, dependency direction, or error category differently, and that neither differs from master. Detected contradictions route through ORCH-018.
- **ORCH-022** — The orchestrator may correct wording, structure and terminology drift directly; it may **not** change a major architectural decision directly — that goes to a documented decision, and to the user where it materially affects the project (master §58).
- **ORCH-023** — Coverage check: every requirement of master relevant to the phase is traceable to a `REQ-<SCOPE>-<NNN>` id and a deliverable; gaps are recorded as incomplete, not papered over (P0 prompt §22).
- **ORCH-024** — Integration is the orchestrator's (or the integration role's) single hand on the phase branch: one integrator merges, so no merge silently privileges one agent's interpretation (§7; `GIT-025`).

## 7. Ownership violations and overlap handling

- **ORCH-025** — Classification and handling:

| Case | Handling |
| --- | --- |
| Write sets overlap before dispatch | Do not launch both; re-scope, serialise per ORCH-014, or name a single owner |
| Shared interface file needed by two workstreams | Owner edits; the other submits a proposal through the orchestrator (P0 prompt §20; `GIT-022`) |
| An agent edited a file outside its write set | Orchestrator reads the edit and decides adopt-with-attribution, revert-to-owner, or escalate-as-architecture — never silent acceptance, and never deletion of work the decider did not author (`GIT-032`) |
| Deliverables overlap in content | Merge into one deliverable with one owner; the second task is amended in `task-list.md`, not left to duplicate |
| Two agents wrote contradictory claims about one fact | Treat as contradiction under ORCH-021; route to ORCH-018; record outcome in phase `validation.md` Issues |

- **ORCH-026 (re-assignment)** — A workstream is re-assigned only by this route: (1) record the re-assignment and its reason in `task-list.md` against the `TASK-<SCOPE>-<NNN>` id; (2) void the superseded brief explicitly rather than letting two live; (3) issue a new brief with the full six items of ORCH-008 and the updated write set; (4) re-run the intersection test of ORCH-016; (5) notify every dependent agent; (6) hand off the outgoing role's existing files by path, not by narration.
- **ORCH-027** — Re-assignment may not move work across a phase boundary or into an unauthorised phase; if the natural fix requires that, it is deferred and recorded (master §50).

## 8. Phase execution contract

- **ORCH-028** — For any prompt of the form **"Execute Phase X"**, master §49's eighteen steps are the standard order of work, and coding does not begin before step 10:

| # | Step | # | Step |
| --- | --- | --- | --- |
| 1 | Read the project context | 10 | Implement |
| 2 | Read all existing phase documentation | 11 | Test |
| 3 | Inspect the current repository | 12 | Review |
| 4 | Inspect completed tasks | 13 | Integrate |
| 5 | Inspect previous decisions | 14 | Run regression checks |
| 6 | Identify dependencies | 15 | Update documentation |
| 7 | Determine what can be parallelized | 16 | Mark tasks complete only when acceptance criteria pass |
| 8 | Create a sub-agent execution plan | 17 | Record unresolved issues |
| 9 | Assign independent tasks to specialized sub-agents where appropriate | 18 | Stop at the phase boundary |

- **ORCH-029** — Steps 6–9 are the gating gate for step 10: no implementation task is dispatched until dependencies are identified, parallelisation is decided from the task-list graph, write sets are disjoint, and each agent has a complete brief.
- **ORCH-030** — **No automatic phase progression** (master §50; P0 prompt EXECUTION MODE, §25). On completion of Phase X the orchestrator stops and reports; it does not start Phase X+1, does not "begin Bluetooth / device detection / GATT / RFCOMM / codec implementation / UI / vendor protocol work", and does not infer permission from silence. The next phase runs only on the user's explicit "Execute Phase X+1".
- **ORCH-031** — Completion report shape. The canonical fields are master §50's: `PHASE COMPLETE`, `Implemented`, `Tests`, `Known limitations`, `Deferred`, `Next phase: Phase X+1`. A phase prompt may append fields — Phase 0's §24 adds repository state, documentation created, architecture decisions, requirements/tasks counts, validation PASS/FAIL and next-phase readiness — but may not drop any master field.
- **ORCH-032** — The report is bound by ORCH-017 and ORCH-020: it states what actually exists. For Phase 0 that includes saying plainly that the repository is still not under version control and that no `git init` was run.

## 9. Worked example — hypothetical Phase 6 (Bluetooth Transport Layer)

Illustrative only: Phase 6 is not authorised by this prompt and nothing below is executed.

| Workstream | Files it owns (write set) | Deliverable |
| --- | --- | --- |
| C — shared transport interface (must run **first**) | `transport/` interface + `TransportManager` contract (master §8), `docs/phases/phase-6/specs.md` transport contracts | One frozen transport contract every transport conforms to |
| A — GATT transport | `transport/gatt/**`, `docs/bluetooth/gatt-transport.md`, `docs/phases/phase-6/test-plan.md` GATT entries | GATTTransport conforming to C |
| B — RFCOMM/SPP transport | `transport/rfcomm/**`, `docs/bluetooth/rfcomm-transport.md`, same test-plan section, different entries | RFCOMMTransport conforming to C |

- Correct: C completes and its contract is frozen; **then** A and B run in parallel — their write sets are disjoint, so neither can clobber the other (ORCH-013), and both depend only on C, not on each other (ORCH-014).
- Must be serialised: A and B both want a new method on the shared interface — for example a timeout/retry hook. Neither edits `transport/` interface; each files a proposal, the owning role applies it once, and the other rebases on it (`GIT-022`). This is exactly master §39's `DeviceManager` bad pattern in transport clothing.
- Also serialised: A and B writing the *same* rows of `test-plan.md`. Either the file gets sequenced owners or the shared document is split by the orchestrator before dispatch.
- Not permitted because it is Phase 6 work: an agent in an earlier phase pre-creating `transport/` stubs "to save time" (ORCH-017; master §50).

## 10. Conflicts surfaced between the two sources

| # | Conflict | Resolution (master wins) |
| --- | --- | --- |
| C1 | Master §37 names architecture, Bluetooth, protocol research, audio, testing, security and integration roles; P0 prompt §20's ownership table lists only six territories and omits architecture, security and integration. | Catalogue in §2 keeps the prompt's territory mapping and adds the master roles as owners of their own deliverables. |
| C2 | P0 prompt §4 puts agent outputs in `docs/phase-0/`; §6 and master §40/§56 put phase documentation in `docs/phases/phase-<N>/`. | `docs/phases/phase-0/` is canonical; the prompt's shorter path is flagged for reconciliation. |
| C3 | Verification states appear as `LAB-TESTED`, `HARDWARE-VERIFIED`, `PERSISTENCE-VERIFIED` (P0 prompt §16) but `LAB_TESTED`, `HARDWARE_VERIFIED`, `PERSISTENCE_VERIFIED` (master §27). | Underscore forms are canonical (ORCH-010); hyphenated prose is not a state name. |
| C4 | P0 prompt §17's codec list has five states including "Codec Selected" and omits `CONFIGURABLE`; master §15 defines six including `ENABLED` and `CONFIGURABLE`. | Master's six-state `CodecState` is canonical; "selected" is not a state name. |
| C5 | Task ids: master §44 `TASK-BT-001` versus P0 prompt §10 `P0-T001`. | `TASK-<SCOPE>-<NNN>` governs (§7's re-assignment record uses it). |
| C6 | P0 prompt §6 omits the `docs/product/` directory that master §56 lists. | Master's documentation tree is the requirement; the omission is noted for the Phase 0 review, and no directory is created here. |

## 11. Related documents

- `docs/phases/phase-0/git-workflow.md` — branch/commit/isolation/PR/conflict/rollback/tag rules; `GIT-020` to `GIT-026` are the landing mechanics for §4 and §5 above.
- `docs/phases/phase-0/requirements.md` (REQ-P0-014 sub-agent governance), `design.md`, `specs.md`, `task-list.md` (owner field = ownership record), `test-plan.md` (TEST-P0-008 orchestration check), `validation.md`, `decisions.md` (ADR-P0-010), `risk-register.md` (RISK-<NNN>).
- `docs/MASTER-CONTEXT.md` §37, §38, §39, §40, §44, §45, §46, §49, §50, §51, §58.
