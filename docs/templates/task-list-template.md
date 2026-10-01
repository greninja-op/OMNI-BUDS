<!--
TEMPLATE: task-list.md — copy to docs/phases/phase-<N>/task-list.md
DERIVES: docs/MASTER-CONTEXT.md §44 (task fields + TASK-BT-001..005 example), §49 step 16 ("mark tasks complete only when acceptance criteria pass"),
         §37 sub-agent orchestration, §39 parallelisation; docs/phases/phase-0/execution-prompt.md §10 (owner + expected files + verification fields), §20 ownership boundaries, §21.
COMPLETION RULES:
  R1 ID grammar TASK-<SCOPE>-<NNN>; <SCOPE> matches the phase's REQ scope id. Atomic: one agent, one outcome, executable without invention.
  R2 No blank field: write NONE, or UNKNOWN plus reason.
  R3 Bad (rejected, MASTER-CONTEXT §44): "Implement Bluetooth."  Good: "TASK-BT-002 Implement Android Bluetooth adapter state observer."
  R4 A task may be set to done ONLY when its acceptance criteria pass and its listed tests exist and pass. Compiling code is not completion.
  R5 verified-on-hardware is a separate state from done; real-hardware evidence is required and a mock/lab result never earns it (MASTER-CONTEXT §54).
  R6 Parallelise only independent tasks; two tasks never own the same file at the same time (MASTER-CONTEXT §39, Phase 0 §20).
-->

# Phase `<N>` — Task List

**Phase:** `<N>` — `<phase title>` · **Scope id:** `<SCOPE>` · **Orchestrator:** `<agent>`

## 1. Status legend

| Status | Meaning | Evidence required to enter it |
|---|---|---|
| `pending` | specified, not started | acceptance criteria written |
| `in_progress` | one owner actively working | owning agent named, files locked to that agent |
| `blocked` | cannot proceed | blocking task/REQ id + reason recorded |
| `done` | implementation finished | all acceptance criteria pass; listed tests pass |
| `verified-on-hardware` | proven on a real device | hardware-in-the-loop test passed; device identity + protocol + read-back recorded |
| `deferred` | moved out of this phase | reason + target phase recorded in `validation.md` |

`done` and `verified-on-hardware` are never merged: a task can be `done` and still only `IMPLEMENTED` or `LAB_TESTED` on the verification scale.

## 2. Task record (repeat per task)

```text
TASK-<SCOPE>-<NNN>
Objective:            <single atomic outcome, imperative>
Requirements:         <REQ-<SCOPE>-<NNN>, ...>
Dependencies:         <TASK ids | NONE>  (must already be done or in a prior phase)
Owning agent:         <architecture | bluetooth | protocol-research | audio | testing | security | integration | documentation | qa | orchestrator>
Files / modules:      <paths or module names — exclusive to this owner>
Implementation notes: <constraints, boundaries, gotchas, master sections to preserve; NO invented protocol values>
Tests:                <TEST-<SCOPE>-<NNN> ids | NONE + reason>
Acceptance criteria:  <observable pass/fail statements>
Verification level:   <INFERRED | IMPLEMENTED | LAB_TESTED | HARDWARE_VERIFIED | PERSISTENCE_VERIFIED | UNKNOWN>
Status:               <pending | in_progress | blocked | done | verified-on-hardware | deferred>
```

## 3. Illustrative examples (MASTER-CONTEXT §44 shape — reference only)

```text
TASK-BT-005
Objective:            Add unit tests for adapter state transitions
Requirements:         REQ-BT-001
Dependencies:         TASK-BT-002
Owning agent:         testing
Files / modules:      <test source set for the bluetooth module>
Implementation notes: cover every adapter state edge; assert unknown stays unknown
Tests:                TEST-BT-005
Acceptance criteria:  all adapter state transitions asserted; suite green
Verification level:   IMPLEMENTED
Status:               pending
```

Siblings of the same granularity: `TASK-BT-001` BluetoothManager abstraction, `TASK-BT-002` adapter state observer, `TASK-BT-003` connected-device discovery, `TASK-BT-004` Android-version-specific permission handling.

## 4. Dependency ordering table

| Order | Task | Depends on | Parallel-safe with | Owner | Status |
|---|---|---|---|---|---|
| `<1>` | `TASK-<SCOPE>-<NNN>` | `NONE` | `TASK-<SCOPE>-<NNN>` | `<agent>` | `pending` |

## 5. Rollup

**Tasks:** `<n>` · **done:** `<n>` · **verified-on-hardware:** `<n>` · **blocked:** `<n>` · **deferred:** `<n>` · **Tasks without acceptance criteria:** must be `0` · **Tasks without a REQ link:** must be `0`.

## 6. Phase 0 governance references

- `docs/MASTER-CONTEXT.md` — §44 task fields and atomicity examples, §49 execution contract (step 16), §37 orchestration, §38 sub-agent rules, §39 parallelisation, §54 hardware verification standard.
- `docs/phases/phase-0/execution-prompt.md` — §10 task fields, §20 sub-agent code ownership, §21 review checklist, §23 failure conditions.
- `docs/phases/phase-0/task-list.md` — Phase 0 instance; Phase 0 uses `TASK-P0-<NNN>` for the §10 task set.
- `docs/templates/requirements-template.md`, `docs/templates/test-plan-template.md` — REQ and TEST ids referenced above.
