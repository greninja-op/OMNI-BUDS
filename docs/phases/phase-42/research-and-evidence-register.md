# Phase 42 — Research and Evidence Register

## Sources

| Source | Type | Scope | Retrieved |
|---|---|---|---|
| Apple support: AirPods with non-Apple devices | OFFICIAL_DOCUMENTATION | Listen/talk work; Siri unavailable | 2026-10-09 |
| thetechgorilla.com pairing guide | PUBLIC_THIRD_PARTY_RESEARCH | ANC/Transparency via stem; battery pop-up, ear detection, spatial audio, Find My, firmware updates unavailable | 2026-10-09 |
| headphonesty.com | PUBLIC_THIRD_PARTY_RESEARCH | Same limitations; battery per-bud unavailable | 2026-10-09 |
| slashgear.com | PUBLIC_THIRD_PARTY_RESEARCH | Hardware controls only | 2026-10-09 |
| LibrePods (GPLv3) | PUBLIC_THIRD_PARTY_RESEARCH | AAP reverse-engineered; Android needs root/device-ID spoofing | 2026-10-09 |
| podbridge prior-art.md | PUBLIC_THIRD_PARTY_RESEARCH | AAP facts: L2CAP PSM 0x1001; spec pinned to Pro 2 USB-C fw 7A305 | 2026-10-09 |
| Bluetooth SIG assigned numbers | PUBLIC_STANDARD | Apple company ID 0x004C; class-of-device audio major class | 2026-10-09 |

## Key findings

1. AirPods work on Android as standard Bluetooth audio devices
   (A2DP/HFP): audio, calls, microphone, on-device ANC/Transparency
   toggling via stem controls.
2. No Apple-specific setting is controllable from Android via
   supported APIs.
3. The reverse-engineered Apple Accessory Protocol requires
   spoofing Apple's BLE identity and typically root — an
   access-control bypass OmniBuds will not perform.
4. LibrePods sources are GPL-encumbered; not adopted.

## Evidence classes used

OFFICIAL_DOCUMENTATION, PUBLIC_STANDARD,
PUBLIC_THIRD_PARTY_RESEARCH, REPOSITORY_EVIDENCE, INFERRED,
IMPLEMENTED, SIMULATED_TESTED. HARDWARE_VERIFIED: none.
