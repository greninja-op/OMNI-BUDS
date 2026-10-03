# OmniBuds documentation

Start here. Read `MASTER-CONTEXT.md` first; it is the source of truth for what the product is and why it is built this way.

## Authority order

```text
1. docs/MASTER-CONTEXT.md          master architecture & product principles
2. docs/phases/phase-<N>/decisions.md   accepted ADRs (bound later phases)
3. docs/phases/phase-0/*-governance.md   project-wide rules
4. docs/phases/phase-<N>/          that phase's record set
```

A phase execution prompt that conflicts with items 1–3 must be reported, not silently followed (master §58).

## Layout

| Path | Contains | Status |
|---|---|---|
| `MASTER-CONTEXT.md` | Master contract, sections 1–58 | authoritative |
| `phases/phase-0/` | Phase 0: engineering contract, repository rules, governance | validated |
| `phases/phase-1/` | Phase 1: project foundation and Kotlin architecture | validated |
| `phases/phase-2/` | Phase 2: Android Bluetooth foundation (mechanism, permissions, capability reporting) | validated, no hardware executed |
| `phases/phase-3/` | Phase 3: connected-device detection (bond and link observation, the paired census, profile union, reconciliation) | validated; no hardware executed, device validation deferred by user directive |
| `phases/phase-4/` | Phase 4: device sessions (the session engine, lifecycle over the projection, reconnect policy, authoritative reactive state) | validated; no hardware executed, device validation deferred by user directive |
| `phases/phase-<N>/` | One directory per future phase, created from `templates/` | not started |
| `templates/` | The eight mandatory phase document templates | complete |
| `decisions/` | Global ADR index → `decisions/README.md` | complete through Phase 3 |
| `development/adb-deployment/` | The local ADB deployment harness: build, validate, install, launch and its error classes | recorded separately from any phase |
| `architecture/`, `protocols/`, `bluetooth/`, `audio/`, `testing/`, `security/`, `requirements/`, `product/` | Long-lived topic documents, promoted out of a phase record when content stops being phase-specific | reserved placeholders; `security/` now holds `device-access-policy.md`, the rest are empty until the owning phase fills them |

Topic folders are deliberately empty rather than pre-filled with stubs: their content will come from the phase that actually earns it, and a stub would imply knowledge that does not exist yet.

## Phase 4 index

| Document | Purpose |
|---|---|
| `execution-prompt.md` | The Phase 4 contract, verbatim |
| `architecture-audit.md` | What Phase 1 and Phase 3 had already decided about sessions, the naming collision it created, and the three-way deferral conflict reported rather than resolved |
| `requirements.md` | REQ-P4-001 … REQ-P4-030 |
| `design.md` | The prompt's diagram as actually built, the dataflow, the mapping table, identity, lifecycle, concurrency, privacy |
| `specs.md` | Real signatures, the reachable-state set, the 13-row mapping table, flow declarations, guard set, open items |
| `task-list.md` | TASK-P4-001 … TASK-P4-018 in execution order |
| `test-plan.md` | TEST-P4-<NNN> records; mocked and physical kept apart, the gap table, and the deferred device session as an instruction |
| `decisions.md` | ADR-P4-001 … ADR-P4-012, with four dated close-out corrections where an ADR had overstated its own implementation |
| `risk-register.md` | RISK-065 … RISK-085, continuing the single project-wide register |
| `validation.md` | Phase 4 acceptance record: the state machine, the reconnection policy, the close-out findings, and Phase 5 readiness |

## Phase 3 index

| Document | Purpose |
|---|---|
| `execution-prompt.md` | The Phase 3 contract, verbatim |
| `architecture-audit.md` | Pre-execution inspection of the tree, the two-axis trap it found, and what Phase 2 left open |
| `connection-observation-research.md` | What Android will and will not tell an app about connected devices, cited per claim, with the UNVERIFIED list |
| `requirements.md` | REQ-P3-001 … REQ-P3-027 |
| `design.md` | Layered flow, the three axes, the maintained profile union, standing-before-looking, lifecycle and platform limits |
| `specs.md` | Real signatures, enum members, the transition grid, join and redaction rules, mapping tables, guard set |
| `task-list.md` | TASK-P3-001 … TASK-P3-021 in execution order, including the corrections made during execution and at close-out |
| `test-plan.md` | TEST-P3-<NNN> records; mocked and physical kept apart, with the deferred device session written as an instruction |
| `decisions.md` | ADR-P3-001 … ADR-P3-019, with dated amendments where the tree moved past an entry |
| `risk-register.md` | RISK-044 … RISK-064, continuing the single project-wide register |
| `validation.md` | Phase 3 acceptance record, the deferred-verification list, and Phase 4 readiness |

## Phase 2 index

| Document | Purpose |
|---|---|
| `execution-prompt.md` | The Phase 2 contract, verbatim |
| `architecture-audit.md` | Pre-execution audit of Phase 0/1 against the prompt, with the findings this phase had to answer |
| `bluetooth-api-research.md` | Android Bluetooth behaviour as documented, cited per question, including where the docs disagree with each other |
| `transport-boundaries.md` | The transport shape Phase 2 establishes and the members it refuses to invent |
| `requirements.md` | REQ-P2-001 … REQ-P2-022 over the authorised inspection scope |
| `design.md` | Platform boundary, adapter abstraction, permission architecture, state observation, concurrency, limitations |
| `specs.md` | Real signatures, enums, the permission matrix, state machine, error and coroutine contracts |
| `task-list.md` | TASK-P2-001 … TASK-P2-032 with files, required tests and evidence |
| `test-plan.md` | TEST-P2-001 … TEST-P2-040 records, mocked and physical kept apart |
| `decisions.md` | ADR-P2-001 … ADR-P2-018 |
| `risk-register.md` | RISK-026 … RISK-043, continuing the single project-wide register |
| `validation.md` | Phase 2 acceptance record, the evidence-ceiling statement and Phase 3 readiness |

## Phase 1 index

| Document | Purpose |
|---|---|
| `execution-prompt.md` | The Phase 1 contract, verbatim |
| `repository-analysis.md` | Toolchain discovery, build inventory, what was already absent |
| `requirements.md` | REQ-P1-001 … REQ-P1-022 |
| `design.md` | Module topology, area layer map, state/capability/audio/protocol architecture |
| `specs.md` | Naming, state, error, coroutine, Flow, dependency and visibility rules as realised |
| `task-list.md` | TASK-P1-001 … TASK-P1-035 with owners and verification |
| `test-plan.md` | TEST-P1-<NNN> records over the 302 executed tests |
| `decisions.md` | ADR-P1-001 … ADR-P1-021, including corrections made after review |
| `risk-register.md` | Project risks added by Phase 1 |
| `validation.md` | Phase 1 acceptance record and Phase 2 readiness |
| `architecture-review.md` | The eight architecture questions of prompt section 54, answered |
| `domain-model-review.md` | Domain types, invariants and the temptations that would weaken them |
| `testing-review.md` | Test tiers reached, doubles strategy and remaining gaps |
| `kmp-review.md` | What is genuinely portable today and what Phase 46 must still do |
| `code-quality-review.md` | Naming, visibility, dependency hygiene and open findings |

## Phase 0 index

| Document | Purpose |
|---|---|
| `execution-prompt.md` | The Phase 0 contract, verbatim |
| `repository-audit.md` | What existed before this phase (nothing but the master doc; not a git repo) |
| `requirements.md` | REQ-P0-001 … REQ-P0-021 |
| `design.md` | Governance architecture: layers, dependency direction, doc interfaces, phase lifecycle |
| `specs.md` | Project-wide naming, state, error, coroutine and retry conventions |
| `task-list.md` | TASK-P0-001 … TASK-P0-019 with owners and verification |
| `test-plan.md` | TEST-P0-001 … TEST-P0-018, all hardware-free |
| `decisions.md` | ADR-P0-001 … ADR-P0-021, incl. master-vs-prompt conflict resolutions |
| `risk-register.md` | RISK-001 … RISK-015 |
| `architecture-governance.md` | Layers, module boundaries, dependency rules, code quality, KMP |
| `protocol-governance.md` | Transport/protocol abstraction, capability state machine, verification ladder, protocol database |
| `audio-governance.md` | Audio path isolation, codec state model, registry, DSP separation |
| `security-governance.md` | Permissions, identifiers, logging, unknown-device and write safety, research authorization |
| `testing-governance.md` | Test tiers, mocking ceilings, definition of done, regression and phase acceptance |
| `git-workflow.md` | Branches, commits, review gates, rollback (repository not yet initialised) |
| `sub-agent-orchestration.md` | Roles, ownership, briefs, parallelisation, escalation, stop rule |
| `validation.md` | Phase 0 acceptance record and Phase 1 readiness |

## Non-negotiables

- OmniBuds never represents an unimplemented hardware capability as supported (ADR-P0-001).
- OmniBuds stays outside the media audio path (ADR-P0-002).
- `UNKNOWN` is never written as `UNSUPPORTED`, and "not verified" is never written as `SUPPORTED` (ADR-P0-004, ADR-P0-016).
- A successful write never by itself proves persistence (ADR-P0-005, ADR-P0-017).
- Unknown devices are read-only; no arbitrary writes, no fuzzing, no undocumented commands (ADR-P0-006).
- A phase stops at its boundary and waits for an explicit execution prompt (ADR-P0-009).
