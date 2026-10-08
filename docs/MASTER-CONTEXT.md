# OMNIBUDS — MASTER PROJECT CONTEXT, ARCHITECTURE & PLANNING PROMPT

## Repository status (updated 2026-10-03)

This file is the master source of truth for OmniBuds planning and architecture.

- **Phases 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 and 11 are complete and validated.** Phase 0 fixed the governance rules; Phase 1 built the build foundation and the platform-independent domain contracts; Phase 2 established the Android Bluetooth foundation — adapter inspection and observation, the version-aware permission model, platform capability reporting, transport boundaries, structured platform errors and the Android mechanism in `:platform:android`; Phase 3 established connected-device observation — a three-axis platform vocabulary (link, bond, availability), a profile-union reconciliation engine, snapshot/event ordering, duplicate suppression by a self-redacting key, live paired-census reporting and the Android mechanism behind a testable seam; Phase 4 established device sessions — a single authoritative engine (`DeviceSessionEngine`) that turns the projection into per-device session state over Phase 1's `ConnectionState` machine, with a documented reconnection policy, key-based duplicate prevention, ambiguity kept as a state rather than merged, bounded events, and no new reach into the phone; Phase 5 established device fingerprinting and identification — typed identity signals with six stated qualities, a versioned normaliser (trim/collapse/case-fold only), a deterministic pure matcher that reuses Phase 1's `DeviceFingerprint`, a seven-outcome `IdentificationResult` whose confidence is computed from independent evidence rather than copied from a rule, an evidence-gated registry that **ships empty** with a test asserting it, and session enrichment that keeps a matched conclusion separate from a reported identity (ADR-P5-001 … 012); Phase 6 established the Bluetooth transport layer — it *filled* the boundaries Phases 1/2 left empty: a `TransportState` lifecycle machine (never `CONNECTED` before the platform confirms it, `CLOSED`/`UNAVAILABLE` terminal), the deferred GATT characteristic and RFCOMM stream operation surfaces addressed by caller-supplied data with no device UUID and no framing, a `TransportResolver` contract whose only implementation selects nothing (the safe unknown), concurrency/ownership with one operation in flight and `NonCancellable` teardown, error mapping onto the existing categories with their retry class intact, and a real Android `BluetoothGatt`/`BluetoothSocket` mechanism behind a framework-free handle seam — compiled and seam-tested, never run on a radio (ADR-P6-001 … 012); Phase 7 established the protocol abstraction engine, **reusing** the protocol knowledge model Phases 1/2 built (`ProtocolDefinition`, the empty `ProtocolRegistry`, `CommandDefinition`/`EffectClass`, capability interfaces, `VendorExtension`) and adding only the missing engine: a `ProtocolState` lifecycle machine as a *third* axis (READY only after initialize, CLOSED terminal), a `ProtocolSession`/`ProtocolTransportAdapter` runtime contract reaching channels only through transport abstractions, a deterministic `ProtocolResolver` over Phase 5 evidence with six safe outcomes that never selects by brand or connects, bounded one-shot `ProtocolCommand`/`ProtocolResponse` correlation with timeout-never-resent retry safety, a cancellation-safe `ProtocolEvent` stream keeping requested ≠ confirmed state, and reconciliation of prompt §8/§11's vocabulary onto the master `VerificationLevel`/`EffectClass` rather than a fork; the registry ships empty and no production session is implemented (ADR-P7-001 … 010). Phase 8 established the capability discovery engine, **reusing** the capability model Phases 1/2 built (`FeatureId`, `FeatureCapability`, `DeviceCapabilities`, `CapabilityState`, `VerificationLevel`, `VendorExtension`, `OmniBudsErrorCategory`) and adding only what it lacks: `CapabilityAvailability` as the one genuinely-new "right now" dimension carried beside `FeatureCapability` (never written into it), a `CapabilityEvidence` provenance record whose `EvidenceKind` caps the verification rung it may claim, a per-protocol `CapabilityDependency` + `DependencyValidator` that detects cycles and blocks a dependent's availability on a missing prerequisite without ever inferring or enabling one, a fourth `DiscoveryState` lifecycle (complete ≠ partial ≠ failed, terminal states never revive), an immutable deterministic `CapabilitySnapshot` that *wraps* `DeviceCapabilities` and surfaces `PartialFailure`/`UnresolvedConflict`, and a `CapabilityDiscoveryEngine` that reads only through a handed-in `CapabilityDiscoverySource` so the layer-2 capability area never imports the layer-4 protocol (ADR-P8-005); a failed read keeps its feature `UNKNOWN` not `UNSUPPORTED`, reads fold through the evidence ladder so a weak record never silently overwrites a strong one, a mislabeled response is refused rather than crashing the pass, and cancellation preserves what was gathered. The engine ships with no protocol, no production discovery source and no device claim, ceiling `IMPLEMENTED`/`LAB_TESTED` (ADR-P8-001 … 010). Phase 9 established the hardware feature engine, **reusing** the models Phases 1/2 and 8 built (`FeatureId`, `FeatureCapability`, `DeviceCapabilities`, `CapabilitySnapshot`, `CapabilityAvailability`, `CapabilityDependency`/`DependencyValidator`, `ConfigurationValue`, `FeatureReadSupport`/`FeatureWriteSupport`, `OperationOutcome`, `OmniBudsErrorCategory`/`RetryClass`) and adding only what control needs: five new `ConfigurationValue` shapes (FLOAT, RANGE, STRUCTURED, BITMASK, CUSTOM) rather than a forked value hierarchy (ADR-P9-001), a six-state `FeatureState` control machine (UNKNOWN/AVAILABLE/PENDING/CONFIRMED/FAILED/UNAVAILABLE) that keeps requested values separate from device-confirmed ones while `CapabilityState` is untouched (ADR-P9-003), a 10-step `FeatureValidator` (capability gating, access, shape/constraints refused never clamped, dependencies, conflicts, port support, transport establishment), a six-kind dependency/conflict evaluator reusing `DependencyValidator` for requires-edges with cycle detection that refuses rather than silently disabling, a handed-in `FeatureProtocolPort` seam the layer-5 feature area uses to reach the device so it never imports the protocol or transport layers (ADR-P9-002), a single-owner reactive `FeatureStateRepository`, and a `FeatureEngine` that serializes one operation per feature, writes through the port under timeout with a **mandatory read-back** (never re-sending a timed-out write), accepts validated device-reported updates that supersede stale requests, restores on cancellation, unknowns everything on session invalidation, and maps the prompt's §24 error names onto the existing categories with retry through `RetryClass`; the standard catalogue defines 19 hardware feature contracts (noise control, transparency, one structured equalizer, gestures, wear detection, multipoint, spatial audio, head tracking, gaming mode, voice prompts, sidetone) as pure definitions with no vendor commands, no production port implementation and no hardware contact (ADR-P9-001 … 010).  Phase 10 established the audio transport engine, **reusing** the audio models Phases 1/2 built (`AudioTransportKind`, `AudioTransportState`, `Codec`, `CodecState`, `OmniBudsErrorCategory`) and adding only what observation needs: `HSP` in the transport taxonomy rather than a parallel enum (ADR-P10-002), a seven-state `AudioConnectionState` vocabulary (UNKNOWN/DISCONNECTED/CONNECTING/CONNECTED/ACTIVE/SUSPENDED/DISCONNECTING) where CONNECTED is not ACTIVE and UNKNOWN is not DISCONNECTED, an `ObservedAudioDevice` record kept separate from Bluetooth device identity with a session-scoped platform id that is never a persistent identity, per-profile `AudioProfileState` with the HFP SCO audio state tracked separately from the profile connection, an immutable schema-versioned `AudioTransportSnapshot` as the single authoritative store, a pure-function `AudioReconciler` implementing five rules (capability gate, conflict preservation to UNKNOWN with diagnostics, impossible-combination repair, evidence-only active-transport election with no priority order, 30s stale-callback tolerance), an `AudioTransportEngine` with an explicit STOPPED/STARTING/OBSERVING/STOPPING lifecycle behind a mutex, a SupervisorJob scope with injected dispatcher, and deterministic teardown, two read-only ports (`AudioProfileSource`, `AudioDeviceSource`) answered by Android adapters behind a raw-primitives handle seam with the translation isolated in `audio/mapping`, an API-33-isolated `LeAudioApi33` behind the pure `leAudioSupport(apiLevel)` guard (minSdk stays 26), an `AudioControlTopology` keeping the audio and control planes as different types the compiler will not conflate, and three error categories (`AUDIO_OBSERVATION_FAILED` safe to retry, `LE_AUDIO_UNAVAILABLE`/`AUDIO_STATE_CONFLICT` never retry, none invalidating a session); HSP is honestly reported UNKNOWN on Android where the platform exposes no HSP-specific state (ADR-P10-004), SCO devices are attributed to neither HFP nor HSP, and machine-checked scope tests ban media capture, codec configuration, routing control, microphone permission, vendor literals and Android imports from the core audio package (ADR-P10-001 … 010).  Phase 11 established the codec capability engine, **extending** the codec vocabulary Phases 1/2 built (`Codec`, `CodecFamily`, `CodecState`, `CodecCapability`) rather than forking a parallel taxonomy (ADR-P11-001): the `CodecState` evidence ladder already distinguishes SUPPORTED/AVAILABLE/ENABLED/NEGOTIATED/ACTIVE with CONFIGURABLE orthogonal, and Phase 11 adds only what observation needs — `OPUS` in the codec enum (ADR-P11-002), a `CodecEvidence` provenance record (source × confidence × time, confidence never inflating), a `CodecObservability` model separating platform limits from device limits, nullable `CodecMetadata` (sample rate, bit depth, channel mode, sealed bitrate, quality mode — missing values never defaulting), a `CodecRuntimeState` split from capability with `CodecFreshness` (stop marks STALE, reconnect re-observes), an immutable per-device `CodecSnapshot` keyed by `DeviceIdentity`, and a `CodecCapabilityEngine` with per-device lifecycle behind the `CodecObservationSource` port; the Android adapter is honest about the verified platform reality that no public API exposes the active/negotiated codec (ADR-P11-003) — API 35+ `getSupportedCodecTypes()` yields local SUPPORTED capabilities, everything else is UNKNOWN with NOT_OBSERVABLE and explicit limitations, the API-35 code isolated in `CodecApi35` (ADR-P11-004), LC3 stays LE Audio, aptX Adaptive/Lossless stay NOT_OBSERVABLE without platform constants, and machine-checked scope tests ban codec switching, media interception, and Android imports from core (ADR-P11-007, ADR-P11-008). Records live in `docs/phases/phase-0/` … `docs/phases/phase-11/`; the documentation map is `docs/README.md`.
- **Code now exists**: `:core` (platform-independent Kotlin/JVM domain; `kotlinx-coroutines-core` returned as the Flow surface it was waiting for, ADR-P2-003), `:platform:android` (Android library: the Bluetooth mechanism behind the boundary, whose manifest now declares exactly one permission — `BLUETOOTH_CONNECT`, earned by the device reads Phase 3 added, ADR-P3-012) and `:tools:companion-shell` (a debug-only, dependency-free harness target for the device bridge, ADR-P2-010). The repository is on branch `main`.
- **No Bluetooth has been executed on hardware by any phase, and no device is controlled by any phase.** Every capability in this project remains at most `IMPLEMENTED` on the evidence ladder: Phase 8's 628 core and 102-per-variant platform tests run against scripted seams, pure fixtures, scripted transport handles, a test-only scripted protocol session and a test-only scripted discovery source, the twelve instrumented methods Phase 3 compiled have still never run (Phases 6, 7 and 8 added code that is compiled and seam-tested but never executed on a device), and the only physical-device verification in the repository belongs to the ADB deployment harness (`docs/development/adb-deployment/`), which cannot import product code.
- **Device verification is deferred out of every phase's completion criteria by user directive** (ADR-P3-014, 2026-10-02): the application is built first and the hardware session runs at the end. That is a scheduling decision, not a change to the evidence ladder — a deferred claim stays visibly unverified, and `docs/phases/phase-3/risk-register.md` RISK-044/RISK-045 carry the obligation that it stays that way.
- **Phase 2 deliberately did not** discover devices, open a transport, read battery or codec state, request a permission, or build a screen (prompt section 6).
- **Phase 3 deliberately did not** identify a device, infer a manufacturer, select a vendor protocol, open a transport, read a battery, touch the audio path, persist a device history, request a permission, or build a screen. A Phase 3 observation is not evidence that a device is an earbud or that it is supported, and Phase 4 must not read it as though it were.
- **Phase 4 deliberately did not** fingerprint a device, identify a manufacturer, discover a protocol, open a transport, read a battery, alter the audio path, persist a saved-device record, request a permission, build a screen, or pair/connect anything. A Phase 4 session is observed application state and is not proof that a device is an earbud, is supported, or can be controlled; Phase 5 must not read it as though it were.
- **Phase 5 deliberately did not** open a scanner or any transport, select a vendor protocol, resolve a protocol, read a battery, touch the audio path, persist a saved-device record or an unidentified-device history, build a screen, or populate the registry with a device signature. A Phase 5 identification is a conclusion about which product a device resembles, at a stated confidence, and it is **not** proof that a device is supported or that a transport reaches it — `maySupportProtocolResolution` is advisory evidence toward Phase 6/7, never a selection. The shipped registry is empty, so the correct answer for every device today is `Unknown` (ADR-P5-006/007).
- **Phase 6 deliberately did not** scan or discover devices, run the transport on a handset, implement any vendor protocol, framing, opcode or device-specific UUID, select a protocol, read battery or firmware, touch the media-audio path (structurally walled off: transport is L1, audio L2), persist anything, or build UI. A Phase 6 `CONNECTED` means only the platform confirmed a link; it is **not** proof a device speaks a protocol or is controllable (prompt §13's closing line), and the shipped resolver selects nothing on purpose (ADR-P6-005/012).
- **Phase 7 deliberately did not** register a vendor protocol or invent an opcode, GATT UUID or packet layout (the registry ships empty); implement a production `ProtocolSession`; run discovery during resolution or auto-connect to a candidate; select a protocol from a brand name; do capability discovery (Phase 8), UI, persistence, or any hardware operation. A resolved protocol is a *candidate*, never a verdict, and a sent command is delivery, never an applied change — requested and device-confirmed state are kept in distinct types (ADR-P7-006/010; prompt §21's closing principle). The `ProtocolState` axis stays separate from `ConnectionState` and `TransportState`.
- **Phase 8 deliberately did not** command any hardware feature (no ANC, transparency, EQ, gesture, battery, firmware or codec operation), implement any vendor packet or parser, build any production `CapabilityDiscoverySource` or bind one to a real `EarbudProtocol`/`ProtocolSession` (that adapter is L3/L4 wiring, deferred), connect or open any transport, persist a snapshot, or build any UI. A Phase 8 snapshot is a discovery *outcome*: an unavailable capability is not an unsupported one, an un-read feature is `UNKNOWN` not `UNSUPPORTED`, and — because the engine ships with no protocol — the honest answer for every device today is that nothing is established (ADR-P8-005/010). The `DiscoveryState` axis stays separate from `ConnectionState`, `TransportState` and `ProtocolState`.
- **Phase 12 has not started** and will not until an explicit "Execute Phase 12" prompt (ADR-P0-009).
- `ADR-P0-018` (research ladder ordering) is **settled**: Phase 5's ADR-P5-002 recorded the ordering the code had assumed for four phases (enumerate → identify) on the user's explicit deferral, closing the item that had stayed `proposed` since Phase 0 while Phase 1's code relied on it. Phase 20's registry-enrichment consequences follow from that recorded ordering.
- Where a future phase prompt conflicts with this document, this document wins; the conflict is reported and recorded as an ADR, never silently resolved (section 58). Phase 1 amended Phase 0 in four documented places: ADR-P1-002 (package root), ADR-P1-005 (codec state), ADR-P1-006 (error categories), ADR-P1-016 (session state). Phase 2 amended it in one: ADR-P2-009 (permission requirements follow `targetSdkVersion`, correcting `SEC-PERM-002`). Phase 3 amended Phase 2 in two places: ADR-P3-013 (Bluetooth receivers must register exported, correcting `ADR-P2-016`'s flag choice — a shipped-behaviour fix, not a documentation edit) and ADR-P3-012 (the boundary manifest's single earned permission, amending `ADR-P2-011`). Phase 4 amended Phase 1's records in the open rather than resolving them quietly: `domain-model-review.md` D-7/D-10/D-12 assigned the session-holder work to Phase 2, `validation.md` known issue 5 assigned the association to Phase 4 and the mutation confinement to Phase 24, and ADR-P4-001 draws that line — Phase 4 carries the association and owns session state, and does **not** claim Phase 24's confinement. Phase 5 settled the long-`proposed` `ADR-P0-018` in `ADR-P5-002` and corrected inherited data in the open: `ADR-P5-012` moved `DEVICE_DISCOVERY_SCAN`'s authorization tag from Phase 5 to Phase 6 (the first phase that can open a scanner), superseding the Phase-5 half of `ADR-P3-018` by the same "data must not contradict the phase it names" rule that moved it off Phase 3. Phase 6 filled the transport boundaries Phases 1/2 left unimplemented and corrected two inherited artefacts in the open: `ADR-P6-004` settled the `BleTransport` doc-vs-code contradiction (a BLE control channel *is* GATT; the "TransportKind has no BLE constant" claim was stale), and `ADR-P6-008` legitimately narrowed the Phase 2/3 forbidden-token guards (`TransportBoundariesTest`, `DependencyDirectionTest`) so client GATT/RFCOMM opens are permitted while scanning, discovery, server-listen, audio-path and UI stay refused. Phase 7 reused the protocol knowledge model Phases 1/2 built and, rather than obey prompt §8/§11 literally, reconciled their vocabulary onto the master ladders in the open: ADR-P7-003 mapped `RESEARCHED`/`AUTOMATED_TESTED` onto `VerificationLevel` and ADR-P7-004 kept "unknown side effect" structural instead of adding an `EffectClass` member, so no second evidence or effect ladder was forked. Phase 8 reused the capability model Phases 1/2 built and, rather than obey prompt §6/§7/§15 literally, reconciled their vocabulary onto the existing model in the open: ADR-P8-001 refused a `CapabilityId`/`CapabilitySet`/four-parallel-enums fork and *wrapped* `DeviceCapabilities`; ADR-P8-002/003 added exactly one missing dimension (`CapabilityAvailability`) and reused `VerificationLevel` with an evidence-kind ceiling rather than a second ladder; and ADR-P8-004 mapped §15's ~11 error names onto `OmniBudsErrorCategory` (malformed → `INVALID_STATE`, session-unavailable → `CONNECTION_UNAVAILABLE`, protocol-unresolved → `PROTOCOL_MISMATCH`) instead of minting new categories, so no second capability, verification or error taxonomy was forked.

## IMPORTANT — THIS IS A CONTEXT / PLANNING PROMPT ONLY

You are being given this prompt to establish the complete engineering context for a project called **OmniBuds**.

**DO NOT IMPLEMENT ANY FEATURE.**

**DO NOT WRITE APPLICATION CODE.**

**DO NOT MODIFY source-code files.**

**DO NOT install dependencies.**

**DO NOT run migrations.**

**DO NOT modify Android configuration.**

**DO NOT connect to or interact with physical Bluetooth devices.**

**DO NOT execute tests intended for implementation validation.**

**DO NOT begin Phase 0 or any later implementation phase.**

Your job at this stage is ONLY to:

1. Understand the complete project.
2. Understand all requirements.
3. Understand the architecture.
4. Understand the development philosophy.
5. Understand the future phase structure.
6. Prepare the planning/documentation structure.
7. Preserve this information as project context for subsequent execution prompts.

The actual implementation will happen later through **separate phase-by-phase execution prompts**.

At the end of this prompt, **STOP**.

Do not continue automatically into implementation.

---

# 1. PROJECT IDENTITY

Project name:

**OmniBuds**

Project description:

> A universal hardware-control platform for Bluetooth earbuds and headphones that allows users to control the actual capabilities implemented by their connected audio devices, regardless of manufacturer, without requiring separate manufacturer applications.

Primary platform:

**Android**

Primary language:

**Kotlin**

Architecture target:

**Kotlin Multiplatform-ready**

Future platforms:

- Windows
- macOS
- Linux
- potentially additional desktop platforms

The Android application is the first implementation.

The desktop application is a future phase.

---

# 2. CORE PRODUCT PRINCIPLE

The most important rule of OmniBuds is:

> **OmniBuds never simulates a hardware capability. It discovers, verifies, and controls capabilities actually implemented by the connected device.**

Examples:

If a headset physically supports:

- ANC
- Transparency
- Adaptive ANC
- EQ
- programmable gestures
- wear detection
- spatial audio
- head tracking
- multipoint
- gaming mode
- vendor-specific DSP
- codec configuration

then OmniBuds should attempt to expose and control those capabilities through the actual device protocol.

If the hardware does not support a feature:

**DO NOT SHOW IT AS AVAILABLE.**

Do not create:

- fake ANC
- software ANC
- fake transparency
- fake LDAC
- fake spatial audio
- fake hardware EQ
- fake vendor features

A software feature may exist only when it is explicitly a software feature and is clearly identified as such.

For the primary hardware-control experience, OmniBuds must control the actual device.

---

# 3. AUDIO PATH PRINCIPLE

OmniBuds is NOT an audio player.

OmniBuds is NOT intended to replace Android's Bluetooth audio pipeline.

OmniBuds should normally remain outside the media-audio path.

Normal architecture:

```text
Music / Video App
       ↓
Android Audio Stack
       ↓
Bluetooth Audio Transport
       ↓
Earbuds / Headphones
```

OmniBuds operates alongside that path:

```text
                  Android Audio
                       │
                       ▼
                  Bluetooth
                       │
                       ▼
                    Device

OmniBuds
    │
    └──── control / configuration channel
```

OmniBuds should not unnecessarily:

```text
capture audio
→ process audio
→ re-encode audio
→ transmit audio again
```

because doing so could degrade:

- quality
- latency
- codec behavior
- battery
- stability

The normal Android/Bluetooth audio path should remain responsible for media transport.

---

# 4. AUTOMATIC DEVICE DETECTION

The user does NOT want the manufacturer-app workflow where:

```text
Bluetooth Settings
    ↓
connect earbuds
    ↓
open manufacturer application
    ↓
connect earbuds again
```

Instead:

```text
Android Bluetooth connects device
            ↓
OmniBuds detects connected device
            ↓
Build device fingerprint
            ↓
Identify device
            ↓
Determine available transport
            ↓
Attach control channel
            ↓
Discover capabilities
            ↓
Read current state
            ↓
READY
```

The user should not have to manually pair the same earbuds a second time with OmniBuds.

However, the implementation must respect Android's Bluetooth security, permission, background execution, and platform limitations.

Never promise functionality that Android does not actually permit.

---

# 5. DEVICE SESSION MODEL

OmniBuds must distinguish between:

## Active session devices

Devices currently connected.

Example:

```text
CONNECTED
    ↓
ACTIVE SESSION
```

If the device disconnects:

```text
ACTIVE SESSION
    ↓
DISCONNECTED
    ↓
REMOVE FROM ACTIVE LIST
```

unless the user explicitly saved the device.

## Saved devices

The user can explicitly choose:

**Add to My Devices**

A saved device remains known to the application even when disconnected.

The application must NOT permanently retain every Bluetooth device the user temporarily tries.

---

# 6. DEVICE INFORMATION REQUIREMENTS

OmniBuds should collect as much useful metadata as safely available.

Potential metadata:

- Bluetooth name
- manufacturer
- manufacturer data
- model
- model identifier
- device class
- Bluetooth address where legitimately accessible
- service UUIDs
- characteristic UUIDs
- descriptors
- supported profiles
- transport type
- firmware version
- hardware revision
- protocol version
- protocol family
- battery capabilities
- audio capabilities
- device-specific capabilities
- control-channel information

Do not assume all fields are available.

Unknown values must remain unknown.

Do not fabricate metadata.

Do not retain sensitive identifiers indefinitely without a reason.

---

# 7. DEVICE FINGERPRINT

A device must be identified using a fingerprint rather than only its Bluetooth display name.

Conceptually:

```kotlin
DeviceFingerprint(
    manufacturer,
    model,
    manufacturerData,
    serviceUuids,
    characteristicUuids,
    deviceClass,
    transportCandidates,
    protocolCandidates,
    firmwareVersion
)
```

The exact implementation will be designed later.

The fingerprint should allow OmniBuds to determine:

```text
What device is this?
Which protocol applies?
Which transport applies?
Which capabilities can be safely queried?
```

---

# 8. TRANSPORT ARCHITECTURE

Never assume every device uses BLE/GATT.

OmniBuds must support a transport abstraction.

Potential transports:

```text
Classic Bluetooth
BLE / GATT
RFCOMM / SPP
A2DP
AVRCP
HFP
LE Audio
vendor-specific control transports
```

Conceptually:

```text
TransportManager
│
├── GATTTransport
├── RFCOMMTransport
├── ClassicBluetoothTransport
├── LEAudioTransport
└── FutureTransport
```

Different manufacturers may use different transports.

Some devices may use:

- BLE for advertisements/bookkeeping
- RFCOMM for control
- A2DP for audio
- another proprietary mechanism for configuration

The architecture must accommodate this.

---

# 9. PROTOCOL ARCHITECTURE

Create a vendor-protocol abstraction.

Conceptually:

```kotlin
interface EarbudProtocol {

    suspend fun identify()

    suspend fun discoverCapabilities()

    suspend fun readState()

    suspend fun readBattery()

    suspend fun readFirmware()

    suspend fun setAnc()

    suspend fun setTransparency()

    suspend fun readEqualizer()

    suspend fun writeEqualizer()

    suspend fun readGestures()

    suspend fun writeGestures()
}
```

This is conceptual only at this stage.

Do not force every vendor to implement every method.

Capability negotiation determines what is actually available.

---

# 10. CAPABILITY ENGINE

The capability engine is one of the central systems of OmniBuds.

A capability must NOT simply be:

```text
supported = true
```

It needs richer state.

Conceptually:

```text
UNKNOWN
UNSUPPORTED
READ_ONLY
SUPPORTED_VOLATILE
SUPPORTED_PERSISTENT
PERSISTENCE_VERIFIED
```

A capability may also contain:

- readable
- writable
- transport
- protocol
- requires connection
- persistence behavior
- verification status

Example:

```text
ANC
SUPPORTED_PERSISTENT
READABLE
WRITABLE
TRANSPORT = vendor protocol
```

Another:

```text
Battery
READ_ONLY
```

Another:

```text
Spatial Audio
UNKNOWN
```

The UI must be driven by this capability state.

---

# 11. DYNAMIC UI PRINCIPLE

The backend determines what the UI is allowed to show.

If device supports:

```text
ANC
Transparency
EQ
```

show them.

If device supports:

```text
ANC
EQ
```

do NOT show Transparency.

If device doesn't expose EQ:

do NOT show an EQ control.

If a capability is unknown:

do not represent it as supported.

This principle must exist throughout the architecture.

---

# 12. HARDWARE FEATURES

The architecture must account for real hardware features including, where supported:

### Noise control

- ANC
- Transparency
- Normal
- Adaptive ANC
- custom ANC levels
- transparency levels
- environmental modes
- wind reduction
- traffic mode
- indoor/outdoor modes
- vendor-specific noise modes

### Equalization

- presets
- bass/mid/treble
- graphic EQ
- parametric EQ
- vendor-specific EQ

### Gestures

- single tap
- double tap
- triple tap
- long press
- left/right independent actions

Potential actions:

- play/pause
- next
- previous
- volume up
- volume down
- ANC
- transparency
- assistant
- gaming mode
- vendor-specific actions

### Sensors

- wear detection
- in-ear detection
- touch sensors
- motion sensors
- head tracking

### Other capabilities

- multipoint
- spatial audio
- head tracking
- gaming mode
- voice prompts
- sidetone
- auto transparency
- auto answer
- vendor-specific DSP controls
- device-specific features

Do not assume every device supports every feature.

---

# 13. VENDOR-SPECIFIC FEATURES

Universal does NOT mean reducing every headset to:

```text
ANC
EQ
Battery
```

If a manufacturer exposes unique functionality through its protocol, OmniBuds should preserve it.

Examples may include:

- Sony-specific features
- Bose-specific features
- Samsung-specific features
- OnePlus-specific features
- Apple-specific features
- Nothing-specific features
- Soundcore-specific features

Common functionality gets a common interface.

Unique functionality gets a vendor extension.

---

# 14. AUDIO ENGINE

Audio must be treated as a first-class subsystem.

The audio subsystem must understand:

### Classic Bluetooth

Potential codecs:

- SBC
- AAC
- aptX
- aptX HD
- aptX Adaptive
- aptX Lossless where actually exposed
- LDAC
- other platform/vendor codecs

### LE Audio

- LC3
- LE Audio transport state
- other exposed LE Audio capabilities

Do not assume that a codec is available merely because a device is theoretically capable of it.

---

# 15. CODEC STATE MODEL

The system must distinguish:

```text
SUPPORTED
AVAILABLE
ENABLED
NEGOTIATED
ACTIVE
CONFIGURABLE
```

Example:

```text
LDAC

Earbuds support: YES
Phone supports: YES
Available: YES
Enabled: YES
Negotiated: NO
Active: NO
```

That must NOT be displayed as:

```text
LDAC ACTIVE
```

Another example:

```text
LDAC: SUPPORTED
AAC: ACTIVE
```

The user must be able to understand the difference.

---

# 16. AUDIO QUALITY INFORMATION

Where Android and the device expose the information, OmniBuds should understand:

- codec
- current codec
- sample rate
- bit depth
- bitrate
- channel mode
- quality mode
- connection priority
- adaptive mode
- LE Audio state
- high-quality state
- low-latency state

Conceptual model:

```kotlin
AudioTransportState(
    transport,
    codec,
    sampleRate,
    bitsPerSample,
    bitrate,
    channelMode,
    qualityMode,
    active
)
```

Unknown values remain unknown.

Never show fake numbers.

---

# 17. LDAC

Where supported and where Android permits configuration, account for:

- LDAC availability
- current active state
- sound-quality priority
- balanced mode
- connection-quality priority
- adaptive behavior
- actual negotiated state

Do not claim that OmniBuds can force LDAC on every Android device.

The Android OS, Bluetooth stack, OEM implementation, phone hardware, and headset all affect what is possible.

---

# 18. AAC

AAC must be part of the codec registry.

Detect:

```text
Phone supports AAC
Earbuds support AAC
AAC available
AAC active
```

Do not display AAC as active when another codec is actually being used.

---

# 19. APTX FAMILY

Account for:

- aptX
- aptX HD
- aptX Adaptive
- aptX Lossless where actually exposed

Each is a separate capability.

Never collapse them into:

```text
aptX = supported
```

when the distinction matters.

---

# 20. LE AUDIO / LC3

LE Audio must be treated separately from Classic Bluetooth.

Architecture must understand:

```text
Classic Bluetooth
    ↓
A2DP
    ↓
SBC/AAC/aptX/LDAC/etc.

LE Audio
    ↓
LC3
```

Do not architect LE Audio as merely another A2DP codec.

---

# 21. AUDIO CONFIGURATION

Where Android legitimately permits control, account for:

- codec
- sample rate
- bit depth
- bitrate
- quality mode
- connection priority
- adaptive mode
- channel mode

The application must respect OS restrictions.

If the OS exposes only read access:

```text
READ ONLY
```

Do not create a fake setting.

---

# 22. HARDWARE DSP VS PHONE DSP

This distinction must be maintained.

Preferred:

```text
OmniBuds
   ↓
Vendor protocol
   ↓
Earbud DSP
```

for hardware EQ/ANC/etc.

OmniBuds should not use phone-side audio processing as a fake substitute for a missing hardware feature.

---

# 23. BATTERY

Account for:

- left battery
- right battery
- case battery
- charging state
- left/right asymmetry
- unknown state

Unknown:

```text
null
```

not:

```text
0%
```

---

# 24. PERSISTENT SETTINGS

If a user changes:

```text
Double tap → Pause
Triple tap → ANC
Long press → Transparency
```

and the earbuds support persistent configuration, OmniBuds should attempt to write the configuration into the device.

But never assume persistence.

Required verification:

```text
READ
↓
WRITE
↓
READ BACK
↓
VERIFY
↓
DISCONNECT
↓
RECONNECT
↓
READ AGAIN
↓
VERIFY
```

Only then:

```text
PERSISTENCE_VERIFIED
```

If it does not survive:

```text
SUPPORTED_VOLATILE
```

or the appropriate state.

---

# 25. UNKNOWN DEVICE SAFETY

Unknown devices must begin in:

```text
READ-ONLY MODE
```

Safe discovery may include:

- device metadata
- services
- characteristics
- descriptors
- manufacturer data
- standard battery
- standard Bluetooth information

Do not randomly write to unknown characteristics.

Do not blindly fuzz devices.

Do not send undocumented commands.

---

# 26. PROTOCOL LAB

A separate engineering environment should eventually exist for reverse engineering and validation.

It should include:

```text
Device Scanner
GATT Explorer
Service Explorer
Characteristic Explorer
Descriptor Explorer
Notification Monitor
RFCOMM Explorer
Packet Logger
Hex Viewer
Command Recorder
State Comparator
Protocol Replay
Read Tester
Write Tester
Persistence Tester
```

Unknown device research workflow:

```text
DISCOVER
↓
IDENTIFY
↓
READ
↓
UNDERSTAND
↓
VALIDATE
↓
WRITE
↓
READ BACK
↓
RECONNECT
↓
VERIFY
```

Only perform protocol research on devices and software that the development team is authorized to analyze.

---

# 27. PROTOCOL DATABASE

Create a structured protocol knowledge base.

It should eventually contain:

```text
manufacturer
model
fingerprint rules
transport
protocol
protocol version
firmware compatibility
service UUIDs
characteristics
commands
responses
parsers
encoders
capabilities
persistence behavior
known limitations
test status
```

Protocol confidence should be represented explicitly.

Possible states:

```text
INFERRED
IMPLEMENTED
LAB_TESTED
HARDWARE_VERIFIED
PERSISTENCE_VERIFIED
```

---

# 28. DEVICE DATABASE VS PROTOCOL DATABASE

These must remain separate.

### Protocol database

Global application knowledge.

Example:

```text
Sony model X uses protocol Y.
```

### User device database

User-specific information.

Example:

```text
The user's saved Sony model X.
```

Do not mix the two.

---

# 29. FEATURE DEPENDENCIES

Some features may conflict.

Example:

```text
LDAC
+
Multipoint
```

may not simultaneously be possible on a specific device.

The architecture therefore needs a:

```text
FeatureDependencyEngine
```

that can represent:

- conflicts
- prerequisites
- mutually exclusive modes
- firmware limitations
- transport limitations

Never claim two features are active if the hardware cannot actually do both.

---

# 30. ERROR ENGINE

All operations need structured errors.

Potential categories:

```text
BluetoothDisabled
PermissionDenied
DeviceDisconnected
TransportUnavailable
GattFailure
RfcommFailure
ProtocolMismatch
UnsupportedFeature
WriteRejected
VerificationFailed
Timeout
FirmwareMismatch
CodecUnavailable
```

Read and write operations must have different retry policies.

Do NOT blindly retry commands that could cause side effects.

---

# 31. DIAGNOSTICS

Eventually OmniBuds should be able to generate a diagnostic report containing:

```text
Device
Manufacturer
Model
Firmware
Hardware revision

Bluetooth
Profiles
Services

Transport
Protocol
Protocol version

Capabilities

ANC
Transparency
EQ
Gestures
Battery
Sensors

Audio
Transport
Codec
Sample rate
Bit depth
Bitrate
Channel mode

Errors
Warnings
```

This will be essential for debugging unsupported devices.

---

# 32. QUICK SETTINGS

Use Android's legitimate Quick Settings mechanisms.

Potential controls:

```text
ANC
Transparency
Normal
Device
```

Do not assume Android allows a custom arbitrary control panel inside the system shade.

Respect platform restrictions.

---

# 33. NOTIFICATION

A persistent control notification may eventually contain:

```text
OMNIBUDS

Device
Sony WF-XXXX

ANC
Transparency
Normal

L 87%
R 92%
Case 74%

Codec: LDAC
```

Only show values actually available.

---

# 34. WIDGET

A future home-screen widget may expose:

```text
Device
ANC
Transparency
Normal
Battery
Codec
Audio quality
```

Again, dynamic based on capabilities.

---

# 35. BACKGROUND BEHAVIOR

The application should handle:

- screen off
- app closed
- Bluetooth reconnect
- Bluetooth disabled/re-enabled
- phone reboot
- earbuds reconnect
- case open
- earbuds removed from case
- saved-device restoration

But all behavior must remain compliant with Android background execution restrictions.

---

# 36. TESTING PHILOSOPHY

Testing must occur at multiple levels.

### Unit tests

- parsers
- encoders
- decoders
- state machines
- capability logic
- codec state logic
- persistence logic

### Protocol tests

- packet correctness
- command correctness
- response parsing
- error handling

### Integration tests

- Bluetooth lifecycle
- connection
- reconnect
- capability discovery

### Hardware-in-the-loop tests

Real earbuds.

### Persistence tests

Real disconnect/reconnect.

### Cross-device tests

Different models.

### Cross-phone tests

Different Android manufacturers.

---

# 37. SUB-AGENT ORCHESTRATION

When implementation begins, do NOT perform every task sequentially with one agent if the editor supports multiple sub-agents.

Use specialized agents.

For example:

```text
                    ORCHESTRATOR
                         │
       ┌─────────────────┼─────────────────┐
       │                 │                 │
       ▼                 ▼                 ▼
 Architecture       Bluetooth          Protocol
 Agent              Agent              Research Agent
       │                 │                 │
       ▼                 ▼                 ▼
 Testing Agent       Audio Agent       Security Agent
       │                 │                 │
       └─────────────────┼─────────────────┘
                         ▼
                    Integration
                       Agent
                         │
                         ▼
                       QA
```

Each sub-agent must receive:

- its specific objective
- relevant files
- constraints
- expected outputs
- test requirements
- stop conditions

The orchestrator is responsible for integrating results.

---

# 38. SUB-AGENT RULES

Sub-agents must NOT:

- duplicate another agent's work
- overwrite unrelated work
- make architecture decisions silently
- invent unsupported hardware capabilities
- assume protocol behavior
- skip tests
- change project-wide architecture without reporting it

When agents discover conflicts:

```text
Agent
↓
Report conflict
↓
Orchestrator evaluates
↓
Architecture decision
↓
Document decision
```

---

# 39. PARALLELIZATION

Parallel work should be used only when tasks are independent.

Good parallelization:

```text
Agent A → Bluetooth architecture research

Agent B → Audio/codec architecture research

Agent C → Protocol database design

Agent D → Testing architecture

Agent E → Security/privacy review
```

Bad parallelization:

```text
Agent A edits DeviceManager
Agent B simultaneously rewrites DeviceManager
Agent C changes DeviceManager API
```

Avoid conflicting modifications.

---

# 40. PHASE STRUCTURE

Every implementation phase must have its own documentation.

For EVERY phase, create/use:

```text
phase-X/
│
├── requirements.md
├── design.md
├── specs.md
├── task-list.md
├── test-plan.md
├── validation.md
└── decisions.md
```

Where useful, also:

```text
architecture.md
protocol.md
research.md
risk-register.md
```

---

# 41. REQUIREMENTS.MD

Each phase's `requirements.md` must contain real requirements.

Do NOT write:

```text
- Build Bluetooth support.
```

Instead write detailed requirements such as:

```text
REQ-BT-001
The application SHALL detect the Bluetooth adapter state.

REQ-BT-002
The application SHALL detect eligible already-connected devices.

REQ-BT-003
The application SHALL distinguish paired from connected devices.

REQ-BT-004
The application SHALL not require a second user pairing operation
when the device is already connected through Android Bluetooth,
unless the Android platform explicitly requires an association step.

REQ-BT-005
The system SHALL represent unavailable information as unknown rather
than fabricating values.
```

Every requirement should have:

- ID
- description
- rationale
- dependencies
- acceptance criteria
- verification method

---

# 42. DESIGN.MD

Each phase's `design.md` must describe:

- architecture
- modules
- responsibilities
- data flow
- state transitions
- interfaces
- dependencies
- error handling
- lifecycle
- concurrency
- security considerations
- platform limitations
- future extensibility

Include diagrams where useful.

---

# 43. SPECS.MD

Each phase's `specs.md` must contain implementation-level specifications.

Examples:

```text
API contracts
Data models
Enums
State machines
Interfaces
Persistence schema
Transport contracts
Protocol contracts
Error contracts
Threading requirements
Coroutine behavior
Flow behavior
Timeouts
Retry policies
Validation rules
```

Do not leave important behavior implicit.

---

# 44. TASK-LIST.MD

Tasks must be atomic and executable.

Bad:

```text
Implement Bluetooth.
```

Good:

```text
TASK-BT-001
Create BluetoothManager abstraction.

TASK-BT-002
Implement Android Bluetooth adapter state observer.

TASK-BT-003
Implement connected-device discovery.

TASK-BT-004
Add Android-version-specific permission handling.

TASK-BT-005
Add unit tests for adapter state transitions.
```

Each task should include:

- ID
- objective
- dependencies
- files/modules involved
- implementation notes
- tests
- acceptance criteria
- completion state

---

# 45. TEST-PLAN.MD

Every phase must have explicit tests.

Each test needs:

```text
TEST-ID
Purpose
Setup
Input
Expected result
Failure condition
Automation possibility
Hardware requirement
```

---

# 46. VALIDATION.MD

After implementation of a phase, document:

```text
Implemented
Not implemented
Tests passed
Tests failed
Known limitations
Platform limitations
Hardware limitations
Deferred work
```

Never mark a feature "complete" merely because code compiles.

---

# 47. DECISIONS.MD

Architecture decisions must be recorded.

Example:

```text
ADR-001
Why OmniBuds does not process media audio.

Decision:
Keep OmniBuds outside the media path.

Reason:
Preserve native codec negotiation and audio quality.
```

---

# 48. PHASE-BY-PHASE DEVELOPMENT ROADMAP

The actual project will eventually be executed in these phases:

```text
PHASE 0
Engineering Contract & Repository Rules

PHASE 1
Project Foundation & Kotlin Architecture

PHASE 2
Android Bluetooth Foundation

PHASE 3
Connected Device Detection

PHASE 4
Device Session & Lifecycle Management

PHASE 5
Device Fingerprinting & Identification

PHASE 6
Bluetooth Transport Layer

PHASE 7
Protocol Abstraction Engine

PHASE 8
Capability Discovery Engine

PHASE 9
Hardware Feature Engine

PHASE 10
Audio Transport Engine

PHASE 11
Codec Capability Engine

PHASE 12
AAC / LDAC / aptX / LC3 Support

PHASE 13
Audio Quality & Negotiation State

PHASE 14
Audio Path Validation

PHASE 15
Hardware DSP / Audio Separation

PHASE 16
Battery & Power State

PHASE 17
Persistent Configuration Engine

PHASE 18
Persistence Verification Framework

PHASE 19
First Fully Supported Vendor Device

PHASE 20
Protocol Laboratory

PHASE 21
Unknown Device / Read-Only Mode

PHASE 22
Protocol Knowledge Database

PHASE 23
Vendor-Specific Feature Framework

PHASE 24
Global Device State Engine

PHASE 25
Quick Settings Integration

PHASE 26
Notification Controls

PHASE 27
Home-Screen Widget

PHASE 28
Background Device Lifecycle

PHASE 29
Feature Dependency & Conflict Engine

PHASE 30
Comprehensive Device Test Framework

PHASE 31
Cross-Device Testing

PHASE 32
Cross-Android Testing

PHASE 33
Audio Quality Testing

PHASE 34
Failure & Recovery Engine

PHASE 35
Security & Privacy Hardening

PHASE 36
Diagnostics & Logging

PHASE 37
Protocol Test Automation

PHASE 38
Hardware-in-the-Loop Testing

PHASE 39
First Production-Quality Device

PHASE 40
Vendor Expansion Framework

PHASE 41
Sony / Bose / JBL / Samsung / etc.

PHASE 42
Apple / AirPods Research & Integration

PHASE 43
Community Protocol SDK

PHASE 44
Protocol Versioning

PHASE 45
Firmware Compatibility

PHASE 46
Kotlin Multiplatform Core

PHASE 47
Desktop Bluetooth Layer

PHASE 48
Desktop Application

PHASE 49
Android UI

PHASE 50
Unified UI / UX

PHASE 51
Release Engineering

PHASE 52
Final QA & Production Release
```

The phase list may be refined during planning, but major architectural changes must be documented rather than silently made.

---

# 49. PHASE EXECUTION CONTRACT

When a future prompt says:

> Execute Phase X

you must NOT blindly begin coding.

First:

1. Read the project context.
2. Read all existing phase documentation.
3. Inspect the current repository.
4. Inspect completed tasks.
5. Inspect previous decisions.
6. Identify dependencies.
7. Determine what can be parallelized.
8. Create a sub-agent execution plan.
9. Assign independent tasks to specialized sub-agents where appropriate.
10. Implement.
11. Test.
12. Review.
13. Integrate.
14. Run regression checks.
15. Update documentation.
16. Mark tasks complete only when acceptance criteria pass.
17. Record unresolved issues.
18. Stop at the phase boundary.

---

# 50. NO AUTOMATIC PHASE PROGRESSION

When Phase X is complete:

**DO NOT AUTOMATICALLY START Phase X+1.**

Stop and report:

```text
PHASE COMPLETE

Implemented:
...

Tests:
...

Known limitations:
...

Deferred:
...

Next phase:
Phase X+1
```

Wait for the next explicit execution prompt.

---

# 51. IMPLEMENTATION QUALITY RULES

All future implementation must prioritize:

- correctness
- maintainability
- modularity
- testability
- observability
- extensibility
- Kotlin idioms
- coroutine safety
- lifecycle safety
- Android compatibility
- KMP readiness
- clear interfaces
- minimal coupling
- explicit state
- explicit errors

Avoid:

- giant manager classes
- hard-coded manufacturer logic
- UI-driven business logic
- duplicated Bluetooth logic
- hidden global state
- unsafe background services
- magic UUIDs scattered through code
- magic protocol bytes scattered through code
- fake capability detection
- hard-coded assumptions about devices

---

# 52. NO MAGIC PROTOCOL IMPLEMENTATION

Vendor protocol commands must be represented structurally.

Do not scatter:

```text
0x01
0x0A
0xFF
```

through random classes.

Instead maintain:

```text
ProtocolDefinition
CommandDefinition
ResponseDefinition
Parser
Encoder
CapabilityMapping
```

with documentation.

---

# 53. UNKNOWN MUST REMAIN UNKNOWN

Never transform:

```text
not discovered
```

into:

```text
unsupported
```

And never transform:

```text
not verified
```

into:

```text
supported
```

Use:

```text
UNKNOWN
```

when necessary.

---

# 54. HARDWARE VERIFICATION STANDARD

A capability should be classified as hardware verified only when:

1. Device identity is known.
2. Correct protocol is known.
3. Command is understood.
4. Command is sent to real hardware.
5. Device responds correctly.
6. State is read back.
7. Result matches expected behavior.
8. Reconnect behavior is tested when persistence matters.

---

# 55. NO FALSE FEATURE PARITY

Do not say:

```text
"OmniBuds supports Sony."
```

unless we define what that means.

Instead report:

```text
Sony Model X

Verified:
✓ ANC
✓ Transparency
✓ EQ
✓ Battery
✓ Gestures

Not verified:
? Spatial Audio
? Head Tracking

Unsupported:
✗ Firmware Update
```

This is the standard OmniBuds must use.

---

# 56. DEVELOPMENT DOCUMENTATION

The project should maintain:

```text
docs/
│
├── product/
├── architecture/
├── requirements/
├── protocols/
├── audio/
├── bluetooth/
├── testing/
├── security/
├── decisions/
└── phases/
```

Each phase should have its own documentation folder.

---

# 57. CURRENT TASK

At this moment, your ONLY task is to absorb this entire specification as project context.

You may create or organize planning documentation ONLY if necessary to preserve this context.

You must NOT:

- implement Bluetooth
- implement Android services
- implement protocol adapters
- implement codecs
- implement UI
- implement database logic
- run hardware tests
- modify application behavior
- begin Phase 0
- begin Phase 1
- begin any implementation phase

Do not infer permission to execute anything from this prompt.

The user will provide explicit execution prompts later.

---

# 58. FINAL INSTRUCTION

Treat this document as the **master source of truth for OmniBuds planning and architecture**.

Future phase prompts will build on this context.

When a future phase prompt conflicts with this document:

1. Identify the conflict.
2. Do not silently overwrite the architecture.
3. Explain the conflict.
4. Propose the necessary architectural change.
5. Wait for confirmation if the change materially affects the project.

For now:

**ABSORB CONTEXT → ORGANIZE PLANNING INFORMATION → STOP.**

Do not implement anything.

Do not proceed to the first phase.

Wait for the next explicit prompt.