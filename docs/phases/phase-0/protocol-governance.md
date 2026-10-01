# OMNIBUDS — PROTOCOL GOVERNANCE (PHASE 0)

## 1. Purpose, authority and scope restriction

| Item | Value |
|---|---|
| Document | `docs/phases/phase-0/protocol-governance.md` |
| Status | Binding rulebook for transports, protocols, capabilities and verification |
| Superior source | `docs/MASTER-CONTEXT.md` (master §58 — master source of truth) |
| Authorising prompt | `docs/phases/phase-0/execution-prompt.md` (§22 "Protocol" acceptance, §15, §16, §18) |
| Peer document | `docs/phases/phase-0/architecture-governance.md` (owns boundaries; defers the executable procedure here — its ARCH-PROTO-004, ARCH-PERSIST-005) |
| Applies to | Phases 1–52, every sub-agent, every protocol claim |

- PROTO-DOC-001. This document states rules. It is not a protocol specification and contains no discovered protocol fact.
- PROTO-DOC-002. Nothing in this document authorises implementation. Bluetooth, transport code, protocol adapters and reverse-engineering tooling begin in later phases (prompt §2, §25; master §57). The Protocol Laboratory is Phase 20 (master §48) and MUST NOT be built, scaffolded or stubbed now.
- PROTO-DOC-003. Where this document and `architecture-governance.md` overlap, that document owns layer boundaries and vocabularies; this document owns the procedure inside them. A contradiction is reported to the orchestrator, never resolved by editing either file silently (master §38, §58).
- PROTO-DOC-004. Type and operation names in fenced blocks are illustrative documentation, not files to create (prompt §2). No real vendor opcode, characteristic UUID or AES key appears anywhere: `<…>` marks a placeholder standing for a fact that has not been discovered and MUST NOT be filled in by an example.
- PROTO-DOC-005. Rule namespace `PROTO-<area>-<NNN>` is reserved for this document; `<area>` names the section (DOC, XPORT, ABST, ID, CAP, PERSIST, VERIFY, RESEARCH, DB, NOMAGIC, VENDOR, DEP, ERR, SESSION). IDs are immutable and never renumbered (mirrors ARCH-GOV §12.1).
- PROTO-DOC-006. Citation convention: `master §N` is `docs/MASTER-CONTEXT.md`, `prompt §N` / `REQ-P0-NNN` / `ADR-P0-NNN` is `docs/phases/phase-0/execution-prompt.md`, `ARCH-…` is `architecture-governance.md`, and a bare `§N` is a section of this document.

**Terminology contract (only permitted forms).**

| Vocabulary | Members | Source |
|---|---|---|
| `CapabilityState` | `UNKNOWN`, `UNSUPPORTED`, `READ_ONLY`, `SUPPORTED_VOLATILE`, `SUPPORTED_PERSISTENT`, `PERSISTENCE_VERIFIED` | master §10, prompt REQ-P0-006 |
| `CodecState` | `SUPPORTED`, `AVAILABLE`, `ENABLED`, `NEGOTIATED`, `ACTIVE`, `CONFIGURABLE` | master §15 |
| `VerificationLevel` / `ProtocolConfidence` | `INFERRED`, `IMPLEMENTED`, `LAB_TESTED`, `HARDWARE_VERIFIED`, `PERSISTENCE_VERIFIED` — five evidential levels, none of them `UNKNOWN` | master §27, prompt §16 |
| Error categories | the 13 categories in §13 | master §30 |
| ID grammar | `REQ-`, `TASK-`, `TEST-`, `ADR-<SCOPE>-<NNN>`, `RISK-<NNN>` | master §41, §44, §45, §47, prompt §14 |

**Conflicts with the Phase 0 prompt.** MASTER-CONTEXT.md wins in every row; each is recorded, none is silently resolved (master §58).

| # | Conflict | Governing outcome |
|---|---|---|
| D1 | prompt REQ-P0-007 ladder starts at `WRITE` and omits the pre-disconnect `VERIFY`; master §24 has 8 steps | master §24's 8-step ladder governs (§6). Already C4 in `architecture-governance.md` §1.4. |
| D2 | master §26 workflow is 9 steps and orders DISCOVER before IDENTIFY; prompt §15 is 11 steps and orders Identify first | Both preserved: no control read before identity is resolved, no write before step 7 (§8). `security-governance.md` SEC-RES-005 records master §26's precedence, so discovery precedes identification; see PROTO-RESEARCH-006. |
| D3 | prompt §16 says "A capability is …"; master §27 uses the same ladder for protocol confidence | One ladder, two subjects; a level is meaningless without its subject (§7). |
| D4 | prompt §9.3 lists 10 error categories; master §30 lists 13 | 13 categories (§13). Already C5. |
| D5 | prompt §17 codec vocabulary omits `CONFIGURABLE` | master §15 governs; owned by `audio-governance.md` (see PROTO-SESSION-005). Already C3. |
| D6 | prompt §4 names Agent outputs `docs/phase-0/…`; prompt §6–§14 and master §56 use `docs/phases/phase-0/…` | `docs/phases/phase-0/` is canonical. Already C1. |
| D7 | prompt §4 assigns no agent to this file, yet prompt §22 "Protocol" and §21 "Protocol safety documented" demand it | Filled by Agent 2's scope extension; the orchestrator must confirm ownership. No protocol rule may exist in no-man's-land: §16 here maps every prompt §22 Protocol bullet to rules in this document. |
| D8 | §22's per-device support claim needs a device matrix, but prompt §6 and §14 name no such file | `docs/phases/phase-0/device-support-matrix.md` is treated as a proposed sibling deliverable, not an existing one; until it exists, §7's report format is the only permitted claim form. |

## 2. Transport abstraction (master §8)

- PROTO-XPORT-001. GATT is never the default assumption. No layer above the platform implementation may treat "no GATT service" as "no control channel" (master §8; ARCH-XPORT-001).
- PROTO-XPORT-002. Every transport below is a first-class candidate, described as a role, never as a byte, socket parameter or callback contract (prompt §2).

| Transport (master §8) | Typical roles | Rule |
|---|---|---|
| Classic Bluetooth | device/classic info; some control paths | absence is not failure |
| BLE / GATT | bookkeeping, advertisements, many vendor control services | never assumed |
| RFCOMM / SPP | control on devices where configuration is classic-only | explicit open attempt required |
| A2DP | media audio; codec/profile state | control code must not send commands over a media channel |
| AVRCP | audio control/status introspection | introspection ≠ vendor control |
| HFP | call-path state, some battery/level reporting | derived level is not vendor battery truth |
| LE Audio | an architecture in its own right, not an A2DP codec (master §20) | separate state paths |
| Vendor-specific control | proprietary configuration channels | research-gated (§8) |
| Future transport | extensibility | new implementation, no shared-contract edit |

- PROTO-XPORT-003. A session attaches zero or more control channels; the transport set is a property of the session, never of a device class or a vendor class (master §8).
- PROTO-XPORT-004. Selection order is fixed by master §4: detect connected device → build fingerprint (§4 of this doc) → resolve protocol candidates → determine available transports → attach control channel → discover capabilities → read current state → READY. Reaching READY with zero attached control channels is permitted and is reported as read-only/observational, never as full support.
- PROTO-XPORT-005. One device may use BLE for advertisements/bookkeeping, RFCOMM for control, A2DP for audio and a proprietary mechanism for configuration. All four must be recorded simultaneously for one session — per-purpose channels, not a single per-device channel (master §8).
- PROTO-XPORT-006. A transport advertises its availability as domain vocabulary: an offered profile, an openable channel, or neither. Presentation results are mapped to `TransportUnavailable`, `GattFailure`, `RfcommFailure`, `Timeout` or `PermissionDenied` (ARCH-AND-003) with the transport named.
- PROTO-XPORT-007. A missing or refused transport yields `TransportUnavailable` and a recorded failed-candidate entry. Silent skip, fallback to "assume GATT", and reporting an absent transport as `UnsupportedFeature` are each violations (master §8, §30; ARCH-XPORT-003).
- PROTO-XPORT-008. Transport probing on an unidentified device is constrained by §8's gates and `docs/phases/phase-0/security-governance.md`; it never includes writes.

## 3. Protocol abstraction (master §9)

Operations are modelled at operation level, never as byte calls. Illustratively, and only as documentation:

```text
identity operations   identify(), readFirmware()
state read operations readState(), readBattery(), readEqualizer(), readGestures()
discovery operations  discoverCapabilities()
control operations    setAnc(), setTransparency(), writeEqualizer(), writeGestures()
```

- PROTO-ABST-001. `EarbudProtocol` (master §9) is the conceptual contract; vendors are never required to implement every operation (master §9, §13; ARCH-PROTO-001).
- PROTO-ABST-002. Presence of a vendor class in the repository is not evidence about a device; capability negotiation decides what is available for a connected device (master §9, §10; ARCH-PROTO-002).
- PROTO-ABST-003. An unimplemented operation yields `UNKNOWN` for its capability, never `UNSUPPORTED` (master §9, §53; ARCH-PROTO-001).
- PROTO-ABST-004. Identity operations run before state reads, and state reads before control operations (master §4, §25, §26; PROTO-RESEARCH-006).
- PROTO-ABST-005. A vendor shortcutting identity (hard-coding model or firmware into control paths) violates master §51 and prompt §18 even if it works on one device.
- PROTO-ABST-006. Protocol semantics SHALL NOT be expressed in transport primitives; the contract a vendor implements names capabilities and values, not characteristic handles (ARCH-DEP-004, ARCH-XPORT).

## 4. Device identity and fingerprint governance (master §6, §7)

Fingerprint inputs (master §6, §7). Every field is optional; a missing field is `UNKNOWN`, never empty and never inferred.

| Input | Master source | Absence means |
|---|---|---|
| Manufacturer, model, model identifier | §6 metadata | device not identified — §8 read-only mode applies |
| Manufacturer data | §6, §7 | one fewer discriminator; no other field is upgraded by it |
| Service UUIDs, characteristic UUIDs, descriptors | §6, §7 | transport candidates may be empty — not zero-capability |
| Device class, supported profiles, transport type | §6, §7 | fewer transport candidates |
| Firmware version, hardware revision, protocol version/family | §6, §7 | compatibility and confidence cannot be claimed (§7, §9) |
| Battery and audio capabilities, device-specific capabilities, control-channel info | §6 | `UNKNOWN` |
| Transport candidates, protocol candidates | §7 `DeviceFingerprint` | nothing to attach or negotiate |

- PROTO-ID-001. A device is identified by a fingerprint, not by its Bluetooth display name: the display name alone is never identity (master §7).
- PROTO-ID-002. The fingerprint must answer four questions (master §7): what device is this? which protocol applies? which transport applies? which capabilities can be safely queried? An unanswered question is a missing permission: no control operation may proceed on a guessed answer.
- PROTO-ID-003. `protocolCandidates` and `transportCandidates` are candidate lists to be confirmed by reads, never verdicts (master §7).
- PROTO-ID-004. Unknown metadata stays unknown; metadata is never fabricated; sensitive identifiers are not retained without a stated reason and no longer than needed (master §6; ARCH-QUAL-011).
- PROTO-ID-005. Two colliding fingerprints are resolved by recorded evidence, not by choosing the shorter path; unresolved, the device is treated as unknown (§8).
- PROTO-ID-006. Identity is necessary but not sufficient: a recognised device does not gain per-feature evidence it does not have (§5).

## 5. Capability engine rules (master §10, §11)

- PROTO-CAP-001. A capability is `CapabilityState` plus the metadata below — never `supported = true` (master §10; REQ-P0-006; ARCH-CAP-002).

| State | Meaning | Minimum evidence | UI consequence (master §11) |
|---|---|---|---|
| `UNKNOWN` | not yet established | none | hidden or explicitly unknown; never rendered working |
| `UNSUPPORTED` | absence positively established | a conclusive negative from a protocol known to govern the device | not shown; reason recordable in diagnostics |
| `READ_ONLY` | readable state, no write path | successful reads plus no negotiated write | indicator only; no control |
| `SUPPORTED_VOLATILE` | writable, not proven to survive a power/reconnect cycle | accepted write + read-back in the same session | control shown, labelled session-only; no restart promise |
| `SUPPORTED_PERSISTENT` | persistence asserted for this device, not yet observed surviving here | §6 rungs 1–4 plus a persistence assertion recorded for this fingerprint (§9) | normal control; wording must not claim observed survival |
| `PERSISTENCE_VERIFIED` | survival observed on real hardware after a real reconnect | the complete eight-rung §6 ladder on this device | strongest claim; §7 report format |

| From | To | Trigger |
|---|---|---|
| `UNKNOWN` | any other state | positive evidence for that state (§7) |
| `UNKNOWN` | `UNSUPPORTED` | **forbidden** — absence of information is not absence of capability (master §53) |
| `UNKNOWN` | any `SUPPORTED_*` | **forbidden** — "not verified" is not "supported" (master §53) |
| `READ_ONLY` | `SUPPORTED_VOLATILE` | negotiated write accepted and read back |
| `SUPPORTED_VOLATILE` | `SUPPORTED_PERSISTENT` | persistence asserted for this fingerprint (§9); still not an observation |
| `SUPPORTED_PERSISTENT` | `PERSISTENCE_VERIFIED` | the full §6 ladder including a real disconnect/reconnect — master §24: "Only then: PERSISTENCE_VERIFIED" |
| any | `UNKNOWN` | never automatic; only a recorded withdrawal of the supporting evidence (§7) |

- PROTO-CAP-002. Capability metadata (master §10), all required per capability record: `readable`, `writable`, `transport`, `protocol`, `requires-connection`, `persistence behavior`, `verification status`. A record missing any field is `UNKNOWN`, not a default.
- PROTO-CAP-003. Promotion requires evidence at the level claimed; demotion is immediate on counter-evidence or on a firmware change invalidating compatibility (§9). `VerificationFailed` demotes to `READ_ONLY`/`SUPPORTED_VOLATILE` and never blocks reads.
- PROTO-CAP-004. UNKNOWN is never rewritten. No cache, UI default, protocol-name match, saved device, or database import may convert `UNKNOWN` into `UNSUPPORTED` or into any `SUPPORTED_*` (REQ-P0-002; master §53; ARCH-CAP-003).
- PROTO-CAP-005. Backend capability state decides what UI may show: unsupported absent, `UNKNOWN` never shown as supported, capability absent ⇒ control absent (master §10, §11; ARCH-CAP-001, ARCH-CAP-004).

## 6. Persistence verification (master §24, prompt REQ-P0-007)

- PROTO-PERSIST-001. A successful write never establishes `SUPPORTED_PERSISTENT`. Only the ladder below does (REQ-P0-007; master §24; ARCH-PERSIST-001, ARCH-PERSIST-002).

| Rung (master §24) | Establishes | Explicitly does not establish | On failure |
|---|---|---|---|
| 1 READ | baseline before touching anything | that a write is accepted | no write attempted; `UNKNOWN` kept |
| 2 WRITE | command accepted | that the value was applied | `WriteRejected`; state unchanged |
| 3 READ BACK | reported value matches the request | that anything survives a disconnect | `VerificationFailed`; no promotion |
| 4 VERIFY | read-back compared to expected semantics | persistence | `VerificationFailed`; no promotion |
| 5 DISCONNECT | session actually torn down | that state survived yet | ladder incomplete |
| 6 RECONNECT | device re-established | — | `DeviceDisconnected` recorded; verdict stays `UNKNOWN` |
| 7 READ AGAIN | post-reconnect value | — | `VerificationFailed`; downgrade (PROTO-PERSIST-002) |
| 8 VERIFY | value survives the real cycle | nothing — this is the top | `VerificationFailed`; downgrade (PROTO-PERSIST-002) |

- PROTO-PERSIST-002. Value reverts, resets or becomes unreadable after reconnect ⇒ `SUPPORTED_VOLATILE` (or the appropriate state), and the device's persistence expectation is recorded (master §24; ARCH-PERSIST-003).
- PROTO-PERSIST-003. Rungs 5–6 SHALL use a real disconnect/reconnect. A simulated, in-memory or app-restart-only "fake reconnect" SHALL NOT satisfy any rung (ARCH-PERSIST-002).
- PROTO-PERSIST-004. "Written this session" is not a persistence claim; presentation may not use restart-surviving wording for it (ARCH-PERSIST-001).
- PROTO-PERSIST-005. Persistence expectations are per capability and per fingerprint; never inherited across models or generations (master §24, §51; ARCH-PERSIST-004).
- PROTO-PERSIST-006. Never-persisted settings MUST NOT be silently cached as if they persisted (ARCH-PERSIST-004).
- PROTO-PERSIST-007. The ladder is executed by Phases 17–18 (Persistent Configuration Engine; Persistence Verification Framework) and tested per master §36 (persistence tests with real hardware).
- PROTO-PERSIST-008. Where a capability is `READ_ONLY` by nature (a battery level, master §10, §23), rungs 2–4 and 7–8 are recorded as not applicable, never as failed: an unwritable control cannot be persistence-verified, and that is `READ_ONLY`, not `UNSUPPORTED` (master §53).

## 7. Hardware verification statuses (prompt §16; master §27, §54, §55)

- PROTO-VERIFY-001. The five levels below are the same ladder used for a protocol's confidence (master §27) and for a capability's verification status (master §10) (D3). A level stated without its subject is unverified by construction.

| Level | Definition | Permitted claim | Forbidden claim |
|---|---|---|---|
| `INFERRED` | from documentation or protocol resemblance, nothing run (prompt §16) | "may exist" | any capability-state change |
| `IMPLEMENTED` | code path exists, never executed (prompt §16) | "a candidate implementation exists" | `LAB_TESTED`, `HARDWARE_VERIFIED` |
| `LAB_TESTED` | exercised against a simulation/mock, never against hardware (prompt §16) | "works against the model" | "device supports it" |
| `HARDWARE_VERIFIED` | all eight master §54 conditions | "verified on this device" | persistence survival |
| `PERSISTENCE_VERIFIED` | behaviour survives a real disconnect/reconnect (§6) | strongest claim | nothing |

- PROTO-VERIFY-002. The eight master §54 conditions for `HARDWARE_VERIFIED`: device identity known; correct protocol known; command understood; command sent to real hardware; device responds correctly; state read back; result matches expected behaviour; reconnect behaviour tested where persistence matters. Missing conditions keep the claim at its true level — partial satisfaction is never promoted.
- PROTO-VERIFY-003. Levels are monotonic; each requires the rung below plus new evidence. A status is never reported above its evidence (prompt §16; master §27, §54; ARCH-CAP-005, ARCH-CAP-006).
- PROTO-VERIFY-004. `LAB_TESTED` never substitutes for `HARDWARE_VERIFIED`; mock or replay evidence never promotes a capability (prompt §16, §18; master §2, §54).
- PROTO-VERIFY-005. Levels attach to a (fingerprint, protocol version, firmware range, capability) tuple; evidence on one device never promotes a sibling (ARCH-CAP-007, ARCH-PERSIST-004).
- PROTO-VERIFY-006. The only acceptable support claim is the per-device report (master §55): verified / not verified / unsupported, three explicit lists, never "OmniBuds supports <brand>" (ARCH-CAP-007; `docs/phases/phase-0/testing-governance.md` owns test evidence).
- PROTO-VERIFY-007. A firmware or protocol-version change outside the recorded compatible range revokes claims above `INFERRED` for affected capabilities to `UNKNOWN` and records the reason (§9; `FirmwareMismatch` in §13).

## 8. Protocol research workflow (prompt §15; master §25, §26)

- PROTO-RESEARCH-001. Investigation follows the eleven steps below in order. The steps are prompt §15's eleven; their order is master §26's, which also matches `security-governance.md` SEC-RES-005 (see D2).

| # | Step | Gate before advancing |
|---|---|---|
| 1 | Discover | safe discovery only: metadata, services, characteristics, descriptors, manufacturer data, standard battery (master §25) |
| 2 | Identify | fingerprint built; prompt §15 lists this step first (D2) (§4) |
| 3 | Read | read-only operations only (master §25) |
| 4 | Observe | notifications/indications observed passively; nothing is written |
| 5 | Understand | structure recorded as a `ProtocolDefinition` draft (§10) |
| 6 | Validate | reads reproduce consistently; competing explanations written down, not silently dropped |
| 7 | Write | first permitted write; only an operation meeting every condition of `security-governance.md` SEC-WRITE-001 — including protocol confidence of at least `LAB_TESTED` for that command |
| 8 | Read back | rung 3 of §6 |
| 9 | Disconnect | rung 5 of §6, a real teardown |
| 10 | Reconnect | rung 6 of §6 |
| 11 | Verify | rung 7–8 of §6 |

- PROTO-RESEARCH-002. Never begin with arbitrary writes (prompt §15; master §25; ARCH-QUAL-008). Before step 7: no writes to unknown characteristics, no blind fuzzing, no undocumented commands.
- PROTO-RESEARCH-003. Never assume a UUID's purpose or a byte's meaning without evidence (prompt §15; master §25, §52). A guessed purpose stays `INFERRED` and is labelled a hypothesis, never a database fact (§9).
- PROTO-RESEARCH-004. Research proceeds only on devices and software the development team is authorized to analyze; uncertainty about authorization is a stop condition, not a risk to accept (master §26; `docs/phases/phase-0/security-governance.md` SEC-RES-001, SEC-RES-008). Logging, packet-capture handling, identifier retention and the rule that a user's device is never a research target by default are also SEC-RES-007 and that document's jurisdiction.
- PROTO-RESEARCH-005. Research tooling lives in the Protocol Laboratory (Phase 20) and enters the app only through the protocol database (ARCH-BOUND-002). Phase 0 documents the rules and runs no research (prompt §2, §25).
- PROTO-RESEARCH-006. Identity is a gate, not a formality: step 2 must answer §7's four questions well enough before step 3 issues any control read, and no read may be issued that a different protocol would interpret differently (master §7, §25, §26; D2).

## 9. Protocol database governance (master §27, §28)

- PROTO-DB-001. Fields carried by master §27, all required per record (the schema and storage engine are deferred, §15).

| Field | Rule |
|---|---|
| manufacturer, model | scope of every claim; never widened beyond tested models (§7) |
| fingerprint rules | which evidence answers the four §7 questions |
| transport | transport(s) carrying control, per-purpose (§2) |
| protocol, protocol version | protocol identity plus the version it applies to |
| firmware compatibility | range/versions evidence covers; outside it, claims are revoked (§7); future phases may refine into per-feature matrices (master §45) |
| service UUIDs, characteristics | where a channel lives, with evidence, never with assumed purpose (PROTO-RESEARCH-003) |
| commands, responses, parsers, encoders | structural definitions with documentation, never scattered literals (§10) |
| capabilities | which capability each command maps to |
| persistence behavior | per capability, backed only by §6 evidence |
| known limitations | conflicts, firmware/transport restrictions, platform restrictions |
| test status | `VerificationLevel` plus subject and device tuple (§7) |

- PROTO-DB-002. `ProtocolConfidence` is recorded per protocol version and firmware range. Its five members carry no `UNKNOWN`: a device with no matching record has no protocol entry at all and its capabilities are `UNKNOWN` — never weakly `INFERRED` (master §27, §53).
- PROTO-DB-003. Known limitations and test status are mandatory; a record without them is incomplete and cannot back a UI claim (master §27, §55).
- PROTO-DB-004. Databases remain separate (master §28; ARCH-BOUND-003).

| | Protocol database | User device database |
|---|---|---|
| Contains | knowledge about models and protocols | the user's saved devices |
| Grows from | research and verification (§8) | the user's explicit "Add to My Devices" (master §5) |
| Answers | "how is this protocol controlled?" | "what does this user own?" |
| MUST NOT contain | identifiers of a user's device | fabricated protocol facts |
| Failure mode to prevent | — | device seen once becoming a saved device (§14) |

## 10. No-magic-protocol rule (master §51, §52; prompt §18)

- PROTO-NOMAGIC-001. Protocol commands SHALL be structural data (master §52).

| Element | Holds |
|---|---|
| `ProtocolDefinition` | transport, framing, version, firmware range, confidence |
| `CommandDefinition` | an operation's symbolic name, fields, expected response kind |
| `ResponseDefinition` | response shape and error indications |
| `Parser` | bytes → domain value |
| `Encoder` | domain value → bytes |
| `CapabilityMapping` | which capability an operation sets or reads |

- PROTO-NOMAGIC-002. Scattered hex literals and scattered UUID constants are forbidden: master §51 names "magic UUIDs scattered through code" and "magic protocol bytes scattered through code", and §52 illustrates the literals it rejects by naming them as unnamed numbers. Protocol facts live in the elements above with documentation.
- PROTO-NOMAGIC-003. Every literal needs three things: a named definition element, a documentation line carrying evidence (source, lab or hardware, and §7 level), and a capability mapping. An operation is named symbolically — `<VENDOR_FEATURE_OP>`, `<VENDOR_FEATURE_READBACK>` — never by a value someone typed.
- PROTO-NOMAGIC-004. A reviewer detects a violation by: (a) searching outside `ProtocolDefinition`/`CommandDefinition`/`ResponseDefinition`/`Parser`/`Encoder` for byte, hex and UUID literals; (b) flagging any new definition element without documentation; (c) refusing any control path assembled ad hoc at a call site; (d) flagging vendor byte or model checks in UI or domain code (ARCH-QUAL-003, ARCH-QUAL-014); (e) treating a "quick test write" bypassing the §8 ladder as a Phase 0 failure condition (prompt §23).

## 11. Vendor extension strategy (master §13; REQ-P0-012)

- PROTO-VENDOR-001. Universal must not mean ANC/EQ/battery: reducing every headset to the lowest common denominator is prohibited (master §13; REQ-P0-012).
- PROTO-VENDOR-002. Common functionality gets a common interface; unique functionality gets a vendor extension (master §13; ARCH-PROTO-003).

| Situation | Representation |
|---|---|
| common capability, modelled | core capability key |
| vendor capability, modelled | vendor extension namespaced by protocol family, mapped to UI |
| vendor capability, known to exist but not modelled | recorded in the database: `UNKNOWN`, unmapped, hidden in UI; never silently dropped |
| no sign either way | `UNKNOWN`; nothing recorded as unsupported |

- PROTO-VENDOR-003. An unmodelled feature is not unsupported. Evidence of existence — a documented product feature, a vendor app control, an advertised capability (master §6) — keeps it `UNKNOWN` with the evidence cited (master §53).
- PROTO-VENDOR-004. Vendor extensions are discovered through capability negotiation (master §9, §10), never by branching on a model string (master §51; ARCH-QUAL-014); vendor-specific features are expected (master §12, §13).
- PROTO-VENDOR-005. Vendor extension rules are exercised by Phases 23 and 40–45; the device-by-device record is `docs/phases/phase-0/device-support-matrix.md`.

## 12. Feature dependency and conflict governance (master §29)

- PROTO-DEP-001. The `FeatureDependencyEngine` represents five relation kinds (master §29); each is asserted only with evidence and never inferred by the UI (ARCH-DEP-010).

| Relation | Meaning | Illustrative case |
|---|---|---|
| conflict | two features cannot hold at once | high-bitrate codec vs multipoint on a given device (master §29) |
| prerequisite | one needs another first | a sensor feature needing wear detection enabled |
| mutually exclusive modes | one setting with several modes | ANC / transparency / normal |
| firmware limitation | behaviour differs by firmware | feature absent on some firmware (§9) |
| transport limitation | capability depends on the attached transport set | configuration path unavailable (§2) |

- PROTO-DEP-002. Two features are never shown active when the hardware cannot do both (master §29). A verified conflict downgrades the weaker claim to `UNKNOWN` or shows the enforced limitation, and both are recorded (master §55; ARCH-CAP-007).
- PROTO-DEP-003. Conflicts bind presentation, notifications, Quick Settings and widget content (master §32, §33, §34), not only screens.
- PROTO-DEP-004. Dependency resolution is the Phase 29 engine; Phase 0 fixes only the claim rule.

## 13. Error and retry governance (master §30; prompt §9.3, §9.5)

- PROTO-ERR-001. The 13 categories of master §30 are the only structured error vocabulary; raw platform codes never surface (ARCH-AND-003).

| Category | Meaning | Retry class |
|---|---|---|
| `BluetoothDisabled` | adapter off | re-evaluate on adapter change, no send |
| `PermissionDenied` | platform/human gate blocks | re-evaluate on grant, never blind |
| `DeviceDisconnected` | session gone | no implicit auto-resend (§14) |
| `TransportUnavailable` | candidate channel absent/refused (§2) | retry candidates after a change event, never same-op blind |
| `GattFailure` | GATT-layer failure | reads only, capped |
| `RfcommFailure` | RFCOMM/SPP failure | reads only, capped |
| `ProtocolMismatch` | response contradicts the definition | no retry; downgrade to `UNKNOWN`/`INFERRED` (§7) |
| `UnsupportedFeature` | positively established absence (never absence of knowledge) | no retry |
| `WriteRejected` | write refused | no retry without diagnosis (§8) |
| `VerificationFailed` | read-back or ladder mismatch | no retry; demote (§5, §6) |
| `Timeout` | no answer in the allowed window | reads may retry, capped; writes resolve by reading instead (PROTO-ERR-002) |
| `FirmwareMismatch` | outside the recorded compatible range | no retry; claims revoked (§7) |
| `CodecUnavailable` | codec not obtainable now | re-evaluate on negotiation change |

- PROTO-ERR-002. Worked example (prompt §9.5): `SET ANC` → `Timeout` → DO NOT send `SET ANC` again → READ CURRENT ANC → determine actual state. The read, not a resend, resolves the ambiguity.
- PROTO-ERR-003. Reads may have controlled retries: capped attempts with a documented budget (master §30, §43). Side-effecting writes MUST NOT retry blindly (master §30; ARCH-QUAL-009).
- PROTO-ERR-004. `UnsupportedFeature` requires positively established absence; "no answer" is `Timeout`/`ProtocolMismatch`/`TransportUnavailable` and keeps `UNKNOWN` (master §30, §53).
- PROTO-ERR-005. Recovery ownership is Phase 34 (Failure & Recovery Engine, master §48); Phase 0 fixes only the classification and claim rules.

## 14. Device session and lifecycle rules (master §5)

- PROTO-SESSION-001. An active session is a connected device; a saved device is one the user explicitly added. They are distinct categories (master §5; REQ-P0-004; ARCH-STATE-007).
- PROTO-SESSION-002. On disconnect the device leaves the active list unless saved (master §5); capabilities keep the state and level already evidenced — disconnection promotes nothing (§5 of this document); post-reconnect reads are fresh, and stale values are labelled stale and never offered as control targets (ARCH-STATE-006).
- PROTO-SESSION-003. The application must not permanently retain every device encountered (master §5); scanning discovery records are transient, not a device database (master §28).
- PROTO-SESSION-004. On reconnect a saved device re-runs fingerprint → transport candidates → capability read from evidence, not from what the last session concluded (§5).
- PROTO-SESSION-005. Session rules are implemented in Phases 3–4; audio/codec state belongs to `audio-governance.md` (D5); permissions and background operation to `security-governance.md`.

## 15. Open questions deliberately left to later phases

| ID | Undecided | Owner |
|---|---|---|
| OQ-PROTO-01 | Concrete transport contract: characteristics, socket parameters, callback shape | Phase 6 |
| OQ-PROTO-02 | How the `EarbudProtocol` surface (master §9) becomes interfaces/data models | Phase 7 |
| OQ-PROTO-03 | Negotiation representation (names, keys, versioning of the capability vocabulary) | Phase 7 |
| OQ-PROTO-04 | Capability engine storage and observation mechanics | Phases 8–9 |
| OQ-PROTO-05 | Executable persistence ladder: timeouts, retry budgets, operator UX of a real disconnect | Phases 17–18 |
| OQ-PROTO-06 | Protocol database schema and separation mechanism (OQ-03 there; master §56) | Phase 22 |
| OQ-PROTO-07 | Protocol Laboratory architecture (master §26; OQ-08 there) | Phase 20 |
| OQ-PROTO-08 | Vendor extension packaging and community contribution rules | Phases 23, 40, 43 |
| OQ-PROTO-09 | Dependency-rule authoring and conflict UI | Phase 29 |
| OQ-PROTO-10 | Which model-by-model claims populate the per-device support record (D8) | Phase 0 `risk-register.md` plus the proposed `device-support-matrix.md`; extended in Phases 19 and 39 |

## 16. Acceptance mapping (prompt §22 "Protocol"; prompt §21 checklist)

| Acceptance item | Where satisfied |
|---|---|
| transport abstraction documented | §2 (PROTO-XPORT-001…008) + ARCH-XPORT-001…003 |
| protocol abstraction documented | §3 (PROTO-ABST-001…006), §4 (PROTO-ID-001…006), §5 (PROTO-CAP-001…005) |
| vendor extension strategy documented | §11 (PROTO-VENDOR-001…005), §9 (PROTO-DB-004) |
| protocol safety documented | §6 (PROTO-PERSIST-001…008), §8 (PROTO-RESEARCH-001…006), §10 (PROTO-NOMAGIC-001…004), §13 (PROTO-ERR-001…005) |
| prompt §21 "Protocol safety", "Hardware verification", "Persistence verification" documented; prompt §21 "No implementation accidentally started" | §7 (PROTO-VERIFY-001…007), §6, §8; §1 (PROTO-DOC-002, PROTO-DOC-004), §8 (PROTO-RESEARCH-005) |
| prompt §22 per-device support evidence | §7 (PROTO-VERIFY-006), §15 (OQ-PROTO-10), D8 |

End of `docs/phases/phase-0/protocol-governance.md`. Phase 0 remains governance-only; nothing here authorises transport code, protocol adapters or research tooling.
