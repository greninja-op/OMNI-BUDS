# Phase 40 — Vendor Registry and Resolution

## Registry

`VendorRegistry` (existing, unchanged): immutable, deterministic.
Exactly one Matched and no Ambiguous → adapter; otherwise null.

## Resolution outcomes

`VendorResolution`:

- ExactMatch(adapter, evidence).
- FamilyMatch(adapter, reason).
- Ambiguous(reason).
- KnownUnsupported(reason).
- UnknownDevice.
- IncompatibleVersion(reason).

`VendorResolver` wraps the registry and reports typed outcomes
with reasons for diagnostics.

## Rules

- No silent first-match selection.
- No dynamic code loading.
- Ambiguity never enables writes.
- Removal is by constructing a new registry (immutable).
