# Phase 47 — Desktop Bluetooth Layer Design

## 1. Architectural Boundaries and Dependency Direction

The desktop Bluetooth layer adheres to OmniBuds' strict downward-only dependency architecture.

```
Desktop UI (Phase 48)
        │
        ▼
Shared Core Domain (`:core`)
        │
        ├─────────────────────────────┐
        ▼                             ▼
Android Platform (`:platform:android`)  Desktop Bluetooth Contracts (`core/platform/desktop`)
                                              │
                                              ├─ Generic / Unsupported Adapter
                                              ├─ Linux BlueZ Adapter Boundary
                                              ├─ macOS CoreBluetooth Boundary
                                              └─ Windows WinRT Boundary
```

### Module Boundary
- Core contracts and domain interfaces are situated in `com.omnibuds.core.platform.desktop` inside `:core` (Layer 1).
- No OS-specific framework libraries (e.g. D-Bus, WinRT, Objective-C bridges) are imported into `:core`.
- Native platform adapter boundaries provide explicit hook points and translate platform outcomes to `OperationOutcome<T>`.

---

## 2. Core Components

### 2.1 Desktop Bluetooth Availability
The `DesktopBluetoothAvailability` enum captures all possible operating system states:
- `AVAILABLE`: Adapter exists, powered on, and authorized.
- `UNAVAILABLE`: Hardware missing or controller unattached.
- `DISABLED`: Adapter powered off or disabled by radio kill switch.
- `PERMISSION_REQUIRED`: Host OS requires user consent (e.g., macOS TCC).
- `PERMISSION_DENIED`: User or system policy rejected Bluetooth access.
- `UNSUPPORTED`: Platform subsystem lacks Bluetooth support.
- `INITIALIZING`: System daemon is booting up.
- `UNKNOWN`: Insufficient evidence to determine status.

### 2.2 Device Discovery
- `DesktopDiscoveryProvider`: Exposes synchronous initialization of discovery sessions.
- `DesktopDeviceDiscoverySession`: Manages observation flows with idempotent `stop()` handling and cancellation safety.
- `DesktopDiscoveredDevice`: Preserves platform identifiers, device names, MAC addresses (where permitted), and signal strengths while providing sanitized privacy labels (`safeLabel`).

### 2.3 Session Management & Tri-State Separation
The system strictly distinguishes three distinct connection levels via `DesktopConnectionSessionState`:
1. **OS-Level Connection (`isConnectedAtOsLevel`)**: The operating system has connected standard audio profiles (A2DP/HFP).
2. **Application Transport Session (`hasActiveTransportSession`)**: OmniBuds holds an open GATT/RFCOMM socket.
3. **Vendor Protocol Session (`hasVendorProtocolSession`)**: Verified bidirectional protocol messaging is established.

`isVendorControllable` is `true` if and only if both the transport session and vendor protocol session are active.

---

## 3. Platform Support Matrix

| Platform | Subsystem | Candidate Transports | Permission Mechanism | Status |
|---|---|---|---|---|
| **Linux** | BlueZ (D-Bus) | Classic Bluetooth, BLE, RFCOMM | System D-Bus access / user group | Boundary implemented; offline verified |
| **macOS** | CoreBluetooth / IOBluetooth | BLE, GATT | TCC (`NSBluetoothAlwaysUsageDescription`) | Boundary implemented; offline verified |
| **Windows** | WinRT (`Windows.Devices.Bluetooth`) | Classic Bluetooth, BLE, GATT | App manifest capabilities / Radio state | Boundary implemented; offline verified |
| **Generic Desktop**| Standalone JVM | None | N/A | Default unsupported fallback |
