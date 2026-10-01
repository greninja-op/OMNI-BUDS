# Phase 0 — Requirements

Phase: 0 — Engineering Contract, Repository Rules & Development Governance.
Sources: `docs/MASTER-CONTEXT.md` (master), `docs/phases/phase-0/execution-prompt.md` (prompt). Where the two disagree, master wins; the resolution is recorded in `decisions.md`.
Priority legend: **MUST** (phase fails without it) · **SHOULD** (expected, deviation needs an ADR) · **MAY**.
Status legend: **done** · **partial** · **not-started**. Phase 0 produces documents, so a requirement is done when its verification method passes.

Every requirement below is a governance requirement. No requirement in this file authorises implementing Bluetooth, transport, protocol, audio, capability, UI, database or background behavior — those belong to Phases 1 and later.

---

## Governance scope and repository rules

### REQ-P0-001 — Master architecture is documented and authoritative
**Description.** The project SHALL maintain a single documented master architecture that later phases read before acting, and SHALL treat it as the source of truth for terminology and principles.
**Rationale.** Phase prompts arrive separately. Without one authoritative document, each session reinvents the boundaries and the "never simulate hardware" rule erodes quietly.
**Dependencies.** None.
**Acceptance criteria.** Master architecture exists in versioned storage; module boundaries documented in `architecture-governance.md`; dependency direction documented; future KMP compatibility documented; conflicts with a phase prompt are surfaced, not silently overwritten (master §58).
**Verification.** TEST-P0-001, TEST-P0-009.
**Priority.** MUST · **Status.** done

### REQ-P0-002 — Hardware truth
**Description.** OmniBuds SHALL never represent a capability that the physical device does not implement as supported, and SHALL never present a software substitute as a hardware feature.
**Rationale.** This is the product's founding promise (master §2). A single simulated ANC toggle makes every later support claim untrustworthy.
**Dependencies.** REQ-P0-006 (capability model), REQ-P0-008 (unknown-device safety).
**Acceptance criteria.** Capability truth documented; `UNKNOWN` is distinct from `UNSUPPORTED` in the documented model; unsupported controls are not exposed; the ban on fake ANC/transparency/LDAC/spatial/EQ/vendor features is written as a rule, not a slogan; a software feature may exist only when explicitly labelled as software.
**Verification.** TEST-P0-001, TEST-P0-002.
**Priority.** MUST · **Status.** done

### REQ-P0-003 — Audio path isolation
**Description.** OmniBuds SHALL normally remain outside the media audio path and SHALL treat the Android/Bluetooth stack as the owner of media transport.
**Rationale.** Capture → process → re-encode → re-transmit degrades quality, latency, codec behavior, battery and stability (master §3).
**Dependencies.** REQ-P0-009 (codec truth), REQ-P0-011 (audio quality state).
**Acceptance criteria.** `audio-governance.md` documents the isolation with the master's path diagrams; states the prohibition on decoding/re-encoding media audio; states the conditions under which in-path behavior could ever be proposed (ADR plus explicit software labelling).
**Verification.** TEST-P0-006.
**Priority.** MUST · **Status.** done

### REQ-P0-004 — Device session separation
**Description.** Temporarily connected devices SHALL be modelled distinctly from explicitly saved devices, and the application SHALL NOT permanently retain every device it momentarily observed.
**Rationale.** Users connect shared and borrowed devices; blanket retention is both a privacy problem and a clutter problem (master §5, §6).
**Dependencies.** REQ-P0-002.
**Acceptance criteria.** Active-session concept documented with its connect/disconnect transitions; saved-device concept documented with the explicit user action that creates one; disconnect behavior documented; retention differences recorded in `security-governance.md`.
**Verification.** TEST-P0-001, TEST-P0-010.
**Priority.** MUST · **Status.** done

### REQ-P0-005 — Transport abstraction
**Description.** Bluetooth control SHALL NOT be tied exclusively to GATT; the architecture SHALL admit classic, RFCOMM/SPP, AVRCP/HFP, LE Audio and vendor-specific control transports.
**Rationale.** Real vendor stacks mix BLE for bookkeeping with RFCOMM for control (master §8). A GATT-only core would force rewrites per vendor.
**Dependencies.** REQ-P0-001.
**Acceptance criteria.** Architecture documents support for GATT, RFCOMM, classic Bluetooth, LE Audio and future transports; a transport-availability failure is specified as a structured error rather than silence; no phase document assumes BLE universally.
**Verification.** TEST-P0-003 (transport coverage check).
**Priority.** MUST · **Status.** done

### REQ-P0-006 — Capability model
**Description.** Capabilities SHALL support richer state than a boolean, using at minimum `UNKNOWN`, `UNSUPPORTED`, `READ_ONLY`, `SUPPORTED_VOLATILE`, `SUPPORTED_PERSISTENT`, `PERSISTENCE_VERIFIED`, and SHALL carry readable/writable/transport/protocol/requires-connection/persistence/verification metadata.
**Rationale.** "supported = true" cannot express write-only, volatile, unverified, or unknown — the exact cases where apps lie to users (master §10).
**Dependencies.** REQ-P0-002.
**Acceptance criteria.** All six states defined with meanings and transitions; metadata enumerated; UI rule documented (backend capability state decides what UI may show, master §11); no document maps `UNKNOWN` to `UNSUPPORTED` or to a supported state.
**Verification.** TEST-P0-002.
**Priority.** MUST · **Status.** done

### REQ-P0-007 — Persistent setting verification
**Description.** A setting SHALL NOT be classified as device-persistent merely because a write succeeded. Classification as `PERSISTENCE_VERIFIED` requires the full ladder: READ → WRITE → READ BACK → VERIFY → DISCONNECT → RECONNECT → READ AGAIN → VERIFY.
**Rationale.** Devices accept writes into RAM and forget them on reconnect; a gesture mapping that silently reverts is a user-visible defect (master §24).
**Dependencies.** REQ-P0-006.
**Acceptance criteria.** The complete ladder is documented in `protocol-governance.md`; the consequence of a failed ladder is stated (`SUPPORTED_VOLATILE` or lower); the ladder is stated as a required future test, not implemented now.
**Verification.** TEST-P0-004.
**Priority.** MUST · **Status.** done

### REQ-P0-008 — Unknown device safety
**Description.** Unknown devices SHALL begin in read-only mode; the system SHALL NOT write to an undocumented characteristic, fuzz a device, or send undocumented commands automatically.
**Rationale.** OmniBuds writes to hardware the user owns; an improper write can misconfigure or brick a device (master §25).
**Dependencies.** REQ-P0-002, REQ-P0-006.
**Acceptance criteria.** Read-only default documented; the safe discovery set enumerated (metadata, services, characteristics, descriptors, manufacturer data, standard battery); the prohibition list recorded; research workflow documented as starting with read-only discovery and ending in verification before any write becomes permissible.
**Verification.** TEST-P0-005.
**Priority.** MUST · **Status.** done

### REQ-P0-009 — Codec truth
**Description.** The system SHALL distinguish `SUPPORTED`, `AVAILABLE`, `ENABLED`, `NEGOTIATED`, `ACTIVE`, `CONFIGURABLE` for every codec, and SHALL NOT display a codec as active unless the active state is actually observed.
**Rationale.** The common failure is "LDAC" in the UI while AAC is running (master §15, §18).
**Dependencies.** REQ-P0-011.
**Acceptance criteria.** Six states defined with the transition ladder; worked examples included (LDAC supported + AAC active must not render "LDAC ACTIVE"); "Selected" is not used as a state name; no document infers availability from theoretical device capability.
**Verification.** TEST-P0-003.
**Priority.** MUST · **Status.** done

### REQ-P0-010 — Audio codec coverage
**Description.** The architecture SHALL account for SBC, AAC, aptX, aptX HD, aptX Adaptive, aptX Lossless where actually exposed, LDAC, LC3 and future codecs, treating aptX variants as separate capabilities.
**Rationale.** Collapsing the aptX family into one flag destroys the distinction users buy hardware on (master §19).
**Dependencies.** REQ-P0-009, REQ-P0-005.
**Acceptance criteria.** Codec registry lists the required entries plus an extension path; aptX variants are separate entries; LC3 is modelled under LE Audio rather than as an A2DP codec (master §20).
**Verification.** TEST-P0-003.
**Priority.** MUST · **Status.** done

### REQ-P0-011 — Audio quality state
**Description.** The architecture SHALL allow representation of transport, codec, sample rate, bit depth, bitrate, channel mode, quality mode and active state, with unknown values remaining unknown.
**Rationale.** Users diagnose real audio problems with these fields; fabricated numbers misdiagnose them (master §16).
**Dependencies.** REQ-P0-009.
**Acceptance criteria.** Field set documented; unknown rendering defined; explicit prohibition on invented figures; read-only-vs-writable control expectations documented per OS restriction (master §21).
**Verification.** TEST-P0-006.
**Priority.** MUST · **Status.** done

### REQ-P0-012 — Vendor extensions preserved
**Description.** Vendor-specific functionality SHALL be preserved through extension points instead of being discarded in favor of a lowest-common-denominator API.
**Rationale.** "Universal" that reduces every headset to ANC/EQ/battery is not the product (master §13).
**Dependencies.** REQ-P0-006, REQ-P0-005.
**Acceptance criteria.** Common interface for common functionality and a vendor extension mechanism are documented; an unmodelled vendor feature must be recorded as known-to-exist rather than dropped; no hard-coded manufacturer branching in shared layers.
**Verification.** TEST-P0-007.
**Priority.** MUST · **Status.** done

## Process and documentation governance

### REQ-P0-013 — Documentation per phase
**Description.** Every implementation phase SHALL contain `requirements.md`, `design.md`, `specs.md`, `task-list.md`, `test-plan.md`, `validation.md` and `decisions.md`, and MAY contain `architecture.md`, `protocol.md`, `research.md`, `risk-register.md`.
**Rationale.** Uniform artifacts are what let a later session or a sub-agent resume work without re-deriving context (master §40).
**Dependencies.** REQ-P0-016.
**Acceptance criteria.** Phase 0 itself satisfies the rule; templates exist for each document; the phase directory location `docs/phases/phase-<N>/` is fixed by an ADR.
**Verification.** TEST-P0-011.
**Priority.** MUST · **Status.** done

### REQ-P0-014 — Sub-agent governance
**Description.** Sub-agents SHALL have isolated responsibilities, explicit file ownership, a mandatory brief, and a documented escalation path for conflicts.
**Rationale.** Parallel agents editing the same abstraction produce silently lost work (master §38, §39).
**Dependencies.** REQ-P0-001.
**Acceptance criteria.** `sub-agent-orchestration.md` defines roles, ownership boundaries, brief contents, parallelisation rules, prohibitions and the proposal-then-integrate path; no two concurrent workstreams are assigned the same file.
**Verification.** TEST-P0-008.
**Priority.** MUST · **Status.** done

### REQ-P0-015 — Phase boundaries
**Description.** The editor SHALL NOT automatically advance from one phase to the next; on completing a phase it SHALL stop and emit the completion report and wait for an explicit execution prompt.
**Rationale.** The user controls scope and cost; runaway phases also bypass the ADR gate (master §50).
**Dependencies.** REQ-P0-013.
**Acceptance criteria.** The stop rule and the report shape are documented; Phase 0 itself stops at the boundary and reports rather than starting Phase 1.
**Verification.** TEST-P0-008.
**Priority.** MUST · **Status.** done

### REQ-P0-016 — Reusable phase document templates
**Description.** The project SHALL maintain phase-agnostic templates for every mandatory phase document, each encoding the required record fields.
**Rationale.** Templates are what stop "detailed requirements" from decaying into bullet vagueness in later phases.
**Dependencies.** REQ-P0-013.
**Acceptance criteria.** `docs/templates/` contains the eight templates named in the Phase 0 prompt §4/§6; each carries the mandatory field list and ID grammar; examples in templates are drawn from master, not invented.
**Verification.** TEST-P0-011.
**Priority.** MUST · **Status.** done

### REQ-P0-017 — Project-wide specifications
**Description.** The project SHALL fix naming, state-representation, error, coroutine and retry conventions in a written specification before implementation begins.
**Rationale.** Consistent conventions are what keep 52 phases of agent-authored code reviewable (prompt §9).
**Dependencies.** REQ-P0-001.
**Acceptance criteria.** `specs.md` defines all five convention families, including the read-versus-write retry asymmetry; `null` is not used as a substitute for every state; error categories are a fixed superset.
**Verification.** TEST-P0-012.
**Priority.** MUST · **Status.** done

### REQ-P0-018 — Git workflow defined without initialising the repository
**Description.** The project SHALL document branch naming, commit conventions, review gates, change isolation, conflict resolution, rollback policy and documentation-update requirements; Phase 0 SHALL NOT create a repository.
**Rationale.** Governance must exist before Phase 1 writes code, but initialising version control changes workspace state and is Phase 1's decision to make.
**Dependencies.** REQ-P0-014.
**Acceptance criteria.** `git-workflow.md` exists and states the not-yet-a-repository condition honestly; prohibited destructive operations are listed; the deferral is recorded as an open item in `validation.md`.
**Verification.** TEST-P0-013.
**Priority.** MUST · **Status.** done

### REQ-P0-019 — Safety rules for protocol research, privacy and verification
**Description.** The project SHALL document permission handling rules, identifier retention limits, logging redaction rules, unknown-device read-only policy, arbitrary-write prohibition, firmware-update exclusion, research authorization constraints and background-operation compliance.
**Rationale.** Bluetooth identifiers and packet captures are personal data; writes act on hardware the user depends on.
**Dependencies.** REQ-P0-004, REQ-P0-008.
**Acceptance criteria.** `security-governance.md` exists with rule IDs; each rule names the phase and requirement type that must later make it testable; the eleven-step research workflow and its "never start with writes" rule are documented.
**Verification.** TEST-P0-005, TEST-P0-014.
**Priority.** MUST · **Status.** done

### REQ-P0-020 — Risk register
**Description.** The project SHALL maintain a risk register covering, at minimum, the fourteen risk areas named in Phase 0 prompt §14, each with probability, impact, mitigation, detection strategy, fallback and owner.
**Rationale.** OmniBuds' unknowns are structural (OEM stacks, secret protocols, firmware drift); unrecorded risks get designed around silently.
**Dependencies.** REQ-P0-001.
**Acceptance criteria.** All fourteen risks present with all six fields populated at governance level; each risk links to the rule that mitigates it.
**Verification.** TEST-P0-015.
**Priority.** MUST · **Status.** done

### REQ-P0-021 — No implementation during Phase 0
**Description.** Phase 0 SHALL NOT create source files, build configuration, dependency declarations, migrations, manifests, UI, database behavior, Bluetooth code, protocol commands, codec configuration or fake placeholder implementations.
**Rationale.** The prompt's failure conditions (prompt §23) make accidental implementation a Phase 0 failure, not a bonus.
**Dependencies.** None (it constrains all others).
**Acceptance criteria.** File inventory after the phase contains only markdown documents; no Kotlin/Gradle/manifest/`.gitignore` file exists; no stub claims a system "works".
**Verification.** TEST-P0-016.
**Priority.** MUST · **Status.** done

---

## Traceability

| Requirement | Governance document | Task | Test |
|---|---|---|---|
| REQ-P0-001 | `architecture-governance.md` | TASK-P0-002, TASK-P0-003 | TEST-P0-001, TEST-P0-009 |
| REQ-P0-002 | `protocol-governance.md` | TASK-P0-005 | TEST-P0-001, TEST-P0-002 |
| REQ-P0-003 | `audio-governance.md` | TASK-P0-007 | TEST-P0-006 |
| REQ-P0-004 | `protocol-governance.md`, `security-governance.md` | TASK-P0-006 | TEST-P0-010 |
| REQ-P0-005 | `protocol-governance.md` | TASK-P0-005 | TEST-P0-003 |
| REQ-P0-006 | `protocol-governance.md` | TASK-P0-005 | TEST-P0-002 |
| REQ-P0-007 | `protocol-governance.md` | TASK-P0-009 | TEST-P0-004 |
| REQ-P0-008 | `security-governance.md` | TASK-P0-010 | TEST-P0-005 |
| REQ-P0-009 | `audio-governance.md` | TASK-P0-008 | TEST-P0-003 |
| REQ-P0-010 | `audio-governance.md` | TASK-P0-008 | TEST-P0-003 |
| REQ-P0-011 | `audio-governance.md` | TASK-P0-008 | TEST-P0-006 |
| REQ-P0-012 | `protocol-governance.md` | TASK-P0-005 | TEST-P0-007 |
| REQ-P0-013 | `design.md`, `docs/templates/` | TASK-P0-003, TASK-P0-004 | TEST-P0-011 |
| REQ-P0-014 | `sub-agent-orchestration.md` | TASK-P0-011 | TEST-P0-008 |
| REQ-P0-015 | `sub-agent-orchestration.md`, `testing-governance.md` | TASK-P0-011 | TEST-P0-008 |
| REQ-P0-016 | `docs/templates/` | TASK-P0-004 | TEST-P0-011 |
| REQ-P0-017 | `specs.md` | TASK-P0-017 | TEST-P0-012 |
| REQ-P0-018 | `git-workflow.md` | TASK-P0-012 | TEST-P0-013 |
| REQ-P0-019 | `security-governance.md`, `protocol-governance.md` | TASK-P0-010, TASK-P0-018 | TEST-P0-005, TEST-P0-014 |
| REQ-P0-020 | `risk-register.md` | TASK-P0-013 | TEST-P0-015 |
| REQ-P0-021 | `execution-prompt.md` §2 (constraint) | TASK-P0-014, TASK-P0-015 | TEST-P0-016 |

Open requirements deliberately carried forward: none of the above require code. Every rule that constrains implementation is converted into an implementation requirement with a verification method by the phase that owns that subsystem — recorded under Deferred in `validation.md`.
