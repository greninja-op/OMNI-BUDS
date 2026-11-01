# OmniBuds Required Screen Inventory

This inventory documents all 17 target screen states, views, and sheets across OmniBuds. It maps each user-facing surface to its architectural source, presentation model, and behavioral contract.

---

## 1. Inventory Matrix

| # | Screen / Surface | Route / Destination | Presentation Model | Backend Engine | Key UI Components |
|---|---|---|---|---|---|
| **1** | **Devices Dashboard** | `AndroidDestination.Devices` | `DevicesViewModel` | `AndroidBluetoothAdapterBridge` | Paired device list, connection status badge, quick battery chip, hero scan CTA |
| **2** | **Device Discovery & Scanning** | `DevicesScreen` (scanning mode) | `DevicesViewModel` | `AndroidDeviceDiscoverySession` | Concentric scanning radar, live discovered device cards, RSSI signal bars, stop action |
| **3** | **Device Details & Overview** | `AndroidDestination.DeviceWorkspace(tab = OVERVIEW)` | `DeviceWorkspaceViewModel` | `GlobalDeviceStateRepository` | Hero earbud outline vector, connection state pill, model name, battery strip, active controls summary |
| **4** | **Hardware Control Center** | `DeviceWorkspace(tab = CONTROLS)` | `DeviceWorkspaceViewModel` | `HardwareFeatureEngine` (6-state) | Segmented switches for ANC/Ambient/Normal, action cards, execution progress indicators |
| **5** | **ANC & Transparency Sheet** | Modal Bottom Sheet from Controls | `DeviceWorkspaceViewModel` | `HardwareFeatureEngine` | Multi-level noise cancellation selector (Off, ANC, Transparency, Wind Reduction), level slider |
| **6** | **Equalizer Panel** | `DeviceWorkspace(tab = CONTROLS)` | `DeviceWorkspaceViewModel` | `HardwareFeatureEngine` | Preset chips (Bass Boost, Vocal, Treble, Balanced), custom band sliders if protocol-supported |
| **7** | **Battery & Power Details** | `DeviceWorkspace(tab = BATTERY)` | `BatteryPresentationModel` | `BatteryEngine` | Tri-component card (Left bud, Right bud, Charging case), percentage bars, charging lightning bolt |
| **8** | **Audio & Codec Information** | `DeviceWorkspace(tab = AUDIO)` | `AudioPresentationModel` | `AudioTransportEngine`, `AudioQualityState` | Active transport (A2DP / LE Audio), sample rate / bit depth, honest `NOT_OBSERVABLE` platform notice |
| **9** | **Settings** | `AndroidDestination.Settings` | `SettingsViewModel` | `PersistentConfigurationEngine` | Theme mode selector, auto-connect toggle, background scan frequency, diagnostic export |
| **10** | **Diagnostics & Logs** | `AndroidDestination.Diagnostics` | `DiagnosticsViewModel` | `GlobalDeviceStateRepository`, Telemetry | Real-time event stream, protocol transaction ledger, error counts, raw log export button |
| **11** | **Device Information & Specs** | `DeviceWorkspace(tab = INFO)` | `DeviceWorkspaceViewModel` | `DeviceFingerprint`, `FirmwareRevision` | Bluetooth MAC address, Hardware revision, Firmware version, Protocol version, Supported capability matrix |
| **12** | **Permissions & Bluetooth Gate** | Overlay / Warning banner | `DevicesViewModel` | `AndroidPermissionChecker` | "Bluetooth Powered Off" banner with system shortcut, "Nearby Devices Permission" request card |
| **13** | **Purposeful Empty State** | `DevicesScreen(mode = NO_DEVICES)` | `DevicesViewModel` | Census reader | Clean hardware outline illustration, paired device guidance, prominent fluorescent yellow scan button |
| **14** | **Loading States** | Global & Local | Presentation State `isLoading` | Asynchronous coroutines | Skeleton shimmer cards for device loading, circular accent spinner for operations |
| **15** | **Operation Failure & Recovery** | Inline Error Banner | Presentation State `errorBanner` | Finite state machine rollback | High-contrast error banner, exact failure reason, "Retry" action button, "Dismiss" action |
| **16** | **Unsupported & Read-Only State** | Device Card / Sheet | `DeviceWorkspaceViewModel` | `AccessPolicyEngine` | Default-deny banner, explanation of unverified vendor protocol, read-only telemetry mode |
| **17** | **Dialogs & Confirmation Sheets** | Modal Bottom Sheets | `AndroidApplicationShell` | Configuration store | "Disconnect Device" prompt, "Forget Device", "Clear Diagnostic Logs" confirmation |

---

## 2. Screen Hierarchy & Navigation Graph

```
                               ┌───────────────────────────┐
                               │   OmniBuds App Shell      │
                               │   (Top Bar + Bottom Nav)  │
                               └─────────────┬─────────────┘
                                             │
             ┌───────────────────────────────┼───────────────────────────────┐
             │                               │                               │
             ▼                               ▼                               ▼
    [Devices Destination]          [Settings Destination]          [Diagnostics Destination]
             │                               │                               │
             ├─ No Devices Empty State       ├─ Theme Settings               ├─ Event Stream
             ├─ Scanning Active View         ├─ Background Scanning          ├─ Transaction Log
             ├─ Paired Devices List          └─ Telemetry Storage            └─ Error Register
             │
             ▼ (Select Device)
    [Device Workspace Destination]
             │
             ├──► [Overview Tab]      Hero summary, battery quick-strip, primary controls
             ├──► [Controls Tab]      Hardware Feature Engine: ANC, Ambient, EQ presets
             ├──► [Battery Tab]       Detailed Left / Right / Case telemetry & charging states
             ├──► [Audio Tab]         A2DP / LE Audio transport state, honest codec limitation notice
             └──► [Device Info Tab]   MAC, firmware revision, capability discovery matrix
```
