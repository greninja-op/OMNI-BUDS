# Phase 12 — Codec Support & Configuration Architecture: Requirements

**Status:** Authoritative for Phase 12 execution.
**Scope:** Codec support/configuration architecture (AAC, LDAC, aptX family, LC3, SBC)
built on the Phase 11 Codec Capability Engine. Real configuration only where a
legitimate platform or verified vendor-protocol mechanism exists. No fake control,
no hidden APIs, no shell/root, no media-audio interception, no UI, no
physical-device testing.
**Requirement ID scheme:** `OB-P12-REQ-001` … `OB-P12-REQ-040`.

---

## OB-P12-REQ-001 — Five-level distinction

- **Description:** The architecture distinguishes KNOWING about a codec from OBSERVING
  it from SUPPORTING it from CONFIGURING it from ACTUALLY CHANGING it, as separate
  modeled facts.
- **Rationale:** The existence of a codec enum is not permission to control it.
- **Priority:** Must
- **Dependencies:** Phase 11 codec vocabulary
- **Acceptance criteria:** `CodecControlCapability` carries observable/supported/
  selectable/configurable/verifiable as independent booleans; LDAC with
  supported=true, selectable=false, configurable=false is representable.
- **Verification:** `CodecControlCapabilityTest`.

## OB-P12-REQ-002 — No fake control

- **Description:** No operation may return success unless the codec change was
  actually performed. Requesting a codec is never reported as the codec being active.
- **Rationale:** OmniBuds must never create the illusion of codec control.
- **Priority:** Must
- **Dependencies:** OB-P12-REQ-001
- **Acceptance criteria:** Request LDAC → observation still AAC → result is
  VERIFICATION_FAILED, confirmed state stays AAC; no path reports LDAC active.
- **Verification:** `CodecControlEngineTest.requestWithoutVerificationNeverConfirms`.

## OB-P12-REQ-003 — Codec support matrix

- **Description:** SBC, AAC, aptX, aptX HD, aptX Adaptive, aptX Lossless, LDAC, LC3
  each have an independent control-capability record.
- **Rationale:** Support for one codec must never imply support for another.
- **Priority:** Must
- **Dependencies:** OB-P12-REQ-001
- **Acceptance criteria:** aptX support does not imply aptX Adaptive; aptX Lossless
  independence; LC3 pinned to LE Audio.
- **Verification:** `CodecControlCapabilityTest.aptXVariantsAreIndependent`,
  `...lc3PinnedToLeAudio`.

## OB-P12-REQ-004 — Control capability dimensions

- **Description:** Extend the Phase 11 model with OBSERVABLE, SUPPORTED, SELECTABLE,
  CONFIGURABLE, VERIFIABLE as independent dimensions with evidence.
- **Rationale:** Phase 12 §4 requires the five-way split.
- **Priority:** Must
- **Dependencies:** OB-P12-REQ-001
- **Acceptance criteria:** All five dimensions independently settable; evidence
  attached; UNKNOWN preserved where unobserved.
- **Verification:** `CodecControlCapabilityTest`.

## OB-P12-REQ-005 — Selection vs configuration

- **Description:** Codec selection ("use LDAC instead of AAC") and codec
  configuration ("LDAC with quality mode X") are distinct operation types with
  distinct preconditions and validation.
- **Rationale:** Phase 12 §5; not every platform exposes either.
- **Priority:** Must
- **Dependencies:** OB-P12-REQ-004
- **Acceptance criteria:** `SELECT_CODEC` requires selectable; `CONFIGURE_CODEC`
  requires configurable; a platform exposing neither rejects both explicitly.
- **Verification:** `CodecOperationValidationTest`.

## OB-P12-REQ-006 — Operation model

- **Description:** A domain-level `CodecOperation` value: operationId (caller-supplied),
  target device, codec, requested configuration, expected transport, preconditions,
  timeout, verification strategy.
- **Rationale:** Operations are values, not callbacks; attributable and testable.
- **Priority:** Must
- **Dependencies:** OB-P12-REQ-005
- **Acceptance criteria:** `init` validates invariants (SELECT needs no config,
  CONFIGURE needs config; timeout positive); operationId never minted from
  wall-clock in core.
- **Verification:** `CodecOperationTest`.

## OB-P12-REQ-007 — Structured operation results

- **Description:** `CodecOperationResult` sealed hierarchy: ACCEPTED, APPLIED,
  VERIFIED, APPLIED_UNVERIFIED, REJECTED, UNSUPPORTED, NOT_SELECTABLE,
  NOT_CONFIGURABLE, NOT_OBSERVABLE, DEVICE_DISCONNECTED, PLATFORM_UNAVAILABLE,
  VERIFICATION_FAILED, TIMEOUT, FAILED.
- **Rationale:** The caller must determine what actually happened; never collapse
  to Exception/false.
- **Priority:** Must
- **Dependencies:** OB-P12-REQ-006
- **Acceptance criteria:** Every terminal state reachable in tests; failures carry
  structured reason, never bare exceptions.
- **Verification:** `CodecOperationResultTest`.

## OB-P12-REQ-008 — Requested vs confirmed state

- **Description:** The engine tracks requestedCodec/requestedConfiguration
  separately from observed and confirmed; confirmation requires verification.
- **Rationale:** Request ≠ reality; Phase 12 §8.
- **Priority:** Must
- **Dependencies:** OB-P12-REQ-007
- **Acceptance criteria:** After unverified apply, requested=LDAC, confirmed=AAC;
  confirmed only advances on VERIFIED.
- **Verification:** `CodecControlEngineTest.requestedVsConfirmedSeparation`.

## OB-P12-REQ-009 — AAC first-class

- **Description:** AAC identity, capability, observability, selectability,
  configurability modeled; configurability only where a legitimate mechanism exists.
- **Rationale:** Phase 12 §9.
- **Priority:** Must
- **Dependencies:** OB-P12-REQ-003
- **Acceptance criteria:** AAC capability record exists; Android resolver reports
  selectable=false/configurable=false with PLATFORM_LIMITATION evidence.
- **Verification:** `AacControlTest`.

## OB-P12-REQ-010 — LDAC dedicated architecture

- **Description:** LDAC detection, capability, selection, configuration, runtime
  verification architecture; quality modes (SOUND_QUALITY_PRIORITY, BALANCED,
  CONNECTION_QUALITY_PRIORITY, ADAPTIVE, UNKNOWN) as configuration fields.
- **Rationale:** Phase 12 §10.
- **Priority:** Must
- **Dependencies:** OB-P12-REQ-003
- **Acceptance criteria:** LDAC quality mode configurable in the model; no
  hard-coded bitrates (330/660/990) unless observed; validation rejects unknown modes.
- **Verification:** `LdacControlTest`.

## OB-P12-REQ-011 — aptX family normalization

- **Description:** aptX, aptX HD, aptX Adaptive, aptX Lossless as independent
  identities with independent capability records; no proprietary Qualcomm behavior
  without a legitimate mechanism.
- **Rationale:** Phase 12 §11.
- **Priority:** Must
- **Dependencies:** OB-P12-REQ-003
- **Acceptance criteria:** Four separate records; independence tests; no mechanism
  → NOT_CONFIGURABLE.
- **Verification:** `AptXFamilyControlTest`.

## OB-P12-REQ-012 — LC3 as LE Audio

- **Description:** LC3 bound to LE Audio; LC3-over-A2DP rejected as invalid
  configuration; unknown LE Audio state handled.
- **Rationale:** Phase 12 §12.
- **Priority:** Must
- **Dependencies:** OB-P12-REQ-003
- **Acceptance criteria:** Validation rejects LC3 + CLASSIC_A2DP transport;
  unavailable configuration → NOT_CONFIGURABLE.
- **Verification:** `Lc3ControlTest`.

## OB-P12-REQ-013 — SBC baseline

- **Description:** SBC support, availability, runtime state, configuration
  capability modeled; no assumption SBC is always active or always selectable.
- **Rationale:** Phase 12 §13.
- **Priority:** Must
- **Dependencies:** OB-P12-REQ-003
- **Acceptance criteria:** SBC capability record; selection/configurability
  capability-gated, not assumed.
- **Verification:** `SbcControlTest`.

## OB-P12-REQ-014 — Capability resolver

- **Description:** `CodecControlCapabilityResolver` determines canObserve,
  canSelect, canConfigure, canVerify per codec per device, considering API level,
  transport, profile, device, platform APIs, vendor protocol availability,
  previously verified capabilities, permission state, lifecycle state.
- **Rationale:** Phase 12 §14; every operation is capability-gated.
- **Priority:** Must
- **Dependencies:** OB-P12-REQ-004
- **Acceptance criteria:** Resolver interface with the four predicates; Android
  implementation returns false for select/configure/verify with evidence.
- **Verification:** `CodecControlCapabilityResolverTest`.

## OB-P12-REQ-015 — Public API first

- **Description:** Only official public Android APIs; no hidden APIs, reflection,
  shell, system properties, undocumented settings, vendor hacks. Limitations
  documented.
- **Rationale:** Phase 12 §15.
- **Priority:** Must
- **Dependencies:** OB-P12-REQ-014
- **Acceptance criteria:** Scope test bans reflection/hidden-API vocabulary in
  codec control code; Android adapter uses only public APIs.
- **Verification:** `CodecControlScopeTest`.

## OB-P12-REQ-016 — Vendor protocol path

- **Description:** If a verified device/vendor protocol supports codec control,
  integrate through the Protocol Abstraction Engine. No guessed commands, no
  undocumented writes, no transport bypass. If no verified protocol exists,
  codec control via vendor path is unavailable (READ-ONLY).
- **Rationale:** Phase 12 §16–17.
- **Priority:** Must
- **Dependencies:** Protocol Engine
- **Acceptance criteria:** Protocol integration point defined behind the engine;
  no vendor packets in codec engine; empty registry → control unavailable.
- **Verification:** `ProtocolCodecPathTest` (registry empty → NOT_CONFIGURABLE).

## OB-P12-REQ-017 — Configuration model

- **Description:** Normalized `CodecConfiguration`: codec, qualityMode, bitrate,
  sampleRate, bitDepth, channelMode, adaptiveMode — every field nullable or
  explicitly unknown; per-codec field support.
- **Rationale:** Phase 12 §18.
- **Priority:** Must
- **Dependencies:** OB-P12-REQ-005
- **Acceptance criteria:** All fields nullable/unknown; LDAC qualityMode=available
  + bitrate=unknown is valid.
- **Verification:** `CodecConfigurationTest`.

## OB-P12-REQ-018 — Validation rules

- **Description:** Before applying: validate codec capability, transport, device
  connection, field support, allowed value, operation support, verification
  capability. Reject invalid configuration before hardware/platform operations.
- **Rationale:** Phase 12 §19.
- **Priority:** Must
- **Dependencies:** OB-P12-REQ-017
- **Acceptance criteria:** Each rule rejects with a structured result; no
  platform call precedes validation.
- **Verification:** `CodecConfigurationValidationTest` (each rule).

## OB-P12-REQ-019 — Configuration constraints

- **Description:** Explicit constraints: supported quality modes, bitrates, sample
  rates, bit depths, channel modes. DEVICE_OBSERVED preferred over
  THEORETICAL_CODEC_SPEC.
- **Rationale:** Phase 12 §20.
- **Priority:** Should
- **Dependencies:** OB-P12-REQ-018
- **Acceptance criteria:** Constraint model exists; observed constraints override
  theoretical ones.
- **Verification:** `CodecConfigurationConstraintsTest`.

## OB-P12-REQ-020 — Transaction model

- **Description:** PRECHECK → REQUEST → APPLY → WAIT_FOR_STATE_UPDATE →
  RE-OBSERVE → VERIFY → COMMIT CONFIRMED STATE. Verification failure never
  commits requested state as confirmed.
- **Rationale:** Phase 12 §21.
- **Priority:** Must
- **Dependencies:** OB-P12-REQ-008
- **Acceptance criteria:** State machine implemented in `CodecControlEngine`;
  each phase observable in tests via scripted adapter.
- **Verification:** `CodecControlTransactionTest`.

## OB-P12-REQ-021 — Rollback

- **Description:** Where a previous confirmed configuration exists and the
  platform supports safe rollback: preserve previous, attempt rollback, verify.
  If impossible: mark uncertain, require refresh.
- **Rationale:** Phase 12 §22.
- **Priority:** Should
- **Dependencies:** OB-P12-REQ-020
- **Acceptance criteria:** Rollback path exists in the engine; rollback failure
  → uncertain state, never fake success.
- **Verification:** `CodecControlRollbackTest`.

## OB-P12-REQ-022 — Timeouts

- **Description:** Every control operation has a bounded timeout via structured
  coroutine timeouts; timeout → TIMEOUT, never SUCCESS; no Thread.sleep, no
  infinite waits.
- **Rationale:** Phase 12 §23.
- **Priority:** Must
- **Dependencies:** OB-P12-REQ-006
- **Acceptance criteria:** Scripted hanging adapter → TIMEOUT result; no
  thread blocking.
- **Verification:** `CodecControlTimeoutTest`.

## OB-P12-REQ-023 — Cancellation safety

- **Description:** Cancellation cancels pending operations, releases resources,
  never commits unverified state, leaves state consistent; CancellationException
  never swallowed.
- **Rationale:** Phase 12 §24.
- **Priority:** Must
- **Dependencies:** OB-P12-REQ-020
- **Acceptance criteria:** Cancelled operation → no confirmed-state change;
  CancellationException propagates.
- **Verification:** `CodecControlCancellationTest`.

## OB-P12-REQ-024 — Device disconnection

- **Description:** Disconnect during operation → DEVICE_DISCONNECTED; never
  report success; state becomes uncertain/stale.
- **Rationale:** Phase 12 §25.
- **Priority:** Must
- **Dependencies:** OB-P12-REQ-020
- **Acceptance criteria:** Scripted disconnect mid-transaction → structured
  DEVICE_DISCONNECTED; no false success.
- **Verification:** `CodecControlDisconnectTest`.

## OB-P12-REQ-025 — Concurrency

- **Description:** One codec-control transaction per device (per-device
  serialization); independent devices operate independently; concurrent LDAC/AAC
  requests → deterministic serialization or rejection, never a race.
- **Rationale:** Phase 12 §26.
- **Priority:** Must
- **Dependencies:** OB-P12-REQ-020
- **Acceptance criteria:** Per-device Mutex; concurrent ops on one device
  serialize; two devices proceed in parallel.
- **Verification:** `CodecControlConcurrencyTest`.

## OB-P12-REQ-026 — State invalidation

- **Description:** On disconnect, transport change, route change, or staleness:
  invalidate relevant runtime configuration; never display old confirmed state
  as current.
- **Rationale:** Phase 12 §27.
- **Priority:** Must
- **Dependencies:** OB-P12-REQ-008
- **Acceptance criteria:** Invalidation API marks confirmed state stale/unknown.
- **Verification:** `CodecControlInvalidationTest`.

## OB-P12-REQ-027 — Verification strategies

- **Description:** Explicit strategies: PLATFORM_OBSERVATION,
  DEVICE_PROTOCOL_READBACK, AUDIO_DEVICE_OBSERVATION, COMBINED, NONE. NONE →
  operation cannot claim VERIFIED.
- **Rationale:** Phase 12 §28.
- **Priority:** Must
- **Dependencies:** OB-P12-REQ-007
- **Acceptance criteria:** Strategy is part of the operation; NONE caps result
  at APPLIED_UNVERIFIED.
- **Verification:** `CodecVerificationStrategyTest`.

## OB-P12-REQ-028 — Verification levels

- **Description:** Maintain INFERRED/IMPLEMENTED/LAB_TESTED/HARDWARE_VERIFIED/
  PERSISTENCE_VERIFIED hierarchy; Phase 12 mechanisms are IMPLEMENTED, never
  auto-marked HARDWARE_VERIFIED.
- **Rationale:** Phase 12 §29.
- **Priority:** Must
- **Dependencies:** OB-P12-REQ-027
- **Acceptance criteria:** No code path marks HARDWARE_VERIFIED without evidence.
- **Verification:** Scope test / review.

## OB-P12-REQ-029 — Persistence honesty

- **Description:** Distinguish applied-for-session vs persisted-by-platform vs
  persisted-by-firmware vs UNKNOWN. No fake persistence in OmniBuds.
- **Rationale:** Phase 12 §30.
- **Priority:** Should
- **Dependencies:** OB-P12-REQ-008
- **Acceptance criteria:** Persistence dimension on confirmed state; default UNKNOWN.
- **Verification:** `CodecPersistenceTest`.

## OB-P12-REQ-030 — Configuration cache

- **Description:** Cache only observed/confirmed info with timestamps; invalidate
  on disconnect; never treat historical state as current.
- **Rationale:** Phase 12 §31.
- **Priority:** Should
- **Dependencies:** OB-P12-REQ-026
- **Acceptance criteria:** Cache entries timestamped; disconnect invalidates.
- **Verification:** `CodecControlCacheTest`.

## OB-P12-REQ-031 — Performance

- **Description:** No main-thread blocking; coroutines + structured concurrency;
  bounded operations; no busy polling, infinite retry, arbitrary sleeps,
  unnecessary scans/queries. Retries bounded, cancellation-safe, operation-aware.
- **Rationale:** Phase 12 §48.
- **Priority:** Must
- **Dependencies:** OB-P12-REQ-020
- **Acceptance criteria:** Injectabledispatchers; no Thread.sleep in codec code
  (scope test).
- **Verification:** `CodecControlScopeTest`.

## OB-P12-REQ-032 — Security

- **Description:** Verify target device, active session, capability, transport,
  operation support, command ownership before any control attempt. Unknown
  devices remain read-only.
- **Rationale:** Phase 12 §49.
- **Priority:** Must
- **Dependencies:** OB-P12-REQ-018
- **Acceptance criteria:** PRECHECK rejects unknown/ambiguous devices; security
  review passes.
- **Verification:** `CodecControlSecurityTest` + Agent 6 review.

## OB-P12-REQ-033 — Architectural invariants

- **Description:** The 10 invariants of §50 hold (no raw packets, no transport/
  protocol bypass, no media ownership, no invented support, no requested-as-
  confirmed, no UNKNOWN→UNSUPPORTED, no hidden APIs).
- **Rationale:** Phase 12 §50.
- **Priority:** Must
- **Dependencies:** All
- **Acceptance criteria:** Scope tests enforce vocabulary bans; architecture
  test enforces layering.
- **Verification:** `CodecControlScopeTest`, `DependencyDirectionTest`.

## OB-P12-REQ-034 — Android adapter honesty

- **Description:** The Android control adapter reports what public APIs can
  actually do. With no public codec-control API, select/configure resolve to
  NOT_SELECTABLE/NOT_CONFIGURABLE with PLATFORM_LIMITATION evidence.
- **Rationale:** Phase 12 §15; never manufacture a mechanism.
- **Priority:** Must
- **Dependencies:** OB-P12-REQ-014
- **Acceptance criteria:** Adapter returns structured unavailability; no
  reflection/hidden API.
- **Verification:** `AndroidCodecControlAdapterTest`.

## OB-P12-REQ-035 — Multi-device isolation

- **Description:** Device A → LDAC operation leaves Device B (AAC) unchanged and
  vice versa.
- **Rationale:** Phase 12 §38.
- **Priority:** Must
- **Dependencies:** OB-P12-REQ-025
- **Acceptance criteria:** Per-device state maps; cross-device assertions.
- **Verification:** `CodecControlMultiDeviceTest`.

## OB-P12-REQ-036 — Unknown preservation

- **Description:** UNKNOWN ≠ UNSUPPORTED, UNKNOWN ≠ FAILED, NOT_OBSERVABLE ≠
  UNSUPPORTED, NOT_CONFIGURABLE ≠ UNSUPPORTED.
- **Rationale:** Phase 12 §42.
- **Priority:** Must
- **Dependencies:** OB-P12-REQ-004
- **Acceptance criteria:** Distinct result/type assertions.
- **Verification:** `CodecControlUnknownTest`.

## OB-P12-REQ-037 — Android testing

- **Description:** Android adapter tests: supported path, unavailable path,
  permission limits, transport mismatch, unavailable device, unsupported codec/
  configuration, observation failure. Deterministic fakes; no hardware.
- **Rationale:** Phase 12 §43.
- **Priority:** Must
- **Dependencies:** OB-P12-REQ-034
- **Acceptance criteria:** All paths covered with fakes.
- **Verification:** `AndroidCodecControlAdapterTest`.

## OB-P12-REQ-038 — Documentation

- **Description:** The eight mandatory records plus architecture.md,
  codec-control.md, platform-limitations.md explaining selection, configuration,
  platform/vendor support, verification, requested vs confirmed, rollback,
  concurrency, timeout, persistence, Android limitations.
- **Rationale:** Phase 12 §44.
- **Priority:** Must
- **Dependencies:** All
- **Acceptance criteria:** All files present and accurate.
- **Verification:** Review.

## OB-P12-REQ-039 — No forbidden scope

- **Description:** No UI, no media interception, no decode/re-encode, no shell,
  no root, no system-file modification, no guessed vendor commands, no
  physical-device testing, Phase 13 not started.
- **Rationale:** Phase boundary.
- **Priority:** Must
- **Dependencies:** None
- **Acceptance criteria:** Scope tests + review confirm absence.
- **Verification:** `CodecControlScopeTest` + final audit.

## OB-P12-REQ-040 — Regression

- **Description:** All Phase 0–11 tests continue to pass; new tests added for
  Phase 12.
- **Rationale:** Phase stability.
- **Priority:** Must
- **Dependencies:** All
- **Acceptance criteria:** Full suite green.
- **Verification:** Full test run.
