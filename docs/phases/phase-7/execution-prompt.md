# OMNIBUDS — PHASE 7
## Protocol Abstraction Engine
### Complete Engineering Execution Prompt

You are the principal engineering orchestrator responsible for implementing Phase 7 of OmniBuds.

Your responsibility is to establish a modular, extensible, transport-independent protocol architecture that supports future real vendor-specific earbud and headphone control implementations.

Inspect the actual repository, read all previous phase documentation, delegate independent work to specialized agents where supported, implement the authorized scope, run automated validation, update documentation, and stop at the Phase 7 boundary.

Do not restart the project.

Do not assume the repository exactly matches the proposed architecture.

Do not fabricate hardware support, protocol behavior, or successful communication.

---

# 1. PRODUCT CONTEXT

OmniBuds is a universal Bluetooth earbuds and headphones hardware-control application.

Its core principle is:

"OmniBuds never simulates a hardware capability. It discovers, verifies, and controls capabilities implemented by the connected device."

The application must eventually support multiple manufacturers and preserve their unique features.

Examples include:

- Sony
- Bose
- JBL
- Samsung
- Apple
- OnePlus
- Oppo
- Motorola
- Nothing
- Soundcore
- SoundPEATS
- Baseus
- Other compatible manufacturers

Different manufacturers may use:

- BLE/GATT characteristics.
- Bluetooth Classic.
- RFCOMM/SPP.
- Proprietary packet framing.
- Different command identifiers.
- Different response formats.
- Different state representations.
- Different persistence behavior.
- Different firmware compatibility rules.

The architecture must support these differences without forcing all devices into one lowest-common-denominator protocol.

---

# 2. MANDATORY PRE-EXECUTION AUDIT

Before editing:

1. Inspect the complete repository.
2. Read Phase 0 engineering governance.
3. Read Phase 1 architecture and Kotlin domain models.
4. Read Phase 2 Android Bluetooth foundation.
5. Read Phase 3 connected-device detection.
6. Read Phase 4 session management.
7. Read Phase 5 identity and fingerprinting.
8. Read Phase 6 transport architecture.
9. Inspect existing protocol abstractions.
10. Inspect transport interfaces.
11. Inspect DeviceIdentity and DeviceFingerprint.
12. Inspect DeviceSession.
13. Inspect error and result models.
14. Inspect coroutine and Flow conventions.
15. Inspect dependency injection.
16. Inspect testing infrastructure.
17. Inspect Git status and current branch.

Reuse existing contracts where appropriate.

Do not introduce duplicate protocol managers.

Do not silently change earlier phase behavior.

If previous implementation details differ from this prompt, document the discrepancy and integrate with the actual architecture.

---

# 3. PHASE 7 OBJECTIVE

Implement a protocol abstraction engine capable of:

- Representing different vendor protocols.
- Separating protocol logic from Bluetooth transport.
- Registering protocol implementations.
- Resolving protocol candidates using device identity evidence.
- Representing unsupported and unknown protocols.
- Exposing structured protocol capabilities.
- Defining command and response contracts.
- Supporting asynchronous device state updates.
- Handling protocol errors.
- Supporting protocol versioning.
- Supporting future vendor extensions.
- Supporting deterministic automated protocol tests.
- Preventing unverified protocol implementations from being treated as production-ready.

Phase 7 establishes the framework.

It does not implement actual ANC, EQ, gesture, battery, or vendor command behavior.

---

# 4. SUB-AGENT ORCHESTRATION

Use specialized sub-agents where supported.

## Agent A — Repository and Architecture Auditor

Inspect previous phases and establish the correct integration points.

Deliver:

- Architecture audit.
- Existing contract reuse.
- Dependency direction.
- Integration plan.
- Risks.

## Agent B — Protocol Domain Architect

Design:

- EarbudProtocol.
- ProtocolDescriptor.
- ProtocolIdentity.
- ProtocolVersion.
- ProtocolSession.
- ProtocolState.
- ProtocolCapability.
- ProtocolCommand.
- ProtocolResponse.
- ProtocolError.
- ProtocolResolution.

Deliver:

- Domain contracts.
- State machine.
- API specifications.

## Agent C — Transport/Protocol Boundary Specialist

Establish how protocol implementations consume Phase 6 transports.

Requirements:

- Protocols must not depend directly on Android Bluetooth framework classes.
- Transport selection and protocol parsing must remain separate.
- Protocol implementations must declare transport requirements.
- Unsupported transport combinations must fail explicitly.

Deliver:

- Transport adapter contracts.
- Dependency validation.

## Agent D — Protocol Registry Specialist

Implement an extensible registry.

Responsibilities:

- Protocol registration.
- Protocol identifiers.
- Manufacturer association.
- Version compatibility.
- Implementation availability.
- Verification status.
- Duplicate registration handling.
- Deterministic lookup.

Deliver:

- Registry implementation.
- Registration tests.

## Agent E — Protocol Resolution Specialist

Implement safe protocol resolution.

Resolution must use available identity and fingerprint evidence.

Possible outcomes:

- Exact protocol candidate.
- Multiple candidates.
- Unknown protocol.
- Unsupported device.
- Insufficient identity evidence.
- Incompatible version.

Do not select a protocol merely because a Bluetooth name contains a manufacturer name.

Deliver:

- Resolver.
- Ambiguity handling.
- Tests.

## Agent F — Command and Response Contract Specialist

Define generic protocol operation contracts.

Responsibilities:

- Command identity.
- Payload representation.
- Response correlation.
- Timeout semantics.
- Operation result.
- Error mapping.
- Side-effect classification.
- Retry safety.

Do not invent real vendor opcodes.

Deliver:

- Command contracts.
- Error model integration.

## Agent G — State and Event Specialist

Design:

- Protocol session lifecycle.
- Protocol readiness.
- State observation.
- Asynchronous updates.
- Session closure.
- Event ordering.

Deliver:

- State model.
- Lifecycle tests.

## Agent H — Security and Reliability Specialist

Review:

- Untrusted packet data.
- Payload size limits.
- Malformed responses.
- Sensitive data logging.
- Cancellation.
- Timeouts.
- Resource ownership.
- Retry behavior.

Deliver:

- Security review.
- Reliability findings.

## Agent I — Testing and QA Specialist

Create deterministic tests for:

- Protocol registration.
- Protocol resolution.
- Command contracts.
- Response parsing boundaries.
- State transitions.
- Unknown protocol handling.
- Error propagation.
- Architecture boundaries.

Deliver:

- Automated test suite.
- Test report.

## Agent J — Documentation Specialist

Create the complete Phase 7 documentation set.

## Agent K — Integration Orchestrator

Integrate the work, resolve conflicts, run validation, inspect the final diff, and produce the completion report.

No agent may independently authorize Phase 8.

Avoid concurrent edits to the same files.

---

# 5. PROTOCOL ARCHITECTURE

Establish the following conceptual architecture:

```text
Device Session
      |
      v
Device Identity
      |
      v
Protocol Resolver
      |
      v
Protocol Registry
      |
      v
Protocol Descriptor
      |
      v
Protocol Factory
      |
      v
Protocol Session
      |
      v
Transport Adapter
      |
      v
Bluetooth Transport
```

Adapt this design to the existing repository.

Important boundaries:

- Device identity does not prove protocol compatibility.
- Protocol resolution does not prove successful communication.
- A protocol implementation does not imply all features are supported.
- Transport availability does not imply protocol readiness.
- Protocol readiness does not imply hardware verification.

---

# 6. CORE PROTOCOL CONTRACT

Create or refine a platform-independent protocol interface.

Conceptual example:

```kotlin
interface EarbudProtocol {

    val descriptor: ProtocolDescriptor

    val state: StateFlow<ProtocolState>

    suspend fun initialize(): ProtocolResult<Unit>

    suspend fun close()

    suspend fun execute(
        command: ProtocolCommand
    ): ProtocolResult<ProtocolResponse>
}
```

Adapt this interface to the existing result and state conventions.

Do not force all protocols to support irrelevant operations.

Use smaller capability-specific interfaces where appropriate.

Requirements:

- No Android framework types in core protocol contracts.
- Explicit protocol lifecycle.
- Structured results.
- Cancellation support.
- Defined timeout behavior.
- No fabricated responses.
- No production fake hardware behavior.

---

# 7. PROTOCOL DESCRIPTOR

Create a structured protocol descriptor.

Potential fields:

- Protocol ID.
- Protocol display name.
- Manufacturer association.
- Supported protocol versions.
- Required transport types.
- Minimum compatibility constraints.
- Implementation version.
- Verification status.
- Supported operation categories.
- Known limitations.
- Documentation reference.

Use stable identifiers.

Do not use display names as protocol identity keys.

---

# 8. PROTOCOL VERIFICATION STATUS

Establish explicit verification levels.

Suggested statuses:

```text
RESEARCHED
INFERRED
IMPLEMENTED
AUTOMATED_TESTED
HARDWARE_VERIFIED
PERSISTENCE_VERIFIED
```

Define exact semantics for every status.

Do not automatically promote an implementation to hardware-verified because its unit tests pass.

Do not claim persistence verification without the required real-device evidence.

Physical verification remains deferred.

---

# 9. PROTOCOL REGISTRY

Implement a central registry abstraction.

Conceptual:

```kotlin
interface ProtocolRegistry {

    fun findById(
        protocolId: ProtocolId
    ): ProtocolDescriptor?

    fun findCandidates(
        identity: DeviceIdentity,
        fingerprint: DeviceFingerprint
    ): List<ProtocolDescriptor>
}
```

Adapt to the actual architecture.

Requirements:

- Deterministic registration.
- Duplicate protocol ID rejection or explicit replacement policy.
- Version awareness.
- Clear unknown behavior.
- No hidden network lookup.
- No manufacturer-specific control logic inside the generic registry.
- No coupling to UI.
- No permanent user-device history.

---

# 10. PROTOCOL RESOLUTION

Implement a protocol resolver.

Conceptual outcomes:

```text
RESOLVED
AMBIGUOUS
UNKNOWN
UNSUPPORTED
INSUFFICIENT_EVIDENCE
INCOMPATIBLE_VERSION
```

Requirements:

- Use documented identity evidence.
- Preserve ambiguity.
- Do not select arbitrary candidates.
- Do not treat a manufacturer match as an exact protocol match.
- Do not initiate a transport connection automatically.
- Do not execute commands during resolution.
- Keep resolution deterministic.

The resolver must be independently testable.

---

# 11. COMMAND AND RESPONSE CONTRACTS

Define generic protocol command models.

Potential fields:

- Command ID.
- Operation category.
- Payload.
- Correlation ID.
- Timeout.
- Side-effect classification.
- Retry policy.
- Expected response type.

Potential side-effect classifications:

```text
READ_ONLY
STATE_MUTATION
IRREVERSIBLE
UNKNOWN_SIDE_EFFECT
```

Requirements:

- Unknown commands must fail explicitly.
- Malformed responses must not crash the engine.
- Side-effecting writes must not be blindly retried.
- A timeout must not be interpreted as proof that a write failed.
- Command correlation must be deterministic.
- Payload sizes must be bounded.
- Sensitive payloads must not be logged by default.

Do not create invented vendor opcodes or packet layouts.

---

# 12. PROTOCOL SESSION LIFECYCLE

Define a documented lifecycle.

Suggested states:

```text
UNRESOLVED
RESOLVED
CREATED
INITIALIZING
READY
DEGRADED
FAILED
CLOSING
CLOSED
```

Adapt to the actual implementation.

Define:

- Valid transitions.
- Initialization failure.
- Transport loss.
- Cancellation.
- Reinitialization policy.
- Session closure.
- Operation behavior while not ready.

Do not mark a protocol ready before its actual initialization contract succeeds.

Do not automatically retry unsafe initialization commands.

---

# 13. ASYNCHRONOUS STATE OBSERVATION

Establish the contract for future device state notifications.

Support conceptual categories:

- State changed.
- Command completed.
- Transport disconnected.
- Protocol error.
- Initialization completed.
- Device-reported capability update.

Requirements:

- Bounded event delivery.
- Cancellation-safe collectors.
- Documented event ordering.
- No unbounded event history.
- No fake hardware state changes.
- No conflation of requested state with device-confirmed state.

Keep future UI state separate from protocol events.

---

# 14. VENDOR EXTENSION ARCHITECTURE

Preserve manufacturer-specific functionality.

A future device may expose:

- Unique ANC modes.
- Custom EQ behavior.
- Spatial audio.
- Head tracking.
- Gaming features.
- Proprietary gesture assignments.
- Vendor-specific diagnostics.

The protocol architecture must allow these without forcing them into a generic interface.

Use a structured vendor-extension mechanism.

Requirements:

- Namespaced identifiers.
- Explicit type information.
- Version compatibility.
- Unknown extension handling.
- No collision with common feature IDs.
- No assumptions that all vendors implement the same extension.

Do not implement actual vendor-specific features in Phase 7.

---

# 15. SECURITY AND RELIABILITY

Requirements:

- Treat received packet data as untrusted.
- Validate payload lengths.
- Validate response structure.
- Avoid logging raw sensitive data.
- Use structured errors.
- Support cancellation.
- Use explicit timeouts.
- Avoid infinite retry loops.
- Prevent resource leaks.
- Avoid blocking the main thread.
- Maintain deterministic operation ownership.
- Prevent malformed protocol data from crashing the application.

Do not weaken existing security or architecture rules to make tests pass.

---

# 16. TESTING IMPLEMENTATION

Use JVM tests and automated validation as the primary verification path.

Test-only scripted protocol implementations are allowed.

They must remain confined to test source sets and must never be presented as production hardware support.

Required tests:

## Registry

- Valid registration.
- Duplicate registration.
- Unknown protocol.
- Version mismatch.
- Deterministic lookup.

## Resolver

- Exact candidate.
- Ambiguous candidates.
- Unknown device.
- Insufficient evidence.
- Manufacturer-only identity.
- Incompatible protocol.
- Conflicting identity evidence.

## Protocol lifecycle

- Initialization success.
- Initialization failure.
- Cancellation.
- Timeout.
- Close.
- Repeated close.
- Operation before readiness.
- Transport loss.

## Commands

- Valid command.
- Unknown command.
- Malformed response.
- Response correlation.
- Side-effect retry protection.
- Payload limits.

## Events

- Event ordering.
- Cancellation.
- Bounded delivery.
- State consistency.

## Architecture

- Core remains Android-independent.
- Protocols depend on transport abstractions, not Android Bluetooth classes.
- Registry does not depend on UI.
- Test-only implementations are not shipped as real vendor support.

---

# 17. EXPLICITLY FORBIDDEN IN PHASE 7

Do not implement:

- Real vendor ANC commands.
- Transparency commands.
- EQ commands.
- Gesture writes.
- Battery communication.
- Firmware communication.
- Codec configuration.
- Firmware updates.
- Production vendor protocol databases.
- Device-specific GATT UUID implementations without verified evidence.
- Proprietary RFCOMM packet formats.
- Production UI.
- Quick Settings.
- Notifications.
- Widgets.
- Automatic Bluetooth pairing.
- Automatic connection attempts.
- Physical-device testing requirements.

These belong to later phases.

---

# 18. REQUIRED DOCUMENTATION

Create:

```text
docs/phases/phase-7/
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
OB-P7-REQ-001
OB-P7-REQ-002
```

Document:

- Protocol architecture.
- Transport boundary.
- Registry schema.
- Resolution rules.
- Protocol lifecycle.
- Command/response contracts.
- Error handling.
- Retry policy.
- Verification levels.
- Vendor extensions.
- Security.
- Known limitations.

---

# 19. AUTOMATED VALIDATION

Before declaring completion:

1. Run the relevant unit tests.
2. Run protocol contract tests.
3. Run architecture tests.
4. Run static analysis.
5. Run the applicable project build.
6. Verify module dependency direction.
7. Verify test-only implementations are not included in production artifacts.
8. Inspect the complete Git diff.
9. Verify no unrelated changes were included.
10. Update validation documentation.

Physical-device protocol verification must remain explicitly deferred.

Do not report unexecuted tests as passed.

---

# 20. ACCEPTANCE CRITERIA

Phase 7 is complete only when:

- [ ] Previous phase contracts are respected.
- [ ] Core protocol interface exists.
- [ ] Protocol descriptors are structured.
- [ ] Protocol registry exists.
- [ ] Protocol resolution is deterministic.
- [ ] Unknown and ambiguous protocols are handled safely.
- [ ] Protocol lifecycle is explicit.
- [ ] Command and response contracts exist.
- [ ] Error mapping is structured.
- [ ] Retry safety is defined.
- [ ] Vendor extension architecture exists.
- [ ] Verification levels are explicit.
- [ ] No invented vendor commands exist.
- [ ] No false hardware support is claimed.
- [ ] Core remains platform-independent.
- [ ] Automated tests pass or failures are documented.
- [ ] Build and static analysis pass or blockers are documented.
- [ ] All mandatory documentation exists.
- [ ] Physical-device verification is marked deferred.
- [ ] Git diff contains only authorized changes.
- [ ] Final validation report is complete.

---

# 21. STOP CONDITION

After completing Phase 7:

1. Stop implementation.
2. Do not begin Phase 8.
3. Do not implement capability discovery.
4. Do not implement real vendor controls.
5. Do not add production UI.
6. Do not add unauthorized persistent storage.
7. Do not silently expand scope.

Produce a final report containing:

- Completion status.
- Implemented modules.
- Files changed.
- Protocol interface design.
- Registry architecture.
- Resolution behavior.
- Verification-status model.
- Tests executed.
- Build and static-analysis results.
- Known limitations.
- Outstanding risks.
- Deferred physical-device verification.
- Git commit information, if applicable.
- Confirmation of readiness for Phase 8.

Wait for explicit user authorization before proceeding.

---

# FINAL ENGINEERING PRINCIPLE

A known device is not automatically a supported device.

A registered protocol is not automatically a verified protocol.

A successful command request is not proof that the device applied the requested change.

A requested state must remain distinct from device-confirmed state.

Implement Phase 7 only.