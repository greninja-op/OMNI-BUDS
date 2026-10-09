# Phase 15 — Hardware DSP / Audio Separation: Requirements

**Status:** Authoritative for Phase 15 execution.
**Scope:** Processing-domain architecture separating device hardware DSP,
Android platform processing, and OmniBuds application logic. Boundaries,
models, capability checks, integration contracts. No vendor DSP protocol
implementation. No hardware simulation. No media-path changes. No UI.
**Requirement ID scheme:** `OB-P15-REQ-001` … `OB-P15-REQ-028`.

---

## OB-P15-REQ-001 — Processing domain model

- **Description:** Normalized `AudioProcessingDomain`: DEVICE_HARDWARE_DSP,
  DEVICE_FIRMWARE, ANDROID_PLATFORM, APPLICATION_LOGIC, UNKNOWN. Hardware
  DSP vs firmware boundary preserved as uncertain when unidentifiable.
- **Rationale:** Every capability needs an owner.
- **Priority:** Must
- **Acceptance criteria:** Enum exists; UNKNOWN is the default.
- **Verification:** `ProcessingDomainTest`.

## OB-P15-REQ-002 — Processing capability model

- **Description:** Immutable capability: stable id, category, domain,
  support/observability/read/write status (separate fields), evidence,
  verification level, parameters, dependencies, conflicts, device/session,
  limitations.
- **Rationale:** One boolean cannot represent four facts.
- **Priority:** Must
- **Acceptance criteria:** All fields independent.
- **Verification:** `ProcessingCapabilityTest`.

## OB-P15-REQ-003 — ANC category

- **Description:** Hardware ANC: enabled/disabled/adjustable/adaptive/
  environment-aware/wind modes — each independently evidenced. Never infer
  from form factor.
- **Rationale:** ANC is a claim, not a default.
- **Priority:** Must
- **Acceptance criteria:** No mode assumed.
- **Verification:** `AncCapabilityTest`.

## OB-P15-REQ-004 — Transparency category

- **Description:** Transparency independent from ANC. ANC support never
  implies transparency support.
- **Rationale:** Separate features.
- **Priority:** Must
- **Acceptance criteria:** Independence tested.
- **Verification:** `TransparencyCapabilityTest`.

## OB-P15-REQ-005 — Hardware EQ category

- **Description:** Device EQ: presets, custom, parametric — each evidenced.
  Android EQ ≠ hardware EQ. No PCM filters in OmniBuds.
- **Rationale:** Platform ≠ device.
- **Priority:** Must
- **Acceptance criteria:** Separation enforced.
- **Verification:** `HardwareEqTest`.

## OB-P15-REQ-006 — Spatial audio category

- **Description:** Device spatial DSP vs Android spatial audio; head-tracking
  capability vs active state. Sensors ≠ active tracking.
- **Rationale:** Ownership matters.
- **Priority:** Must
- **Acceptance criteria:** Four facts separated.
- **Verification:** `SpatialAudioTest`.

## OB-P15-REQ-007 — Sidetone category

- **Description:** Device sidetone modeled without mic permission.
- **Rationale:** Capability ≠ recording.
- **Priority:** Must
- **Acceptance criteria:** No mic permission needed.
- **Verification:** `SidetoneTest`.

## OB-P15-REQ-008 — Adaptive processing

- **Description:** Adaptive ANC/EQ only with evidence. Never from marketing.
- **Rationale:** Behavior needs proof.
- **Priority:** Must
- **Acceptance criteria:** Evidence-gated.
- **Verification:** `AdaptiveProcessingTest`.

## OB-P15-REQ-009 — Extensible categories

- **Description:** Future categories via extension, not enum growth.
- **Rationale:** Vendors invent features.
- **Priority:** Should
- **Acceptance criteria:** Extension point exists.
- **Verification:** Review.

## OB-P15-REQ-010 — Domain resolver

- **Description:** Resolver assigns capabilities to domains using: device
  identity, fingerprint, discovered capabilities, protocol registration,
  transport/profile, platform support, evidence, verification, session,
  dependencies. Unknown devices read-only.
- **Rationale:** One deterministic assignment point.
- **Priority:** Must
- **Acceptance criteria:** Pure function; documented.
- **Verification:** `DomainResolverTest`.

## OB-P15-REQ-011 — No domain substitution

- **Description:** The resolver never silently substitutes Android processing
  for device hardware or vice versa. Unsupported hardware never falls back
  to software emulation.
- **Rationale:** Substitution is fabrication.
- **Priority:** Must
- **Acceptance criteria:** Substitution rejected/tested.
- **Verification:** `NoSubstitutionTest`.

## OB-P15-REQ-012 — Feature control boundaries

- **Description:** Integrate Phase 9 Feature Engine. Each control request
  names its domain. No generic setter. Unsupported/ambiguous → rejected.
- **Rationale:** Operations aren't interchangeable.
- **Priority:** Must
- **Acceptance criteria:** Domain-tagged requests; rejections tested.
- **Verification:** `ControlBoundaryTest`.

## OB-P15-REQ-013 — Processing ownership

- **Description:** Model answers: who owns the setting, who observes, who
  changes, who confirms, device/firmware/platform/app, session vs
  persistent.
- **Rationale:** Ownership determines control.
- **Priority:** Must
- **Acceptance criteria:** Ownership fields present.
- **Verification:** `OwnershipTest`.

## OB-P15-REQ-014 — Requested/observed/confirmed separation

- **Description:** Requested ≠ observed ≠ confirmed ≠ historical ≠ stale ≠
  unknown. Dispatch ≠ applied.
- **Rationale:** Honest state.
- **Priority:** Must
- **Acceptance criteria:** Six states separated.
- **Verification:** `ProcessingStateTest`.

## OB-P15-REQ-015 — Evidence and verification

- **Description:** Reuse evidence architecture + verification levels
  (INFERRED → HARDWARE_VERIFIED). Never upgrade without evidence.
- **Rationale:** Provenance.
- **Priority:** Must
- **Acceptance criteria:** No confidence inflation.
- **Verification:** `EvidenceTest`.

## OB-P15-REQ-016 — Parameter model

- **Description:** Extensible parameters: toggle, bounded numeric, discrete
  mode, preset id, structured config, unknown. Validation constraints
  required. No assumed ranges (ANC ≠ 0–100).
- **Rationale:** Vendors differ.
- **Priority:** Must
- **Acceptance criteria:** Constraints enforced.
- **Verification:** `ParameterModelTest`.

## OB-P15-REQ-017 — Dependencies and conflicts

- **Description:** Minimal contract for dependencies/conflicts. No
  speculative universal rules. Vendor constraints only with evidence.
- **Rationale:** Don't invent restrictions.
- **Priority:** Should
- **Acceptance criteria:** Contract exists; no hard-coded speculation.
- **Verification:** `DependencyTest`.

## OB-P15-REQ-018 — No application DSP

- **Description:** No PCM capture/filters, virtual ANC, interception,
  decoding, re-encoding, routing, loopback. Unavailable → reported, not
  synthesized.
- **Rationale:** The phase's hard boundary.
- **Priority:** Must
- **Acceptance criteria:** Scope test bans DSP vocabulary.
- **Verification:** `NoAppDspTest`.

## OB-P15-REQ-019 — Protocol integration

- **Description:** Hardware controls via verified protocol abstractions
  only. No raw packets in the resolver. No invented UUIDs/opcodes.
- **Rationale:** Abstraction boundaries.
- **Priority:** Must
- **Acceptance criteria:** No packet literals (scope test).
- **Verification:** `ProtocolBoundaryTest`.

## OB-P15-REQ-020 — Persistence ownership

- **Description:** Distinguish firmware / platform / app-preference /
  session-only persistence. Saving a preference ≠ applying to hardware.
- **Rationale:** Persistence has an owner.
- **Priority:** Must
- **Acceptance criteria:** Four owners separated.
- **Verification:** `PersistenceTest`.

## OB-P15-REQ-021 — Error model

- **Description:** Reuse structured errors. Uncertainty → structured result,
  not exception.
- **Rationale:** Expected unknown is normal.
- **Priority:** Must
- **Acceptance criteria:** New categories only if unmappable.
- **Verification:** Review.

## OB-P15-REQ-022 — Concurrency and lifecycle

- **Description:** Session-correct state; disconnect/reconnect handling;
  no arbitrary delays or indefinite retries.
- **Rationale:** Async reality.
- **Priority:** Must
- **Acceptance criteria:** Lifecycle tests.
- **Verification:** `LifecycleTest`.

## OB-P15-REQ-023 — Performance and privacy

- **Description:** Lightweight; event-driven; no polling; no mic permission;
  minimal identifiers; no sensitive logging.
- **Rationale:** Boundaries.
- **Priority:** Must
- **Acceptance criteria:** Scope test; review.
- **Verification:** `PrivacyTest`.

## OB-P15-REQ-024 — Multi-device isolation

- **Description:** Per-device processing state; no cross-device leakage.
- **Rationale:** Devices are independent.
- **Priority:** Must
- **Acceptance criteria:** Isolation tested.
- **Verification:** `MultiDeviceTest`.

## OB-P15-REQ-025 — Architecture

- **Description:** Domain models Android-independent; Android classes inside
  the boundary.
- **Rationale:** Testable core.
- **Priority:** Must
- **Acceptance criteria:** `DependencyDirectionTest` passes.
- **Verification:** Architecture test.

## OB-P15-REQ-026 — Documentation

- **Description:** Eight mandatory records.
- **Rationale:** Understandability.
- **Priority:** Must
- **Acceptance criteria:** All present and accurate.
- **Verification:** Review.

## OB-P15-REQ-027 — Regression

- **Description:** All Phase 0–14 tests pass.
- **Rationale:** No regressions.
- **Priority:** Must
- **Acceptance criteria:** Full suite green.
- **Verification:** Full test run.

## OB-P15-REQ-028 — Stop condition

- **Description:** Phase 16 not started; no UI; no physical device; no
  battery/power functionality.
- **Rationale:** Boundary.
- **Priority:** Must
- **Acceptance criteria:** Final audit.
- **Verification:** Scope tests.
