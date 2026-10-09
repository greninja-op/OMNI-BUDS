# Phase 32 — Test Plan

## Unit (`CompatTests`, 19 tests)
ApiLevelPolicy (7): Bluetooth 31, legacy 26–30, notification 33,
channels, tile, supported range, FGS types.
PermissionPolicy (6): granted/denied/unavailable/restricted/unknown,
SecurityException.
BluetoothPlatformPolicy (6): no adapter, disabled, granted, denied,
unknown, grant≠success.

## Audits
Tile, notification, widget, lifecycle — reviewed against existing
implementations and tests.

## Blocked
Robolectric, emulator, instrumentation — unavailable in this
environment; marked NOT_RUN.

## Regression
Full suite: core + android, 0 failures.
