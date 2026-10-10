# Phase 47 — Test Plan

## 1. Objectives & Approach

The Phase 47 test plan verifies the desktop Bluetooth layer contracts, discovery flows, lifecycle transitions, state mapping, and concurrency guards entirely offline. No physical Bluetooth hardware or OS radios are engaged during testing.

---

## 2. Test Fixtures & Harness

- `DeterministicSimulatedDesktopAdapter`: An in-memory, deterministic test double located in `core/src/test/kotlin/com/omnibuds/core/platform/desktop/`.
- `SimulatedDesktopDiscoverySession`: Cold flow-backed discovery session enabling precise event injection, replay verification, and cancellation checks.
- `SimulatedDesktopTransportSession`: Thread-safe mock transport handle tracking connection state and closure callbacks.

---

## 3. Targeted Test Matrix

| Test Method | Category | Scenario Under Test | Expected Result |
|---|---|---|---|
| `adapterAvailabilityDistinguishesStatesTruthfully` | Status & State | Verification of all 8 states in `DesktopBluetoothAvailability` | Correct boolean flags for `isUsable`, `isAuthorizationIssue`, `isIndeterminate`. |
| `unsupportedAdapterTruthfullyReportsUnsupported` | Platform Boundary | Interaction with `UnsupportedDesktopBluetoothAdapter` | Returns `UNSUPPORTED`, fails discovery with `ADAPTER_UNAVAILABLE`. |
| `linuxBluezAdapterReportsAvailabilityBasedOnDaemonAndRadio` | Platform Boundary | Tests `LinuxBlueZDesktopAdapter` across daemon and power combinations | Returns `UNAVAILABLE` when daemon missing, `DISABLED` when unpowered, `AVAILABLE` when ready. |
| `macOsCoreBluetoothAdapterReportsPermissionAndPowerAccurately` | Platform Boundary | Tests `MacOsCoreBluetoothDesktopAdapter` with TCC & radio toggles | Returns `PERMISSION_REQUIRED` without TCC, rejects discovery with `PERMISSION_DENIED`. |
| `windowsWinRtAdapterReportsRadioPresenceAndState` | Platform Boundary | Tests `WindowsWinRtDesktopAdapter` with radio states | Correct mapping between radio presence/power and availability. |
| `discoverySessionStartsStreamsAndCleansUpOnStop` | Discovery Lifecycle | Discovery start, device observation streaming, stop, and late event drop | Observations emitted correctly; post-stop events dropped; discovery cleaned up. |
| `adapterDisabledDuringDiscoveryCancelsSessionAutomatically` | Resilience | Hardware/radio disabled while discovery session active | Active discovery session automatically cancelled without hanging. |
| `duplicateDiscoveryStartFailsWithStructuredError` | Concurrency | Calling `startDiscovery()` while session is already active | Returns structured `INVALID_STATE` failure. |
| `transportSessionLifecycleAndDuplicatePrevention` | Session Management | Opening concurrent transport sessions to the same device | Second concurrent open is rejected with `INVALID_STATE`. |
| `unsupportedTransportKindIsRejected` | Transport Contract | Requesting a transport kind not supported by the adapter (e.g., LE Audio) | Fails with `TRANSPORT_UNAVAILABLE`. |
| `discoveredDeviceProtectsPrivacyAndMaintainsIntegrity` | Privacy & Security | `safeLabel` computation for named vs unnamed devices | Address and identifier redacted in label. |
| `desktopConnectionSessionStateDistinguishesOsAndVendorControl` | State Tri-State | Evaluates `isVendorControllable` across all 3 connection axes | Returns `true` only when both transport and vendor protocol are active. |
