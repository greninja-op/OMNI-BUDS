# OMNIBUDS — PHASE 4
## Device Session & Lifecycle Management
### Complete Engineering Execution Prompt

You are the principal engineering orchestrator responsible for implementing Phase 4 of OmniBuds.

Your responsibility is to build a robust device-session management engine on top of the architecture established in Phases 0–3.

Inspect the actual repository, read all previous phase documentation, delegate independent work to specialized agents where available, implement the authorized scope, validate it using automated tests, update documentation, and stop at the Phase 4 boundary.

Do not restart the project.

Do not assume the repository exactly matches the planned architecture.

Do not skip requirements or silently expand scope.

---

# 1. PRODUCT CONTEXT

OmniBuds is a universal Bluetooth earbuds and headphones hardware-control application.

Its fundamental principle is:

"OmniBuds never simulates a hardware capability. It discovers, verifies, and controls capabilities implemented by the connected device."

The application must eventually support:

- Multiple Bluetooth device manufacturers.
- Automatic connected-device observation.
- Capability-aware controls.
- Vendor-specific protocols.
- Audio and codec information.
- Battery information.
- Device configuration.
- Android system integration.
- Future desktop support.

Phase 4 does not implement vendor control or production UI.

It establishes the session-management foundation that all subsequent device-related features will use.

---

# 2. MANDATORY PRE-EXECUTION AUDIT

Before editing:

1. Inspect the complete repository.
2. Read the Phase 0 engineering contract.
3. Read Phase 1 architecture and domain models.
4. Read Phase 2 Android Bluetooth foundation documentation.
5. Read Phase 3 connected-device detection documentation.
6. Inspect the current device observation implementation.
7. Inspect connection-state models.
8. Inspect repository abstractions.
9. Inspect coroutine and Flow conventions.
10. Inspect dependency injection.
11. Inspect existing test infrastructure.
12. Inspect Git status and current branch.
13. Identify unfinished work and architectural inconsistencies.

Do not recreate an existing implementation merely because its location differs from the proposed structure.

If a Phase 3 dependency is missing, identify whether it blocks Phase 4.

Do not silently treat incomplete functionality as verified.

Preserve unrelated changes.

---

# 3. PHASE 4 OBJECTIVE

Implement a centralized device-session management engine capable of:

- Creating sessions from valid device observations.
- Maintaining active device sessions.
- Updating session state as observations change.
- Handling disconnects.
- Handling reconnections.
- Supporting multiple simultaneous device sessions.
- Preventing duplicate sessions.
- Distinguishing temporary sessions from future saved-device profiles.
- Handling incomplete device identity.
- Handling observation failures.
- Handling application lifecycle changes.
- Exposing authoritative reactive session state.
- Cleaning up stale sessions.
- Providing deterministic session behavior for future UI and hardware-control engines.

The session engine must be platform-independent wherever practical.

Android Bluetooth observation remains behind the existing platform boundary.

---

# 4. SUB-AGENT ORCHESTRATION

Use specialized sub-agents if supported.

## Agent A — Architecture Auditor

Inspect previous phases and identify the correct integration points.

Responsibilities:

- Verify dependency direction.
- Inspect current session-related models.
- Identify existing reusable abstractions.
- Prevent duplicate state-management systems.

Output:

- Architecture review.
- Integration plan.
- Risk findings.

## Agent B — Session Domain Specialist

Design:

- DeviceSession.
- Session identity.
- Session lifecycle.
- Session status.
- Session timestamps.
- Session metadata.
- Session transitions.

Output:

- Domain models.
- State machine.
- Interface contracts.

## Agent C — Lifecycle and Reconciliation Specialist

Design and implement:

- Observation-to-session reconciliation.
- Connection transitions.
- Disconnect handling.
- Reconnection behavior.
- Stale-session cleanup.
- Duplicate suppression.

Output:

- Reconciliation engine.
- Transition tests.

## Agent D — Multi-Device Specialist

Handle:

- Multiple connected devices.
- Multiple profiles per device.
- Duplicate platform observations.
- Identity collisions.
- Missing identifiers.
- Stable session keys.

Output:

- Identity and deduplication strategy.
- Multi-device tests.

## Agent E — Reactive State Specialist

Implement:

- Session repository.
- StateFlow or equivalent authoritative state.
- Event publication.
- Atomic state updates.
- Concurrency safety.

Output:

- Repository implementation.
- Concurrency tests.

## Agent F — Testing and QA Specialist

Create deterministic tests for all authorized session behavior.

Output:

- Unit tests.
- Integration tests.
- Failure scenarios.
- Acceptance verification.

## Agent G — Security and Privacy Specialist

Review:

- Session retention.
- Device identifiers.
- Logging.
- Data minimization.
- Lifecycle cleanup.
- Separation of temporary and persistent data.

Output:

- Security review.
- Risk register updates.

## Agent H — Documentation Specialist

Create all mandatory Phase 4 documentation.

## Agent I — Integration Orchestrator

Integrate agent outputs, resolve conflicts, run tests, inspect the final diff, and produce the completion report.

Agents must not independently authorize Phase 5.

Avoid concurrent edits to the same files.

---

# 5. SESSION ARCHITECTURE

Implement a centralized session-management architecture.

Conceptual design:

```text
Connected Device Observation
             |
             v
Observation Normalizer
             |
             v
Session Reconciliation Engine
             |
             v
Device Session Repository
             |
             v
Authoritative Session State
             |
       +-----+------+
       |            |
       v            v
Active Sessions   Session Events
       |            |
       v            v
Future UI       Future Feature Engines
```

Adapt this to the existing architecture.

There must be one authoritative source of active session state.

Do not create separate competing active-device lists.

---

# 6. DEVICE SESSION MODEL

Create or extend a platform-independent session model.

Conceptual example:

```kotlin
data class DeviceSession(
    val sessionId: String,
    val deviceKey: String,
    val identity: DeviceIdentity,
    val connectionState: ConnectionState,
    val lifecycleState: SessionLifecycleState,
    val startedAt: Instant,
    val lastObservedAt: Instant?,
    val disconnectedAt: Instant?,
    val observationStatus: ObservationStatus
)
```

Adapt this to existing models.

Requirements:

- Session identifiers must be stable for the lifetime of a session.
- Device identity must not be fabricated.
- Unknown information must remain unknown.
- Session timestamps must have documented semantics.
- A session must not imply vendor support.
- A session must not imply control readiness.
- A session must not imply that hardware capabilities have been discovered.

Avoid unnecessary duplicated data.

---

# 7. SESSION LIFECYCLE

Implement a documented state machine.

Suggested conceptual states:

```text
CREATED
   |
   v
OBSERVING
   |
   v
ACTIVE
   |
   +------> TEMPORARILY_UNAVAILABLE
   |                  |
   |                  v
   |              RECONNECTED
   |                  |
   |                  v
   +---------------- ACTIVE
   |
   v
DISCONNECTED
   |
   v
ENDED
```

These states are conceptual and must be adapted to the actual observation guarantees.

Do not claim that Android exposes every intermediate connection transition.

Use only evidence-supported transitions.

Define:

- Valid transitions.
- Invalid transitions.
- Repeated-event behavior.
- Reconnection behavior.
- Session termination.
- Error recovery.

State updates must be deterministic.

---

# 8. OBSERVATION-TO-SESSION RECONCILIATION

The session engine must consume the existing Phase 3 observation stream.

It must not independently create another Bluetooth scanner or observer.

Implement reconciliation logic that:

1. Receives an observation snapshot.
2. Normalizes available identity information.
3. Matches observations to existing sessions where identity is sufficiently reliable.
4. Creates sessions for newly observed connected devices.
5. Updates existing sessions.
6. Detects disconnects.
7. Prevents duplicate sessions.
8. Publishes the resulting authoritative state.

Handle incomplete identity conservatively.

Do not merge devices solely because their display names match.

If the platform identity is insufficient for safe matching, represent ambiguity instead of silently merging devices.

---

# 9. DISCONNECT AND RECONNECT SEMANTICS

This is a critical product requirement.

When a device disconnects:

- Remove it from the active-connected projection.
- Record the appropriate session transition.
- Do not permanently save it.
- Do not delete a future user-saved device profile.
- Prevent stale active status.
- Handle duplicate disconnect events idempotently.

When a device reconnects:

- Detect whether it can be safely associated with an existing session.
- Update or create the appropriate session according to documented policy.
- Avoid duplicate active entries.
- Do not automatically initiate a Bluetooth connection.
- Do not automatically pair the device.

Define a clear policy for whether reconnection creates a new session ID or resumes an existing session.

The policy must be consistent and testable.

---

# 10. TEMPORARY SESSIONS VS SAVED DEVICES

Maintain a strict conceptual separation.

## Temporary device sessions

Represent currently observed or recently transitioned connection sessions.

They must not automatically become permanent user records.

## Future saved-device profiles

Represent devices explicitly saved by the user.

Phase 4 must not implement the complete persistent saved-device feature.

Do not create a permanent history of every Bluetooth device encountered.

Do not retain private Bluetooth identifiers unnecessarily.

---

# 11. MULTI-DEVICE SESSION MANAGEMENT

Support multiple active devices.

Requirements:

- Multiple simultaneous sessions.
- No single-device assumption.
- No duplicate sessions for multiple profile observations.
- Independent session transitions.
- Correct active-device projection.
- Stable identity matching.
- Missing-name handling.
- Ambiguous-identity handling.

Do not assume that every connected device is an earbud.

Do not introduce a global singleton device that prevents future multipoint or multiple-headset support.

---

# 12. AUTHORITATIVE REACTIVE STATE

Expose session state through a repository or equivalent abstraction.

Conceptual interface:

```kotlin
interface DeviceSessionRepository {

    val sessions: StateFlow<List<DeviceSession>>

    val activeSessions: StateFlow<List<DeviceSession>>

    val sessionEvents: Flow<DeviceSessionEvent>
}
```

Adapt this to existing project conventions.

Requirements:

- Thread-safe state updates.
- Deterministic ordering.
- Duplicate suppression.
- Cancellation safety.
- No leaked collectors.
- No unbounded event buffers.
- Explicit handling of observation failure.
- Consistent state snapshots.

Do not represent observation failure as an empty session list.

Distinguish:

- Successfully observed zero active devices.
- Observation unavailable.
- Permission denied.
- Bluetooth disabled.
- Observation failed.
- Session repository stopped.

---

# 13. SESSION EVENTS

Define appropriate domain events.

Possible events:

```text
SESSION_CREATED
SESSION_ACTIVATED
SESSION_UPDATED
SESSION_DISCONNECTED
SESSION_RECONNECTED
SESSION_ENDED
SESSION_IDENTITY_CHANGED
SESSION_OBSERVATION_FAILED
```

Use only events supported by the actual state model.

Events must:

- Be deterministic.
- Avoid unnecessary sensitive identifiers.
- Avoid duplicate emission.
- Have documented ordering.
- Remain independent of UI implementation.

Do not create a permanent event-history database in Phase 4.

---

# 14. CONCURRENCY AND LIFECYCLE

Requirements:

- One authoritative session-state owner.
- Structured concurrency.
- Explicit cancellation.
- Safe observation collection.
- Atomic reconciliation.
- No unmanaged global coroutine scopes.
- No blocking operations on the main thread.
- No unbounded polling.
- No resource leaks.
- Safe shutdown.
- Safe observer restart.

Handle rapid sequences such as:

```text
CONNECTED
DISCONNECTED
CONNECTED
DISCONNECTED
```

without inconsistent session state.

Define the behavior of stale or delayed observations.

Do not allow an old event to overwrite newer authoritative state without a documented reconciliation rule.

---

# 15. ERROR HANDLING

Represent:

- Observation unavailable.
- Permission denied.
- Bluetooth disabled.
- Invalid identity.
- Ambiguous identity.
- Reconciliation failure.
- Session repository failure.
- Cancellation.
- Unexpected platform exception.

Do not crash the application due to a session transition.

Do not silently swallow errors.

Do not expose stack traces or private identifiers to normal user-facing state.

---

# 16. FUTURE UI COMPATIBILITY

Although production UI is not part of this phase, the session API must support a future interface that can display:

- Multiple active devices.
- Connection state.
- Temporary availability.
- Device display name where known.
- Last observed state.
- Session status.
- Observation errors.

Do not create screens, layouts, navigation, or visual components.

Do not hardcode a single device into the state model.

---

# 17. EXPLICITLY FORBIDDEN IN PHASE 4

Do not implement:

- Device fingerprinting.
- Manufacturer identification.
- Vendor protocol discovery.
- GATT service discovery.
- RFCOMM control sessions.
- Battery communication.
- ANC.
- Transparency.
- EQ.
- Gesture configuration.
- Firmware information.
- Codec configuration.
- Audio processing.
- Persistent saved-device database.
- Quick Settings.
- Notifications.
- Widgets.
- Production UI.
- Automatic pairing.
- Automatic Bluetooth connection attempts.
- Firmware updates.

These belong to later phases.

---

# 18. REQUIRED DOCUMENTATION

Create:

```text
docs/phases/phase-4/
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
OB-P4-REQ-001
OB-P4-REQ-002
```

Document:

- Session lifecycle.
- State transitions.
- Identity matching.
- Reconnection policy.
- Disconnect semantics.
- Multi-device handling.
- Concurrency.
- Error handling.
- Temporary versus saved-device separation.
- Known limitations.

---

# 19. TESTING REQUIREMENTS

Use JVM and automated tests as the primary validation path.

Physical-device verification remains deferred to the final testing stage.

## Session creation

- New connected device creates a session.
- Repeated observation does not create duplicates.
- Missing identity is handled safely.
- Same display name does not merge distinct devices.

## Session updates

- Connection state updates correctly.
- Metadata updates do not unnecessarily replace the session.
- Profile changes do not create duplicate sessions.

## Disconnect

- Active projection updates.
- Duplicate disconnect is idempotent.
- Stale state is removed.
- Session lifecycle remains consistent.

## Reconnect

- Existing device is matched safely.
- New session behavior follows the documented policy.
- No duplicate active sessions.
- Unknown identity does not cause unsafe merging.

## Multiple devices

- Two or more devices remain independent.
- Disconnecting one does not affect another.
- Identical display names are handled.
- Partial observations are supported.

## Concurrency

- Rapid connection transitions.
- Concurrent snapshot reconciliation.
- Cancellation.
- Repository shutdown.
- Stale observation handling.
- Event ordering.

## Architecture

- Core remains Android-independent.
- Session engine does not depend on vendor protocols.
- No production fake hardware behavior.
- No unauthorized UI or persistence modules.

Document physical-device validation as deferred.

Do not report unexecuted device tests as passed.

---

# 20. ACCEPTANCE CRITERIA

Phase 4 is complete only when:

- [ ] Previous phase contracts are respected.
- [ ] Centralized session management exists.
- [ ] Session lifecycle is documented.
- [ ] Observation reconciliation is implemented.
- [ ] Disconnect handling is correct.
- [ ] Reconnection behavior is deterministic.
- [ ] Multiple devices are supported.
- [ ] Duplicate sessions are prevented.
- [ ] Incomplete identity is handled conservatively.
- [ ] Temporary sessions are separate from saved-device profiles.
- [ ] Reactive session state is authoritative.
- [ ] Concurrency and cancellation are tested.
- [ ] No vendor control functionality was introduced.
- [ ] No production UI was introduced.
- [ ] No unnecessary permanent device history exists.
- [ ] Automated tests pass or failures are documented.
- [ ] Build and static analysis pass or blockers are documented.
- [ ] Mandatory documentation exists.
- [ ] Physical-device validation is explicitly marked deferred.
- [ ] Git diff contains only authorized changes.
- [ ] Final validation report is complete.

---

# 21. STOP CONDITION

After completing Phase 4:

1. Stop implementation.
2. Do not begin Phase 5.
3. Do not implement device fingerprinting.
4. Do not implement vendor identification.
5. Do not create production UI.
6. Do not add unauthorized persistent storage.
7. Do not expand the scope silently.

Produce a final report containing:

- Completion status.
- Implemented modules.
- Files changed.
- Session state machine.
- Reconnection policy.
- Tests executed.
- Build and static-analysis results.
- Known limitations.
- Outstanding risks.
- Deferred physical-device verification.
- Git commit information, if applicable.
- Confirmation of readiness for Phase 5.

Wait for explicit user authorization before proceeding.

---

# FINAL ENGINEERING PRINCIPLE

A device session represents observed application state, not proof of hardware support.

Temporary connections must not become permanent records automatically.

Disconnected devices must not remain falsely active.

Multiple devices must remain independent.

Unknown identity must remain unknown.

Implement Phase 4 only.