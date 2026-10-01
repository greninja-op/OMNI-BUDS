# OMNIBUDS — PHASE 1 EXECUTION PROMPT
# PROJECT FOUNDATION & KOTLIN ARCHITECTURE

## EXECUTION MODE

This prompt explicitly authorizes execution of **PHASE 1 ONLY**.

Phase 0 has already established the engineering contract and governance rules.

You must now implement the **project foundation and architecture** required for future OmniBuds development.

Do NOT execute Phase 2 or any later phase automatically.

At the end of Phase 1, STOP and provide the Phase 1 completion report.

---

# 1. PHASE OBJECTIVE

The objective of Phase 1 is to establish a clean, modular, maintainable Kotlin project foundation for OmniBuds.

By the end of this phase, the repository must have:

- a stable Kotlin project structure
- a clear module architecture
- defined dependency direction
- Android platform boundary
- future Kotlin Multiplatform boundary
- domain models
- core abstractions
- state-model foundations
- capability-model foundations
- audio-model foundations
- device identity foundations
- error model foundations
- repository/documentation conventions
- unit-testing foundation
- build/test foundation
- static-analysis foundation where appropriate
- dependency-management strategy
- configuration strategy
- architecture validation

### This phase does NOT implement Bluetooth.

Bluetooth implementation begins in Phase 2.

---

# 2. CRITICAL SCOPE RESTRICTION

DO NOT implement:

- Bluetooth scanning
- Bluetooth permissions
- Bluetooth connection
- BLE
- GATT
- RFCOMM
- A2DP
- AVRCP
- HFP
- LE Audio transport
- codec negotiation
- ANC control
- transparency control
- EQ communication
- gesture communication
- battery communication
- vendor protocols
- protocol reverse engineering
- Quick Settings
- notifications
- widgets
- background Bluetooth services
- UI screens
- audio processing

Phase 1 establishes **contracts and architecture**, not actual hardware communication.

If an interface is required for future phases, define the interface without implementing the underlying platform behavior.

---

# 3. FIRST ACTION — READ PHASE 0

Before modifying anything:

Read:

```text
docs/phases/phase-0/
```

especially:

```text
requirements.md
design.md
specs.md
task-list.md
test-plan.md
validation.md
decisions.md
risk-register.md
```

Also read:

```text
docs/
```

and any existing project-level instructions.

Phase 0 is authoritative.

Do not contradict Phase 0 without documenting an architectural decision.

---

# 4. SUB-AGENT ORCHESTRATION

Use multiple specialized sub-agents if the environment supports them.

Do not have every agent edit the same files simultaneously.

Use the following workstreams.

---

# AGENT 1 — REPOSITORY / BUILD AUDITOR

Inspect:

- Gradle configuration
- settings
- existing modules
- Kotlin version
- Android Gradle Plugin
- Gradle wrapper
- source sets
- test configuration
- existing dependencies
- build variants
- CI

Deliver:

```text
docs/phases/phase-1/repository-analysis.md
```

Prefer read-only operation.

---

# AGENT 2 — ARCHITECTURE AGENT

Design the module/dependency architecture.

Focus on:

- core
- device
- capability
- protocol
- audio
- persistence
- diagnostics
- Android platform
- future desktop/KMP

Deliver:

```text
docs/phases/phase-1/architecture-review.md
```

---

# AGENT 3 — DOMAIN MODEL AGENT

Design the initial domain contracts:

- DeviceIdentity
- DeviceFingerprint
- DeviceSession
- DeviceCapabilities
- FeatureCapability
- DeviceState
- BatteryState
- AudioTransportState
- Codec state
- FirmwareInfo
- errors
- connection state

Deliver:

```text
docs/phases/phase-1/domain-model-review.md
```

---

# AGENT 4 — TESTING AGENT

Design:

- unit test structure
- test source sets
- fake implementations
- test fixtures
- architecture tests
- domain-model tests
- state-machine tests

Do not implement hardware tests yet.

Deliver:

```text
docs/phases/phase-1/testing-review.md
```

---

# AGENT 5 — KMP ARCHITECTURE AGENT

Evaluate which parts should be platform-independent.

Define:

```text
shared/core
Android-specific
future desktop-specific
```

Deliver:

```text
docs/phases/phase-1/kmp-review.md
```

---

# AGENT 6 — CODE QUALITY AGENT

Review:

- naming
- package structure
- static analysis
- formatting
- dependency hygiene
- API visibility
- Kotlin conventions

Deliver:

```text
docs/phases/phase-1/code-quality-review.md
```

---

# AGENT 7 — DOCUMENTATION AGENT

Prepare Phase 1 documentation.

Deliver:

```text
docs/phases/phase-1/
├── requirements.md
├── design.md
├── specs.md
├── task-list.md
├── test-plan.md
├── validation.md
├── decisions.md
└── risk-register.md
```

---

# AGENT 8 — INTEGRATION / ORCHESTRATOR

After the other agents finish:

1. review all findings
2. detect contradictions
3. choose the final architecture
4. ensure domain models do not depend on Android
5. ensure future KMP compatibility
6. ensure tests cover the foundation
7. integrate implementation
8. run build validation
9. run tests
10. inspect the resulting repository
11. update documentation

---

# 5. TARGET ARCHITECTURE

The foundation should move toward:

```text
OmniBuds/
│
├── android/
│
├── core/
│   ├── common/
│   ├── device/
│   ├── capability/
│   ├── state/
│   ├── protocol/
│   ├── audio/
│   ├── persistence/
│   └── diagnostics/
│
├── bluetooth/
│   ├── classic/
│   ├── ble/
│   ├── gatt/
│   ├── rfcomm/
│   ├── a2dp/
│   ├── avrcp/
│   ├── hfp/
│   └── leaudio/
│
├── protocols/
│   ├── common/
│   ├── apple/
│   ├── bose/
│   ├── jbl/
│   ├── motorola/
│   ├── nothing/
│   ├── oneplus/
│   ├── oppo/
│   ├── samsung/
│   ├── sony/
│   ├── soundcore/
│   └── soundpeats/
│
├── database/
│
├── protocol-lab/
│
├── testing/
│
├── desktop/
│
└── docs/
```

IMPORTANT:

Do not blindly create every future module if doing so would create empty or meaningless modules.

The architecture should be represented through a sensible initial structure.

Future modules may be introduced when their corresponding phases begin.

---

# 6. MODULE RESPONSIBILITIES

Document and enforce the following conceptual responsibilities.

## Core

Contains platform-independent business concepts.

Examples:

```text
DeviceIdentity
DeviceFingerprint
DeviceCapabilities
FeatureCapability
DeviceState
AudioState
BatteryState
ConnectionState
```

Core must not import Android Bluetooth classes.

---

## Device

Responsible for:

- device identity
- device session
- device state
- device lifecycle abstractions

It does not implement Bluetooth.

---

## Capability

Responsible for:

- capability definitions
- capability state
- capability discovery contracts
- support levels
- persistence state

---

## Protocol

Responsible for:

- protocol abstractions
- command definitions
- response definitions
- protocol metadata
- protocol capability contracts

Vendor implementations come later.

---

## Audio

Responsible for domain-level audio concepts:

- codec
- transport
- sample rate
- bit depth
- bitrate
- channel mode
- quality mode
- codec state

It must not implement Android codec control in Phase 1.

---

## Persistence

Responsible for future storage abstractions.

Do not yet build the full device database.

Define the appropriate interfaces/contracts only where needed.

---

## Diagnostics

Responsible for future:

- diagnostic snapshots
- error information
- protocol diagnostics
- logging abstractions

Do not implement packet logging yet.

---

# 7. ANDROID BOUNDARY

Android-specific code must remain outside the domain model.

Avoid:

```kotlin
data class Device(
    val bluetoothDevice: android.bluetooth.BluetoothDevice
)
```

inside the shared/domain layer.

Prefer a platform-independent identity representation.

For example conceptually:

```kotlin
data class DeviceIdentity(
    val manufacturer: String?,
    val model: String?,
    val displayName: String?,
    val modelId: String?
)
```

Android-specific Bluetooth objects belong in the Android transport layer later.

---

# 8. KMP-READY DESIGN

The project is Android-first.

However, do not make the core Android-dependent.

Future architecture:

```text
                Shared Core
                    │
        ┌───────────┴───────────┐
        │                       │
     Android                  Desktop
        │                       │
 Android Bluetooth        Desktop Bluetooth
 Android APIs             Windows/macOS/Linux
```

Shared code should eventually include:

- domain models
- capability models
- protocol parsing
- state machines
- feature logic
- persistence contracts
- validation logic
- diagnostics models

Platform code should include:

- Bluetooth API calls
- OS permission handling
- OS audio integration
- system notifications
- Quick Settings
- desktop system integration

---

# 9. DOMAIN MODEL REQUIREMENTS

Implement the foundational models needed by later phases.

Do not over-engineer fields that have no architectural purpose yet.

---

## 9.1 DeviceIdentity

Must be capable of representing:

- manufacturer
- model
- model ID
- display name
- hardware revision
- firmware version
- protocol version

Unknown information must remain nullable/unknown as appropriate.

Do not fabricate values.

---

# 10. DEVICE FINGERPRINT

Create the foundational representation for:

```text
manufacturer data
service UUIDs
characteristic UUIDs
device class
transport candidates
protocol candidates
firmware
hardware revision
```

The actual Android discovery logic comes later.

---

# 11. DEVICE SESSION

Represent:

```text
session ID
device identity
connection state
session creation time
last state update
saved/temporary classification
```

Do not persist real device data yet unless necessary for architecture tests.

---

# 12. CONNECTION STATE

Create a typed state representation.

Conceptually:

```text
UNKNOWN
DISCOVERED
PAIRED
CONNECTED
IDENTIFYING
CAPABILITY_DISCOVERY
READY
CONTROL_SESSION
DISCONNECTED
TEMPORARILY_UNAVAILABLE
ERROR
```

Do not create a Bluetooth connection implementation.

This is only the domain state model.

---

# 13. CAPABILITY MODEL

Create:

```text
UNKNOWN
UNSUPPORTED
READ_ONLY
SUPPORTED_VOLATILE
SUPPORTED_PERSISTENT
PERSISTENCE_VERIFIED
```

Capabilities should also be able to describe:

- readable
- writable
- transport
- protocol
- requires connection

---

# 14. DEVICE CAPABILITIES

Create the foundation for:

```text
ANC
Transparency
Adaptive ANC
Equalizer
Gestures
Wear Detection
Spatial Audio
Head Tracking
Multipoint
Battery
Case Battery
Firmware Info
Gaming Mode
Voice Prompts
Sidetone
Vendor Extensions
```

Do not assume all are supported.

The capability container describes what has been discovered.

---

# 15. BATTERY MODEL

Create the domain representation for:

```text
left
right
case
left charging
right charging
case charging
```

Use unknown values correctly.

Do not default unknown battery values to zero.

---

# 16. AUDIO DOMAIN MODEL

Create the foundational audio types.

Codec enum should account for:

```text
SBC
AAC
aptX
aptX HD
aptX Adaptive
aptX Lossless
LDAC
LC3
UNKNOWN
```

If a codec is not available on the target platform, that does not mean the domain model must remove it.

The model represents possible ecosystem capabilities.

---

# 17. CODEC STATE

Represent separately:

```text
supported
available
enabled
negotiated
active
configurable
```

Do not collapse them into one boolean.

Example:

```text
CodecCapability(
    supported = true,
    available = true,
    enabled = true,
    negotiated = false,
    active = false,
    configurable = false
)
```

The exact model may be improved if the architecture agent finds a better design.

---

# 18. AUDIO TRANSPORT

Represent:

```text
Classic A2DP
HFP
LE Audio
UNKNOWN
```

Do not implement transport.

---

# 19. AUDIO QUALITY MODEL

Create a domain representation for:

```text
codec
sample rate
bits per sample
bitrate
channel mode
quality mode
transport
active state
```

Unknown values must remain unknown.

Do not fabricate:

```text
96 kHz
24-bit
```

just because a headset advertises Hi-Res.

---

# 20. FEATURE ENUMERATION

Create a generic feature identity system.

Instead of hard-coding every UI action around:

```text
if sony...
if bose...
if samsung...
```

the core should eventually understand features generically.

Possible conceptual feature IDs:

```text
ANC
TRANSPARENCY
ADAPTIVE_ANC
EQUALIZER
GESTURES
WEAR_DETECTION
SPATIAL_AUDIO
HEAD_TRACKING
MULTIPOINT
BATTERY
CASE_BATTERY
FIRMWARE_INFO
GAMING_MODE
VOICE_PROMPTS
SIDETONE
```

Vendor-specific extensions must also be representable.

---

# 21. VENDOR EXTENSION MODEL

Create a mechanism that can represent:

```text
vendor
feature ID
feature metadata
capability state
vendor-specific payload
```

Do not create actual Sony/Bose/Apple implementations yet.

---

# 22. ERROR MODEL

Create a platform-independent error hierarchy.

At minimum:

```text
BluetoothDisabled
PermissionDenied
DeviceDisconnected
TransportUnavailable
ProtocolMismatch
UnsupportedFeature
ReadFailed
WriteRejected
VerificationFailed
Timeout
CodecUnavailable
UnknownDevice
InvalidState
```

The exact Kotlin representation should be selected by the architecture agent.

---

# 23. RESULT / OPERATION MODEL

Define how asynchronous hardware operations will eventually communicate success/failure.

Consider:

- success
- failure
- cancellation
- timeout
- unsupported
- unknown
- disconnected

Do not create a massive abstraction if Kotlin's native patterns are sufficient.

The design must remain simple and composable.

---

# 24. STATE MANAGEMENT

Establish a rule:

There must be a single authoritative state source for each domain object.

Avoid:

```text
UI state says ANC = ON
backend says ANC = OFF
notification says ANC = UNKNOWN
```

Future architecture should flow approximately:

```text
Transport / Protocol
        ↓
Device State
        ↓
Capability State
        ↓
Application State
        ↓
UI / Notification / Quick Settings
```

Phase 1 only establishes the contracts.

---

# 25. REPOSITORY ABSTRACTIONS

Where appropriate, define interfaces such as:

```text
DeviceRepository
CapabilityRepository
ProtocolRepository
```

Do not implement full persistence yet.

The objective is to prevent UI or future services from directly manipulating storage.

---

# 26. PROTOCOL ABSTRACTION

Define the conceptual protocol contract.

It should support future operations such as:

```text
identify
discoverCapabilities
readState
readBattery
readFirmware
setANC
setTransparency
readEQ
writeEQ
readGestures
writeGestures
```

However:

**Do not create fake implementations that claim these operations work.**

If a compile-time implementation is needed, use clearly named test doubles/fakes.

Never expose fake hardware behavior as production functionality.

---

# 27. TEST DOUBLES

Create test-only implementations where useful.

Example:

```text
FakeDeviceRepository
FakeCapabilityRepository
FakeProtocol
FakeDeviceSession
```

They must live in test infrastructure.

They must never accidentally ship as real device implementations.

---

# 28. DEPENDENCY INJECTION

Establish a dependency injection strategy.

Requirements:

- dependencies must be explicit
- platform services must be injectable
- domain code must be testable without Android
- tests must be able to replace transport/protocol implementations

Do not introduce a DI framework purely because it is popular.

Choose the simplest maintainable approach compatible with the project's expected scale.

Document the decision.

---

# 29. COROUTINE ARCHITECTURE

Bluetooth and protocol operations will eventually be asynchronous.

The foundation must support:

```text
suspend functions
Flow
StateFlow
structured concurrency
cancellation
timeouts
```

Rules:

- no blocking operations on the main thread
- cancellation must propagate
- lifecycle-bound operations must be cancellable
- long-running operations must not leak scopes
- retry behavior must remain explicit

---

# 30. CONCURRENCY MODEL

Document future expectations.

Potential concurrent activities:

```text
device connection
capability discovery
battery updates
state notifications
audio state
background reconnect
user commands
```

The architecture must prevent race conditions such as:

```text
User sets ANC = ON
        ↓
disconnect occurs
        ↓
stale response arrives
        ↓
state incorrectly becomes ON
```

Phase 1 does not implement the solution fully, but the state architecture must allow later implementation.

---

# 31. PACKAGE STRUCTURE

Establish clear package ownership.

Do not put everything under:

```text
com.omnibuds.app
```

with hundreds of unrelated classes.

Use logical package boundaries.

Example conceptual structure:

```text
com.omnibuds.core.device
com.omnibuds.core.capability
com.omnibuds.core.audio
com.omnibuds.core.protocol
com.omnibuds.core.state
com.omnibuds.core.diagnostics

com.omnibuds.android
com.omnibuds.android.bluetooth
com.omnibuds.android.audio
```

Adapt to the actual selected module architecture.

---

# 32. DEPENDENCY DIRECTION RULE

Enforce:

```text
Domain/Core
   ↑
Application
   ↑
Platform implementations
```

Core must not depend on:

- Android UI
- Android Bluetooth classes
- Android Activity
- Android Context
- vendor-specific implementation
- database implementation

Platform implementations may depend on core abstractions.

---

# 33. BUILD CONFIGURATION

Establish:

- Gradle wrapper
- settings
- build conventions
- Kotlin configuration
- Android configuration
- source compatibility
- test configuration
- dependency repositories
- version catalog if appropriate

Do not upgrade every dependency blindly.

If an existing repository has versions already selected, inspect compatibility before changing them.

---

# 34. DEPENDENCY POLICY

Every new dependency must have a reason.

Document:

```text
Dependency
Purpose
Why needed
Scope
Alternatives considered
Maintenance risk
KMP compatibility
License
```

Avoid dependency bloat.

---

# 35. STATIC ANALYSIS

Establish an appropriate Kotlin code-quality baseline.

Possible tools may include:

- Kotlin compiler checks
- Android lint
- formatting
- static analysis

Do not add five overlapping tools without justification.

The goal is consistent automated quality checking.

---

# 36. TEST FOUNDATION

Create:

```text
unit tests
architecture tests where useful
domain model tests
state transition tests
serialization tests where applicable
```

Phase 1 must have actual tests for the foundational models.

At minimum test:

### Device identity

- known manufacturer
- unknown manufacturer
- model missing
- firmware missing

### Capability

- unknown
- unsupported
- read-only
- volatile
- persistent
- persistence verified

### Codec state

- supported but inactive
- active
- unavailable
- unknown

### Device state

- valid transitions
- invalid transitions where enforced

### Battery

- known values
- unknown values
- charging state

---

# 37. ARCHITECTURE TESTS

If practical, create checks ensuring:

Core/domain does not depend on Android.

Examples:

```text
core → Android dependency = FAIL
domain → UI dependency = FAIL
protocol implementation → UI dependency = FAIL
```

The exact mechanism may be selected during implementation.

---

# 38. SERIALIZATION

Where models need serialization for future storage or communication:

Choose an appropriate serialization mechanism.

Requirements:

- stable field naming
- backward compatibility considerations
- nullable/unknown handling
- versioning strategy

Do not serialize Android-specific objects.

---

# 39. CONFIGURATION MODEL

Create a foundation for future configuration.

Separate:

### Application configuration

```text
debug logging
feature flags
diagnostic mode
```

from:

### Device configuration

```text
ANC mode
EQ
gesture mappings
```

from:

### Protocol configuration

```text
protocol version
transport parameters
```

Do not mix these concepts.

---

# 40. FEATURE FLAG RULE

If feature flags are introduced:

They must never be used to fake hardware capabilities.

A flag may control:

```text
experimental UI
experimental protocol adapter
debug diagnostics
```

It must not turn:

```text
unsupported ANC
```

into:

```text
supported ANC
```

---

# 41. LOGGING FOUNDATION

Create a minimal logging abstraction if needed.

It should support:

```text
DEBUG
INFO
WARN
ERROR
```

Future:

```text
TRACE
PACKET
```

must be opt-in and privacy-aware.

Do not log sensitive identifiers unnecessarily.

---

# 42. DIAGNOSTIC MODEL

Create foundational diagnostic types.

Potential:

```text
DiagnosticSeverity
DiagnosticCategory
DiagnosticEvent
DiagnosticSnapshot
```

Do not implement protocol packet logging yet.

---

# 43. DOCUMENTATION REQUIREMENTS

Update:

```text
README.md
```

with a concise project description and architecture overview.

The README must explicitly state:

> OmniBuds controls actual device capabilities and does not simulate unsupported hardware functionality.

Also document:

- Android-first
- Kotlin
- KMP-ready architecture
- backend-first development
- audio-path isolation
- vendor protocol architecture
- current development phase

Do not claim support for devices that are not implemented.

---

# 44. PHASE 1 DOCUMENTATION

Create:

```text
docs/phases/phase-1/
├── requirements.md
├── design.md
├── specs.md
├── task-list.md
├── test-plan.md
├── validation.md
├── decisions.md
└── risk-register.md
```

---

# 45. REQUIREMENTS.MD

`requirements.md` must contain detailed requirements using:

```text
REQ-P1-XXX
```

Each must include:

- requirement
- rationale
- dependencies
- acceptance criteria
- verification method
- priority

Cover at minimum:

```text
REQ-P1-001
Project must compile.

REQ-P1-002
Core must remain platform-independent.

REQ-P1-003
Android-specific code must remain isolated.

REQ-P1-004
Future KMP portability must be preserved.

REQ-P1-005
Device identity model must support unknown values.

REQ-P1-006
Capability model must support all required support states.

REQ-P1-007
Audio model must represent codec state.

REQ-P1-008
Device session model must distinguish active and saved concepts.

REQ-P1-009
Protocol abstractions must not fake hardware behavior.

REQ-P1-010
Tests must exist for domain foundations.

REQ-P1-011
Dependency direction must be enforceable.

REQ-P1-012
Build configuration must be reproducible.
```

Add any additional requirements discovered during architecture review.

---

# 46. DESIGN.MD

`design.md` must document:

- final module architecture
- package architecture
- dependency direction
- domain/platform boundaries
- KMP strategy
- state architecture
- capability architecture
- device architecture
- audio architecture
- protocol architecture
- testing architecture
- dependency injection
- concurrency model
- error model
- serialization model
- configuration model

Include diagrams.

---

# 47. SPECS.MD

`specs.md` must define:

- package naming
- module naming
- class naming
- interface naming
- data-model conventions
- enum conventions
- error conventions
- coroutine conventions
- Flow conventions
- state conventions
- dependency rules
- test conventions
- serialization rules
- API visibility rules
- documentation requirements

---

# 48. TASK-LIST.MD

Create atomic implementation tasks.

Example:

```text
P1-T001 Inspect existing repository.

P1-T002 Finalize module structure.

P1-T003 Configure Gradle foundation.

P1-T004 Configure Kotlin.

P1-T005 Establish package structure.

P1-T006 Create DeviceIdentity.

P1-T007 Create DeviceFingerprint.

P1-T008 Create DeviceSession.

P1-T009 Create connection state model.

P1-T010 Create capability model.

P1-T011 Create DeviceCapabilities.

P1-T012 Create BatteryState.

P1-T013 Create audio transport model.

P1-T014 Create codec model.

P1-T015 Create codec state model.

P1-T016 Create AudioTransportState.

P1-T017 Create error model.

P1-T018 Create protocol abstraction.

P1-T019 Create repository abstractions.

P1-T020 Establish dependency injection strategy.

P1-T021 Establish coroutine conventions.

P1-T022 Establish test infrastructure.

P1-T023 Add domain tests.

P1-T024 Add architecture validation.

P1-T025 Update README.

P1-T026 Perform final architecture review.

P1-T027 Perform Phase 1 validation.
```

The actual task list may be expanded substantially.

---

# 49. TEST PLAN

Create detailed tests.

Minimum categories:

## Build

```text
P1-BUILD-001
Clean build succeeds.

P1-BUILD-002
Debug build succeeds.

P1-BUILD-003
Tests compile.

P1-BUILD-004
No unexpected dependency resolution failure.
```

## Architecture

```text
P1-ARCH-001
Core does not import Android.

P1-ARCH-002
Domain does not import UI.

P1-ARCH-003
Vendor protocol layer does not own UI state.

P1-ARCH-004
Platform implementations depend on abstractions.
```

## Domain

```text
P1-DOM-001
DeviceIdentity handles unknown fields.

P1-DOM-002
Capability states are distinct.

P1-DOM-003
Codec state distinguishes supported from active.

P1-DOM-004
Battery unknown does not become 0.

P1-DOM-005
Connection states behave correctly.
```

## Protocol

```text
P1-PROTO-001
Protocol abstraction compiles without vendor implementation.

P1-PROTO-002
No production fake hardware adapter exists.
```

---

# 50. DEFINITION OF DONE

Phase 1 is NOT complete simply because Gradle builds.

Phase 1 is complete only when:

```text
[ ] Repository inspected
[ ] Architecture finalized
[ ] Modules established
[ ] Package boundaries established
[ ] Core/domain independent of Android
[ ] KMP boundary documented
[ ] Device models implemented
[ ] Capability model implemented
[ ] Audio domain model implemented
[ ] Codec model implemented
[ ] Error model implemented
[ ] Protocol abstractions implemented
[ ] Repository abstractions established
[ ] Coroutine rules established
[ ] Dependency injection strategy established
[ ] Test foundation established
[ ] Architecture validation established
[ ] Unit tests pass
[ ] Build passes
[ ] Documentation updated
[ ] No Bluetooth implementation accidentally added
[ ] No fake hardware implementation added
[ ] Phase validation completed
```

---

# 51. NO PREMATURE IMPLEMENTATION

The following are explicitly forbidden in Phase 1:

```text
BluetoothAdapter
BluetoothGatt
BluetoothDevice connection
BluetoothSocket
BluetoothLeAudio connection
A2DP control
codec switching
vendor packet transmission
ANC packet transmission
GATT characteristic writes
RFCOMM writes
```

The architecture may reference these concepts.

It must not implement them.

---

# 52. NO PREMATURE UI

Do not create:

- device screens
- ANC buttons
- EQ screens
- codec screens
- settings screens
- dashboards

unless a minimal build shell is genuinely necessary.

The user explicitly wants backend first.

---

# 53. NO FAKE IMPLEMENTATION

Do not create code like:

```kotlin
fun setAnc(mode: AncMode) {
    currentState = mode
}
```

and treat it as hardware control.

If a fake is required for tests:

```text
FakeProtocol
```

must be:

- test-only
- clearly named
- clearly documented
- impossible to mistake for production hardware support

---

# 54. ARCHITECTURE REVIEW BEFORE FINALIZATION

Before completing Phase 1, the orchestrator must ask:

### Question 1

Can Android-specific Bluetooth code later be added without modifying the domain model?

### Question 2

Can an RFCOMM device and a GATT device use the same higher-level protocol abstraction?

### Question 3

Can the audio engine represent AAC, LDAC, aptX and LC3 without rewriting its architecture?

### Question 4

Can vendor-specific features exist without polluting common feature interfaces?

### Question 5

Can a device be temporarily connected without being permanently saved?

### Question 6

Can persistent configuration be verified across reconnect?

### Question 7

Can the core eventually compile in a KMP environment?

### Question 8

Can every future UI surface consume the same authoritative device state?

If any answer is NO:

Fix the architecture before declaring Phase 1 complete.

---

# 55. REGRESSION REQUIREMENT

Before finishing Phase 1:

Run all available tests from Phase 0.

Do not break:

- documentation
- architecture rules
- existing repository behavior
- existing tests

If existing tests fail because of pre-existing issues:

Document them.

Do not silently ignore them.

---

# 56. FINAL VALIDATION

Update:

```text
docs/phases/phase-1/validation.md
```

with:

```text
Phase: 1
Status:

Build:
PASS / FAIL

Tests:
PASS / FAIL

Architecture:
PASS / FAIL

KMP readiness:
PASS / FAIL

Domain models:
PASS / FAIL

Documentation:
PASS / FAIL

Bluetooth implementation:
MUST BE NONE

Known issues:

Deferred:

Ready for Phase 2:
YES / NO
```

---

# 57. FINAL PHASE REPORT

At the end, provide:

```text
OMNIBUDS — PHASE 1 COMPLETE

Repository:
...

Architecture:
...

Modules created:
...

Core models:
...

Protocol abstractions:
...

Audio abstractions:
...

Tests:
...

Build:
...

Architecture checks:
...

Known limitations:
...

Deferred:
...

Phase 2 readiness:
READY / NOT READY
```

Do not start Phase 2.

---

# 58. FINAL STOP CONDITION

After completing Phase 1:

**STOP COMPLETELY.**

Do not automatically continue to:

- Bluetooth
- GATT
- RFCOMM
- device detection
- codec implementation
- ANC
- EQ
- vendor protocols
- UI
- Quick Settings
- Phase 2

Wait for an explicit user instruction:

> **Execute Phase 2**

Only then begin Phase 2.

# END OF PHASE 1