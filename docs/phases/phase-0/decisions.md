# Phase 0 — Architecture Decision Records

Scope: Phase 0 governance decisions, plus the resolutions required where `docs/MASTER-CONTEXT.md` and `docs/phases/phase-0/execution-prompt.md` disagreed. Per master §58 no conflict was absorbed silently; each appears here exactly once. ADR status `proposed` means it needs the user's confirmation before the affected phase relies on it.

Global index: `docs/decisions/README.md`.

---

## Part A — Foundational decisions (required by prompt §13)

### ADR-P0-001 — OmniBuds does not simulate hardware
**Status.** accepted · **Date.** 2026-10-01
**Context.** Manufacturer apps and demo apps commonly show controls the hardware ignores. OmniBuds' identity claim is that it exposes only what a device actually implements (master §2).
**Decision.** OmniBuds never renders, reports or emulates a capability it has not established the device implements. No fake ANC, transparency, LDAC, spatial audio, hardware EQ or vendor feature. A software-only feature may exist only when explicitly labelled as software and never presented as device hardware behavior.
**Options considered.** (a) Show common controls greyed-out — rejected: users read greyed-out as "not yet enabled"; (b) best-effort software approximation — rejected: it is the exact deception the product exists to avoid; (c) backend capability state drives UI — adopted.
**Consequences.** Every UI surface becomes downstream of discovery; the product will sometimes show very few controls, which is correct behavior; the capability model must be able to express uncertainty.
**Relates to.** REQ-P0-002, `protocol-governance.md`, `architecture-governance.md`.

### ADR-P0-002 — OmniBuds stays outside the media audio path
**Status.** accepted
**Context.** Reprocessing audio would let the app fake EQ or ANC but degrades quality, latency, codec negotiation, battery and stability (master §3).
**Decision.** OmniBuds uses a control/configuration channel only. Android's Bluetooth audio stack owns media transport. OmniBuds does not capture, process, re-encode or retransmit media audio in the primary experience.
**Options considered.** (a) Phone-side DSP for universal EQ — rejected under ADR-P0-001; (b) optional in-path mode — deferred: only admissible by a future ADR plus explicit software labelling.
**Consequences.** Hardware EQ/ANC require vendor protocols; features unavailable to a device stay unavailable; the audio subsystem's job becomes *reporting and control*, not *rendering*.
**Relates to.** REQ-P0-003, `audio-governance.md`.

### ADR-P0-003 — Transport abstraction is required
**Status.** accepted
**Context.** Vendor stacks mix BLE for advertising/bookkeeping, RFCOMM for control, A2DP for audio, and sometimes proprietary configuration channels (master §8).
**Decision.** No component may assume GATT. Transports sit behind one abstraction; a session selects and attaches a transport; unavailability is a structured `TransportUnavailable` result.
**Options considered.** BLE-only MVP — rejected: guarantees a rewrite for the first RFCOMM vendor; per-vendor bespoke connection code — rejected: duplicated transport logic (master §51).
**Consequences.** Phase 6 must build the abstraction before any vendor works; capability discovery gains a transport binding field.
**Relates to.** REQ-P0-005, `protocol-governance.md`.

### ADR-P0-004 — Capabilities are richer than boolean values
**Status.** accepted
**Context.** "supported = true" cannot distinguish read-only, volatile, persistent, verified, or merely-not-yet-discovered (master §10).
**Decision.** `CapabilityState = UNKNOWN | UNSUPPORTED | READ_ONLY | SUPPORTED_VOLATILE | SUPPORTED_PERSISTENT | PERSISTENCE_VERIFIED`, with readable/writable/transport/protocol/requires-connection/persistence/verification metadata. UI shows only what the state permits.
**Consequences.** Discovery becomes a state-producing pipeline with evidence rules; tests must assert state, not mere presence of a control.
**Relates to.** REQ-P0-006, ADR-P0-001, `specs.md` §2.

### ADR-P0-005 — Persistent writes require reconnect verification
**Status.** accepted
**Context.** Devices routinely accept writes into volatile configuration; the setting then reverts after reconnect (master §24).
**Decision.** `SUPPORTED_PERSISTENT` requires read-back; `PERSISTENCE_VERIFIED` additionally requires the disconnect/reconnect round trip. A successful write alone never establishes persistence.
**Consequences.** Persistence tests need hardware and user cooperation; some settings will be reported as volatile, which is accurate; UI must communicate volatility.
**Relates to.** REQ-P0-007, ADR-P0-017, `testing-governance.md`.

### ADR-P0-006 — Unknown devices start read-only
**Status.** accepted
**Context.** Writing undocumented commands to hardware the user owns risks misconfiguration or worse (master §25).
**Decision.** Devices with no protocol record are limited to read-only discovery of metadata, services, characteristics, descriptors, manufacturer data and standard battery. No arbitrary writes, no fuzzing, no undocumented commands.
**Consequences.** Unsupported devices still yield useful diagnostics; protocol research becomes an explicit, gated laboratory activity rather than an app behavior.
**Relates to.** REQ-P0-008, `security-governance.md`, ADR-P0-018.

### ADR-P0-007 — Vendor-specific capabilities remain accessible
**Status.** accepted
**Context.** "Universal" must not mean the intersection of features across vendors (master §13).
**Decision.** Common functionality gets a common interface; unique functionality gets a vendor extension namespace and stays addressable. Unmodelled vendor features are recorded as known-to-exist rather than dropped.
**Consequences.** A capability registry that supports extension ids; more UI surface to maintain; support claims become per-feature rather than per-brand.
**Relates to.** REQ-P0-012, `protocol-governance.md`.

### ADR-P0-008 — Android first; KMP compatibility is an architectural target
**Status.** accepted
**Context.** The product promise depends on reaching the widest installed base first, while the long-term plan includes Windows, macOS and Linux (master §1).
**Decision.** Android is the first implementation. Core abstractions are designed to be portable; platform APIs stay behind boundaries; module topology and `expect`/`actual` mechanics are decided in Phase 1 and Phase 46, not now.
**Consequences.** Phase 1 must not smuggle Android types into shared code; some convenience is deferred.
**Relates to.** `architecture-governance.md`, `design.md` §6.

### ADR-P0-009 — Phases do not automatically advance
**Status.** accepted
**Context.** The user sequences work through explicit execution prompts; auto-advancement would bypass review and multiply cost (master §50).
**Decision.** Completing a phase ends the turn: emit the completion report (implemented / tests / known limitations / deferred / next phase) and stop.
**Consequences.** Phases are reviewable units; a phase's validation document is the gate.
**Relates to.** REQ-P0-015, `sub-agent-orchestration.md`.

### ADR-P0-010 — Sub-agents are orchestrated through explicit ownership boundaries
**Status.** accepted
**Context.** Parallel agents editing the same abstraction lose work silently (master §38, §39).
**Decision.** Every agent gets a named workstream with file-disjoint deliverables, a mandatory brief (objective, files, constraints, outputs, tests, stop conditions), and an escalation path. Shared interfaces are authored serially and consumed in parallel. The orchestrator verifies output on disk instead of trusting reports.
**Consequences.** Phase planning becomes a dependency-graph exercise; this phase was executed exactly this way (see `task-list.md`).
**Relates to.** REQ-P0-014, `sub-agent-orchestration.md`.

---

## Part B — Conflict resolutions between the master and the Phase 0 prompt

### ADR-P0-011 — Phase documents live in `docs/phases/phase-<N>/`
**Status.** accepted
**Conflict.** Prompt §4 names agent outputs under `docs/phase-0/`, while prompt §6 and master §40/§56 place phase documents under `docs/phases/`.
**Decision.** Canonical location is `docs/phases/phase-<N>/`; the required per-phase file set is unchanged. All Phase 0 outputs were written there.
**Consequences.** One location to look in; the prompt's §4 paths are treated as shorthand. No content moved.

### ADR-P0-012 — Error category set is the master's thirteen
**Status.** accepted
**Conflict.** Master §30 lists thirteen categories; prompt §9.3 lists ten (omitting `RfcommFailure`, `FirmwareMismatch`, and one BLE-specific entry).
**Decision.** Thirteen is canonical: BluetoothDisabled, PermissionDenied, DeviceDisconnected, TransportUnavailable, GattFailure, RfcommFailure, ProtocolMismatch, UnsupportedFeature, WriteRejected, VerificationFailed, Timeout, FirmwareMismatch, CodecUnavailable.
**Consequences.** RFCOMM and firmware failures get their own handling instead of being disguised as generic transport errors — directly needed by ADR-P0-003.

### ADR-P0-013 — Identifier grammar normalised to `TASK-<SCOPE>-<NNN>` / `TEST-<SCOPE>-<NNN>`
**Status.** accepted
**Conflict.** Prompt §10/§11 use `P0-T001` and `P0-TEST-001`; master §44/§45 and the project grammar use a leading kind token.
**Decision.** One grammar for all ids: `REQ|TASK|TEST|ADR-<SCOPE>-<NNN>`, `RISK-<NNN>`. Phase 0 task `P0-T001`… maps 1:1 onto `TASK-P0-001`…; `P0-TEST-001`… onto `TEST-P0-001`….
**Consequences.** Cross-document references parse uniformly and are greppable; anyone searching the prompt's numbering must use the mapping above.

### ADR-P0-014 — Verification levels use underscore spelling and apply to two subjects
**Status.** accepted
**Conflict.** Prompt §16 hyphenates (`LAB-TESTED`, `HARDWARE-VERIFIED`) and speaks of capability classification; master §27 uses underscores and speaks of protocol confidence.
**Decision.** Canonical values: `INFERRED | IMPLEMENTED | LAB_TESTED | HARDWARE_VERIFIED | PERSISTENCE_VERIFIED`. The ladder is reused for two distinct subjects — protocol confidence (master §27) and capability verification status (master §10/§54) — and a record must say which subject it qualifies.
**Consequences.** Removes the ambiguity where "verified" could mean either the protocol or the feature; makes "code exists" (`IMPLEMENTED`) visibly weaker than "worked on real hardware".

### ADR-P0-015 — Six codec states; "Selected" maps to `ENABLED`
**Status.** accepted
**Conflict.** Prompt §17 gives a five-name ladder including "Codec Selected" and omits `CONFIGURABLE`; master §15 defines six states.
**Decision.** `CodecState = SUPPORTED | AVAILABLE | ENABLED | NEGOTIATED | ACTIVE | CONFIGURABLE`. "Selected" is vocabulary, not a state: it denotes the user- or system-chosen codec, i.e. `ENABLED`, which may still fail to negotiate.
**Consequences.** The common real-world case — user selected LDAC, stack is running AAC — is expressible, which is precisely the display failure the product must not commit.

### ADR-P0-016 — Unknown representation is tiered, not absolute
**Status.** accepted (interpretation)
**Conflict.** Master §23 says unknown battery is `null`, not `0%`. Prompt §9.2 says `null` must not substitute for state.
**Decision.** Three tiers: enums for status (explicit `UNKNOWN`), nullable scalars for unreported measurements (`null`, displayed as unknown), typed errors for failed operations. `null` is legitimate for a measurement that was not reported and is never legitimate as a capability, codec or verification state.
**Consequences.** Kills the two opposite failure modes at once — 0% displayed for an unread battery, and `null` silently meaning "unsupported".

### ADR-P0-017 — Persistence ladder is the master's eight steps
**Status.** accepted
**Conflict.** Prompt REQ-P0-007 begins at WRITE with six steps; master §24 begins at READ and includes read-back verification before disconnect.
**Decision.** Canonical: READ → WRITE → READ BACK → VERIFY → DISCONNECT → RECONNECT → READ AGAIN → VERIFY. The pre-write baseline read is required because without it "read back equals intent" cannot be distinguished from "the device already had that value".
**Consequences.** Persistence tests become slightly slower and strictly more meaningful; several devices will be revealed as `SUPPORTED_VOLATILE` that a shorter ladder would have mislabelled.

### ADR-P0-018 — Research order: enumeration precedes identification
**Status.** **proposed — awaiting confirmation**
**Conflict.** Prompt §15 orders "1. Identify → 2. Discover"; master §26 orders "DISCOVER → IDENTIFY". Both are defensible depending on what "discover" means.
**Decision (proposed).** Introduce two distinct terms — **enumeration** (read-only listing of metadata, services, characteristics, descriptors) and **capability discovery** (determining which features the device implements). Canonical ladder: enumerate → identify → read → observe → understand → validate → write → read back → disconnect → reconnect → verify. Capability discovery sits inside "read/observe", and identification may reuse an existing protocol record to shortcut enumeration only when the identity is already established.
**Rationale.** You cannot identify an unknown device without first reading something about it, and identification is precisely the gate that permits writes. This matches master §26 and the read-only rule of ADR-P0-006.
**Consequences.** Phases 3, 5 and 20 will follow this order. Because this is an interpretation of an ambiguous instruction rather than a plain adoption of the master, it is flagged for the user rather than assumed.

### ADR-P0-019 — `docs/product/` is created to match master §56
**Status.** accepted
**Conflict.** Master §56 includes a `docs/product/` folder; prompt §6's target tree omits it.
**Decision.** Created as part of the documentation architecture; it is empty until a phase produces product content. No Phase 0 deliverable was invented to fill it.

### ADR-P0-020 — Two workstreams added beyond prompt §4
**Status.** accepted
**Context.** Prompt §4 names seven agents but assigns no owner for protocol/transport governance rules or for the orchestration contract, both of which prompt §22 requires as acceptance criteria.
**Decision.** Added `protocol-governance.md` and `sub-agent-orchestration.md` workstreams; the seven prompt-named deliverables were still produced, and `repository-audit.md` was produced by the orchestrator directly because the repository was empty enough that delegation added no value.
**Consequences.** Phase 0 covers every §22 acceptance area with an owning document; the deviation is recorded rather than hidden.

### ADR-P0-021 — Version control is not initialised in Phase 0
**Status.** accepted
**Context.** The workspace is not a git repository. Phase 0 was authorised to document a Git workflow (prompt §19), not to create a repository, and initialising one changes workspace state that the user may want arranged differently (root vs parent, remote hosting, existing external sync).
**Decision.** `git-workflow.md` specifies the workflow; `git init`, `.gitignore`, remote setup and CI are deferred to Phase 1 as explicit steps requiring the user's confirmation.
**Consequences.** The workflow is currently unenforceable — recorded as a known limitation in `validation.md` and as RISK-015 rather than presented as completed governance.
