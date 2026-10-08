# Phase 12 — Platform Limitations

**Status:** Authoritative. Verified against the API-35 `android.jar`
(class-file surface + `api-versions.xml`) and the public Android developer
reference.

---

## 1. The decisive finding

**No public Android API — on any API level — lets a third-party app select or
configure a Bluetooth codec.** Specifically:

### A2DP (classic)

| Wanted | Public API | Verdict |
|---|---|---|
| Select codec (e.g. "use LDAC") | None. `BluetoothA2dp` exposes `getConnectedDevices`, `getConnectionState`, `getDevicesMatchingConnectionStates`, `getSupportedCodecTypes`, `isA2dpPlaying`. | **Impossible** |
| Configure codec parameters | None. `BluetoothCodecConfig.setCodecPriority()` existed only as a hidden/system API. | **Impossible** |
| Observe active codec | None. No `getCodecStatus()`. | **Impossible** (Phase 11) |
| Observe codec change events | None public. `ACTION_CODEC_CONFIG_CHANGED` is reserved for system use. | **Impossible** |

### LE Audio

| Wanted | Public API | Verdict |
|---|---|---|
| Select/configure codec | None. `BluetoothLeAudio` exposes only connection/group management. | **Impossible** |
| Observe active codec config | None. `BluetoothLeAudioCodecStatus` is vocabulary without a source. | **Impossible** |

## 2. What *does* exist

- `BluetoothA2dp.getSupportedCodecTypes()` (API 35+): the **local phone's**
  codec list. Requires `BLUETOOTH_PRIVILEGED` (system apps only) — effectively
  unusable by OmniBuds. Reports the phone, never the earbuds.
- `BluetoothCodecType` / `BluetoothCodecConfig` (API 33/35): vocabulary
  (constants, data classes) with no obtainable instance.
- Codec taxonomy gaps: no constants for aptX Adaptive, aptX Lossless, or LC3
  (LC3 appears only as deprecated `SOURCE_CODEC_TYPE_LC3`).

## 3. Consequences for the architecture

| Capability | Value on public Android APIs | Evidence |
|---|---|---|
| selectable | `false` | `PLATFORM_LIMITATION` |
| configurable | `false` | `PLATFORM_LIMITATION` |
| verifiable | `false` | `PLATFORM_LIMITATION` |

Every `SELECT_CODEC` → `NOT_SELECTABLE`. Every `CONFIGURE_CODEC` →
`NOT_CONFIGURABLE`. This is honest, not a stub.

## 4. What could change this

1. **A future Android public API** for codec control → new
   `CodecControlAdapter` implementation; the engine is unchanged.
2. **A verified vendor protocol** with codec commands → adapter behind the
   Protocol Abstraction Engine; no guessed commands, no direct packets.
3. **System/privileged status** for OmniBuds → `getSupportedCodecTypes()`
   becomes reachable (observation only, still not control).

Until one of these exists, `NOT_SELECTABLE` / `NOT_CONFIGURABLE` is the
correct engineering answer.

## 5. Vendor protocol status

The protocol registry ships empty (Phase 7). No verified vendor protocol in
the repository defines codec-control commands. Per §17, no commands are
guessed. The vendor path is therefore unavailable; `READ-ONLY` is correct.

## 6. Permissions

No new permissions. `BLUETOOTH_CONNECT` remains the only runtime permission.
`RECORD_AUDIO` is not needed, not requested, not used. `BLUETOOTH_PRIVILEGED`
is not grantable to third-party apps.
