# OMNIBUDS — PHASE 5
## Device Fingerprinting & Identification
### Complete Engineering Execution Prompt

You are the principal engineering orchestrator responsible for implementing Phase 5 of OmniBuds.

Your objective is to establish a reliable, extensible, privacy-conscious device identity and fingerprinting engine using the architecture established in Phases 0–4.

Inspect the actual repository, read all previous phase documentation, delegate independent work to specialized agents where supported, implement the authorized scope, run automated validation, update documentation, and stop at the Phase 5 boundary.

Do not restart the project.

Do not assume the repository exactly matches the planned architecture.

Do not invent device identity information or claim unsupported identification accuracy.

---

# 1. PRODUCT CONTEXT

OmniBuds is a universal Bluetooth earbuds and headphones hardware-control application.

Its core principle is:

"OmniBuds never simulates a hardware capability. It discovers, verifies, and controls capabilities implemented by the connected device."

OmniBuds must eventually support devices from multiple manufacturers, including:

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

The application must preserve vendor-specific features rather than reducing every device to a generic lowest-common-denominator model.

However, Phase 5 is limited to device identity and fingerprinting.

It does not implement proprietary control protocols or hardware capabilities.

---

# 2. MANDATORY PRE-EXECUTION AUDIT

Before changing files:

1. Inspect the complete repository.
2. Read Phase 0 engineering governance.
3. Read Phase 1 architecture and domain models.
4. Read Phase 2 Android Bluetooth foundation.
5. Read Phase 3 connected-device detection.
6. Read Phase 4 session-management documentation.
7. Inspect existing DeviceIdentity models.
8. Inspect DeviceFingerprint models, if present.
9. Inspect the connected-device observation repository.
10. Inspect the session repository.
11. Inspect persistence abstractions.
12. Inspect dependency injection.
13. Inspect test infrastructure.
14. Inspect existing vendor or manufacturer definitions.
15. Inspect Git status and current branch.

Identify existing reusable functionality.

Do not create duplicate identity models or competing device registries.

If an earlier phase is incomplete, identify whether it blocks Phase 5.

Preserve unrelated changes.

---

# 3. PHASE 5 OBJECTIVE

Implement a device identity and fingerprinting engine capable of:

- Collecting available device identity signals.
- Normalizing identity information.
- Separating observed facts from inferred information.
- Identifying manufacturers when evidence is sufficient.
- Matching known device models using explicit rules.
- Representing confidence and ambiguity.
- Handling unknown devices.
- Handling incomplete metadata.
- Supporting multiple devices with similar names.
- Producing stable fingerprint results.
- Keeping user device records separate from the global identification knowledge base.
- Supporting future protocol selection without implementing it now.

The engine must never claim exact model identification based only on a display name unless the identification policy explicitly permits that limited confidence.

---

# 4. SUB-AGENT ORCHESTRATION

Use specialized sub-agents where available.

## Agent A — Repository and Architecture Auditor

Inspect previous phase models and determine how identity should integrate with sessions.

Output:

- Architecture audit.
- Existing model reuse.
- Integration plan.

## Agent B — Bluetooth Identity Research Specialist

Investigate identity information available through Android Bluetooth APIs.

Evaluate:

- Bluetooth device name.
- Bluetooth address, where accessible.
- Device type.
- Bond state.
- Manufacturer-specific advertising information, where legitimately available.
- Service UUIDs, where available through authorized observation.
- Bluetooth class information.
- Profile information.
- Platform limitations.

Do not assume every signal is available for every device.

Output:

- Identity signal matrix.
- Availability and reliability guidance.
- Privacy considerations.

## Agent C — Fingerprint Model Specialist

Design:

- DeviceFingerprint.
- IdentitySignal.
- ManufacturerIdentity.
- ModelIdentity.
- IdentificationConfidence.
- IdentityEvidence.
- MatchResult.
- UnknownDeviceIdentity.

Output:

- Domain models.
- Serialization contracts.
- Validation rules.

## Agent D — Matching Engine Specialist

Implement deterministic matching rules.

Requirements:

- Exact and normalized matching.
- Explicit rule precedence.
- Ambiguity handling.
- Confidence calculation based on documented evidence.
- Unknown fallback.
- Versioned rules.

Do not use arbitrary confidence percentages without defined meaning.

Output:

- Matching engine.
- Unit tests.
- Rule evaluation design.

## Agent E — Manufacturer and Model Registry Specialist

Design an extensible global identification registry.

Keep identity data separate from:

- User-saved devices.
- Active sessions.
- Vendor protocol implementations.
- Capability definitions.

Output:

- Registry architecture.
- Initial schema.
- Extensibility plan.

## Agent F — Privacy and Security Specialist

Review:

- Bluetooth address handling.
- Device identifier exposure.
- Logging.
- Persistence.
- Data minimization.
- Untrusted device metadata.

Output:

- Privacy review.
- Security findings.

## Agent G — Testing and QA Specialist

Create deterministic tests for matching, ambiguity, missing information, and identity stability.

Output:

- Test plan.
- Unit tests.
- Architecture tests.

## Agent H — Documentation Specialist

Create all mandatory Phase 5 documents.

## Agent I — Integration Orchestrator

Integrate implementation, resolve contradictions, run tests, inspect the final diff, and produce the final report.

Do not permit agents to independently authorize Phase 6.

Avoid conflicting concurrent edits.

---

# 5. DEVICE IDENTITY ARCHITECTURE

Implement a clean identity pipeline.

Conceptual design:

```text id="7hs8em"
Connected Device Session
          |
          v
Identity Signal Collector
          |
          v
Signal Normalizer
          |
          v
Fingerprint Builder
          |
          v
Identification Matcher
          |
          v
Identity Evidence Evaluator
          |
          v
Identification Result
          |
     +----+----+
     |         |
     v         v
Known Device  Unknown Device
     |
     v
Future Protocol Resolution
```

The final protocol-resolution step is future functionality and must not be implemented in Phase 5.

Adapt this design to the existing architecture.

---

# 6. IDENTITY SIGNAL MODEL

Represent available signals explicitly.

Potential signal categories:

- Platform device identifier.
- Bluetooth address, where available.
- Advertised or reported name.
- Normalized device name.
- Bluetooth device type.
- Bond state.
- Observed Bluetooth profiles.
- Manufacturer-specific data, where available.
- Service UUIDs, where available.
- Bluetooth class.
- Platform-reported metadata.
- Signal observation timestamp.
- Signal source.
- Signal reliability.

Each signal must distinguish:

- Observed.
- Derived.
- Inferred.
- Unknown.
- Unavailable.
- Invalid.

Do not represent missing information as a fabricated default.

Do not assume an absent signal means the device does not support a feature.

---

# 7. DEVICE FINGERPRINT MODEL

Create a platform-independent fingerprint model.

Conceptual example:

```kotlin id="m2kq4p"
data class DeviceFingerprint(
    val fingerprintVersion: Int,
    val normalizedName: String?,
    val deviceType: DeviceType,
    val manufacturerData: ByteArray?,
    val serviceIdentifiers: Set<String>,
    val observedProfiles: Set<BluetoothProfileType>,
    val evidence: List<IdentityEvidence>
)
```

Adapt to the actual project conventions.

Requirements:

- Fingerprints must be deterministic for equivalent inputs.
- Equality semantics for byte arrays must be correct.
- Fingerprint versioning must be explicit.
- Unknown fields must remain unknown.
- Fingerprints must not include unnecessary personal data.
- Normalization must not destroy meaningful identity distinctions.

Do not use a raw Bluetooth address as the sole model fingerprint.

Do not assume device addresses are permanently stable.

---

# 8. IDENTIFICATION RESULT

Create an explicit identification result model.

Conceptual outcomes:

```text id="q5b9cv"
EXACT_MATCH
LIKELY_MATCH
MANUFACTURER_ONLY
UNKNOWN_DEVICE
AMBIGUOUS_MATCH
INSUFFICIENT_EVIDENCE
INVALID_EVIDENCE
```

Adapt the final set to the architecture.

Every result must contain:

- Identification status.
- Matched manufacturer, if supported by evidence.
- Matched model, if supported by evidence.
- Evidence references.
- Confidence classification.
- Matching rule version.
- Known limitations.

Do not assign a model when several candidates remain indistinguishable.

Do not convert an ambiguous result into an arbitrary winner.

---

# 9. IDENTIFICATION CONFIDENCE

Use documented confidence categories.

Suggested categories:

```text id="zz15jw"
VERIFIED
HIGH
MODERATE
LOW
UNKNOWN
```

These categories must have defined evidence requirements.

Examples:

- A user-editable Bluetooth name alone should not establish verified model identity.
- Multiple independent, reliable identity signals may support stronger identification.
- A known manufacturer identifier may establish manufacturer identity without proving a specific product model.
- Conflicting signals must reduce confidence or produce ambiguity.
- Missing evidence must not be treated as negative evidence.

Do not use arbitrary numeric scores without a calibrated, documented model.

If numeric scoring is necessary, document its exact meaning and limitations.

---

# 10. MANUFACTURER NORMALIZATION

Implement a structured manufacturer identity model.

Potential fields:

- Canonical manufacturer ID.
- Display name.
- Known aliases.
- Identity evidence.
- Registry version.

Examples of normalization:

```text id="5d9p1b"
Canonical ID: sony
Display name: Sony
Aliases: documented Sony naming variants
```

Do not assume that a device name containing a brand name proves manufacturer identity.

Do not map generic or misleading names to a manufacturer without sufficient evidence.

Do not embed vendor protocol logic in the manufacturer registry.

---

# 11. MODEL MATCHING RULES

Implement a versioned matching system.

Rules should support:

- Exact model identifiers.
- Documented name patterns.
- Manufacturer-specific identifiers.
- Service identifier combinations.
- Multiple independent evidence signals.
- Explicit exclusions.
- Ambiguous candidate handling.

Each rule should document:

- Rule ID.
- Manufacturer.
- Candidate model.
- Input signal requirements.
- Match conditions.
- Confidence classification.
- Rule version.
- Evidence source.
- Known limitations.
- Verification status.

Do not invent real device signatures.

Do not label guessed patterns as hardware verified.

Do not add unsupported device models simply to increase registry size.

---

# 12. GLOBAL REGISTRY VS USER DEVICE DATA

Maintain strict separation between:

## Global identification registry

Contains reusable manufacturer and model matching knowledge.

It is application knowledge, not personal device history.

## User device records

Represent devices the user explicitly saves.

## Active device sessions

Represent current observed application sessions.

Do not combine these into one database table or repository.

Do not implement the complete user-saved-device persistence feature in this phase.

Do not permanently store every unidentified Bluetooth device.

---

# 13. UNKNOWN DEVICE HANDLING

Unknown devices must be first-class results.

For an unknown device:

- Preserve available identity signals.
- Return an explicit unknown or insufficient-evidence result.
- Do not assign a random manufacturer.
- Do not guess a model.
- Do not select a vendor protocol.
- Do not expose unsupported controls.
- Preserve the ability to improve identification later.

Unknown identification is not an application failure.

The result must distinguish:

- Unknown manufacturer.
- Known manufacturer, unknown model.
- Ambiguous model.
- Insufficient evidence.
- Invalid identity metadata.

---

# 14. IDENTITY STABILITY AND SESSION INTEGRATION

Integrate identification results with Phase 4 sessions.

Requirements:

- Identity enrichment must not unnecessarily recreate a session.
- Model identification must not alter Bluetooth connection state.
- Session identity and product identity must remain separate concepts.
- A changed display name must not automatically create a new physical device.
- Conflicting identity evidence must be represented.
- Matching must not merge two active devices incorrectly.

Define a safe identity-update policy.

Do not use manufacturer identification as proof of protocol compatibility.

---

# 15. PRIVACY AND SECURITY

Treat device metadata as potentially sensitive.

Requirements:

- Minimize retention of raw Bluetooth addresses.
- Avoid logging full addresses.
- Use redacted diagnostic representations.
- Do not upload identity data to external services.
- Do not introduce cloud-based identification.
- Do not persist raw advertising captures unnecessarily.
- Treat device names and manufacturer data as untrusted input.
- Validate lengths and formats.
- Prevent malformed metadata from crashing the matching engine.
- Keep identification deterministic and local.

Do not collect unrelated nearby-device information.

---

# 16. PERFORMANCE AND CONCURRENCY

Requirements:

- Matching should be deterministic.
- Matching should not block the main thread.
- Registry access should be thread-safe.
- Avoid unnecessary repeated normalization.
- Avoid unbounded caches.
- Avoid unbounded metadata retention.
- Support cancellation.
- Support registry versioning.
- Handle malformed signals safely.

Do not introduce machine-learning dependencies merely to perform deterministic identity matching.

---

# 17. EXPLICITLY FORBIDDEN IN PHASE 5

Do not implement:

- GATT service discovery.
- GATT characteristic discovery.
- RFCOMM communication.
- Vendor protocol selection.
- Vendor packet parsing.
- ANC.
- Transparency.
- EQ.
- Gesture controls.
- Battery retrieval.
- Firmware retrieval.
- Codec configuration.
- Audio processing.
- Persistent saved-device management.
- Production UI.
- Quick Settings.
- Notifications.
- Widgets.
- Firmware updates.
- Automatic pairing.
- Automatic Bluetooth connection.

Do not claim a device is fully supported simply because its manufacturer or model has been identified.

---

# 18. REQUIRED DOCUMENTATION

Create:

```text id="zj8j61"
docs/phases/phase-5/
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

```text id="r6fbnd"
OB-P5-REQ-001
OB-P5-REQ-002
```

Document:

- Identity signal definitions.
- Fingerprint format.
- Normalization rules.
- Matching rules.
- Confidence semantics.
- Registry versioning.
- Ambiguity behavior.
- Unknown device handling.
- Privacy rules.
- Identity/session separation.
- Known platform limitations.

---

# 19. TESTING REQUIREMENTS

Use JVM and automated tests as the primary validation path.

Physical-device validation remains deferred.

## Signal normalization

- Missing name.
- Empty name.
- Whitespace normalization.
- Case normalization.
- Unicode names.
- Malformed manufacturer data.
- Missing service identifiers.
- Conflicting identity signals.

## Fingerprinting

- Deterministic equivalent inputs.
- Different model signals.
- Missing metadata.
- Fingerprint version changes.
- Byte-array equality.
- Invalid input.

## Matching

- Exact match.
- Likely match.
- Manufacturer-only match.
- Unknown device.
- Ambiguous model.
- Insufficient evidence.
- Conflicting signals.
- Unsupported signature.
- Registry version changes.

## Session integration

- Identity enrichment preserves session.
- Display-name change does not automatically create a new device.
- Multiple devices remain distinct.
- Conflicting identity does not merge sessions.

## Privacy

- Sensitive identifiers are redacted.
- No external network dependency.
- No unnecessary persistent identity history.

## Architecture

- Core remains Android-independent.
- Registry remains separate from user device storage.
- Identification does not depend on vendor protocol implementations.
- No production fake hardware behavior.

Do not report physical-device identification as verified.

---

# 20. ACCEPTANCE CRITERIA

Phase 5 is complete only when:

- [ ] Previous phase contracts are respected.
- [ ] Identity signals are modeled.
- [ ] Fingerprint generation is deterministic.
- [ ] Manufacturer normalization exists.
- [ ] Model matching is rule-based and documented.
- [ ] Confidence semantics are explicit.
- [ ] Ambiguous identities remain ambiguous.
- [ ] Unknown devices are handled correctly.
- [ ] Identity enrichment does not corrupt sessions.
- [ ] Global registry and user device data remain separate.
- [ ] Sensitive identifiers are protected.
- [ ] No unsupported device signatures are invented.
- [ ] No vendor protocol is implemented.
- [ ] No production UI is introduced.
- [ ] Automated tests pass or failures are documented.
- [ ] Build and static analysis pass or blockers are documented.
- [ ] All mandatory documentation exists.
- [ ] Physical-device verification is marked deferred.
- [ ] Git diff contains only authorized changes.
- [ ] Final validation report is complete.

---

# 21. STOP CONDITION

After completing Phase 5:

1. Stop implementation.
2. Do not begin Phase 6.
3. Do not implement Bluetooth transport communication.
4. Do not begin vendor protocol integration.
5. Do not create production UI.
6. Do not add unauthorized persistent storage.
7. Do not silently expand scope.

Produce a final report containing:

- Completion status.
- Implemented modules.
- Files changed.
- Fingerprint format.
- Identification confidence policy.
- Registry design.
- Tests executed.
- Build and static-analysis results.
- Known limitations.
- Outstanding risks.
- Deferred physical-device verification.
- Git commit information, if applicable.
- Confirmation of readiness for Phase 6.

Wait for explicit user authorization before proceeding.

---

# FINAL ENGINEERING PRINCIPLE

A device name is not proof of identity.

A manufacturer match is not proof of model identity.

A model match is not proof of protocol compatibility.

Unknown devices must remain unknown until sufficient evidence exists.

Implement Phase 5 only.