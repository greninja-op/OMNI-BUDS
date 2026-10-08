# Phase 10 — Audio Transport Engine: Design

## 1. Problem

OmniBuds must understand which Bluetooth audio transport the phone is using — A2DP, HFP/HSP,
or LE Audio — without becoming an audio application. Android owns the media path; OmniBuds
observes transport state so later phases (codec discovery, quality analysis) have a truthful
foundation. The design challenge: three platform subsystems (Bluetooth profile proxies,
AudioManager device callbacks, platform capabilities) report overlapping state at different
times, and they can contradict each other.

## 2. Architecture

```
platform/android                          :core
┌─────────────────────────────┐          ┌──────────────────────────────────┐
│ SystemAudioTransportHandle  │ raw      │ AudioTransportEngine             │
│  - binds proxies per read   │ ──────── │  - owns StateFlow<Snapshot>      │
│  - AudioDeviceCallback      │  prim-   │  - lifecycle START/STOP           │
│  - AudioManager.getDevices  │ itives   │  - merges profile + device ports  │
├─────────────────────────────┤          ├──────────────────────────────────┤
│ AndroidAudioTransportSource │ domain   │ AudioReconciler (pure fn)        │
│  - implements core ports    │ ──────── │  - 5 reconciliation rules        │
│  - permission-first reads   │  types   │  - conflict → UNKNOWN + diagn.   │
├─────────────────────────────┤          ├──────────────────────────────────┤
│ audio/mapping               │          │ Domain: kinds, states, devices,  │
│  - ONLY translator          │          │ snapshot, lifecycle, topology,   │
├─────────────────────────────┤          │ LE Audio guard, error categories │
│ LeAudioApi33 (API 33+ only) │          └──────────────────────────────────┘
│  - isolated class loading   │
└─────────────────────────────┘
```

### 2.1 Hexagonal ports

`:core` defines two ports; `platform/android` answers them:

- `AudioProfileSource` — "what do the Bluetooth profiles report" (`readProfileStates`,
  `readActiveTransportHint`).
- `AudioDeviceSource` — "which audio devices exist" (`readDevices`, `observeDeviceEvents`).

Two ports, not one, because the answers come from different subsystems that fail
independently — and the reconciler's job is to notice when they disagree.

### 2.2 The handle seam

`AudioTransportHandle` is the narrow framework seam. It speaks raw values (profile ints,
`RawAudioDevice` primitives) and is read-only by construction: no routing writes exist in
the interface. Profile reads are single binder sessions (bind → read → release); proxies are
never held across reads, so there is no IPC binding to leak.

`LeAudioHandle` is separate because `BluetoothLeAudio` does not exist below API 33 —
an unconditional reference risks `VerifyError` on older phones (ADR-P10-005).

### 2.3 The engine

`AudioTransportEngine` owns the single `StateFlow<AudioTransportSnapshot>`, the lifecycle
state machine, and a private `SupervisorJob` scope. Observation merges:

1. Initial read: profiles + devices + capabilities → reconcile → publish.
2. Device events: each `AudioDeviceEvent` → re-reconcile → publish.
3. Host-triggered `refreshProfiles()`: the engine never polls by itself.

A failing device-event flow is caught, recorded as a diagnostic, and observation continues —
a broken callback must not kill the engine. `stop()` cancels the scope and joins it, so no
callback survives teardown.

### 2.4 The reconciler (pure function)

Five rules, applied in order (OB-P10-REQ-016):

1. **Capability gate** — a profile the platform cannot offer (LE Audio on API < 33) is forced
   to UNAVAILABLE/UNKNOWN, whatever a stale callback says. Applies only once capabilities are
   actually determined.
2. **Conflict preservation** — profile CONNECTED with no matching device (or vice versa) →
   UNKNOWN + diagnostic. The reconciler never picks the "more plausible" source.
3. **Impossible-combination repair** — ACTIVE with no active device → downgraded to CONNECTED
   + diagnostic. The connection evidence was real; the activity claim was not.
4. **Evidence-only active election** — `activeTransport` is set only for exactly one ACTIVE
   transport consistent with an active device. No priority order exists.
5. **Stale-callback tolerance** — a device record older than 30s contradicting a fresh profile
   read is dropped. Freshness is the only allowed tiebreaker (it is evidence, not preference).

Pure: all inputs are parameters (timestamps included); no clock reads, no randomness.

### 2.5 Lifecycle

```
STOPPED --start--> STARTING --ready--> OBSERVING --stop--> STOPPING --done--> STOPPED
```

- Repeated start/stop are no-op successes (no duplicate callbacks).
- Failed start returns to STOPPED with the error in the outcome — never half-registered.
- No FAILED state (failure is an `OperationOutcome`, ADR-P1-004).

### 2.6 Audio/control separation

`AudioControlTopology` pins the two planes side by side: audio transport
(`AudioTransportKind`) and control transport (`TransportKind`). `verify()` is true only when
both are independently known. LE Audio as an audio transport and `LE_AUDIO` as a control
channel are different types — the compiler enforces the separation.

## 3. Key decisions (see decisions.md)

- **ADR-P10-001**: Observation-only engine; Android owns the media path.
- **ADR-P10-002**: Reuse `AudioTransportKind`, extend with HSP — no parallel taxonomy.
- **ADR-P10-003**: Reconciler is a pure function; conflicts become UNKNOWN + diagnostics.
- **ADR-P10-004**: HSP reported UNKNOWN on Android (no HSP-specific platform state).
- **ADR-P10-005**: `LeAudioApi33` isolated; never loaded below API 33.
- **ADR-P10-006**: `AutoCloseable` (not `PlatformRegistration`) for the audio callback —
  `awaitClose` cannot suspend.
- **ADR-P10-007**: Three error categories; observation never invalidates a session.
- **ADR-P10-008**: Engine dispatcher injected for deterministic tests.
- **ADR-P10-009**: Android adapters live under `bluetooth.audio` (existing architecture boundary).
- **ADR-P10-010**: `SCO` devices are not attributed to HFP or HSP (shared path).

## 4. What Phase 10 does NOT do

Codec negotiation/selection, bitrate or sample-rate control, A2DP packet interception, SCO
activation, audio recording, DSP/effects, vendor audio commands, production UI, notifications,
physical-device testing. See specs.md §8 (forbidden list) and the machine-checked
`PhaseTenScopeTest`.
