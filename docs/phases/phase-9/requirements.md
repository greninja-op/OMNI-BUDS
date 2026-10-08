# Phase 9 — Requirements

**Phase:** 9 — Hardware Feature Engine · **Scope id:** P9 · **Owner agent:** orchestrator
**Document status:** accepted
**Master sections implemented by this phase:** §9 (protocol abstraction contracts for features), §10 (capability model consumption), §12 (feature vocabulary), §24 (persistence ladder — read-back rule), §29 (validation before command), §53 (unknown stays unknown)

## 1. Specificity gate

- [x] One observable obligation per record, in `SHALL` / `SHALL NOT` form.
- [x] Actor and object named.
- [x] Every acceptance criterion is pass/fail decidable without interpretation.
- [x] Verification method is runnable at this phase's access level (unit test; no hardware).
- [x] No invented vendor facts, no assumed protocol bytes, no assumed UUID purpose.
- [x] Vague wording is rejected.

## 2. Requirement records

```text
OB-P9-REQ-001
Title:                Hardware feature domain model
Description:          The system SHALL provide a platform-independent hardware feature domain model
                      comprising feature definitions, value types, access levels and operation types.
Rationale:            MASTER-CONTEXT §12; without a shared model, every future vendor protocol would
                      invent its own feature vocabulary (ADR-P0-007).
Dependencies:         NONE
Acceptance Criteria:  AC-1: FeatureDefinition, FeatureValueType, FeatureAccess and FeatureOperationType
                      exist in :core and compile without Android imports.
                      AC-2: Architecture tests enforce the feature area's layer placement.
Verification Method:  unit test (FeatureValueTest, PhaseNineScopeTest, DependencyDirectionTest)
Priority:             MUST
Status:               implemented
```

```text
OB-P9-REQ-002
Title:                Extended typed value system
Description:          The system SHALL extend ConfigurationValue with FLOAT, RANGE, STRUCTURED,
                      BITMASK and CUSTOM shapes rather than forking a second value type.
Rationale:            ADR-P9-001; a forked hierarchy would let the same value mean two things
                      (the ADR-P8-001 precedent against parallel enums).
Dependencies:         NONE
Acceptance Criteria:  AC-1: The five new shapes exist as ConfigurationValue subtypes with
                      construction-time validation.
                      AC-2: No second value hierarchy exists in the feature area (scope test).
Verification Method:  unit test (FeatureValueTest, PhaseNineScopeTest)
Priority:             MUST
Status:               implemented
```

```text
OB-P9-REQ-003
Title:                Requested state is distinct from confirmed state
Description:          The system SHALL represent a requested-but-unconfirmed value as
                      FeatureState.Pending and SHALL NOT treat it as the device state.
Rationale:            Phase 9 prompt §10; a command acceptance is rung 2 of the persistence
                      ladder, not an applied change (Phase 7 FeatureWriteSupport contract).
Dependencies:         OB-P9-REQ-001
Acceptance Criteria:  AC-1: A write moves the feature to Pending carrying the requested value.
                      AC-2: Confirmed is written only after read-back equality or a device report.
                      AC-3: Tests demonstrate Pending -> Confirmed and Pending -> Failed.
Verification Method:  unit test (FeatureEngineTest, FeatureStateTest)
Priority:             MUST
Status:               implemented
```

```text
OB-P9-REQ-004
Title:                Six-state control state model
Description:          The system SHALL model feature control state as UNKNOWN, AVAILABLE, PENDING,
                      CONFIRMED, FAILED or UNAVAILABLE, with an explicit legal-transition table.
Rationale:            Phase 9 prompt §9; reconciles the prompt's §2 rule 3 vocabulary onto one
                      axis (ADR-P9-003). CapabilityState is untouched (ARCH-TERM-003).
Dependencies:         OB-P9-REQ-001
Acceptance Criteria:  AC-1: FeatureState and FeatureStateTransitions exist; the repository
                      refuses illegal transitions.
                      AC-2: UNKNOWN -> PENDING is illegal (writes require established capability).
Verification Method:  unit test (FeatureStateTest)
Priority:             MUST
Status:               implemented
```

```text
OB-P9-REQ-005
Title:                Capability gating of operations
Description:          The engine SHALL refuse operations on features whose capability state is
                      UNKNOWN or UNSUPPORTED, and SHALL distinguish the two in its errors.
Rationale:            MASTER-CONTEXT §53; Rule 2 — unknown is not unsupported, and neither may
                      be operated on.
Dependencies:         OB-P9-REQ-001
Acceptance Criteria:  AC-1: Validation fails with FEATURE_UNKNOWN for unknown, FEATURE_UNSUPPORTED
                      for positively unsupported.
                      AC-2: No port call is made for a refused operation.
Verification Method:  unit test (FeatureValidatorTest)
Priority:             MUST
Status:               implemented
```

```text
OB-P9-REQ-006
Title:                Read/write access modelling
Description:          The system SHALL derive FeatureAccess (UNKNOWN, READ_ONLY, WRITE_ONLY,
                      READ_WRITE, UNAVAILABLE) from the capability record and momentary
                      availability, and SHALL NOT offer write controls for read-only capabilities.
Rationale:            Phase 9 prompt §11; affordances follow the capability record.
Dependencies:         OB-P9-REQ-005
Acceptance Criteria:  AC-1: accessOf maps every CapabilityState and CapabilityAvailability.
                      AC-2: WRITE_ONLY is reserved vocabulary, never produced (documented).
                      AC-3: permits() refuses WRITE under READ_ONLY.
Verification Method:  unit test (FeatureAccessTest)
Priority:             MUST
Status:               implemented
```

```text
OB-P9-REQ-007
Title:                Value validation without silent clamping
Description:          The engine SHALL validate requested values against the definition's shape
                      and constraints before sending any command, and SHALL refuse invalid values
                      with INVALID_VALUE rather than clamping them.
Rationale:            Phase 9 prompt §13; silently changing a requested value is fabrication.
Dependencies:         OB-P9-REQ-002
Acceptance Criteria:  AC-1: Wrong shape, out-of-range, disallowed mode/flag and over-long
                      values are refused with a naming detail.
                      AC-2: No clamping code exists in the engine.
Verification Method:  unit test (FeatureValueTest, FeatureValidatorTest)
Priority:             MUST
Status:               implemented
```

```text
OB-P9-REQ-008
Title:                Feature dependency evaluation
Description:          The system SHALL evaluate Requires, Requires-One-Of, Conflicts-With,
                      Mutually-Exclusive, Implies and Vendor-Exception relations declared by
                      feature definitions, with cycle detection, deterministic verdicts and
                      explanations.
Rationale:            Phase 9 prompt §19; Requires-edges reuse DependencyValidator so
                      "requires" means one thing in discovery and control.
Dependencies:         OB-P9-REQ-001
Acceptance Criteria:  AC-1: Missing/unknown/cycled prerequisites block writes with
                      DEPENDENCY_NOT_SATISFIED; unknown prerequisites do not block reads.
                      AC-2: Cycles across definitions are detected and block.
                      AC-3: The evaluator never enables a prerequisite or disables a feature.
Verification Method:  unit test (FeatureDependencyEvaluatorTest)
Priority:             MUST
Status:               implemented
```

```text
OB-P9-REQ-009
Title:                Conflict handling without silent changes
Description:          The system SHALL refuse a write that would drive two declared-conflicting
                      features at once, with an explanation, and SHALL NOT silently disable the
                      conflicting feature.
Rationale:            Phase 9 prompt §19 — "Never silently disable conflicting features."
Dependencies:         OB-P9-REQ-008
Acceptance Criteria:  AC-1: An active confirmed value on a conflicting feature blocks the write
                      with FEATURE_CONFLICT.
                      AC-2: An inert value on the conflicting feature does not block.
Verification Method:  unit test (FeatureDependencyEvaluatorTest, FeatureValidatorTest)
Priority:             MUST
Status:               implemented
```

```text
OB-P9-REQ-010
Title:                Vendor extension contract
Description:          The system SHALL support vendor-specific features under
                      vendor.<vendor>.<feature> identities through the same engine, validator
                      and state machinery as core features, with no brand branches in shared code.
Rationale:            Phase 9 prompt Rule 5; ADR-P0-007; the capability half lives in
                      VendorExtension.
Dependencies:         OB-P9-REQ-001
Acceptance Criteria:  AC-1: VendorFeatureContract.define creates valid vendor definitions and
                      refuses malformed namespaces.
                      AC-2: Vendor definitions validate values and participate in dependency
                      evaluation identically to core ones.
                      AC-3: No brand names appear in feature main sources (scope test).
Verification Method:  unit test (VendorFeatureContractTest, PhaseNineScopeTest)
Priority:             MUST
Status:               implemented
```

```text
OB-P9-REQ-011
Title:                Authoritative reactive state repository
Description:          The system SHALL provide a single-owner, thread-safe FeatureStateRepository
                      exposing control state as a StateFlow, with no duplicate state owners and
                      no UI dependencies.
Rationale:            Phase 9 prompt §23; ARCH-LAYER-003 — control truth lives in exactly one place.
Dependencies:         OB-P9-REQ-004
Acceptance Criteria:  AC-1: InMemoryFeatureStateRepository serializes updates and refuses
                      illegal transitions.
                      AC-2: states is a StateFlow of an immutable map.
Verification Method:  unit test (FeatureEngineTest observe test, FeatureStateTest)
Priority:             MUST
Status:               implemented
```

```text
OB-P9-REQ-012
Title:                Operation lifecycle with mandatory read-back
Description:          The engine SHALL read a feature back after every accepted write and SHALL
                      record Confirmed only when the read-back equals the request; a divergent
                      read-back confirms the device's value and fails the operation with
                      STATE_VERIFICATION_FAILED; a failed read-back leaves the state UNKNOWN.
Rationale:            Phase 9 prompt §10; the Phase 7 FeatureWriteSupport contract obliges the
                      caller to read back and record the comparison.
Dependencies:         OB-P9-REQ-003, OB-P9-REQ-011
Acceptance Criteria:  AC-1: Write tests cover equal, divergent and failed read-back.
                      AC-2: Exactly one write call per accepted write in every case.
Verification Method:  unit test (FeatureEngineTest)
Priority:             MUST
Status:               implemented
```

```text
OB-P9-REQ-013
Title:                Retry safety for side-effecting operations
Description:          The engine SHALL NOT re-send a timed-out write; it SHALL follow a timeout
                      with one read-back and mark the result confirmed, failed or unknown based
                      on the evidence. Retry behaviour SHALL derive from the error category's
                      RetryClass, never from the call site.
Rationale:            Phase 9 prompt §21; PROTO-ERR-002; RetryClass contract.
Dependencies:         OB-P9-REQ-012
Acceptance Criteria:  AC-1: A timed-out write produces exactly one port write call.
                      AC-2: Timeout + read==requested confirms; + divergent confirms the device
                      value and reports TIMEOUT; + failed read leaves UNKNOWN and reports TIMEOUT.
Verification Method:  unit test (FeatureEngineTest)
Priority:             MUST
Status:               implemented
```

```text
OB-P9-REQ-014
Title:                External device state updates
Description:          The engine SHALL accept device-reported values at any time, validate them
                      against the definition, and record them as Confirmed, overriding stale
                      requested state.
Rationale:            Phase 9 prompt §22; the hardware may change on its own — device truth wins.
Dependencies:         OB-P9-REQ-004, OB-P9-REQ-007
Acceptance Criteria:  AC-1: onDeviceReported during a Pending write supersedes it; the write's
                      completion does not overwrite the report.
                      AC-2: Reports for undefined features and malformed values are ignored.
Verification Method:  unit test (FeatureEngineTest)
Priority:             MUST
Status:               implemented
```

```text
OB-P9-REQ-015
Title:                Structured feature errors on existing categories
Description:          Every feature failure SHALL be a structured OmniBudsError whose category is
                      an existing OmniBudsErrorCategory, and SHALL communicate retry behaviour
                      through that category's RetryClass.
Rationale:            ADR-P8-004 precedent — no second error taxonomy; Phase 9 prompt §24.
Dependencies:         OB-P9-REQ-001
Acceptance Criteria:  AC-1: FeatureErrorCode maps every code to an existing category.
                      AC-2: Cancellation is not an error code (ADR-P1-004).
Verification Method:  unit test (FeatureEngineTest, FeatureValidatorTest)
Priority:             MUST
Status:               implemented
```

```text
OB-P9-REQ-016
Title:                Concurrency: one operation per feature
Description:          The engine SHALL serialize operations on the same feature while allowing
                      concurrent operations on different features, and SHALL restore the prior
                      state (not strand Pending) when an operation is cancelled.
Rationale:            Phase 9 prompt §22; interleaved writes to one feature are contradictory.
Dependencies:         OB-P9-REQ-011
Acceptance Criteria:  AC-1: Two concurrent writes to one feature serialize; the second
                      validates against fresh state.
                      AC-2: Writes to different features overlap.
                      AC-3: Cancellation never leaves Pending behind.
Verification Method:  unit test (FeatureConcurrencyTest, FeatureEngineTest)
Priority:             MUST
Status:               implemented
```

```text
OB-P9-REQ-017
Title:                Session invalidation handling
Description:          On session invalidation the engine SHALL move every tracked feature to
                      UNKNOWN (keeping the last confirmed value as stale knowledge) and SHALL
                      complete in-flight operations as DEVICE_DISCONNECTED without touching state.
Rationale:            A dead session's in-flight results cannot be trusted (INVALID_STATE family).
Dependencies:         OB-P9-REQ-004
Acceptance Criteria:  AC-1: In-flight write during invalidation reports DEVICE_DISCONNECTED.
                      AC-2: All tracked states become Unknown with lastConfirmed preserved.
Verification Method:  unit test (FeatureEngineTest, FeatureConcurrencyTest)
Priority:             MUST
Status:               implemented
```

```text
OB-P9-REQ-018
Title:                ANC architecture contracts
Description:          The system SHALL define ANC control contracts (switch, mode, level,
                      adaptive, wind reduction, environment mode) as capability-driven
                      definitions without implementing any vendor ANC command.
Rationale:            Phase 9 prompt §14; definitions describe, they do not encode.
Dependencies:         OB-P9-REQ-001
Acceptance Criteria:  AC-1: The six noise-control definitions exist with shapes and relations.
                      AC-2: No vendor command, opcode, UUID or packet layout exists in main.
Verification Method:  unit test (StandardFeaturesTest, PhaseNineScopeTest)
Priority:             MUST
Status:               implemented
```

```text
OB-P9-REQ-019
Title:                Transparency architecture contracts
Description:          The system SHALL define transparency contracts (switch, level, automatic,
                      voice pass-through) supporting both on/off-only and levelled devices.
Rationale:            Phase 9 prompt §15.
Dependencies:         OB-P9-REQ-001
Acceptance Criteria:  AC-1: The four transparency definitions exist; level Requires transparency.
Verification Method:  unit test (StandardFeaturesTest)
Priority:             MUST
Status:               implemented
```

```text
OB-P9-REQ-020
Title:                Equalizer architecture contract
Description:          The system SHALL define one structured equalizer contract covering preset,
                      graphic (variable bands), parametric and tone forms, controlling the
                      device's hardware EQ with no phone-side DSP.
Rationale:            Phase 9 prompt §16 and Rule 6; CoreFeature's "bands are values" precedent
                      (ADR-P9-004).
Dependencies:         OB-P9-REQ-002
Acceptance Criteria:  AC-1: EqualizerValues constructors produce values the definition accepts.
                      AC-2: Variable band counts validate; NaN/Infinity never construct.
                      AC-3: No audio processing code exists in the feature area.
Verification Method:  unit test (StandardFeaturesTest)
Priority:             MUST
Status:               implemented
```

```text
OB-P9-REQ-021
Title:                Gesture architecture contract
Description:          The system SHALL define a structured gesture-assignment contract (gesture,
                      side, action) with a documented — not enforced — action vocabulary, so
                      vendor-specific actions remain expressible.
Rationale:            Phase 9 prompt §17; a closed universal action set would fabricate a claim.
Dependencies:         OB-P9-REQ-002
Acceptance Criteria:  AC-1: GestureValues produce values the definition accepts, including a
                      vendor action outside the documented set.
Verification Method:  unit test (StandardFeaturesTest)
Priority:             MUST
Status:               implemented
```

```text
OB-P9-REQ-022
Title:                Remaining hardware feature contracts
Description:          The system SHALL define contracts for wear detection, multipoint, spatial
                      audio, head tracking, gaming mode, voice prompts and sidetone as
                      definition-level vocabulary without vendor commands.
Rationale:            Phase 9 prompt §18.
Dependencies:         OB-P9-REQ-001
Acceptance Criteria:  AC-1: The seven definitions exist with the prompt's shapes.
Verification Method:  unit test (StandardFeaturesTest)
Priority:             MUST
Status:               implemented
```

```text
OB-P9-REQ-023
Title:                Protocol integration through the port seam
Description:          The engine SHALL reach the device only through the handed-in
                      FeatureProtocolPort, SHALL NOT import the protocol or transport layers,
                      and SHALL NOT bypass protocol abstractions.
Rationale:            Phase 9 prompt Agent G; ADR-P9-002 (the ADR-P8-005 pattern).
Dependencies:         OB-P9-REQ-001
Acceptance Criteria:  AC-1: No feature main source imports com.omnibuds.core.protocol or
                      com.omnibuds.core.transport (scope test).
                      AC-2: No production source implements FeatureProtocolPort (scope test).
                      AC-3: Validation checks port.supportsRead/supportsWrite before attempting.
Verification Method:  unit test (PhaseNineScopeTest, FeatureValidatorTest)
Priority:             MUST
Status:               implemented
```

```text
OB-P9-REQ-024
Title:                UI contract without UI implementation
Description:          The engine SHALL expose enough structured state (support, access,
                      confirmed value, pending request, failure, unknown) for a future UI to
                      answer its questions, and SHALL contain no UI code.
Rationale:            Phase 9 prompt §26; UI arrives at Phase 49 (ADR-P1-001).
Dependencies:         OB-P9-REQ-004, OB-P9-REQ-011
Acceptance Criteria:  AC-1: observe() exposes per-feature state reactively.
                      AC-2: The architecture tests find no UI framework references.
Verification Method:  unit test (FeatureEngineTest, DependencyDirectionTest)
Priority:             MUST
Status:               implemented
```

```text
OB-P9-REQ-025
Title:                Phase 9 forbiddens are absent
Description:          The phase SHALL NOT implement vendor commands, phone-side simulated
                      effects, production UI, firmware updates, or any hardware contact.
Rationale:            Phase 9 prompt §27.
Dependencies:         NONE
Acceptance Criteria:  AC-1: PhaseNineScopeTest finds no protocol literals, no brand names,
                      no simulation markers, no production port implementation.
                      AC-2: Git diff contains only the feature area, the config value shapes,
                      tests, and phase-9 docs.
Verification Method:  unit test (PhaseNineScopeTest); diff review
Priority:             MUST
Status:               implemented
```

## 3. Traceability

| REQ | TASK ids | TEST ids |
|---|---|---|
| OB-P9-REQ-001 | TASK-P9-001, TASK-P9-002 | TEST-P9-001, TEST-P9-009 |
| OB-P9-REQ-002 | TASK-P9-003 | TEST-P9-001, TEST-P9-009 |
| OB-P9-REQ-003 | TASK-P9-004 | TEST-P9-003, TEST-P9-004 |
| OB-P9-REQ-004 | TASK-P9-004 | TEST-P9-003 |
| OB-P9-REQ-005 | TASK-P9-005 | TEST-P9-005 |
| OB-P9-REQ-006 | TASK-P9-006 | TEST-P9-002 |
| OB-P9-REQ-007 | TASK-P9-007 | TEST-P9-001, TEST-P9-005 |
| OB-P9-REQ-008 | TASK-P9-008 | TEST-P9-006 |
| OB-P9-REQ-009 | TASK-P9-008 | TEST-P9-006 |
| OB-P9-REQ-010 | TASK-P9-009 | TEST-P9-008, TEST-P9-009 |
| OB-P9-REQ-011 | TASK-P9-010 | TEST-P9-004 |
| OB-P9-REQ-012 | TASK-P9-011 | TEST-P9-004 |
| OB-P9-REQ-013 | TASK-P9-011 | TEST-P9-004 |
| OB-P9-REQ-014 | TASK-P9-012 | TEST-P9-004 |
| OB-P9-REQ-015 | TASK-P9-013 | TEST-P9-004, TEST-P9-005 |
| OB-P9-REQ-016 | TASK-P9-014 | TEST-P9-007 |
| OB-P9-REQ-017 | TASK-P9-015 | TEST-P9-004, TEST-P9-007 |
| OB-P9-REQ-018 | TASK-P9-016 | TEST-P9-010 |
| OB-P9-REQ-019 | TASK-P9-016 | TEST-P9-010 |
| OB-P9-REQ-020 | TASK-P9-016 | TEST-P9-010 |
| OB-P9-REQ-021 | TASK-P9-016 | TEST-P9-010 |
| OB-P9-REQ-022 | TASK-P9-016 | TEST-P9-010 |
| OB-P9-REQ-023 | TASK-P9-017 | TEST-P9-009 |
| OB-P9-REQ-024 | TASK-P9-010 | TEST-P9-004 |
| OB-P9-REQ-025 | TASK-P9-018 | TEST-P9-009 |
