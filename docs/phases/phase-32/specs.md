# Phase 32 — Specifications

## ApiLevelPolicy

- bluetoothRuntimePermissionsRequired: apiLevel >= 31
- legacyBluetoothPermissions: 26..30
- notificationRuntimePermissionRequired: apiLevel >= 33
- notificationChannelsRequired: apiLevel >= 26
- tileServiceAvailable: apiLevel >= 24
- widgetPreviewSupported: apiLevel >= 31
- foregroundServiceTypesEnforced: apiLevel >= 29
- backgroundActivityRestricted: apiLevel >= 29
- exactAlarmRestricted: apiLevel >= 31
- isSupported: 26..35

## PermissionPolicy

decide(state, operation): GRANTED → Allow; DENIED/UNAVAILABLE/
RESTRICTED → Refuse(reason); UNKNOWN → Defer(reason).
classifySecurityException → Refuse.

## BluetoothPlatformPolicy

decide(state): !adapterPresent → CannotOperate; !adapterEnabled →
Degraded; permission Allow → MayOperate; Refuse → CannotOperate;
Defer → Degraded.
