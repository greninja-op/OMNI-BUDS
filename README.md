# OmniBuds

<p align="center">
  <img width="260" height="260" alt="OmniBuds App Logo" src="https://github.com/user-attachments/assets/a1a3b59f-ad00-43a8-ab1d-73066f74a4d1" style="border-radius: 28px; box-shadow: 0 8px 24px rgba(0,0,0,0.5);" />
</p>

<h3 align="center">The Universal, Open-Source Hardware Control Platform for Wireless Earbuds & Headphones</h3>

<p align="center">
  One unified, lightning-fast app to replace a drawer full of proprietary OEM bloatware. 
  <br />
  <b>Zero accounts. Zero cloud telemetry. Zero background bloat. 100% Local Hardware Control.</b>
</p>

<p align="center">
  <a href="#-why-omnibuds"><img src="https://img.shields.io/badge/Platform-Android%2012%2B-22C55E?style=for-the-badge&logo=android&logoColor=white" alt="Platform: Android" /></a>
  <a href="#-architecture--tech-stack"><img src="https://img.shields.io/badge/Language-Kotlin%20%7C%20Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Language: Kotlin" /></a>
  <a href="#-hardware-integration"><img src="https://img.shields.io/badge/Bluetooth-BLE%20%2B%20Classic%20RFCOMM-0082FC?style=for-the-badge&logo=bluetooth&logoColor=white" alt="Bluetooth: BLE + RFCOMM" /></a>
  <a href="#-privacy--security"><img src="https://img.shields.io/badge/Privacy-100%25%20Offline-E5FF36?style=for-the-badge&labelColor=000000&color=E5FF36" alt="Privacy: 100% Offline" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-F97316?style=for-the-badge" alt="License: Apache 2.0" /></a>
</p>

---

## 🎧 Why OmniBuds?

Modern wireless earbuds sound phenomenal, but the software experience is broken. Every manufacturer forces you to install their own 150MB+ companion app (HeyMelody, realme Link, Galaxy Wearable, Sony Headphones Connect) with mandatory account registration, invasive location tracking, cloud background syncing, and intrusive promotional banners.

**OmniBuds reclaims your audio gear.** It bypasses proprietary cloud ecosystems and speaks directly to your earbuds' native hardware over standard Bluetooth Low Energy (BLE) GATT and RFCOMM/SPP channels. 

Whether you're switching Noise Cancellation modes, monitoring individual Left/Right/Case battery levels, or tuning high-fidelity DSP filters, OmniBuds executes directly on the physical hardware in under 30 milliseconds.

---

## ✨ Key Features

### ⚡ Persistent Quick-Control Notification Shade
- **Instant Background Access:** Toggle **Active Noise Cancellation (ANC)**, **Ambient Mode**, and **Transparency** straight from your Android notification shade without opening the app.
- **One-Tap Reconnect & Disconnect:** Clean, high-contrast capsule actions designed for dark mode to instantly manage your audio links.
- **Real-Time Multi-Cell Battery Telemetry:** Precise independent battery percentages for Left earbud, Right earbud, and Charging Case with charging and dock detection.
- **Auto-Dismiss & Auto-Popup:** Automatically pins to your status bar the second any Bluetooth audio device connects, and cleanly unmounts when disconnected or placed back into the case.

### 🚀 Low-Latency `BleCommandSequencer`
- **Sub-30ms Fast Execution:** Leverages `WRITE_TYPE_NO_RESPONSE` on supported Bluetooth GATT characteristics for instantaneous mode transitions.
- **Strict Deduplication & No Double-Chimes:** Prevents duplicate intent dispatching and multi-characteristic write collisions that cause jarring double chimes in your ears.
- **"Latest-Wins" Rapid-Tap Coalescing:** Switch between ANC, Transparency, and Normal modes as fast as you can tap—the sequencer automatically skips obsolete intermediate writes and locks in your final choice.
- **Battery Polling Preemption:** User interactions instantly preempt the 1.5-second battery polling loop, guaranteeing zero queue starvation.

### 🔕 Hardware-Grade Noise Control & Teardown Sequences
- **True ANC & Transparency Power Down:** Direct register-level commands for chipset architectures (BES, BES2600, Airoha, Qualcomm, and Realtek). 
- **Zero Ghost Passthrough:** Full multi-byte hardware teardown frames (`[0x01, 0x01, 0x00, 0x00, 0x00, 0x00]`) that physically cut power to external ambient microphones in Normal/Off mode rather than leaving them idling.

### 🌌 Orbital Solar-System Radar Scanner
- **Planetary Bluetooth Device Visualizer:** Discovers and maps nearby wireless audio hardware in real time as planets orbiting your phone on an interactive, physics-driven orbital plane.
- **Adaptive Audio Glyphs:** Automatically resolves device form factors—in-ear stems, open-fit buds, over-ear studio monitors, and neckbands—with matching vector iconography.

### 🎛️ Universal Audio DSP & Sound Studio
- **Dynamic 5-Band Equalizer:** Fine-tune frequencies with Flat, Bass Boost, Vocal Enhancer, and Treble Boost presets.
- **Spatial Audio 3D Virtualizer:** Expands soundstage width and channel separation for immersive stereo audio.
- **Loudness & Volume Enhancer:** Clean pre-amp amplification with distortion clamping for quiet recordings.

### 🔄 Autonomous Background Lifecycle (`OmniBudsForegroundService`)
- **Survives App Swiping:** Keep your ongoing notification panel alive even when swiping OmniBuds away from the Android Recents screen.
- **Multi-Layer Disconnection Watchdog:** Real-time listeners for `ACTION_ACL_DISCONNECTED`, `ACTION_CONNECTION_STATE_CHANGED`, and GATT disconnections distinguish between single-bud docking and full session teardown.

---

## 🥊 OmniBuds vs. OEM Proprietary Apps

| Feature | OEM Companion Apps (realme Link, HeyMelody, etc.) | OmniBuds |
| :--- | :---: | :---: |
| **Account & Login** | ❌ Mandatory Phone/Email Sign-in | ✅ **Zero Accounts / Plug & Play** |
| **Internet & Cloud Telemetry** | ❌ Constant background tracking & ad analytics | ✅ **100% Offline & Local-First** |
| **Multi-Brand Compatibility** | ❌ Locked to a single manufacturer | ✅ **Universal Multi-Vendor Engine** |
| **Notification Bar Controls** | ⚠️ Bulky, missing, or opens app on tap | ✅ **True Native Background Buttons** |
| **Command Latency** | ⚠️ 150ms – 400ms (Heavy UI animations) | ✅ **<30ms (`BleCommandSequencer`)** |
| **Double-Chime Prevention** | ❌ Frequent packet spamming on quick taps | ✅ **Hardware Idempotency Guard** |
| **Single-Bud Docking Logic** | ⚠️ Often reports whole headset as offline | ✅ **Multi-Cell Independent Telemetry** |
| **App Footprint** | ❌ 100MB – 250MB+ with ad libraries | ✅ **Lightweight Kotlin Core (~15MB)** |

---

## 📱 Supported Devices & Protocols

OmniBuds incorporates a protocol router that identifies headsets by fingerprinting Bluetooth vendor IDs, model names, GATT service UUIDs, and Fast Pair advertisements:

```
                          ┌──────────────────────────┐
                          │   UniversalTwsEngine     │
                          └─────────────┬────────────┘
                                        │
           ┌────────────────────────────┼────────────────────────────┐
           ▼                            ▼                            ▼
┌────────────────────┐       ┌────────────────────┐       ┌────────────────────┐
│ OnePlus / HeyMelody│       │   Realme Protocol  │       │  Universal Classic │
│   Driver (BES)     │       │     Parser Pro     │       │   A2DP / AVRCP     │
└────────────────────┘       └────────────────────┘       └────────────────────┘
```

- **realme Buds Family:** realme Buds T300 (`RMA2302`), Air 5 Pro, Air 5, Air 3, Wireless series.
- **OnePlus & Oppo Family:** Buds Pro 2, Buds Pro, Buds Z2, Enco X2, Enco Air series (via reverse-engineered HeyMelody BLE protocol).
- **Universal Non-TWS & Studio Headsets:** Sony WH/WF series, Bose QC series, Marshall, JBL, Sennheiser, Samsung Galaxy Buds (A2DP, AVRCP, and RFCOMM audio management).
- **Apple AirPods & Beats:** Reverse-engineered BLE advertisement parsing for independent L/R/Case battery monitoring on Android.

---

## 📐 Architecture & Audio Path Isolation

OmniBuds operates under a strict **Zero-Audio-Degradation Rule**:

> **OmniBuds does NOT intercept, decode, re-encode, or route audio streams.** 

```
[Music Player (Spotify / Apple Music)] ───────► [Android OS Audio Pipeline] ───────► [Bluetooth A2DP / LDAC / AAC] ───────► [Earbuds]
                                                                                                ▲
                                                                                                │ (BLE GATT / SPP Only)
                                                                                     [OmniBuds Hardware Controller]
```

Capturing and software-processing audio streams damages sound quality, wastes battery, and introduces latency. OmniBuds leaves the media stream 100% untouched on Android's native A2DP stack while running hardware control alongside it over dedicated BLE/RFCOMM channels.

---

## 🛠️ Building & Developing

### Prerequisites
- **JDK 17** or later
- **Android SDK** (API Platform 35, Build Tools 35.0.0+)
- **Gradle 8.13+** (or use the included `./gradlew` wrapper)

### Build Steps

1. **Clone the repository:**
   ```bash
   git clone https://github.com/greninja-op/OMNI-BUDS.git
   cd OMNI-BUDS
   ```

2. **Configure your Android SDK path:**
   Create a `local.properties` file in the root directory:
   ```properties
   sdk.dir=/path/to/your/android/sdk
   ```

3. **Run Unit Tests:**
   ```bash
   ./gradlew testDebugUnitTest
   ```

4. **Assemble Debug APK:**
   ```bash
   ./gradlew assembleDebug
   ```
   The compiled APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

5. **Deploy Directly to Connected Device via ADB:**
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell am start -n com.omnibuds.app/.MainActivity
   ```

---

## 🛡️ Privacy & Permissions

OmniBuds requests **only** the permissions required to discover and control Bluetooth devices:
- `BLUETOOTH_CONNECT` & `BLUETOOTH_SCAN` (Android 12+): Required to inspect paired audio devices and send BLE configuration packets.
- `POST_NOTIFICATIONS` (Android 13+): Required for the ongoing Quick Control notification shade.
- `FOREGROUND_SERVICE_CONNECTED_DEVICE` (Android 14+): Required to keep the hardware link responsive when the screen is off.
- **ZERO Internet Permissions:** OmniBuds cannot connect to the internet, upload analytics, or phone home. Your usage stays strictly on your device.

---

## 📄 License

Distributed under the **Apache License, Version 2.0**. See [`LICENSE`](LICENSE) for more information.

---

<p align="center">
  <b>Built with ❤️ for audiophiles who demand control over their hardware.</b>
</p>
