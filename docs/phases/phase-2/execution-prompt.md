# OMNIBUDS — PHASE 2
## Android Bluetooth Foundation
### Complete Engineering Execution Prompt

You are the engineering orchestrator responsible for implementing Phase 2 of OmniBuds.

This is a production-oriented Android Bluetooth infrastructure task. You must inspect the existing repository, understand the architecture established in Phases 0 and 1, delegate independent work to specialized sub-agents where supported, implement the authorized scope, validate it, document all decisions, and stop at the phase boundary.

Do not skip requirements, invent hardware capabilities, or begin future phases.

---

# 1. PRODUCT CONTEXT

OmniBuds is a universal Bluetooth earbuds and headphones hardware-control application.

Its purpose is to provide one application for controlling compatible devices from multiple manufacturers.

The product must discover, identify, inspect, and eventually control real hardware capabilities.

Core principle:

"OmniBuds never simulates a hardware capability. It discovers, verifies, and controls capabilities implemented by the connected device."

Audio principle:

OmniBuds must not interfere unnecessarily with the normal Android media-audio path. Android and the Bluetooth stack remain responsible for normal audio transport and codec negotiation.

OmniBuds will eventually support:

- Bluetooth Classic
- BLE
- GATT
- RFCOMM/SPP
- A2DP
- AVRCP
- HFP/HSP
- LE Audio
- Vendor-specific control transports

However, Phase 2 establishes only the foundational Android Bluetooth infrastructure and transport boundaries.

---

# 2. MANDATORY PRE-EXECUTION AUDIT

Before modifying anything:

1. Inspect the complete repository structure.
2. Read the Phase 0 engineering contract.
3. Read every relevant Phase 1 document.
4. Inspect the actual Gradle configuration.
5. Inspect module dependencies.
6. Inspect the Android library boundary selected during Phase 1.
7. Inspect existing domain models and interfaces.
8. Inspect test infrastructure.
9. Inspect Git status and existing changes.
10. Identify incomplete or conflicting architecture decisions.

Do not assume the repository matches the intended architecture.

If a required Phase 1 artifact is missing, document the issue and determine whether Phase 2 can safely proceed.

Do not silently overwrite previous work.

Preserve unrelated user changes.

---

# 3. PHASE 2 OBJECTIVE

Establish a reliable, permission-aware, lifecycle-safe Android Bluetooth foundation.

At completion, OmniBuds should have:

- A platform-isolated Android Bluetooth implementation boundary.
- Bluetooth adapter availability inspection.
- Bluetooth adapter state observation.
- Runtime permission requirements modeled by Android version.
- Permission-state inspection.
- Bluetooth capability inspection.
- Safe access to system Bluetooth services.
- Foundation abstractions for Classic Bluetooth and BLE.
- Foundation abstractions for GATT and RFCOMM.
- Coroutine and lifecycle-safe operation patterns.
- Structured Bluetooth errors.
- Testable interfaces and fake platform dependencies for unit tests.
- Android manifest configuration appropriate to the authorized scope.
- Documentation and tests for every implemented component.

This phase must not claim that earbuds are fully identified or controlled.

---

# 4. SUB-AGENT ORCHESTRATION

Use specialized sub-agents if available.

The orchestrator must assign independent responsibilities and prevent conflicting edits.

## Agent A — Repository and Architecture Auditor

Responsibilities:

- Inspect Phase 0 and Phase 1.
- Verify current module boundaries.
- Identify dependency-direction violations.
- Identify missing interfaces.
- Report architectural risks.

Output:

- Architecture audit.
- Required changes.
- Compatibility recommendations.

## Agent B — Android Bluetooth API Specialist

Responsibilities:

- Inspect Android Bluetooth APIs relevant to the supported project configuration.
- Determine appropriate adapter and manager abstractions.
- Identify version-specific API constraints.
- Verify Android platform documentation where needed.

Output:

- Bluetooth API design.
- Platform compatibility matrix.
- API usage recommendations.

## Agent C — Permission and Privacy Specialist

Responsibilities:

- Define permission requirements by Android version.
- Inspect manifest declarations.
- Separate scan, connect, and location-related requirements where applicable.
- Prevent unnecessary permission requests.
- Define permission-denied and permanently-denied behavior.

Output:

- Permission matrix.
- Permission-state model.
- Privacy and security considerations.

## Agent D — Bluetooth State and Lifecycle Specialist

Responsibilities:

- Design adapter-state observation.
- Handle adapter unavailable, disabled, enabling, enabled, and error states.
- Define lifecycle-safe observation.
- Define cancellation and resource cleanup.

Output:

- State machine.
- Coroutine and lifecycle design.
- Cleanup requirements.

## Agent E — Transport Architecture Specialist

Responsibilities:

- Establish transport boundaries for:
  - Bluetooth Classic
  - BLE
  - GATT
  - RFCOMM
  - A2DP
  - AVRCP
  - HFP
  - LE Audio

Do not implement full transport protocols.

Output:

- Transport abstractions.
- Capability and availability boundaries.
- Deferred implementation list.

## Agent F — Testing and QA Specialist

Responsibilities:

- Define unit tests.
- Define Android-specific tests.
- Define permission tests.
- Define state-transition tests.
- Define lifecycle and cancellation tests.
- Define test doubles.

Output:

- Test plan.
- Test cases.
- Acceptance criteria.

## Agent G — Security Specialist

Responsibilities:

- Inspect permission handling.
- Prevent accidental device-data logging.
- Ensure no credentials or sensitive captured data are committed.
- Review Bluetooth operation boundaries.
- Review error exposure.

Output:

- Security review.
- Risk register updates.

## Agent H — Documentation Specialist

Responsibilities:

Create all mandatory Phase 2 documentation.

Do not document functionality that was not implemented or tested.

## Agent I — Integration Orchestrator

Responsibilities:

- Review all agent outputs.
- Resolve conflicting decisions.
- Integrate implementation.
- Run tests and static checks.
- Review Git diff.
- Verify documentation.
- Confirm phase scope.
- Produce final report.

No sub-agent may independently authorize Phase 3.

---

# 5. AUTHORIZED IMPLEMENTATION SCOPE

## 5.1 Android Bluetooth platform boundary

Implement a clean boundary between Android framework APIs and the platform-independent core.

Possible conceptual structure:

```text
android/
├── bluetooth/
│   ├── adapter/
│   ├── permissions/
│   ├── state/
│   ├── classic/
│   ├── ble/
│   ├── transport/
│   ├── lifecycle/
│   └── errors/
```

Adapt this structure to the actual Phase 1 architecture.

Do not create unnecessary empty modules.

Android framework types must not leak into platform-independent domain models.

## 5.2 Bluetooth adapter abstraction

Establish an interface for inspecting the local Bluetooth adapter.

Conceptual responsibilities:

```kotlin
interface BluetoothPlatform {

    fun isBluetoothAvailable(): Boolean

    fun observeAdapterState(): Flow<BluetoothAdapterState>

    suspend fun getPlatformCapabilities(): BluetoothPlatformCapabilities
}
```

This is illustrative, not a requirement to copy the exact API.

Use appropriate suspend functions, Flow, Result types, and structured errors consistent with Phase 1.

The implementation should distinguish:

- Bluetooth hardware unavailable
- Adapter unavailable
- Adapter disabled
- Adapter enabling
- Adapter enabled
- Adapter disabling
- State unknown
- Platform access denied
- Platform operation failed

Do not treat a missing adapter as an application crash.

## 5.3 Permission architecture

Implement a centralized permission-state model.

Conceptual states:

```text
NOT_REQUIRED
NOT_REQUESTED
GRANTED
DENIED
DENIED_PERMANENTLY
REQUIRES_USER_ACTION
UNKNOWN
```

The implementation must account for relevant Android version differences.

Inspect and verify the requirements for:

- BLUETOOTH
- BLUETOOTH_ADMIN
- BLUETOOTH_SCAN
- BLUETOOTH_CONNECT
- ACCESS_FINE_LOCATION
- ACCESS_COARSE_LOCATION

Do not blindly declare or request every permission.

Determine which permissions are required for each authorized operation and Android version.

Important:

- Do not request location permission without a documented reason.
- Do not request scan permission when an operation only requires an already-authorized connection-state inspection.
- Do not assume a permission grant guarantees that Bluetooth hardware is available.
- Do not silently bypass denied permissions.
- Do not repeatedly trigger permission prompts.
- Do not claim a permission is permanently denied unless the platform context supports that conclusion.

Create a centralized permission requirement resolver.

## 5.4 Bluetooth adapter state observation

Create a reliable adapter-state observation mechanism.

It must handle:

- Initial state retrieval.
- State changes.
- Duplicate broadcast prevention.
- Lifecycle registration and unregistration.
- Coroutine cancellation.
- Resource cleanup.
- Missing or unavailable adapter.
- Permission failures.

Use an appropriate Android state-observation mechanism.

Do not introduce permanent background polling.

Prefer event-driven observation where supported.

## 5.5 Bluetooth platform capabilities

Create a platform capability model describing what the current Android device and OS can expose.

Possible fields:

- Bluetooth availability
- BLE support
- Classic Bluetooth support
- GATT support
- LE Audio API availability
- Relevant API-level features
- Permission requirements
- Unsupported or unknown platform capabilities

Distinguish:

1. OS API availability
2. Phone hardware capability
3. Runtime permission availability
4. Actual connected-device support

These are different facts.

Do not claim LE Audio is active merely because an API exists.

Do not claim a phone supports a feature without evidence.

## 5.6 Transport boundaries

Establish transport interfaces for future implementations.

Potential abstractions:

```kotlin
interface BluetoothTransport

interface BleTransport : BluetoothTransport

interface GattTransport : BluetoothTransport

interface ClassicTransport : BluetoothTransport

interface RfcommTransport : BluetoothTransport

interface LeAudioTransport : BluetoothTransport
```

Adapt them to the existing architecture.

At this stage, these are infrastructure boundaries only.

Do not implement vendor-specific communication.

Do not introduce fake successful hardware responses.

## 5.7 Bluetooth operation errors

Create structured errors for:

- Bluetooth unavailable
- Adapter disabled
- Permission denied
- Unsupported operation
- Platform API unavailable
- Operation cancelled
- Connection unavailable
- Resource unavailable
- Platform exception
- Unknown failure

Preserve underlying causes for diagnostics without exposing sensitive information to normal UI-facing messages.

## 5.8 Coroutine and concurrency rules

Define and implement safe coroutine conventions.

Requirements:

- No blocking Bluetooth operations on the main thread.
- Structured concurrency.
- Explicit cancellation behavior.
- Bounded operations where applicable.
- No unmanaged global coroutine scopes.
- No leaked receivers or callbacks.
- No duplicate concurrent adapter observers.
- No unbounded retry loops.
- No hidden background polling.

Use dispatchers and dependency injection consistently with Phase 1.

## 5.9 Dependency injection

Provide a testable way to supply:

- Bluetooth platform implementation
- Permission-state provider
- Adapter-state observer
- Transport factories
- Platform capability provider

Follow the existing dependency injection strategy.

Do not introduce a large dependency solely for a trivial abstraction.

---

# 6. EXPLICITLY FORBIDDEN IN PHASE 2

Do not implement:

- Earbud identification
- Device fingerprinting
- Full paired-device repository
- Automatic connected-earbud session creation
- Vendor identification
- GATT service discovery
- GATT characteristic enumeration
- GATT characteristic writes
- RFCOMM protocol communication
- Vendor packet encoders or decoders
- ANC controls
- Transparency controls
- EQ controls
- Gesture configuration
- Battery retrieval from earbuds
- Firmware retrieval
- Codec switching
- LDAC configuration
- aptX configuration
- AAC configuration
- LC3 configuration
- Audio processing
- Media playback
- Quick Settings tiles
- Notification controls
- Home-screen widgets
- Background monitoring service
- UI screens
- Firmware updates
- Protocol reverse engineering

These belong to later authorized phases.

Do not create fake hardware implementations to make tests appear successful.

Test doubles may simulate platform dependency responses strictly inside tests.

---

# 7. AUDIO ISOLATION REQUIREMENT

OmniBuds is a hardware-control application, not a music player.

Phase 2 must not:

- Intercept media audio.
- Decode Bluetooth media streams.
- Re-encode audio.
- Modify normal Android audio routing.
- Force a codec.
- Claim an audio codec is active.
- Change audio quality settings.

The architecture must preserve the ability to inspect audio transport later without unnecessarily interfering with Android's audio stack.

---

# 8. REQUIRED DOCUMENTATION

Create:

```text
docs/phases/phase-2/
├── requirements.md
├── design.md
├── specs.md
├── task-list.md
├── test-plan.md
├── validation.md
├── decisions.md
└── risk-register.md
```

## requirements.md

Every requirement must contain:

- Requirement ID
- Description
- Rationale
- Priority
- Dependencies
- Acceptance criteria
- Verification method

Use stable IDs such as:

```text
OB-P2-REQ-001
OB-P2-REQ-002
```

## design.md

Document:

- Module architecture
- Dependency direction
- Android platform boundary
- Bluetooth adapter abstraction
- Permission architecture
- State observation
- Transport boundaries
- Data flow
- Lifecycle
- Concurrency
- Error handling
- Security
- Extensibility
- Android platform limitations

## specs.md

Document:

- Public interfaces
- Data models
- Enums
- Permission matrix
- Adapter state machine
- Error contracts
- Coroutine contracts
- Cancellation
- Timeouts
- Resource cleanup
- Dependency injection
- Compatibility requirements

## task-list.md

Each task must contain:

- Task ID
- Objective
- Dependencies
- Files or modules
- Implementation notes
- Required tests
- Acceptance criteria
- Status

Tasks must be atomic and executable.

## test-plan.md

Include:

- Unit tests
- Permission tests
- Adapter-state tests
- Lifecycle tests
- Cancellation tests
- Error tests
- Architecture tests
- Android-specific tests
- Manual device validation

## validation.md

Record actual:

- Build results
- Test results
- Static analysis
- Architecture checks
- Manifest review
- Permission review
- Scope review
- Remaining issues

Never mark a test as passed if it was not executed.

## decisions.md

Record significant architectural choices and alternatives considered.

## risk-register.md

Include:

- Risk ID
- Description
- Probability
- Impact
- Mitigation
- Owner
- Status

---

# 9. TESTING REQUIREMENTS

At minimum, test:

### Adapter

- Bluetooth available.
- Bluetooth unavailable.
- Adapter enabled.
- Adapter disabled.
- Adapter state transition.
- Unknown state.
- Platform exception.

### Permissions

- Permission granted.
- Permission denied.
- Permission not requested.
- Permission not required.
- Version-dependent permission resolution.
- Permission revocation during operation.

### Lifecycle

- Observer starts.
- Observer stops.
- Cancellation cleans up registrations.
- No duplicate observer registration.
- No leaked callbacks.

### Architecture

- Core does not depend on Android framework classes.
- Android implementation depends on core abstractions in the permitted direction.
- Transport interfaces do not introduce vendor-specific dependencies.
- No unauthorized UI or protocol modules.

### Failure behavior

- Missing Bluetooth adapter.
- Permission failure.
- Platform API unavailable.
- Operation cancellation.
- Unexpected platform exception.

Do not require physical Bluetooth hardware for every unit test.

Document which tests require a real Android device.

---

# 10. GIT AND CHANGE MANAGEMENT

Respect the Git setup chosen during Phase 1.

Before implementation:

- Inspect current branch.
- Inspect working tree.
- Preserve unrelated changes.
- Do not rewrite history.
- Do not commit secrets or captured private device data.

If the repository is initialized and the agreed workflow permits it, create a Phase 2 commit only after all validation succeeds.

If the working tree contains unrelated changes, do not include them.

---

# 11. ACCEPTANCE CRITERIA

Phase 2 is complete only when:

- [ ] Phase 0 and Phase 1 contracts are respected.
- [ ] Android Bluetooth platform boundary exists.
- [ ] Adapter availability can be inspected.
- [ ] Adapter state can be observed safely.
- [ ] Permission requirements are centralized and version-aware.
- [ ] Permission failures are represented correctly.
- [ ] Platform capability information is modeled accurately.
- [ ] Transport abstractions are established.
- [ ] Structured Bluetooth errors exist.
- [ ] Coroutine cancellation and lifecycle cleanup are tested.
- [ ] Core remains independent of Android framework APIs.
- [ ] No fake hardware capability is exposed.
- [ ] No vendor protocol has been implemented.
- [ ] No audio path has been modified.
- [ ] All mandatory documentation exists.
- [ ] Tests pass or failures are explicitly documented.
- [ ] Build and static analysis pass, or blockers are documented.
- [ ] Git diff contains only authorized Phase 2 changes.
- [ ] Final validation report is complete.

---

# 12. STOP CONDITION

This is mandatory.

After completing Phase 2:

1. Stop implementation.
2. Do not begin Phase 3.
3. Do not automatically implement device discovery.
4. Do not expand into vendor protocol work.
5. Do not begin UI development.
6. Do not silently add future-phase features.

Produce a final report containing:

- Phase 2 completion status
- Implemented components
- Files and modules changed
- Architecture decisions
- Tests executed
- Build and static-analysis results
- Permission compatibility findings
- Known limitations
- Outstanding risks
- Deferred work
- Git commit information, if applicable
- Confirmation that the repository is ready for Phase 3

Wait for explicit user authorization before proceeding.

---

# FINAL ENGINEERING PRINCIPLE

Build the foundation correctly rather than rushing toward visible features.

OmniBuds must remain:

- Hardware-truthful
- Capability-aware
- Permission-aware
- Lifecycle-safe
- Audio-quality-conscious
- Modular
- Testable
- Extensible
- Ready for future Android and desktop implementations

Execute Phase 2 only.