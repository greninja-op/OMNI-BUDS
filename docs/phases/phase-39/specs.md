# Phase 39 — Specifications

## VendorRegistry (existing, verified)

- resolve: exactly one Matched and no Ambiguous → adapter;
  otherwise null.
- isAmbiguous: any Ambiguous → true.
- Immutable registration.

## MatchResult (existing)

- Matched(adapterId, evidence): definite.
- NotMatched: no support.
- Ambiguous(reason): might be supported; writes disabled.

## ScriptedVendorAdapter (test-only)

- Matches on manufacturer company ID.
- Partial evidence → Ambiguous.
- Unobserved → NotMatched.
