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
| ADR-P0-018 | Research order: enumeration precedes identification | accepted — settled by ADR-P5-002 on the user's explicit deferral (was `proposed` since Phase 0 while Phase 1's code relied on it, breaching rule 3) | `docs/phases/phase-0/decisions.md`, `docs/phases/phase-5/decisions.md` |
| ADR-P0-019 | `docs/product/` created to match master §56 | accepted | `docs/phases/phase-0/decisions.md` |
| ADR-P0-020 | Two Phase 0 workstreams added beyond prompt §4 | accepted | `docs/phases/phase-0/decisions.md` |
| ADR-P0-021 | Version control is not initialised in Phase 0 | accepted — **closed by Phase 1** | `docs/phases/phase-0/decisions.md` |

Phase 8 — `docs/phases/phase-8/decisions.md`:

| ADR | Title | Status |
|---|---|---|
| ADR-P8-001 | Reuse the Phase 1 capability model; no `CapabilityId`, no second container, no four parallel §7 enums | accepted — answers prompt §2's "reuse models / do not duplicate registries"; snapshot *wraps* `DeviceCapabilities` |
| ADR-P8-002 | Add exactly one §7 dimension — `CapabilityAvailability` — carried beside, never inside, `FeatureCapability` | accepted — the only dimension the `CapabilityState` ladder does not encode |
| ADR-P8-003 | Reuse `VerificationLevel`; an evidence kind caps the rung its claim may reach | accepted — no competing verification ladder (ADR-P0-014); ceiling `IMPLEMENTED`/`LAB_TESTED` |
| ADR-P8-004 | Discovery errors reuse `OmniBudsErrorCategory`; §15 names are mapped, none added | accepted — malformed → `INVALID_STATE`; conflict/dependency are fields, not categories (ADR-P3-006/P6-006 precedent) |
| ADR-P8-005 | The engine consumes a handed-in read-only `CapabilityDiscoverySource`; it never imports the protocol layer | accepted — honours the L2↮L4 layer ban; binding to `EarbudProtocol.discoverCapabilities` is deferred L3/L4 wiring |
| ADR-P8-006 | Evidence carries provenance; reads fold through the evidence ladder and disagreements surface as `UnresolvedConflict` | accepted — no silent overwrite, no numeric confidence (ADR-P5-004) |
| ADR-P8-007 | Dependencies are per-protocol/firmware edges with cycle detection; a missing prerequisite blocks availability only, never support | accepted — never infers or enables a prerequisite (prompt §11; master §53) |
| ADR-P8-008 | The snapshot is immutable, deterministic, schema-versioned and wraps `DeviceCapabilities`; complete ≠ partial ≠ failed | accepted — `subjectRef` is a `String?` because capability and device share a layer |
| ADR-P8-009 | Vendor extensions reuse `VendorExtension`/namespaced `FeatureId`; unparseable extensions preserved, no command implemented | accepted — prompt §12; ADR-P1-008/P7-009 |
| ADR-P8-010 | The engine ships with no protocol and no production source; ceiling `IMPLEMENTED`; device discovery `NOT RUN` | accepted — ADR-P1-013/ADR-P5-006/ADR-P7-010 precedent; standing build-first directive |

Phase 7 — `docs/phases/phase-7/decisions.md`:

| ADR | Title | Status |
|---|---|---|
| ADR-P7-001 | Reuse the Phase 1/2 protocol knowledge model; no duplicate descriptor, registry or manager | accepted — answers prompt §2's "no duplicate protocol managers" |
| ADR-P7-002 | A `ProtocolSession` is a separate runtime type; `EarbudProtocol` stays the stateless family contract | accepted — keeps Phase 8's `discoverCapabilities` out of scope |
| ADR-P7-003 | Reuse the master `VerificationLevel` ladder; §8's `RESEARCHED`/`AUTOMATED_TESTED` are aliases | accepted — no parallel evidence enum (ADR-P0-014) |
| ADR-P7-004 | Reuse `EffectClass`; "unknown side effect" is structural absence, not a fourth member | accepted — read/write retry asymmetry preserved (specs §4) |
| ADR-P7-005 | `ProtocolState` is a third lifecycle axis; `READY` only after `initialize()` succeeds | accepted — distinct from `ConnectionState`/`TransportState` (ADR-P3-002) |
| ADR-P7-006 | `ProtocolResolver` returns six outcomes over the registry + Phase 5 evidence; connects/executes nothing | accepted — a manufacturer name never selects a protocol (PROTO-ID-001) |
| ADR-P7-007 | The transport-adapter boundary is a `:core` interface; no Android types, no framework binding shipped | accepted — prompt §16 |
| ADR-P7-008 | Protocol events are a bounded, cancellation-safe `Flow`; requested ≠ device-confirmed | accepted — prompt §13; Phase 4 event pattern |
| ADR-P7-009 | Vendor extensions reuse `core.capability.VendorExtension`; namespaced, non-colliding, unknown-resolved | accepted — prompt §14; ADR-P1-008 |
| ADR-P7-010 | The protocol set ships empty; ceiling `IMPLEMENTED`; scripted protocols are test-only | accepted — ADR-P1-013/ADR-P5-006 precedent; prompt §16/§17 |

Phase 6 — `docs/phases/phase-6/decisions.md`:

| ADR | Title | Status |
|---|---|---|
| ADR-P6-001 | Phase 6 fills the Phase 1/2 transport hierarchy; no parallel interface tree, no new area | accepted — answers prompt §2's "reuse, do not duplicate" |
| ADR-P6-002 | A transport has one authoritative lifecycle state; `CONNECTED` only on the platform's confirmation | accepted — `TransportState`/`Transitions`, mirrors `ConnectionStateTransitions` |
| ADR-P6-003 | Capability-specific transport interfaces, never one oversized `DeviceTransport` | accepted — GATT/RFCOMM members; PROTO-ABST-006 held |
| ADR-P6-004 | A control channel over BLE *is* GATT; `BleTransport` is the link-availability boundary | accepted — **corrects the stale "no BLE constant" docs** (RISK-039-class) |
| ADR-P6-005 | `TransportResolver` selects nothing; the only Phase 6 impl is the safe unknown | accepted — no manufacturer rule, no auto-connect (prompt §12) |
| ADR-P6-006 | No new error category; platform statuses map onto existing ones with their retry class intact | accepted — ADR-P3-006 precedent; timeout never means no-effect |
| ADR-P6-007 | One operation in flight per channel, cancellation-safe teardown, closed rejects work, no auto-reconnect | accepted — prompt §14 |
| ADR-P6-008 | The Android mechanism is written and seam-tested but never run; ceiling `IMPLEMENTED` | accepted — user's "Domain + Android mechanism" choice; Phase 2/3 precedent |
| ADR-P6-009 | Audio/control separation is enforced by the layer map, not a runtime check | accepted — transport L1 cannot import audio L2; prompt §11 |
| ADR-P6-010 | Timeouts flow through the `TimeProvider` seam; `:core` reads no wall clock | accepted — exchange bound = min(caller, request) |
| ADR-P6-011 | The notification channel gets a Flow home on `GattTransport`, subscription-leak-safe | accepted — closes Phase 2's deferred item 1 |
| ADR-P6-012 | Phase 6 authorises transport opens and keeps the scan deferred; constructs, never connects | accepted — extends ADR-P5-012; scan tag stays at 6, unexercised |

Phase 5 — `docs/phases/phase-5/decisions.md`:

| ADR | Title | Status |
|---|---|---|
| ADR-P5-001 | Phase 5 consumes Phase 1's fingerprint model; no parallel identity type, no 13th area | accepted — answers prompt §2's "no duplicate identity models" |
| ADR-P5-002 | The ladder runs enumerate → identify; a fingerprint is evidence, an identification a conclusion | accepted — **settles ADR-P0-018** (was `proposed`; code relied on it, breaching rule 3) |
| ADR-P5-003 | Signals are typed by quality, not a boolean: observed/derived/inferred/unknown/unavailable/invalid | accepted — ADR-P0-016 widened to identity; passive-read facts corrected |
| ADR-P5-004 | `VERIFIED` confidence exists in the type and is unreachable from Phase 5's code | accepted — HIGH needs two independent kinds; no numeric scores |
| ADR-P5-005 | A name is evidence of nothing except a name: normalization is trim/collapse/case-fold only | accepted — no fuzzy/substring/brand-stripping (prompt §7) |
| ADR-P5-006 | The registry ships with no vendor rules, and a test asserts the emptiness | accepted — ADR-P1-013 precedent; invented signatures refused by construction |
| ADR-P5-007 | No scan, no advertisement, no service discovery: the passive boundary is the user's decision | accepted — user "skip the phone" directive; MANUFACTURER_DATA/CHARACTERISTIC_UUID are UNAVAILABLE |
| ADR-P5-008 | Ambiguity is a result with candidates in it, never an arbitrary winner | accepted — `Ambiguous` exposes no single manufacturer/model |
| ADR-P5-009 | Identification enriches a session; never recreates one, never touches connection, stays a separate concept | accepted — `productIdentity` is a sibling of reported `DeviceIdentity` |
| ADR-P5-010 | An address is never an identity signal, and identity data is never a store | accepted — SEC-ID-003; nothing persisted |
| ADR-P5-011 | Matching is synchronous pure logic in the caller's thread, with the boundary stated not hidden | accepted — prompt §16; no cache, no ML |
| ADR-P5-012 | The discovery-scan authorization tag moves off Phase 5, which opens no scanner | accepted — **corrects ADR-P3-018's Phase-5 tag to Phase 6** |

Phase 4 — `docs/phases/phase-4/decisions.md`:

| ADR | Title | Status |
|---|---|---|
| ADR-P4-001 | The session engine lives in `core/session`, consumes the projection, owns exactly one thing | accepted — answers Phase 1's three-way deferral (Phase 2/4/24) |
| ADR-P4-002 | A session carries no second lifecycle axis | accepted — extends Phase 1's `DeviceSession` rule |
| ADR-P4-003 | Six reachable `ConnectionState` members; the other five, including `ERROR`, are pinned unreachable | accepted — retitled at close-out from "five states plus ERROR" |
| ADR-P4-004 | Observation-to-session mapping is one table; absence is a disconnect only when the union answered | accepted — the `arrival` claim corrected at close-out |
| ADR-P4-005 | `sessionId` is minted and opaque; ambiguity is a state, never a merge | accepted — honours SEC-ID-001/003/004 |
| ADR-P4-006 | Reconnect resumes inside a session; after termination a new id; grace is one round, not a timer | accepted — resolves prompt §9's named policy question |
| ADR-P4-007 | A refused round never empties the list; the projection now carries its own refusal reason | accepted — amends `ConnectedDeviceSnapshot` |
| ADR-P4-008 | Events are notifications with a bounded buffer; `SESSION_ACTIVATED` is refused | accepted — Phase 0 specs rules 5.5/5.6 |
| ADR-P4-009 | One mutex, one revision; `applyIfNewer` is deliberately not called here | accepted — corrected at close-out; the gate is Phase 6's |
| ADR-P4-010 | Temporary is the only classification the engine can produce; nothing reaches storage | accepted — honours SEC-ID-005/006, ADR-P0-004 |
| ADR-P4-011 | `refresh()` stays uncalled, and the reason is the engine's input contract | accepted — re-scores Phase 3's RISK-058 without closing it |
| ADR-P4-012 | Phase 4's evidence ceiling is `IMPLEMENTED`; a session is not a claim about a device | accepted — T1 tier, TST-MOCK-001, ADR-P3-014 |

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
| ADR-P3-018 | Device discovery is authorised in Phase 5, not Phase 3; the set is pinned by a scope test | accepted — corrects inherited data, closes TEST-P3-036; **its Phase-5 tag superseded by ADR-P5-012 (moved to Phase 6, which is the first that opens a scanner)** |
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
| ~~ADR-P0-018~~ **CLOSED by Phase 5** | The research ladder question (*enumerate → identify* vs *identify → discover*) was settled in ADR-P5-002: the user had no preference and directed the phone work be skipped, so the ordering the code already assumed was recorded. No longer awaiting the user. | Nothing |
| Deferred device session | The end-of-project handset session the user scheduled instead of per-phase verification (ADR-P3-014) has no date, no owner and no trigger; RISK-045 names that as the process risk and RISK-065/066 carry it into Phase 4. Phases 1-5 built no hardware claim (Phase 5 adds RISK-097), so nothing is blocked — but every `IMPLEMENTED` in this index stays one until that session runs. | Nothing blocked; everything unverified |
| ADR-P1-015 | Phase 2 must re-decide `minSdk` (26 was chosen only to make the library module compile; the Bluetooth runtime-permission model changed at API 31). | Phase 2 |
| ADR-P1-018 | Per-endpoint codec support (phone versus headset) is unmodelled; it needs a discriminator that must not read as "unknown". | Phase 11 |
| ADR-P0-021 / RISK-015 | Closed by Phase 1: the repository is initialised on branch `main`. CI and any remote remain undecided. | None; CI still absent (RISK-015 partially open) |
