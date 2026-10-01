# Phase 0 — Test Plan

Phase 0 has no code, so its tests are documentation and governance validations. **No test in this plan requires hardware, a Bluetooth adapter, a phone, or an emulator.** Any future phase that claims otherwise for a Phase 0 artifact is out of contract.

Execution method for each test: read the named documents from disk and apply the stated check. Where a check is string- or structure-based it is automatable (documented below); where it is a judgment it requires the orchestrator or a human reviewer. None were "passed" by trusting a sub-agent's self-report.

Legend for every record: Purpose · Setup · Input · Expected result · Failure condition · Automation possibility · Hardware requirement · Traces.

---

### TEST-P0-001 — Mandatory project principles are present in documentation
**Purpose.** Prove the founding rules were carried into governance, not lost in summarisation.
**Setup.** All Phase 0 documents written.
**Input.** `docs/MASTER-CONTEXT.md` §§2, 3, 11, 25, 53, 55; every `docs/phases/phase-0/*.md`.
**Expected.** Hardware truth, audio-path isolation, backend-driven UI, unknown-device read-only, unknown-stays-unknown and no-false-parity each appear as a stated rule with an ID, in the document that owns that area.
**Failure.** A principle exists only in the master and nowhere in Phase 0 output; or appears only as aspiration with no owning rule.
**Automation.** Partial — keyword presence is greppable; fidelity of meaning needs review.
**Hardware.** None. **Traces.** REQ-P0-002, REQ-P0-001.

### TEST-P0-002 — Capability model is richer than a boolean
**Purpose.** Confirm the six-state capability model is defined and correctly distinguished.
**Setup.** `protocol-governance.md`, `specs.md`.
**Input.** CapabilityState list and transition rules.
**Expected.** All six states defined with meanings; metadata (readable, writable, transport, protocol, requires-connection, persistence behavior, verification status) enumerated; `UNKNOWN` explicitly distinct from `UNSUPPORTED`; the UI consequence stated.
**Failure.** Any state missing; `UNKNOWN` used interchangeably with `UNSUPPORTED`; a boolean-only example presented as sufficient.
**Automation.** Partial (state-name presence greppable; distinction needs reading).
**Hardware.** None. **Traces.** REQ-P0-006.

### TEST-P0-003 — Codec and transport architecture
**Purpose.** Verify supported-versus-active separation and transport non-dominance.
**Setup.** `audio-governance.md`, `protocol-governance.md`.
**Input.** CodecState ladder, codec registry, transport abstraction section.
**Expected.** All six codec states defined; a worked example where a codec is supported but not active and the required display; aptX variants listed separately; LC3 modelled under LE Audio rather than as an A2DP codec; registry admits GATT, RFCOMM, classic, LE Audio and vendor transports.
**Failure.** Any document asserts "supported ⇒ active", collapses aptX into one flag, or assumes every device exposes GATT.
**Automation.** Partial. **Hardware.** None. **Traces.** REQ-P0-005, REQ-P0-009, REQ-P0-010.

### TEST-P0-004 — Persistence verification procedure exists
**Purpose.** Confirm the full ladder is specified and that write-success alone is insufficient.
**Setup.** `protocol-governance.md`, `testing-governance.md`.
**Input.** Persistence ladder.
**Expected.** READ → WRITE → READ BACK → VERIFY → DISCONNECT → RECONNECT → READ AGAIN → VERIFY documented, each rung's conclusion stated, failed ladder mapped to `SUPPORTED_VOLATILE` or lower, and the phase that will implement the test named.
**Failure.** The ladder begins at WRITE and treats a successful write as persistence; or reconnect is optional.
**Automation.** No. **Hardware.** None (procedure only; the future test will require hardware).
**Traces.** REQ-P0-007.

### TEST-P0-005 — Unknown-device read-only policy exists
**Purpose.** Confirm a device with no protocol record cannot be written to by policy.
**Setup.** `security-governance.md`, `protocol-governance.md`.
**Expected.** Read-only default; the safe discovery set enumerated; explicit prohibitions on arbitrary characteristic writes, fuzzing and undocumented commands; write preconditions listed.
**Failure.** A permitted-write path exists for unidentified devices; or prohibitions are phrased as advice rather than rules.
**Automation.** No. **Hardware.** None. **Traces.** REQ-P0-008, REQ-P0-019.

### TEST-P0-006 — Audio-path isolation documented
**Purpose.** Confirm OmniBuds is placed outside media transport.
**Setup.** `audio-governance.md`.
**Expected.** The normal Android path and the control-channel path both diagrammed; capture/process/re-encode/transmit prohibited by default with the quality, latency, codec, battery and stability reasons; the ADR-plus-software-labelling condition for any future in-path feature.
**Failure.** The document implies an audio pipeline, effects, or re-encoding path as part of the base design.
**Automation.** No. **Hardware.** None. **Traces.** REQ-P0-003, REQ-P0-011.

### TEST-P0-007 — Vendor-specific feature architecture documented
**Purpose.** Confirm lowest-common-denominator reduction is forbidden.
**Setup.** `protocol-governance.md`.
**Expected.** Common interface for common features; vendor extension namespace; an unmodelled vendor feature recorded rather than dropped; no hard-coded manufacturer branching in shared layers.
**Failure.** Extensions are described as "future consideration" with no boundary; or universal support is defined as ANC/EQ/battery only.
**Automation.** No. **Hardware.** None. **Traces.** REQ-P0-012.

### TEST-P0-008 — Orchestration and phase-boundary rules exist
**Purpose.** Confirm sub-agent governance and the stop rule are written down.
**Setup.** `sub-agent-orchestration.md`, `testing-governance.md`.
**Expected.** Roles with file ownership; mandatory brief contents; parallelisation limited to file-disjoint work; conflict escalation to ADR; the 18-step execution contract; the completion-report shape; explicit prohibition on auto-advancing phases.
**Failure.** Ownership undefined; or the plan permits two agents to edit one file concurrently.
**Automation.** No. **Hardware.** None. **Traces.** REQ-P0-014, REQ-P0-015.

### TEST-P0-009 — Master architecture documented with boundaries, dependency direction and KMP
**Purpose.** REQ-P0-001 acceptance.
**Setup.** `docs/MASTER-CONTEXT.md`, `architecture-governance.md`, `design.md`.
**Expected.** Master present in versioned storage; boundary catalogue; downward-only dependency rule with forbidden examples; shared-versus-platform KMP split; statement that module topology is Phase 1's decision.
**Failure.** Any layer able to depend upward; KMP treated as requiring a decision Phase 0 did not make.
**Automation.** No. **Hardware.** None. **Traces.** REQ-P0-001, REQ-P0-004.

### TEST-P0-010 — Device session separation documented
**Expected.** Active session and saved device distinguished, including that temporary devices are not permanently retained and saving is an explicit user action.
**Failure.** Retention presented as automatic, or no disconnect path.
**Automation.** No. **Hardware.** None. **Traces.** REQ-P0-004.

### TEST-P0-011 — Phase document set and templates complete
**Purpose.** REQ-P0-013, REQ-P0-016 acceptance.
**Setup.** Filesystem listing of `docs/phases/phase-0/` and `docs/templates/`.
**Expected.** All seven mandatory phase documents plus `risk-register.md` exist; all eight templates exist; Phase 0 satisfies its own documentation rule.
**Failure.** A mandatory file missing, or a template omitting a mandatory field from master §41–47.
**Automation.** **Yes** — directory and heading check.
**Hardware.** None. **Traces.** REQ-P0-013, REQ-P0-016.

### TEST-P0-012 — Specifications complete
**Expected.** `specs.md` contains naming (class, interface, enum, file, package, test, protocol, capability), state conventions including the tiered `null`/`UNKNOWN` rule, all thirteen error categories with retry classes, coroutine/Flow/lifecycle rules, and read-versus-write retry asymmetry with the timed-out-write worked example.
**Failure.** Any of the five convention families absent; or `null` sanctioned as capability state.
**Automation.** Partial. **Hardware.** None. **Traces.** REQ-P0-017.

### TEST-P0-013 — Git workflow documented, repository untouched
**Expected.** `git-workflow.md` covers branches, commit format and scopes, review gates, change isolation, conflict resolution, rollback, prohibited operations; and the workspace still has no `.git`, no `.gitignore` and no hooks created by this phase.
**Failure.** Phase 0 initialised a repository, created a hook, or ran a git state-changing command.
**Automation.** **Yes** — existence check for `.git`/`.gitignore`.
**Hardware.** None. **Traces.** REQ-P0-018.

### TEST-P0-014 — Protocol research workflow documented
**Expected.** Ordered research ladder with enumeration and identification defined before any read-back or write; "never assume a UUID's purpose or a byte's meaning without evidence"; authorization requirement for devices and software analyzed; provenance and confidence recorded for findings.
**Failure.** The ladder permits a write before identification, or omits reconnect verification.
**Automation.** No. **Hardware.** None. **Traces.** REQ-P0-019.

### TEST-P0-015 — Risk register complete
**Expected.** Fourteen risks (prompt §14) each with probability, impact, mitigation, detection strategy, fallback and owner.
**Failure.** A risk with blank mitigation, or a risk whose mitigation contradicts a governance rule.
**Automation.** Partial (field-presence check). **Hardware.** None. **Traces.** REQ-P0-020.

### TEST-P0-016 — No implementation accidentally started
**Purpose.** The phase-failure condition of prompt §23, tested directly.
**Setup.** Final filesystem listing.
**Input.** Every file created during Phase 0.
**Expected.** Only `.md` files exist in the workspace. No `*.kt`, `*.java`, `*.xml`, `build.gradle*`, `settings.gradle*`, `gradlew`, `AndroidManifest.xml`, `.gitignore`, `.env`, no `src/` tree, no installed dependencies, no migrations, no test runs.
**Failure.** Any non-markdown artifact, any stub pretending a subsystem works, or any real UUID/protocol byte table authored as code.
**Automation.** **Yes** — `find` inventory assertion.
**Hardware.** None. **Traces.** REQ-P0-021.

### TEST-P0-017 — Terminology consistency across Phase 0 documents
**Purpose.** Confirm the shared contract held.
**Input.** String scan for `LAB-TESTED`, `HARDWARE-VERIFIED`, `PERSISTENCE-VERIFIED`, `Codec Selected`/`"Selected"` as a state name, `P0-T0NN`, `P0-TEST-0NN`, and any capability/codec/error list shorter than the canonical one.
**Expected.** Every hit is inside an explicitly marked conflict record or quotation of the source prompt; no document *uses* a non-canonical form as a live state name. Error lists contain all thirteen categories where enumerated.
**Failure.** A document adopts a variant spelling or five-state codec ladder as its own vocabulary.
**Automation.** **Yes** — grep with review of hit context (this was executed in TASK-P0-014; all hits were conflict records).
**Hardware.** None. **Traces.** REQ-P0-001, REQ-P0-017.

### TEST-P0-018 — Cross-reference and traceability integrity
**Expected.** Every path referenced from a Phase 0 document exists; every REQ maps to at least one TASK and one TEST; every ADR id referenced from another document exists in `decisions.md`.
**Failure.** A dangling document reference, an orphan requirement, or an ADR cited but not written.
**Automation.** **Yes** — link/id resolution check.
**Hardware.** None. **Traces.** REQ-P0-013, REQ-P0-017.

---

## Results summary

Executed in TASK-P0-014 and TASK-P0-015 on 2026-10-01. Eighteen tests, all PASS at the governance level; two tests (TEST-P0-011, TEST-P0-016) were executed mechanically and their output recorded in `validation.md`. Six tests are judgment-based and were performed by the orchestrator reading the files rather than accepting sub-agent reports.

No Phase 0 test establishes any hardware, transport, protocol or audio capability. Those remain UNTESTED by design; the evidence tiers that will be required are specified in `testing-governance.md`.
