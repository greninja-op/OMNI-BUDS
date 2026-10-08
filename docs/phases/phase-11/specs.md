# Phase 11 — Codec Capability Engine: Specifications

## 1. Domain types

### `com.omnibuds.core.audio` (vocabulary — extended)

| Type | Kind | Summary |
|---|---|---|
| `Codec` | enum (+OPUS) | SBC, AAC, APTX, APTX_HD, APTX_ADAPTIVE, APTX_LOSSLESS, LDAC, LC3, OPUS, UNKNOWN; each with `displayName` and `family` |
| `CodecFamily` | enum | CLASSIC_A2DP, LE_AUDIO, UNKNOWN |
| `CodecState` | enum (ladder) | UNKNOWN, UNSUPPORTED, SUPPORTED, AVAILABLE, ENABLED, NEGOTIATED, ACTIVE |
| `CodecCapability` | data class | `codec, state, configurable` + `evidence` (default unknown), `observability` (default UNKNOWN), `metadata?` (default null) |
| `CodecEvidence` | data class | `source, confidence, observedAtMillis?, detail?` |
| `CodecEvidenceSource` | enum | ANDROID_FRAMEWORK, AUDIO_DEVICE_INFO, BLUETOOTH_PROFILE, PLATFORM_CODEC_METADATA, DEVICE_PROTOCOL, VENDOR_PROTOCOL, UNKNOWN |
| `EvidenceConfidence` | enum | UNKNOWN, INFERRED, OBSERVED, VERIFIED (ordered) |
| `CodecObservability` | enum | OBSERVABLE, PARTIALLY_OBSERVABLE, NOT_OBSERVABLE, UNKNOWN |
| `CodecMetadata` | data class | `sampleRateHz?, bitsPerSample?, channelMode` (default UNKNOWN), `bitrate` (default Unknown), `qualityMode` (default UNKNOWN) |
| `CodecBitrate` | sealed | Exact(bps), Range(min, max), Adaptive, Unknown |
| `CodecFreshness` | enum | CURRENT, STALE, UNKNOWN |
| `ChannelMode` | enum (existing) | MONO, STEREO, UNKNOWN |
| `QualityMode` | enum (existing) | SOUND_QUALITY_PRIORITY, BALANCED, CONNECTION_QUALITY_PRIORITY, ADAPTIVE, UNKNOWN |

### `com.omnibuds.core.codec` (engine — new)

| Type | Kind | Summary |
|---|---|---|
| `CodecRuntimeState` | data class | `codec, state` (ladder), `activeParameters?, observedAtMillis?, freshness, evidence` |
| `CodecSnapshot` | data class | `schemaVersion=1, timestampMillis, deviceId: DeviceIdentity, transport, capabilities, runtimeState?, observability, limitations, diagnostics`; `activeCodec` derived |
| `CodecDiagnostic` | data class | `code, message, timestampMillis` (bounded at 8) |
| `CodecObservationSource` | interface | `readCapabilities`, `readRuntimeState`, `observeRuntimeStates` |
| `CodecCapabilityEngine` | class | per-device `start/stop/refresh/stopAll`; `snapshots: StateFlow<Map<DeviceIdentity, CodecSnapshot>>`; `observeSnapshot(device)` |

## 2. Engine contract

- `start(device)`: idempotent; reads capabilities + runtime; publishes CURRENT snapshot; collects runtime flow with catch-and-continue. Failure → typed error, no snapshot.
- `stop(device)`: idempotent; cancels flow; marks runtime STALE (capabilities survive).
- `refresh(device)`: host-triggered re-read; INVALID_STATE when unobserved.
- Constructor: `(observationSource, clockMillis = System::currentTimeMillis, dispatcher = Dispatchers.Default)`.

## 3. Platform contract (`com.omnibuds.android.bluetooth.audio.codec`)

- `CodecObservationHandle.readLocalSupportedCodecIds(): List<RawCodecInfo>` — raw platform ids; empty on unavailability/denial/failure.
- `CodecApi35` — API-35-isolated; init throws below 35; one binder session per read.
- `SystemCodecObservationHandle` — permission-first (BLUETOOTH_CONNECT); delegates to `CodecApi35` on 35+.
- `AndroidCodecObservationSource` — port implementation; empty → all-UNKNOWN/NOT_OBSERVABLE; list → SUPPORTED/OBSERVED; runtime → null.
- `codec/mapping` — `codecFromCodecId` (CODEC_ID_*), `codecFromSourceCodecType` (SOURCE_CODEC_TYPE_*); unknown → null.

## 4. API-level matrix (verified via android.jar reflection + api-versions.xml)

| API | Min level | Notes |
|---|---|---|
| `BluetoothCodecConfig` / `BluetoothCodecStatus` (public) | 33 | Data classes only; no public getter |
| `BluetoothLeAudioCodecConfig` / `...Status` (public) | 33 | Data classes only; no public getter on `BluetoothLeAudio` |
| `BluetoothA2dp.getSupportedCodecTypes()` | 35 | Local phone capabilities; **requires `BLUETOOTH_PRIVILEGED` (system apps only)** — effectively unusable by OmniBuds; needs BLUETOOTH_CONNECT |
| `BluetoothCodecType` (+ `CODEC_ID_*`) | 35 | SBC=0, AAC=2, APTX=16797695, APTX_HD=604035071, LDAC=-1442763265, OPUS=16834815; no aptX Adaptive/Lossless/LC3 |
| `SOURCE_CODEC_TYPE_*` | 33 | SBC=0, AAC=1, APTX=2, APTX_HD=3, LDAC=4, LC3=5, OPUS=6 |
| Active/negotiated codec getter | — | **Does not exist in public API** |
| Codec broadcast | — | **None public** |

## 5. Error categories

| Category | Retry | Invalidates session |
|---|---|---|
| `CODEC_OBSERVATION_FAILED` | SAFE_TO_RETRY | false |
| `CODEC_NOT_OBSERVABLE` | NEVER_RETRY | false |
| `CODEC_STATE_STALE` | SAFE_TO_RETRY | false |

## 6. Invariants

- Enum membership is not a support claim (all capabilities start UNKNOWN).
- UNKNOWN never becomes UNSUPPORTED without a positive negative reading.
- Missing metadata never becomes a default (no 0/16/stereo/990kbps invention).
- Confidence never increases during normalization.
- Device A's state never appears in device B's snapshot.
- LC3 is never classified as A2DP.
- ACTIVE requires authoritative runtime evidence (currently unobtainable → honest UNKNOWN).
