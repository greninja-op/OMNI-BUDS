# Phase 39 — Repository Audit

**Date:** 2026-10-09

## Blocker (stop condition)

**No evidence-backed first-vendor implementation exists.**

Phase 19 (target-selection.md) evaluated 6 candidates and REJECTED
all of them for implementation: Bose BMAP (strongest, but byte-level
specs inaccessible and no hardware), QCY/Realme/Technics/Huawei/
SoundPEATS (license unverified, insufficient docs, or no hardware).
Phase 19 status: **BLOCKED**.

What exists in `core/vendor/`:

- `VendorAdapter` — interface (match, protocol, supportedFirmware).
- `NullVendorAdapter` — matches nothing; honest default.
- `VendorRegistry` — deterministic resolution; ambiguous/conflicting
  matches resolve to null (safe fallback).
- `core/extension/` — vendor feature framework (Phase 23).

Per the phase stop conditions ("If the original target is not
identifiable, stop and report the blocker rather than choosing an
arbitrary vendor"), no new vendor adapter will be implemented in
Phase 39.

## What Phase 39 CAN harden (justified by existing evidence)

1. Vendor matching semantics: ambiguity, conflicts, missing
   metadata, name-only fingerprints.
2. Registry determinism and isolation.
3. Unknown-device fallback (Phase 21) integration.
4. Capability-state correctness (Phase 8 states).
5. Extension framework isolation (Phase 23).

## What stays BLOCKED

Device-specific protocol operations, capability reads, persistence
verification, acoustic behavior — all deferred to Phase 52 with a
hardware-verification campaign definition.
