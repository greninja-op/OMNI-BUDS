# Phase 0 — Design

Scope of this document: how OmniBuds is *governed*. Phase 0 designs a documentation and decision system, not a runtime system. Runtime architecture is specified as constraints that Phases 1+ must satisfy; it is not implemented here.

Reference: `docs/MASTER-CONTEXT.md` §§40–58, `docs/phases/phase-0/execution-prompt.md` §§6–8.

---

## 1. What Phase 0 produces

```text
                     Master contract (unchanging)
                              │
              docs/MASTER-CONTEXT.md  (source of truth, §58 authority)
                              │
        ┌─────────────────────┼─────────────────────┐
        ▼                     ▼                     ▼
  Governance rulebook   Document templates   Phase record set
  (architecture,        (docs/templates/)    (docs/phases/phase-N/)
   protocol, audio,                            │
   security, testing,                          ├── requirements.md
   git, orchestration)                         ├── design.md
        │                                      ├── specs.md
        └──────────────┬───────────────────────┤
                       │                       ├── task-list.md
                       ▼                       ├── test-plan.md
              docs/<topic>/  long-lived        ├── validation.md
              topic folders                    ├── decisions.md
                                               └── risk-register.md
```

Two kinds of document exist and must not be confused:

| Kind | Location | Lifetime | Contains |
|---|---|---|---|
| Governance rulebook | `docs/phases/phase-0/*-governance.md`, `docs/<topic>/` | Project-wide, edited by ADR | Rules that bind every phase |
| Phase record | `docs/phases/phase-<N>/` | Immutable once the phase is validated | What that phase required, designed, built and proved |

Phase records are history and are not rewritten by later phases; when a later phase changes a rule, it writes an ADR and the governance rulebook is updated.

---

## 2. Architecture hierarchy (prompt §8.1)

```text
Product        what the user is promised: control of real device capabilities
   ↓
Architecture   layers, boundaries, dependency direction
   ↓
Core abstractions   DeviceIdentity, DeviceFingerprint, Capability, DeviceState,
                    AudioState, Transport, Protocol, VerificationLevel
   ↓
Platform implementations   Android Bluetooth, permissions, notifications,
                           audio APIs, lifecycle, Quick Settings
   ↓
Vendor protocols   structured protocol definitions per manufacturer family
   ↓
Device implementations   per-model behaviour, limitations and verified facts
```

Ownership at each level, in one line each:

- Product and architecture levels are owned by the master document plus accepted ADRs.
- Core abstractions are owned by `architecture-governance.md`; they are platform-independent and must be KMP-portable.
- Platform implementations are owned by platform modules; they expose capabilities *through* core abstractions and never become the place where capability truth lives.
- Vendor protocols are data first (definitions, parsers, encoders) and code second; they own no global state.
- Device implementations are records of what has been proven on specific hardware, expressed through the verification ladder.

---

## 3. Dependency direction (prompt §8.2)

```text
UI / presentation
   ↓
Application / state          (single source of device truth lives here)
   ↓
Core domain                  (no Android imports)
   ↓
Protocol / capability abstractions
   ↓
Platform transport implementations
   ↓
Vendor protocol definitions
```

Rules:

1. Dependencies point downward only. A lower layer never references a higher one.
2. Vendor protocol code MUST NOT become the owner of global application state (prompt §8.2).
3. UI never decides whether a capability exists; it renders the capability state it is handed (master §11).
4. The domain model must not become tightly coupled to Android classes (prompt §8.3); Android types stop at platform boundaries and are translated.
5. A transport implementation must not be reachable from the UI layer directly; it is reached through the abstraction.

Forbidden, regardless of phase:

```text
core-domain      → android.*
vendor-protocol  → UI / state owner
UI               → GATT/RFCOMM callbacks
transport impl   → another transport impl
protocol data    → hard-coded hex literals scattered in call sites (master §52)
```

---

## 4. Module boundaries (conceptual; Phase 1 maps them to Gradle)

| Bounded area | Owns | Must not own |
|---|---|---|
| Adapter/platform services | Adapter state, permission gates, system event sources | Capability truth |
| Session & lifecycle | Active session set, saved device set, transitions | Protocol parsing |
| Fingerprint & identity | Identity inputs, matching, identification result | UI presentation |
| Transport abstraction | Transport selection, channel attach, transport contract | Vendor command meaning |
| Protocol abstraction | Operation shapes, negotiation, session protocol binding | Global app state |
| Vendor protocols | Command/response structure, parsers, encoders | Persistence claims about user intent |
| Capability engine | CapabilityState per feature, metadata, promotion rules | UI layout |
| Audio/codec subsystem | Transport/codec/quality state, registry | Media audio path |
| Persistence engine | Write intent, verification ladder results | Silent assumptions of durability |
| Feature dependency | Conflicts, prerequisites, exclusivity | Firmware update paths |
| Diagnostics | Report assembly, redaction | Autonomous network upload |
| Presentation | Rendering, user intent capture | Support decisions |

Details and the full rule set: `docs/phases/phase-0/architecture-governance.md`.

---

## 5. Android isolation (prompt §8.3)

Android-specific surfaces stay behind platform boundaries: Bluetooth APIs, runtime permissions, Quick Settings, notifications, lifecycle owners, background service mechanisms, Android audio APIs.

Consequences that later phases must honor:

- Anything Android forbids is reported as forbidden, not worked around (master §4, §32, §35).
- Platform behavior that varies by Android version is a documented capability input, not an assumption.
- Domain code receives platform facts as data through the abstractions above.

---

## 6. Future KMP compatibility (prompt §8.4)

```text
Shared (target: commonMain in a future phase)
   DeviceIdentity · DeviceFingerprint · Capability/CapabilityState ·
   DeviceState · AudioState · ProtocolDefinition/Command/Response ·
   packet parsing · state machines · persistence rules · validation logic ·
   diagnostics models

Platform-specific (per-platform source sets or platform modules)
   Bluetooth transport · Android permissions · Quick Settings ·
   Android notification · Windows/macOS/Linux Bluetooth · desktop integration
```

Phase 0 imposes only cost-free constraints now: no Android types crossing the abstraction boundary, no vendor logic in shared code paths, no assumption that a transport exists on non-Android platforms. Module topology, expect/actual usage and build layout are Phase 1 and Phase 46 decisions — Phase 0 does not pre-decide them.

---

## 7. Documentation interfaces: ID grammar

The join key across all documents. Every reference between documents is by ID, never by section title.

```text
REQ-<SCOPE>-<NNN>     requirement
TASK-<SCOPE>-<NNN>    task
TEST-<SCOPE>-<NNN>    test
ADR-<SCOPE>-<NNN>     architecture decision record
RISK-<NNN>            project risk (scope is always project-wide)
ARCH|PROTO|AUD|SEC|TST|GIT|ORCH-<area>-<NNN>   governance rule IDs
```

`<SCOPE>` is a phase or subsystem tag: `P0`, `BT`, `SESS`, `FPR`, `TR`, `PROT`, `CAP`, `AUDIO`, `CODEC`, `PERS`, `VND`, `UI`, `QA`, `KMP`.

Resolution of the prompt's `P0-T001` / `P0-TEST-001` forms to this grammar is recorded in ADR-P0-013.

---

## 8. Lifecycle: how a phase runs

```text
   [Prompt "Execute Phase N"]
            │
            ▼
   [Inspect repo] → [Read master] → [Read prior phase records] → [Read ADRs]
            │
            ▼
   [Build dependency graph] → [Partition into file-disjoint workstreams]
            │
            ▼
      [Sub-agents execute] ──────────► conflict discovered?
            │                              │
            ▼                              ▼
   [Orchestrator reviews output      [Report → evaluate → ADR →
    against files on disk]            document; never silent]
            │
            ▼
   [Implement] → [Test] → [Integrate] → [Regression]
            │
            ▼
   [Update task-list, validation, decisions]
            │
            ▼
   [Mark done ONLY where acceptance criteria pass]
            │
            ▼
   [STOP at phase boundary — emit report, do not advance]
```

States of a phase record: `draft` → `in-review` → `validated` → `closed`. Once `validated`, a phase record is not edited except by an erratum entry that references the ADR causing the change.

---

## 9. Data flow of a requirement

```text
requirements.md  REQ-*  ──acceptance criteria──►  design.md section
      │                                                 │
      │                                                 ▼
      │                                          specs.md contract
      │                                                 │
      ▼                                                 ▼
 task-list.md  TASK-* (depends on REQ-*, owns files) ──► implementation
      │
      ▼
 test-plan.md  TEST-* (verifies REQ-*, evidence level declared)
      │
      ▼
 validation.md  (implemented / not implemented / passed / failed /
                 known + platform + hardware limitations / deferred)
```

Every REQ must be reachable from at least one TASK and at least one TEST. An orphan requirement is a validation failure (prompt §23: vague or unverifiable requirements fail the phase).

---

## 10. Interfaces between the governance documents

| Document | Exports | Consumed by |
|---|---|---|
| `architecture-governance.md` | Layer/boundary rules, code-quality rules, KMP constraints | Phases 1, 46, every implementation task |
| `protocol-governance.md` | CapabilityState machine, verification ladder, research workflow, protocol DB schema | Phases 5–8, 17–23, 37 |
| `audio-governance.md` | CodecState model, registry, path-isolation rule | Phases 10–15 |
| `security-governance.md` | Permission, identifier, logging, write-safety rules | Phases 2–4, 21, 25–28, 35 |
| `testing-governance.md` | Test tiers, DoD, verification ceilings, regression rules | Every phase |
| `git-workflow.md` | Branch, commit, review, rollback rules | Every phase |
| `sub-agent-orchestration.md` | Roles, ownership, briefs, parallelisation | Every phase |

---

## 11. Error handling and conflict policy (governance level)

- A conflict between a phase prompt and the master is an *event to be reported*, never absorbed silently (master §58).
- A conflict between two sub-agent outputs is resolved by the orchestrator against the master, and the resolution is written to `decisions.md`.
- Unresolved architectural disagreement is recorded as an open question with a named owner and blocks the affected requirement, not the whole phase.
- Implementation-level structured errors are specified in `specs.md` §3.

---

## 12. Concurrency (governance level)

Parallelism is permitted only across file-disjoint deliverables, ordered by the task dependency graph. Shared interfaces are serialised: one workstream writes the contract, others consume it. Details: `sub-agent-orchestration.md`.

---

## 13. Platform limitations acknowledged in Phase 0

- Android background execution, permission and Quick Settings restrictions are treated as design inputs (master §32, §35).
- Codec selection is jointly determined by OS, stack, OEM, phone hardware and headset (master §17); no rule in Phase 0 may promise control the platform does not expose.
- No physical device was tested in Phase 0. Every hardware-related statement in this phase is a *rule*, at verification level INFERRED, and none is a support claim.

---

## 14. Extensibility

New subsystems enter through the same door: a requirement, a boundary in the architecture rulebook, a capability or codec/protocol registry entry, an ADR if a boundary moves. No subsystem is permitted to add a hidden global, a manager god-class, or a vendor `if` branch inside shared layers (master §51).
