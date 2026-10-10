# Phase 47 — Risk Register & Mitigation Strategy

## 1. Identified Risks & Gaps

| Risk ID | Category | Risk Description | Severity | Impact | Mitigation Strategy |
|---|---|---|---|---|---|
| **RSK-47-01** | OS API Fragmentation | Desktop operating systems (Linux BlueZ, macOS CoreBluetooth, Windows WinRT) expose radically different Bluetooth paradigms (D-Bus daemon vs CentralManager vs WinRT async). | High | Incompatible cross-platform assumptions. | Model boundaries explicitly (`LinuxBlueZDesktopAdapter`, `MacOsCoreBluetoothDesktopAdapter`, `WindowsWinRtDesktopAdapter`) rather than a leaky universal abstraction. |
| **RSK-47-02** | Conflating Audio Connection with Protocol Availability | Users and UI assume that if earbuds play music via OS Bluetooth, OmniBuds can immediately control ANC/EQ. | High | Failed commands, crashes, or deceptive UI state. | Strict tri-state separation in `DesktopConnectionSessionState`. Control requires verified transport + protocol session. |
| **RSK-47-03** | macOS TCC Permissions | macOS sandboxing and privacy daemon (TCC) require user permission prompts for Bluetooth scanning and peripheral access. | Medium | Silent discovery failures or unhandled app terminations. | Explicit `PERMISSION_REQUIRED` and `PERMISSION_DENIED` states in `DesktopBluetoothAvailability`. |
| **RSK-47-04** | Resource Leaks During Discovery | Aborted or uncaught discovery sessions can keep native radio scanning active in the background, draining host power. | Medium | Excessive power/CPU consumption on desktop hosts. | Idempotent `stop()` contract on `DesktopDeviceDiscoverySession`, automatic cancellation on adapter power-down. |
| **RSK-47-05** | Native Toolchain & Dependency Constraints | The sandbox environment has no raw internet access to download native C/JNI/D-Bus desktop Bluetooth libraries. | High | Build breakages if external native dependencies are introduced. | Keep portable interfaces and boundaries pure Kotlin in `:core`. Defer native JNI packaging to downstream desktop packaging phase. |
