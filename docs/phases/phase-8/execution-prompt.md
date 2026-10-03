# OMNIBUDS — PHASE 8
## Capability Discovery Engine
### Complete Engineering Execution Prompt

You are the principal engineering orchestrator responsible for implementing Phase 8 of OmniBuds.

Your objective is to establish a reliable, extensible, evidence-driven capability discovery engine that determines which hardware features are supported by a device, what operations are available, and how confidently each capability is known.

Inspect the actual repository, read all previous phase documentation, delegate independent tasks to specialized agents where supported, implement the authorized scope, run automated validation, update documentation, and stop at the Phase 8 boundary.

Do not restart the project.

Do not assume the repository exactly matches the proposed architecture.

Do not fabricate hardware capabilities, protocol responses, or successful discovery.

---

# 1. PRODUCT CONTEXT

OmniBuds is a universal Bluetooth earbuds and headphones hardware-control application.

Its fundamental principle is:

"OmniBuds never simulates a hardware capability. It discovers, verifies, and controls capabilities implemented by the connected device."

The application must support multiple manufacturers without reducing their feature sets to a common minimum.

Potential device capabilities include:

- Active Noise Cancellation (ANC).
- Transparency mode.
- Normal mode.
- Adaptive ANC.
- Custom ANC levels.
- Transparency intensity.
- Wind reduction.
- Environmental modes.
- Equalizer.
- Graphic EQ.
- Parametric EQ.
- Bass and treble adjustment.
- Gesture customization.
- Wear detection.
- Multipoint.
- Spatial audio.
- Head tracking.
- Gaming mode.
- Voice prompts.
- Sidetone.
- Auto transparency.
- Battery reporting.
- Charging state.
- Firmware information.
- Vendor-specific features.

A device may support only a subset.

A capability may also be:

- Supported but read-only.
- Supported and controllable.
- Supported but temporarily unavailable.
- Supported only through a particular transport.
- Unknown because evidence is insufficient.
- Unsupported based on verified evidence.

These states must not be conflated.

---

# 2. MANDATORY PRE-EXECUTION AUDIT

Before editing:

1. Inspect the complete repository.
2. Read Phase 0 engineering governance.
3. Read Phase 1 domain and architecture foundations.
4. Read Phase 2 Android Bluetooth foundation.
5. Read Phase 3 device observation.
6. Read Phase 4 session lifecycle.
7. Read Phase 5 identity and fingerprinting.
8. Read Phase 6 transport architecture.
9. Read Phase 7 protocol abstraction engine.
10. Inspect existing capability models.
11. Inspect DeviceIdentity and DeviceFingerprint.
12. Inspect DeviceSession.
13. Inspect protocol registry and resolver.
14. Inspect protocol verification levels.
15. Inspect error and result contracts.
16. Inspect coroutine and Flow conventions.
17. Inspect dependency injection.
18. Inspect test infrastructure.
19. Inspect build configuration.
20. Inspect Git status and current branch.

Reuse valid existing models.

Do not duplicate capability registries.

Do not silently rewrite earlier phase contracts.

If existing implementation differs from this prompt, document the discrepancy and integrate with the actual repository.

---

# 3. PHASE 8 OBJECTIVE

Implement a capability discovery engine that:

- Discovers device capabilities through protocol-defined discovery contracts.
- Represents individual capabilities independently.
- Distinguishes support from availability.
- Distinguishes read access from write access.
- Distinguishes verified facts from inferred information.
- Preserves vendor-specific extensions.
- Handles unknown and incomplete discovery.
- Supports capability dependencies.
- Supports transport-specific requirements.
- Supports protocol-version constraints.
- Prevents unsupported controls from being exposed as available.
- Produces deterministic discovery results.
- Supports future UI capability-driven rendering.
- Supports automated testing without hardware.

Phase 8 creates the discovery foundation.

It does not implement actual ANC, EQ, gestures, or other hardware controls.

---

# 4. SUB-AGENT ORCHESTRATION

Use specialized sub-agents where supported.

## Agent A — Repository and Architecture Auditor

Review previous phase implementation and establish integration points.

Deliver:

- Architecture audit.
- Existing capability model inventory.
- Dependency direction.
- Integration plan.
- Risks.

## Agent B — Capability Domain Architect

Design the core capability data model.

Responsibilities:

- CapabilityId.
- CapabilityCategory.
- CapabilityDescriptor.
- CapabilitySupport.
- CapabilityAvailability.
- CapabilityAccess.
- CapabilityEvidence.
- CapabilityDiscoveryResult.
- CapabilitySet.

Deliver:

- Domain model.
- State semantics.
- API contracts.

## Agent C — Discovery Engine Specialist

Implement the orchestration engine.

Responsibilities:

- Discovery lifecycle.
- Protocol integration.
- Discovery result aggregation.
- Partial discovery.
- Failure isolation.
- Deterministic output.
- Cancellation.

Deliver:

- Discovery engine.
- Unit tests.

## Agent D — Evidence and Verification Specialist

Design evidence-based capability classification.

Responsibilities:

- Protocol declarations.
- Explicit device responses.
- Verified protocol metadata.
- Inferred capabilities.
- Unknown values.
- Contradictory evidence.
- Confidence and verification status.

Deliver:

- Evidence model.
- Classification rules.
- Tests.

## Agent E — Capability Dependency Specialist

Design capability dependencies and constraints.

Examples:

- Adaptive ANC may require ANC.
- Head tracking may require spatial audio.
- A custom EQ may require an EQ engine.
- A feature may require a particular transport.

Do not assume these dependencies universally apply to every manufacturer.

Deliver:

- Dependency model.
- Constraint evaluation.
- Cycle detection.
- Tests.

## Agent F — Vendor Extension Specialist

Design a vendor extension mechanism.

Responsibilities:

- Namespaced capability IDs.
- Vendor-specific metadata.
- Version compatibility.
- Unknown extension preservation.
- Collision prevention.

Deliver:

- Extension contracts.
- Validation rules.

## Agent G — Protocol Integration Specialist

Integrate Phase 7 protocol descriptors and discovery contracts.

Requirements:

- Protocols declare which discovery operations they support.
- Discovery must not require every protocol to implement every capability.
- Unsupported discovery operations must remain explicit.
- Protocol verification status must constrain the claims made by the engine.

Deliver:

- Integration contracts.
- Compatibility tests.

## Agent H — Reliability and Security Specialist

Review:

- Malformed capability responses.
- Partial results.
- Timeouts.
- Cancellation.
- Conflicting evidence.
- Resource limits.
- Sensitive diagnostics.

Deliver:

- Risk assessment.
- Failure-handling rules.

## Agent I — Testing and QA Specialist

Create comprehensive automated tests.

Deliver:

- Unit tests.
- Contract tests.
- Architecture tests.
- Validation report.

## Agent J — Documentation Specialist

Create the complete Phase 8 documentation set.

## Agent K — Integration Orchestrator

Integrate agent work, resolve contradictions, run validation, inspect the final diff, and produce the completion report.

No agent may independently authorize Phase 9.

Avoid concurrent edits to the same files.

---

# 5. CAPABILITY ARCHITECTURE

Establish the following conceptual architecture:

```text
Device Identity
      |
      v
Resolved Protocol
      |
      v
Capability Discovery Engine
      |
      +-----------------------+
      |                       |
      v                       v
Protocol Discovery       Capability Registry
      |                       |
      +-----------+-----------+
                  |
                  v
          Evidence Evaluator
                  |
                  v
        Dependency Validator
                  |
                  v
         Capability Snapshot
                  |
                  v
         Device State Layer
```

Adapt to the actual repository.

The capability discovery engine must remain independent of UI rendering.

---

# 6. CAPABILITY IDENTITY

Create stable capability identifiers.

Potential common capabilities:

```text
ANC
TRANSPARENCY
NORMAL_MODE
ADAPTIVE_ANC
CUSTOM_ANC_LEVEL
TRANSPARENCY_LEVEL
WIND_REDUCTION
EQUALIZER
GRAPHIC_EQ
PARAMETRIC_EQ
BASS_ADJUSTMENT
TREBLE_ADJUSTMENT
GESTURE_CONFIGURATION
WEAR_DETECTION
MULTIPOINT
SPATIAL_AUDIO
HEAD_TRACKING
GAMING_MODE
VOICE_PROMPTS
SIDETONE
AUTO_TRANSPARENCY
BATTERY_REPORTING
CASE_BATTERY
FIRMWARE_INFORMATION
```

This list is a capability vocabulary, not a claim that every device supports these features.

Requirements:

- Stable IDs.
- Explicit categories.
- No identity based only on display text.
- Namespaced vendor extensions.
- Backward-compatible serialization strategy.
- Unknown capability preservation where safe.

Do not create a capability entry merely to make the UI look complete.

---

# 7. SUPPORT, ACCESS, AND AVAILABILITY

These dimensions must remain separate.

## Support state

```text
UNKNOWN
SUPPORTED
UNSUPPORTED
```

## Access state

```text
UNKNOWN
READ_ONLY
READ_WRITE
WRITE_ONLY
```

Use only states that can be represented accurately by the underlying protocol.

## Availability state

```text
UNKNOWN
AVAILABLE
UNAVAILABLE
TEMPORARILY_UNAVAILABLE
```

## Verification state

Reuse the Phase 7 verification model where possible.

Potential levels:

```text
RESEARCHED
INFERRED
IMPLEMENTED
AUTOMATED_TESTED
HARDWARE_VERIFIED
PERSISTENCE_VERIFIED
```

Do not create competing verification systems.

A capability may be supported but unavailable at the current moment.

A capability may be available for reading but not writing.

An unknown capability must not be treated as unsupported.

---

# 8. CAPABILITY EVIDENCE MODEL

Implement structured evidence.

Potential evidence sources:

- Verified protocol descriptor.
- Explicit protocol capability response.
- Device-reported feature flags.
- Verified model-specific protocol metadata.
- Firmware compatibility information.
- Transport availability.
- Inferred model information.
- Unknown or incomplete evidence.

Each evidence record should contain appropriate metadata such as:

- Evidence type.
- Source.
- Timestamp where applicable.
- Protocol identifier.
- Protocol version.
- Verification status.
- Supporting details.
- Limitations.

Requirements:

- Preserve evidence provenance.
- Do not invent confidence percentages.
- Do not treat manufacturer marketing descriptions as proof of runtime availability.
- Do not treat an implemented parser as proof of hardware support.
- Do not let weak evidence override explicit contradictory evidence silently.
- Record unresolved conflicts.

---

# 9. CAPABILITY DISCOVERY ENGINE

Implement a deterministic discovery coordinator.

Conceptual interface:

```kotlin
interface CapabilityDiscoveryEngine {

    suspend fun discover(
        session: DeviceSession
    ): CapabilityDiscoveryResult
}
```

Adapt to the existing domain architecture.

The engine must:

1. Validate the session.
2. Resolve the protocol context.
3. Determine available discovery operations.
4. Execute authorized read-only discovery operations.
5. Collect evidence.
6. Evaluate support states.
7. Evaluate access states.
8. Evaluate availability.
9. Validate dependencies.
10. Produce a capability snapshot.
11. Preserve partial results.
12. Report structured errors.

Do not automatically issue hardware-changing commands.

Do not automatically connect to additional transports.

Do not claim that discovery succeeded when no actual discovery evidence exists.

---

# 10. PARTIAL DISCOVERY

Discovery must tolerate incomplete information.

Examples:

- ANC information available, EQ unknown.
- Battery reporting available, case battery unsupported.
- Gesture support known, individual mappings unknown.
- Firmware information unavailable.
- A vendor extension could not be parsed.

Requirements:

- One failed capability must not invalidate all unrelated capabilities.
- Preserve successful evidence.
- Report partial completion explicitly.
- Distinguish failed discovery from verified unsupported status.
- Avoid replacing known information with fabricated defaults.
- Define deterministic merge behavior.

---

# 11. CAPABILITY DEPENDENCIES

Implement a dependency representation.

Conceptual example:

```text
HEAD_TRACKING
    |
    +-- requires SPATIAL_AUDIO
```

This is an illustrative relationship, not a universal device rule.

Requirements:

- Dependencies may be protocol-specific.
- Dependencies may be firmware-specific.
- Missing prerequisites must be represented explicitly.
- Detect dependency cycles.
- Do not infer support for a prerequisite merely because a dependent feature exists.
- Preserve manufacturer-specific exceptions.

The engine must not automatically enable prerequisite features.

---

# 12. VENDOR-SPECIFIC CAPABILITIES

OmniBuds must not discard unique vendor features.

Create a namespaced extension model.

Conceptual:

```text
vendor.bose.auto_transparency
vendor.sony.adaptive_sound_control
vendor.oneplus.gaming_mode
vendor.soundpeats.custom_anc
```

These are illustrative identifiers only.

Requirements:

- No unsupported capability claims.
- Stable namespacing.
- Versioned metadata.
- Type-safe values where known.
- Unknown extension handling.
- Collision prevention.
- Explicit documentation.
- No vendor-specific command implementation in Phase 8.

---

# 13. CAPABILITY SNAPSHOT

Create a structured snapshot representing the current discovery outcome.

Potential fields:

- Device identity reference.
- Protocol identity.
- Protocol version.
- Discovery timestamp.
- Discovery completion state.
- Capability collection.
- Evidence collection.
- Partial failures.
- Unresolved conflicts.
- Snapshot schema version.

Requirements:

- Immutable snapshot semantics where practical.
- Deterministic ordering.
- Explicit unknown values.
- No fake default capabilities.
- No unnecessary permanent device history.
- No coupling to UI widgets.

Do not create a persistent database unless previous architecture explicitly requires it.

---

# 14. DISCOVERY LIFECYCLE

Define lifecycle states.

Suggested model:

```text
NOT_STARTED
INITIALIZING
DISCOVERING
PARTIALLY_COMPLETE
COMPLETE
FAILED
CANCELLED
```

Define:

- Valid transitions.
- Repeated discovery behavior.
- Cancellation.
- Timeout.
- Partial completion.
- Protocol failure.
- Session disconnection.
- Snapshot replacement.
- State consistency.

Do not mark discovery complete if mandatory discovery work has not finished.

Partial completion must remain distinguishable from complete discovery.

---

# 15. ERROR HANDLING

Use structured errors.

Potential categories:

```text
SESSION_UNAVAILABLE
PROTOCOL_UNRESOLVED
DISCOVERY_UNSUPPORTED
DISCOVERY_TIMEOUT
MALFORMED_CAPABILITY_RESPONSE
CAPABILITY_CONFLICT
DEPENDENCY_INVALID
TRANSPORT_UNAVAILABLE
PERMISSION_DENIED
CANCELLED
UNKNOWN_ERROR
```

Adapt to the existing error taxonomy.

Requirements:

- Preserve partial results.
- Do not expose raw Android exceptions through core.
- Distinguish unknown from unsupported.
- Avoid unbounded retries.
- Do not retry side-effecting operations.
- Maintain diagnostic context without sensitive payload logging.

---

# 16. UI READINESS CONTRACT

Phase 8 must establish a capability model that future UI can consume.

For example:

```text
Device capability snapshot
        |
        v
UI feature availability
        |
        +--> ANC control visible only when supported
        |
        +--> EQ section visible only when supported
        |
        +--> Gesture editor visible only when supported
        |
        +--> Unknown features not presented as confirmed
```

Do not build the actual UI in Phase 8.

Do not introduce fake UI values.

Do not use generic feature availability as proof that an operation will succeed.

---

# 17. SECURITY AND RELIABILITY

Requirements:

- Treat capability responses as untrusted.
- Validate identifiers and payload sizes.
- Prevent malformed data from crashing discovery.
- Use bounded collections.
- Avoid unbounded event history.
- Support cancellation.
- Use explicit timeouts.
- Preserve evidence provenance.
- Avoid sensitive packet logging.
- Prevent invalid dependency cycles.
- Prevent one capability failure from corrupting unrelated results.
- Do not weaken architecture rules to make tests pass.

---

# 18. TESTING IMPLEMENTATION

Use JVM tests and architecture tests as the primary verification path.

Test-only scripted protocol responses are allowed.

They must remain in test source sets and must never be presented as real device support.

Required test groups:

## Capability model

- Support states.
- Access states.
- Availability states.
- Verification states.
- Unknown handling.
- Stable identifiers.
- Vendor namespaces.

## Discovery engine

- Successful discovery.
- Partial discovery.
- Unsupported discovery.
- Timeout.
- Cancellation.
- Session unavailable.
- Protocol unresolved.
- Empty evidence.
- Malformed response.

## Evidence

- Explicit support evidence.
- Inferred evidence.
- Conflicting evidence.
- Evidence provenance.
- Verification-level constraints.

## Dependencies

- Valid dependency.
- Missing prerequisite.
- Cycle detection.
- Vendor-specific dependency.
- Unknown prerequisite.

## Snapshot

- Deterministic ordering.
- Partial results.
- Schema version.
- Unknown values.
- Conflict preservation.

## Architecture

- Core remains Android-independent.
- No UI dependencies.
- No vendor command implementation.
- No fake production hardware capability.
- No unauthorized persistence.

---

# 19. EXPLICITLY FORBIDDEN IN PHASE 8

Do not implement:

- Actual ANC commands.
- Transparency writes.
- EQ writes.
- Gesture configuration.
- Battery communication.
- Firmware retrieval.
- Firmware updates.
- Codec negotiation.
- Audio processing.
- Vendor-specific packet encoders.
- Device-specific hardware claims without evidence.
- Production UI screens.
- Quick Settings.
- Notifications.
- Widgets.
- Physical-device testing requirements.
- Automatic Bluetooth connections.
- Automatic hardware-changing operations.

These belong to later phases.

---

# 20. REQUIRED DOCUMENTATION

Create:

```text
docs/phases/phase-8/
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
OB-P8-REQ-001
OB-P8-REQ-002
```

Document:

- Capability taxonomy.
- Support/access/availability distinctions.
- Evidence provenance.
- Verification constraints.
- Discovery lifecycle.
- Partial discovery.
- Dependency rules.
- Vendor extensions.
- Snapshot schema.
- Error handling.
- Security.
- Known limitations.

---

# 21. AUTOMATED VALIDATION

Before declaring completion:

1. Run relevant unit tests.
2. Run capability contract tests.
3. Run architecture tests.
4. Run static analysis.
5. Run applicable project builds.
6. Verify dependency direction.
7. Verify test-only scripted responses are not shipped as production support.
8. Inspect the complete Git diff.
9. Verify no unrelated changes were included.
10. Update validation documentation.

Physical-device verification must remain explicitly deferred.

Do not report unexecuted tests as passed.

---

# 22. ACCEPTANCE CRITERIA

Phase 8 is complete only when:

- [ ] Previous phase contracts are respected.
- [ ] Capability taxonomy exists.
- [ ] Support, access, and availability are distinct.
- [ ] Evidence provenance is represented.
- [ ] Verification status is respected.
- [ ] Discovery engine exists.
- [ ] Partial discovery is supported.
- [ ] Unknown and unsupported are distinct.
- [ ] Dependency validation exists.
- [ ] Vendor extension model exists.
- [ ] Capability snapshots are deterministic.
- [ ] Discovery lifecycle is explicit.
- [ ] Structured errors are implemented.
- [ ] No fake production hardware capabilities exist.
- [ ] No hardware-changing commands were introduced.
- [ ] Core remains platform-independent.
- [ ] Automated tests pass or failures are documented.
- [ ] Build and static analysis pass or blockers are documented.
- [ ] All mandatory documentation exists.
- [ ] Physical-device verification is marked deferred.
- [ ] Git diff contains only authorized changes.
- [ ] Final validation report is complete.

---

# 23. STOP CONDITION

After completing Phase 8:

1. Stop implementation.
2. Do not begin Phase 9.
3. Do not implement hardware feature controls.
4. Do not implement production UI.
5. Do not add unauthorized persistence.
6. Do not perform physical-device testing.
7. Do not silently expand scope.

Produce a final report containing:

- Completion status.
- Implemented modules.
- Files changed.
- Capability taxonomy.
- Discovery engine architecture.
- Evidence model.
- Dependency validation.
- Vendor extension design.
- Tests executed.
- Build and static-analysis results.
- Known limitations.
- Outstanding risks.
- Deferred physical-device verification.
- Git commit information, if applicable.
- Confirmation of readiness for Phase 9.

Wait for explicit user authorization before proceeding.

---

# FINAL ENGINEERING PRINCIPLE

A device name is not proof of a capability.

An inferred capability is not a verified capability.

An unknown capability is not an unsupported capability.

A supported feature is not necessarily available at this moment.

A capability declaration is not proof that a hardware command will succeed.

Implement Phase 8 only.