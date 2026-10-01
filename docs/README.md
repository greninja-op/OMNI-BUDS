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
| `phases/phase-<N>/` | One directory per future phase, created from `templates/` | not started |
| `templates/` | The eight mandatory phase document templates | complete |
| `decisions/` | Global ADR index → `decisions/README.md` | complete through Phase 2 |
| `development/adb-deployment/` | The local ADB deployment harness: build, validate, install, launch and its error classes | recorded separately from any phase |
| `architecture/`, `protocols/`, `bluetooth/`, `audio/`, `testing/`, `security/`, `requirements/`, `product/` | Long-lived topic documents, promoted out of a phase record when content stops being phase-specific | reserved placeholders; `security/` now holds `device-access-policy.md`, the rest are empty until the owning phase fills them |

Topic folders are deliberately empty rather than pre-filled with stubs: their content will come from the phase that actually earns it, and a stub would imply knowledge that does not exist yet.

## Phase 2 index

| Document | Purpose |
|---|---|
| `execution-prompt.md` | The Phase 2 contract, verbatim |
| `architecture-audit.md` | Pre-execution audit of Phase 0/1 against the prompt, with the findings this phase had to answer |
| `bluetooth-api-research.md` | Android Bluetooth behaviour as documented, cited per question, including where the docs disagree with each other |
| `transport-boundaries.md` | The transport shape Phase 2 establishes and the members it refuses to invent |
| `requirements.md` | REQ-P2-<NNN> over the authorised inspection scope |
| `design.md` | Platform boundary, adapter abstraction, permission architecture, state observation, concurrency, limitations |
| `specs.md` | Real signatures, enums, the permission matrix, state machine, error and coroutine contracts |
| `task-list.md` | TASK-P2-<NNN> with files, required tests and evidence |
| `test-plan.md` | TEST-P2-<NNN> records, mocked and physical kept apart |
| `decisions.md` | ADR-P2-001 … ADR-P2-017 |
| `risk-register.md` | Project risks added by Phase 2, continuing the single register |
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
