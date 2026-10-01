# OMNIBUDS — PHASE 3
## Connected Device Detection
### Production-Oriented Engineering Execution Prompt

You are the principal engineering orchestrator responsible for implementing Phase 3 of OmniBuds.

Your task is to build a reliable Android connected-device detection foundation using the architecture established in Phases 0, 1, and 2.

You must inspect the actual repository, read the previous phase documentation, delegate independent tasks to specialized sub-agents where available, implement the authorized scope, execute tests, update documentation, and stop at the Phase 3 boundary.

Do not restart the project.

Do not assume that the repository is identical to the planned architecture.

Do not silently skip requirements.

---

# 1. PRODUCT CONTEXT

OmniBuds is a universal Bluetooth earbuds and headphones hardware-control application.

Its core principle is:

"OmniBuds never simulates a hardware capability. It discovers, verifies, and controls capabilities implemented by the connected device."

The application must eventually support compatible devices from multiple manufacturers.

However, this phase focuses exclusively on detecting and observing connected Bluetooth devices.

## Critical product requirement

When a user connects earbuds through Android's normal Bluetooth settings, OmniBuds should be able to observe the relevant connection state without unnecessarily initiating a second pairing process.

Android remains responsible for Bluetooth pairing and normal audio connections.

OmniBuds must observe the available system state rather than interfere with it.

---

# 2. MANDATORY PRE-EXECUTION AUDIT

Before modifying files:

1. Inspect the complete repository.
2. Read Phase 0 engineering governance.
3. Read Phase 1 architecture and domain-model documentation.
4. Read Phase 2 requirements, design, specifications, decisions, and validation.
5. Inspect the actual Android Bluetooth platform implementation.
6. Inspect the permission resolver.
7. Inspect adapter-state observation.
8. Inspect existing ADB deployment infrastructure.
9. Inspect all existing device-related models.
10. Inspect dependency injection.
11. Inspect test infrastructure.
12. Inspect Git status and current branch.
13. Identify unfinished work or unresolved Phase 2 blockers.

If Phase 2 is not complete, do not silently treat it as complete.

Determine whether the missing work is a hard dependency for Phase 3.

If required infrastructure is missing, report the blocker and do not create a parallel implementation that contradicts Phase 2.

Preserve unrelated user changes and active agent work.

---

# 3. PHASE 3 OBJECTIVE

Implement an Android connected-device observation engine that can:

- Observe relevant Bluetooth connection state.
- Identify currently connected device candidates.
- Distinguish paired devices from connected devices.
- Observe connection and disconnection transitions.
- Represent device availability accurately.
- Handle permission denial.
- Handle adapter disablement.
- Handle Android lifecycle changes.
- Handle multiple connected Bluetooth devices.
- Avoid duplicate device entries.
- Publish device observations through Kotlin Flow.
- Remain independent of vendor-specific protocols.
- Support deterministic testing.

At completion, OmniBuds should have a reliable foundation for the later device-session and fingerprinting phases.

This phase does not establish that a detected device is an earbud.

---

# 4. SUB-AGENT ORCHESTRATION

Use specialized sub-agents if supported.

## Agent A — Repository and Architecture Auditor

Inspect existing architecture and determine how Phase 3 should integrate without violating previous phase boundaries.

Output:

- Architecture audit.
- Dependency findings.
- Integration plan.

## Agent B — Android Bluetooth Connection Researcher

Investigate Android's supported ways of observing Bluetooth connection state.

Evaluate relevant APIs and profile-specific connection information.

Distinguish:

- Bluetooth bonded state.
- Bluetooth connection state.
- Bluetooth profile connection state.
- BLE connection state.
- Audio profile connection state.
- Device availability.

Do not assume one Android API exposes every connected device.

Output:

- API compatibility matrix.
- Supported observation methods.
- Platform limitations.

## Agent C — Device Observation Engine Specialist

Design and implement:

- Device observation models.
- Connected-device repository.
- Observation streams.
- Snapshot reconciliation.
- Duplicate prevention.
- Connection transition handling.

Output:

- Implementation.
- Tests.
- Interface contracts.

## Agent D — Android Lifecycle Specialist

Handle:

- Receiver registration.
- Receiver unregistration.
- Flow cancellation.
- Adapter disablement.
- Permission revocation.
- Observer restart.
- Resource cleanup.

Output:

- Lifecycle-safe implementation.
- Lifecycle tests.

## Agent E — Device Identity Specialist

Establish a temporary device identity representation using only information Android legitimately exposes.

Distinguish:

- Platform identifier.
- Bluetooth address, where available.
- Device name.
- Device type.
- Bonded status.
- Observed profiles.
- Connection state.

Do not implement Phase 5 fingerprinting.

Do not infer manufacturer or model from an unreliable device name.

Output:

- Identity observation model.
- Privacy considerations.
- Tests.

## Agent F — Testing and QA Specialist

Create deterministic tests for connection-state observations and edge cases.

Output:

- Unit tests.
- Integration tests.
- Manual hardware validation plan.

## Agent G — Security and Privacy Specialist

Review:

- Permission boundaries.
- Device identifier handling.
- Logging.
- Data retention.
- Broadcast exposure.
- Unauthorized background access.

Output:

- Security review.
- Risk register.

## Agent H — Documentation Specialist

Create all required Phase 3 documentation.

## Agent I — Integration Orchestrator

Review all agent outputs, resolve contradictions, integrate implementation, run tests, inspect the final diff, and produce the final report.

Do not allow agents to edit the same files simultaneously without coordination.

---

# 5. CONNECTED DEVICE DETECTION ARCHITECTURE

Implement a clean separation between:

```text
Android Bluetooth Framework
          |
          v
Android Connection Observer
          |
          v
Connection Event Normalizer
          |
          v
Device Observation Repository
          |
          v
Device Observation Flow
          |
          v
Platform-Independent Domain
```

Adapt this architecture to the actual repository.

Do not introduce unnecessary modules or duplicate existing abstractions.

---

# 6. PAIRED VS CONNECTED DEVICE DISTINCTION

This distinction is mandatory.

A paired device is not necessarily connected.

A connected device is not necessarily an earbud.

An audio-profile connection is not proof that the device exposes vendor control capabilities.

The model must distinguish at least:

```text
BONDED
CONNECTED
DISCONNECTED
CONNECTING
DISCONNECTING
UNKNOWN
UNAVAILABLE
```

Use accurate state transitions and document which states Android can actually expose.

Do not fabricate a CONNECTING or DISCONNECTING event when the platform provides no evidence.

Where an intermediate state cannot be observed, represent the transition conservatively.

---

# 7. DEVICE OBSERVATION MODEL

Create or extend a platform-independent observation model.

Conceptual example:

```kotlin
data class DeviceObservation(
    val platformId: String?,
    val name: String?,
    val deviceType: DeviceType,
    val bonded: Boolean,
    val connectionState: DeviceConnectionState,
    val observedProfiles: Set<BluetoothProfileType>,
    val lastObservedAt: Instant?,
    val identityConfidence: IdentityConfidence
)
```

This is illustrative.

Adapt types and serialization to the existing Phase 1 models.

Requirements:

- Unknown values remain unknown.
- Missing device names remain null.
- Missing identifiers remain null.
- Do not substitute empty strings for unknown identity.
- Do not invent manufacturer information.
- Do not infer earbuds solely from a generic Bluetooth name.
- Do not store sensitive identifiers unnecessarily.

If the platform exposes only partial information, represent partial information honestly.

---

# 8. CONNECTION OBSERVATION

Implement supported Android observation mechanisms.

Investigate and use appropriate mechanisms for:

- Adapter connection-state changes.
- Relevant Bluetooth profile connection states.
- Bond-state changes where useful for distinguishing paired devices.
- Initial connection snapshots where permitted and supported.

Do not assume Android provides a universal broadcast for every BLE connection.

Do not assume `BluetoothProfile` APIs expose every connected device or vendor control connection.

Document limitations by Android API level.

Do not use continuous aggressive polling.

Prefer event-driven observation and bounded reconciliation where required.

---

# 9. INITIAL SNAPSHOT AND EVENT RECONCILIATION

The observation engine must handle the race between:

1. Initial state retrieval.
2. Broadcast receiver registration.
3. Incoming connection events.

Avoid losing connection transitions during initialization.

Use an appropriate synchronization or reconciliation strategy.

Requirements:

- Initial snapshot.
- Event observation.
- Snapshot reconciliation.
- Duplicate suppression.
- Stable device keys where available.
- Safe handling of missing identifiers.
- Deterministic state updates.

Do not assume broadcast ordering is perfectly reliable.

Do not emit duplicate device records for the same observed device merely because multiple profiles report its connection.

Preserve profile-specific observations where needed.

---

# 10. DEVICE COLLECTION SEMANTICS

The system must distinguish three conceptual collections:

## A. Currently observed devices

Devices currently reported as connected through supported Android observation mechanisms.

## B. Paired devices

Devices bonded with Android.

These may be disconnected.

Do not present them as active connections.

## C. Saved user devices

This is a future persistent user collection.

Do not implement the persistent saved-device feature in Phase 3.

Do not permanently retain every discovered or connected device.

Do not create a permanent database of the user's Bluetooth history.

---

# 11. DISCONNECTION BEHAVIOR

When a device disconnects:

- Update its observed connection state.
- Remove it from the active-connected projection.
- Preserve a transition event long enough for downstream observers to process it.
- Do not permanently save it.
- Do not automatically delete a future user-saved profile.
- Handle repeated disconnection events idempotently.

If the Bluetooth adapter turns off:

- Mark current connection observations unavailable or disconnected according to verified platform semantics.
- Clear stale active-connected projections.
- Do not claim that physical device power was turned off unless known.

---

# 12. MULTIPLE DEVICE SUPPORT

The observation engine must support multiple connected Bluetooth devices.

Do not assume only one headset can be connected.

Handle:

- Multiple devices.
- Multiple profiles per device.
- Duplicate observations.
- Device names that are identical.
- Missing names.
- Missing addresses.
- Profile changes.
- Disconnect/reconnect cycles.

Do not merge two devices solely because they share the same display name.

Use the strongest legitimate platform identity available.

---

# 13. PERMISSION AND SECURITY REQUIREMENTS

Respect the Phase 2 permission resolver.

Do not bypass Android runtime permissions.

Handle:

- Permission granted.
- Permission denied.
- Permission revoked.
- Bluetooth disabled.
- Bluetooth unavailable.
- Unsupported API.
- Platform exception.

Do not automatically trigger permission dialogs from background observers.

Do not request location permission without a documented platform requirement.

Do not expose Bluetooth addresses in ordinary application logs.

Use redacted diagnostics.

Do not collect unrelated nearby-device information.

Do not add unnecessary background services.

---

# 14. REACTIVE STATE API

Expose the observed state through a testable repository or equivalent abstraction.

Conceptual example:

```kotlin
interface ConnectedDeviceRepository {

    val connectedDevices: Flow<List<DeviceObservation>>

    val observationState: StateFlow<ObservationState>

    suspend fun refresh()
}
```

Adapt this to the actual Phase 1 architecture.

The API must distinguish:

- Observation not started.
- Observing.
- Permission required.
- Bluetooth disabled.
- Unsupported.
- Failed.
- Stopped.

Do not represent failure as an empty device list.

An empty list means the observation completed successfully and no devices are currently reported.

An observation failure must remain distinguishable.

---

# 15. ERROR HANDLING

Define structured errors for:

- Permission denied.
- Adapter disabled.
- Bluetooth unavailable.
- Unsupported platform operation.
- Receiver registration failure.
- Observation failure.
- Device information unavailable.
- Operation cancelled.
- Unknown platform exception.

Do not crash the application because a device disconnects during observation.

Do not swallow exceptions silently.

Do not expose stack traces or sensitive identifiers to normal user-facing state.

---

# 16. EXPLICITLY FORBIDDEN IN PHASE 3

Do not implement:

- Device fingerprinting.
- Manufacturer identification.
- Vendor protocol selection.
- GATT service discovery.
- GATT characteristic discovery.
- RFCOMM control sessions.
- Battery retrieval.
- ANC controls.
- Transparency controls.
- EQ controls.
- Gesture configuration.
- Firmware information retrieval.
- Codec negotiation or switching.
- Audio stream interception.
- Persistent saved-device database.
- Quick Settings controls.
- Notification controls.
- Production UI.
- Firmware updates.
- Automatic pairing.
- Automatic connection attempts.

These belong to later phases.

The observation engine must not claim that a connected device is fully supported.

---

# 17. AUDIO PATH ISOLATION

Android remains responsible for normal Bluetooth audio transport.

Phase 3 must not:

- Modify A2DP audio streams.
- Intercept media audio.
- Decode or re-encode audio.
- Force audio profiles.
- Change codecs.
- Alter audio routing.
- Disconnect or reconnect devices merely to obtain more information.

Observe system state only.

---

# 18. REQUIRED DOCUMENTATION

Create:

```text
docs/phases/phase-3/
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
OB-P3-REQ-001
OB-P3-REQ-002
```

Document actual Android platform limitations rather than promising universal connection visibility.

---

# 19. TESTING REQUIREMENTS

## Unit tests

Test:

- Connected device observation.
- Bonded versus connected distinction.
- Multiple devices.
- Multiple profiles.
- Duplicate suppression.
- Missing device names.
- Missing identifiers.
- Unknown state.
- Disconnection.
- Reconnection.
- Adapter disablement.
- Permission denial.
- Permission revocation.
- Cancellation.
- Platform errors.
- Snapshot/event reconciliation.

## Architecture tests

Verify:

- Core remains Android-independent.
- Android framework types do not leak into domain models.
- Device observation does not depend on vendor protocols.
- No production fake hardware capability exists.
- No unauthorized UI module was introduced.

## Manual Android validation

Where a physical device is available:

1. Pair earbuds using Android settings.
2. Connect the earbuds normally.
3. Open or refresh OmniBuds observation.
4. Verify the connected device is detected if the platform exposes it.
5. Disconnect the earbuds.
6. Verify the active observation updates.
7. Reconnect.
8. Verify no duplicate device records.
9. Disable Bluetooth.
10. Verify stale connected-device state is not presented as active.

Record the phone model, Android version, and actual tested profile.

Do not claim universal device compatibility based on one handset.

---

# 20. ACCEPTANCE CRITERIA

Phase 3 is complete only when:

- [ ] Phase 0, 1, and 2 contracts are respected.
- [ ] Connected and bonded states are distinct.
- [ ] Android connection observation exists.
- [ ] Initial snapshot and event reconciliation are implemented.
- [ ] Multiple devices are supported.
- [ ] Multiple profiles are handled without incorrect duplication.
- [ ] Disconnection updates the active projection.
- [ ] Adapter disablement is handled.
- [ ] Permission failures are represented accurately.
- [ ] Device identity remains conservative.
- [ ] No unsupported manufacturer inference exists.
- [ ] No permanent device history is created.
- [ ] No vendor control protocol is implemented.
- [ ] No normal audio path is modified.
- [ ] Unit tests pass or failures are documented.
- [ ] Architecture tests pass.
- [ ] Mandatory Phase 3 documentation exists.
- [ ] Physical-device results are honestly recorded.
- [ ] Git diff contains only authorized changes.
- [ ] Final validation report is complete.

---

# 21. STOP CONDITION

After Phase 3:

1. Stop implementation.
2. Do not start Phase 4.
3. Do not implement device session management beyond the observation contracts required here.
4. Do not begin device fingerprinting.
5. Do not implement vendor protocols.
6. Do not create production UI.
7. Do not silently expand the authorized scope.

Produce a final report containing:

- Phase 3 completion status.
- Implemented modules.
- Files changed.
- Android APIs used.
- Platform limitations.
- Tests executed.
- Build and static-analysis results.
- Physical-device verification results.
- Known issues.
- Outstanding risks.
- Git commit information.
- Confirmation of readiness for Phase 4.

Wait for explicit user authorization before proceeding.

---

# FINAL ENGINEERING PRINCIPLE

Detect what Android actually exposes.

Distinguish what is known from what is inferred.

Never mistake a paired device for a connected device.

Never mistake a connected device for a supported earbud.

Never simulate hardware capabilities.

Implement Phase 3 only.