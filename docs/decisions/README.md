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
| ADR-P0-021 | Version control is not initialised in Phase 0 | accepted — **closed by Phase 1** | `docs/phases/phase-0/decisions.md` |

Phase 1 — `docs/phases/phase-1/decisions.md`:

| ADR | Title | Status |
|---|---|---|
| ADR-P1-001 | Two modules now, the rest when they earn their keep | accepted |
| ADR-P1-002 | Package root is `com.omnibuds.core.<area>` (amends Phase 0 specs) | accepted |
| ADR-P1-003 | The area layer map, and the three violations it caught | accepted |
| ADR-P1-004 | Three outcomes, thirteen-plus causes | accepted |
| ADR-P1-005 | Codec state as an ordinal ladder, `configurable` as an attribute (amends ADR-P0-015) | accepted |
| ADR-P1-006 | Error categories are the union of both sources (amends ADR-P0-012) | accepted |
| ADR-P1-007 | One narrow protocol contract, optional capability interfaces | accepted |
| ADR-P1-008 | Feature identity is namespaced text, not a brand conditional | accepted |
| ADR-P1-009 | Manual constructor injection, no framework, no ambient registry | accepted |
| ADR-P1-010 | Persistence is contracts; serialization deferred deliberately | accepted |
| ADR-P1-011 | Quality baseline is the compiler plus dependency-free architecture tests | accepted |
| ADR-P1-012 | Epoch milliseconds, nullable, for all time | accepted |
| ADR-P1-013 | Test doubles are test-only; "nothing is implemented" is asserted | accepted |
| ADR-P1-014 | Toolchain pinned from what this workstation already provides | accepted |
| ADR-P1-015 | minSdk 26 is provisional and expires at Phase 2 | accepted — revisit required |
| ADR-P1-016 | ConnectionState plus SessionClassification supersede SessionState | accepted |
| ADR-P1-017 | Commit scopes `build` and `deps` are added | accepted |
| ADR-P1-018 | Endpoint-differentiated codec support is an open model gap | **open — deferred to Phase 11** |
| ADR-P1-019 | Phase 1 leaves the diagnostic redactor unimplemented | accepted — gap recorded |
| ADR-P1-020 | Connection state has exactly one owner | accepted — corrected after review |
| ADR-P1-021 | `:core` ships with no production dependency | accepted | `docs/phases/phase-0/decisions.md` |

## Rules for this index

1. A new ADR is added to its own phase's `decisions.md` first, then indexed here with a one-line title.
2. Superseding an ADR marks the old entry `superseded by ADR-…` in both places; the original text is kept, not deleted, because the reasoning is the valuable part.
3. An ADR in `proposed` status must not be relied on by implementation tasks until the user accepts it.
4. Decisions that materially change the master architecture cannot be made inside a phase prompt alone — they require the user's confirmation (master §58).

## Open items awaiting the user

| Item | Question | Blocking |
|---|---|---|
| ADR-P0-018 | Confirm the research ladder reads *enumerate → identify* rather than the prompt's *identify → discover*. | Phases 3, 5, 20 ordering — **not** Phase 2 |
| ADR-P1-015 | Phase 2 must re-decide `minSdk` (26 was chosen only to make the library module compile; the Bluetooth runtime-permission model changed at API 31). | Phase 2 |
| ADR-P1-018 | Per-endpoint codec support (phone versus headset) is unmodelled; it needs a discriminator that must not read as "unknown". | Phase 11 |
| ADR-P0-021 / RISK-015 | Closed by Phase 1: the repository is initialised on branch `main`. CI and any remote remain undecided. | None; CI still absent (RISK-015 partially open) |
