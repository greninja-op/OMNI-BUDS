# Phase 39 — Design

## Scope decision

No new vendor adapter. The phase hardens the vendor *framework*
(matching, registry, fallback) and documents the blocker.

## Modules touched

- `core/vendor/` — no production changes; new tests only.
- `docs/phases/phase-39/` — 14 documents.

## Test strategy

`VendorMatchingHardeningTest` uses scripted adapters to prove
framework safety semantics: ambiguity → null, conflicts → null,
missing metadata → no match, name-only → ambiguous.

## Data flow

Unchanged: fingerprint → registry.resolve → adapter or
unknown-device fallback.
