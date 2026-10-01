<!--
TEMPLATE: validation.md — copy to docs/phases/phase-<N>/validation.md, filled at phase close (not before).
DERIVES: docs/phases/phase-0/execution-prompt.md §12 (exact section order and headings, "Ready: YES/NO"), §24 (phase report), §25 (stop condition);
         docs/MASTER-CONTEXT.md §46 (implemented / not implemented / tests passed / tests failed / known, platform and hardware limitations / deferred work,
         "never mark a feature complete merely because code compiles"), §49, §50, §54, §55.
RECONCILIATION: §12 defines the heading order used below; §46's extra fields are kept as mandatory sub-items (Implemented, Not implemented, Tests passed,
         Tests failed, Platform limitations, Hardware limitations) so neither source loses a field.
COMPLETION RULES:
  R1 Never mark a feature complete because it compiles, is written, or passes a mock. Completeness = acceptance criteria met at the stated evidence level.
  R2 Every requirement is listed as completed or incomplete; the two counts must equal the REQ total in requirements.md.
  R3 Capability claims are capped at the highest level actually evidenced; unevidenced = UNKNOWN.
  R4 Stop at the phase boundary: no next-phase work started or implied (MASTER-CONTEXT §50).
-->

# Phase `<N>` — Validation

**Phase:** `<N>` — `<phase title>`
**Status:** `<complete | complete-with-deferrals | partial | failed>` · **Scope id:** `<SCOPE>` · **Closed by:** `<orchestrator>` · **Date:** `<YYYY-MM-DD>`

## Requirements completed

`<REQ-<SCOPE>-<NNN> — <title> — evidence level achieved>` one per line; count must match `requirements.md`.

## Requirements incomplete

`<REQ-<SCOPE>-<NNN> — reason — blocking task/risk id — target phase>` one per line. `UNKNOWN` is a valid reason; a silent omission is not.

## Documentation created

| Document | Path | Status | Traceable to REQ ids |
|---|---|---|---|
| `<requirements.md>` | `docs/phases/phase-<N>/<file>` | `<present / partial / absent>` | `<…>` |

## Architecture decisions

`ADR-<SCOPE>-<NNN> — <title> — <status>` per line, each recorded in `decisions.md`; note any decision that changed the master architecture and how the conflict was escalated.

## Validation performed

- **Implemented:** `<what actually exists>`
- **Not implemented:** `<what does not, and why>`
- **Tests passed:** `TEST-<SCOPE>-<NNN>` ids + tier + evidence level
- **Tests failed:** `TEST-<SCOPE>-<NNN>` ids + failure condition observed
- **Tests not run / blocked:** ids + reason (never reported as passed)
- **Documentation checks:** `<governance assertions verified this phase>`

## Issues

`<issue — severity — symptom — affected REQ/TASK — owner — disposition>` one per line; unresolved architectural conflicts recorded here and in `decisions.md`, never absorbed silently.

## Known limitations

`<limitation — affected requirement — evidence level>` — includes anything unverified; unverified is stated as unverified, not as unsupported.

## Platform limitations

`<Android/OEM/OS restriction — what the platform does not permit — affected requirement>` — "never promise functionality that Android does not actually permit" (MASTER-CONTEXT §4).

## Hardware limitations

`<device / firmware / protocol variance — which devices are verified, which are not, which are UNKNOWN>` reported per device and per capability (verified / not verified / unsupported), never as blanket vendor support (MASTER-CONTEXT §55).

## Deferred work

`<item — target phase — reason>`; each deferral must be visible in the next phase's requirements or in an ADR.

## Ready for next phase

**YES / NO** — justify in one paragraph. `YES` requires: all MUST requirements completed or explicitly deferred with an owner, no untraced REQ, no conflation of `LAB_TESTED` with `HARDWARE_VERIFIED`, and the Phase 0 §21-style review checklist ticked.

Then stop and report at the phase boundary; wait for an explicit next-phase prompt (MASTER-CONTEXT §50; Phase 0 §25).

## Phase 0 governance references

- `docs/MASTER-CONTEXT.md` — §46 validation contents and "code compiles is not complete", §49 execution contract, §50 no automatic phase progression, §54 hardware verification standard, §55 no false feature parity, §40 phase doc set.
- `docs/phases/phase-0/execution-prompt.md` — §12 validation document shape, §21 orchestrator review checklist, §22 acceptance criteria, §24 final report, §25 stop condition.
- `docs/phases/phase-0/validation.md` — Phase 0 instance of this template.
- `docs/templates/requirements-template.md`, `docs/templates/test-plan-template.md`, `docs/templates/decisions-template.md` — sources for the counts, evidence, and ADR ids cited above.
