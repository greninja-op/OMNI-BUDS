# Phase 13 — Audio Quality & Negotiation State: Requirements

**Status:** Authoritative for Phase 13 execution.
**Scope:** Runtime audio quality & negotiation state engine — observation,
normalization, state, and analytics. No media interception, decoding,
re-encoding, or modification. No UI. No physical-device testing.
**Requirement ID scheme:** `OB-P13-REQ-001` … `OB-P13-REQ-035`.

---

## OB-P13-REQ-001 — Unified AudioQualityState

- **Description:** A unified runtime model combining transport, active device,
  codec, codec state, sample rate, bit depth, bitrate, channel mode, quality
  mode, adaptive state, negotiation state, evidence, observability, freshness,
  timestamp. All fields independently observable/unknown.
- **Rationale:** One answer to "what is the audio state right now?"
- **Priority:** Must
- **Acceptance criteria:** `AudioQualityState` exists; every parameter field
  nullable/unknown; no field inferred from another.
- **Verification:** `AudioQualityStateTest`.

## OB-P13-REQ-002 — State distinctions

- **Description:** Distinguish SUPPORTED/AVAILABLE/ENABLED/NEGOTIATED/ACTIVE/
  CONFIGURABLE (codec) and CONNECTED/AUDIO_AVAILABLE/ROUTED/NEGOTIATING/
  NEGOTIATED/ACTIVE/DISCONNECTED (transport/route).
- **Rationale:** Connected ≠ active; supported ≠ negotiated.
- **Priority:** Must
- **Acceptance criteria:** `Bluetooth CONNECTED + route NOT_ACTIVE + codec
  UNKNOWN` is representable and valid.
- **Verification:** `AudioQualityDistinctionsTest`.

## OB-P13-REQ-003 — Negotiation state machine

- **Description:** Normalized machine: UNKNOWN, IDLE, PREPARING, NEGOTIATING,
  NEGOTIATED, ACTIVE, FAILED, DISCONNECTED, STALE. Deterministic transitions;
  invalid transitions rejected or safely handled.
- **Rationale:** Negotiation is a process with a lifecycle.
- **Priority:** Must
- **Acceptance criteria:** Legal-transition table; `NEGOTIATING → NEGOTIATED →
  ACTIVE`; `NEGOTIATING → FAILED`; any-active → `DISCONNECTED`; `ACTIVE →
  STALE`.
- **Verification:** `NegotiationStateMachineTest` (all transitions + invalid).

## OB-P13-REQ-004 — Negotiation ≠ active

- **Description:** `negotiatedCodec` and `activeCodec` are separate facts.
  `negotiatedCodec = LDAC, activeCodec = UNKNOWN` is valid.
- **Rationale:** Negotiation success ≠ media flowing.
- **Priority:** Must
- **Acceptance criteria:** Separate fields; no automatic promotion.
- **Verification:** `NegotiationVsActiveTest`.

## OB-P13-REQ-005 — Negotiation events

- **Description:** Immutable events for meaningful transitions:
  `AudioTransportDetected`, `AudioDeviceAvailable`, `AudioRouteChanged`,
  `CodecNegotiationStarted/Completed`, `CodecChanged`, `CodecParametersChanged`,
  `AudioBecameActive/Inactive`, `AudioDeviceDisconnected`,
  `AudioStateBecameStale`, `NegotiationFailed`.
- **Rationale:** Externally relevant changes as values.
- **Priority:** Must
- **Acceptance criteria:** Sealed event hierarchy; no events for trivial
  internals.
- **Verification:** `NegotiationEventTest`.

## OB-P13-REQ-006 — Negotiation sessions

- **Description:** Bounded session: sessionId, deviceId, transport, startedAt,
  completedAt, negotiatedCodec, negotiatedConfiguration, finalState,
  failureReason, evidence. Terminated/invalidated on disconnect; never lives
  forever.
- **Rationale:** Negotiation is scoped in time.
- **Priority:** Must
- **Acceptance criteria:** Session lifecycle; disconnect → terminated.
- **Verification:** `NegotiationSessionTest`.

## OB-P13-REQ-007 — Sample rate state

- **Description:** Observed, supported, and unknown sample rates as separate
  facts. Never infer active rate from capability.
- **Rationale:** `supported=[44100,48000,96000], active=UNKNOWN` is valid.
- **Priority:** Must
- **Acceptance criteria:** Separate fields; no inference.
- **Verification:** `AudioParameterTest`.

## OB-P13-REQ-008 — Bit depth state

- **Description:** Supported vs observed bit depth. Never default to 16-bit.
- **Rationale:** Unexposed ≠ 16-bit.
- **Priority:** Must
- **Acceptance criteria:** Null/unknown preserved.
- **Verification:** `AudioParameterTest`.

## OB-P13-REQ-009 — Bitrate state

- **Description:** Supported, configured, observed, adaptive bitrates kept
  separate. Never derive bitrate from codec identity.
- **Rationale:** `LDAC + bitrate=UNKNOWN` is valid.
- **Priority:** Must
- **Acceptance criteria:** Four separate fields; no identity-derived bitrate.
- **Verification:** `AudioParameterTest`.

## OB-P13-REQ-010 — Channel state

- **Description:** MONO/STEREO/UNKNOWN. Never assume stereo; never derive from
  two earbuds.
- **Rationale:** Physical form ≠ channel mode.
- **Priority:** Must
- **Acceptance criteria:** UNKNOWN default preserved.
- **Verification:** `AudioParameterTest`.

## OB-P13-REQ-011 — Quality mode

- **Description:** SOUND_QUALITY_PRIORITY/BALANCED/CONNECTION_QUALITY_PRIORITY/
  ADAPTIVE/UNKNOWN where observable. Distinguish configured vs observed active.
- **Rationale:** Configured ≠ active.
- **Priority:** Must
- **Acceptance criteria:** Two separate fields.
- **Verification:** `AudioParameterTest`.

## OB-P13-REQ-012 — Adaptive state

- **Description:** FIXED/ADAPTIVE/UNKNOWN. Only ADAPTIVE with genuine evidence;
  never inferred from codec capability.
- **Rationale:** Capability ≠ behavior.
- **Priority:** Must
- **Acceptance criteria:** Evidence-gated.
- **Verification:** `AudioParameterTest`.

## OB-P13-REQ-013 — Quality profile

- **Description:** Immutable `QualityProfile`: codec, transport, sample rate,
  bit depth, bitrate, channel mode, quality mode, adaptive mode at a point in
  time.
- **Rationale:** Point-in-time snapshot for diagnostics/history.
- **Priority:** Must
- **Acceptance criteria:** Immutable value; timestamped.
- **Verification:** `QualityProfileTest`.

## OB-P13-REQ-014 — Current vs historical

- **Description:** CURRENT vs HISTORICAL strictly separated. A previous LDAC
  session never displays as current after reconnect until re-confirmed.
- **Rationale:** History must not masquerade as now.
- **Priority:** Must
- **Acceptance criteria:** Reconnect → fresh state; history flagged historical.
- **Verification:** `HistoricalStateTest`.

## OB-P13-REQ-015 — Freshness

- **Description:** CURRENT/STALE/UNKNOWN per runtime fact. Stale preserves the
  observation but blocks "current" treatment.
- **Rationale:** `codec=LDAC, freshness=STALE` must be expressible.
- **Priority:** Must
- **Acceptance criteria:** Independent freshness; stale ≠ deleted.
- **Verification:** `FreshnessTest`.

## OB-P13-REQ-016 — Evidence

- **Description:** Reuse Phase 11 evidence. Every quality-state claim has
  provenance. Never upgrade INFERRED → OBSERVED without evidence.
- **Rationale:** Provenance is the honesty mechanism.
- **Priority:** Must
- **Acceptance criteria:** Evidence on all claims; confidence never inflates.
- **Verification:** `EvidencePreservationTest`.

## OB-P13-REQ-017 — Observability

- **Description:** OBSERVABLE/PARTIALLY_OBSERVABLE/NOT_OBSERVABLE/UNKNOWN per
  fact. NOT_OBSERVABLE ≠ UNSUPPORTED ≠ failure.
- **Rationale:** Lack of visibility is information, not error.
- **Priority:** Must
- **Acceptance criteria:** Four states; no conflation.
- **Verification:** `ObservabilityTest`.

## OB-P13-REQ-018 — Deterministic resolver

- **Description:** `AudioQualityResolver`: (transport state, device state, codec
  snapshot, runtime state, control state, route state, events) →
  `AudioQualityState`. No invention; explicit precedence on conflicts.
- **Rationale:** One deterministic normalization point.
- **Priority:** Must
- **Acceptance criteria:** Pure function; documented precedence.
- **Verification:** `AudioQualityResolverTest`.

## OB-P13-REQ-019 — Source precedence

- **Description:** Documented hierarchy, e.g. verified runtime observation >
  platform runtime metadata > verified device protocol > capability database >
  static inference > UNKNOWN. Vendor data never auto-above platform evidence.
- **Rationale:** Conflicts need a rule, not a guess.
- **Priority:** Must
- **Acceptance criteria:** Documented + tested.
- **Verification:** `SourcePrecedenceTest`.

## OB-P13-REQ-020 — Conflict resolution

- **Description:** Current runtime observation beats stale historical data
  (e.g. Android=AAC vs stale protocol=LDAC → AAC). Conflicts marked, never
  silently resolved.
- **Rationale:** Fresh truth wins; history stays history.
- **Priority:** Must
- **Acceptance criteria:** Conflict flag; precedence applied.
- **Verification:** `ConflictResolutionTest`.

## OB-P13-REQ-021 — Quality StateFlow

- **Description:** `StateFlow<Map<DeviceIdentity, AudioQualityState>>`;
  negotiation state, codec/route changes, stale transitions observable.
  Equality-aware dedup: no emission without meaningful change.
- **Rationale:** Reactive consumers, no spam.
- **Priority:** Must
- **Acceptance criteria:** Dedup tested; lifecycle-safe.
- **Verification:** `AudioQualityFlowTest`.

## OB-P13-REQ-022 — Debouncing

- **Description:** Controlled debouncing only where justified (rapid transient
  Android events). Documented: why, duration, scope, rationale. No arbitrary
  delays.
- **Rationale:** Stability without hiding truth.
- **Priority:** Should
- **Acceptance criteria:** Documented policy; tests.
- **Verification:** `DebounceTest` / docs.

## OB-P13-REQ-023 — Negotiation timeline

- **Description:** Bounded, coherent timeline reconstruction
  (connected → device → negotiating → codec → route → parameters).
- **Rationale:** Diagnostics need sequence.
- **Priority:** Should
- **Acceptance criteria:** Bounded history; chronological.
- **Verification:** `NegotiationTimelineTest`.

## OB-P13-REQ-024 — Change detection

- **Description:** Codec changes (AAC→LDAC, LDAC→UNKNOWN, …) and parameter
  changes (48→96 kHz) detected; identical re-observations are not changes;
  UNKNOWN→value and value→UNKNOWN are meaningful only per policy.
- **Rationale:** Signal, not noise.
- **Priority:** Must
- **Acceptance criteria:** Change predicates; documented UNKNOWN policy.
- **Verification:** `ChangeDetectionTest`.

## OB-P13-REQ-025 — Route integration

- **Description:** Route changes via Phase 10 abstractions; codec state follows
  the correct device.
- **Rationale:** Route determines which device's state matters.
- **Priority:** Must
- **Acceptance criteria:** Route events update the right device.
- **Verification:** `RouteIntegrationTest`.

## OB-P13-REQ-026 — Multi-device isolation

- **Description:** Device A (LDAC) / Device B (AAC) isolated; disconnect A →
  B unaffected.
- **Rationale:** No global codec state.
- **Priority:** Must
- **Acceptance criteria:** Per-device maps; isolation tests.
- **Verification:** `MultiDeviceQualityTest`.

## OB-P13-REQ-027 — LE Audio / Classic / HFP separation

- **Description:** LE Audio + LC3 separate from A2DP; LC3 never via A2DP;
  HFP/HSP (communication) distinct from A2DP (media). Unobservable → UNKNOWN.
- **Rationale:** Transports are not interchangeable.
- **Priority:** Must
- **Acceptance criteria:** Separation enforced in types/tests.
- **Verification:** `TransportSeparationTest`.

## OB-P13-REQ-028 — No quality scores

- **Description:** No `LDAC=10, AAC=8` scoring. Facts only; scoring deferred to
  a future architecture decision.
- **Rationale:** Identity ≠ quality.
- **Priority:** Must
- **Acceptance criteria:** No scoring vocabulary in code (scope test).
- **Verification:** `AudioQualityScopeTest`.

## OB-P13-REQ-029 — Android integration

- **Description:** Build on existing Android audio abstractions; public APIs
  only; unexposed → UNKNOWN. No hidden APIs, reflection, or shell.
- **Rationale:** Legitimate observation only.
- **Priority:** Must
- **Acceptance criteria:** Adapter reuses Phase 10/11 sources.
- **Verification:** `AndroidAudioQualityTest`.

## OB-P13-REQ-030 — Architecture

- **Description:** `Android APIs → Transport Engine → Codec Capability Engine →
  Codec Runtime State → Negotiation Engine → Quality Resolver →
  AudioQualityState → Consumers`. Domain stays Android-independent.
- **Rationale:** Layered, testable.
- **Priority:** Must
- **Acceptance criteria:** `DependencyDirectionTest` passes.
- **Verification:** Architecture test.

## OB-P13-REQ-031 — Error model

- **Description:** Reuse structured errors; UNKNOWN is usually the right answer,
  not an exception.
- **Rationale:** Uncertainty is a state.
- **Priority:** Must
- **Acceptance criteria:** New categories only if unmappable.
- **Verification:** Review.

## OB-P13-REQ-032 — Performance

- **Description:** Event-driven, immutable, deterministic, bounded history,
  lifecycle-aware. No polling, scans, unlimited emissions, or duplicate work.
- **Rationale:** Lightweight engine.
- **Priority:** Must
- **Acceptance criteria:** No polling in code (scope test); bounded buffers.
- **Verification:** `AudioQualityScopeTest` + review.

## OB-P13-REQ-033 — Logging

- **Description:** Log meaningful transitions (negotiation start/result, codec/
  parameter/route changes, stale, conflicts, failures) via `OmniBudsLogger`.
  Never log credentials/tokens/secrets; minimize identifiers.
- **Rationale:** Observability of the observer.
- **Priority:** Should
- **Acceptance criteria:** Transition logging; no sensitive data.
- **Verification:** Review.

## OB-P13-REQ-034 — Documentation

- **Description:** Eight mandatory records + `architecture.md` +
  `negotiation-model.md`, covering the state machine, quality model,
  precedence, conflicts, freshness, observability, multi-device, transports,
  parameters, events.
- **Rationale:** The model must be understandable.
- **Priority:** Must
- **Acceptance criteria:** All files present and accurate.
- **Verification:** Review.

## OB-P13-REQ-035 — Regression & boundaries

- **Description:** All Phase 0–12 tests pass; no UI; no media path changes;
  no physical-device testing; Phase 14 not started.
- **Rationale:** Phase boundary.
- **Priority:** Must
- **Acceptance criteria:** Full suite green; final audit.
- **Verification:** Full test run + scope tests.
