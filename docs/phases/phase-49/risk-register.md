# Phase 49 — Android UI Risk Register

| Risk ID | Risk Summary | Severity | Likelihood | Impact | Mitigation Strategy | Status |
|---|---|---|---|---|---|---|
| **RSK-49-01** | Offline Compose Compiler Incompatibility | HIGH | HIGH | HIGH | All presentation logic, state flows, view models, and accessibility semantics are decoupled into pure-Kotlin files (`presentation/`). Compose screens (`ui/compose/`) remain ultra-thin declarative layouts. | Mitigated |
| **RSK-49-02** | Android Bluetooth Permission Denials (API 31+) | HIGH | MEDIUM | HIGH | `DevicesViewModel` integrates directly with `BluetoothPlatformPolicy`, displaying clear, non-nagging guidance when `BLUETOOTH_CONNECT` or `BLUETOOTH_SCAN` permissions are denied or restricted. | Mitigated |
| **RSK-49-03** | Split-Brain State Across OS Surfaces | MEDIUM | MEDIUM | HIGH | `AndroidSurfaceCoordinator` acts as the single source of truth, synchronizing device selection across the Main App, Quick Settings Tile, Notification, and Home Widget. | Mitigated |
| **RSK-49-04** | Rapid User Taps Causing Duplicate Hardware Operations | HIGH | HIGH | MEDIUM | `DeviceWorkspaceViewModel` maintains an internal `inFlightOperations` registry and ignores redundant button presses while an operation is in `PENDING` state. | Mitigated |
| **RSK-49-05** | UI Freezing on Conflated Flow Emissions | MEDIUM | LOW | HIGH | Mutex locks guard state transitions in view models; flows are conflated StateFlows to prevent collector backpressure or buffer exhaustion. | Mitigated |
| **RSK-49-06** | Accidental Display of Synthetic Battery or Codec Values | HIGH | LOW | CRITICAL | Models explicitly require nullable integers for battery percentages and flag unobservable codecs with human-readable OS limitation notes. | Mitigated |
