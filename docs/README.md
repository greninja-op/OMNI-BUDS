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
| `phases/phase-5/` | Phase 5: device fingerprinting & identification (typed signals, versioned normalization, deterministic matching, an empty evidence-gated registry, session enrichment) | validated; no hardware executed, device validation deferred by user directive |
| `phases/phase-6/` | Phase 6: Bluetooth transport layer (lifecycle state machine, GATT/RFCOMM operation surface, resolver contract, Android mechanism behind a seam) | validated; no hardware executed, device validation deferred by user directive |
| `phases/phase-7/` | Phase 7: protocol abstraction engine (lifecycle machine, session + transport-adapter contracts, resolver, command/response, events, vendor-extension reuse — registry ships empty) | validated; no hardware executed, device validation deferred by user directive |
| `phases/phase-8/` | Phase 8: capability discovery engine (evidence provenance, the availability axis, per-protocol dependency validation with cycle detection, a deterministic lifecycle + snapshot wrapping `DeviceCapabilities`, a read-only engine driven by a handed-in source — no protocol, no production source, no device claim) | validated; no hardware executed, device validation deferred by user directive |
| `phases/phase-9/` | Phase 9: hardware feature engine (six-state control machine with requested-vs-confirmed separation, 10-step validator, six-kind dependency/conflict evaluation, mandatory write read-back with never-resent timeouts, per-feature serialization, session invalidation, reactive state repository — handed-in port seam, no vendor commands, no hardware contact) | validated; no hardware executed, device validation deferred by user directive |
| `phases/phase-10/` | Phase 10: audio transport engine (observation-only; A2DP/HFP/HSP/LE Audio taxonomy, 7-state connection vocabulary, audio-device observation separate from Bluetooth identity, immutable snapshot, 5-rule pure reconciler, lifecycle-safe engine, API-33-guarded LE Audio, audio/control plane separation — no capture, no codec configuration, no routing control) | validated; no hardware executed, device validation deferred by user directive |
| `phases/phase-11/` | Phase 11: codec capability engine (extends the Phase 1 codec vocabulary — no parallel taxonomy; `CodecState` ladder + orthogonal configurable; evidence/confidence/observability models; nullable metadata; per-device immutable snapshots; staleness as a state; API-35-isolated adapter; active codec honestly NOT_OBSERVABLE via public APIs — no switching, no forcing, no media interception) | validated; no hardware executed, device validation deferred by user directive |
| `phases/phase-12/` | Phase 12: codec support & configuration architecture (five-dimension control capability; normalized nullable configuration; validated operation values; 14-state structured results; PRECHECK→APPLY→RE-OBSERVE→VERIFY→COMMIT transactions; per-device serialization; timeouts; cancellation safety; rollback; requested-vs-confirmed separation; honest Android adapter — no public codec-control API exists, so select/configure resolve to NOT_SELECTABLE/NOT_CONFIGURABLE; no fake control, no hidden APIs, no vendor guesses) | validated; no hardware executed, device validation deferred by user directive |
| `phases/phase-<N>/` | One directory per future phase, created from `templates/` | not started |
| `templates/` | The eight mandatory phase document templates | complete |
| `decisions/` | Global ADR index → `decisions/README.md` | complete through Phase 12 |
| `development/adb-deployment/` | The local ADB deployment harness: build, validate, install, launch and its error classes | recorded separately from any phase |
| `architecture/`, `protocols/`, `bluetooth/`, `audio/`, `testing/`, `security/`, `requirements/`, `product/` | Long-lived topic documents, promoted out of a phase record when content stops being phase-specific | reserved placeholders; `security/` now holds `device-access-policy.md`, the rest are empty until the owning phase fills them |

Topic folders are deliberately empty rather than pre-filled with stubs: their content will come from the phase that actually earns it, and a stub would imply knowledge that does not exist yet.

## Phase 11 index

| Document | Purpose |
|---|---|
| `requirements.md` | OB-P11-REQ-001 … OB-P11-REQ-025 |
| `design.md` | Extending the Phase 1 codec vocabulary; the honest adapter; evidence, observability, staleness |
| `architecture.md` | Layering, the evidence ladder, provenance flow, observability semantics, snapshot lifecycle, what the control phase must know |
| `specs.md` | Domain types, engine contract, platform contract, verified API-level matrix, error categories, invariants |
| `task-list.md` | Phase 11 tasks and their status |
| `test-plan.md` | Core + Android unit tests, explicit non-coverage |
| `decisions.md` | ADR-P11-001 … ADR-P11-008 — vocabulary reuse, OPUS, NOT_OBSERVABLE honesty, CodecApi35 isolation, staleness, error categories, scope test, layer placement |
| `risk-register.md` | RISK-P11-001 … RISK-P11-008, continuing the single project-wide register |
| `validation.md` | Phase 11 acceptance record (944 tests), the claim-ceiling statement, and Phase 12 readiness |

## Phase 10 index

| Document | Purpose |
|---|---|
| `requirements.md` | OB-P10-REQ-001 … OB-P10-REQ-025 |
| `design.md` | The observation-only engine: ports, handle seam, reconciler rules, lifecycle, audio/control separation, and what it refuses to be |
| `specs.md` | Domain types, engine contract, platform contract, error categories, API-level matrix, permissions, forbidden list |
| `task-list.md` | Phase 10 tasks and their status |
| `test-plan.md` | Core + Android unit tests, the fake-handle pattern, explicit non-coverage |
| `decisions.md` | ADR-P10-001 … ADR-P10-010 — observation-only, taxonomy reuse, pure reconciler, HSP honesty, LE Audio isolation, AutoCloseable, error categories, injected dispatcher, bluetooth.audio package, SCO non-attribution |
| `risk-register.md` | RISK-P10-001 … RISK-P10-008, continuing the single project-wide register |
| `validation.md` | Phase 10 acceptance record (890 tests), the claim-ceiling statement, and Phase 11 readiness |

## Phase 9 index

| Document | Purpose |
|---|---|
| `requirements.md` | OB-P9-REQ-001 … OB-P9-REQ-025 |
| `design.md` | The feature engine mapped onto the reused models — the control state machine, the 10-step validator, dependencies/conflicts, the operation lifecycle, the port seam, the standard catalogue, and what it refuses to be |
| `specs.md` | Package layout, `FeatureOperation`/`FeatureStateRepository`/`FeatureEngine`/`FeatureProtocolPort` contracts, the five new value shapes, the §24→category error map, the nine invariants |
| `task-list.md` | TASK-P9-001 … TASK-P9-024 and their status |
| `test-plan.md` | TEST-P9-001 … TEST-P9-011 grouped by requirement, the scripted port, and the deferred device session as `NOT RUN` |
| `decisions.md` | ADR-P9-001 … ADR-P9-010 — value-shape extension, the port seam, vocabulary reconciliation, EQ-as-value, write-only reservation, the conflict convention |
| `risk-register.md` | RISK-P9-001 … RISK-P9-008, continuing the single project-wide register |
| `validation.md` | Phase 9 acceptance record, the claim-ceiling statement, and Phase 10 readiness |

## Phase 8 index

| Document | Purpose |
|---|---|
| `execution-prompt.md` | The Phase 8 contract, verbatim |
| `architecture-audit.md` | That Phases 1/2 already built the capability model, what Phase 8 genuinely adds (evidence, availability, dependencies, lifecycle, snapshot, engine), the §7/§15 vocabulary reconciliations, and the L2↮L4 layer rule that forces a handed-in source |
| `requirements.md` | OB-P8-REQ-001 … OB-P8-REQ-028 |
| `design.md` | The discovery engine mapped onto the reused model — the read-only seam, evidence folding + conflict, the three-way lifecycle, deterministic snapshot, dependency resolution as a report, and what it refuses to be |
| `specs.md` | Identity reuse, the four dimensions, the evidence ceiling, the fold/conflict rule, the source contract, the §15→category map, the lifecycle table, the snapshot schema, dependency rules, vendor rules, prohibitions, deferrals |
| `task-list.md` | P8-T-001 … P8-T-023 mapped to commit `968adb4` and the test proving each, plus the deferred L3/L4 source binding and device discovery as `NOT RUN` |
| `test-plan.md` | 48 tests (all tier T1) grouped by requirement, the reused Phase 1 capability guards, the test-only scripted source, and the deferred device session as `NOT RUN` |
| `decisions.md` | ADR-P8-001 … ADR-P8-010 — reuse not rebuild, the availability addition, the evidence-ladder fold, the read-only seam, the empty-registry discipline |
| `risk-register.md` | RISK-122 … RISK-138, continuing the single project-wide register |
| `validation.md` | Phase 8 acceptance record (prompt §21/§22), the claim-ceiling statement, the re-summed counts (`:core` 628), and Phase 9 readiness |

## Phase 7 index

| Document | Purpose |
|---|---|
| `execution-prompt.md` | The Phase 7 contract, verbatim |
| `architecture-audit.md` | That Phases 1/2 already built the protocol knowledge model, what Phase 7 genuinely adds, and the §8/§11 vocabulary discrepancies surfaced rather than forked |
| `requirements.md` | OB-P7-REQ-001 … OB-P7-REQ-020 |
| `design.md` | The protocol engine mapped onto the reused model — lifecycle, session/transport boundary, resolver, commands, events, vendor extensions, and what it refuses to be |
| `specs.md` | Descriptor contract, verification-ladder aliasing, lifecycle transition table, session/adapter signatures, resolution rules, command/response/event contracts, security, limitations |
| `task-list.md` | P7-T-001 … P7-T-010 mapped to commit `d1fe1a0` and the test proving each, plus the explicitly-not-run scope |
| `test-plan.md` | 31 tests (all tier T1) grouped by requirement, the reused registry guards, the test-only scripted session, and the deferred device session as `NOT RUN` |
| `decisions.md` | ADR-P7-001 … ADR-P7-010 — reuse not rebuild, the ladder reconciliations, the empty-registry discipline |
| `risk-register.md` | RISK-110 … RISK-121, continuing the single project-wide register |
| `validation.md` | Phase 7 acceptance record (prompt §20), the claim-ceiling statement, the close-out findings, and Phase 8 readiness |

## Phase 6 index

| Document | Purpose |
|---|---|
| `execution-prompt.md` | The Phase 6 contract, verbatim |
| `architecture-audit.md` | That Phases 1/2 already built the transport boundaries and left them unimplemented, the layer-map facts, and the `BleTransport` doc/code contradiction escalated rather than filled |
| `requirements.md` | OB-P6-REQ-001 … OB-P6-REQ-022 |
| `design.md` | The transport hierarchy as filled in — lifecycle, operation surface, concurrency, resolution, the framework-free seam and the Android mechanism, and what the design refuses to be |
| `specs.md` | Taxonomy table, state transition table, GATT/RFCOMM member signatures, error mapping, resolution outcomes, platform limitations |
| `task-list.md` | P6-T-001 … P6-T-015 mapped to commit `83740c5` and the test proving each, plus the explicitly-not-run scope |
| `test-plan.md` | 24 tests (all tier T1) grouped by requirement, the two inherited guards amended in the open, and the deferred device session as `NOT RUN` |
| `decisions.md` | ADR-P6-001 … ADR-P6-012, including the `BleTransport` contradiction correction and the scan-tag/scope boundary carried from Phase 5 |
| `risk-register.md` | RISK-098 … RISK-109, continuing the single project-wide register |
| `validation.md` | Phase 6 acceptance record (prompt §19), the claim-ceiling statement, the close-out findings, and Phase 7 readiness |

## Phase 5 index

| Document | Purpose |
|---|---|
| `execution-prompt.md` | The Phase 5 contract, verbatim |
| `architecture-audit.md` | What Phase 1 had built-but-never-fed for identity, the reuse decision, the layer arithmetic, and the two inherited over-claims escalated rather than filled |
| `requirements.md` | OB-P5-REQ-001 … OB-P5-REQ-030 |
| `design.md` | The identity pipeline mapped onto the existing architecture, the signal/fingerprint/registry/result models, matching and confidence, session integration, and what the design refuses to be |
| `specs.md` | Signal contract and reachability table, normalization rules, fingerprint format, confidence semantics table, result contract, matching rules, registry versioning, privacy rules, platform limitations |
| `task-list.md` | P5-T-001 … P5-T-012 mapped to commit `a5a177d` and the test that proves each, plus the explicitly-not-run scope |
| `test-plan.md` | 43 tests (all tier T1) grouped by requirement, the inherited guards, and the deferred physical-device verification as `NOT RUN` |
| `decisions.md` | ADR-P5-001 … ADR-P5-012, including the settlement of the long-`proposed` ADR-P0-018 and the scan-tag correction to inherited data |
| `risk-register.md` | RISK-086 … RISK-097, continuing the single project-wide register |
| `validation.md` | Phase 5 acceptance record (prompt §20), the claim-ceiling statement, the close-out findings, and Phase 6 readiness |

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
