# Phase 10 — Audio Transport Engine: Task List

All tasks complete. Checked entries are done and validated; the list is retained as the
execution record.

## Domain model (core)

- [x] `AudioConnectionState` — 7-state vocabulary with CONNECTED≠ACTIVE≠PLAYING semantics
- [x] `AudioDirection` — OUTPUT/INPUT/BIDIRECTIONAL/UNKNOWN, observed not hardcoded
- [x] `AudioDeviceType` — platform device types in domain vocabulary
- [x] `ObservedAudioDevice` — session-scoped id, nullable address, provenance source
- [x] `ProfileAvailability` — AVAILABLE/UNAVAILABLE/UNKNOWN
- [x] `AudioProfileState` — per-profile state with separate HFP audioState
- [x] `AudioTransportKind` extended with `HSP` (reuse, not a parallel taxonomy)
- [x] `AudioPlatformCapabilities` — pure-data platform capability record
- [x] `AudioDiagnostic` + bounded diagnostics on the snapshot
- [x] `AudioTransportSnapshot` — immutable, schema-versioned, single authoritative shape

## Engine

- [x] `AudioReconciler` — pure function implementing the 5 rules
- [x] `AudioProfileSource` / `AudioDeviceSource` ports + `AudioDeviceEvent` sealed hierarchy
- [x] `AudioObserverLifecycle` — STOPPED/STARTING/OBSERVING/STOPPING with transition guards
- [x] `AudioTransportEngine` — snapshot flow, lifecycle, SupervisorJob scope, injected dispatcher
- [x] `LeAudioSupport` — pure `leAudioSupport(apiLevel)` guard; minSdk stays 26
- [x] `AudioControlTopology` — audio/control plane separation, compiler-enforced
- [x] Error categories: `AUDIO_OBSERVATION_FAILED`, `LE_AUDIO_UNAVAILABLE`, `AUDIO_STATE_CONFLICT`

## Platform adapters

- [x] `AudioTransportHandle` — read-only seam, session-based profile reads, `AutoCloseable` callbacks
- [x] `RawAudioDevice` / `RawProfileRead` — primitives cross the seam, never framework objects
- [x] `audio/mapping` — the single translator (raw ints → domain)
- [x] `AndroidAudioTransportSource` — implements both ports; permission-first; HSP UNKNOWN
- [x] `LeAudioHandle` + `LeAudioApi33` — API-33-isolated, init-time guard, deterministic release
- [x] `SystemAudioTransportHandle` — thin framework edge (bind/read/release per call)

## Tests

- [x] Core: `AudioConnectionStateTest`, `AudioReconcilerTest` (9), `LeAudioSupportTest`,
  `AudioControlTopologyTest`, `AudioTransportEngineTest` (11), `PhaseTenScopeTest` (8)
- [x] Existing `OmniBudsErrorCategoryTest` updated for the 3 new categories
- [x] Android: `AudioStateMappingTest` (7), `AndroidAudioTransportSourceTest` (8)
- [x] Full suite: 774 core + 116 android = **890 tests, all passing**

## Documentation

- [x] `docs/phases/phase-10/`: requirements, design, specs, task-list, test-plan, validation,
  decisions, risk-register
- [x] Index updates: `docs/README.md`, `docs/decisions/README.md`, `docs/MASTER-CONTEXT.md`

## Deferred (not Phase 10)

- [ ] Physical-device verification of the system handle (needs hardware; explicitly deferred)
- [ ] Codec discovery/configuration (Phase 11)
- [ ] Production UI / app icon (later phase; logo asset recorded in memory)
