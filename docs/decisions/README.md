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

Phase 3 — `docs/phases/phase-3/decisions.md`:

| ADR | Title | Status |
|---|---|---|
| ADR-P3-001 | Platform link state, bond state and availability are three axes, not one enum | accepted |
| ADR-P3-002 | Android profile observations do not enter `TransportKind` | accepted |
| ADR-P3-003 | Observation vocabulary lives in `platform` at layer 1; no 13th area | accepted |
| ADR-P3-004 | `ConnectedDeviceSnapshot` — a projection is not a `Repository` | accepted |
| ADR-P3-005 | Failure never becomes an empty list: `ObservationStage` and a typed outcome | accepted |
| ADR-P3-006 | No new error category; observation failures reuse three existing ones | accepted |
| ADR-P3-007 | Instrumented verification in the boundary module, five test-only coordinates | accepted — user-selected route |
| ADR-P3-008 | Event-driven observation reconciled with a proxy snapshot; no list call exists | accepted |
| ADR-P3-009 | Permission standing consulted before enumeration, so empty never means refused | accepted |
| ADR-P3-010 | `DeviceObservationKey` carries the address, redacts itself, and is the only join | accepted |
| ADR-P3-011 | Bluetooth receivers stay exported; Phase 2's flag choice left to a handset | accepted — raises a Phase 2 finding |
| ADR-P3-012 | `BLUETOOTH_CONNECT` is the only manifest entry this phase earns | accepted — amends ADR-P2-011 |
| ADR-P3-013 | Bluetooth receivers are exported, Phase 2's included | accepted — supersedes ADR-P2-016's flag reasoning |
| ADR-P3-014 | Device verification deferred out of completion criteria; application first | accepted — user directive, standing |
| ADR-P3-015 | Twelve engine rules settled; empty-union category overridden | accepted — amends ADR-P3-010 |
| ADR-P3-016 | Instrumented sources policed; receiver confinement widened in location only | accepted — amends ADR-P2-016, closes ADR-P3-007's gap |
| ADR-P3-017 | The paired census is a second question with its own standing; the bond type cannot claim a link | accepted — implements prompt §10's collection B, supersedes this phase's own drafts |
| ADR-P3-018 | Device discovery is authorised in Phase 5, not Phase 3; the set is pinned by a scope test | accepted — corrects inherited data, closes TEST-P3-036 |
| ADR-P3-019 | A pending bind outranks a refusal in the answerability ladder | accepted — corrects code against its own KDoc and ADR-P3-008 |

*Two Phase 3 decisions stay open pending the device session rather than being decided by prose: whether
an adapter-state announcement reaches a `RECEIVER_NOT_EXPORTED` receiver on a real handset
(ADR-P3-011's finding against Phase 2), and whether binding a profile proxy leaves any trace on the
audio path (research U-6, against ADR-P3-008).*

Phase 2 — `docs/phases/phase-2/decisions.md`:

| ADR | Title | Status |
|---|---|---|
| ADR-P2-001 | Phase 2 lives inside the Phase 1 boundary; `platform` is a registered core area | accepted |
| ADR-P2-002 | minSdk 26 confirmed; the matrix must cover both permission models | accepted — closes ADR-P1-015 |
| ADR-P2-003 | `kotlinx-coroutines-core` returns to `:core` on the recorded trigger | accepted |
| ADR-P2-004 | Seven platform-failure categories added; two candidates refused (amends ADR-P1-006) | accepted |
| ADR-P2-005 | Retry and invalidation asserted by exhaustive tables | accepted — closes Phase 1 known issue 6 |
| ADR-P2-006 | Platform module guarded by capability scope, not by emptiness | accepted |
| ADR-P2-007 | Adapter-state observation is single-slot with recorded teardown failure | accepted |
| ADR-P2-008 | Platform facts kept as separate axes; hardware evidence rarely exceeds INFERRED | accepted |
| **ADR-P2-009** | **Requirements key off `targetSdkVersion`, not device API level** | accepted — **corrects Phase 0 SEC-PERM-002** |
| ADR-P2-010 | Debug-only companion shell so the bridge is verified, not described | accepted |
| ADR-P2-011 | Zero manifest permissions in Phase 2, in either module | accepted |
| ADR-P2-012 | `DENIED_PERMANENTLY` retained but unreachable from app code | accepted |
| ADR-P2-013 | Transport boundaries pin their kind; `TransportKind` gains `BLE` | accepted |
| ADR-P2-014 | Phase authorisation is test metadata, not runtime gating | accepted |
| ADR-P2-015 | Framework class names banned in core string literals too | accepted |
| ADR-P2-016 | UI-framework guard split; broadcast reception confined and re-guarded | accepted — amends the Phase 2 rule 9 token list |
| ADR-P2-017 | A feature flag answers API availability only; hardware evidence stays INFERRED | accepted |
| ADR-P2-018 | Audit findings R-7/R-8 fixed in the observation machine; `DEDUPED` deleted | accepted — closes audit R-7, R-8 |

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
| ADR-P1-015 | minSdk 26 is provisional and expires at Phase 2 | accepted — **closed by ADR-P2-002** |
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
