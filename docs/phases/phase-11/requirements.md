# Phase 11 — Codec Capability Engine: Requirements

**Status:** Authoritative for Phase 11 execution.
**Scope:** Platform-independent codec capability observation. No codec configuration,
no codec switching, no media-audio processing, no production UI, no physical-device testing.
**Requirement ID scheme:** `OB-P11-REQ-001` … `OB-P11-REQ-030`.

---

## OB-P11-REQ-001 — Codec identity model

- **Description:** A stable domain-level codec identity covering SBC, AAC, aptX, aptX HD,
  aptX Adaptive, aptX Lossless, LDAC, LC3, Opus, and UNKNOWN.
- **Rationale:** Identity is the foundation; without it, capabilities cannot be attributed.
- **Priority:** Must
- **Dependencies:** None
- **Acceptance criteria:** `Codec` enum extended with `OPUS`; all 10 identities present;
  display names separate from identities.
- **Verification:** `CodecDomainTest.allRequiredCodecIdentitiesExist`.

## OB-P11-REQ-002 — Transport association

- **Description:** Every codec carries its transport family (`CodecFamily`): LC3 → LE_AUDIO;
  SBC/AAC/aptX variants/LDAC/Opus → CLASSIC_A2DP; UNKNOWN → UNKNOWN.
- **Rationale:** LC3-as-A2DP is the canonical invalid assumption; the type system must
  make it inexpressible.
- **Priority:** Must
- **Dependencies:** OB-P11-REQ-001
- **Acceptance criteria:** Family assertions per codec; engine derives snapshot transport
  from codec family, never a hard-coded default.
- **Verification:** `CodecDomainTest`, `CodecMappingTest.lc3IsNeverNormalizedToA2dp`,
  `CodecCapabilityEngineTest.transportComesFromCodecFamily`.

## OB-P11-REQ-003 — Capability states distinguishable

- **Description:** SUPPORTED, AVAILABLE, ENABLED, NEGOTIATED, ACTIVE are distinct rungs
  on the `CodecState` evidence ladder; CONFIGURABLE is orthogonal; UNKNOWN is the default.
- **Rationale:** "LDAC is active" and "LDAC exists in an enum" are different facts; the
  model must not let one be read as the other.
- **Priority:** Must
- **Dependencies:** None (reuses ADR-P1-005 ladder)
- **Acceptance criteria:** Each rung distinct; `isActive` exact (only ACTIVE);
  `configurable` independent of rung.
- **Verification:** `CodecCapabilityStateTest` (9 tests).

## OB-P11-REQ-004 — Capability vs runtime state separated

- **Description:** `CodecCapability` (static: what the codec can do, with evidence) and
  `CodecRuntimeState` (live: what it is doing, timestamped, freshness-aware) are
  separate types, never merged.
- **Rationale:** Merging them recreates the six-boolean nonsense the ladder was built
  to prevent.
- **Priority:** Must
- **Dependencies:** OB-P11-REQ-003
- **Acceptance criteria:** Two types exist; engine holds both per device; no combined
  mega-model.
- **Verification:** Code review; `CodecCapabilityEngineTest`.

## OB-P11-REQ-005 — Codec metadata (nullable)

- **Description:** Sample rate, bits per sample, channel mode, bitrate (exact/range/
  adaptive/unknown), quality mode — all nullable-or-unknown. Missing values never
  become 0, 16, stereo, or an invented bitrate.
- **Rationale:** A partial read is a valid result; defaults are fabrications.
- **Priority:** Must
- **Dependencies:** None
- **Acceptance criteria:** `CodecMetadata` with nullable fields; `CodecBitrate` sealed
  hierarchy; invariant tests for no-default behavior.
- **Verification:** `CodecMetadataTest` (8 tests).

## OB-P11-REQ-006 — Evidence model

- **Description:** Every codec claim carries `CodecEvidence`: source
  (`CodecEvidenceSource`: ANDROID_FRAMEWORK, AUDIO_DEVICE_INFO, BLUETOOTH_PROFILE,
  PLATFORM_CODEC_METADATA, DEVICE_PROTOCOL, VENDOR_PROTOCOL, UNKNOWN), confidence
  (`EvidenceConfidence`: UNKNOWN, INFERRED, OBSERVED, VERIFIED), observation time,
  and an API-naming detail.
- **Rationale:** OmniBuds must know WHY it believes something; inferred claims must
  never carry observed confidence.
- **Priority:** Must
- **Dependencies:** None
- **Acceptance criteria:** Evidence on `CodecCapability` and `CodecRuntimeState`;
  confidence ordering; detail strings name APIs, not devices.
- **Verification:** `CodecEvidenceTest`; `CodecScopeTest`.

## OB-P11-REQ-007 — Observability model

- **Description:** `CodecObservability`: OBSERVABLE, PARTIALLY_OBSERVABLE,
  NOT_OBSERVABLE, UNKNOWN. Separates platform limitations from device limitations.
- **Rationale:** "AAC UNKNOWN + NOT_OBSERVABLE" must not render as "AAC unsupported".
- **Priority:** Must
- **Dependencies:** OB-P11-REQ-006
- **Acceptance criteria:** Observability on capabilities and snapshots; explicit tests
  that NOT_OBSERVABLE ≠ unsupported.
- **Verification:** `CodecEvidenceTest.notObservableIsNotUnsupported`;
  `AndroidCodecObservationSourceTest.emptyReadMeansUnobservableNotUnsupported`.

## OB-P11-REQ-008 — Codec snapshot

- **Description:** Immutable per-device `CodecSnapshot`: schema version, timestamp,
  device, transport, capabilities list, runtime state, observability, explicit
  limitations, bounded diagnostics. No mutable collections escape.
- **Rationale:** One coherent per-device picture; limitations documented, not hidden.
- **Priority:** Must
- **Dependencies:** OB-P11-REQ-002, OB-P11-REQ-004, OB-P11-REQ-007
- **Acceptance criteria:** Data class with all fields; `activeCodec` derived;
  immutability test.
- **Verification:** `CodecCapabilityEngineTest.snapshotIsImmutable`.

## OB-P11-REQ-009 — Multiple devices isolated

- **Description:** Snapshots keyed by `DeviceIdentity`; device A's codec state can
  never appear in device B's snapshot.
- **Rationale:** Earbuds on LDAC + headphones on AAC is normal, not an edge case.
- **Priority:** Must
- **Dependencies:** OB-P11-REQ-008
- **Acceptance criteria:** Map-structured store; tests with A→LDAC, B→AAC; disconnect
  A leaves B current.
- **Verification:** `CodecCapabilityEngineTest.deviceAStateNeverAppearsInDeviceB`,
  `deviceBRemainsActiveWhenDeviceADisconnects`.

## OB-P11-REQ-010 — Staleness handling

- **Description:** `CodecFreshness`: CURRENT, STALE, UNKNOWN. `stop()` marks runtime
  STALE (never silently drops, never leaves current); reconnect re-observes fresh.
- **Rationale:** A disconnected device's "active" codec must not render as current.
- **Priority:** Must
- **Dependencies:** OB-P11-REQ-004
- **Acceptance criteria:** stop → STALE; start after stop → CURRENT with new data;
  stale record does not survive reconnect.
- **Verification:** `CodecCapabilityEngineTest.stopMarksRuntimeStaleInsteadOfDropping`,
  `reconnectReobservesFreshState`.

## OB-P11-REQ-011 — Engine lifecycle

- **Description:** Per-device `start`/`stop`/`refresh`; idempotent start; refresh
  requires observation; failed start publishes nothing and returns a typed error.
- **Rationale:** Lifecycle safety is the Phase 10 pattern; codec observation gets
  the same guarantees.
- **Priority:** Must
- **Dependencies:** OB-P11-REQ-008
- **Acceptance criteria:** All lifecycle tests pass; `refresh` on unobserved device
  → INVALID_STATE.
- **Verification:** `CodecCapabilityEngineTest` (11 tests).

## OB-P11-REQ-012 — Flow/state observation

- **Description:** `snapshots: StateFlow<Map<DeviceIdentity, CodecSnapshot>>`;
  `observeSnapshot(device)` per-device flow; runtime updates via port flow;
  failing flow → diagnostic, engine survives.
- **Rationale:** Reactive consumers; a broken callback must not kill observation.
- **Priority:** Must
- **Dependencies:** OB-P11-REQ-011
- **Acceptance criteria:** Flows exist; catch-and-continue on flow failure.
- **Verification:** Code review; engine tests.

## OB-P11-REQ-013 — Structured errors

- **Description:** New categories: `CODEC_OBSERVATION_FAILED` (SAFE_TO_RETRY),
  `CODEC_NOT_OBSERVABLE` (NEVER_RETRY), `CODEC_STATE_STALE` (SAFE_TO_RETRY); none
  invalidates a session. Observation failure never becomes "codec unsupported".
- **Rationale:** Callers need the project's error vocabulary; a failed read is
  not a negative claim.
- **Priority:** Must
- **Dependencies:** None
- **Acceptance criteria:** Categories added with correct retry/session semantics;
  exhaustive tests updated.
- **Verification:** `OmniBudsErrorCategoryTest`.

## OB-P11-REQ-014 — Android adapter (API 35+ local capabilities)

- **Description:** `AndroidCodecObservationSource` reads local supported codec types
  via `BluetoothA2dp.getSupportedCodecTypes()` on API 35+ → SUPPORTED rung,
  OBSERVED confidence. Below API 35 or without permission: UNKNOWN + NOT_OBSERVABLE.
- **Rationale:** The only public codec-capability API; honest about its limits
  (local phone capabilities, not per-device state).
- **Priority:** Must
- **Dependencies:** OB-P11-REQ-003, OB-P11-REQ-006
- **Acceptance criteria:** Fake-handle tests for both paths; permission-first
  (empty, never throw).
- **Verification:** `AndroidCodecObservationSourceTest` (6 tests).

## OB-P11-REQ-015 — Active codec honesty

- **Description:** No public Android API exposes the active/negotiated A2DP or
  LE Audio codec. The adapter returns null runtime state ("unobserved") and
  records NOT_OBSERVABLE + explicit limitations — never invents a codec.
- **Rationale:** RULE 2, RULE 4: no authoritative runtime evidence exists, so
  no ACTIVE claim is made.
- **Priority:** Must
- **Dependencies:** OB-P11-REQ-007
- **Acceptance criteria:** `readRuntimeState` returns null; snapshot limitations
  name the missing API; scope test bans invented bitrates.
- **Verification:** `AndroidCodecObservationSourceTest.runtimeStateIsNullWhenUnexposed`;
  `CodecCapabilityEngineTest.unobservablePlatformRecordsLimitations`.

## OB-P11-REQ-016 — Platform mapping isolation

- **Description:** `codec/mapping` is the only translator of platform codec ids;
  unknown ids → null (never a guess); `Codec`/`CodecState` never leak Android
  constants; core has no `android.*` imports.
- **Rationale:** The translation split is what makes the adapter testable.
- **Priority:** Must
- **Dependencies:** None
- **Acceptance criteria:** Mapping tests for both constant families; LC3→LE_AUDIO;
  scope test bans android imports in core codec.
- **Verification:** `CodecMappingTest`; `CodecScopeTest.noAndroidImportsInCoreCodec`.

## OB-P11-REQ-017 — API-level guards

- **Description:** API 35+ code isolated in `CodecApi35` (init-time check, never
  loaded below 35); `Build.VERSION_CODES.VANILLA_ICE_CREAM` guard; minSdk stays 26.
- **Rationale:** Unconditional references risk VerifyError on older runtimes.
- **Priority:** Must
- **Dependencies:** OB-P11-REQ-014
- **Acceptance criteria:** Isolated class; graceful degradation to NOT_OBSERVABLE.
- **Verification:** Code review; `AndroidCodecObservationSourceTest`.

## OB-P11-REQ-018 — Permission handling

- **Description:** BLUETOOTH_CONNECT checked in the handle; denial → empty read →
  UNKNOWN/NOT_OBSERVABLE. No RECORD_AUDIO. No permission requests from domain.
- **Rationale:** A permission denial is a limitation, not a negative codec claim.
- **Priority:** Must
- **Dependencies:** OB-P11-REQ-014
- **Acceptance criteria:** `hasBluetoothConnect` gate; scope test bans RECORD_AUDIO.
- **Verification:** Code review; `CodecScopeTest`.

## OB-P11-REQ-019 — No codec control

- **Description:** No switching, forcing, LDAC/aptX/LC3 configuration, priority
  modification, hidden settings, shell commands, or vendor writes exist.
- **Rationale:** The phase boundary is the product contract.
- **Priority:** Must
- **Dependencies:** None
- **Acceptance criteria:** Scope test bans control vocabulary; code review confirms
  no write paths.
- **Verification:** `CodecScopeTest.noCodecSwitchingVocabulary`.

## OB-P11-REQ-020 — No media interception

- **Description:** No capture, decode, re-encode, interception, proxying, DSP, or
  recording-to-determine-codec.
- **Rationale:** OmniBuds stays outside the media data path.
- **Priority:** Must
- **Dependencies:** None
- **Acceptance criteria:** Scope test bans interception vocabulary.
- **Verification:** `CodecScopeTest.noMediaInterceptionVocabulary`.

## OB-P11-REQ-021 — Phase 10 integration

- **Description:** Consume Phase 10 abstractions (`AudioTransportKind`,
  `OmniBudsErrorCategory` patterns); do not rewrite Phase 10.
- **Rationale:** The codec engine layers on the transport engine, not beside it.
- **Priority:** Must
- **Dependencies:** None
- **Acceptance criteria:** No Phase 10 file rewritten except additive `Codec` enum
  entry and error categories; architecture test layer map updated.
- **Verification:** `DependencyDirectionTest`; git diff review.

## OB-P11-REQ-022 — LDAC modeling

- **Description:** LDAC identity, transport, all six ladder rungs, configurable
  flag, and quality-mode metadata where exposed. No assumed mode, no switching.
- **Rationale:** LDAC is the codec users ask about; the model must be complete
  and honest.
- **Priority:** Must
- **Dependencies:** OB-P11-REQ-001, OB-P11-REQ-005
- **Acceptance criteria:** LDAC in all mapping tests; quality modes in metadata.
- **Verification:** `CodecMappingTest`; `CodecMetadataTest`.

## OB-P11-REQ-023 — LE Audio / LC3 separation

- **Description:** LC3 modeled as LE Audio; never as A2DP. LE Audio runtime codec
  config has no public API → NOT_OBSERVABLE with explicit limitation.
- **Rationale:** RULE 10, RULE 11.
- **Priority:** Must
- **Dependencies:** OB-P11-REQ-002
- **Acceptance criteria:** Mapping and engine tests prove separation; snapshot
  limitations name the LE Audio gap.
- **Verification:** `CodecMappingTest.lc3IsNeverNormalizedToA2dp`;
  `CodecCapabilityEngineTest.transportComesFromCodecFamily`.

## OB-P11-REQ-024 — aptX variants separated

- **Description:** aptX, aptX HD, aptX Adaptive, aptX Lossless are four identities;
  no platform constant exists for Adaptive/Lossless → NOT_OBSERVABLE, never
  inferred from aptX.
- **Rationale:** Supporting aptX evidences nothing about the variants.
- **Priority:** Must
- **Dependencies:** OB-P11-REQ-001
- **Acceptance criteria:** Four distinct entries; mapping test proves no id maps
  to Adaptive/Lossless.
- **Verification:** `CodecDomainTest.aptXVariantsAreSeparateIdentities`;
  `CodecMappingTest.aptXAdaptiveAndLosslessHaveNoPlatformConstant`.

## OB-P11-REQ-025 — Documentation

- **Description:** Eight mandatory records + `architecture.md`; honest platform
  limitations; no implication that Android exposes what it does not.
- **Rationale:** The phase is not complete until its reasoning is recorded.
- **Priority:** Must
- **Dependencies:** All above
- **Acceptance criteria:** `docs/phases/phase-11/` complete; validation records
  actual counts and the Gradle limitation.
- **Verification:** File review.

## OB-P11-REQ-026 – OB-P11-REQ-030 — Reserved

Reserved for Phase 11 extensions. Not used.
