# Phase 10 — Audio Transport Engine: Specifications

## 1. Domain types (`com.omnibuds.core.audio`)

| Type | Kind | Summary |
|---|---|---|
| `AudioTransportKind` | enum (+HSP) | `CLASSIC_A2DP, HFP, HSP, LE_AUDIO, UNKNOWN` |
| `AudioConnectionState` | enum | `UNKNOWN, DISCONNECTED, CONNECTING, CONNECTED, ACTIVE, SUSPENDED, DISCONNECTING` |
| `AudioDirection` | enum | `OUTPUT, INPUT, BIDIRECTIONAL, UNKNOWN` |
| `AudioDeviceType` | enum | `BLUETOOTH_A2DP, BLUETOOTH_SCO, BLE_HEADSET, BLE_SPEAKER, BUILTIN_SPEAKER, BUILTIN_EARPIECE, WIRED_HEADSET, USB_DEVICE, UNKNOWN` |
| `ProfileAvailability` | enum | `AVAILABLE, UNAVAILABLE, UNKNOWN` |
| `ObservedAudioDevice` | data class | `platformDeviceId, type, productName?, bluetoothAddress?, direction, isActive, source` |
| `AudioProfileState` | data class | `profile, availability, connectionState, audioState?, deviceAddress?, source` |
| `AudioPlatformCapabilities` | data class | `leAudioApiAvailable, apiLevel?, a2dpSupported, hfpSupported, hspSupported, audioDeviceCallbackSupported` |
| `AudioDiagnostic` | data class | `code, message, timestampMillis` (bounded at 8 per snapshot) |
| `AudioTransportSnapshot` | data class | `schemaVersion=1, timestampMillis, devices, profileStates, activeTransport?, activeOutputDevice?, activeInputDevice?, capabilities, diagnostics` |
| `AudioObserverLifecycle` | enum | `STOPPED, STARTING, OBSERVING, STOPPING` + `canStart()/canStop()` |
| `AudioTransportEngine` | class | owns snapshot flow + lifecycle; `start/stop/refreshProfiles` |
| `AudioReconciler` | object | pure `reconcile(...)`; `STALE_DEVICE_AGE_MILLIS=30_000` |
| `AudioProfileSource` / `AudioDeviceSource` | interfaces | the two ports; `AudioDeviceEvent` sealed (Added/Removed/Resynchronized) |
| `LeAudioSupport` | enum + fn | `SUPPORTED, API_TOO_OLD, UNKNOWN`; `leAudioSupport(apiLevel: Int?)`; `LE_AUDIO_MIN_API_LEVEL=33` |
| `AudioControlTopology` | data class | `audioTransport, controlTransport`; `verify()`; `UNVERIFIED` |

### State semantics (normative)

- `CONNECTED` = profile linked; says nothing about audio flowing.
- `ACTIVE` = platform routes audio through this transport; says nothing about media playing now
  or which codec is in use.
- `UNKNOWN` = unread or contradictory; never a default for "off".
- `SUSPENDED` = parked without disconnecting; recoverable without reconnecting.
- `activeTransport = null` = platform did not identify one; not "nothing playing".

### Snapshot invariants

- `schemaVersion` must equal `SCHEMA_VERSION` (1); mismatches fail fast.
- `diagnostics.size <= MAX_DIAGNOSTICS` (8); newest first.
- `devices` never merged on name similarity; `platformDeviceId` never treated as persistent identity.
- No codec-configuration fields; no UI fields; no history (each snapshot replaces the last).

## 2. Reconciliation rules (normative)

See design.md §2.4. Rule order is significant: capability gate → stale-drop → per-profile
rules (conflict preservation, impossible-combination repair) → active election.

## 3. Engine contract

- `start(): OperationOutcome<Unit>` — idempotent; failure → STOPPED + `AUDIO_OBSERVATION_FAILED`.
- `stop(): OperationOutcome<Unit>` — idempotent; joins the observation scope.
- `refreshProfiles(): OperationOutcome<Unit>` — host-triggered re-read; fails with
  `INVALID_STATE` when not OBSERVING.
- `snapshots: StateFlow<AudioTransportSnapshot>` — the single authoritative stream.
- `lifecycle: StateFlow<AudioObserverLifecycle>` — observable transitions.
- Constructor: `(profileSource, deviceSource, capabilities, clockMillis = System::currentTimeMillis, dispatcher = Dispatchers.Default)`.

## 4. Platform contract (`com.omnibuds.android.bluetooth.audio`)

- `AudioTransportHandle.readRawProfiles(): RawProfileRead(states, connectedAddresses, headsetAudioState)` —
  one binder session; proxies released before return.
- `readRawAudioDevices(): List<RawAudioDevice>`; `readActiveDeviceIds(): Set<Int>`.
- `openAudioDeviceChanges(emit: () -> Unit): AutoCloseable` — idempotent close.
- `AndroidAudioTransportSource` implements both core ports; permission-first
  (no BLUETOOTH_CONNECT → empty profile map, never a throw); HSP always UNKNOWN (ADR-P10-004).
- `LeAudioHandle` + `LeAudioApi33`: bound via `bind(): Boolean`, released via `release()`;
  `init` throws below API 33.
- `audio/mapping`: the only translator (`audioConnectionStateOf`, `headsetAudioStateOf`,
  `audioDeviceTypeOf`, `observedAudioDeviceOf`, `audioTransportKindOf`).

## 5. Error categories (additions to `OmniBudsErrorCategory`)

| Category | Retry | Invalidates session |
|---|---|---|
| `AUDIO_OBSERVATION_FAILED` | `SAFE_TO_RETRY` (reads are side-effect-free) | false |
| `LE_AUDIO_UNAVAILABLE` | `NEVER_RETRY` | false |
| `AUDIO_STATE_CONFLICT` | `NEVER_RETRY` | false |

## 6. API-level matrix (from research)

| API surface | Min API | Notes |
|---|---|---|
| `AudioManager.getDevices` / `registerAudioDeviceCallback` | 23 | No permission needed |
| `AudioDeviceInfo` types incl. `TYPE_BLE_*` | 23 / 33 | `getId()` not stable; `getAddress()` opaque |
| `getCommunicationDevice()` (read-only) | 31 | Never call the setter |
| `BluetoothA2dp` / `BluetoothHeadset` proxies | 3 | `BLUETOOTH_CONNECT` on 31+ |
| `BluetoothLeAudio`, `isLeAudioSupported()` | 33 | Guarded; minSdk stays 26 |
| `startBluetoothSco()` et al | — | Deprecated; never called |

## 7. Permissions

No new permissions. Profile reads need `BLUETOOTH_CONNECT` (Phase 2 model); device reads need
none. `RECORD_AUDIO` is never requested, declared, or needed.

## 8. Explicitly forbidden (machine-checked by `PhaseTenScopeTest`)

Codec negotiation/selection/configuration; bitrate/sample-rate forcing; A2DP packet
interception; HFP SCO activation; audio recording/capture; MediaProjection; DSP/effects;
virtual ANC; vendor audio commands; routing changes (`setCommunicationDevice`,
`startBluetoothSco`, …); production UI; notifications/widgets; physical-device testing.
