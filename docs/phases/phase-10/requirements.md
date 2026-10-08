# Phase 10 — Audio Transport Engine: Requirements

**Status:** Authoritative for Phase 10 execution.
**Scope:** Platform-independent Bluetooth audio transport observation. No media-audio
processing, no codec configuration, no routing control, no production UI, no physical-device testing.
**Requirement ID scheme:** `OB-P10-REQ-001` … `OB-P10-REQ-025`.

Every requirement carries: ID, description, rationale, priority (Must/Should), dependencies,
acceptance criteria, verification method.

---

## OB-P10-REQ-001 — Audio transport taxonomy

- **Description:** The engine models five transport kinds: A2DP (classic), HFP, HSP, LE Audio,
  and UNKNOWN. LE Audio is architecturally distinct from A2DP — never a codec of it.
- **Rationale:** Android exposes multiple audio transports that coexist; collapsing them loses
  the ability to say which one the platform actually reported.
- **Priority:** Must
- **Dependencies:** None
- **Acceptance criteria:** `AudioTransportKind` has exactly `CLASSIC_A2DP, HFP, HSP, LE_AUDIO, UNKNOWN`;
  KDoc forbids LE-Audio-as-A2DP-codec treatment.
- **Verification:** `AudioConnectionStateTest`, `PhaseTenScopeTest`; code review.

## OB-P10-REQ-002 — HSP modeled separately from HFP

- **Description:** HSP is a distinct classic profile with its own proxy and state semantics.
- **Rationale:** Collapsing HSP into HFP would lose the ability to say which profile the
  platform reported.
- **Priority:** Must
- **Dependencies:** OB-P10-REQ-001
- **Acceptance criteria:** HSP exists in the taxonomy; the Android adapter reports HSP as
  UNKNOWN (not copied from HFP) because the platform exposes no HSP-specific state (ADR-P10-004).
- **Verification:** `AndroidAudioTransportSourceTest.hspIsReportedUnknownNeverCopiedFromHfp`.

## OB-P10-REQ-003 — No codec configuration

- **Description:** Phase 10 implements no codec negotiation, selection, priority change,
  bitrate/sample-rate forcing, or audio-quality modification. Codec state beyond the transport
  kind is Phase 11's subject.
- **Rationale:** Observation is not control; the phase boundary is the product contract.
- **Priority:** Must
- **Dependencies:** None
- **Acceptance criteria:** `PhaseTenScopeTest` bans codec-configuration vocabulary in
  `core/audio`; no codec fields on `AudioProfileState`.
- **Verification:** `PhaseTenScopeTest.noCodecConfigurationVocabulary`; architecture review.

## OB-P10-REQ-004 — Explicit connection-state vocabulary

- **Description:** Seven states: UNKNOWN, DISCONNECTED, CONNECTING, CONNECTED, ACTIVE, SUSPENDED,
  DISCONNECTING. CONNECTED ≠ ACTIVE ≠ PLAYING; UNKNOWN ≠ DISCONNECTED.
- **Rationale:** The three distinctions prevent the two most common fabrications: "connected
  therefore active" and "unread therefore disconnected".
- **Priority:** Must
- **Dependencies:** None
- **Acceptance criteria:** `AudioConnectionState` enum with documented semantics for each value.
- **Verification:** `AudioConnectionStateTest`.

## OB-P10-REQ-005 — Audio-device observation

- **Description:** The engine observes audio devices (platform id, type, product name, address
  where exposed, direction, active flag, source) separately from Bluetooth device identity.
- **Rationale:** An audio device is what Android's audio subsystem sees; the Bluetooth device is
  what the radio sees. Conflating them breaks when the two disagree.
- **Priority:** Must
- **Dependencies:** None
- **Acceptance criteria:** `ObservedAudioDevice` with platform-scoped id (never a persistent
  identity), nullable address, direction from `isSink`/`isSource`.
- **Verification:** `AudioStateMappingTest`; `AndroidAudioTransportSourceTest`.

## OB-P10-REQ-006 — Audio transport state model

- **Description:** Per-transport state combines transport kind, connection state, device
  association, direction, and active flag.
- **Rationale:** Consumers need one coherent per-transport picture, not three disagreeing sources.
- **Priority:** Must
- **Dependencies:** OB-P10-REQ-004, OB-P10-REQ-005
- **Acceptance criteria:** `AudioProfileState` + `AudioTransportSnapshot` cover the model.
- **Verification:** `AudioReconcilerTest`, `AudioTransportEngineTest`.

## OB-P10-REQ-007 — Explicit audio direction

- **Description:** Direction (OUTPUT, INPUT, BIDIRECTIONAL, UNKNOWN) is observed per transport/device,
  never hardcoded from the transport kind.
- **Rationale:** A2DP is commonly output, HFP is bidirectional, LE Audio varies — but the platform
  is the authority, and hardcoding the common case as an axiom breaks on the uncommon one.
- **Priority:** Must
- **Dependencies:** None
- **Acceptance criteria:** `AudioDirection` enum; mapping derives direction from
  `isSink`/`isSource`, never from device type.
- **Verification:** `AudioStateMappingTest.directionComesFromSinkSourceNotFromType`.

## OB-P10-REQ-008 — Audio device identity separation

- **Description:** `platformDeviceId` is a session-scoped handle, never a persistent user-device
  identity; product name is a display label, never an identity; devices are never merged on name
  similarity.
- **Rationale:** Treating a transient handle as identity corrupts cross-session records; merging
  on names merges distinct devices.
- **Priority:** Must
- **Dependencies:** OB-P10-REQ-005
- **Acceptance criteria:** KDoc prohibitions on `ObservedAudioDevice`; no persistent storage of
  platform ids.
- **Verification:** Code review; `PhaseTenScopeTest` (no persistence in audio package).

## OB-P10-REQ-009 — A2DP observation

- **Description:** Observe A2DP availability and connection state; associate with a device where the
  platform exposes it. Never configure codecs, intercept packets, or modify routing.
- **Rationale:** A2DP is Android's normal media path; OmniBuds stays outside the media data path.
- **Priority:** Must
- **Dependencies:** OB-P10-REQ-004
- **Acceptance criteria:** A2DP profile states flow into snapshots; scope test bans media interception.
- **Verification:** `AndroidAudioTransportSourceTest.profileStatesAreTranslated`; `PhaseTenScopeTest`.

## OB-P10-REQ-010 — HFP/HSP observation

- **Description:** Observe HFP/HSP profile availability, connection state, and the SCO audio state
  where the platform exposes it separately. Never activate SCO, never capture the microphone,
  never request RECORD_AUDIO for observation.
- **Rationale:** The HFP connection state and the SCO audio state are different facts; observing
  the latter must not activate it.
- **Priority:** Must
- **Dependencies:** OB-P10-REQ-004
- **Acceptance criteria:** `AudioProfileState.audioState` holds the SCO state separately;
  `RECORD_AUDIO` appears nowhere in the audio path.
- **Verification:** `AndroidAudioTransportSourceTest.headsetAudioStateIsTrackedSeparatelyFromProfileConnection`;
  `PhaseTenScopeTest.noMicrophonePermissionVocabulary`.

## OB-P10-REQ-011 — Read-only platform seam

- **Description:** The Android handle exposes only reads (profile states, device lists, change
  callbacks). No `setCommunicationDevice`, no SCO start/stop, no routing change exists in the
  seam.
- **Rationale:** A write method in the seam would be an unauthorised capability behind an
  innocuous name.
- **Priority:** Must
- **Dependencies:** None
- **Acceptance criteria:** `AudioTransportHandle` has no write methods; scope test bans routing
  vocabulary in core.
- **Verification:** `PhaseTenScopeTest.noRoutingControlVocabulary`; code review.

## OB-P10-REQ-012 — No framework objects in core

- **Description:** Core deals in domain types; `AudioDeviceInfo` and Bluetooth constants are
  translated in exactly one mapping module in the platform layer.
- **Rationale:** The translation split is what makes the engine testable without a radio.
- **Priority:** Must
- **Dependencies:** None
- **Acceptance criteria:** No `android.*` imports in `core/audio`; mapping is the only translator.
- **Verification:** `PhaseTenScopeTest.noAndroidImportsInCoreAudio`; `DependencyDirectionTest`.

## OB-P10-REQ-013 — Single authoritative snapshot

- **Description:** Exactly one `StateFlow<AudioTransportSnapshot>`; the engine owns it, consumers
  observe it, no second store keeps a rival version.
- **Rationale:** Two stores disagree; one store with reconciliation is the product.
- **Priority:** Must
- **Dependencies:** OB-P10-REQ-006
- **Acceptance criteria:** Engine declares the single `MutableStateFlow`; scope test forbids others.
- **Verification:** `PhaseTenScopeTest.engineOwnsExactlyOneSnapshotFlow`; `AudioTransportEngineTest`.

## OB-P10-REQ-014 — No transport priority

- **Description:** The engine defines no LE Audio > A2DP > HFP priority. Active transport is
  elected only on unambiguous platform evidence; otherwise null with a diagnostic.
- **Rationale:** Priority without platform evidence is invention.
- **Priority:** Must
- **Dependencies:** OB-P10-REQ-006
- **Acceptance criteria:** `AudioReconciler` rule 4; ambiguous actives elect nothing.
- **Verification:** `AudioReconcilerTest.rule4_ambiguousActiveTransportsElectNothing`.

## OB-P10-REQ-015 — Multiple audio devices

- **Description:** The snapshot lists all observed audio devices; the same device may expose
  multiple profiles; devices are tracked independently.
- **Rationale:** Earbuds A on A2DP + headset B on HFP is normal, not an edge case.
- **Priority:** Must
- **Dependencies:** OB-P10-REQ-005
- **Acceptance criteria:** `devices` is a list, never collapsed; per-profile states are independent.
- **Verification:** `AudioReconcilerTest`; `AudioTransportEngineTest.deviceEventsAreMergedIntoSnapshots`.

## OB-P10-REQ-016 — State reconciliation

- **Description:** Profile state, device state, and capabilities are reconciled by five rules:
  capability gate, conflict preservation (UNKNOWN + diagnostic), impossible-combination repair,
  evidence-only active election, stale-callback tolerance. The reconciler is a pure function.
- **Rationale:** Sources update at different times and contradict; guessing hides the conflict.
- **Priority:** Must
- **Dependencies:** OB-P10-REQ-006
- **Acceptance criteria:** All five rules implemented and individually tested; reconciler reads no
  clock, no randomness.
- **Verification:** `AudioReconcilerTest` (9 tests); `PhaseTenScopeTest.reconcilerStaysAPureFunction`.

## OB-P10-REQ-017 — Lifecycle safety

- **Description:** Explicit STOPPED→STARTING→OBSERVING→STOPPING→STOPPED lifecycle; repeated start/stop
  are no-op successes; no leaked callbacks or profile proxies; cancellation-safe; deterministic
  unregistration.
- **Rationale:** Leaked platform registrations are the failure mode of sloppy observation.
- **Priority:** Must
- **Dependencies:** OB-P10-REQ-013
- **Acceptance criteria:** Lifecycle enum with transition guards; engine stop cancels and joins;
  `AutoCloseable` callback registration is idempotent.
- **Verification:** `AudioTransportEngineTest` (lifecycle tests);
  `AndroidAudioTransportSourceTest.deviceObservationRegistersAndUnregistersDeterministically`.

## OB-P10-REQ-018 — Audio/control transport separation

- **Description:** The audio plane (A2DP/HFP/LE Audio) and the control plane (GATT/RFCOMM) are
  tracked independently; neither is ever inferred from the other.
- **Rationale:** A GATT control channel says nothing about the audio transport and vice versa.
- **Priority:** Must
- **Dependencies:** None
- **Acceptance criteria:** `AudioControlTopology` pins both planes; `verify()` requires both known
  independently.
- **Verification:** `AudioControlTopologyTest`.

## OB-P10-REQ-019 — LE Audio API-level guards

- **Description:** LE Audio observation requires API 33+; below that it is UNAVAILABLE/UNKNOWN,
  never inferred. `BluetoothLeAudio` references live in a class loaded only under the guard.
- **Rationale:** Unconditional references risk `VerifyError` on older phones; inference from A2DP
  state is fabrication.
- **Priority:** Must
- **Dependencies:** OB-P10-REQ-001
- **Acceptance criteria:** `leAudioSupport(apiLevel)` pure function; `LeAudioApi33` isolated with
  init-time check; minSdk stays 26.
- **Verification:** `LeAudioSupportTest`; code review of the guard.

## OB-P10-REQ-020 — Minimal permission surface

- **Description:** Phase 10 adds no permissions. It reuses the Phase 2 Bluetooth permission model
  (BLUETOOTH_CONNECT for profile reads). RECORD_AUDIO is never requested or declared for
  observation.
- **Rationale:** Requesting microphone access to observe transport state is a privacy defect.
- **Priority:** Must
- **Dependencies:** None
- **Acceptance criteria:** No new `<uses-permission>` in the manifest; scope test bans RECORD_AUDIO.
- **Verification:** `PhaseTenScopeTest.noMicrophonePermissionVocabulary`;
  `DependencyDirectionTest.platformManifestDeclaresNothingUnjustified`.

## OB-P10-REQ-021 — Privacy: no audio capture

- **Description:** No audio is captured, stored, transmitted, or logged. Device identifiers are
  minimized; diagnostics redact addresses.
- **Rationale:** Observation is metadata-only by product principle.
- **Priority:** Must
- **Dependencies:** OB-P10-REQ-011
- **Acceptance criteria:** Scope test bans capture vocabulary; diagnostics carry codes and
  platform ids, not raw audio or addresses.
- **Verification:** `PhaseTenScopeTest.noMediaCaptureVocabulary`; security review (risk register).

## OB-P10-REQ-022 — Performance: callbacks, not polling

- **Description:** Observation is callback-driven; snapshots recompute only on input change; no
  timer polling; no main-thread blocking; bounded diagnostics; Flow-based with deterministic cleanup.
- **Rationale:** A lightweight observer must not become a battery or memory cost.
- **Priority:** Should
- **Dependencies:** OB-P10-REQ-017
- **Acceptance criteria:** Engine exposes `refreshProfiles()` for host-triggered reads only; no
  scheduled polling in the engine; diagnostics bounded at 8.
- **Verification:** Code review; `AudioTransportEngineTest`.

## OB-P10-REQ-023 — Structured errors

- **Description:** Audio failures use `OmniBudsErrorCategory` additions: `AUDIO_OBSERVATION_FAILED`
  (safe to retry — reads are side-effect-free), `LE_AUDIO_UNAVAILABLE` (never retry),
  `AUDIO_STATE_CONFLICT` (never retry; session untouched). No raw Android exceptions cross into core.
- **Rationale:** Callers need exactly the project's error vocabulary, not platform exceptions.
- **Priority:** Must
- **Dependencies:** None
- **Acceptance criteria:** Three categories added with retry classes and session semantics; existing
  exhaustive category tests updated.
- **Verification:** `OmniBudsErrorCategoryTest`; `AudioTransportEngineTest.failedStartLandsBackInStoppedWithAnError`.

## OB-P10-REQ-024 — Android 14/15 awareness

- **Description:** The design tolerates Android 14 LE Audio routing changes and Android 15 broadcast
  (`TYPE_BLE_BROADCAST`) devices appearing without profile connections.
- **Rationale:** New platform surfaces must not break observation or be misread as connections.
- **Priority:** Should
- **Dependencies:** OB-P10-REQ-016
- **Acceptance criteria:** Unknown device types map to `AudioDeviceType.UNKNOWN`, not to a guessed
  transport; reconciliation handles device-without-profile.
- **Verification:** `AudioStateMappingTest` (unknown type); `AudioReconcilerTest.rule2`.

## OB-P10-REQ-025 — Documentation

- **Description:** The eight mandatory Phase 10 records exist and are accurate: requirements,
  design, specs, task-list, test-plan, validation, decisions, risk-register.
- **Rationale:** The phase is not complete until its reasoning is recorded.
- **Priority:** Must
- **Dependencies:** All above
- **Acceptance criteria:** `docs/phases/phase-10/` contains all eight files; validation records
  actual test counts and the Gradle/sandbox limitation honestly.
- **Verification:** File review; this checklist.
