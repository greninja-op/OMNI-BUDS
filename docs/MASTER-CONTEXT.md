# OMNIBUDS — MASTER PROJECT CONTEXT, ARCHITECTURE & PLANNING PROMPT

## Repository status (updated 2026-10-01)

This file is the master source of truth for OmniBuds planning and architecture.

- **Phase 0 and Phase 1 are complete and validated.** Phase 0 fixed the governance rules; Phase 1 built the build foundation and the platform-independent domain contracts. Records live in `docs/phases/phase-0/` and `docs/phases/phase-1/`; the documentation map is `docs/README.md`.
- **Code now exists**: `:core` (platform-independent Kotlin/JVM domain, no production dependencies) and `:platform:android` (Android library boundary containing no sources). The repository is now under git on branch `main`.
- **No Bluetooth, no UI, no device support.** Nothing has been tested against hardware, so no capability in this project is more than `IMPLEMENTED` on the evidence ladder.
- **Phase 2 has not started** and will not until an explicit "Execute Phase 2" prompt (ADR-P0-009).
- Still open from Phase 0: `ADR-P0-018` (research ladder ordering) is `proposed`, awaiting confirmation; it affects Phases 3, 5 and 20, not Phase 2.
- Where a future phase prompt conflicts with this document, this document wins; the conflict is reported and recorded as an ADR, never silently resolved (section 58). Phase 1 amended Phase 0 in four documented places: ADR-P1-002 (package root), ADR-P1-005 (codec state), ADR-P1-006 (error categories), ADR-P1-016 (session state).

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