# Phase 0 — Task List

ID grammar: `TASK-P0-<NNN>` (the prompt's `P0-TNNN` shorthand is normalised by ADR-P0-013).
Status legend: `done` · `in-progress` · `blocked` · `not-started`. A task is `done` only when its verification passes, not when a file merely exists.

## Workstream partition

The phase was divided into file-disjoint workstreams so no two writers touched the same document. The orchestrator kept the record set (requirements, design, specs, task list, test plan, decisions, risks, audit, validation) because those are the integration points.

| Workstream | Owner role | Files owned | Parallelised with |
|---|---|---|---|
| W1 Audit & records | Orchestrator | `repository-audit.md`, `requirements.md`, `design.md`, `specs.md`, `task-list.md`, `test-plan.md`, `decisions.md`, `risk-register.md`, `validation.md` | W2–W7 |
| W2 Architecture | Architecture Governance Agent | `architecture-governance.md` | yes |
| W3 Testing/QA | Testing & QA Agent | `testing-governance.md` | yes |
| W4 Security/privacy | Security & Safety Agent | `security-governance.md` | yes |
| W5 Audio | Audio Architecture Agent | `audio-governance.md` | yes |
| W6 Protocol | Protocol Governance Agent | `protocol-governance.md` | yes |
| W7 Process | Git/Workflow Agent | `git-workflow.md`, `sub-agent-orchestration.md` | yes |
| W8 Templates | Documentation Agent | `docs/templates/` × 8 | yes |

Interface note: W2 and W6 both describe the capability and transport abstractions. The shared contract is this file set plus `specs.md` §2–3, which every writer was required to use verbatim; the orchestrator reconciled wording during TASK-P0-014 rather than letting either workstream edit the other's file.

---

## Tasks

### TASK-P0-001 — Inspect repository
**Objective.** Establish ground truth before changing anything (prompt §3).
**Dependencies.** None. **Owner.** Orchestrator (W1).
**Expected files.** `repository-audit.md`.
**Implementation notes.** Enumerated source, build files, Gradle/AGP/Kotlin versions, modules, git state, tests, CI, README, `.gitignore`, IDE and agent configuration, prior decisions.
**Acceptance criteria.** Every item in prompt §3.1 has a recorded finding; "not present" is recorded as not present rather than assumed.
**Verification.** TEST-P0-016 inspection + orchestrator read-back. **Status.** `done`

### TASK-P0-002 — Document existing architecture
**Objective.** Record what already exists so nothing is silently replaced (prompt §3.2).
**Dependencies.** TASK-P0-001. **Owner.** Orchestrator, with W2.
**Expected files.** `repository-audit.md` §2–4, `architecture-governance.md`.
**Acceptance criteria.** No pre-existing implementation found is stated explicitly; no existing behavior was deleted, rewritten or re-architected.
**Verification.** TEST-P0-009. **Status.** `done`

### TASK-P0-003 — Create documentation structure
**Objective.** Create the directory architecture required by prompt §6.
**Dependencies.** TASK-P0-001. **Owner.** Orchestrator.
**Expected files.** `docs/{architecture,requirements,protocols,bluetooth,audio,testing,security,decisions,templates,phases/phase-0}` plus `docs/product/` (ADR-P0-019).
**Acceptance criteria.** Structure matches prompt §6; phase directory resolves the §4/§6 path conflict per ADR-P0-011.
**Verification.** TEST-P0-011. **Status.** `done`

### TASK-P0-004 — Create documentation templates
**Objective.** Supply the eight reusable phase templates (prompt §4 Agent 6).
**Dependencies.** TASK-P0-003. **Owner.** Documentation Agent (W8).
**Expected files.** `docs/templates/{requirements,design,specs,task-list,test-plan,validation,decisions,risk-register}-template.md`.
**Acceptance criteria.** Each template carries every mandatory field from master §41–47 and prompt §7–14; examples come from master text, not invention; phase-agnostic.
**Verification.** TEST-P0-011. **Status.** `done`

### TASK-P0-005 — Document capability truth model
**Objective.** Specify the six-state capability model, metadata, transitions and the UI consequence.
**Dependencies.** TASK-P0-003. **Owner.** Protocol Governance Agent (W6).
**Expected files.** `protocol-governance.md` (capability sections), `architecture-governance.md` (boundary rules).
**Acceptance criteria.** REQ-P0-002 and REQ-P0-006 acceptance criteria met; `UNKNOWN` never collapsed into `UNSUPPORTED`.
**Verification.** TEST-P0-001, TEST-P0-002. **Status.** `done`

### TASK-P0-006 — Document device session lifecycle
**Objective.** Specify active session versus saved device, transitions and disconnect behavior.
**Dependencies.** TASK-P0-005. **Owner.** Protocol Governance Agent (W6), `security-governance.md` for retention.
**Acceptance criteria.** REQ-P0-004 acceptance criteria met.
**Verification.** TEST-P0-010. **Status.** `done`

### TASK-P0-007 — Document audio-path isolation
**Objective.** Fix the rule that OmniBuds stays outside the media path.
**Dependencies.** TASK-P0-003. **Owner.** Audio Architecture Agent (W5).
**Acceptance criteria.** REQ-P0-003 criteria; diagrams reproduced; in-path proposal conditions stated.
**Verification.** TEST-P0-006. **Status.** `done`

### TASK-P0-008 — Document codec-state semantics
**Objective.** Define the six-state codec model, registry, quality-state fields and OS restrictions.
**Dependencies.** TASK-P0-007. **Owner.** Audio Architecture Agent (W5).
**Acceptance criteria.** REQ-P0-009, REQ-P0-010, REQ-P0-011 criteria; worked examples include "LDAC supported, AAC active".
**Verification.** TEST-P0-003. **Status.** `done`

### TASK-P0-009 — Document persistence verification
**Objective.** Specify the full read-back-plus-reconnect ladder and what each rung proves.
**Dependencies.** TASK-P0-005. **Owner.** Protocol Governance Agent (W6).
**Acceptance criteria.** REQ-P0-007 criteria; a successful write alone never yields `SUPPORTED_PERSISTENT`.
**Verification.** TEST-P0-004. **Status.** `done`

### TASK-P0-010 — Document unknown-device safety
**Objective.** Specify read-only default, safe discovery set and prohibitions.
**Dependencies.** TASK-P0-005. **Owner.** Security & Safety Agent (W4).
**Acceptance criteria.** REQ-P0-008 criteria; explicit bans on arbitrary writes, fuzzing, undocumented commands.
**Verification.** TEST-P0-005. **Status.** `done`

### TASK-P0-011 — Document sub-agent orchestration and phase boundaries
**Objective.** Specify roles, ownership, briefs, parallelisation, escalation, the 18-step execution contract and the stop rule.
**Dependencies.** TASK-P0-003. **Owner.** Git/Workflow Agent (W7).
**Acceptance criteria.** REQ-P0-014, REQ-P0-015 criteria.
**Verification.** TEST-P0-008. **Status.** `done`

### TASK-P0-012 — Document Git workflow
**Objective.** Specify branches, commits, review gates, isolation, conflict and rollback rules without creating a repository.
**Dependencies.** TASK-P0-001. **Owner.** Git/Workflow Agent (W7).
**Acceptance criteria.** REQ-P0-018 criteria; the not-yet-a-repository condition stated honestly.
**Verification.** TEST-P0-013. **Status.** `done`

### TASK-P0-013 — Create Phase 0 risk register
**Dependencies.** TASK-P0-005, TASK-P0-008, TASK-P0-010. **Owner.** Orchestrator.
**Expected files.** `risk-register.md`.
**Acceptance criteria.** REQ-P0-020 criteria: all fourteen risks with all six fields.
**Verification.** TEST-P0-015. **Status.** `done`

### TASK-P0-014 — Perform consistency review
**Objective.** Detect contradictions across the eight governance documents; enforce one terminology; confirm nothing was silently re-architected (prompt §5).
**Dependencies.** TASK-P0-004 … TASK-P0-013. **Owner.** Orchestrator.
**Implementation notes.** Read back every agent file from disk rather than accepting agent reports; string-level check for terminology drift; cross-reference existence check; conflict ledger consolidated into `decisions.md`.
**Acceptance criteria.** TEST-P0-017 and TEST-P0-018 pass; every conflict found by any workstream appears exactly once in `decisions.md` with a resolution.
**Verification.** Orchestrator review + TEST-P0-017/018. **Status.** `done`

### TASK-P0-015 — Perform Phase 0 validation
**Dependencies.** TASK-P0-014. **Owner.** Orchestrator.
**Expected files.** `validation.md`.
**Acceptance criteria.** Prompt §12 shape complete; prompt §21 checklist walked item by item; Phase 1 readiness stated with reasons.
**Verification.** TEST-P0-016 plus manual review. **Status.** done — see `validation.md`

### TASK-P0-016 — Persist the master and phase prompts into the repository
**Objective.** Remove dependence on ephemeral session attachment paths.
**Dependencies.** None. **Owner.** Orchestrator.
**Expected files.** `docs/MASTER-CONTEXT.md`, `docs/phases/phase-0/execution-prompt.md`.
**Acceptance criteria.** Both prompts stored verbatim apart from an added status banner; master not otherwise edited.
**Verification.** Diff read-back. **Status.** `done`

### TASK-P0-017 — Author project-wide specifications
**Objective.** Fix naming, state, error, coroutine and retry conventions (prompt §9).
**Dependencies.** TASK-P0-003. **Owner.** Orchestrator.
**Expected files.** `specs.md`.
**Acceptance criteria.** REQ-P0-017 criteria; the `null`-versus-`UNKNOWN` tension resolved by tiering per ADR-P0-016.
**Verification.** TEST-P0-012. **Status.** `done`

### TASK-P0-018 — Document protocol research workflow and verification statuses
**Objective.** Specify the eleven-step research ladder, the five verification levels, and the per-device support report format.
**Dependencies.** TASK-P0-005. **Owner.** Protocol Governance Agent (W6).
**Expected files.** `protocol-governance.md`, `security-governance.md` (authorization).
**Acceptance criteria.** REQ-P0-019 criteria; levels never conflated; research starts read-only.
**Verification.** TEST-P0-014. **Status.** `done`

### TASK-P0-019 — Record conflict resolutions as ADRs
**Objective.** Convert every surfaced master-versus-prompt conflict into a numbered, justified decision.
**Dependencies.** TASK-P0-014. **Owner.** Orchestrator.
**Expected files.** `decisions.md`, `docs/decisions/README.md`.
**Acceptance criteria.** No conflict remains only in prose; nothing resolved silently; items needing user confirmation flagged as proposed rather than accepted.
**Verification.** TEST-P0-009. **Status.** `done`

---

## Summary

19 / 19 tasks `done`. All deliverables are markdown documents; no task in this phase produced or required source code, and none was marked done on the basis of a file existing rather than its acceptance criteria passing.
