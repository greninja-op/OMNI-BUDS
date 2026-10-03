# OMNIBUDS — PHASE 6
## Bluetooth Transport Layer
### Complete Engineering Execution Prompt

You are the principal engineering orchestrator responsible for implementing Phase 6 of OmniBuds.

Your objective is to establish a modular, extensible, lifecycle-safe Bluetooth transport architecture that supports the future implementation of real vendor-specific earbud control protocols.

Inspect the actual repository, read all previous phase documentation, delegate independent tasks to specialized agents where supported, implement the authorized scope, run automated validation, update documentation, and stop at the Phase 6 boundary.

Do not restart the project.

Do not assume the repository exactly matches the planned architecture.

Do not invent successful hardware communication.

---

# 1. PRODUCT CONTEXT

OmniBuds is a universal Bluetooth earbuds and headphones hardware-control application.

Its fundamental principle is:

"OmniBuds never simulates a hardware capability. It discovers, verifies, and controls capabilities implemented by the connected device."

The application must eventually support multiple transport architectures, including:

- Bluetooth Low Energy (BLE)
- Generic Attribute Profile (GATT)
- Bluetooth Classic
- RFCOMM
- Serial Port Profile (SPP)
- A2DP
- AVRCP
- HFP/HSP
- Bluetooth LE Audio
- Future vendor-specific control transports

These technologies do not all serve the same purpose.

The transport architecture must distinguish:

1. Device-control communication.
2. Bluetooth connection and profile state.
3. Normal media-audio transport.
4. Future vendor-specific protocol sessions.

OmniBuds must not unnecessarily interfere with Android's normal media-audio path.

---

# 2. MANDATORY PRE-EXECUTION AUDIT

Before editing:

1. Inspect the complete repository.
2. Read Phase 0 engineering governance.
3. Read Phase 1 architecture and Kotlin module boundaries.
4. Read Phase 2 Android Bluetooth foundation.
5. Read Phase 3 connected-device observation.
6. Read Phase 4 session management.
7. Read Phase 5 identity and fingerprinting.
8. Inspect existing Bluetooth abstractions.
9. Inspect existing Android platform adapters.
10. Inspect coroutine and Flow conventions.
11. Inspect error models.
12. Inspect dependency injection.
13. Inspect test infrastructure.
14. Inspect build configuration.
15. Inspect Git status and branch.

Identify existing transport-related work before adding anything.

Reuse valid existing abstractions.

Do not duplicate platform Bluetooth managers.

Do not silently remove unfinished work.

---

# 3. PHASE 6 OBJECTIVE

Implement a transport layer that:

- Separates platform Bluetooth APIs from domain logic.
- Supports multiple transport types through explicit abstractions.
- Provides lifecycle-safe transport sessions.
- Defines connection and disconnection semantics.
- Provides structured transport errors.
- Supports cancellation and timeouts.
- Handles concurrent operations safely.
- Supports future protocol-specific transport selection.
- Prevents transport assumptions from leaking into vendor protocol code.
- Keeps normal media-audio transport separate from device-control communication.
- Supports deterministic automated testing.

The transport foundation must be useful for later phases without prematurely implementing vendor commands.

---

# 4. SUB-AGENT ORCHESTRATION

Use specialized agents if supported.

## Agent A — Architecture Auditor

Review previous phases and determine correct transport integration points.

Deliver:

- Existing architecture findings.
- Dependency boundaries.
- Integration plan.
- Risk assessment.

## Agent B — Bluetooth Transport Research Specialist

Review the Android Bluetooth transport APIs and their documented capabilities.

Investigate:

- BLE/GATT.
- Bluetooth Classic.
- RFCOMM.
- Profile proxies.
- A2DP.
- AVRCP-related platform boundaries.
- HFP/HSP.
- LE Audio.
- Android version constraints.
- Permission requirements.

Distinguish API availability from actual device support.

Deliver:

- Transport capability matrix.
- Platform constraints.
- Recommended abstractions.

## Agent C — Transport Domain Architect

Design:

- TransportType.
- TransportCapabilities.
- TransportState.
- TransportSession.
- TransportEndpoint.
- TransportOperation.
- TransportResult.
- TransportError.

Deliver:

- Domain models.
- Interfaces.
- State machine.

## Agent D — BLE/GATT Architecture Specialist

Establish the BLE/GATT transport abstraction.

Scope:

- GATT connection lifecycle.
- Service discovery boundary.
- Characteristic read/write contracts.
- Notification subscription contracts.
- MTU and payload capability representation.
- Timeout and cancellation semantics.

Do not implement device-specific GATT services or commands.

Deliver:

- BLE/GATT contracts.
- Lifecycle design.
- Test coverage.

## Agent E — Classic/RFCOMM Architecture Specialist

Establish Classic Bluetooth and RFCOMM abstraction boundaries.

Scope:

- RFCOMM session lifecycle.
- Stream ownership.
- Connection state.
- Read/write contracts.
- Timeout handling.
- Cancellation.
- Resource cleanup.

Do not implement proprietary command framing.

Deliver:

- RFCOMM contracts.
- Lifecycle design.
- Test coverage.

## Agent F — Audio Transport Boundary Specialist

Define how OmniBuds represents audio-related Bluetooth transport without interfering with Android's media path.

Scope:

- A2DP.
- HFP/HSP.
- LE Audio.
- Profile observation.
- Transport-state representation.
- Separation from control-channel sessions.

Do not implement codec configuration or audio processing.

Deliver:

- Audio/control separation design.
- API boundary.
- Limitations.

## Agent G — Concurrency and Reliability Specialist

Review:

- Structured concurrency.
- Cancellation.
- Timeouts.
- Operation serialization.
- Session shutdown.
- Resource cleanup.
- Race conditions.
- Retry safety.

Deliver:

- Reliability requirements.
- Failure scenarios.
- Concurrency tests.

## Agent H — Testing and QA Specialist

Build deterministic transport tests and architecture tests.

Deliver:

- Unit tests.
- Contract tests.
- Failure tests.
- Test report.

## Agent I — Security and Privacy Specialist

Review:

- Bluetooth permission boundaries.
- Device identifier handling.
- Data logging.
- Transport security assumptions.
- Sensitive packet data.
- Resource ownership.

Deliver:

- Security review.
- Risk register updates.

## Agent J — Documentation Specialist

Create all mandatory Phase 6 documentation.

## Agent K — Integration Orchestrator

Integrate agent work, resolve contradictions, run all validation, inspect the final diff, and produce the completion report.

No agent may independently authorize Phase 7.

Avoid concurrent edits to the same files.

---

# 5. TRANSPORT ARCHITECTURE

Establish a modular architecture.

Conceptual design:

```text
Device Session
      |
      v
Transport Resolver
      |
      v
Transport Manager
      |
      +-------------------+
      |                   |
      v                   v
Control Transports    Audio/Profile Observations
      |                   |
      +---------+---------+
                |
                v
       Platform Adapters
                |
                v
        Android Bluetooth APIs
```

Control transport candidates:

```text
TransportManager
├── BleTransport
├── GattTransport
├── RfcommTransport
└── FutureControlTransport
```

Audio and profile observations:

```text
AudioProfileObserver
├── A2DP
├── HFP/HSP
└── LE Audio
```

These are architectural boundaries, not a requirement to create every future module as an empty directory.

Use the repository's existing module organization.

---

# 6. CORE TRANSPORT CONTRACTS

Create or refine platform-independent interfaces.

Conceptual example:

```kotlin
interface DeviceTransport {

    val transportType: TransportType

    val state: StateFlow<TransportState>

    suspend fun connect(): TransportResult<Unit>

    suspend fun disconnect(): TransportResult<Unit>

    suspend fun close()
}
```

Adapt to the existing result and error models.

Requirements:

- Explicit ownership.
- Structured lifecycle.
- Cancellation support.
- No Android framework types in core interfaces.
- No vendor-specific command assumptions.
- No hidden automatic connection attempts.
- Clear operation outcomes.

Do not create one oversized interface requiring every transport to implement irrelevant methods.

Use capability-specific interfaces where appropriate.

---

# 7. TRANSPORT TYPES

Define an explicit transport taxonomy.

Potential categories:

```text
BLE
GATT
BLUETOOTH_CLASSIC
RFCOMM
A2DP
AVRCP
HFP
HSP
LE_AUDIO
UNKNOWN
```

Document which are:

- Physical transport technologies.
- Profiles.
- Application-level communication abstractions.
- Audio paths.
- Control channels.

Do not treat every item as interchangeable.

Avoid invalid combinations.

For example:

- GATT is not a substitute for RFCOMM.
- A2DP is not a generic vendor-control channel.
- LE Audio is not equivalent to Classic A2DP.
- A connected Bluetooth device does not prove an available control channel.

---

# 8. TRANSPORT STATE MACHINE

Define a documented lifecycle.

Suggested conceptual states:

```text
UNAVAILABLE
     |
     v
IDLE
     |
     v
CONNECTING
     |
     v
CONNECTED
     |
     v
CLOSING
     |
     v
CLOSED
```

Additional states may include:

- FAILED.
- DISCONNECTED.
- CANCELLING.
- UNKNOWN.

Adapt the model to actual platform behavior.

Define:

- Valid transitions.
- Invalid transitions.
- Repeated operation behavior.
- Cancellation behavior.
- Connection timeout.
- Disconnection cleanup.
- Error recovery.

Do not report a transport as connected before the underlying transport confirms it.

---

# 9. BLE/GATT TRANSPORT FOUNDATION

Establish the BLE/GATT contracts.

Required conceptual operations:

- Connect.
- Disconnect.
- Discover services.
- Read characteristic.
- Write characteristic.
- Subscribe to notifications.
- Unsubscribe.
- Close session.

Represent:

- Service UUID.
- Characteristic UUID.
- Descriptor UUID.
- Properties.
- Permissions where exposed.
- Payload size constraints.
- Notification state.
- Operation result.

Requirements:

- Do not assume every device exposes GATT.
- Do not assume service discovery succeeds.
- Do not assume every characteristic is writable.
- Do not perform arbitrary writes.
- Do not implement vendor-specific UUIDs without verified evidence.
- Handle cancellation and cleanup.
- Prevent notification subscription leaks.
- Serialize operations where required by the platform adapter.
- Keep platform callbacks outside the domain model.

No production device-specific GATT command implementation is authorized in this phase.

---

# 10. CLASSIC BLUETOOTH AND RFCOMM FOUNDATION

Establish the Classic Bluetooth and RFCOMM boundaries.

Required concepts:

- RFCOMM session.
- Socket lifecycle.
- Input stream.
- Output stream.
- Connection state.
- Read operation.
- Write operation.
- Close operation.
- Timeout.
- Cancellation.
- Structured error.

Requirements:

- Do not assume every device exposes RFCOMM.
- Do not invent service UUIDs.
- Do not initiate arbitrary vendor connections.
- Do not implement proprietary packet framing.
- Do not assume a successful socket connection proves protocol compatibility.
- Ensure streams and sockets are closed reliably.
- Prevent concurrent writes from corrupting future protocol framing.

Keep Android-specific socket types inside the platform implementation.

---

# 11. AUDIO TRANSPORT SEPARATION

This is a mandatory architectural constraint.

OmniBuds is a hardware-control application, not a music player.

Android and the Bluetooth stack must continue handling normal media audio.

The transport layer may represent:

- A2DP profile state.
- HFP/HSP profile state.
- LE Audio state.
- Available profile information.
- Audio transport observations.

It must not:

- Decode media audio.
- Re-encode media audio.
- Route media through OmniBuds unnecessarily.
- Replace Android's codec negotiation.
- Claim codec activation without evidence.
- Modify audio quality in this phase.

Keep audio transport observations separate from vendor control sessions.

---

# 12. TRANSPORT RESOLUTION

Define a future-compatible transport resolution interface.

Conceptual:

```kotlin
interface TransportResolver {

    suspend fun resolve(
        device: DeviceIdentity,
        fingerprint: DeviceFingerprint
    ): TransportResolution
}
```

Adapt to actual models.

A resolution may represent:

- Candidate transport.
- Evidence.
- Availability.
- Confidence.
- Unsupported.
- Unknown.
- Ambiguous.

Important:

Phase 6 must not implement manufacturer-specific transport-selection rules.

It must establish the contract and safe unknown behavior.

Do not automatically connect to every candidate transport.

---

# 13. OPERATION AND ERROR CONTRACTS

Use structured results.

Potential errors:

```text
TRANSPORT_UNAVAILABLE
UNSUPPORTED_TRANSPORT
PERMISSION_DENIED
BLUETOOTH_DISABLED
CONNECTION_FAILED
CONNECTION_TIMEOUT
DISCONNECTED
OPERATION_TIMEOUT
OPERATION_CANCELLED
INVALID_STATE
RESOURCE_CLOSED
PROTOCOL_NOT_IMPLEMENTED
UNKNOWN_ERROR
```

Adapt to the established error taxonomy.

Requirements:

- Do not expose raw Android exceptions through core.
- Preserve useful diagnostic context.
- Avoid logging sensitive packet contents.
- Distinguish retryable from non-retryable failures.
- Do not automatically retry side-effecting operations.
- Do not treat timeout as proof that an operation had no effect.

---

# 14. CONCURRENCY AND RESOURCE OWNERSHIP

Requirements:

- Structured concurrency.
- Explicit transport ownership.
- No unmanaged global coroutine scopes.
- Safe cancellation.
- Serialized operations where required.
- Bounded queues.
- Explicit timeouts.
- Deterministic shutdown.
- No leaked sockets.
- No leaked GATT clients.
- No leaked notification collectors.
- No main-thread blocking.
- No infinite reconnect loop.

A closed transport must not accept new operations.

A failed transport must expose a consistent state.

---

# 15. TESTING ARCHITECTURE

Create deterministic transport test implementations strictly within test source sets.

Test doubles must not masquerade as real hardware in production.

Support tests for:

- Transport state transitions.
- Connection timeout.
- Cancellation.
- Disconnection.
- Repeated close.
- Invalid operations.
- Concurrent operations.
- Resource cleanup.
- Error mapping.
- Resolver ambiguity.
- Audio/control separation.
- Architecture boundaries.

Use scripted transport behavior rather than fake claims of real Bluetooth hardware support.

Physical-device verification remains deferred.

---

# 16. EXPLICITLY FORBIDDEN IN PHASE 6

Do not implement:

- Vendor protocol commands.
- ANC controls.
- Transparency controls.
- EQ controls.
- Gesture configuration.
- Battery retrieval.
- Firmware retrieval.
- Codec selection.
- Codec negotiation.
- Audio processing.
- Device-specific service UUID databases.
- Device-specific packet encoders.
- Firmware updates.
- Production UI.
- Quick Settings.
- Notifications.
- Widgets.
- Automatic pairing.
- Automatic Bluetooth connection.
- Automatic device-control writes.

These belong to later phases.

---

# 17. REQUIRED DOCUMENTATION

Create:

```text
docs/phases/phase-6/
├── requirements.md
├── design.md
├── specs.md
├── task-list.md
├── test-plan.md
├── validation.md
├── decisions.md
└── risk-register.md
```

Every requirement must include:

- Requirement ID.
- Description.
- Rationale.
- Priority.
- Dependencies.
- Acceptance criteria.
- Verification method.

Use stable IDs:

```text
OB-P6-REQ-001
OB-P6-REQ-002
```

Document:

- Transport taxonomy.
- Control/audio separation.
- BLE/GATT interfaces.
- RFCOMM interfaces.
- Transport lifecycle.
- Error mapping.
- Timeouts.
- Cancellation.
- Resource ownership.
- Concurrency.
- Platform constraints.
- Known limitations.
- Future extension points.

---

# 18. AUTOMATED VALIDATION

Use JVM tests, architecture tests, static analysis, and build validation as the primary verification path.

Physical-device testing remains deferred.

Required test groups:

## Core contracts

- Transport type validation.
- State transitions.
- Invalid operations.
- Structured errors.
- Resolver results.

## BLE/GATT

- Service discovery outcomes.
- Characteristic read/write contracts.
- Notification lifecycle.
- Cancellation.
- Cleanup.
- Unsupported operations.

## RFCOMM

- Connection lifecycle.
- Read/write failures.
- Timeout.
- Cancellation.
- Socket cleanup.
- Repeated close.

## Audio separation

- No audio processing dependency.
- No vendor control dependency on A2DP.
- No false codec-active claims.

## Concurrency

- Concurrent operation safety.
- Shutdown races.
- Cancellation races.
- State consistency.

## Architecture

- Core does not import Android framework classes.
- Vendor protocols do not depend on Android Bluetooth socket classes.
- Test doubles remain test-only.
- No production fake hardware behavior.

Document physical-device validation as deferred.

---

# 19. ACCEPTANCE CRITERIA

Phase 6 is complete only when:

- [ ] Previous phase contracts are respected.
- [ ] Transport taxonomy is explicit.
- [ ] BLE/GATT abstractions exist.
- [ ] Classic/RFCOMM abstractions exist.
- [ ] Audio profile observation is architecturally separate.
- [ ] Transport lifecycle is documented.
- [ ] Structured error mapping exists.
- [ ] Cancellation and timeout behavior are defined.
- [ ] Resource ownership is explicit.
- [ ] Concurrent operation behavior is tested.
- [ ] Unknown transport behavior is safe.
- [ ] No unsupported transport is claimed available.
- [ ] No vendor-specific command was introduced.
- [ ] No audio processing was introduced.
- [ ] Core remains platform-independent.
- [ ] Automated tests pass or failures are documented.
- [ ] Build and static analysis pass or blockers are documented.
- [ ] All mandatory documentation exists.
- [ ] Physical-device verification is marked deferred.
- [ ] Git diff contains only authorized changes.
- [ ] Final validation report is complete.

---

# 20. STOP CONDITION

After completing Phase 6:

1. Stop implementation.
2. Do not begin Phase 7.
3. Do not implement vendor protocols.
4. Do not implement capability discovery.
5. Do not implement ANC/EQ/gesture controls.
6. Do not implement production UI.
7. Do not add unauthorized persistent storage.
8. Do not silently expand scope.

Produce a final report containing:

- Completion status.
- Implemented modules.
- Files changed.
- Transport architecture.
- BLE/GATT contracts.
- RFCOMM contracts.
- Audio/control separation.
- Tests executed.
- Build and static-analysis results.
- Known limitations.
- Outstanding risks.
- Deferred physical-device verification.
- Git commit information, if applicable.
- Confirmation of readiness for Phase 7.

Wait for explicit user authorization before proceeding.

---

# FINAL ENGINEERING PRINCIPLE

A Bluetooth connection is not proof of a usable control channel.

A transport abstraction is not proof of hardware compatibility.

A successful transport connection is not proof of vendor protocol support.

Normal media audio must remain under Android's Bluetooth audio stack.

Implement Phase 6 only.