# OmniBuds Screen Specifications

This document defines the wireframes, layout hierarchy, interactive behaviors, and responsive adaptations for primary screens in the OmniBuds redesign.

---

## 1. Screen 1: Devices Dashboard & Purposeful Empty State

### 1.1 Wireframe: Empty State (Before Discovery)

```
┌──────────────────────────────────────────────────────────┐
│  OMNIBUDS                                      (BT-ON)   │ ◄ Top Bar: Monospace brand logo + BT status pill
├──────────────────────────────────────────────────────────┤
│                                                          │
│  Devices                                                 │ ◄ Headline Large (28sp SemiBold)
│  Manage your connected wireless accessories              │ ◄ Subtitle (13sp Secondary Grey)
│                                                          │
│  ┌────────────────────────────────────────────────────┐  │
│  │                                                    │  │
│  │                    ╭──────────╮                    │  │ ◄ Vector Outline Illustration:
│  │                 ╭──╯          ╰──╮                 │  │   Clean headset outline with
│  │                 │   (  ||  )     │                 │  │   subtle fluorescent yellow
│  │                 ╰──╮          ╭──╯                 │  │   status dot.
│  │                    ╰──────────╯                    │  │
│  │                                                    │  │
│  │            No Devices Connected                    │  │ ◄ Title Medium (18sp White)
│  │   Make sure your earbuds are in pairing mode or    │  │ ◄ Body Medium (13sp Grey)
│  │   connected to your Android Bluetooth settings.    │  │
│  │                                                    │  │
│  │        ┌──────────────────────────────────┐        │  │
│  │        │       [+] SCAN FOR EARBUDS       │        │  │ ◄ Primary Hero Button: Fluorescent Yellow
│  │        └──────────────────────────────────┘        │  │   background (#D4FF00), Black text, Pill shape.
│  │                                                    │  │
│  └────────────────────────────────────────────────────┘  │
│                                                          │
│  QUICK TROUBLESHOOTING                                   │ ◄ Section Header (12sp Bold Caps)
│  ┌────────────────────────────────────────────────────┐  │
│  │ [?] How to put earbuds into pairing mode        › │  │ ◄ Helpful inline guidance card
│  ├────────────────────────────────────────────────────┤  │
│  │ [i] Android Bluetooth Settings                  › │  │ ◄ Intent shortcut to OS BT settings
│  └────────────────────────────────────────────────────┘  │
│                                                          │
├──────────────────────────────────────────────────────────┤
│    [=] Devices      [*] Settings    [#] Diag    [i] About│ ◄ Bottom Nav: Fluorescent Yellow active indicator
└──────────────────────────────────────────────────────────┘
```

### 1.2 Wireframe: Populated Devices Dashboard

```
┌──────────────────────────────────────────────────────────┐
│  OMNIBUDS                                      (BT-ON)   │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  Devices                             [+ Scan Devices]    │ ◄ Header + Secondary Outlined Scan Action
│  1 Active accessory                                      │
│                                                          │
│  ACTIVE ACCESSORY                                        │
│  ┌────────────────────────────────────────────────────┐  │
│  │  ╭───╮  Sony WF-1000XM5              [CONNECTED]   │  │ ◄ Hero Device Card:
│  │  │( )│  A2DP High Resolution Audio                  │  │   - Outline Earbud Vector
│  │  ╰───╯                                             │  │   - Device Model Name
│  │                                                    │  │   - Green Status Pill
│  │  BATTERY LEVEL                                     │  │
│  │  [ L: 85% ■■■■■ ]  [ R: 85% ■■■■■ ]  [ Case: 90% ] │  │   - Tri-component battery strip
│  │                                                    │  │
│  │  NOISE CONTROL PRESET                              │  │
│  │  ┌──────────────────────────────────────────────┐  │  │
│  │  │  [ ANC ]   │     Ambient      │     Off      │  │  │   - Quick Segmented Switch
│  │  └──────────────────────────────────────────────┘  │  │     (Fluorescent Yellow active thumb)
│  │                                                    │  │
│  │  Tap to open Hardware Workspace                 ›  │  │
│  └────────────────────────────────────────────────────┘  │
│                                                          │
│  OTHER PAIRED ACCESSORIES                                │
│  ┌────────────────────────────────────────────────────┐  │
│  │  Bose QuietComfort 45               [DISCONNECTED] │  │ ◄ Secondary Paired Card (Dimmed)
│  │  Last seen 2 hours ago                          ›  │  │
│  └────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
```

---

## 2. Screen 2: Active Scanning & Discovery Mode

```
┌──────────────────────────────────────────────────────────┐
│  ◄ Back                     Scanning for Accessories     │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  ┌────────────────────────────────────────────────────┐  │
│  │                     ((   ))                        │  │ ◄ Dynamic Radar Visualizer:
│  │                   ((   O   ))                      │  │   Concentric fluorescent yellow rings
│  │                     ((   ))                        │  │   pulsing smoothly.
│  │              Searching for nearby BLE devices      │  │
│  │                 [ Stop Scanning (12s) ]            │  │ ◄ Secondary stop button with timeout
│  └────────────────────────────────────────────────────┘  │
│                                                          │
│  DISCOVERED ACCESSORIES (2)                              │
│  ┌────────────────────────────────────────────────────┐  │
│  │  Pixel Buds Pro 2                     [PAIR / ADD] │  │ ◄ High-priority discovered card
│  │  Signal: Strong (-52 dBm)  • Audio Sink            │  │
│  ├────────────────────────────────────────────────────┤  │
│  │  AirPods Pro (2nd gen)                [PAIR / ADD] │  │
│  │  Signal: Fair (-71 dBm)  • BLE SPP                 │  │
│  └────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
```

---

## 3. Screen 3: Device Workspace & Hardware Control Center

```
┌──────────────────────────────────────────────────────────┐
│  ◄ Devices              Sony WF-1000XM5       [Options]  │ ◄ Top Bar: Back, Device Title, Overflow
├──────────────────────────────────────────────────────────┤
│                                                          │
│  [ OVERVIEW ]   [ CONTROLS ]   [ BATTERY ]   [ AUDIO ]   │ ◄ Scrollable Hardware Navigation Tabs
│  ────────────────                                        │   (Active tab marked with 2dp Fluorescent Bar)
│                                                          │
│  NOISE CANCELLATION (HARDWARE DSP)                       │
│  ┌────────────────────────────────────────────────────┐  │
│  │  Active Noise Control                              │  │ ◄ Hardware Feature Card:
│  │  Managed by on-headset V2 processor                │  │   - Direct hardware state
│  │                                                    │  │   - 3-way tactile segmented switch
│  │  ┌──────────────────────────────────────────────┐  │  │   - Active state has #D4FF00 background
│  │  │   [ Noise Cancelling ] │  Ambient  │   Off   │  │  │
│  │  └──────────────────────────────────────────────┘  │  │
│  │                                                    │  │
│  │  Ambient Sound Sensitivity                         │  │
│  │  [───────●────────────────────────] Level 4 of 20  │  │ ◄ Bounded Slider (Touch Target 48dp)
│  └────────────────────────────────────────────────────┘  │
│                                                          │
│  HARDWARE EQUALIZER PRESETS                              │
│  ┌────────────────────────────────────────────────────┐  │
│  │  Sound Profile (Device-stored)                     │  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────┐ │  │ ◄ Preset Filter Chips
│  │  │ [Custom] │ │Bass Boost│ │  Vocal   │ │ Flat   │ │  │   (Custom selected in #D4FF00)
│  │  └──────────┘ └──────────┘ └──────────┘ └────────┘ │  │
│  │                                                    │  │
│  │  [ 400Hz ]   [ 1kHz ]   [ 2.5kHz ]   [ 6.3kHz ]    │  │ ◄ Vertical Band Sliders
│  │    +2dB        0dB         -1dB         +3dB       │  │
│  └────────────────────────────────────────────────────┘  │
│                                                          │
│  HARDWARE PERSISTENCE STATUS                             │
│  ┌────────────────────────────────────────────────────┐  │
│  │  [✓] Configuration Saved to Headset Memory         │  │ ◄ Persistence Verification Badge
│  │  Survives disconnect and reconnect events.         │  │
│  └────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
```

---

## 4. Screen 4: Audio & Codec Transparency Screen

```
┌──────────────────────────────────────────────────────────┐
│  ◄ Workspace                  Audio & Codec Telemetry    │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  ACTIVE AUDIO LINK                                       │
│  ┌────────────────────────────────────────────────────┐  │
│  │  Transport Profile: Advanced Audio (A2DP)          │  │
│  │  Audio Route: Media & Communications               │  │
│  │  Stream State: ACTIVE (Rendering)                  │  │
│  └────────────────────────────────────────────────────┘  │
│                                                          │
│  HONEST PLATFORM LIMITATION NOTICE                       │
│  ┌────────────────────────────────────────────────────┐  │
│  │  [i] Codec Observability Restricted                │  │ ◄ Honest limitation card:
│  │  Public Android framework APIs (AOSP API 34) do    │  │   Explains why OmniBuds does not
│  │  not expose the active Bluetooth codec or allow    │  │   fabricate active codec values.
│  │  codec switching without hidden system APIs.       │  │
│  │                                                    │  │
│  │  Active Codec: NOT_OBSERVABLE                      │  │
│  │  Codec Selection: NOT_SELECTABLE                   │  │
│  └────────────────────────────────────────────────────┘  │
│                                                          │
│  SUPPORTED CODECS (DEVICE SPECIFICATION)                 │
│  ┌────────────────────────────────────────────────────┐  │
│  │  • LDAC (High Resolution 990 kbps capable)         │  │ ◄ Supported codec catalogue
│  │  • AAC (Advanced Audio Coding)                     │  │
│  │  • SBC (Sub-band Codec standard)                   │  │
│  └────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
```
