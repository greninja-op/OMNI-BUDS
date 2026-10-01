# OMNIBUDS — PHASE 0 · GIT WORKFLOW AND REPOSITORY RULES

**Rule id grammar:** `GIT-<NNN>`. **Sources of authority:** `docs/MASTER-CONTEXT.md` (cited as "master") and `docs/phases/phase-0/execution-prompt.md` (cited as "P0 prompt"). Where the two disagree, master wins and the disagreement is recorded in §12 rather than silently resolved (master §58). **Companion rule file:** `docs/phases/phase-0/sub-agent-orchestration.md`. Paths are repo-root-relative. **This document is a ruleset, not an operation:** writing it executed no git command beyond confirming the absence of a repository (§1).

---

## 1. Current repository state — stated honestly

| Fact | Verified position (recorded 2026-10-01) |
| --- | --- |
| Git repository | **Does not exist.** No `.git` in the workspace or in any ancestor directory. |
| Branches / history | None. There is no `main`, no `develop`, no commit to point at. |
| Remote | None configured. |
| CI | None (master "Repository status"; P0 prompt §3.1 inspection). |
| `.gitignore` | Absent. |
| Build files / Gradle / Kotlin sources | Absent (master "Repository status": no code, no Gradle config, no dependencies, no modules). |
| Actual content | Documentation only at inspection time: `docs/MASTER-CONTEXT.md`, `docs/phases/phase-0/execution-prompt.md`, and the documentation directories (`architecture/`, `audio/`, `bluetooth/`, `decisions/`, `phases/phase-0/`, `protocols/`, `requirements/`, `security/`, `templates/`, `testing/`) — which the concurrent Phase 0 workstreams populate; nothing in them is under version control. |

- **GIT-001** — Every agent working on OmniBuds must describe the repository as **documentation-only and not yet under version control**. Claiming a branch, a commit, a PR, a review gate or a CI run for Phase 0 is a false statement of fact and is a P0 prompt §23 failure mode ("implementation begins accidentally" applied to repository machinery).
- **GIT-002** — All rules in §3 through §11 are **requirements on the repository that Phase 1 creates**, not descriptions of present machinery. They bind the first commit onward and bind planning behaviour (file-disjoint ownership) immediately.
- **GIT-003** — Phase 0 outputs are governed by the *documentation* rules of this file (GIT-016, GIT-041 to GIT-044) and by ownership rules in the companion file; they cannot be governed by PR mechanics that do not exist.

## 2. Deferral of `git init`, remote and CI to Phase 1

- **GIT-004** — **Phase 0 must not run `git init`.** It must not create `.git`, `.gitignore`, hooks, workflows, CI configuration or any remote linkage.
- **GIT-005 (reason)** — Initialising version control mutates project-wide state and fixes history that later phases cannot un-write. Master "Repository status" and §57 withhold all phase work except under an explicit execution prompt, and P0 prompt §2 restricts Phase 0 to governance; version-control initialisation is therefore **deferred to Phase 1 as an explicit, user-approved step**, alongside the Gradle/KMP foundation Phase 1 owns.
- **GIT-006** — The Phase 1 task-list must contain, as separately authorised tasks, at minimum: (a) repository initialisation and first commit of the existing documentation tree; (b) `.gitignore` per §10; (c) branch model per §3; (d) remote setup; (e) CI definition. Each must cite its `TASK-<SCOPE>-<NNN>` id and none may be collapsed into "set up project".
- **GIT-007** — Until GIT-006(a)–(c) are done, change isolation for parallel agents relies on **disjoint file lists in the agent brief** (companion file ORCH-016) rather than on branches or worktrees. An agent that cannot isolate by branch must not start.
- **GIT-008** — Deferral is not a licence to skip the rules: the first Phase 1 commit must already satisfy §4, so that no "history clean-up" (an operation §8 prohibits) is ever needed.

## 3. Branch strategy and naming

| Pattern | Example (P0 prompt §19) | Rule |
| --- | --- | --- |
| Trunk / released | `main` | Holds tagged, validated states only (GIT-030). |
| Integration | `develop` | Holds merged, phase-complete work. Never a place for direct authoring. |
| Phase branch | `phase/0-foundation` | `phase/<N>-<slug>`; one branch per phase, created from `develop`. |
| Feature | `feature/bluetooth-foundation`, `feature/device-fingerprint`, `feature/audio-codec-engine` | `feature/<slug>`; created from the phase branch it belongs to. |
| Fix | `fix/gatt-timeout` | `fix/<slug>`; created from the branch that carries the defective work. |

- **GIT-009** — Use exactly the prefixes above. Introducing any other prefix (`hotfix/`, `release/`, `chore/`) is an architecture-of-workflow change and requires an ADR first.
- **GIT-010** — **One phase's work stays on its own branch.** A branch must not carry work belonging to two phases, and a `feature/*` or `fix/*` branch merges only into its own phase branch, never into `main`.
- **GIT-011** — Branch slugs are lowercase kebab-case and must be traceable to a `TASK-<SCOPE>-<NNN>` id in that phase's `task-list.md`.
- **GIT-012** — **One task maps to reviewable commits.** A task's deliverable must be expressible as one or more commits that a reviewer can read against the task's acceptance criteria; if it cannot, the task violates master §44 atomicity and must be split before work starts.
- **GIT-013** — Long-lived branches are forbidden: a branch lives until its task set is merged, then its deletion follows §8 (deletion is a prohibited operation without authorisation).

## 4. Commit conventions

**GIT-014** — Format: `<type>(<scope>): <imperative summary>`, summary ≤ 72 characters, body states *why* and cites ids (`TASK-`, `REQ-`, `TEST-`, `ADR-`). Permitted types: `feat`, `fix`, `docs`, `test`, `refactor`. Adding a type requires an ADR.

| Scope | Owned territory (basis: P0 prompt §20, master §8–§15, §41, §56) |
| --- | --- |
| `core` | Domain model, device fingerprint, session model, state types (master §5–§7) |
| `bluetooth` | Adapter state, discovery, Android Bluetooth boundaries (master §4) |
| `transport` | Transport abstraction and implementations: GATT, RFCOMM, Classic, LE Audio (master §8) |
| `protocol` | Vendor protocol abstraction, definitions, parsers/encoders (master §9, §52) |
| `audio` | Audio-path rules, codec registry, quality state (master §3, §14–§21) |
| `capability` | Capability engine and its states (master §10) |
| `persistence` | Persistence and persistence-verification logic (master §24) |
| `ui` | Capability-driven UI, Quick Settings, notification, widget (master §11, §32–§34) |
| `docs` | Documentation, templates, governance files; `docs(phase-0)` is also valid — see GIT-046 |
| `test` | Test architecture, protocol/lab tests, verification suites (master §36) |

- **GIT-015** — Meaningless messages are prohibited, verbatim from P0 prompt §19: `update`, `fix` (as a whole message), `changes`, `stuff`. A message that does not name what changed and in which scope is rejected at review.
- **GIT-016** — A commit that **alters architecture must carry or link an ADR**: either the ADR is added in the same commit under `docs/phases/phase-<N>/decisions.md` / `docs/decisions/`, or the message links an existing `ADR-<SCOPE>-<NNN>` id. Silence is prohibited by master §48, §58 and P0 prompt §18 ("no silent architecture changes").
- **GIT-017** — Architecture-affecting is defined as: any change to module boundaries, dependency direction (P0 prompt §8.2), Android/KMP boundaries (§8.4), transport or protocol abstraction shape, capability/codec/verification state enumerations, persistence rules, or error taxonomy (master §30).
- **GIT-018** — Doc-only commits are first-class: `docs(phase-0): define hardware verification rules` is a valid commit (P0 prompt §19). Mixing doc and code in one commit is allowed only when the doc exists to describe exactly that code change.
- **GIT-019** — Commits must not contain generated artefacts, captured device data, credentials or local IDE settings (see §10).

## 5. Change isolation rules for sub-agent output

- **GIT-020** — **One agent = one branch = one working tree.** Where the environment supports git worktrees, each parallel agent gets its own worktree checked out to its own branch; agents must never share a checkout, because a shared working directory silently merges two agents' uncommitted edits.
- **GIT-021** — Deliverables assigned to concurrently running agents must be **file-disjoint** (master §39 "Bad parallelization"; P0 prompt §4). Branch ownership alone is not isolation: two branches over the same file produce the clobber master §39 forbids.
- **GIT-022** — Where two agents need the same file, the **proposal-then-integrate path of P0 prompt §20 is mandatory**: the requesting agent writes a *proposal* (its intended edit, described in its deliverable or a dedicated file), the orchestrator holds the proposal, and the owning agent or the integration agent applies it. Concurrent uncontrolled edits are prohibited.
- **GIT-023** — Ownership is per file/directory, published in the companion file's role catalogue. An agent may edit only files it owns plus files explicitly listed in its brief as its deliverables; everything else it touches only through GIT-022.
- **GIT-024** — Agents must not reformat, rename, move or "tidy" any file outside their deliverable set, and must not run whole-tree formatters or generate bulk diffs. A diff that is larger than its task is a review failure.
- **GIT-025** — Integration is a single role's job (companion ORCH-024). No agent merges its own branch into the phase branch; the integrating agent resolves conflicts under §7, and may not invoke any shortcut §7 prohibits.
- **GIT-026** — Cross-phase isolation: a Phase N agent must not open a Phase N+1 file even speculatively; forward-looking needs become a `RISK-<NNN>` entry or an ADR candidate, not an edit.

## 6. Pull-request expectations and review gates

**GIT-027** — Every PR body must state: objective; `TASK-<SCOPE>-<NNN>` ids; `REQ-<SCOPE>-<NNN>` ids satisfied; files touched; owning role; tests run *and* tests deferred with hardware reasons (master §36, §45); documentation files updated; ADR link if GIT-016 applies.

| Gate | Requirement | Source |
| --- | --- | --- |
| G1 Acceptance | Each task's acceptance criteria demonstrably met, not "code compiles" | master §46 |
| G2 Tests | Phase `test-plan.md` entries executed or explicitly deferred as hardware-dependent | master §45 |
| G3 Docs | `task-list.md` status, `validation.md`, and affected specs/design docs updated | GIT-041 |
| G4 Ownership | Diff touches no file outside the author role's ownership | GIT-023 |
| G5 Scope | **Diff contains no work from a phase other than the authorised one, and no implementation at all during a governance phase** | P0 prompt §21, §23 |
| G6 Terminology | State names match the shared contract verbatim | companion ORCH-010 |

- **GIT-028** — G5 is the reviewer's explicit duty: the reviewer confirms that **no implementation started outside the authorised phase** (P0 prompt §21 last checklist item; §23 failure list). For a Phase 0 PR the answer must be "no source file, no Gradle file, no Android manifest, no git machinery appears in the diff".
- **GIT-029** — A PR is approved by the owning role plus one other role it interacts with; disagreement escalates to the orchestrator, which decides by ADR rather than by picking a side silently (master §38).
- **GIT-030** — Merge granularity preserves the task→commit mapping of GIT-012; blanket squash that erases task attribution requires the orchestrator's approval.

## 7. Conflict resolution policy

- **GIT-031** — **Resolve, never discard.** A conflict resolution must incorporate both sides' intent into a single coherent result. Wholesale adoption of one side — including blanket "checkout over a collaborator's work", `checkout .`-style resets of a directory, or deleting a file because it conflicts — is prohibited.
- **GIT-032** — **Never delete work you did not author.** Removing another agent's or another phase's content requires that author's acknowledgement and the orchestrator's decision, recorded in the phase's `decisions.md`.
- **GIT-033** — Existing work found in the repository is preserved and documented, not rewritten or replaced (P0 prompt §3.2); if it contradicts master, that contradiction is recorded in `docs/decisions/` — not "fixed" by deletion.
- **GIT-034** — **Architecture-level conflicts escalate and stop.** Two correct-looking local resolutions that change a boundary, a dependency direction, or a state enumeration are architecture-level: the agent reports, the orchestrator evaluates, an architecture decision is made, and it is documented as `ADR-<SCOPE>-<NNN>` (master §38 flow; master §58).
- **GIT-035** — Unresolved conflicts are recorded, not swallowed: they appear in the phase `validation.md` "Issues" field, and become `RISK-<NNN>` entries when they can affect a later phase.

## 8. Rollback strategy and prohibited operations

- **GIT-036** — **Revert, do not reset, for anything published.** Published work (on `develop`, on `main`, or on any branch another agent has built on) is undone with a revert commit that preserves history and task attribution. Rewriting published history is never a rollback mechanism.
- **GIT-037** — Only genuinely unpublished, local, single-author commits may be reworked before they are shared.
- **GIT-038** — Rollback is minimal: revert the smallest commit set that restores the broken invariant, link the revert to the original `TASK-<SCOPE>-<NNN>`, and add or extend a `TEST-<SCOPE>-<NNN>` so the defect cannot return silently (master §49 step 14 regression checks).
- **GIT-039** — **Prohibited without direct user authorisation for the specific operation:** force push; hard reset; `clean` of untracked or ignored files; branch deletion; amending commits that have been published; skipping hooks (`--no-verify` or equivalent); bypassing or disabling commit signing. These are prohibited as operations, and no agent may request blanket standing authorisation.
- **GIT-040** — Today there are no hooks, no signing configuration and no CI (GIT-001). GIT-039's prohibitions bind from the moment Phase 1 introduces those mechanisms; the absence of a mechanism is not permission to plan to bypass it.

## 9. Documentation updates accompanying code changes

- **GIT-041** — A code change lands with the documentation it makes true: `requirements.md` (if requirement scope moved), `design.md`, `specs.md`, `task-list.md` status, `test-plan.md`, and `validation.md` of the owning phase (master §40, §46).
- **GIT-042** — Documentation that contradicts master without an ADR is a phase failure (P0 prompt §23). The commit is not done until the contradiction is either removed or justified by a linked `ADR-<SCOPE>-<NNN>`.
- **GIT-043** — A terminology change propagates in the same commit to every document that uses it. Partial renames leave parallel agents writing inconsistent output (companion ORCH-010).
- **GIT-044** — Never mark a feature complete because it compiles (master §46); docs must state implemented, not implemented, tests passed, tests failed, known/platform/hardware limitations, and deferred work.

## 10. `.gitignore` — requirements for Phase 1 (not created now)

**GIT-045** — Phase 1 authors `.gitignore` **before** the first commit that could include generated content. Required coverage, expressed as requirements:

| Category | Must be excluded | Reason |
| --- | --- | --- |
| Build output | Gradle build directories, intermediate artifacts, logs | Reproducible; machine-generated |
| IDE / editor files | Local workspace and editor settings | Per-machine, not project truth (P0 prompt §3.1) |
| Local secrets | Credentials, keys, signing material, local config with secrets | Never committed, even when ignored (P0 prompt §4 Agent 4) |
| Captured device data | Packet captures, hex dumps, GATT/RFCOMM recordings, protocol logs | Contains real MAC/identifiers; master §6 forbids retaining identifiers without reason; §26 lab output is research data, not source |

- **GIT-046** — `.gitignore` must not exclude any file that is project truth: `docs/`, phase documentation, ADRs, protocol *descriptions* (parsed, documented forms per master §52), and test plans stay tracked. Ignoring a category is not a licence to store secrets locally in a tracked-adjacent path without documenting where they live.

## 11. Phase tags and release convention

- **GIT-047** — Each completed phase receives one annotated tag on the integration commit that carries it into `develop`. Tag form: `phase-<N>-<slug>`, e.g. `phase-1-foundation`; tag message names the phase, its validation result, and its open limitations.
- **GIT-048** — **A tag is created only after that phase's `validation.md` passes.** No validation file, a FAIL, or a phase with unrecorded incomplete acceptance criteria means no tag (master §46; §49 step 16; P0 prompt §12).
- **GIT-049** — A partially completed phase is not tagged; it is merged with its limitations recorded, or held. Deferred work is stated in `validation.md`, not implied by a missing tag.
- **GIT-050** — Tags are immutable: they are never moved, deleted, or re-created — that path is prohibited by GIT-039. Product releases are a Phase 51 concern (master §48, "Release Engineering"); phase tags are governance milestones, not releases.

## 12. Conflicts surfaced between the two sources

| # | Conflict | Precedence applied here |
| --- | --- | --- |
| C1 | P0 prompt §4 assigns Agent 7's output to `docs/phase-0/git-workflow.md`, while §6 and master §56/§40 place phase documentation at `docs/phases/phase-0/`. | Master wins: this file lives at `docs/phases/phase-0/git-workflow.md`. Flag for the orchestrator to reconcile the prompt's path mentions. |
| C2 | P0 prompt §19's own commit example uses scope `phase-0`; the enumerated scope set for this project is subsystem-scoped. | Kept both: `docs` commits may take a subsystem scope or a `phase-<N>` scope (GIT-014 scope table, GIT-018). Recorded so a later ADR can formalise it. |
| C3 | P0 prompt §19 exemplifies types `feat`, `docs`, `test`; `fix` appears only as a branch prefix. | `fix` is admitted as a type because `fix/gatt-timeout` implies defective-work commits, and GIT-015 bans the bare word `fix` as a message — the two readings are reconciled, not weakened. |
| C4 | P0 prompt §19 mandates branch naming and commits but is silent on whether a repository exists; master "Repository status" says nothing has been started. | Read as: §19 is a ruleset for the repository Phase 1 creates (§2). No `git init` in Phase 0 (GIT-004). |
| C5 | P0 prompt §10 numbers tasks `P0-T001`, while master §44 uses `TASK-<SCOPE>-<NNN>` (`TASK-BT-001`) and the project ID grammar requires scope + three digits. | Master wins: ids of record are `TASK-P0-001`-style; `P0-T001` is treated as a legacy alias only inside the P0 prompt itself. |

## 13. Related documents

- `docs/phases/phase-0/sub-agent-orchestration.md` — ownership, parallelisation, brief template, escalation; owns the "who may edit" half of §5 and §6 of this file.
- `docs/phases/phase-0/requirements.md` — `REQ-P0-<NNN>` entries that this file must satisfy; `docs/phases/phase-0/task-list.md` — `TASK-P0-<NNN>` entries for GIT-006's Phase 1 hand-off; `docs/phases/phase-0/test-plan.md` — `TEST-P0-<NNN>` checks for §4 and §6 gates; `docs/phases/phase-0/validation.md` — the gate for GIT-048; `docs/phases/phase-0/decisions.md` — home of the `ADR-P0-<NNN>` records required by GIT-016 and GIT-034.
- `docs/MASTER-CONTEXT.md` §37–§40, §44, §46, §48–§51, §56, §58.
