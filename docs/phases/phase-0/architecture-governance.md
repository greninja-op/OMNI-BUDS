# OMNIBUDS — ARCHITECTURE GOVERNANCE (PHASE 0)

## 1. Purpose, authority and scope restriction

### 1.1 Purpose and authority

| Item | Value |
|---|---|
| Document | `docs/phases/phase-0/architecture-governance.md` |
| Status | Binding project-wide architecture rulebook |
| Superior source | `docs/MASTER-CONTEXT.md` (master §58: master source of truth) |
| Authorising prompt | `docs/phases/phase-0/execution-prompt.md` (Phase 0, §4 Agent 2) |
| Applies to | Every phase from Phase 1 to Phase 52, every sub-agent, every contribution |

- ARCH-DOC-001. This document SHALL bind all later phases. A later phase that cannot comply SHALL report the conflict to the orchestrator rather than proceed (master §38, §58).
- ARCH-DOC-002. Where this document and any phase prompt disagree, `docs/MASTER-CONTEXT.md` wins; this document wins over any phase-local document; no implementation document may relax an ARCH rule silently — only an accepted ADR may (§12).
- ARCH-DOC-003. Each ARCH rule is written so that a reviewer can verify it by reading files or inspecting a diff. A rule that cannot be checked is advisory, not governance; none of the rules below are advisory.
- ARCH-DOC-004. Phase 0 produces rules, not software. Every type, interface or symbol named below (for example `TransportManager`, `EarbudProtocol`, `ProtocolDefinition`) is illustrative prose inside this document and MUST NOT be created as a file, module or stub in Phase 0.
- ARCH-DOC-005. Names of conceptual layers in this document are not Gradle module names or package names. Module topology is Phase 1 work (§13).

### 1.2 Phase 0 scope restriction — subsystems Phase 0 must not implement

Phase 0 SHALL NOT implement any of the following — the 24 items below are exactly those enumerated in Phase 0 prompt §2, with none dropped or softened. A placeholder that pretends the subsystem works is a Phase 0 failure (prompt §2, §23).

| # | Forbidden in Phase 0 | # | Forbidden in Phase 0 |
|---|---|---|---|
| 1 | Bluetooth scanning | 13 | gestures |
| 2 | Bluetooth connection | 14 | battery communication |
| 3 | BLE/GATT communication | 15 | vendor protocols |
| 4 | RFCOMM | 16 | protocol reverse engineering |
| 5 | A2DP | 17 | Quick Settings |
| 6 | AVRCP | 18 | notifications |
| 7 | HFP | 19 | widgets |
| 8 | LE Audio | 20 | UI |
| 9 | codec configuration | 21 | database behavior |
| 10 | ANC | 22 | background Bluetooth services |
| 11 | transparency | 23 | device control |
| 12 | EQ | 24 | audio processing |

- ARCH-SCOPE-001. No Kotlin source, Gradle file, `AndroidManifest.xml`, resource, dependency, test execution or git mutation may be produced by Phase 0. Only markdown.

### 1.3 Canonical vocabulary (normative, no variants)

- ARCH-TERM-001. The following spellings are the only permitted forms across all documentation and future code.

| Vocabulary | Permitted members |
|---|---|
| `CapabilityState` | `UNKNOWN`, `UNSUPPORTED`, `READ_ONLY`, `SUPPORTED_VOLATILE`, `SUPPORTED_PERSISTENT`, `PERSISTENCE_VERIFIED` (master §10) |
| `CodecState` | `SUPPORTED`, `AVAILABLE`, `ENABLED`, `NEGOTIATED`, `ACTIVE`, `CONFIGURABLE` (master §15) |
| `VerificationLevel` / `ProtocolConfidence` | `INFERRED`, `IMPLEMENTED`, `LAB_TESTED`, `HARDWARE_VERIFIED`, `PERSISTENCE_VERIFIED` (master §27; underscores, never hyphens) |
| Error categories | `BluetoothDisabled`, `PermissionDenied`, `DeviceDisconnected`, `TransportUnavailable`, `GattFailure`, `RfcommFailure`, `ProtocolMismatch`, `UnsupportedFeature`, `WriteRejected`, `VerificationFailed`, `Timeout`, `FirmwareMismatch`, `CodecUnavailable` (master §30) |
| ID grammar | `REQ-<SCOPE>-<NNN>`, `TASK-<SCOPE>-<NNN>`, `TEST-<SCOPE>-<NNN>`, `ADR-<SCOPE>-<NNN>`, `RISK-<NNN>` (§12.1). This document adds `ARCH-<area>-<NNN>` for its own rules. |

- ARCH-TERM-002. "Selected" MUST NOT be used as a state name. Phase 0 prompt §17 "Codec Selected" means `ENABLED`.
- ARCH-TERM-003. `UNAVAILABLE` and `DISCONNECTED` (prompt §9.2) are session/availability descriptors, not `CapabilityState` members, and MUST NOT be added to `CapabilityState`.
- ARCH-TERM-004. Absence of information SHALL mean `UNKNOWN`. It MUST NOT be represented as `false`, `0`, empty string, `"none"`, `UNSUPPORTED` or a fabricated value (master §6, §23, §53).

### 1.4 Conflict register between the two authoritative sources

MASTER-CONTEXT.md wins in every row (master §58). These are recorded, not silently resolved.

| # | Conflict | Governing outcome |
|---|---|---|
| C1 | Phase 0 prompt §4 outputs use `docs/phase-0/...`; prompt §6–§14 and master §56 use `docs/phases/phase-0/...` | `docs/phases/phase-0/` is canonical. All Phase 0 governance documents live there; every sibling link in §12.3 uses that path. |
| C2 | Phase 0 prompt §16 uses `LAB-TESTED`, `HARDWARE-VERIFIED`, `PERSISTENCE-VERIFIED`; master §27 uses underscores | Underscore forms are canonical (ARCH-TERM-001). |
| C3 | Phase 0 prompt §17 lists 5 codec states including "Codec Selected" and omits `CONFIGURABLE`; master §15 lists 6 states | Six states, `ENABLED` replaces "Selected" (master §15). |
| C4 | Phase 0 prompt REQ-P0-007 verification chain omits the initial `READ` and the first `VERIFY`; master §24 includes both | Master §24's fuller chain governs (§8). |
| C5 | Phase 0 prompt §9.3 lists 10 error categories, omitting `GattFailure`, `RfcommFailure`, `FirmwareMismatch`; master §30 lists 13 | Thirteen categories are canonical (master §30). |
| C6 | master §23 says unknown battery SHALL be `null`; Phase 0 prompt §9.2 forbids `null` as a state substitute | Both bind: unknown battery MUST NOT become `0%` (master §23) and `null` MUST NOT be a general-purpose state sentinel (prompt §9.2). The encoding (typed `Unknown` alternative vs nullable field) is an open decision (§13, OQ-05); neither source may be weakened until an ADR settles it. |
| C7 | Phase 0 prompt §10/§11 use `P0-T001` / `P0-TEST-001`; master §44/§45 and the ID grammar require `TASK-<SCOPE>-<NNN>` / `TEST-<SCOPE>-<NNN>` | `TASK-P0-001`, `TEST-P0-001` are canonical; `P0-T001` is a non-conforming example, not a rule. |
| C8 | Phase 0 prompt §8.2's dependency chain ends at "Platform transport implementations" and omits vendor protocol; prompt §8.1 and the layer chain here include it | The full chain (§2) governs; prompt §8.2's key constraint — vendor protocol must never own global application state — is preserved verbatim as ARCH-LAYER-004. |

## 2. Architecture hierarchy and layer ownership

- ARCH-LAYER-001. The conceptual hierarchy SHALL be understood as Product → Architecture → Core abstractions → Platform implementations → Vendor protocols → Device implementations (prompt §8.1), realised in the runtime layer chain:

```text
presentation → application/state → core-domain → protocol abstraction
              → transport abstraction → platform implementation → vendor protocol
```

- ARCH-LAYER-002. Dependency arrows point downward only; the concrete implementations at the bottom are injected into the abstractions above them, never the reverse.
- ARCH-LAYER-003. Every architectural concern below has exactly one owning layer.

| Layer | Owns | Must not own |
|---|---|---|
| presentation | rendering of backend-provided state; user intent capture | capability truth, codec truth, protocol knowledge, retry policy |
| application/state | the single observable device-state snapshot; session orchestration; error surfacing | raw bytes, UUID literals, Android types |
| core-domain | capability model, device identity/fingerprint models, session and persistence rules, feature-dependency rules, error model | transport mechanics, Android/JVM APIs, UI concerns |
| protocol abstraction | operation contracts, parsers/encoders contracts, capability mapping per protocol | global mutable state, UI state, device persistence |
| transport abstraction | transport contract (open/send/receive/close), transport availability reporting | protocol semantics, command meaning |
| platform implementation | Android Bluetooth adapter, sockets, GATT, permissions, lifecycle, audio APIs | capability classification decisions |
| vendor protocol | byte-level command construction, response parsing for one vendor family | global application state, session lifetime, UI state |

- ARCH-LAYER-004. Vendor protocol code MUST NOT become the owner of global application state (prompt §8.2). A vendor implementation SHALL be stateless with respect to the device session, or hold only its own protocol-scoped transient state.
- ARCH-LAYER-005. Layer ownership maps onto sub-agent ownership as defined in `docs/phases/phase-0/sub-agent-orchestration.md` (master §37, §38, prompt §20). Two agents SHALL NOT own one layer.

## 3. Boundary catalogue

For each bounded area: responsibility, permitted dependencies, who must never depend on it, and its state owner. "Owner" is the single place that area's state is mutated.

| Area | Responsibility | May depend on | Never depended on by | State owner |
|---|---|---|---|---|
| Bluetooth/adapter platform layer | adapter state, bonded/connected device enumeration, permission gating, socket/GATT object creation | core-domain contracts, Android SDK | core-domain, protocol abstraction, vendor protocol, presentation | platform layer |
| Transport abstraction | one uniform open/send/receive/close contract over GATT, RFCOMM, Classic, A2DP/AVRCP/HFP control paths, LE Audio, vendor transports | core-domain only | presentation, vendor protocol internals | transport abstraction (per-session instance) |
| Protocol abstraction | operation-level contracts (identify, discover, read, write) and the negotiation of which operations exist | core-domain, transport abstraction | presentation, platform layer | core-domain (via capability engine) |
| Vendor protocol implementations | command encoding, response parsing, capability mapping for one vendor family | protocol abstraction, transport abstraction, protocol database reads | core-domain, presentation, other vendor implementations | protocol database (knowledge), never runtime global state |
| Capability engine | produce `CapabilityState` per capability with readable/writable/transport/protocol/persistence/verification metadata | core-domain, protocol abstraction | vendor protocol, transport | capability engine |
| Device session and lifecycle | active-session vs saved-device, connect/detect/discover/ready/disconnect transitions | core-domain, transport abstraction, capability engine | protocol abstraction, vendor protocol | device session holder in application/state |
| Device fingerprinting | build `DeviceFingerprint`, resolve protocol and transport candidates | core-domain, metadata supplied by platform layer | vendor protocol write logic | fingerprint store (read-only to others) |
| Audio/codec subsystem | `CodecState` per codec, audio transport state, sample rate/bit depth/bitrate/channel/quality mode as exposed | core-domain, platform audio APIs behind a boundary | presentation inference, vendor protocol | audio/codec subsystem |
| Battery | left/right/case level, charging state, asymmetry, unknown | core-domain, standard battery read path | codec/audio inference | battery state holder in application/state |
| Persistence engine | write-intent policy, read-back and reconnect verification requirements, persistence classification rules | core-domain, capability engine, protocol abstraction | presentation | persistence engine |
| Feature-dependency engine | conflicts, prerequisites, mutually exclusive modes, firmware and transport limitations | core-domain, capability engine, protocol database | UI-derived guesses | feature-dependency engine |
| Diagnostics | assemble a read-only report from other areas' state; error and warning log | all areas, read-only | any area as a truth source | owns none — diagnostics SHALL NOT be a state owner |
| UI layer (presentation, Quick Settings, notification, widget) | render backend-declared visibility and state; emit user intents | application/state only | core-domain, transport, protocol, vendor protocol | none — UI holds only view state |
| Protocol laboratory | discovery/exploration/logging tooling for authorized research | transport abstraction, protocol abstraction, platform layer | production runtime state | its own sandbox; MUST NOT own device session or global state |

- ARCH-BOUND-001. Diagnostics SHALL NEVER be a truth source: it reads, formats and reports; no other area may consume state that only exists in diagnostics output.
- ARCH-BOUND-002. Protocol laboratory code SHALL NOT attach to the production device session or write into the user device database; research findings enter the app only through the protocol database (master §26, §27).
- ARCH-BOUND-003. The protocol database (global knowledge) and the user device database (per-user saved devices) MUST remain separate boundaries; neither may be used to answer a question belonging to the other (master §28).
- ARCH-BOUND-004. Battery SHALL NOT be derived from codec or audio-path state, and codec state SHALL NOT be derived from battery state.

## 4. Dependency direction and forbidden dependencies

- ARCH-DEP-001. Dependencies SHALL point from presentation toward platform/vendor concreteness, and concrete implementations SHALL be supplied to abstractions at the boundary. No layer may import a type from a layer above it.
- ARCH-DEP-002. Presentation SHALL depend only on the observable state and intent sinks published by application/state.

The following dependencies are forbidden. Each is checkable by reading the affected sources or the diff.

| Rule | Forbidden dependency | Reviewer check |
|---|---|---|
| ARCH-DEP-003 | core-domain importing Android Bluetooth classes (`BluetoothAdapter`, `BluetoothDevice`, `BluetoothGatt`, `BluetoothGattCallback`, `BluetoothSocket`) | no `android.bluetooth.*` import outside the platform layer |
| ARCH-DEP-004 | transport abstraction or protocol abstraction naming a GATT-only assumption (e.g. `onCharacteristicWrite` in a shared contract) | contract signatures contain no GATT-specific callbacks |
| ARCH-DEP-005 | UI owning capability truth (a composable/view deciding a control is supported) | no `supported`/codec/feature branching inside presentation sources |
| ARCH-DEP-006 | vendor protocol reaching into UI state or application/state holders | vendor sources reference no session/view/state holder type |
| ARCH-DEP-007 | duplicated transport logic (same read/write sequence reimplemented per vendor or per screen) | one implementation per transport operation; a second copy is a defect |
| ARCH-DEP-008 | application/state or presentation holding raw protocol bytes or UUID literals | byte arrays and UUID literals appear only in vendor protocol + protocol database |
| ARCH-DEP-009 | persistence engine invoked directly from UI | persistence calls originate in application/state or the persistence engine |
| ARCH-DEP-010 | feature-dependency engine reading UI state to resolve a conflict | its inputs are capability/audio/firmware state only |
| ARCH-DEP-011 | one platform implementation depending on another platform implementation | cross-platform imports absent |
| ARCH-DEP-012 | capability engine depending on a concrete vendor class | it depends on protocol abstraction only |
| ARCH-DEP-013 | a giant manager class that owns more than one boundary in §3 | any class spanning two §3 rows shall be split |

## 5. Core/domain separation and Android isolation

- ARCH-AND-001. Android surfaces SHALL stay behind platform boundaries: Bluetooth APIs, runtime permissions, Quick Settings tiles, notifications, Activity/Service/WorkManager lifecycle, and audio APIs (`AudioManager`, codec-override surfaces) (prompt §8.3).
- ARCH-AND-002. Domain code SHALL receive those capabilities only through contracts declared by core-domain and implemented by the platform layer. Illustratively, the domain asks an "adapter state source" and a "permission gate"; it never asks an Android context.
- ARCH-AND-003. Android results SHALL cross the boundary as domain vocabulary: adapter off → `BluetoothDisabled`; denied permission → `PermissionDenied`; unavailable channel → `TransportUnavailable`. Raw Android status codes and request-result integers MUST NOT leak upward.
- ARCH-AND-004. The domain model MUST NOT become tightly coupled to Android classes (prompt §8.3); `java.*`/`javax.*` imports SHALL likewise stay out of core-domain so Phase 46 does not have to re-cut the boundary (master §51, prompt §8.4).
- ARCH-AND-005. Where Android does not permit an operation, the platform layer SHALL report the restriction and the app SHALL show it as a limitation — never as a capability (master §4: never promise functionality Android does not permit; master §21, §32, §35).
- ARCH-AND-006. Background behavior SHALL be designed to be compliant with Android background-execution restrictions before any phase claims it (master §35).

## 6. Transport and protocol abstraction boundaries

- ARCH-XPORT-001. No layer above the platform implementation SHALL assume every device uses BLE/GATT (master §8). GATT, RFCOMM, Classic Bluetooth, A2DP, AVRCP, HFP, LE Audio and vendor-specific control transports SHALL all be expressible as transports behind one contract (prompt REQ-P0-005).
- ARCH-XPORT-002. A device SHALL be able to use different transports for different purposes (BLE for advertisements, RFCOMM for control, A2DP for audio, a proprietary mechanism for configuration) without any layer assuming a single channel (master §8).
- ARCH-XPORT-003. The absence of a transport SHALL be reported as `TransportUnavailable`, distinct from `UnsupportedFeature`; one does not imply the other (master §30).
- ARCH-PROTO-001. The protocol abstraction SHALL expose operations as negotiable contracts; a vendor MUST NOT be required to implement every operation (master §9). Unimplemented operations SHALL resolve to `UNKNOWN` until discovery provides evidence, never to `UNSUPPORTED` by omission (master §53).
- ARCH-PROTO-002. Capability negotiation SHALL decide what exists for a connected device; compile-time presence of a vendor class SHALL NOT be evidence that the device supports an operation (master §9, §10).
- ARCH-PROTO-003. Common functionality gets a common interface; unique functionality gets a vendor extension — the lowest-common-denominator API is forbidden (master §13, prompt REQ-P0-012).
- ARCH-PROTO-004. Protocol research SHALL follow Identify → Discover → Read → Observe → Understand → Validate → Write → Read back → Disconnect → Reconnect → Verify; arbitrary writes and assumed UUID/byte meanings are forbidden (prompt §15, master §25, §26). See `docs/phases/phase-0/protocol-governance.md` for the procedure and its safety gates.

## 7. Capability-model boundary

- ARCH-CAP-001. The backend SHALL decide what the UI may show; the UI MUST NOT infer support from appearance, brand, model string or the presence of a vendor class (master §11, prompt §18).
- ARCH-CAP-002. A capability SHALL be modelled as `CapabilityState` plus metadata (readable, writable, transport, protocol, requires-connection, persistence behavior, verification status) — never as a boolean `supported` (master §10, prompt REQ-P0-006).
- ARCH-CAP-003. `UNKNOWN` MUST NEVER be collapsed into `UNSUPPORTED` ("not discovered" is not "absent") nor into any `SUPPORTED_*` ("not verified" is not "supported") (master §53, prompt REQ-P0-002).
- ARCH-CAP-004. An `UNKNOWN` capability SHALL render as hidden or explicitly unknown; it MUST NOT render as enabled or as a working control (master §11).
- ARCH-CAP-005. `VerificationLevel` values SHALL never be conflated: a capability at `IMPLEMENTED` is not `LAB_TESTED`, and `LAB_TESTED` is not `HARDWARE_VERIFIED` (prompt §16, master §27, §54).
- ARCH-CAP-006. `HARDWARE_VERIFIED` SHALL require all eight conditions of master §54, including real-hardware response, read-back and — where persistence matters — reconnect testing.
- ARCH-CAP-007. Feature claims SHALL be reported per model with verified / not verified / unsupported lists; blanket statements such as "OmniBuds supports Sony" are forbidden (master §55).
- ARCH-CAP-008. Software-only features SHALL be explicitly labelled as software features; software ANC, fake transparency, fake LDAC, fake spatial audio, fake hardware EQ and similar are forbidden (master §2, §22).

## 8. Persistence boundary

- ARCH-PERSIST-001. A successful write SHALL NOT, by itself, establish `SUPPORTED_PERSISTENT`. Post-write read-back before a disconnect establishes only "written this session" and MUST NOT be labelled as persistence (master §24, prompt REQ-P0-007).
- ARCH-PERSIST-002. `PERSISTENCE_VERIFIED` SHALL be granted only after the full master §24 sequence: READ → WRITE → READ BACK → VERIFY → DISCONNECT → RECONNECT → READ AGAIN → VERIFY, using a real disconnect/reconnect cycle. A simulated or in-memory "fake reconnect" SHALL NOT satisfy this rule.
- ARCH-PERSIST-003. If configuration does not survive reconnect, the capability SHALL be reclassified to `SUPPORTED_VOLATILE` or the appropriate state, and the downgrade SHALL be recorded (master §24).
- ARCH-PERSIST-004. Persistence rules are per capability and per device fingerprint; a device's history of volatile behavior MUST NOT be assumed for another model, and never-persisted settings MUST NOT be silently cached as if they persisted (master §24, §51).
- ARCH-PERSIST-005. The executable verification procedure, timeout and reconnect policy is specified in `docs/phases/phase-0/protocol-governance.md` and implemented in Phases 17–18; this document fixes only the boundary that no write-only evidence may be presented as persistence.

## 9. State-management rules

- ARCH-STATE-001. Exactly one application/state component SHALL own the observable device-state snapshot; every other area publishes input to it and reads output from it. Two sources for the same field is a defect.
- ARCH-STATE-002. State SHALL be expressed as explicit enumerated states (`UNKNOWN`, `UNAVAILABLE`, `DISCONNECTED`, `SUPPORTED`, `UNSUPPORTED`, and the §1.3 vocabularies) rather than nullable booleans (prompt §9.2). `Boolean?` MUST NOT encode capability, codec or persistence truth.
- ARCH-STATE-003. Hidden mutable global state is forbidden (master §51, prompt §18). Shared mutable state SHALL live in the owning boundary of §3 and be observable, not a singleton cache reached from arbitrary call sites.
- ARCH-STATE-004. UI-derived business logic is forbidden: transitions, retry decisions, capability classification and codec inference SHALL be computed below presentation (master §51).
- ARCH-STATE-005. Every state transition relevant to a phase SHALL be written down in that phase's `specs.md` before implementation (master §42, §43); implicit state behavior is not acceptable.
- ARCH-STATE-006. Reads of a disconnected device's state SHALL report `DISCONNECTED`/`DeviceDisconnected` and MUST NOT retain stale values as current (master §5).
- ARCH-STATE-007. Active-session devices and explicitly saved devices SHALL be distinct state categories; the application MUST NOT permanently retain every temporarily encountered device (master §5, prompt REQ-P0-004).

## 10. Future KMP compatibility rules

- ARCH-KMP-001. The Phase 1 module design SHALL satisfy the rule that everything in the shared list below is free of Android and JVM-only imports and is expressible without the Android SDK.

| Shared (portable) | Platform-specific (per-OS) |
|---|---|
| Device identity, device fingerprint models | Bluetooth transport |
| Capability model and classification rules | Android permissions |
| Device state and audio state models | Quick Settings |
| Protocol definitions, packet parsing, state machines | Android notifications |
| Persistence rules, validation logic | Windows / macOS / Linux Bluetooth |
| Diagnostics models | Desktop system integration |

- ARCH-KMP-002. Every boundary in §3 SHALL be drawn so that swapping the platform implementation requires no change in shared code; the injection point is the platform boundary.
- ARCH-KMP-003. Mechanisms for the shared/platform split (expect/actual, interface injection, per-target source sets) are Phase 1 decisions; Phase 0 rules bind the outcome, not the mechanism.
- ARCH-KMP-004. To keep Phase 46 cheap, the following SHALL be avoided now: Android types in domain models, `java.util`/`java.time` types crossing boundaries, hard-coded Android-only assumptions in state machines, protocol parsing expressed via Android APIs, and any capability model that requires a Context to evaluate.
- ARCH-KMP-005. Android remains first; KMP compatibility is an architectural target, not a licence to add desktop modules now (master §1, prompt ADR-P0-008).

## 11. Code quality rules

| ID | Rule | Rationale (one line) |
|---|---|---|
| ARCH-QUAL-001 | No giant manager class may own more than one §3 boundary or exceed a single stated responsibility. | One owner per concern keeps state traceable and testable (prompt §18). |
| ARCH-QUAL-002 | No magic protocol values: `ProtocolDefinition`, `CommandDefinition`, `ResponseDefinition`, `Parser`, `Encoder`, `CapabilityMapping` SHALL hold command structure; scattered `0x01`/`0x0A`/`0xFF` literals are forbidden. | Structural definitions are the documented form of protocol knowledge (master §52). |
| ARCH-QUAL-003 | No vendor logic in UI: presentation MUST NOT branch on manufacturer, model string or vendor byte payloads. | Vendor truth belongs to capability negotiation, not screens (prompt §18, master §11). |
| ARCH-QUAL-004 | No UI business logic: views compose state and emit intents only. | Prevents UI-derived capability truth (master §51). |
| ARCH-QUAL-005 | No duplicated transport implementations: one implementation per transport operation, reused by every vendor. | Divergent copies make Bluetooth bugs unfixable (prompt §18). |
| ARCH-QUAL-006 | No hidden mutable global state; shared state lives in its §3 owner and is observable. | Hidden globals break verification claims (prompt §18, master §51). |
| ARCH-QUAL-007 | No blocking Bluetooth work on the main thread: GATT/RFCOMM/classic calls SHALL run off the UI dispatcher with defined timeout and cancellation. | Main-thread Bluetooth stalls the UI and leaks sessions (prompt §18, §9.4). |
| ARCH-QUAL-008 | No arbitrary writes: writes to unverified characteristics are forbidden; unknown devices start read-only (master §25, prompt REQ-P0-008). | Blind writes can brick or misconfigure hardware. |
| ARCH-QUAL-009 | No retry of side-effecting writes after `Timeout`: re-read the actual state instead (prompt §9.5, master §30). | Retrying `SET ANC` may double-toggle the device. |
| ARCH-QUAL-010 | No fake capabilities: absence of evidence yields `UNKNOWN`, never a simulated control (master §2). | Simulation violates the founding product rule. |
| ARCH-QUAL-011 | No fabricated metadata or numbers: unknown fields stay unknown; battery unknown is never `0%` (master §6, §16, §23). | Invented values become user-visible lies. |
| ARCH-QUAL-012 | No magic UUIDs scattered through code; UUIDs live in protocol/fingerprint definitions with documentation (master §51). | Unnamed UUIDs cannot be audited or reverified. |
| ARCH-QUAL-013 | No unsafe background services: background behavior SHALL respect Android restrictions and be lifecycle-safe (master §35, §51). | Aggressive backgrounding is rejected by the platform and by users. |
| ARCH-QUAL-014 | No hard-coded assumptions about devices: model/brand/firmware conditions SHALL be data-driven via the protocol database, never `if (model == "...")` in domain code (master §51). | Hard-coding collapses universality and hides verification status. |
| ARCH-QUAL-015 | No silent architecture changes: any boundary or vocabulary change goes through §12 (master §38, §58, prompt §18). | Undocumented drift makes later phases unreviewable. |

## 12. Change governance

### 12.1 Identifier grammar

`REQ-<SCOPE>-<NNN>`, `TASK-<SCOPE>-<NNN>`, `TEST-<SCOPE>-<NNN>`, `ADR-<SCOPE>-<NNN>`, `RISK-<NNN>`; scope is a short uppercase area tag (`P0`, `BT`, `CORE`, `AUDIO`, `PROTO`, `UI`, `TEST`, `SEC`, `KMP`). `ARCH-<area>-<NNN>` is reserved for this document's rules. IDs SHALL be immutable — never reused, never renumbered; a retired rule keeps its ID and is marked withdrawn with the ADR that withdrew it.

### 12.2 Proposing an architecture change

- ARCH-GOV-001. An architecture change SHALL follow: agent identifies conflict → reports it (no silent edit) → orchestrator evaluates → architecture decision → decision documented (master §38).
- ARCH-GOV-002. Every change to a boundary in §3, a dependency rule in §4, a vocabulary entry in §1.3, or a rule in §7–§11 SHALL be recorded as `ADR-<SCOPE>-<NNN>` in `docs/phases/phase-0/decisions.md` (or that phase's `decisions.md`) with decision, reason, alternatives and affected ARCH rules (master §47).
- ARCH-GOV-003. Only the orchestrator — or an agent explicitly delegated by it — may change shared boundaries: layer chain, dependency direction, capability vocabulary, persistence classification rules, transport/protocol contracts. Sub-agents propose; they MUST NOT unilaterally rewrite a shared boundary or another agent's files (master §38, prompt §5, §20).
- ARCH-GOV-004. Conflicts between a phase prompt and this document SHALL be surfaced in the phase report; where master and phase prompt conflict, master wins and the conflict is added to §1.4 (master §58).
- ARCH-GOV-005. Cross-file ownership, review and integration rules are in `docs/phases/phase-0/sub-agent-orchestration.md`; commit and branch rules are in `docs/phases/phase-0/git-workflow.md`.
- ARCH-GOV-006. Phase 0 SHALL NOT advance into Phase 1 or create implementation scaffolding "for convenience" (master §50, prompt §25).

### 12.3 Sibling Phase 0 documents

`docs/phases/phase-0/execution-prompt.md`, `docs/phases/phase-0/requirements.md`, `docs/phases/phase-0/design.md`, `docs/phases/phase-0/specs.md`, `docs/phases/phase-0/task-list.md`, `docs/phases/phase-0/test-plan.md`, `docs/phases/phase-0/validation.md`, `docs/phases/phase-0/decisions.md`, `docs/phases/phase-0/risk-register.md`, `docs/phases/phase-0/repository-audit.md`, `docs/phases/phase-0/testing-governance.md`, `docs/phases/phase-0/security-governance.md`, `docs/phases/phase-0/audio-governance.md`, `docs/phases/phase-0/bluetooth-governance.md`, `docs/phases/phase-0/protocol-governance.md`, `docs/phases/phase-0/sub-agent-orchestration.md`, `docs/phases/phase-0/git-workflow.md`, `docs/templates/`. Where this document and a sibling disagree, §1.4 and master govern; naming and path conventions follow C1.

## 13. Open questions deliberately left to later phases

No agent SHALL assume an answer to any item below; an unstated assumption is `UNKNOWN`, not a default.

| ID | Undecided | Owner |
|---|---|---|
| OQ-01 | Gradle module topology, module names, source-set layout — explicitly Phase 1 work; the layers in §2 are conceptual only | Phase 1 |
| OQ-02 | DI framework (or hand-built graph) and how platform boundaries are injected | Phase 1 |
| OQ-03 | Persistence technology for the protocol database and the user device database (and their physical separation, per ARCH-BOUND-003) | Phases 4/22 |
| OQ-04 | UI framework choice, and how Quick Settings / notification / widget surfaces map onto the presentation boundary | Phases 25–27, 49 |
| OQ-05 | Encoding of "unknown" for per-field numeric state such as battery, pending resolution of conflict C6 | Phase 16, with an ADR |
| OQ-06 | Identifier and UUID value types for `DeviceFingerprint` and protocol definitions | Phase 1 / Phase 5 |
| OQ-07 | Coroutine/dispatcher policy, timeouts, retry budgets and Flow/StateFlow shape (prompt §9.4 scope) | Phase 1 specs |
| OQ-08 | Whether the protocol laboratory ships inside the app or as a separate tool target | Phase 20 |
| OQ-09 | Naming conventions for classes, packages, tests, protocols and capabilities (prompt §9.1 belongs to `specs.md`) | Phase 0 `specs.md` / Phase 1 |
| OQ-010 | Vendor family list and the first fully supported device | Phase 19 |

---

End of `docs/phases/phase-0/architecture-governance.md`. Phase 0 remains governance-only; nothing here authorises implementation work.
