# Phase 42 — Android Accessibility Boundaries

## Accessible via supported Android APIs

- Pairing, connection state, bond state.
- A2DP audio, HFP calls, microphone.
- Device name, class-of-device, manufacturer data.
- Battery level extra when the device reports it
  (`BluetoothDevice.EXTRA_BATTERY_LEVEL`).
- Codec information where the platform exposes it.

## Not accessible via supported APIs

- ANC/Transparency mode selection (works only via on-device
  stem controls).
- Per-bud and case battery levels.
- Ear detection state.
- Spatial audio / head tracking.
- Firmware version and updates.
- Gesture customization.
- Find My, Siri, auto-switching.

## Hard boundary

Anything requiring Apple's proprietary protocol (AAP) needs
device-identity spoofing and usually root on Android. That is an
access-control bypass and is out of scope permanently.
