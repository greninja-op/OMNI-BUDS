# Phase 32 — Platform Test Strategy

## Layers

- **A (JVM unit):** ApiLevelPolicy, PermissionPolicy,
  BluetoothPlatformPolicy — pure logic, fully tested here.
- **B (Robolectric):** Not available in this environment — marked NOT_RUN.
- **C (SDK/emulator):** No emulator images — marked NOT_RUN.
- **D (instrumentation):** No device — marked NOT_RUN.

## Evidence mapping (Phase 31 compatible)

| This phase | Phase 31 equivalent |
|---|---|
| JVM unit pass | UNIT_TESTED |
| Framework-simulated | FRAMEWORK_SIMULATED |
| Emulator | EMULATOR_API_VERIFIED (not claimed) |
| Instrumentation | INSTRUMENTATION_VERIFIED (not claimed) |
| OEM | OEM_VERIFIED (not claimed) |
| Hardware | HARDWARE_VERIFIED (not claimed) |
| Unavailable | NOT_TESTED / BLOCKED |

## Claim discipline

JVM tests prove decision logic. They do not prove the Android
Bluetooth stack, OEM behavior, or physical-device compatibility.
