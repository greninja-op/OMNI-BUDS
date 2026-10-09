# Phase 40 — Identity and Compatibility Policy

## Evidence

Manufacturer IDs, product/model IDs, service/protocol
fingerprints, firmware, hardware revision, transport
characteristics, verified protocol knowledge.

## Rules

- Bluetooth display names alone never establish an exact match.
- Missing evidence is explicit; conflicting evidence is
  Ambiguous.
- Broad match rules that could route one vendor's command to
  another vendor's device are forbidden.
- Insufficient identity confidence denies mutating operations.

## Firmware

Evaluated per integration; unknown stays unknown; incompatible is
rejected. Stale compatibility data never silently authorizes
operations.
