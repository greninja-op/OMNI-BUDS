# OMNIBUDS — PHASE 0 EXECUTION PROMPT
# ENGINEERING CONTRACT, REPOSITORY RULES & DEVELOPMENT GOVERNANCE

## EXECUTION MODE

This prompt explicitly authorizes execution of **PHASE 0 ONLY**.

Do NOT execute Phase 1 or any later phase automatically.

At the end of Phase 0, STOP and provide the phase completion report.

---

# 1. PHASE OBJECTIVE

The objective of Phase 0 is to establish the permanent engineering foundation and governance rules for OmniBuds.

This phase must establish:

- project engineering principles
- repository organization
- documentation architecture
- requirements management
- architecture decision recording
- task management conventions
- testing conventions
- validation conventions
- sub-agent orchestration rules
- code ownership boundaries
- naming conventions
- state-management rules
- Bluetooth safety rules
- protocol safety rules
- audio-path rules
- hardware verification rules
- persistence-verification rules
- unknown-device safety rules
- Git workflow
- branch/worktree strategy where applicable
- definition of done
- phase completion rules
- regression rules
- future KMP compatibility rules

This phase must make the project ready for the actual engineering work that begins in later phases.

---

# 2. CRITICAL SCOPE RESTRICTION

Phase 0 is a governance and project-foundation phase.

DO NOT implement:

- Bluetooth scanning
- Bluetooth connection
- BLE/GATT communication
- RFCOMM
- A2DP
- AVRCP
- HFP
- LE Audio
- codec configuration
- ANC
- transparency
- EQ
- gestures
- battery communication
- vendor protocols
- protocol reverse engineering
- Quick Settings
- notifications
- widgets
- UI
- database behavior
- background Bluetooth services
- device control
- audio processing

Do not create fake placeholder implementations that pretend these systems already work.

If scaffolding is required for future work, create only the minimum documentation/interface placeholders necessary to establish architecture.

---

# 3. FIRST ACTION — REPOSITORY INSPECTION

Before changing anything:

## 3.1 Inspect the repository

Determine:

- current repository root
- existing source files
- existing documentation
- build files
- Gradle configuration
- Kotlin version
- Android Gradle Plugin version
- existing modules
- existing branches
- existing tests
- existing CI
- existing README
- existing `.gitignore`
- existing editor/IDE configuration
- existing agent configuration
- existing project instructions
- existing architecture decisions

Do NOT assume the repository is empty.

## 3.2 Existing work must be preserved

If existing code is found:

- do not delete it
- do not rewrite it
- do not replace it with a new architecture
- document what already exists
- identify conflicts with the OmniBuds master architecture
- preserve existing behavior unless explicitly authorized

If existing implementation contradicts the master architecture, record the conflict in:

```text
docs/decisions/
```

Do not silently resolve major conflicts.

---

# 4. SUB-AGENT ORCHESTRATION

If your environment supports sub-agents, use them.

Do NOT launch every agent simultaneously if they would modify the same files.

The orchestrator must divide Phase 0 into independent workstreams.

Recommended agents:

---

## AGENT 1 — REPOSITORY AUDITOR

Responsibility:

Inspect the repository and report:

- current structure
- build system
- modules
- source code
- tests
- documentation
- CI
- existing conventions
- existing architecture
- potential conflicts

Output:

```text
docs/phase-0/repository-audit.md
```

This agent should preferably be read-only.

---

## AGENT 2 — ARCHITECTURE GOVERNANCE AGENT

Responsibility:

Translate the OmniBuds master architecture into engineering rules.

Focus on:

- module boundaries
- dependency direction
- core/domain separation
- Android-specific boundaries
- future KMP boundaries
- transport abstraction
- protocol abstraction
- capability abstraction
- state management
- persistence boundaries

Output:

```text
docs/phase-0/architecture-governance.md
```

---

## AGENT 3 — TESTING & QA AGENT

Responsibility:

Define:

- unit-testing standards
- integration-testing standards
- protocol-testing standards
- hardware-in-the-loop standards
- persistence verification
- reconnect testing
- cross-device testing
- cross-phone testing
- regression testing
- phase acceptance criteria

Output:

```text
docs/phase-0/testing-governance.md
```

---

## AGENT 4 — SECURITY & SAFETY AGENT

Responsibility:

Define rules for:

- Bluetooth permissions
- device identifiers
- vendor protocol handling
- unknown devices
- arbitrary writes
- firmware updates
- packet logging
- sensitive information
- debug logging
- protocol research
- user privacy
- background operation

Output:

```text
docs/phase-0/security-governance.md
```

---

## AGENT 5 — AUDIO ARCHITECTURE AGENT

Responsibility:

Document the architectural rules for:

- Android native audio path
- A2DP
- LE Audio
- AAC
- SBC
- aptX family
- LDAC
- LC3
- codec capability vs active codec
- sample rate
- bit depth
- bitrate
- audio transport
- hardware DSP
- phone-side processing

This is documentation only.

Do not implement the audio subsystem in Phase 0.

Output:

```text
docs/phase-0/audio-governance.md
```

---

## AGENT 6 — DOCUMENTATION AGENT

Responsibility:

Create the standard documentation templates that future phases will use.

Output:

```text
docs/templates/
```

including:

```text
requirements-template.md
design-template.md
specs-template.md
task-list-template.md
test-plan-template.md
validation-template.md
decisions-template.md
risk-register-template.md
```

---

## AGENT 7 — GIT / WORKFLOW AGENT

Responsibility:

Define:

- branch naming
- commit conventions
- pull-request expectations
- change isolation
- sub-agent change handling
- conflict resolution
- rollback strategy
- documentation update requirements

Output:

```text
docs/phase-0/git-workflow.md
```

---

# 5. ORCHESTRATOR RESPONSIBILITY

After sub-agents finish:

1. Review all outputs.
2. Detect contradictions.
3. Resolve obvious documentation conflicts.
4. Do NOT silently change major architectural decisions.
5. Record unresolved architectural decisions.
6. Merge compatible documentation.
7. Ensure terminology is consistent.
8. Ensure all requirements from the master context are represented.
9. Perform a final repository review.
10. Run only safe Phase 0 validation.

The orchestrator must not simply trust sub-agent outputs.

---

# 6. REQUIRED DIRECTORY STRUCTURE

Create the project documentation architecture.

Target:

```text
docs/
│
├── architecture/
│
├── requirements/
│
├── protocols/
│
├── bluetooth/
│
├── audio/
│
├── testing/
│
├── security/
│
├── decisions/
│
├── templates/
│
└── phases/
    │
    └── phase-0/
        ├── requirements.md
        ├── design.md
        ├── specs.md
        ├── task-list.md
        ├── test-plan.md
        ├── validation.md
        ├── decisions.md
        └── risk-register.md
```

If the repository already has a compatible documentation structure, adapt it rather than duplicating it.

---

# 7. PHASE 0 REQUIREMENTS.MD

Create:

```text
docs/phases/phase-0/requirements.md
```

It must contain real requirements.

Each requirement must have:

```text
REQ-P0-XXX
Title
Description
Rationale
Dependencies
Acceptance Criteria
Verification Method
Priority
```

At minimum, cover the following.

---

## REQ-P0-001 — Master Architecture

The project SHALL maintain a documented master architecture.

Acceptance:

- architecture document exists
- module boundaries are documented
- dependency direction is documented
- future KMP compatibility is documented

---

## REQ-P0-002 — Hardware Truth

OmniBuds SHALL never represent an unsupported physical device capability as supported.

Acceptance:

- capability truth is documented
- UNKNOWN is distinct from UNSUPPORTED
- unsupported controls are not to be exposed

---

## REQ-P0-003 — Audio Path Isolation

OmniBuds SHALL normally remain outside the media audio path.

Acceptance:

- audio architecture documents this
- future implementation must not unnecessarily decode/re-encode audio

---

## REQ-P0-004 — Device Session Separation

Temporary connected devices SHALL be distinct from explicitly saved devices.

Acceptance:

- active-session concept documented
- saved-device concept documented
- disconnect behavior documented

---

## REQ-P0-005 — Transport Abstraction

Bluetooth control SHALL NOT be tied exclusively to GATT.

Acceptance:

Architecture documents support for future:

- GATT
- RFCOMM
- Classic Bluetooth
- LE Audio
- other legitimate transports

---

## REQ-P0-006 — Capability Model

Capabilities SHALL support richer states than boolean supported/unsupported.

At minimum:

```text
UNKNOWN
UNSUPPORTED
READ_ONLY
SUPPORTED_VOLATILE
SUPPORTED_PERSISTENT
PERSISTENCE_VERIFIED
```

---

## REQ-P0-007 — Persistent Setting Verification

A setting SHALL NOT be classified as device-persistent solely because a write succeeds.

Required conceptual verification:

```text
WRITE
READ BACK
DISCONNECT
RECONNECT
READ AGAIN
VERIFY
```

---

## REQ-P0-008 — Unknown Device Safety

Unknown devices SHALL begin in read-only mode.

No undocumented write should occur automatically.

---

## REQ-P0-009 — Codec Truth

The system SHALL distinguish:

```text
SUPPORTED
AVAILABLE
ENABLED
NEGOTIATED
ACTIVE
CONFIGURABLE
```

---

## REQ-P0-010 — Audio Codec Coverage

The architecture SHALL account for:

- SBC
- AAC
- aptX
- aptX HD
- aptX Adaptive
- aptX Lossless where exposed
- LDAC
- LC3
- future codecs

---

## REQ-P0-011 — Audio Quality State

The architecture SHALL allow representation of:

- codec
- sample rate
- bit depth
- bitrate
- channel mode
- quality mode
- transport
- active state

Unknown values must remain unknown.

---

## REQ-P0-012 — Vendor Extensions

Vendor-specific functionality SHALL be preserved instead of being discarded in favor of a lowest-common-denominator API.

---

## REQ-P0-013 — Documentation Per Phase

Every implementation phase SHALL contain:

```text
requirements.md
design.md
specs.md
task-list.md
test-plan.md
validation.md
decisions.md
```

---

## REQ-P0-014 — Sub-Agent Governance

Sub-agents SHALL have isolated responsibilities and explicit deliverables.

---

## REQ-P0-015 — Phase Boundaries

The editor SHALL NOT automatically advance from one phase to another.

---

# 8. DESIGN.MD

Create:

```text
docs/phases/phase-0/design.md
```

This document must explain how the project will be governed.

Include:

## 8.1 Architecture hierarchy

```text
Product
↓
Architecture
↓
Core abstractions
↓
Platform implementations
↓
Vendor protocols
↓
Device implementations
```

---

## 8.2 Dependency direction

Document which layers may depend on which.

The preferred conceptual direction:

```text
UI
 ↓
Application / State
 ↓
Core Domain
 ↓
Protocol / Capability abstractions
 ↓
Platform transport implementations
```

Vendor protocol code must not become the owner of global application state.

---

## 8.3 Android isolation

Android-specific APIs should remain behind platform boundaries whenever practical.

Examples:

- Bluetooth APIs
- Quick Settings
- Android notifications
- Android lifecycle
- Android permissions
- Android audio APIs

The domain model should not become tightly coupled to Android classes.

---

## 8.4 Future KMP compatibility

The core should eventually be portable.

Potential shared areas:

```text
DeviceIdentity
DeviceFingerprint
Capability
DeviceState
AudioState
Protocol definitions
Packet parsing
State machines
Persistence rules
Validation logic
Diagnostics models
```

Platform-specific:

```text
Bluetooth transport
Android permissions
Quick Settings
Android notification
Windows Bluetooth
macOS Bluetooth
Linux Bluetooth
Desktop system integration
```

---

# 9. SPECS.MD

Create:

```text
docs/phases/phase-0/specs.md
```

It must define project-wide specifications.

Include:

## 9.1 Naming conventions

Define:

- Kotlin class naming
- interface naming
- enum naming
- file naming
- package naming
- test naming
- protocol naming
- capability naming

---

## 9.2 State conventions

Define how states should be represented.

Do not use:

```text
null
```

as a substitute for every possible state.

Where appropriate, explicitly represent:

```text
UNKNOWN
UNAVAILABLE
DISCONNECTED
SUPPORTED
UNSUPPORTED
```

---

## 9.3 Error conventions

Define structured errors.

Examples:

```text
BluetoothDisabled
PermissionDenied
DeviceDisconnected
TransportUnavailable
ProtocolMismatch
UnsupportedFeature
WriteRejected
VerificationFailed
Timeout
CodecUnavailable
```

---

## 9.4 Coroutine conventions

Define rules for:

- structured concurrency
- cancellation
- timeout
- retry
- Flow
- StateFlow
- shared state
- lifecycle-bound work

---

## 9.5 Retry conventions

Read operations may have controlled retries.

Side-effecting writes must NOT automatically retry blindly.

Example:

```text
SET ANC
timeout
↓
DO NOT blindly send SET ANC again
↓
READ CURRENT ANC
↓
determine actual state
```

---

# 10. TASK-LIST.MD

Create:

```text
docs/phases/phase-0/task-list.md
```

Tasks must be atomic.

Include tasks similar to:

```text
P0-T001
Inspect repository.

P0-T002
Document existing architecture.

P0-T003
Create phase documentation structure.

P0-T004
Create documentation templates.

P0-T005
Document capability truth model.

P0-T006
Document device session lifecycle.

P0-T007
Document audio-path isolation.

P0-T008
Document codec-state semantics.

P0-T009
Document persistence verification.

P0-T010
Document unknown-device safety.

P0-T011
Document sub-agent orchestration.

P0-T012
Document Git workflow.

P0-T013
Create Phase 0 risk register.

P0-T014
Perform consistency review.

P0-T015
Perform Phase 0 validation.
```

Each task needs:

- ID
- description
- dependency
- owner
- expected files
- acceptance criteria
- test/verification
- status

---

# 11. TEST-PLAN.MD

Create:

```text
docs/phases/phase-0/test-plan.md
```

Phase 0 tests are primarily documentation/governance validation.

Examples:

### P0-TEST-001

Verify all mandatory project principles exist in documentation.

### P0-TEST-002

Verify every future phase has the required documentation structure.

### P0-TEST-003

Verify codec architecture distinguishes supported vs active.

### P0-TEST-004

Verify persistence verification procedure exists.

### P0-TEST-005

Verify unknown-device read-only policy exists.

### P0-TEST-006

Verify audio-path isolation is documented.

### P0-TEST-007

Verify vendor-specific feature architecture is documented.

### P0-TEST-008

Verify sub-agent orchestration rules exist.

---

# 12. VALIDATION.MD

Create:

```text
docs/phases/phase-0/validation.md
```

The document must contain:

```text
Phase:
Status:

Requirements completed:
Requirements incomplete:

Documentation created:

Architecture decisions:

Validation performed:

Issues:

Known limitations:

Deferred work:

Ready for Phase 1:
YES / NO
```

---

# 13. DECISIONS.MD

Create:

```text
docs/phases/phase-0/decisions.md
```

Record important decisions.

At minimum:

### ADR-P0-001
OmniBuds does not simulate hardware.

### ADR-P0-002
OmniBuds stays outside the normal media audio path.

### ADR-P0-003
Transport abstraction is required.

### ADR-P0-004
Capabilities are richer than boolean values.

### ADR-P0-005
Persistent writes require reconnect verification.

### ADR-P0-006
Unknown devices start read-only.

### ADR-P0-007
Vendor-specific capabilities remain accessible.

### ADR-P0-008
Android is first; KMP compatibility is an architectural target.

### ADR-P0-009
Phases do not automatically advance.

### ADR-P0-010
Sub-agents are orchestrated through explicit ownership boundaries.

---

# 14. RISK REGISTER

Create:

```text
docs/phases/phase-0/risk-register.md
```

At minimum identify:

```text
RISK-001
Android OEM Bluetooth differences

RISK-002
Vendor protocol changes

RISK-003
Unknown proprietary protocols

RISK-004
Authentication/encryption

RISK-005
Codec control restrictions

RISK-006
LE Audio platform differences

RISK-007
Background execution restrictions

RISK-008
Firmware incompatibility

RISK-009
Persistence assumptions

RISK-010
Protocol write safety

RISK-011
Cross-device behavior differences

RISK-012
Cross-phone behavior differences

RISK-013
Apple/AirPods proprietary restrictions

RISK-014
Future desktop Bluetooth API differences
```

Each risk should contain:

- probability
- impact
- mitigation
- detection strategy
- fallback
- owner

---

# 15. PROTOCOL RESEARCH RULES

Create documentation stating:

When investigating an unknown device:

```text
1. Identify
2. Discover
3. Read
4. Observe
5. Understand
6. Validate
7. Write
8. Read back
9. Disconnect
10. Reconnect
11. Verify
```

Never begin with arbitrary writes.

Never assume a UUID's purpose without evidence.

Never assume a packet byte's meaning without evidence.

---

# 16. HARDWARE VERIFICATION RULE

Create a formal definition:

A capability is:

### INFERRED

Based on documentation or protocol evidence.

### IMPLEMENTED

Code exists.

### LAB-TESTED

Tested against a protocol simulation/mock.

### HARDWARE-VERIFIED

Tested against actual physical hardware.

### PERSISTENCE-VERIFIED

Hardware behavior survives disconnect/reconnect.

These statuses must never be conflated.

---

# 17. AUDIO VERIFICATION RULE

Similarly define:

```text
Codec Supported
Codec Available
Codec Selected
Codec Negotiated
Codec Active
```

The UI must not claim:

```text
LDAC Active
```

unless active state is actually verified.

---

# 18. CODE QUALITY RULES

Establish project rules:

- no giant manager classes
- no magic protocol values scattered through code
- no vendor logic in UI
- no UI business logic
- no duplicated transport implementations
- no hidden mutable global state
- no blocking Bluetooth operations on the main thread
- no arbitrary writes
- no fake capabilities
- no fabricated metadata
- no silent architecture changes

---

# 19. GIT RULES

Document:

### Branches

Use a consistent naming scheme.

Examples:

```text
main
develop
phase/0-foundation
feature/bluetooth-foundation
feature/device-fingerprint
feature/audio-codec-engine
fix/gatt-timeout
```

Adapt to the existing repository if one already exists.

### Commits

Use meaningful commits.

Example:

```text
feat(core): add capability model
docs(phase-0): define hardware verification rules
test(core): add capability state tests
```

Do not create meaningless commits such as:

```text
update
fix
changes
stuff
```

---

# 20. SUB-AGENT CODE OWNERSHIP

When implementation begins, sub-agents should be assigned ownership.

Example:

```text
Bluetooth Agent
→ bluetooth/

Audio Agent
→ audio/

Protocol Agent
→ protocols/

Core Agent
→ core/

Testing Agent
→ testing/

Documentation Agent
→ docs/
```

If two agents need the same file:

Do NOT allow concurrent uncontrolled edits.

Instead:

```text
Agent A
↓
proposal
↓
Orchestrator
↓
integration
```

---

# 21. ORCHESTRATOR REVIEW CHECKLIST

Before declaring Phase 0 complete, verify:

```text
[ ] Repository inspected
[ ] Existing architecture documented
[ ] No unrelated code changed
[ ] Documentation structure created
[ ] Requirements template created
[ ] Design template created
[ ] Specs template created
[ ] Task template created
[ ] Test template created
[ ] Validation template created
[ ] Decision template created
[ ] Risk register created
[ ] Audio governance documented
[ ] Bluetooth governance documented
[ ] Protocol safety documented
[ ] Hardware verification documented
[ ] Persistence verification documented
[ ] Codec semantics documented
[ ] Unknown device behavior documented
[ ] KMP boundary documented
[ ] Sub-agent orchestration documented
[ ] Git workflow documented
[ ] Phase boundary rules documented
[ ] No implementation accidentally started
```

---

# 22. PHASE 0 ACCEPTANCE CRITERIA

Phase 0 is complete only when:

### Architecture

- master architecture is documented
- module boundaries are clear
- KMP strategy is clear
- Android-specific boundaries are clear

### Hardware

- hardware-truth principle documented
- capability states documented
- persistence verification documented
- unknown-device safety documented

### Audio

- audio-path isolation documented
- codec architecture documented
- AAC documented
- LDAC documented
- aptX family documented
- LC3/LE Audio documented
- active vs supported codec distinction documented

### Protocol

- transport abstraction documented
- protocol abstraction documented
- vendor extension strategy documented
- protocol safety documented

### Process

- phase structure documented
- sub-agent workflow documented
- testing workflow documented
- Git workflow documented
- definition of done documented

### Documentation

All required Phase 0 files exist and are internally consistent.

---

# 23. WHAT COUNTS AS FAILURE

Phase 0 fails if:

- implementation begins accidentally
- Bluetooth APIs are implemented
- vendor protocol commands are implemented
- fake hardware support is created
- documentation contradicts the master architecture without an ADR
- requirements are vague
- acceptance criteria are missing
- testing rules are missing
- sub-agent responsibilities are unclear
- audio architecture is omitted
- codec architecture is omitted
- persistence verification is omitted
- unknown-device safety is omitted

---

# 24. FINAL PHASE 0 REPORT

When complete, report:

```text
OMNIBUDS — PHASE 0 COMPLETE

Repository:
...

Documentation created:
...

Architecture decisions:
...

Requirements:
X / Y complete

Tasks:
X / Y complete

Validation:
PASS / FAIL

Known issues:
...

Deferred:
...

Phase 1 readiness:
READY / NOT READY
```

Do NOT start Phase 1.

---

# 25. FINAL STOP CONDITION

After Phase 0 is complete:

**STOP.**

Do not:

- start Bluetooth
- start device detection
- start GATT
- start RFCOMM
- start codec implementation
- start UI
- start vendor protocol work
- start Phase 1

Wait for the user's explicit:

> Execute Phase 1

instruction.

# END OF PHASE 0