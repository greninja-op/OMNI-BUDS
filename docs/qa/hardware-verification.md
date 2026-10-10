# OmniBuds — Hardware Verification Audit & HIL Status (Phase 52)

## 1. Executive Summary & Policy Compliance

The Fundamental Principle of Hardware Truth dictates:
> OmniBuds never simulates a hardware capability. It discovers, verifies, and controls capabilities implemented by the connected device. Fixture and simulated results never count as physical hardware verification.

In Phase 52, an exhaustive audit of the test runtime was conducted. The host execution environment is a headless Linux container (Linux 7.0.0-39-generic x86_64).
- **Physical Bluetooth HCI Controllers**: 0 present.
- **Physical Audio Peripherals (Earbuds/Headphones)**: 0 present.
- **Physical Android Handsets / Test Rigs**: 0 present.
- **Display Servers (X11 / Wayland)**: 0 present.

Consequently, all physical hardware release gates are truthfully evaluated as **`NOT VERIFIED (BLOCKED_NO_ENVIRONMENT)`**. No test double, fixture response, or packet simulation was substituted for physical verification.

---

## 2. Hardware Device Coverage & Verification Status

| Target Device Model | Expected Transport | Target Capabilities | HIL Plan Reference | Verification Status | Verified Evidence Source |
|---|---|---|---|---|---|
| **Apple AirPods Pro 2** | BLE GATT / Apple Proprietary | ANC, Transparency, Adaptive Audio, Spatial Audio, Battery | Phase 39 / Phase 38 | **NOT VERIFIED** (Blocked) | None (Hardware absent) |
| **Sony WH-1000XM5** | RFCOMM / Sony Sound Connect | ANC, Ambient Sound Level, Speak-to-Chat, LDAC, Multi-point | Phase 40 / Phase 38 | **NOT VERIFIED** (Blocked) | None (Hardware absent) |
| **Bose QuietComfort Ultra** | BLE GATT / Bose SoundLink | ANC, Immersion Mode, Wind Block, Battery Levels | Phase 41 / Phase 38 | **NOT VERIFIED** (Blocked) | None (Hardware absent) |
| **Google Pixel Buds Pro** | BLE GATT / Google Bisto | ANC, Transparency, Volume EQ, In-ear Detection | Phase 42 / Phase 38 | **NOT VERIFIED** (Blocked) | None (Hardware absent) |
| **Samsung Galaxy Buds2 Pro** | RFCOMM / BLE / Samsung Protocol | ANC, Ambient Sound, Voice Detect, 360 Audio | Phase 43 / Phase 38 | **NOT VERIFIED** (Blocked) | None (Hardware absent) |

---

## 3. Real Hardware-Control Verification Workflow (10-Step Protocol)

When physical testing is executed by release engineers with access to physical hardware, each device must follow the 10-step evidence trail:

```
[1. DISCOVER]  -> Confirm device is observed via live Bluetooth scan.
[2. IDENTIFY]  -> Match manufacturer data, model ID, and firmware string.
[3. READ]      -> Query observable state (battery, active ANC mode).
[4. UNDERSTAND]-> Decode protocol payloads according to verified schema.
[5. VALIDATE]  -> Check state prerequisites and dependency constraints.
[6. WRITE]     -> Issue authorized mutating command (e.g. switch ANC to Transparent).
[7. READ BACK] -> Read authoritative device state; verify hardware applied the change.
[8. DISCONNECT]-> Terminate Bluetooth session cleanly.
[9. RECONNECT] -> Re-establish Bluetooth link and discover session state.
[10. VERIFY]   -> Confirm setting persistence across link reconnection.
```

### Current Status of 10-Step Workflow:
- Steps 1 through 10 have been rigorously exercised in **deterministic simulation** (via `ScriptedTransport` and `TestDoublesAreNotHardwareTest` in `:core`), ensuring that software state engines handle all protocol permutations correctly.
- Steps 1 through 10 have **NOT** been performed on physical hardware due to the missing environment.

---

## 4. Hardware Persistence Classification

Persistence verification distinguishes between four distinct boundaries:
1. **Local Session Persistence**: Setting persists in memory while connected (Software verified).
2. **Device Disconnect / Reconnect Persistence**: Setting persists on physical device after RF link tear-down and re-establishment (Requires physical device; **NOT VERIFIED**).
3. **Application Restart Persistence**: Setting re-read accurately from physical device after client app kill (Requires physical device; **NOT VERIFIED**).
4. **Device Power-Cycle Persistence**: Setting survives physical earbud placement in charging case and removal (Requires physical device; **NOT VERIFIED**).

Local app storage (`SharedPreferences` or local SQLite/JSON files) is NEVER conflated with device-level hardware persistence.

---

## 5. Prerequisites for Hardware Verification Pass

To transition hardware release gates from `NOT VERIFIED` to `HARDWARE_VERIFIED`:
1. Provision physical test rig with Linux host + BlueZ HCI controller or Android handset (API 31+).
2. Pair physical target earbuds (AirPods Pro 2, Sony WH-1000XM5, Bose QC Ultra, Pixel Buds Pro, Galaxy Buds2 Pro).
3. Record exact firmware versions (e.g. AirPods 6F8, Sony 2.0.2).
4. Execute HIL automated runner using live RF transport.
5. Capture acoustic / electrical verification where applicable or capture authoritative read-back frames.
