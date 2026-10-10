# OmniBuds — Known Limitations (Release 1.0.0-RC1)

## 1. Platform-Specific Boundaries

### 1.1 Android Platform
- **Library Architecture (`.aar`)**: The `:platform:android` module compiles as an Android Archive Library (`omnibuds-android-1.0.0.aar`). It is designed to be integrated into companion applications, OEM settings shells, or OEM system services. A reference verification shell (`omnibuds-companion-shell-1.0.0-unsigned.apk`) is packaged for build validation, but the core distribution unit is the AAR.
- **Bluetooth Audio Codec Control**: Standard non-root Android apps cannot directly change active Bluetooth A2DP codecs (e.g., forcing LDAC or AAC) due to Android OS security restrictions (`@SystemApi` protection on `BluetoothA2dp.setCodecConfigPreference`). OmniBuds honestly surfaces `NOT_SELECTABLE` and `NOT_OBSERVABLE` rather than falsifying controls.
- **Battery Observation**: Public Android SDK APIs prior to Android 12/14 provide limited standard battery reporting for Bluetooth Classic headsets. OmniBuds retrieves battery data via vendor-specific protocol frames where available; otherwise, values remain `Unknown`.

### 1.2 Desktop Platform
- **Linux BlueZ DBus Requirement**: On Linux desktop, OmniBuds communicates via BlueZ DBus interfaces. If `bluetoothd` is stopped, inaccessible, or DBus permissions are restricted, the desktop adapter safely falls back to `UnsupportedDesktopBluetoothAdapter`.
- **macOS & Windows Support**: The desktop architecture provides platform adapter stubs (`MacOsCoreBluetoothDesktopAdapter` and `WindowsWinRtDesktopAdapter`), but official verification was executed on Linux x86_64. Native macOS/Windows execution requires building on their respective host OSes.

---

## 2. Hardware & Firmware Boundaries

### 2.1 Physical Hardware Testing Status
- The automated test suite validates protocol packet serializers, parsers, firmware compatibility resolvers, and state machines against verified trace fixtures and deterministic simulated transports.
- **Physical Hardware Verification is NOT VERIFIED**: No physical headphones, earbuds, or host Bluetooth radios were present during final QA in the container environment. Physical verification must be conducted in an authorized hardware test lab prior to general public release.

### 2.2 Firmware Version Constraints
- Earbud manufacturers frequently alter packet formats across major firmware updates.
- Devices running firmware versions not cataloged in the compatibility database (`FirmwareCompatibilityResolver`) are restricted to safe read-only operations (`AccessLevel.READ_ONLY`).
- Mutating operations (switching ANC modes, toggling EQ) are permitted only when the device model and firmware revision match approved compatibility rules.

---

## 3. Audio & Acoustic Invariants

- **No Media Interception or DSP**: OmniBuds is strictly a hardware control and telemetry application. It does not intercept, record, capture, or re-encode live audio streams.
- **Supported vs. Active Codecs**: Displaying that an earbud supports LDAC or aptX Adaptive does NOT imply that LDAC is currently negotiated or active on the audio link.
- **No Virtual DSP or Fake ANC**: OmniBuds never simulates active noise cancellation or equalizer effects in software. Controls are only active if implemented by the physical earbud DSP.
