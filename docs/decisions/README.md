# Architecture decision index

Full text of every ADR lives in the phase record that authored it. This file is the index and the status tracker — it deliberately does not restate decisions, so there is only ever one place to correct when a decision changes.

| ADR | Title | Status | Phase record |
|---|---|---|---|
| ADR-P0-001 | OmniBuds does not simulate hardware | accepted | `docs/phases/phase-0/decisions.md` |
| ADR-P0-002 | OmniBuds stays outside the media audio path | accepted | `docs/phases/phase-0/decisions.md` |
| ADR-P0-003 | Transport abstraction is required | accepted | `docs/phases/phase-0/decisions.md` |
| ADR-P0-004 | Capabilities are richer than boolean values | accepted | `docs/phases/phase-0/decisions.md` |
| ADR-P0-005 | Persistent writes require reconnect verification | accepted | `docs/phases/phase-0/decisions.md` |
| ADR-P0-006 | Unknown devices start read-only | accepted | `docs/phases/phase-0/decisions.md` |
| ADR-P0-007 | Vendor-specific capabilities remain accessible | accepted | `docs/phases/phase-0/decisions.md` |
| ADR-P0-008 | Android first; KMP compatibility is an architectural target | accepted | `docs/phases/phase-0/decisions.md` |
| ADR-P0-009 | Phases do not automatically advance | accepted | `docs/phases/phase-0/decisions.md` |
| ADR-P0-010 | Sub-agents are orchestrated through explicit ownership boundaries | accepted | `docs/phases/phase-0/decisions.md` |
| ADR-P0-011 | Phase documents live in `docs/phases/phase-<N>/` | accepted | `docs/phases/phase-0/decisions.md` |
| ADR-P0-012 | Error category set is the master's thirteen | accepted | `docs/phases/phase-0/decisions.md` |
| ADR-P0-013 | Identifier grammar normalised to `TASK-`/`TEST-<SCOPE>-<NNN>` | accepted | `docs/phases/phase-0/decisions.md` |
| ADR-P0-014 | Verification levels use underscore spelling and qualify two subjects | accepted | `docs/phases/phase-0/decisions.md` |
| ADR-P0-015 | Six codec states; "Selected" maps to `ENABLED` | accepted | `docs/phases/phase-0/decisions.md` |
| ADR-P0-016 | Unknown representation is tiered, not absolute | accepted (interpretation) | `docs/phases/phase-0/decisions.md` |
| ADR-P0-017 | Persistence ladder is the master's eight steps | accepted | `docs/phases/phase-0/decisions.md` |
| **ADR-P0-018** | **Research order: enumeration precedes identification** | **proposed — needs user confirmation** | `docs/phases/phase-0/decisions.md` |
| ADR-P0-019 | `docs/product/` created to match master §56 | accepted | `docs/phases/phase-0/decisions.md` |
| ADR-P0-020 | Two Phase 0 workstreams added beyond prompt §4 | accepted | `docs/phases/phase-0/decisions.md` |
| ADR-P0-021 | Version control is not initialised in Phase 0 | accepted | `docs/phases/phase-0/decisions.md` |

## Rules for this index

1. A new ADR is added to its own phase's `decisions.md` first, then indexed here with a one-line title.
2. Superseding an ADR marks the old entry `superseded by ADR-…` in both places; the original text is kept, not deleted, because the reasoning is the valuable part.
3. An ADR in `proposed` status must not be relied on by implementation tasks until the user accepts it.
4. Decisions that materially change the master architecture cannot be made inside a phase prompt alone — they require the user's confirmation (master §58).

## Open items awaiting the user

| Item | Question | Blocking |
|---|---|---|
| ADR-P0-018 | Confirm the research ladder reads *enumerate → identify* rather than the prompt's *identify → discover*. | Phases 3, 5, 20 ordering |
| ADR-P0-021 / RISK-015 | Confirm Phase 1 should `git init` at this workspace root, and whether a remote/CI is expected. | Git workflow enforcement |
| Repository hygiene | Whether to add a project instruction file (`AGENTS.md`/`QODER.md`) so future sessions inherit the Phase 0 contract automatically. | None — convenience, but it would reduce re-reading cost every session |
