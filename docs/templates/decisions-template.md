<!--
TEMPLATE: decisions.md — copy to docs/phases/phase-<N>/decisions.md (project-wide decisions also live in docs/decisions/)
DERIVES: docs/MASTER-CONTEXT.md §47 (ADR example), §38 (agent conflict flow), §58 (conflict handling: identify, do not silently overwrite, explain, propose, wait),
         §40 (per-phase doc set); docs/phases/phase-0/execution-prompt.md §3.2 (conflicts recorded, not silently resolved), §5 (orchestrator duties), §13 (ADR list), §23.
COMPLETION RULES:
  R1 ID grammar ADR-<SCOPE>-<NNN>; ids are permanent. An ADR is superseded, never edited into a different decision.
  R2 Every ADR records the master/phase-0 sections it preserves or deliberately changes; changing one requires the conflict path below.
  R3 Status is exactly one of proposed / accepted / superseded.
  R4 An agent that discovers a conflict STOPS and records it here. It must not silently change architecture, project-wide structure, or another agent's work
      (MASTER-CONTEXT §38, §58; Phase 0 §3.2, §5). Sequence: Agent → report conflict → orchestrator evaluates → architecture decision → document decision.
-->

# Phase `<N>` — Architecture Decisions

**Phase:** `<N>` — `<phase title>` · **Scope id:** `<SCOPE>` · **Decision authority:** orchestrator

## 1. ADR record (repeat per decision)

```text
ADR-<SCOPE>-<NNN>
Title:                <short decision statement, active voice>
Status:               <proposed | accepted | superseded>  (date accepted: <YYYY-MM-DD>)
Context:              <the forcing situation: requirement, conflict, platform limit; cite MASTER-CONTEXT §<n> / Phase 0 §<n>>
Decision:             <what the project will do, stated so it can be violated detectably>
Options considered:   <option A — pros — cons> / <option B — pros — cons> / <option C> ; include the rejected ones and why
Consequences:         <positive | negative | neutral, incl. obligations this creates for later phases and UNKNOWNs it leaves>
Affected requirements: <REQ-<SCOPE>-<NNN>, ...>
Affected phases:      <Phase <N>, Phase <m> …>
Supersedes:           <ADR id | NONE>
Superseded by:        <ADR id | NONE>
```

## 2. Illustrative example (from MASTER-CONTEXT §47 — reference shape only)

```text
ADR-BT-001
Title:                OmniBuds does not process media audio
Status:               accepted
Context:              MASTER-CONTEXT §2, §3, §22: re-encoding would degrade quality, latency, codec behaviour, battery and stability,
                      and would blur hardware DSP with phone-side DSP.
Decision:             Keep OmniBuds outside the media path; operate only on a control/configuration channel.
Options considered:   (a) in-line audio processing — rejected: quality/latency/battery cost, risks fake hardware features;
                      (b) bypass media path, control channel only — accepted; (c) selective processing — rejected: needs a clearly labelled
                      software-feature rule that the project has not specified yet.
Consequences:         Codec negotiation stays native; hardware features must be driven by vendor protocol; software features must be
                      explicitly labelled as software.
Affected requirements: REQ-BT-005 (unknown values are not fabricated) and the audio-path requirements of the audio phases
Affected phases:      Audio transport, codec capability and DSP-separation phases; desktop phases inherit the same boundary
Supersedes:           NONE
Superseded by:        NONE
```

## 3. Conflict and escalation register

| Conflict observed | Observed by (agent) | Master/ADR section at risk | Resolution path taken | ADR id | Outcome |
|---|---|---|---|---|---|
| `<what contradicted what>` | `<agent>` | `<§n / ADR-…>` | `reported → orchestrator evaluated → decision documented` | `ADR-<SCOPE>-<NNN>` | `<accepted / deferred pending confirmation>` |

If a change materially affects the project, the agent explains it, proposes the architectural change, and waits for confirmation (MASTER-CONTEXT §58).

## 4. Decision index

| ADR id | Title | Status | Phase |
|---|---|---|---|
| `ADR-<SCOPE>-001` | `<…>` | `proposed` | `<N>` |

## 5. Phase 0 governance references

- `docs/MASTER-CONTEXT.md` — §47 decisions contents and ADR example, §38 sub-agent rules and conflict flow, §58 final conflict-handling instruction, §40 phase doc set, §51 quality rules (silent architecture change is prohibited).
- `docs/phases/phase-0/execution-prompt.md` — §3.2 conflicts recorded in `docs/decisions/`, §5 orchestrator responsibilities, §13 the Phase 0 ADR list (`ADR-P0-001`…`ADR-P0-010`), §23 documentation contradicting the master without an ADR is a failure.
- `docs/phases/phase-0/decisions.md` — Phase 0 instance of this template.
- `docs/templates/requirements-template.md`, `docs/templates/design-template.md` — where ADR ids are traced.
