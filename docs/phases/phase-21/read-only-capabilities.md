# Phase 21 — Read-Only Capabilities

## Observable without vendor support

These categories may be exposed through public interfaces only:

- Device discovery metadata.
- Manufacturer information, when available.
- Model identity, when adequately established.
- Pairing/bonding status.
- Connection status.
- Available transport information.
- Audio-route information.
- OS-exposed device metadata.
- Publicly observable battery information, where supported.
- Protocol-independent device information.
- Vendor-specific state through a *verified* read-only operation.

These are possible categories, not guarantees.

## Restricted operations

- Hardware-state write.
- Configuration reset.
- Firmware update.
- Arbitrary raw transport write.

These require: supported classification + verified protocol +
capability-level write verification + firmware compatibility + fresh evidence.

## No fabricated defaults

Unknown battery is not 0%. Unknown ANC is not NORMAL. Unknown codec is
not SBC. Unknown EQ is not flat. Missing case-battery support is not a
zero reading. Null means unknown; there is no other representation.

## No inference from display names

A display name is not a model identification. It authorizes nothing.
