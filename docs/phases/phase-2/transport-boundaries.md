# Phase 2 — Transport boundaries

Agent E deliverable (Phase 2 prompt section 4, "Transport Architecture Specialist").

| Item | Value |
|---|---|
| Document | `docs/phases/phase-2/transport-boundaries.md` |
| Describes | `core/src/main/kotlin/com/omnibuds/core/transport/*.kt` as it stands at the Phase 2 boundary |
| Binding sources | `docs/MASTER-CONTEXT.md` sections 8, 20, 30; `docs/phases/phase-0/protocol-governance.md` sections 2, 3, 13; `docs/phases/phase-0/specs.md` sections 1–5; `docs/phases/phase-1/decisions.md` ADR-P1-003, ADR-P1-004, ADR-P1-021; `docs/phases/phase-2/decisions.md` ADR-P2-001, ADR-P2-004, ADR-P2-008 |
| Authorises | Nothing beyond Phase 2 prompt section 5.6 ("infrastructure boundaries only") |
| Verification ceiling | **No transport in this document has been opened, probed, read or written.** The strongest claim any Phase 2 transport record can carry is `INFERRED`; unit tests exercise the *value types*, which is `LAB_TESTED` evidence about the types and no evidence at all about a channel (PROTO-VERIFY-001) |

---

## 1. Scope: boundaries, not channels

Phase 2 prompt section 5.6 asks for transport interfaces and states that "at this stage, these are
infrastructure boundaries only". Section 6 then removes every activity that would make a boundary
real: no GATT service discovery, no characteristic enumeration, no characteristic writes, no RFCOMM
protocol communication, no vendor packet encoders or decoders, no codec work, no battery or firmware
retrieval. `BluetoothOperation` records the same line in domain data: `TRANSPORT_GATT_OPEN` and
`TRANSPORT_RFCOMM_OPEN` carry `authorizedInPhase = 6`, and `LE_AUDIO_SESSION_INSPECTION` carries
`authorizedInPhase = 10`.

The deliverable is therefore a vocabulary, not a mechanism. Two consequences are worth stating
before any reviewer mistakes them for gaps:

1. **`open()`/`close()`/`exchange()` remain inherited and unimplemented.** `TransportContract`'s own
   documentation says nothing in `:core` may implement it; an implementation today could only be a
   double that reports a working channel, which is what ADR-P1-013 and Phase 1 prompt section 27
   forbid. Test doubles live in test source.
2. **`probeAvailability()` has no caller.** It is declared so that Phase 6 does not have to invent
   the shape of an availability answer, and so that the question a boundary can be asked today is
   exactly the honest one. `TransportBoundariesTest.noPhaseTwoTransportCodeOpensOrProbesAChannel`
   asserts both halves mechanically: the symbol is declared once, in `BluetoothTransport.kt`, called
   nowhere, and no GATT/RFCOMM/socket API name appears in a code line of this package.

---

## 2. The boundary set as implemented

| Type | File | What it pins | Members it adds | Opening phase |
|---|---|---|---|---|
| `BluetoothTransport` | `transport/BluetoothTransport.kt` | the Bluetooth-shaped reading of `TransportContract.kind` | `override val kind: TransportKind`, `suspend fun probeAvailability(): OperationOutcome<TransportAvailability>` | Phase 6 |
| `GattTransport` | `transport/GattTransport.kt` | `TransportKind.GATT` | none | Phase 6 |
| `RfcommTransport` | `transport/RfcommTransport.kt` | `TransportKind.RFCOMM` | none | Phase 6 |
| `ClassicTransport` | `transport/ClassicTransport.kt` | `TransportKind.CLASSIC_BLUETOOTH` | none | Phase 6 (SDP/classic metadata visible from Phase 3) |
| `BleTransport` | `transport/BleTransport.kt` | the BLE link, no control channel | none | Phase 3 for link facts, Phase 6 for a channel |
| `LeAudioTransport` | `transport/LeAudioTransport.kt` | `TransportKind.LE_AUDIO` | none | Phase 10 inspection; Phase 6 at the earliest for any channel |
| `TransportBoundary` | `transport/TransportBoundary.kt` | one candidate row: kind + availability + evidence tier + note | value type | usable from Phase 3 onward |
| `TransportNegotiation` | `transport/TransportNegotiation.kt` | offered-vs-selected, plus `preferring(order)` and `isUndetermined` | value type | Phase 4/6 |

Every sub-interface extends `BluetoothTransport`, which extends `TransportContract`; the assertion
is checked in `TransportBoundariesTest` by a `List<KClass<out BluetoothTransport>>` literal, which
compiles only if the subtyping holds. No per-transport protocol method was invented.

### 2.1 Three of these are honest thin markers, deliberately

`BleTransport`, `LeAudioTransport` and `ClassicTransport` add nothing. Each file says so in its own
KDoc, together with the specific members that were considered and rejected, and the fabrication each
would have been:

- **`BleTransport`** — BLE is a link layer and an advertising regime, not a control channel; a
  vendor control service over BLE *is* GATT. `TransportKind` has no `BLE` constant for exactly this
  reason, and adding one would give one physical link two channel identities. Phase 6 either gives
  this interface distinct meaning or retires it in favour of `GattTransport`. This is also the one
  place where the implementation **disagrees with the prompt**: section 5.6 sketches
  `BleTransport : BluetoothTransport` as a sibling of `GattTransport`, and the architecture audit
  (§3.1) recommended refusing the pair. The sibling is declared because the prompt named it, but it
  is declared empty and the disagreement is recorded here rather than papered over with an invented
  member.
- **`LeAudioTransport`** — master section 20 treats LE Audio as "an architecture in its own right,
  not an A2DP codec", so it earns a boundary; but any member would model an unexecuted API surface,
  and prompt section 5.5 forbids claiming LE Audio is active because an API exists. Its availability
  facts live in the platform-facts area (`PlatformFeature.LE_AUDIO`, `ApiAvailability`,
  `BluetoothPlatformCapabilities.candidateTransports`), which this package may name but may not
  import: ADR-P2-001 registers that area at the same layer as `transport`, and ADR-P1-003 allows an
  import to point only strictly downward.
- **`ClassicTransport`** — a classic path that is not SPP means SDP records, L2CAP or a
  vendor-defined classic socket. Each of those needs a service record, a channel number or a socket
  parameter; naming any of them would be Phase 6 mechanics (OQ-PROTO-01) resting on a UUID that
  nobody has observed (PROTO-NOMAGIC-002).

`GattTransport` and `RfcommTransport` are equally thin. `RfcommTransport`'s KDoc carries the reason
this whole abstraction exists: a device that uses BLE for advertisements and RFCOMM for control is
ordinary, not an edge case (master section 8, ADR-P0-003), and RFCOMM delivers an unframed byte
stream, so a "send one command" member would assert a framing the protocol layer has not defined
(PROTO-ABST-006).

---

## 3. What a boundary is allowed to claim

The rules above are prose. `TransportBoundary` and `TransportNegotiation` turn the two that matter
most into construction-time failures, which is where the tests attack them.

| Invariant | Where | The bug it prevents | Test |
|---|---|---|---|
| A row cannot report `available = true` below `LAB_TESTED` | `TransportBoundary.init` | "the vendor service UUID was observed, therefore this channel works" — an inference promoted to a capability (PROTO-RESEARCH-003, ADR-P0-001) | `aBoundaryCannotClaimAnAvailableChannelOnInferenceOrCodeExistenceAlone` |
| `kind` must equal `availability.kind` | `TransportBoundary.init` | one transport silently standing in for another (PROTO-XPORT-003, PROTO-XPORT-005) | `oneBoundaryRowCannotDescribeTwoDifferentChannels` |
| A refusal carries its category; availability and reason stay mutually exclusive | inherited from `TransportAvailability` | a swallowed failure, i.e. "unavailable" with no recorded reason (specs.md section 2.2) | `aRefusedCandidateIsLegalAtEveryEvidenceTier` |
| `notes` is null or non-blank | `TransportBoundary.init` | a blank note reading like a redacted reason (ADR-P0-016) | `aBlankNoteIsRejectedWhileAnAbsentNoteIsARealState` |
| `selected` must appear among the candidates | `TransportNegotiation.init` | choosing a transport a device never offered — "assume GATT" (PROTO-XPORT-001) | `negotiationRefusesASelectionThatWasNeverOffered` |
| `selected` must name a candidate that reports itself available | `TransportNegotiation.init` | a command addressed to a channel nobody established (PROTO-XPORT-007). **This half is stricter than the brief**, which required only membership; without it, a record could hold "selected: GATT" beside "GATT: refused" and a caller would have to re-read the rows to notice the contradiction | `negotiationRefusesASelectionItAlsoRecordsAsUnavailable` |
| Selection is the caller's order, never a property of the type | `preferring(order)` | a ranking heuristic that turns a retry into a different transport; no score, no name-based guess | `preferringUsesTheCallersOrderAndNeverItsOwn`, `preferringSkipsARefusedCandidateWithoutFallingThroughToOneThatWasNotAskedFor`, `preferringReturnsNullWhenNothingInTheCandidateListIsAvailable` |
| An empty or wholly-refused candidate list is `isUndetermined` | `isUndetermined` | absence read as `UNSUPPORTED_FEATURE` (PROTO-ERR-004, ARCH-XPORT-003) | `anEmptyCandidateListAndAnAllRefusedListAreBothUndetermined`, `everyTransportKindCanHoldABoundaryWithoutAssumingGattIsPresent` |

One invariant could **not** be expressed in the types and is documented instead: *each sub-interface
answers for exactly one `TransportKind`*. An interface cannot assert the value of an abstract
property it does not implement, and giving each one a body (`override val kind get() = …`) would be
declaring an implementation in the package that is forbidden to have one. It is therefore written
into each file's KDoc and reserved for the Phase 6 implementation, where a constructor parameter or
a `require` in the concrete type can enforce it.

---

## 4. Deferred implementation list

Which phase opens each transport, what is deliberately absent, and why nothing was implemented now.

| Transport | Opened / inspected by | Deliberately absent in Phase 2 | Why it is absent |
|---|---|---|---|
| GATT | Phase 6 — `BluetoothTransportLayer`, `TRANSPORT_GATT_OPEN` | service discovery, characteristic and descriptor enumeration, read/write, notifications and indications, MTU, connection-priority, the callback shape | prompt section 6 lists discovery, enumeration and writes as forbidden; OQ-PROTO-01 hands characteristics and callback shape to Phase 6; PROTO-RESEARCH-002 permits no write before step 7 of the research workflow, and no protocol exists yet to justify one |
| RFCOMM / SPP | Phase 6 — `TRANSPORT_RFCOMM_OPEN` | socket creation, channel/UUID selection, connect, read and write loops, framing, congestion handling | prompt section 6 forbids RFCOMM protocol communication; PROTO-XPORT-002 requires an *explicit* open attempt, which is a platform action; a framing member here would state a message boundary the protocol layer has not defined (PROTO-ABST-006) |
| Classic (non-SPP) | Phase 6; visible device/profile facts from Phase 3 | SDP record lookup, L2CAP channels, classic metadata reads | service discovery is forbidden in Phase 2 and PROTO-NOMAGIC-002 forbids a service UUID literal standing in for observed evidence |
| BLE (link) | Phase 3 — `DEVICE_DISCOVERY_SCAN` for advertisements; Phase 6 for a channel | scanning, filters, advertisement payload parsing, manufacturer-data decoding | discovery is Phase 3's scope; decoding manufacturer data would begin fingerprinting, which prompt section 6 forbids here |
| LE Audio | Phase 10 — `LE_AUDIO_SESSION_INSPECTION` | group/broadcast state, context type, ISO stream facts, LC3 configuration | prompt sections 5.5 and 6: an existing API is not evidence, and codec configuration belongs to Phase 10/12; master section 20 treats it as its own architecture |
| Vendor-specific control | Phase 20 Protocol Laboratory (research), Phase 23 vendor feature framework, first hardware claims at Phase 19 | any identification, encoding, decoding, command or capability of any vendor | prompt section 6 forbids vendor protocol work and reverse engineering; ADR-P0-003 requires research gating (PROTO-RESEARCH-001..006); ADR-P1-008 keeps brand conditionals out of the model — `TransportKind.VENDOR_SPECIFIC` is a mechanism kind with no mechanism behind it |
| All transports | — | the body of `probeAvailability()` | answering it requires reading adapter state, permission standing and platform features through the platform-facts seam and then combining them; that composition is the Phase 3/4 session and capability work, and a Phase 2 implementation would have to invent an answer |

Cross-cutting items also left open, each for a stated reason:

1. **No notification or event member.** `TransportContract` has no channel for asynchronous device
   indications; a notify-driven vendor path will need one. Adding it now would be a member on a
   released interface (Phase 1 specs section 10: an ADR-gated change) and is Phase 6 mechanics
   (architecture audit section 3.1). The gap is left stated, not filled.
2. **No transport factory.** A factory that can produce a transport is a channel that can be opened,
   which section 6 forbids; prompt section 5.9's "transport factories" is deferred to Phase 6 with
   the audit's recommendation (its section 3.2), because a Phase 2 factory is an unimplemented path
   that reads as implemented.
3. **No selection policy.** `preferring` takes the caller's order and holds none. Which order is
   correct per purpose is a session and protocol decision (PROTO-XPORT-004 fixes only the *step*
   order, not a preference between channels).
4. **No negotiation persistence.** `TransportNegotiation` is an in-memory value; storage belongs to
   the persistence contracts' phase and would need its own record identity rules.
5. **No error mapping from platform codes.** `TransportBoundary.refused(...)` accepts any
   `OmniBudsErrorCategory`, but nothing in Phase 2 maps a platform status to one: that mapping is
   ARCH-AND-003's job inside `:platform:android`, and the categories added by ADR-P2-004 stay
   platform-side until a probe exists to return them.
6. **`TransportKind` is unchanged.** No member was added. Adding one is an ADR-requiring change when
   it affects capability semantics (specs.md section 1.3), and none of Phase 2's findings needed it.

---

## 5. A2DP, AVRCP, HFP and LE Audio: profiles, boundaries, and the media path

Phase 2 prompt section 4.2 lists A2DP, AVRCP and HFP under Agent E's transport boundary brief, and
section 7 simultaneously requires that Phase 2 not intercept media audio, decode Bluetooth streams,
re-encode, alter routing, force or claim a codec, or change audio quality. Those two instructions are
reconciled by one distinction that Phase 0 already fixed: **a profile a device speaks is not a
control channel OmniBuds opens.**

- **A2DP, AVRCP and HFP have no `TransportKind`.** `protocol-governance.md` section 2 assigns them
  their roles and their traps: A2DP — "control code must not send commands over a media channel";
  AVRCP — "introspection ≠ vendor control"; HFP — "derived level is not vendor battery truth". The
  media and call side of the same facts is already modelled where it belongs, in
  `audio/AudioTransportKind.kt` (`CLASSIC_A2DP`, `HFP`, `LE_AUDIO`, `UNKNOWN`), and
  `TransportKind` deliberately describes only how a *control channel* reaches a device. The two
  vocabularies stay separate because a device can be controlled over GATT while audio runs over A2DP.
- **So they are not boundaries in this package, and giving them `TransportKind` values would be a
  claim.** A kind implies a channel OmniBuds opens; opening a media or call profile is precisely the
  interference section 7 and ADR-P0-002 ("OmniBuds stays outside the media audio path") forbid.
  Profile *availability* and *connection state* are platform facts (Phase 3's
  `PROFILE_CONNECTION_STATE_INSPECTION`), and audio transport and codec state arrive with the audio
  phases (10–14), under `audio-governance.md`: AUD-XPORT-004 requires per-transport state and bans
  cross-transport bleed, AUD-XPORT-005 bans any implied fallback between classic and LE Audio, and
  AUD-PATH-002 keeps media transport owned by Android and the Bluetooth stack.
- **LE Audio is the exception that proves the rule.** It appears on both sides: as
  `LeAudioTransport` (a control-transport boundary, empty) and as `AudioTransportKind.LE_AUDIO` (a
  media family carrying LC3). Master section 20 and AUD-XPORT-002 forbid collapsing the two, and the
  boundary's emptiness is what keeps the collapse from starting here.

What OmniBuds may eventually do with these profiles — inspect connection state, read negotiated codec
rung, surface quality mode — is read-only observation of state Android already holds, plus
configuration writes where a documented vendor or standard path exists. None of it involves entering
the media stream, and nothing in Phase 2 does any of it.

---

## 6. Tests, and which claims need real hardware

`core/src/test/kotlin/com/omnibuds/core/transport/TransportBoundariesTest.kt` holds the invariants
above; every case is Tier T1 (unit, no radio), and the fixtures are invented — a category appears
because a test needed a refusal, never because anything was observed. There is no
`GattTransportTest` or `RfcommTransportTest` in this phase: a test for a channel that cannot be
opened could only test a double, and doubles prove nothing about a radio (ADR-P1-013). Those suites
arrive with the phases that can open the channels they name.

Claims that remain impossible until hardware exists, and their honest tier today:

| Claim | Today | Needed to raise it |
|---|---|---|
| "this platform could offer RFCOMM" | `INFERRED` at best | an adapter plus a platform feature read, then a real open attempt (Phase 6) |
| "this device accepts GATT writes" | not representable | a protocol, a ladder, and a device (Phases 6–8, 17–18, 19) |
| "LE Audio works on this phone" | refused by construction | API availability is not hardware support (ADR-P2-008); Phase 10 inspection plus a device |
| "transport selection prefers X" | no such claim exists | a caller's stated order; nothing in `:core` holds one |

---

## 7. What later phases may build on without editing this package

- Phase 3 (Connected Device Detection) may fill `TransportBoundary.refused(...)` rows from platform
  facts and produce the first candidate lists.
- Phase 4 (Device Session & Lifecycle) owns the per-session transport set (PROTO-XPORT-003: the set
  belongs to the session, never to a device class or a vendor class).
- Phase 6 (Bluetooth Transport Layer) implements `open`/`close`/`exchange` and `probeAvailability`
  behind `:platform:android`, and decides the `BleTransport`/`GattTransport` question this document
  leaves open.
- Phase 7 onward may map protocols onto a `TransportKind` without touching these boundaries, because
  protocol semantics were deliberately kept out of them (PROTO-ABST-006).

That is the whole deliverable: the boundary set, the claim rules, and a written account of what is
missing and why. No device was contacted, no channel was opened, and no capability was claimed.
